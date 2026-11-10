package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.model.AppRelease
import com.neteinstein.smsredirect.domain.update.AppUpdateInstallResult
import com.neteinstein.smsredirect.domain.update.AppUpdateInstaller

class FakeAppUpdateInstaller(
    var result: AppUpdateInstallResult = AppUpdateInstallResult.InstallerOpened,
    private val progress: List<Int?> = emptyList(),
) : AppUpdateInstaller {
    val installed = mutableListOf<AppRelease>()

    override suspend fun downloadAndInstall(
        release: AppRelease,
        onProgress: (Int?) -> Unit,
    ): AppUpdateInstallResult {
        installed += release
        progress.forEach(onProgress)
        return result
    }
}
