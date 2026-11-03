package com.neteinstein.smsredirect.domain.sms

import com.neteinstein.smsredirect.domain.model.SmsMessageToBackup

/**
 * Reads the device's received and sent SMS. Mirrors the `domain/contacts/ContactNameResolver`
 * split: the interface here, the `ContentResolver` query in `data/sms/`.
 *
 * Each message carries its [com.neteinstein.smsredirect.domain.model.SmsBox]. Drafts, the outbox
 * and failed sends are never returned. MMS is out of scope.
 */
interface SmsMessageSource {
    /** Number of eligible received and sent messages, used for the "X of Y" denominator. */
    suspend fun count(sinceEpochMs: Long): Int

    /**
     * Reads up to [limit] messages ordered by (date, _id), starting strictly after the given
     * cursor. Paging on the composite key rather than an offset keeps the scan stable while new
     * messages arrive mid-backfill.
     */
    suspend fun readBatch(
        sinceEpochMs: Long,
        afterDateEpochMs: Long,
        afterId: Long,
        limit: Int,
    ): List<SmsMessageToBackup>
}
