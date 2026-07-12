package com.neteinstein.smsredirect.data.analytics

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.neteinstein.smsredirect.domain.analytics.CrashReporter

/**
 * [CrashReporter] implementation backed by Firebase Crashlytics.
 */
class FirebaseCrashReporter : CrashReporter {
    private val crashlytics = FirebaseCrashlytics.getInstance()

    override fun recordException(throwable: Throwable) {
        crashlytics.recordException(throwable)
    }

    override fun log(message: String) {
        crashlytics.log(message)
    }
}
