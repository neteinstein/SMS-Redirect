package com.neteinstein.smsredirect.presentation.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neteinstein.smsredirect.domain.contacts.ContactNameResolver
import com.neteinstein.smsredirect.domain.messaging.SendFailure
import com.neteinstein.smsredirect.domain.messaging.SendOutcome
import com.neteinstein.smsredirect.domain.model.ConversationMessage
import com.neteinstein.smsredirect.domain.model.MessageStatus
import com.neteinstein.smsredirect.domain.model.OutgoingAttachment
import com.neteinstein.smsredirect.domain.usecase.DeleteMessageUseCase
import com.neteinstein.smsredirect.domain.usecase.GetConversationAddressesUseCase
import com.neteinstein.smsredirect.domain.usecase.GetConversationMessagesUseCase
import com.neteinstein.smsredirect.domain.usecase.GetOrCreateConversationUseCase
import com.neteinstein.smsredirect.domain.usecase.MarkConversationReadUseCase
import com.neteinstein.smsredirect.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class ConversationUiState(
    val threadId: Long = 0L,
    val addresses: List<String> = emptyList(),
    val contactNames: Map<String, String> = emptyMap(),
    val messages: List<ConversationMessage> = emptyList(),
    val draft: String = "",
    val attachments: List<OutgoingAttachment> = emptyList(),
    val sending: Boolean = false,
    val error: SendFailure? = null,
) {
    /** "Ana", "Ana, Rui" or the bare number when there is no contact. */
    val title: String get() = addresses.joinToString(", ") { contactNames[it] ?: it }

    val isGroup: Boolean get() = addresses.size > 1

    val canSend: Boolean get() = !sending && addresses.isNotEmpty() && (draft.isNotBlank() || attachments.isNotEmpty())

    /** Shown under the composer so the user knows a picture or group message uses MMS. */
    val sendsAsMms: Boolean get() = attachments.isNotEmpty() || isGroup
}

/**
 * One conversation. Opened either for an existing thread ([threadId] > 0, from the list or a
 * notification) or for a set of recipients (new message, a `smsto:` link, Share), in which case
 * the thread is looked up -- or created -- from the addresses.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModel(
    threadId: Long,
    initialAddresses: List<String>,
    initialDraft: String?,
    initialAttachment: OutgoingAttachment?,
    getConversationMessagesUseCase: GetConversationMessagesUseCase,
    private val getConversationAddressesUseCase: GetConversationAddressesUseCase,
    private val getOrCreateConversationUseCase: GetOrCreateConversationUseCase,
    private val markConversationReadUseCase: MarkConversationReadUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val deleteMessageUseCase: DeleteMessageUseCase,
    private val contactNameResolver: ContactNameResolver,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            ConversationUiState(
                threadId = threadId,
                addresses = initialAddresses,
                draft = initialDraft.orEmpty(),
                attachments = listOfNotNull(initialAttachment),
            ),
        )
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private val threadIdFlow = MutableStateFlow(threadId)

    /** Whether the screen is in front; only then do arriving messages count as read. */
    private var visible = false

    init {
        viewModelScope.launch {
            val resolvedThread = if (threadId > 0) threadId else getOrCreateConversationUseCase(initialAddresses) ?: 0L
            val addresses = if (threadId > 0) getConversationAddressesUseCase(threadId) else initialAddresses
            _uiState.value = _uiState.value.copy(threadId = resolvedThread, addresses = addresses)
            threadIdFlow.value = resolvedThread
            resolveNames(addresses)
        }
        threadIdFlow
            .filter { it > 0 }
            .flatMapLatest { getConversationMessagesUseCase(it) }
            .onEach { messages ->
                _uiState.value = _uiState.value.copy(messages = messages)
                if (visible) markRead()
            }.launchIn(viewModelScope)
    }

    fun onVisible(isVisible: Boolean) {
        visible = isVisible
        if (isVisible) markRead()
    }

    fun onDraftChanged(text: String) {
        _uiState.value = _uiState.value.copy(draft = text, error = null)
    }

    fun onAttachmentPicked(attachment: OutgoingAttachment) {
        _uiState.value = _uiState.value.copy(attachments = _uiState.value.attachments + attachment, error = null)
    }

    fun onAttachmentRemoved(attachment: OutgoingAttachment) {
        _uiState.value = _uiState.value.copy(attachments = _uiState.value.attachments - attachment)
    }

    fun onErrorShown() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun send() {
        val state = _uiState.value
        if (!state.canSend) return
        _uiState.value = state.copy(sending = true, error = null)
        viewModelScope.launch {
            when (val outcome = sendMessageUseCase(state.addresses, state.draft, state.attachments)) {
                is SendOutcome.Queued -> {
                    _uiState.value = _uiState.value.copy(sending = false, draft = "", attachments = emptyList())
                    if (outcome.threadId != threadIdFlow.value) {
                        _uiState.value = _uiState.value.copy(threadId = outcome.threadId)
                        threadIdFlow.value = outcome.threadId
                    }
                }

                is SendOutcome.Failed -> {
                    _uiState.value = _uiState.value.copy(sending = false, error = outcome.reason)
                }
            }
        }
    }

    /** Resends a failed SMS: the failed copy is removed so the conversation shows it once. */
    fun retry(message: ConversationMessage) {
        if (message.status != MessageStatus.FAILED || message.isMms) return
        viewModelScope.launch {
            val outcome = sendMessageUseCase(_uiState.value.addresses, message.body)
            if (outcome is SendOutcome.Queued) {
                deleteMessageUseCase(message)
            } else if (outcome is SendOutcome.Failed) {
                _uiState.value = _uiState.value.copy(error = outcome.reason)
            }
        }
    }

    fun delete(message: ConversationMessage) {
        viewModelScope.launch { deleteMessageUseCase(message) }
    }

    private fun markRead() {
        val threadId = threadIdFlow.value
        if (threadId <= 0) return
        if (_uiState.value.messages.none { it.isIncoming }) return
        viewModelScope.launch { markConversationReadUseCase(threadId) }
    }

    private suspend fun resolveNames(addresses: List<String>) {
        val names = addresses.mapNotNull { address -> contactNameResolver.lookupNameByNumber(address)?.let { address to it } }.toMap()
        _uiState.value = _uiState.value.copy(contactNames = names)
    }
}

class ConversationViewModelFactory(
    private val threadId: Long,
    private val addresses: List<String>,
    private val draft: String?,
    private val attachment: OutgoingAttachment?,
    private val getConversationMessagesUseCase: GetConversationMessagesUseCase,
    private val getConversationAddressesUseCase: GetConversationAddressesUseCase,
    private val getOrCreateConversationUseCase: GetOrCreateConversationUseCase,
    private val markConversationReadUseCase: MarkConversationReadUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val deleteMessageUseCase: DeleteMessageUseCase,
    private val contactNameResolver: ContactNameResolver,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ConversationViewModel(
            threadId,
            addresses,
            draft,
            attachment,
            getConversationMessagesUseCase,
            getConversationAddressesUseCase,
            getOrCreateConversationUseCase,
            markConversationReadUseCase,
            sendMessageUseCase,
            deleteMessageUseCase,
            contactNameResolver,
        ) as T
}
