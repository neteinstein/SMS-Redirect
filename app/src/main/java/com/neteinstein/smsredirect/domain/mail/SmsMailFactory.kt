package com.neteinstein.smsredirect.domain.mail

import com.neteinstein.smsredirect.domain.model.SmsBackupKey
import com.neteinstein.smsredirect.domain.model.SmsBackupSettings
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
        val displayName = contactName?.takeIf { it.isNotBlank() }

        // Every message from the same address shares one synthetic thread root, which is what
        // makes Gmail group a contact's history into a single conversation. The root message
        // itself never exists; referencing a non-existent id is normal and harmless.
        val threadRoot = "<sms-thread-" + SmsBackupKey.sha256Hex(normalizedAddress).take(24) + "@" + ID_DOMAIN + ">"

        return OutgoingMail(
            from = accountAddress,
            fromDisplayName = displayName ?: displayNumber,
            to = accountAddress,
            subject = buildSubject(subjectTemplate, displayName, displayNumber),
            body = buildBody(displayName, displayNumber, message),
            dateEpochMs = message.dateEpochMs,
            messageId = "<sms-" + message.stableKey.take(32) + "@" + ID_DOMAIN + ">",
            inReplyTo = threadRoot,
            references = listOf(threadRoot),
        )
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
        val sender = if (displayName != null) "$displayName ($displayNumber)" else displayNumber
        val received = BODY_DATE_FORMAT.format(Instant.ofEpochMilli(message.dateEpochMs).atZone(zoneId))
        return buildString {
            append("From: ").append(sender).append('\n')
            append("Received: ").append(received).append('\n')
            append('\n')
            append(message.body)
        }
    }

    companion object {
        /**
         * RFC 6761 reserved TLD: guaranteed never resolvable, so a synthesized id can never be
         * mistaken for a real address or trigger a delivery attempt.
         */
        private const val ID_DOMAIN = "sms-toolbox.invalid"
        private val BODY_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
