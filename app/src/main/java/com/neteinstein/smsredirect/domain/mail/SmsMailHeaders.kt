package com.neteinstein.smsredirect.domain.mail

/**
 * The machine-readable part of the backup format.
 *
 * Restore-critical data lives in custom `X-` headers rather than in the body, deliberately:
 * - The body is what the user reads in Gmail. It stays a clean, human-readable transcript instead
 *   of growing a block of key/value noise.
 * - Headers are unambiguous to parse. The body's "From: Jane (+351…)" line is not -- a contact
 *   name can itself contain parentheses -- and its "Received:" line is formatted in the device's
 *   time zone at second precision, which loses the millisecond timestamp a restore needs to
 *   rebuild the message's exact identity ([com.neteinstein.smsredirect.domain.model.SmsBackupKey]).
 * - Gmail preserves custom headers verbatim on IMAP APPEND, returns them on FETCH, and keeps them
 *   in Google Takeout's mbox export, so the backup stays restorable outside this app too.
 * - Headers are independent of the user-editable subject template.
 *
 * The body is still parsed as a fallback, for backups written before these headers existed.
 *
 * Bump [CURRENT_FORMAT] on any incompatible change and keep the parser accepting older values.
 */
object SmsMailHeaders {
    const val FORMAT = "X-SMS-Toolbox-Format"
    const val ADDRESS = "X-SMS-Address"
    const val DATE = "X-SMS-Date"
    const val DATE_SENT = "X-SMS-Date-Sent"
    const val TYPE = "X-SMS-Type"

    const val CURRENT_FORMAT = "1"

    /**
     * RFC 6761 reserved TLD: guaranteed never resolvable, so a synthesized id can never be
     * mistaken for a real address or trigger a delivery attempt. Also how a restore recognises
     * which mails in the label are ours.
     */
    const val ID_DOMAIN = "sms-toolbox.invalid"
    const val MESSAGE_ID_PREFIX = "<sms-"
    const val THREAD_ID_PREFIX = "<sms-thread-"
}
