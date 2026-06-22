package com.neteinstein.smsredirect.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neteinstein.smsredirect.domain.usecase.GetSendWhileRoamingUseCase
import com.neteinstein.smsredirect.domain.usecase.SetSendWhileRoamingUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SettingsViewModel(
    getSendWhileRoamingUseCase: GetSendWhileRoamingUseCase,
    private val setSendWhileRoamingUseCase: SetSendWhileRoamingUseCase
) : ViewModel() {

    private val _sendWhileRoaming = MutableStateFlow(false)
    val sendWhileRoaming: StateFlow<Boolean> = _sendWhileRoaming.asStateFlow()

    init {
        getSendWhileRoamingUseCase()
            .onEach { _sendWhileRoaming.value = it }
            .launchIn(viewModelScope)
    }

    fun setSendWhileRoaming(enabled: Boolean) {
        viewModelScope.launch {
            setSendWhileRoamingUseCase(enabled)
        }
    }
}

class SettingsViewModelFactory(
    private val getSendWhileRoamingUseCase: GetSendWhileRoamingUseCase,
    private val setSendWhileRoamingUseCase: SetSendWhileRoamingUseCase
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SettingsViewModel(getSendWhileRoamingUseCase, setSendWhileRoamingUseCase) as T
    }
}
