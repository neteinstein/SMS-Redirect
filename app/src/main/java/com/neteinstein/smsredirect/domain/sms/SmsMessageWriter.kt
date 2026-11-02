package com.neteinstein.smsredirect.domain.sms

import com.neteinstein.smsredirect.domain.model.SmsMessageToRestore
import com.neteinstein.smsredirect.domain.model.StoredSms

/**
 * Writes SMS into the device's message store. The write-side twin of [SmsMessageSource].
 *
 * Android only lets the *default SMS app* write to `content://sms`; any other app's insert is
 * silently dropped. Callers must check [canWrite] and treat `false` as "stop", not as a per-message
 * failure -- it means the user switched the default SMS app away.
 */
interface SmsMessageWriter {
    fun canWrite(): Boolean

    /** Messages whose timestamp is within [toleranceMs] of [dateEpochMs], for duplicate checks. */
    suspend fun findAround(
        dateEpochMs: Long,
        toleranceMs: Long,
    ): List<StoredSms>

    /** Returns false when the provider rejected the row. [read] is false only for new arrivals. */
    suspend fun insert(
        message: SmsMessageToRestore,
        read: Boolean = true,
    ): Boolean
}

/**
 * Whether this app currently holds Android's SMS role. Behind an interface so the Backup screen's
 * ViewModel can be tested without a `Context`.
 */
interface DefaultSmsAppChecker {
    fun isDefaultSmsApp(): Boolean
}
