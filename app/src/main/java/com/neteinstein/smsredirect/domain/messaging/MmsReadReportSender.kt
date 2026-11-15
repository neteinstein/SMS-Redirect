package com.neteinstein.smsredirect.domain.messaging

/**
 * Sends M-Read-Rec.ind for the unread MMS in a conversation whose senders asked for a read report.
 * Called just before the conversation is marked read, and only when the user turned MMS read
 * reports on. Implemented over `SmsManager` in `data/`.
 */
fun interface MmsReadReportSender {
    suspend fun sendReadReports(threadId: Long)
}
