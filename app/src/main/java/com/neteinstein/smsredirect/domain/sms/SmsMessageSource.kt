package com.neteinstein.smsredirect.domain.sms

import com.neteinstein.smsredirect.domain.model.SmsMessageToBackup

/**
 * Reads the device's SMS inbox. Mirrors the `domain/contacts/ContactNameResolver` split: the
 * interface here, the `ContentResolver` query in `data/sms/`.
 *
 * Inbox only for now. The live trigger is an incoming-message broadcast, so nothing would prompt
 * a scan when the user *sends* a message; including sent messages would back up old ones and then
 * silently lag on new ones. Sent-message support is a clean follow-up.
 */
interface SmsMessageSource {
    /** Total inbox size, used for the "X of Y" denominator. */
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
