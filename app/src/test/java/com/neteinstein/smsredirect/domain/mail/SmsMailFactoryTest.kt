package com.neteinstein.smsredirect.domain.mail

import com.neteinstein.smsredirect.domain.model.SmsBackupKey
import com.neteinstein.smsredirect.domain.model.SmsBackupSettings
import com.neteinstein.smsredirect.domain.model.SmsMessageToBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class SmsMailFactoryTest {
    private val factory = SmsMailFactory(zoneId = ZoneId.of("UTC"))
    private val account = "me@gmail.com"

    private fun message(
        address: String = "+351912345678",
        body: String = "Hello there",
        date: Long = 1_700_000_000_000,
        providerId: Long = 1L,
    ) = SmsMessageToBackup(
        stableKey = SmsBackupKey.of(address, body, date),
        rawAddress = address,
        body = body,
        dateEpochMs = date,
        providerId = providerId,
    )

    @Test
    fun `both placeholders are substituted`() {
        val mail =
            factory.build(
                account,
                SmsBackupSettings.DEFAULT_SUBJECT_TEMPLATE,
                message(),
                contactName = "Jane Doe",
            )

        assertEquals("SMS with Jane Doe (+351912345678)", mail.subject)
    }

    @Test
    fun `an unresolved contact name falls back to the number`() {
        val mail =
            factory.build(
                account,
                SmsBackupSettings.DEFAULT_SUBJECT_TEMPLATE,
                message(),
                contactName = null,
            )

        assertEquals("SMS with +351912345678 (+351912345678)", mail.subject)
    }

    @Test
    fun `a blank contact name is treated as unresolved`() {
        val mail = factory.build(account, SmsBackupSettings.DEFAULT_SUBJECT_TEMPLATE, message(), contactName = "  ")

        assertEquals("SMS with +351912345678 (+351912345678)", mail.subject)
    }

    @Test
    fun `a template with only one placeholder works`() {
        val mail = factory.build(account, "From \$CONTACT_NAME", message(), contactName = "Jane")

        assertEquals("From Jane", mail.subject)
    }

    @Test
    fun `a template with no placeholders is used verbatim`() {
        val mail = factory.build(account, "Archived SMS", message(), contactName = "Jane")

        assertEquals("Archived SMS", mail.subject)
    }

    @Test
    fun `a literal dollar elsewhere in the template survives`() {
        // Regression guard: a Regex-based replacement would treat $ as a group reference.
        val mail = factory.build(account, "Cost \$5 - \$CONTACT_NAME", message(), contactName = "Jane")

        assertEquals("Cost \$5 - Jane", mail.subject)
    }

    @Test
    fun `a blank template falls back to the default`() {
        val mail = factory.build(account, "", message(), contactName = "Jane")

        assertEquals("SMS with Jane (+351912345678)", mail.subject)
    }

    @Test
    fun `messages from the same address share a thread root`() {
        val first = factory.build(account, "", message(body = "One", date = 1L), contactName = null)
        val second = factory.build(account, "", message(body = "Two", date = 2L), contactName = null)

        assertEquals(first.references, second.references)
        assertEquals(first.inReplyTo, second.inReplyTo)
    }

    @Test
    fun `messages from different addresses do not share a thread root`() {
        val first = factory.build(account, "", message(address = "+351900000001"), contactName = null)
        val second = factory.build(account, "", message(address = "+351900000002"), contactName = null)

        assertNotEquals(first.references, second.references)
    }

    @Test
    fun `an address that differs only in formatting still shares a thread root`() {
        val first = factory.build(account, "", message(address = "+351 912 345 678"), contactName = null)
        val second = factory.build(account, "", message(address = "+351912345678"), contactName = null)

        assertEquals(first.references, second.references)
    }

    @Test
    fun `each message gets a distinct message id`() {
        val first = factory.build(account, "", message(body = "One", date = 1L), contactName = null)
        val second = factory.build(account, "", message(body = "Two", date = 2L), contactName = null)

        assertNotEquals(first.messageId, second.messageId)
    }

    @Test
    fun `synthesized ids use the reserved invalid TLD so nothing can be delivered`() {
        val mail = factory.build(account, "", message(), contactName = null)

        assertTrue(mail.messageId.endsWith("@sms-toolbox.invalid>"))
        assertTrue(mail.references.single().endsWith("@sms-toolbox.invalid>"))
    }

    @Test
    fun `the original sms timestamp is carried through untouched`() {
        val mail = factory.build(account, "", message(date = 1_234_567_890_000), contactName = null)

        assertEquals(1_234_567_890_000, mail.dateEpochMs)
    }

    @Test
    fun `the body includes the sender and the original receive time`() {
        val mail =
            factory.build(
                account,
                "",
                message(body = "Hello there", date = 1_700_000_000_000),
                contactName = "Jane Doe",
            )

        assertTrue(mail.body.contains("From: Jane Doe (+351912345678)"))
        assertTrue(mail.body.contains("Received: 2023-11-14 22:13:20"))
        assertTrue(mail.body.endsWith("Hello there"))
    }

    @Test
    fun `mail is addressed from and to the account so it files under the label`() {
        val mail = factory.build(account, "", message(), contactName = "Jane Doe")

        assertEquals(account, mail.from)
        assertEquals(account, mail.to)
        assertEquals("Jane Doe", mail.fromDisplayName)
    }
}
