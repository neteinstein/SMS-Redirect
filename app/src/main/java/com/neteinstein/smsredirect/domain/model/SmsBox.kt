package com.neteinstein.smsredirect.domain.model

/**
 * Which side of the conversation an SMS sits on. Recorded in every backup (see
 * [com.neteinstein.smsredirect.domain.mail.SmsMailHeaders.TYPE]) so a restore can put the message
 * back in the right box.
 *
 * Both boxes are backed up. Drafts, the outbox, queued and failed messages are not: they were
 * never delivered, and a restore has no box to put them back in.
 */
enum class SmsBox(
    val headerValue: String,
) {
    INBOX("inbox"),
    SENT("sent"),
    ;

    companion object {
        fun fromHeaderValue(value: String?): SmsBox? = entries.firstOrNull { it.headerValue.equals(value?.trim(), ignoreCase = true) }
    }
}
