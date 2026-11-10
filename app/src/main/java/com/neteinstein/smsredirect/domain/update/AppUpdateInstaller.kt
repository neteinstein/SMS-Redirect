package com.neteinstein.smsredirect.domain.update

import com.neteinstein.smsredirect.domain.model.AppRelease

/** Downloads a release's APK and hands it to the system package installer. */
interface AppUpdateInstaller {
    /**
     * Downloads [release]'s APK, reporting [onProgress] as a percentage (0-100, or null while the
     * size is unknown), then opens the system installer, which asks the user to confirm.
     */
    suspend fun downloadAndInstall(
        release: AppRelease,
        onProgress: (Int?) -> Unit,
    ): AppUpdateInstallResult
}

sealed interface AppUpdateInstallResult {
    /** The system installer is showing; Android takes it from here. */
    data object InstallerOpened : AppUpdateInstallResult

    /**
     * The user has not allowed this app to install apps. The system screen to allow it was
     * opened; the user retries once allowed.
     */
    data object NeedsInstallPermission : AppUpdateInstallResult

    /** The release has no APK attached. */
    data object NoApk : AppUpdateInstallResult

    /** Download failed, or the file was not an update of this app. */
    data object Failed : AppUpdateInstallResult
}
