package com.neteinstein.smsredirect.presentation.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neteinstein.smsredirect.domain.model.SmsBackupProgress
import com.neteinstein.smsredirect.domain.model.SmsBackupSettings
import com.neteinstein.smsredirect.domain.scheduling.SmsBackupScheduler
import com.neteinstein.smsredirect.domain.usecase.GetSmsBackupSettingsUseCase
import com.neteinstein.smsredirect.domain.usecase.SetBackupEnabledUseCase
import com.neteinstein.smsredirect.domain.usecase.SetBackupGmailLabelUseCase
import com.neteinstein.smsredirect.domain.usecase.SetBackupPreviousEnabledUseCase
import com.neteinstein.smsredirect.domain.usecase.SetBackupSubjectTemplateUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BackupUiState(
    val settings: SmsBackupSettings = SmsBackupSettings(),
    val progress: SmsBackupProgress? = null,
) {
    val hasCredentials: Boolean get() = settings.hasCredentials
}

class BackupViewModel(
    getSmsBackupSettingsUseCase: GetSmsBackupSettingsUseCase,
    private val setBackupSubjectTemplateUseCase: SetBackupSubjectTemplateUseCase,
    private val setBackupGmailLabelUseCase: SetBackupGmailLabelUseCase,
    private val setBackupPreviousEnabledUseCase: SetBackupPreviousEnabledUseCase,
    private val setBackupEnabledUseCase: SetBackupEnabledUseCase,
    private val smsBackupScheduler: SmsBackupScheduler,
) : ViewModel() {
    val uiState: StateFlow<BackupUiState> =
        combine(
            getSmsBackupSettingsUseCase(),
            smsBackupScheduler.observeProgress(),
        ) { settings, progress -> BackupUiState(settings, progress) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    /** Set when the user taps Enable without usable credentials, so the UI can say why. */
    private val _needsCredentials = MutableStateFlow(false)
    val needsCredentials: StateFlow<Boolean> = _needsCredentials.asStateFlow()

    fun onSubjectTemplateSaved(template: String) {
        viewModelScope.launch { setBackupSubjectTemplateUseCase(template) }
    }

    fun onGmailLabelSaved(label: String) {
        viewModelScope.launch { setBackupGmailLabelUseCase(label) }
    }

    fun onBackupPreviousChanged(enabled: Boolean) {
        viewModelScope.launch {
            setBackupPreviousEnabledUseCase(enabled)
            // The watermark is otherwise only recomputed when backup transitions off -> on, so
            // flipping this while backup is already running would silently leave the old
            // watermark in place. Recompute it for the new choice and re-scan under it.
            val current = uiState.value.settings
            if (current.backupEnabled) {
                setBackupEnabledUseCase(enabled = true, backupPrevious = enabled)
                smsBackupScheduler.requestBackup()
            }
        }
    }

    fun onToggleBackupEnabled() {
        val current = uiState.value.settings
        if (!current.backupEnabled && !current.hasCredentials) {
            _needsCredentials.value = true
            return
        }
        _needsCredentials.value = false

        val enable = !current.backupEnabled
        viewModelScope.launch {
            setBackupEnabledUseCase(enable, current.backupPreviousEnabled)
            if (enable) smsBackupScheduler.requestBackup() else smsBackupScheduler.cancelBackup()
        }
    }

    fun onCredentialsWarningShown() {
        _needsCredentials.value = false
    }
}

class BackupViewModelFactory(
    private val getSmsBackupSettingsUseCase: GetSmsBackupSettingsUseCase,
    private val setBackupSubjectTemplateUseCase: SetBackupSubjectTemplateUseCase,
    private val setBackupGmailLabelUseCase: SetBackupGmailLabelUseCase,
    private val setBackupPreviousEnabledUseCase: SetBackupPreviousEnabledUseCase,
    private val setBackupEnabledUseCase: SetBackupEnabledUseCase,
    private val smsBackupScheduler: SmsBackupScheduler,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        BackupViewModel(
            getSmsBackupSettingsUseCase,
            setBackupSubjectTemplateUseCase,
            setBackupGmailLabelUseCase,
            setBackupPreviousEnabledUseCase,
            setBackupEnabledUseCase,
            smsBackupScheduler,
        ) as T
}
