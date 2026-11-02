package com.neteinstein.smsredirect.domain.model

/**
 * Which side of the conversation an SMS sits on. Recorded in every backup (see
 * [com.neteinstein.smsredirect.domain.mail.SmsMailHeaders.TYPE]) so a restore can put the message
 * back in the right box.
 *
 * Only [INBOX] is backed up today, but the format carries the type from day one so that sent
 * messages, once supported, are restorable without a second format revision.
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
