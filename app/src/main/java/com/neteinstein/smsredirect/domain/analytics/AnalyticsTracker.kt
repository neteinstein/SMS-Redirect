package com.neteinstein.smsredirect.domain.analytics

/**
 * Abstraction over the analytics backend used to report anonymous, aggregate
 * app-usage events (e.g. a rule was created, an SMS was forwarded).
 *
 * Implementations MUST NOT log personally identifiable information such as
 * phone numbers, contact names or message contents. Event names and
 * parameters should only describe *what action happened*, not *who* it
 * happened to or *what data* was involved.
 */
interface AnalyticsTracker {
    fun logEvent(
        name: String,
        params: Map<String, Any> = emptyMap(),
    )
}

/**
 * No-op implementation used as the default value for use cases and classes
 * that accept an [AnalyticsTracker], so callers (and tests) that don't need
 * analytics don't have to provide one explicitly.
 */
object NoOpAnalyticsTracker : AnalyticsTracker {
    override fun logEvent(
        name: String,
        params: Map<String, Any>,
    ) = Unit
}
