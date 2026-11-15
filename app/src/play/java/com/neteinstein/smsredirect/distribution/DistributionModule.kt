package com.neteinstein.smsredirect.distribution

import android.content.Context
import com.neteinstein.smsredirect.data.update.AppUpdateServices
import com.neteinstein.smsredirect.domain.model.AppFeatures

/**
 * Google Play build: identical to the GitHub build except that it has no self-update. Play's
 * Device and Network Abuse policy forbids an app from updating itself by any route other than
 * Play, so this source set deliberately contains no update code at all.
 */
object DistributionModule {
    val features: AppFeatures = AppFeatures.PLAY

    @Suppress("UNUSED_PARAMETER")
    fun createAppUpdateServices(context: Context): AppUpdateServices? = null
}
