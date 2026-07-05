package com.neteinstein.smsredirect.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neteinstein.smsredirect.domain.contacts.ContactNameResolver
import com.neteinstein.smsredirect.domain.model.ForwardHistory
import com.neteinstein.smsredirect.domain.usecase.GetDefaultPrefixUseCase
import com.neteinstein.smsredirect.domain.usecase.GetForwardHistoryUseCase
import com.neteinstein.smsredirect.domain.usecase.GetForwardPrefixUseCase
import com.neteinstein.smsredirect.domain.usecase.GetKeepHistoryUseCase
import com.neteinstein.smsredirect.domain.usecase.GetSendWhileRoamingUseCase
import com.neteinstein.smsredirect.domain.usecase.SetDefaultPrefixUseCase
import com.neteinstein.smsredirect.domain.usecase.SetForwardPrefixUseCase
import com.neteinstein.smsredirect.domain.usecase.SetKeepHistoryUseCase
import com.neteinstein.smsredirect.domain.usecase.SetSendWhileRoamingUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SettingsViewModel(
    getSendWhileRoamingUseCase: GetSendWhileRoamingUseCase,
    private val setSendWhileRoamingUseCase: SetSendWhileRoamingUseCase,
    getKeepHistoryUseCase: GetKeepHistoryUseCase,
    private val setKeepHistoryUseCase: SetKeepHistoryUseCase,
    getDefaultPrefixUseCase: GetDefaultPrefixUseCase,
    private val setDefaultPrefixUseCase: SetDefaultPrefixUseCase,
    getForwardPrefixUseCase: GetForwardPrefixUseCase,
    private val setForwardPrefixUseCase: SetForwardPrefixUseCase,
    getForwardHistoryUseCase: GetForwardHistoryUseCase,
    private val contactNameResolver: ContactNameResolver? = null,
) : ViewModel() {
    private val _sendWhileRoaming = MutableStateFlow(false)
    val sendWhileRoaming: StateFlow<Boolean> = _sendWhileRoaming.asStateFlow()
    private val _keepHistory = MutableStateFlow(true)
    val keepHistory: StateFlow<Boolean> = _keepHistory.asStateFlow()
    private val _defaultPrefix = MutableStateFlow("+351")
    val defaultPrefix: StateFlow<String> = _defaultPrefix.asStateFlow()
    private val _forwardPrefix = MutableStateFlow("SMS Redirect:")
    val forwardPrefix: StateFlow<String> = _forwardPrefix.asStateFlow()
    private val _forwardHistory = MutableStateFlow<List<ForwardHistory>>(emptyList())
    val forwardHistory: StateFlow<List<ForwardHistory>> = _forwardHistory.asStateFlow()
    private val _contactNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val contactNames: StateFlow<Map<String, String>> = _contactNames.asStateFlow()

    init {
        getSendWhileRoamingUseCase()
            .onEach { _sendWhileRoaming.value = it }
            .launchIn(viewModelScope)
        getKeepHistoryUseCase()
            .onEach { _keepHistory.value = it }
            .launchIn(viewModelScope)
        getDefaultPrefixUseCase()
            .onEach { _defaultPrefix.value = it }
            .launchIn(viewModelScope)
        getForwardPrefixUseCase()
            .onEach { _forwardPrefix.value = it }
            .launchIn(viewModelScope)
        getForwardHistoryUseCase(limit = 50)
            .onEach { history ->
                _forwardHistory.value = history
                resolveContactNames(history)
            }
            .launchIn(viewModelScope)
    }

    private fun resolveContactNames(history: List<ForwardHistory>) {
        if (contactNameResolver == null) return
        val numbers =
            history.map { it.sender }
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

    fun setSendWhileRoaming(enabled: Boolean) {
        viewModelScope.launch {
            setSendWhileRoamingUseCase(enabled)
        }
    }

    fun setKeepHistory(enabled: Boolean) {
        viewModelScope.launch {
            setKeepHistoryUseCase(enabled)
        }
    }

    fun setDefaultPrefix(prefix: String) {
        viewModelScope.launch {
            setDefaultPrefixUseCase(prefix)
        }
    }

    fun setForwardPrefix(prefix: String) {
        viewModelScope.launch {
            setForwardPrefixUseCase(prefix)
        }
    }
}

class SettingsViewModelFactory(
    private val getSendWhileRoamingUseCase: GetSendWhileRoamingUseCase,
    private val setSendWhileRoamingUseCase: SetSendWhileRoamingUseCase,
    private val getKeepHistoryUseCase: GetKeepHistoryUseCase,
    private val setKeepHistoryUseCase: SetKeepHistoryUseCase,
    private val getDefaultPrefixUseCase: GetDefaultPrefixUseCase,
    private val setDefaultPrefixUseCase: SetDefaultPrefixUseCase,
    private val getForwardPrefixUseCase: GetForwardPrefixUseCase,
    private val setForwardPrefixUseCase: SetForwardPrefixUseCase,
    private val getForwardHistoryUseCase: GetForwardHistoryUseCase,
    private val contactNameResolver: ContactNameResolver? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SettingsViewModel(
            getSendWhileRoamingUseCase = getSendWhileRoamingUseCase,
            setSendWhileRoamingUseCase = setSendWhileRoamingUseCase,
            getKeepHistoryUseCase = getKeepHistoryUseCase,
            setKeepHistoryUseCase = setKeepHistoryUseCase,
            getDefaultPrefixUseCase = getDefaultPrefixUseCase,
            setDefaultPrefixUseCase = setDefaultPrefixUseCase,
            getForwardPrefixUseCase = getForwardPrefixUseCase,
            setForwardPrefixUseCase = setForwardPrefixUseCase,
            getForwardHistoryUseCase = getForwardHistoryUseCase,
            contactNameResolver = contactNameResolver,
        ) as T
    }
}
