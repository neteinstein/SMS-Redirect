package com.neteinstein.smsredirect.domain.mms

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsPduTest {
    private val parser = MmsPduParser()
    private val composer = MmsPduComposer()

    @Test
    fun `parses a notification indication`() {
        // Given: an M-Notification.ind as a carrier pushes it
        val pdu =
            WspWriter()
                .apply {
                    header(MmsHeaders.MESSAGE_TYPE) { byte(MmsHeaders.MESSAGE_TYPE_NOTIFICATION_IND) }
                    header(MmsHeaders.TRANSACTION_ID) { textString("T123") }
                    header(MmsHeaders.MMS_VERSION) { shortInteger(MmsHeaders.MMS_VERSION_1_2) }
                    header(MmsHeaders.FROM) {
                        valueLength(1 + "+351912345678/TYPE=PLMN".length + 1)
                        byte(MmsHeaders.FROM_ADDRESS_PRESENT)
                        textString("+351912345678/TYPE=PLMN")
                    }
                    header(MmsHeaders.MESSAGE_CLASS) { byte(MmsHeaders.MESSAGE_CLASS_PERSONAL) }
                    header(MmsHeaders.MESSAGE_SIZE) { longInteger(45_000) }
                    header(MmsHeaders.EXPIRY) {
                        // absolute token + long integer (length octet + 4 octets)
                        valueLength(6)
                        byte(128)
                        longInteger(1_700_000_000)
                    }
                    header(MmsHeaders.CONTENT_LOCATION) { textString("http://mmsc.example/abc") }
                }.toByteArray()

        // When
        val parsed = parser.parse(pdu)

        // Then
        assertEquals(
            MmsPdu.NotificationInd(
                transactionId = "T123",
                from = "+351912345678",
                subject = null,
                contentLocation = "http://mmsc.example/abc",
                messageSize = 45_000,
                expirySeconds = 1_700_000_000,
            ),
            parsed,
        )
    }

    @Test
    fun `a notification without a content location is rejected`() {
        val pdu =
            WspWriter()
                .apply {
                    header(MmsHeaders.MESSAGE_TYPE) { byte(MmsHeaders.MESSAGE_TYPE_NOTIFICATION_IND) }
                    header(MmsHeaders.TRANSACTION_ID) { textString("T1") }
                }.toByteArray()

        assertNull(parser.parse(pdu))
    }

    @Test
    fun `truncated input yields null instead of throwing`() {
        val full = composer.composeSendReq("T1", listOf("+15551234567"), null, listOf(textPart("hello")))

        for (length in 0 until full.size) {
            // Any prefix must be handled without an exception escaping.
            parser.parse(full.copyOf(length))
        }
    }

    @Test
    fun `send request round-trips recipients, subject and parts`() {
        // Given
        val image =
            MmsPart("image/jpeg", byteArrayOf(1, 2, 3, 4), name = "image_0.jpg", contentId = "image_0", contentLocation = "image_0.jpg")
        val text = textPart("Olá, isto é um teste")
        val parts = listOf(MmsSmil.build(listOf(image, text)), image, text)

        // When
        val bytes = composer.composeSendReq("T42", listOf("+351 912-345-678", "friend@example.com"), "Férias", parts)
        val parsed = parser.parse(bytes) as MmsPdu.SendReq

        // Then
        assertEquals("T42", parsed.transactionId)
        assertEquals(listOf("+351912345678", "friend@example.com"), parsed.to)
        assertEquals("Férias", parsed.subject)
        assertEquals(MmsHeaders.CONTENT_TYPE_MULTIPART_RELATED, parsed.contentType)
        assertEquals(3, parsed.parts.size)
        assertEquals(MmsHeaders.CONTENT_TYPE_SMIL, parsed.parts[0].contentType)
        assertEquals("smil", parsed.parts[0].contentId)
        assertEquals("image/jpeg", parsed.parts[1].contentType)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), parsed.parts[1].data)
        assertEquals("image_0.jpg", parsed.parts[1].contentLocation)
        assertEquals("Olá, isto é um teste", parsed.parts[2].textOrEmpty())
        assertEquals(MmsCharsets.UTF_8, parsed.parts[2].charset)
    }

    @Test
    fun `send request starts with type, transaction id and version, in that order`() {
        val bytes = composer.composeSendReq("T1", listOf("+15551234567"), null, listOf(textPart("hi")))

        // The MMS spec requires these three headers first; some MMSCs reject anything else.
        assertEquals(0x8C, bytes[0].toInt() and 0xFF)
        assertEquals(0x80, bytes[1].toInt() and 0xFF)
        assertEquals(0x98, bytes[2].toInt() and 0xFF)
        val versionIndex = 3 + "T1".length + 1
        assertEquals(0x8D, bytes[versionIndex].toInt() and 0xFF)
        assertEquals(0x92, bytes[versionIndex + 1].toInt() and 0xFF)
    }

    @Test
    fun `retrieve conf text joins the text parts and ignores the layout`() {
        // Given: a retrieve.conf body built with the same multipart encoding as a send request
        val sendReq =
            composer.composeSendReq(
                "T9",
                listOf("+15551234567"),
                null,
                listOf(MmsSmil.build(emptyList()), textPart("first"), MmsPart("image/png", byteArrayOf(9)), textPart("second")),
            )
        val asRetrieveConf =
            sendReq.copyOf().also { it[1] = (0x80 or MmsHeaders.MESSAGE_TYPE_RETRIEVE_CONF).toByte() }

        // When
        val parsed = parser.parse(asRetrieveConf) as MmsPdu.RetrieveConf

        // Then
        assertEquals("first\nsecond", parsed.text)
        assertEquals(4, parsed.parts.size)
    }

    @Test
    fun `parses a send confirmation`() {
        val pdu =
            WspWriter()
                .apply {
                    header(MmsHeaders.MESSAGE_TYPE) { byte(MmsHeaders.MESSAGE_TYPE_SEND_CONF) }
                    header(MmsHeaders.TRANSACTION_ID) { textString("T7") }
                    header(MmsHeaders.MMS_VERSION) { shortInteger(MmsHeaders.MMS_VERSION_1_2) }
                    header(MmsHeaders.RESPONSE_STATUS) { shortInteger(0) }
                    header(MmsHeaders.MESSAGE_ID) { textString("mid-1") }
                }.toByteArray()

        val parsed = parser.parse(pdu) as MmsPdu.SendConf

        assertTrue(parsed.isOk)
        assertEquals("mid-1", parsed.messageId)
    }

    @Test
    fun `unknown and application headers are skipped`() {
        val pdu =
            WspWriter()
                .apply {
                    header(MmsHeaders.MESSAGE_TYPE) { byte(MmsHeaders.MESSAGE_TYPE_NOTIFICATION_IND) }
                    // An application header: token text name + text value
                    textString("X-Carrier")
                    textString("whatever")
                    // An unknown well-known header with a value-length payload
                    header(0x3A) {
                        valueLength(3)
                        byte(1)
                        byte(2)
                        byte(3)
                    }
                    header(MmsHeaders.CONTENT_LOCATION) { textString("http://mmsc.example/x") }
                }.toByteArray()

        val parsed = parser.parse(pdu) as MmsPdu.NotificationInd

        assertEquals("http://mmsc.example/x", parsed.contentLocation)
    }

    @Test
    fun `acknowledge indication carries the transaction id`() {
        val parsed = parser.parse(composer.composeAcknowledgeInd("T5"))

        assertEquals(MmsPdu.Other("T5", MmsHeaders.MESSAGE_TYPE_ACKNOWLEDGE_IND), parsed)
    }

    @Test
    fun `uintvar and long integers round trip at boundaries`() {
        for (value in listOf(0, 127, 128, 16_383, 16_384, 2_000_000)) {
            val bytes = WspWriter().apply { uintvar(value) }.toByteArray()
            assertEquals(value, WspReader(bytes).readUintvar())
        }
        for (value in listOf(0L, 255L, 256L, 1_700_000_000L)) {
            val bytes = WspWriter().apply { longInteger(value) }.toByteArray()
            assertEquals(value, WspReader(bytes).readLongInteger())
        }
    }

    @Test
    fun `addresses lose their type suffix and phone numbers their formatting`() {
        assertEquals("+351912345678/TYPE=PLMN", MmsPduComposer.toMmsAddress("+351 912 345 678"))
        assertEquals("a@b.c", MmsPduComposer.toMmsAddress(" a@b.c "))
        assertEquals("+351912345678", MmsPduParser.cleanAddress("+351912345678/TYPE=PLMN"))
    }

    @Test
    fun `one-to-one MMS conversation is just the sender`() {
        val addresses =
            MmsRecipients.conversationAddresses(
                from = "+351912345678",
                to = listOf("+351960000000"),
                cc = emptyList(),
                ownNumbers = emptyList(),
            )

        assertEquals(listOf("+351912345678"), addresses)
    }

    @Test
    fun `group MMS conversation is everyone but this phone`() {
        val addresses =
            MmsRecipients.conversationAddresses(
                from = "+351912345678",
                to = listOf("960000000", "+351930000000"),
                cc = listOf("+351912345678"),
                ownNumbers = listOf("+351960000000"),
            )

        assertEquals(listOf("+351912345678", "+351930000000"), addresses)
    }

    private fun textPart(text: String) =
        MmsPart(
            contentType = MmsHeaders.CONTENT_TYPE_TEXT,
            data = text.toByteArray(Charsets.UTF_8),
            name = "text_0.txt",
            contentId = "text_0",
            contentLocation = "text_0.txt",
            charset = MmsCharsets.UTF_8,
        )
}
