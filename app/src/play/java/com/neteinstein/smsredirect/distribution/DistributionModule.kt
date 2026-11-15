package com.neteinstein.smsredirect.distribution

import android.content.Context
import com.neteinstein.smsredirect.data.update.AppUpdateServices
import com.neteinstein.smsredirect.domain.model.AppFeatures

/**
 * Google Play build: the full messaging app and every tool, declared under Play's "Default SMS
 * handler" core use case.
 *
 * - SMS permissions come only from the default SMS role: the role is requested before any of
 *   them, and a user who declines gets no SMS permission prompt at all.
 * - No self-update: Play's Device and Network Abuse policy forbids an app from updating itself
 *   by any route other than Play. This source set deliberately contains no update code at all.
 */
object DistributionModule {
    val features: AppFeatures = AppFeatures.PLAY

    @Suppress("UNUSED_PARAMETER")
    fun createAppUpdateServices(context: Context): AppUpdateServices? = null
}
