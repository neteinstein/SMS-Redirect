package com.neteinstein.smsredirect.data.hibernation

import android.content.Context
import com.neteinstein.smsredirect.domain.hibernation.UnusedAppRestrictionChecker
import com.neteinstein.smsredirect.domain.hibernation.UnusedAppRestrictionStatus

/**
 * Reads the "Remove permissions and free up space" / "Manage app if unused" switch for this
 * package. `isAutoRevokeWhitelisted` is true when the user switched it off (the app is exempt).
 */
class AndroidUnusedAppRestrictionChecker(
    private val context: Context,
) : UnusedAppRestrictionChecker {
    override fun status(): UnusedAppRestrictionStatus =
        try {
            if (context.packageManager.isAutoRevokeWhitelisted) {
                UnusedAppRestrictionStatus.DISABLED
            } else {
                UnusedAppRestrictionStatus.ENABLED
            }
        } catch (e: RuntimeException) {
            UnusedAppRestrictionStatus.UNKNOWN
        }
}
