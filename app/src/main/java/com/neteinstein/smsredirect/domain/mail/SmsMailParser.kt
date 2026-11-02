package com.neteinstein.smsredirect.domain.mail

import com.neteinstein.smsredirect.domain.model.SmsBox
import com.neteinstein.smsredirect.domain.model.SmsMessageToRestore

/**
 * Turns a mail from the backup label back into an SMS -- the inverse of [SmsMailFactory].
 *
 * Two formats are understood:
 * - **Current** (`X-SMS-Toolbox-Format` present): address, box and millisecond timestamps come
 *   from the [SmsMailHeaders]. Exact, so the restored message gets back its original identity.
 * - **Legacy** (backups written before those headers existed): the address is recovered from the
 *   body's `From:` line and the timestamp from the `Date:` header, which only has second
 *   precision. Every legacy backup came from the inbox -- sent messages were never backed up --
 *   so the box is always [SmsBox.INBOX].
 *
 * Returns null for anything that is not one of ours, so a label the user also files other mail
 * under is safe to restore from.
 */
class SmsMailParser {
    fun parse(mail: BackedUpMail): SmsMessageToRestore? {
        if (!isOurs(mail.messageId)) return null

        val text = mail.bodyText?.replace("\r\n", "\n") ?: return null
        val separator = text.indexOf(PREAMBLE_END)
        if (separator < 0) return null
        val preamble = text.substring(0, separator)
        val body = text.substring(separator + PREAMBLE_END.length)
        if (body.isBlank()) return null

        return if (mail.header(SmsMailHeaders.FORMAT) != null) {
            parseCurrent(mail, body)
        } else {
            parseLegacy(mail, preamble, body)
        }
    }

    private fun parseCurrent(
        mail: BackedUpMail,
        body: String,
    ): SmsMessageToRestore? {
        val address = mail.header(SmsMailHeaders.ADDRESS)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val date =
            mail
                .header(SmsMailHeaders.DATE)
                ?.trim()
                ?.toLongOrNull()
                ?.takeIf { it > 0 } ?: return null
        // An unknown type comes from a newer format revision; restoring it into the wrong box
        // would be worse than skipping it.
        val box = mail.header(SmsMailHeaders.TYPE)?.let { SmsBox.fromHeaderValue(it) ?: return null } ?: SmsBox.INBOX
        val dateSent = mail.header(SmsMailHeaders.DATE_SENT)?.trim()?.toLongOrNull() ?: 0L
        return SmsMessageToRestore(address, body, date, dateSent, box)
    }

    private fun parseLegacy(
        mail: BackedUpMail,
        preamble: String,
        body: String,
    ): SmsMessageToRestore? {
        val date = mail.sentDateEpochMs?.takeIf { it > 0 } ?: return null
        val fromLine =
            preamble
                .lineSequence()
                .firstOrNull { it.startsWith(LEGACY_FROM_PREFIX) }
                ?.removePrefix(LEGACY_FROM_PREFIX)
                ?.trim()
                ?: return null
        val address = legacyAddress(fromLine).takeIf { it.isNotEmpty() } ?: return null
        return SmsMessageToRestore(address, body, date, dateSentEpochMs = 0L, box = SmsBox.INBOX)
    }

    /**
     * The legacy line is either `Name (number)` or just `number`. The *last* ` (` is used, so a
     * contact name that itself contains parentheses -- "Ana (work) (+351…)" -- still resolves to
     * the number.
     */
    private fun legacyAddress(fromLine: String): String {
        val open = fromLine.lastIndexOf(" (")
        return if (open >= 0 && fromLine.endsWith(")")) {
            fromLine.substring(open + 2, fromLine.length - 1).trim()
        } else {
            fromLine
        }
    }

    private fun isOurs(messageId: String?): Boolean {
        val id = messageId?.trim() ?: return false
        return id.startsWith(SmsMailHeaders.MESSAGE_ID_PREFIX) &&
            !id.startsWith(SmsMailHeaders.THREAD_ID_PREFIX) &&
            id.endsWith("@" + SmsMailHeaders.ID_DOMAIN + ">")
    }

    private companion object {
        const val PREAMBLE_END = "\n\n"
        const val LEGACY_FROM_PREFIX = "From: "
    }
}
