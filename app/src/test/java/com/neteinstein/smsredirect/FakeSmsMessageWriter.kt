package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.model.SmsMessageToRestore
import com.neteinstein.smsredirect.domain.model.StoredSms
import com.neteinstein.smsredirect.domain.sms.SmsMessageWriter

class FakeSmsMessageWriter(
    initial: List<StoredSms> = emptyList(),
    var isDefaultSmsApp: Boolean = true,
) : SmsMessageWriter {
    val stored = initial.toMutableList()
    val inserted = mutableListOf<SmsMessageToRestore>()

    /** Scripts the provider rejecting every insert while the role is still held. */
    var rejectInserts: Boolean = false

    /** Simulates the user switching the default SMS app away after this many inserts. */
    var loseRoleAfterInserts: Int? = null

    override fun canWrite(): Boolean = isDefaultSmsApp

    override suspend fun findAround(
        dateEpochMs: Long,
        toleranceMs: Long,
    ): List<StoredSms> = stored.filter { it.dateEpochMs in (dateEpochMs - toleranceMs)..(dateEpochMs + toleranceMs) }

    override suspend fun insert(
        message: SmsMessageToRestore,
        read: Boolean,
    ): Boolean {
        if (loseRoleAfterInserts == inserted.size) isDefaultSmsApp = false
        if (!isDefaultSmsApp || rejectInserts) return false
        inserted += message
        stored += StoredSms(message.address, message.body, message.dateEpochMs)
        return true
    }
}
