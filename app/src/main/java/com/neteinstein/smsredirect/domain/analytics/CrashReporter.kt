package com.neteinstein.smsredirect.domain.analytics

/**
 * Abstraction over the crash-reporting backend, used to record caught
 * (non-fatal) exceptions and breadcrumb-style log lines that help diagnose
 * crashes. Fatal (uncaught) crashes are reported automatically once the
 * underlying SDK is initialized, without any code changes needed here.
 */
interface CrashReporter {
    fun recordException(throwable: Throwable)

    fun log(message: String)
}

/**
 * No-op implementation used as the default value for classes that accept a
 * [CrashReporter], so callers (and tests) that don't need crash reporting
 * don't have to provide one explicitly.
 */
object NoOpCrashReporter : CrashReporter {
    override fun recordException(throwable: Throwable) = Unit

    override fun log(message: String) = Unit
}
