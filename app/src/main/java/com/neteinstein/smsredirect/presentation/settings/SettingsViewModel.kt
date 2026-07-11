package com.neteinstein.smsredirect.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neteinstein.smsredirect.domain.usecase.BackupImportResult
import com.neteinstein.smsredirect.domain.usecase.ExportBackupUseCase
import com.neteinstein.smsredirect.domain.usecase.GetDefaultPrefixUseCase
import com.neteinstein.smsredirect.domain.usecase.GetForwardPrefixUseCase
import com.neteinstein.smsredirect.domain.usecase.GetSendWhileRoamingUseCase
import com.neteinstein.smsredirect.domain.usecase.GetShowNotificationsUseCase
import com.neteinstein.smsredirect.domain.usecase.ImportBackupUseCase
import com.neteinstein.smsredirect.domain.usecase.SetDefaultPrefixUseCase
import com.neteinstein.smsredirect.domain.usecase.SetForwardPrefixUseCase
import com.neteinstein.smsredirect.domain.usecase.SetSendWhileRoamingUseCase
import com.neteinstein.smsredirect.domain.usecase.SetShowNotificationsUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SettingsViewModel(
    getSendWhileRoamingUseCase: GetSendWhileRoamingUseCase,
    private val setSendWhileRoamingUseCase: SetSendWhileRoamingUseCase,
    getDefaultPrefixUseCase: GetDefaultPrefixUseCase,
    private val setDefaultPrefixUseCase: SetDefaultPrefixUseCase,
    getForwardPrefixUseCase: GetForwardPrefixUseCase,
    private val setForwardPrefixUseCase: SetForwardPrefixUseCase,
    getShowNotificationsUseCase: GetShowNotificationsUseCase,
    private val setShowNotificationsUseCase: SetShowNotificationsUseCase,
    private val exportBackupUseCase: ExportBackupUseCase,
    private val importBackupUseCase: ImportBackupUseCase,
) : ViewModel() {
    private val _sendWhileRoaming = MutableStateFlow(false)
    val sendWhileRoaming: StateFlow<Boolean> = _sendWhileRoaming.asStateFlow()
    private val _defaultPrefix = MutableStateFlow("+351")
    val defaultPrefix: StateFlow<String> = _defaultPrefix.asStateFlow()
    private val _forwardPrefix = MutableStateFlow("SMS Redirect:")
    val forwardPrefix: StateFlow<String> = _forwardPrefix.asStateFlow()
    private val _showNotifications = MutableStateFlow(true)
    val showNotifications: StateFlow<Boolean> = _showNotifications.asStateFlow()

    init {
        getSendWhileRoamingUseCase()
            .onEach { _sendWhileRoaming.value = it }
            .launchIn(viewModelScope)
        getDefaultPrefixUseCase()
            .onEach { _defaultPrefix.value = it }
            .launchIn(viewModelScope)
        getForwardPrefixUseCase()
            .onEach { _forwardPrefix.value = it }
            .launchIn(viewModelScope)
        getShowNotificationsUseCase()
            .onEach { _showNotifications.value = it }
            .launchIn(viewModelScope)
    }

    fun setSendWhileRoaming(enabled: Boolean) {
        viewModelScope.launch {
            setSendWhileRoamingUseCase(enabled)
        }
    }

    fun setShowNotifications(enabled: Boolean) {
        viewModelScope.launch {
            setShowNotificationsUseCase(enabled)
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

    private val _exportEvent = MutableSharedFlow<String>()
    val exportEvent: SharedFlow<String> = _exportEvent.asSharedFlow()

    private val _importResult = MutableSharedFlow<BackupImportResult>()
    val importResult: SharedFlow<BackupImportResult> = _importResult.asSharedFlow()

    fun exportBackup() {
        viewModelScope.launch {
            val json = exportBackupUseCase()
            _exportEvent.emit(json)
        }
    }

    fun importBackup(json: String) {
        viewModelScope.launch {
            val result = importBackupUseCase(json)
            _importResult.emit(result)
        }
    }
}

class SettingsViewModelFactory(
    private val getSendWhileRoamingUseCase: GetSendWhileRoamingUseCase,
    private val setSendWhileRoamingUseCase: SetSendWhileRoamingUseCase,
    private val getDefaultPrefixUseCase: GetDefaultPrefixUseCase,
    private val setDefaultPrefixUseCase: SetDefaultPrefixUseCase,
    private val getForwardPrefixUseCase: GetForwardPrefixUseCase,
    private val setForwardPrefixUseCase: SetForwardPrefixUseCase,
    private val getShowNotificationsUseCase: GetShowNotificationsUseCase,
    private val setShowNotificationsUseCase: SetShowNotificationsUseCase,
    private val exportBackupUseCase: ExportBackupUseCase,
    private val importBackupUseCase: ImportBackupUseCase,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SettingsViewModel(
            getSendWhileRoamingUseCase = getSendWhileRoamingUseCase,
            setSendWhileRoamingUseCase = setSendWhileRoamingUseCase,
            getDefaultPrefixUseCase = getDefaultPrefixUseCase,
            setDefaultPrefixUseCase = setDefaultPrefixUseCase,
            getForwardPrefixUseCase = getForwardPrefixUseCase,
            setForwardPrefixUseCase = setForwardPrefixUseCase,
            getShowNotificationsUseCase = getShowNotificationsUseCase,
            setShowNotificationsUseCase = setShowNotificationsUseCase,
            exportBackupUseCase = exportBackupUseCase,
            importBackupUseCase = importBackupUseCase,
        ) as T
    }
}
