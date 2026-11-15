package com.neteinstein.smsredirect.presentation.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neteinstein.smsredirect.domain.contacts.ContactNameResolver
import com.neteinstein.smsredirect.domain.model.Conversation
import com.neteinstein.smsredirect.domain.usecase.DeleteConversationUseCase
import com.neteinstein.smsredirect.domain.usecase.GetConversationsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class ConversationListUiState(
    val conversations: List<Conversation> = emptyList(),
    /** Number -> contact name, for the numbers that have one. */
    val contactNames: Map<String, String> = emptyMap(),
    val loaded: Boolean = false,
)

class ConversationListViewModel(
    private val getConversationsUseCase: GetConversationsUseCase,
    private val deleteConversationUseCase: DeleteConversationUseCase,
    private val contactNameResolver: ContactNameResolver,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ConversationListUiState())
    val uiState: StateFlow<ConversationListUiState> = _uiState.asStateFlow()

    /** Names are looked up once per number; the store changes far more often than contacts. */
    private val nameCache = mutableMapOf<String, String?>()

    private var started = false

    /**
     * Starts reading the store. Deferred until the app holds the SMS role: before that there is
     * no READ_SMS, and the Messages tab shows the "set as default" prompt instead.
     */
    fun start() {
        if (started) return
        started = true
        getConversationsUseCase()
            .onEach { conversations ->
                _uiState.value = _uiState.value.copy(conversations = conversations, loaded = true)
                resolveNames(conversations)
            }.launchIn(viewModelScope)
    }

    private suspend fun resolveNames(conversations: List<Conversation>) {
        val missing = conversations.flatMap { it.addresses }.distinct().filterNot { it in nameCache }
        if (missing.isEmpty()) return
        for (number in missing) nameCache[number] = contactNameResolver.lookupNameByNumber(number)
        _uiState.value = _uiState.value.copy(contactNames = nameCache.mapNotNull { (k, v) -> v?.let { k to it } }.toMap())
    }

    fun deleteConversation(threadId: Long) {
        viewModelScope.launch { deleteConversationUseCase(threadId) }
    }
}

class ConversationListViewModelFactory(
    private val getConversationsUseCase: GetConversationsUseCase,
    private val deleteConversationUseCase: DeleteConversationUseCase,
    private val contactNameResolver: ContactNameResolver,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ConversationListViewModel(getConversationsUseCase, deleteConversationUseCase, contactNameResolver) as T
}
