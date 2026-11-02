package com.neteinstein.smsredirect.domain.mail

import com.neteinstein.smsredirect.domain.model.SmsBackupKey
import com.neteinstein.smsredirect.domain.model.SmsBackupSettings
import com.neteinstein.smsredirect.domain.model.SmsBox
import com.neteinstein.smsredirect.domain.model.SmsMessageToBackup
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Turns an SMS into an [OutgoingMail]: subject templating, Gmail threading headers and body
 * formatting.
 *
 * Pure Kotlin with JDK-only dependencies, so every branch here is covered by ordinary unit tests.
 */
class SmsMailFactory(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    fun build(
        accountAddress: String,
        subjectTemplate: String,
        message: SmsMessageToBackup,
        contactName: String?,
    ): OutgoingMail {
        val normalizedAddress = SmsBackupKey.normalizeAddress(message.rawAddress)
        val displayNumber = message.rawAddress.ifBlank { normalizedAddress }
        // Line breaks in a contact name would end the body's preamble early and corrupt both
        // the transcript and the legacy restore parser, which splits on the first blank line.
        val displayName = contactName?.replace(LINE_BREAKS, " ")?.trim()?.takeIf { it.isNotBlank() }

        // Every message from the same address shares one synthetic thread root, which is what
        // makes Gmail group a contact's history into a single conversation. The root message
        // itself never exists; referencing a non-existent id is normal and harmless.
        val threadRoot = "<sms-thread-" + SmsBackupKey.sha256Hex(normalizedAddress).take(24) + "@" + SmsMailHeaders.ID_DOMAIN + ">"

        return OutgoingMail(
            from = accountAddress,
            fromDisplayName = displayName ?: displayNumber,
            to = accountAddress,
            subject = buildSubject(subjectTemplate, displayName, displayNumber),
            body = buildBody(displayName, displayNumber, message),
            dateEpochMs = message.dateEpochMs,
            messageId = SmsMailHeaders.MESSAGE_ID_PREFIX + message.stableKey.take(32) + "@" + SmsMailHeaders.ID_DOMAIN + ">",
            inReplyTo = threadRoot,
            references = listOf(threadRoot),
            extraHeaders = restoreHeaders(message),
        )
    }

    /**
     * Everything a restore needs to rebuild the provider row exactly, in headers rather than the
     * body -- see [SmsMailHeaders] for why. The raw address is kept (not the normalized one) so
     * the restored message threads with the user's contacts exactly as it did before.
     */
    private fun restoreHeaders(message: SmsMessageToBackup): Map<String, String> =
        buildMap {
            put(SmsMailHeaders.FORMAT, SmsMailHeaders.CURRENT_FORMAT)
            put(SmsMailHeaders.TYPE, message.box.headerValue)
            put(SmsMailHeaders.ADDRESS, message.rawAddress)
            put(SmsMailHeaders.DATE, message.dateEpochMs.toString())
            if (message.dateSentEpochMs > 0) put(SmsMailHeaders.DATE_SENT, message.dateSentEpochMs.toString())
        }

    /**
     * Substitutes the two placeholders.
     *
     * Uses literal [String.replace], never [Regex.replace] -- in a Kotlin regex replacement `$`
     * introduces a group reference, so `$CONTACT_NAME` would be parsed as a backreference rather
     * than replaced.
     */
    private fun buildSubject(
        template: String,
        displayName: String?,
        displayNumber: String,
    ): String {
        val effective = template.ifBlank { SmsBackupSettings.DEFAULT_SUBJECT_TEMPLATE }
        return effective
            .replace(SmsBackupSettings.PLACEHOLDER_CONTACT_NAME, displayName ?: displayNumber)
            .replace(SmsBackupSettings.PLACEHOLDER_PHONE_NUMBER, displayNumber)
    }

    private fun buildBody(
        displayName: String?,
        displayNumber: String,
        message: SmsMessageToBackup,
    ): String {
        val party = if (displayName != null) "$displayName ($displayNumber)" else displayNumber
        val timestamp = BODY_DATE_FORMAT.format(Instant.ofEpochMilli(message.dateEpochMs).atZone(zoneId))
        val (partyLabel, timestampLabel) =
            when (message.box) {
                SmsBox.INBOX -> "From: " to "Received: "
                SmsBox.SENT -> "To: " to "Sent: "
            }
        return buildString {
            append(partyLabel).append(party).append('\n')
            append(timestampLabel).append(timestamp).append('\n')
            append('\n')
            append(message.body)
        }
    }

    companion object {
        private val LINE_BREAKS = Regex("[\\r\\n]+")
        private val BODY_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
