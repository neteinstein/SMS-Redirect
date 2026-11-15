package com.neteinstein.smsredirect.presentation.restore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neteinstein.smsredirect.domain.model.SmsBackupSettings
import com.neteinstein.smsredirect.domain.model.SmsRestoreState
import com.neteinstein.smsredirect.domain.scheduling.SmsRestoreScheduler
import com.neteinstein.smsredirect.domain.sms.DefaultSmsAppChecker
import com.neteinstein.smsredirect.domain.usecase.GetSmsBackupSettingsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class RestoreUiState(
    val settings: SmsBackupSettings = SmsBackupSettings(),
    val restoreState: SmsRestoreState = SmsRestoreState.Idle,
    val isDefaultSmsApp: Boolean = false,
) {
    val hasCredentials: Boolean get() = settings.hasCredentials

    val isRestoring: Boolean get() = restoreState is SmsRestoreState.Running
}

/**
 * The Restore tab. Writing to the SMS store needs the default SMS role, which SMS Toolbox -- a
 * messaging app -- normally already holds. When it does not, the screen asks for it through the
 * activity's role flow first and only then calls [onRestoreConfirmed].
 */
class RestoreViewModel(
    getSmsBackupSettingsUseCase: GetSmsBackupSettingsUseCase,
    private val smsRestoreScheduler: SmsRestoreScheduler,
    private val defaultSmsAppChecker: DefaultSmsAppChecker,
) : ViewModel() {
    private val isDefaultSmsApp = MutableStateFlow(defaultSmsAppChecker.isDefaultSmsApp())

    /** Hides a finished/failed restore's result card once the user has acknowledged it. */
    private val restoreResultDismissed = MutableStateFlow(false)

    /** Set when a restore was attempted without the SMS role, so the screen can say why. */
    private val _defaultSmsRoleDenied = MutableStateFlow(false)
    val defaultSmsRoleDenied: StateFlow<Boolean> = _defaultSmsRoleDenied.asStateFlow()

    val uiState: StateFlow<RestoreUiState> =
        combine(
            getSmsBackupSettingsUseCase(),
            smsRestoreScheduler.observeState(),
            isDefaultSmsApp,
            restoreResultDismissed,
        ) { settings, restoreState, isDefault, dismissed ->
            val visibleRestoreState =
                if (dismissed && (restoreState is SmsRestoreState.Finished || restoreState is SmsRestoreState.Failed)) {
                    SmsRestoreState.Idle
                } else {
                    restoreState
                }
            RestoreUiState(settings, visibleRestoreState, isDefault)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, RestoreUiState())

    /** Starts the restore if the SMS role is held; otherwise records that it is missing. */
    fun onRestoreConfirmed() {
        if (refreshDefaultSmsApp()) {
            _defaultSmsRoleDenied.value = false
            restoreResultDismissed.value = false
            smsRestoreScheduler.requestRestore()
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

    /** Re-reads who holds the SMS role; the user can change it in system Settings at any time. */
    fun refreshDefaultSmsApp(): Boolean = defaultSmsAppChecker.isDefaultSmsApp().also { isDefaultSmsApp.value = it }
}

class RestoreViewModelFactory(
    private val getSmsBackupSettingsUseCase: GetSmsBackupSettingsUseCase,
    private val smsRestoreScheduler: SmsRestoreScheduler,
    private val defaultSmsAppChecker: DefaultSmsAppChecker,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        RestoreViewModel(getSmsBackupSettingsUseCase, smsRestoreScheduler, defaultSmsAppChecker) as T
}
