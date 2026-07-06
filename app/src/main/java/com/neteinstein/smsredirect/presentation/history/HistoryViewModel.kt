package com.neteinstein.smsredirect.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neteinstein.smsredirect.domain.contacts.ContactNameResolver
import com.neteinstein.smsredirect.domain.model.ForwardHistory
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
    private val contactNameResolver: ContactNameResolver? = null,
) : ViewModel() {
    private val _forwardHistory = MutableStateFlow<List<ForwardHistory>>(emptyList())
    val forwardHistory: StateFlow<List<ForwardHistory>> = _forwardHistory.asStateFlow()

    private val _contactNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val contactNames: StateFlow<Map<String, String>> = _contactNames.asStateFlow()

    private val _keepHistory = MutableStateFlow(true)
    val keepHistory: StateFlow<Boolean> = _keepHistory.asStateFlow()

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

    fun setKeepHistory(enabled: Boolean) {
        viewModelScope.launch {
            setKeepHistoryUseCase(enabled)
        }
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
    private val contactNameResolver: ContactNameResolver? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return HistoryViewModel(
            getForwardHistoryUseCase = getForwardHistoryUseCase,
            getKeepHistoryUseCase = getKeepHistoryUseCase,
            setKeepHistoryUseCase = setKeepHistoryUseCase,
            contactNameResolver = contactNameResolver,
        ) as T
    }
}
