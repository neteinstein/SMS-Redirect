package com.neteinstein.smsredirect.presentation.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neteinstein.smsredirect.domain.model.SmsBackupProgress
import com.neteinstein.smsredirect.domain.model.SmsBackupSettings
import com.neteinstein.smsredirect.domain.model.SmsRestoreState
import com.neteinstein.smsredirect.domain.scheduling.SmsBackupScheduler
import com.neteinstein.smsredirect.domain.scheduling.SmsRestoreScheduler
import com.neteinstein.smsredirect.domain.sms.DefaultSmsAppChecker
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
    val restoreState: SmsRestoreState = SmsRestoreState.Idle,
    val isDefaultSmsApp: Boolean = false,
) {
    val hasCredentials: Boolean get() = settings.hasCredentials

    val isRestoring: Boolean get() = restoreState is SmsRestoreState.Running

    /**
     * The last step of a restore, and the one most easily forgotten: while this app holds the SMS
     * role the user's usual messaging app receives nothing and MMS cannot arrive at all. Shown
     * whenever the role is held and no restore needs it.
     */
    val shouldSwitchBack: Boolean get() = isDefaultSmsApp && !isRestoring
}

class BackupViewModel(
    getSmsBackupSettingsUseCase: GetSmsBackupSettingsUseCase,
    private val setBackupSubjectTemplateUseCase: SetBackupSubjectTemplateUseCase,
    private val setBackupGmailLabelUseCase: SetBackupGmailLabelUseCase,
    private val setBackupPreviousEnabledUseCase: SetBackupPreviousEnabledUseCase,
    private val setBackupEnabledUseCase: SetBackupEnabledUseCase,
    private val smsBackupScheduler: SmsBackupScheduler,
    private val smsRestoreScheduler: SmsRestoreScheduler,
    private val defaultSmsAppChecker: DefaultSmsAppChecker,
) : ViewModel() {
    private val isDefaultSmsApp = MutableStateFlow(defaultSmsAppChecker.isDefaultSmsApp())

    /** Hides a finished/failed restore's result card once the user has acknowledged it. */
    private val restoreResultDismissed = MutableStateFlow(false)

    val uiState: StateFlow<BackupUiState> =
        combine(
            getSmsBackupSettingsUseCase(),
            smsBackupScheduler.observeProgress(),
            smsRestoreScheduler.observeState(),
            isDefaultSmsApp,
            restoreResultDismissed,
        ) { settings, progress, restoreState, isDefault, dismissed ->
            val visibleRestoreState =
                if (dismissed && (restoreState is SmsRestoreState.Finished || restoreState is SmsRestoreState.Failed)) {
                    SmsRestoreState.Idle
                } else {
                    restoreState
                }
            BackupUiState(settings, progress, visibleRestoreState, isDefault)
        }
            // Eager, matching every other ViewModel in this codebase: onToggleBackupEnabled() and
            // onBackupPreviousChanged() read uiState.value synchronously, so it must already
            // reflect real settings by the time the screen is interacted with, not just once
            // something starts collecting it.
            .stateIn(viewModelScope, SharingStarted.Eagerly, BackupUiState())

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

    /**
     * Set when the screen must ask Android for the SMS role. The request itself needs an
     * Activity, so the screen launches it and reports back through [onDefaultSmsRoleResult].
     */
    private val _requestDefaultSmsRole = MutableStateFlow(false)
    val requestDefaultSmsRole: StateFlow<Boolean> = _requestDefaultSmsRole.asStateFlow()

    /** Set when the user declined the SMS role, so the screen can explain why restore needs it. */
    private val _defaultSmsRoleDenied = MutableStateFlow(false)
    val defaultSmsRoleDenied: StateFlow<Boolean> = _defaultSmsRoleDenied.asStateFlow()

    /** The user confirmed the restore explanation dialog. */
    fun onRestoreConfirmed() {
        _defaultSmsRoleDenied.value = false
        if (refreshDefaultSmsApp()) {
            startRestore()
        } else {
            _requestDefaultSmsRole.value = true
        }
    }

    fun onDefaultSmsRoleRequestLaunched() {
        _requestDefaultSmsRole.value = false
    }

    fun onDefaultSmsRoleResult() {
        if (refreshDefaultSmsApp()) {
            startRestore()
        } else {
            _defaultSmsRoleDenied.value = true
        }
    }

    fun onCancelRestore() {
        smsRestoreScheduler.cancelRestore()
    }

    fun onRestoreResultDismissed() {
        restoreResultDismissed.value = true
    }

    /**
     * Re-reads who holds the SMS role. Called on every resume: the user hands the role back in
     * system Settings, outside this app, so there is no callback to observe.
     */
    fun refreshDefaultSmsApp(): Boolean = defaultSmsAppChecker.isDefaultSmsApp().also { isDefaultSmsApp.value = it }

    private fun startRestore() {
        restoreResultDismissed.value = false
        smsRestoreScheduler.requestRestore()
    }
}

class BackupViewModelFactory(
    private val getSmsBackupSettingsUseCase: GetSmsBackupSettingsUseCase,
    private val setBackupSubjectTemplateUseCase: SetBackupSubjectTemplateUseCase,
    private val setBackupGmailLabelUseCase: SetBackupGmailLabelUseCase,
    private val setBackupPreviousEnabledUseCase: SetBackupPreviousEnabledUseCase,
    private val setBackupEnabledUseCase: SetBackupEnabledUseCase,
    private val smsBackupScheduler: SmsBackupScheduler,
    private val smsRestoreScheduler: SmsRestoreScheduler,
    private val defaultSmsAppChecker: DefaultSmsAppChecker,
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
            smsRestoreScheduler,
            defaultSmsAppChecker,
        ) as T
}
