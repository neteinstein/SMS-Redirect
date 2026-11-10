package com.neteinstein.smsredirect.data.update

import com.neteinstein.smsredirect.domain.update.AppReleaseSource
import com.neteinstein.smsredirect.domain.update.AppUpdateInstaller

/** The flavor-provided pieces of the self-update feature. Only the github build has them. */
data class AppUpdateServices(
    val releaseSource: AppReleaseSource,
    val installer: AppUpdateInstaller,
)
