package com.neteinstein.smsredirect.presentation.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neteinstein.smsredirect.domain.model.AppRelease
import com.neteinstein.smsredirect.domain.update.AppUpdateInstallResult
import com.neteinstein.smsredirect.domain.update.AppUpdateInstaller
import com.neteinstein.smsredirect.domain.usecase.AppUpdateCheckResult
import com.neteinstein.smsredirect.domain.usecase.CheckForAppUpdateUseCase
import com.neteinstein.smsredirect.domain.usecase.GetAvailableAppUpdateUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the "update available" banner on the home screen and the Updates section in Settings.
 * Scoped to the activity so both read one state. Creating it runs the launch-time check, which
 * the use case throttles to once a day.
 */
class AppUpdateViewModel(
    private val currentVersionName: String,
    getAvailableAppUpdateUseCase: GetAvailableAppUpdateUseCase,
    private val checkForAppUpdateUseCase: CheckForAppUpdateUseCase,
    private val installer: AppUpdateInstaller,
) : ViewModel() {
    private val checkStatus = MutableStateFlow(UpdateCheckStatus.Idle)
    private val installStatus = MutableStateFlow<UpdateInstallStatus>(UpdateInstallStatus.Idle)

    val uiState: StateFlow<AppUpdateUiState> =
        combine(getAvailableAppUpdateUseCase(), checkStatus, installStatus) { available, check, install ->
            AppUpdateUiState(
                currentVersionName = currentVersionName,
                availableUpdate = available,
                checkStatus = check,
                installStatus = install,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUpdateUiState(currentVersionName))

    init {
        viewModelScope.launch { checkForAppUpdateUseCase(force = false) }
    }

    /** The user's "Check now": ignores the daily throttle. */
    fun checkNow() {
        if (checkStatus.value == UpdateCheckStatus.Checking) return
        viewModelScope.launch {
            checkStatus.value = UpdateCheckStatus.Checking
            checkStatus.value =
                when (checkForAppUpdateUseCase(force = true)) {
                    is AppUpdateCheckResult.UpdateAvailable -> UpdateCheckStatus.Idle
                    AppUpdateCheckResult.UpToDate, AppUpdateCheckResult.Skipped -> UpdateCheckStatus.UpToDate
                    AppUpdateCheckResult.Failed -> UpdateCheckStatus.Failed
                }
        }
    }

    fun installUpdate() {
        val release = uiState.value.availableUpdate ?: return
        if (installStatus.value is UpdateInstallStatus.Downloading) return
        viewModelScope.launch {
            installStatus.value = UpdateInstallStatus.Downloading(percent = null)
            val result =
                installer.downloadAndInstall(release) { percent ->
                    installStatus.value = UpdateInstallStatus.Downloading(percent)
                }
            installStatus.value =
                when (result) {
                    AppUpdateInstallResult.InstallerOpened -> UpdateInstallStatus.InstallerOpened
                    AppUpdateInstallResult.NeedsInstallPermission -> UpdateInstallStatus.NeedsPermission
                    AppUpdateInstallResult.NoApk, AppUpdateInstallResult.Failed -> UpdateInstallStatus.Failed
                }
        }
    }
}

data class AppUpdateUiState(
    val currentVersionName: String,
    /** A release newer than the running app, or null. */
    val availableUpdate: AppRelease? = null,
    val checkStatus: UpdateCheckStatus = UpdateCheckStatus.Idle,
    val installStatus: UpdateInstallStatus = UpdateInstallStatus.Idle,
)

enum class UpdateCheckStatus { Idle, Checking, UpToDate, Failed }

sealed interface UpdateInstallStatus {
    data object Idle : UpdateInstallStatus

    /** [percent] is null while the download size is not known yet. */
    data class Downloading(
        val percent: Int?,
    ) : UpdateInstallStatus

    data object InstallerOpened : UpdateInstallStatus

    /** The user has to allow "Install unknown apps" for SMS Toolbox, then try again. */
    data object NeedsPermission : UpdateInstallStatus

    data object Failed : UpdateInstallStatus
}

class AppUpdateViewModelFactory(
    private val currentVersionName: String,
    private val getAvailableAppUpdateUseCase: GetAvailableAppUpdateUseCase,
    private val checkForAppUpdateUseCase: CheckForAppUpdateUseCase,
    private val installer: AppUpdateInstaller,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AppUpdateViewModel(
            currentVersionName = currentVersionName,
            getAvailableAppUpdateUseCase = getAvailableAppUpdateUseCase,
            checkForAppUpdateUseCase = checkForAppUpdateUseCase,
            installer = installer,
        ) as T
    }
}
