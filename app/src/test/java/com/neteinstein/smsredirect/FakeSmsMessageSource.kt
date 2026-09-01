package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.model.SmsMessageToBackup
import com.neteinstein.smsredirect.domain.sms.SmsMessageSource

/**
 * Honours the (date, id) keyset cursor and the limit, so paging is genuinely exercised rather
 * than every call returning the whole list.
 */
class FakeSmsMessageSource(
    var messages: List<SmsMessageToBackup> = emptyList(),
) : SmsMessageSource {
    override suspend fun count(sinceEpochMs: Long): Int = eligible(sinceEpochMs).size

    override suspend fun readBatch(
        sinceEpochMs: Long,
        afterDateEpochMs: Long,
        afterId: Long,
        limit: Int,
    ): List<SmsMessageToBackup> =
        eligible(sinceEpochMs)
            .filter { it.dateEpochMs > afterDateEpochMs || (it.dateEpochMs == afterDateEpochMs && it.providerId > afterId) }
            .sortedWith(compareBy({ it.dateEpochMs }, { it.providerId }))
            .take(limit)

    private fun eligible(sinceEpochMs: Long) = messages.filter { sinceEpochMs <= 0 || it.dateEpochMs >= sinceEpochMs }
}
