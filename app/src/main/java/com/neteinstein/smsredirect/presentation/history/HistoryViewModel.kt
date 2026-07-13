package com.neteinstein.smsredirect.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neteinstein.smsredirect.domain.contacts.ContactNameResolver
import com.neteinstein.smsredirect.domain.model.ForwardHistory
import com.neteinstein.smsredirect.domain.usecase.DeleteAllHistoryUseCase
import com.neteinstein.smsredirect.domain.usecase.GetForwardHistoryUseCase
import com.neteinstein.smsredirect.domain.usecase.GetKeepHistoryUseCase
import com.neteinstein.smsredirect.domain.usecase.SetKeepHistoryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class HistoryViewModel(
    getForwardHistoryUseCase: GetForwardHistoryUseCase,
    getKeepHistoryUseCase: GetKeepHistoryUseCase,
    private val setKeepHistoryUseCase: SetKeepHistoryUseCase,
    private val deleteAllHistoryUseCase: DeleteAllHistoryUseCase,
    private val contactNameResolver: ContactNameResolver? = null,
) : ViewModel() {
    private val _forwardHistory = MutableStateFlow<List<ForwardHistory>>(emptyList())
    val forwardHistory: StateFlow<List<ForwardHistory>> = _forwardHistory.asStateFlow()

    private val _contactNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val contactNames: StateFlow<Map<String, String>> = _contactNames.asStateFlow()

    private val _keepHistory = MutableStateFlow(true)
    val keepHistory: StateFlow<Boolean> = _keepHistory.asStateFlow()

    private val _showDisableConfirmation = MutableStateFlow(false)
    val showDisableConfirmation: StateFlow<Boolean> = _showDisableConfirmation.asStateFlow()

    private val _showClearConfirmation = MutableStateFlow(false)
    val showClearConfirmation: StateFlow<Boolean> = _showClearConfirmation.asStateFlow()

    init {
        getForwardHistoryUseCase(limit = 50)
            .onEach { history ->
                _forwardHistory.value = history
                resolveContactNames(history)
            }.launchIn(viewModelScope)
        getKeepHistoryUseCase()
            .onEach { _keepHistory.value = it }
            .launchIn(viewModelScope)
    }

    fun onKeepHistoryToggled(enabled: Boolean) {
        if (!enabled && _forwardHistory.value.isNotEmpty()) {
            _showDisableConfirmation.value = true
        } else {
            viewModelScope.launch {
                setKeepHistoryUseCase(enabled)
            }
        }
    }

    fun confirmDisableHistory() {
        _showDisableConfirmation.value = false
        viewModelScope.launch {
            setKeepHistoryUseCase(false)
            deleteAllHistoryUseCase()
        }
    }

    fun dismissDisableConfirmation() {
        _showDisableConfirmation.value = false
    }

    fun onClearHistoryClicked() {
        _showClearConfirmation.value = true
    }

    fun confirmClearHistory() {
        _showClearConfirmation.value = false
        viewModelScope.launch {
            deleteAllHistoryUseCase()
        }
    }

    fun dismissClearConfirmation() {
        _showClearConfirmation.value = false
    }

    private fun resolveContactNames(history: List<ForwardHistory>) {
        if (contactNameResolver == null) return
        val numbers =
            history
                .map { it.sender }
                .filter { it.isNotBlank() }
                .distinct()

        viewModelScope.launch {
            val resolved = mutableMapOf<String, String>()
            for (number in numbers) {
                val name = contactNameResolver.lookupNameByNumber(number)
                if (name != null) {
                    resolved[number] = name
                }
            }
            _contactNames.value = resolved
        }
    }
}

class HistoryViewModelFactory(
    private val getForwardHistoryUseCase: GetForwardHistoryUseCase,
    private val getKeepHistoryUseCase: GetKeepHistoryUseCase,
    private val setKeepHistoryUseCase: SetKeepHistoryUseCase,
    private val deleteAllHistoryUseCase: DeleteAllHistoryUseCase,
    private val contactNameResolver: ContactNameResolver? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return HistoryViewModel(
            getForwardHistoryUseCase = getForwardHistoryUseCase,
            getKeepHistoryUseCase = getKeepHistoryUseCase,
            setKeepHistoryUseCase = setKeepHistoryUseCase,
            deleteAllHistoryUseCase = deleteAllHistoryUseCase,
            contactNameResolver = contactNameResolver,
        ) as T
    }
}
