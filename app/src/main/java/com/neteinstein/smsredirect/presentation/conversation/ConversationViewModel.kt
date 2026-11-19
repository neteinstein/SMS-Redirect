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
import com.neteinstein.smsredirect.domain.usecase.AssessConversationRiskUseCase
import com.neteinstein.smsredirect.domain.usecase.ConversationRisk
import com.neteinstein.smsredirect.domain.usecase.DeleteMessageUseCase
import com.neteinstein.smsredirect.domain.usecase.DownloadMmsUseCase
import com.neteinstein.smsredirect.domain.usecase.GetConversationAddressesUseCase
import com.neteinstein.smsredirect.domain.usecase.GetConversationMessagesUseCase
import com.neteinstein.smsredirect.domain.usecase.GetOrCreateConversationUseCase
import com.neteinstein.smsredirect.domain.usecase.GetTrustedAmongUseCase
import com.neteinstein.smsredirect.domain.usecase.MarkConversationReadUseCase
import com.neteinstein.smsredirect.domain.usecase.ObserveTrustedSendersUseCase
import com.neteinstein.smsredirect.domain.usecase.SendMessageUseCase
import com.neteinstein.smsredirect.domain.usecase.TrustSendersUseCase
import com.neteinstein.smsredirect.domain.usecase.UntrustSendersUseCase
import com.neteinstein.smsredirect.domain.usecase.requiresMms
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
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
    /** Set when a "not downloaded" picture message could not start downloading. */
    val downloadFailed: Boolean = false,
    /** Received messages that look like spam or fraud: warned about, their links disabled. */
    val risk: ConversationRisk = ConversationRisk.NONE,
    /** People in this conversation the user trusts: offered "Stop trusting". */
    val trustedSenders: List<String> = emptyList(),
) {
    /** "Ana", "Ana, Rui" or the bare number when there is no contact. */
    val title: String get() = addresses.joinToString(", ") { contactNames[it] ?: it }

    val isGroup: Boolean get() = addresses.size > 1

    val canSend: Boolean get() = !sending && addresses.isNotEmpty() && (draft.isNotBlank() || attachments.isNotEmpty())

    /** Shown in the composer so the user knows a picture, group or email message uses MMS. */
    val sendsAsMms: Boolean get() = requiresMms(addresses, attachments.isNotEmpty())

    /**
     * Whether [message]'s links open. A received message is checked in the background, so until
     * the latest result covers it its links are [MessageLinkState.PENDING]: shown as plain text.
     */
    fun linkStateOf(message: ConversationMessage): MessageLinkState =
        when {
            !message.isIncoming -> MessageLinkState.OPEN
            message.key in risk.flaggedMessageKeys -> MessageLinkState.BLOCKED
            message.key !in risk.assessedMessageKeys -> MessageLinkState.PENDING
            else -> MessageLinkState.OPEN
        }
}

enum class MessageLinkState {
    /** Tappable: sent by this phone, or checked and not flagged. */
    OPEN,

    /** Flagged as spam or fraud: tapping asks whether to trust the sender. */
    BLOCKED,

    /** Not checked yet: not a link until it is. */
    PENDING,
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
    private val downloadMmsUseCase: DownloadMmsUseCase,
    private val assessConversationRiskUseCase: AssessConversationRiskUseCase,
    observeTrustedSendersUseCase: ObserveTrustedSendersUseCase,
    private val trustSendersUseCase: TrustSendersUseCase,
    private val untrustSendersUseCase: UntrustSendersUseCase,
    private val getTrustedAmongUseCase: GetTrustedAmongUseCase = GetTrustedAmongUseCase(),
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
        // Re-checked when messages arrive or the user trusts a sender.
        val trusted = observeTrustedSendersUseCase()
        combine(uiState.map { it.messages }.distinctUntilChanged(), trusted) { messages, trustedSet ->
            messages to trustedSet
        }.mapLatest { (messages, trustedSet) -> assessConversationRiskUseCase(messages, trustedSet) }
            .onEach { risk -> _uiState.value = _uiState.value.copy(risk = risk) }
            .launchIn(viewModelScope)
        combine(uiState.map { it.addresses }.distinctUntilChanged(), trusted) { addresses, trustedSet ->
            getTrustedAmongUseCase(addresses, trustedSet)
        }.onEach { senders -> _uiState.value = _uiState.value.copy(trustedSenders = senders) }
            .launchIn(viewModelScope)
    }

    /** Undoes a trust decision: this conversation's messages are checked, and flagged, again. */
    fun stopTrustingSenders() {
        val senders = _uiState.value.trustedSenders
        if (senders.isEmpty()) return
        viewModelScope.launch { untrustSendersUseCase(senders) }
    }

    /** The user confirmed they know who sent the flagged messages: lift the warning, enable links. */
    fun trustFlaggedSenders() {
        val senders = _uiState.value.risk.flaggedSenders
        if (senders.isEmpty()) return
        viewModelScope.launch { trustSendersUseCase(senders) }
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
        _uiState.value = _uiState.value.copy(error = null, downloadFailed = false)
    }

    /** Downloads a picture message that arrived while it could not be fetched. */
    fun download(message: ConversationMessage) {
        viewModelScope.launch {
            if (!downloadMmsUseCase(message)) _uiState.value = _uiState.value.copy(downloadFailed = true)
        }
    }

    fun send() {
        val state = _uiState.value
        if (!state.canSend) return
        _uiState.value = state.copy(sending = true, error = null)
        viewModelScope.launch {
            when (val outcome = sendMessageUseCase(state.addresses, state.draft, state.attachments)) {
                is SendOutcome.Queued -> {
                    // Reading and resizing attachments takes a moment and the composer stays
                    // editable; clear only what was sent, keep anything typed or added meanwhile.
                    val current = _uiState.value
                    _uiState.value =
                        current.copy(
                            sending = false,
                            draft = if (current.draft == state.draft) "" else current.draft,
                            attachments = current.attachments - state.attachments.toSet(),
                        )
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

    /**
     * Resends a failed SMS or MMS: same text, same attachments (read back from the stored MMS
     * parts), same recipients. The failed copy is removed only once the new one is queued, by
     * which time the sender has already copied the attachments.
     */
    fun retry(message: ConversationMessage) {
        if (message.status != MessageStatus.FAILED) return
        viewModelScope.launch {
            val attachments = message.attachments.map { OutgoingAttachment(it.uri, it.contentType, it.name) }
            val outcome = sendMessageUseCase(_uiState.value.addresses, message.body, attachments)
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
    private val downloadMmsUseCase: DownloadMmsUseCase,
    private val assessConversationRiskUseCase: AssessConversationRiskUseCase,
    private val observeTrustedSendersUseCase: ObserveTrustedSendersUseCase,
    private val trustSendersUseCase: TrustSendersUseCase,
    private val untrustSendersUseCase: UntrustSendersUseCase,
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
            downloadMmsUseCase,
            assessConversationRiskUseCase,
            observeTrustedSendersUseCase,
            trustSendersUseCase,
            untrustSendersUseCase,
        ) as T
}
