package com.neteinstein.smsredirect.distribution

import android.content.Context
import com.neteinstein.smsredirect.data.update.AppUpdateServices
import com.neteinstein.smsredirect.domain.model.AppFeatures

/**
 * Google Play build: Backup and Restore only.
 *
 * - No forwarding or scheduling, so `SEND_SMS` is not declared (see this flavor's manifest).
 * - No self-update: Play's Device and Network Abuse policy forbids an app from updating itself
 *   by any route other than Play. This source set deliberately contains no update code at all.
 */
object DistributionModule {
    val features: AppFeatures = AppFeatures.BACKUP_ONLY

    @Suppress("UNUSED_PARAMETER")
    fun createAppUpdateServices(context: Context): AppUpdateServices? = null
}
