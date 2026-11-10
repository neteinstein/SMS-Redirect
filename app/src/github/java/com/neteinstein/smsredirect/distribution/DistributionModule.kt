package com.neteinstein.smsredirect.distribution

import android.content.Context
import com.neteinstein.smsredirect.BuildConfig
import com.neteinstein.smsredirect.data.update.AppUpdateServices
import com.neteinstein.smsredirect.domain.model.AppFeatures

/**
 * GitHub build: every feature, plus an update check against this repository's releases.
 *
 * The self-update is off in debug builds: they carry the `.test` applicationId suffix, so a
 * release APK could never install over them.
 */
object DistributionModule {
    const val GITHUB_REPOSITORY = "neteinstein/SMS-Redirect"

    val features: AppFeatures = AppFeatures.ALL.copy(selfUpdate = !BuildConfig.DEBUG)

    fun createAppUpdateServices(context: Context): AppUpdateServices? {
        if (!features.selfUpdate) return null
        return AppUpdateServices(
            releaseSource = GitHubReleaseSource(GITHUB_REPOSITORY),
            installer = ApkUpdateInstaller(context.applicationContext),
        )
    }
}
