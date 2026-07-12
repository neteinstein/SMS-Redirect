package com.neteinstein.smsredirect.data.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.neteinstein.smsredirect.domain.analytics.AnalyticsTracker

/**
 * [AnalyticsTracker] implementation backed by Firebase Analytics.
 *
 * Only the event name and parameters explicitly passed by callers are sent;
 * this class performs no automatic collection of personal data.
 */
class FirebaseAnalyticsTracker(
    context: Context,
) : AnalyticsTracker {
    private val firebaseAnalytics = FirebaseAnalytics.getInstance(context)

    override fun logEvent(
        name: String,
        params: Map<String, Any>,
    ) {
        val bundle = Bundle()
        params.forEach { (key, value) ->
            when (value) {
                is String -> bundle.putString(key, value)
                is Int -> bundle.putInt(key, value)
                is Long -> bundle.putLong(key, value)
                is Double -> bundle.putDouble(key, value)
                is Boolean -> bundle.putString(key, value.toString())
                else -> bundle.putString(key, value.toString())
            }
        }
        firebaseAnalytics.logEvent(name, bundle)
    }
}
