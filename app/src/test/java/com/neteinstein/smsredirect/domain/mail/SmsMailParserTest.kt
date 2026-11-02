package com.neteinstein.smsredirect.domain.mail

import com.neteinstein.smsredirect.domain.model.SmsBackupKey
import com.neteinstein.smsredirect.domain.model.SmsBackupSettings
import com.neteinstein.smsredirect.domain.model.SmsBox
import com.neteinstein.smsredirect.domain.model.SmsMessageToBackup
import com.neteinstein.smsredirect.domain.model.SmsMessageToRestore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class SmsMailParserTest {
    private val factory = SmsMailFactory(zoneId = ZoneId.of("Europe/Lisbon"))
    private val parser = SmsMailParser()

    private fun backup(
        address: String = "+351 912 345 678",
        body: String = "See you at 8",
        date: Long = 1_700_000_000_123,
        dateSent: Long = 1_699_999_998_000,
        box: SmsBox = SmsBox.INBOX,
    ) = SmsMessageToBackup(
        stableKey = SmsBackupKey.of(address, body, date),
        rawAddress = address,
        body = body,
        dateEpochMs = date,
        providerId = 1L,
        dateSentEpochMs = dateSent,
        box = box,
    )

    /**
     * What a mail looks like after the IMAP round-trip: MIME text comes back with CRLF line
     * endings, and header names in whatever case the server returns.
     */
    private fun roundTrip(
        mail: OutgoingMail,
        uid: Long = 1L,
    ) = BackedUpMail(
        uid = uid,
        messageId = mail.messageId,
        headers = mail.extraHeaders.mapKeys { it.key.lowercase() },
        sentDateEpochMs = mail.dateEpochMs / 1000 * 1000,
        bodyText = mail.body.replace("\n", "\r\n"),
        fromAddress = mail.from,
    )

    private fun SmsMailParser.parse(mail: BackedUpMail) = parse(mail, ACCOUNT)

    @Test
    fun `a current-format backup restores exactly what was backed up`() {
        // Given
        val original = backup()
        val mail = factory.build(ACCOUNT, SmsBackupSettings.DEFAULT_SUBJECT_TEMPLATE, original, "Jane (work)")

        // When
        val restored = parser.parse(roundTrip(mail))

        // Then
        assertEquals(
            SmsMessageToRestore(
                address = "+351 912 345 678",
                body = "See you at 8",
                dateEpochMs = 1_700_000_000_123,
                dateSentEpochMs = 1_699_999_998_000,
                box = SmsBox.INBOX,
            ),
            restored,
        )
    }

    @Test
    fun `the restored message keeps its original backup key`() {
        val original = backup()
        val mail = factory.build(ACCOUNT, "", original, null)

        val restored = parser.parse(roundTrip(mail))!!

        // Same key means the backup scan will not re-upload what a restore just wrote.
        assertEquals(original.stableKey, SmsBackupKey.of(restored.address, restored.body, restored.dateEpochMs))
    }

    @Test
    fun `a body containing blank lines survives intact`() {
        val original = backup(body = "Line one\n\nLine three\n")
        val mail = factory.build(ACCOUNT, "", original, null)

        assertEquals("Line one\n\nLine three\n", parser.parse(roundTrip(mail))?.body)
    }

    @Test
    fun `a sent message is restored to the sent box`() {
        val mail = factory.build(ACCOUNT, "", backup(box = SmsBox.SENT), null)

        assertEquals(SmsBox.SENT, parser.parse(roundTrip(mail))?.box)
    }

    @Test
    fun `a legacy backup without headers falls back to the body and Date header`() {
        // Given: exactly what backups looked like before the restore headers existed.
        val legacy =
            BackedUpMail(
                uid = 7L,
                messageId = "<sms-0123456789abcdef0123456789abcdef@sms-toolbox.invalid>",
                headers = emptyMap(),
                sentDateEpochMs = 1_700_000_000_000,
                bodyText = "From: Ana (work) (+351912345678)\r\nReceived: 2023-11-14 22:13:20\r\n\r\nHello",
                fromAddress = ACCOUNT,
            )

        // When
        val restored = parser.parse(legacy)

        // Then
        assertEquals(SmsMessageToRestore("+351912345678", "Hello", 1_700_000_000_000, 0L, SmsBox.INBOX), restored)
    }

    @Test
    fun `a legacy backup of an unnamed sender uses the whole From line`() {
        val legacy =
            BackedUpMail(
                uid = 7L,
                messageId = "<sms-abc@sms-toolbox.invalid>",
                headers = emptyMap(),
                sentDateEpochMs = 1_700_000_000_000,
                bodyText = "From: VODAFONE\nReceived: 2023-11-14 22:13:20\n\nYour bill is ready",
                fromAddress = ACCOUNT,
            )

        assertEquals("VODAFONE", parser.parse(legacy)?.address)
    }

    @Test
    fun `mail that is not ours is ignored`() {
        val foreign =
            BackedUpMail(
                uid = 1L,
                messageId = "<CAF123@mail.gmail.com>",
                headers = emptyMap(),
                sentDateEpochMs = 1_700_000_000_000,
                bodyText = "From: Someone\n\nHi",
            )

        assertNull(parser.parse(foreign))
    }

    @Test
    fun `a synthetic thread root id is not mistaken for a message`() {
        val mail = roundTrip(factory.build(ACCOUNT, "", backup(), null))

        assertNull(parser.parse(mail.copy(messageId = "<sms-thread-abc@sms-toolbox.invalid>")))
    }

    @Test
    fun `an unknown message type from a newer format is skipped rather than misfiled`() {
        val mail = roundTrip(factory.build(ACCOUNT, "", backup(), null))
        val newer = mail.copy(headers = mail.headers + (SmsMailHeaders.TYPE.lowercase() to "draft"))

        assertNull(parser.parse(newer))
    }

    @Test
    fun `a current-format mail missing its timestamp header is skipped`() {
        val mail = roundTrip(factory.build(ACCOUNT, "", backup(), null))

        assertNull(parser.parse(mail.copy(headers = mail.headers - SmsMailHeaders.DATE.lowercase())))
    }

    @Test
    fun `a forged backup emailed in from someone else is rejected`() {
        // Given: our exact Message-ID format and headers, but sent by a stranger.
        val forged = roundTrip(factory.build(ACCOUNT, "", backup(), null)).copy(fromAddress = "attacker@example.com")

        // Then
        assertNull(parser.parse(forged))
    }

    @Test
    fun `a backup that travelled over SMTP is rejected even with a spoofed From`() {
        val forged = roundTrip(factory.build(ACCOUNT, "", backup(), null)).copy(arrivedOverSmtp = true)

        assertNull(parser.parse(forged))
    }

    @Test
    fun `a mail with no From is rejected`() {
        val mail = roundTrip(factory.build(ACCOUNT, "", backup(), null)).copy(fromAddress = null)

        assertNull(parser.parse(mail))
    }

    @Test
    fun `the account address is matched case-insensitively`() {
        val mail = roundTrip(factory.build(ACCOUNT, "", backup(), null)).copy(fromAddress = "Me@Gmail.com")

        assertEquals("See you at 8", parser.parse(mail)?.body)
    }

    private companion object {
        const val ACCOUNT = "me@gmail.com"
    }
}
