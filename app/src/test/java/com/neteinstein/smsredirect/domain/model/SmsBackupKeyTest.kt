package com.neteinstein.smsredirect.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SmsBackupKeyTest {
    @Test
    fun `same message always produces the same key`() {
        val first = SmsBackupKey.of("+351912345678", "Hello", 1_700_000_000_000)
        val second = SmsBackupKey.of("+351912345678", "Hello", 1_700_000_000_000)

        assertEquals(first, second)
    }

    @Test
    fun `formatting differences in the address do not change the key`() {
        val spaced = SmsBackupKey.of("+351 912 345 678", "Hello", 1_700_000_000_000)
        val dashed = SmsBackupKey.of("+351-912-345-678", "Hello", 1_700_000_000_000)
        val bracketed = SmsBackupKey.of("(+351) 912345678", "Hello", 1_700_000_000_000)

        assertEquals(spaced, dashed)
        assertEquals(spaced, bracketed)
    }

    @Test
    fun `a different timestamp produces a different key`() {
        val first = SmsBackupKey.of("+351912345678", "Hello", 1_700_000_000_000)
        val second = SmsBackupKey.of("+351912345678", "Hello", 1_700_000_000_001)

        assertNotEquals(first, second)
    }

    @Test
    fun `a different body produces a different key`() {
        val first = SmsBackupKey.of("+351912345678", "Hello", 1_700_000_000_000)
        val second = SmsBackupKey.of("+351912345678", "Hello!", 1_700_000_000_000)

        assertNotEquals(first, second)
    }

    @Test
    fun `a different address produces a different key`() {
        val first = SmsBackupKey.of("+351912345678", "Hello", 1_700_000_000_000)
        val second = SmsBackupKey.of("+351912345679", "Hello", 1_700_000_000_000)

        assertNotEquals(first, second)
    }

    @Test
    fun `field separator prevents adjacent fields from colliding`() {
        // Without a separator, ("ab", "c") and ("a", "bc") would hash identically.
        val first = SmsBackupKey.of("12345", "6789", 1_700_000_000_000)
        val second = SmsBackupKey.of("123456", "789", 1_700_000_000_000)

        assertNotEquals(first, second)
    }

    @Test
    fun `alphanumeric shortcodes are lowercased`() {
        assertEquals("vodafone", SmsBackupKey.normalizeAddress("VODAFONE"))
        assertEquals(
            SmsBackupKey.of("VODAFONE", "Hi", 1L),
            SmsBackupKey.of("vodafone", "Hi", 1L),
        )
    }

    @Test
    fun `a blank address normalizes to empty rather than throwing`() {
        assertEquals("", SmsBackupKey.normalizeAddress(null))
        assertEquals("", SmsBackupKey.normalizeAddress("   "))
    }

    @Test
    fun `the key is a hex sha256`() {
        val key = SmsBackupKey.of("+351912345678", "Hello", 1L)

        assertEquals(64, key.length)
        assertEquals(true, key.all { it in "0123456789abcdef" })
    }

    @Test
    fun `the same number with and without a country code is the same sender`() {
        assertEquals(true, SmsBackupKey.sameAddress("+351 912 345 678", "912345678"))
    }

    @Test
    fun `different numbers sharing a short suffix are different senders`() {
        assertEquals(false, SmsBackupKey.sameAddress("+351912345678", "+351962345678"))
    }

    @Test
    fun `short codes must match exactly, ignoring case`() {
        assertEquals(true, SmsBackupKey.sameAddress("VODAFONE", "Vodafone"))
        assertEquals(false, SmsBackupKey.sameAddress("1234", "91234"))
    }

    @Test
    fun `two blank addresses are not the same sender`() {
        assertEquals(false, SmsBackupKey.sameAddress("", " "))
    }

    @Test
    fun `an inbox key is unchanged from before sent messages were backed up`() {
        // Keys already stored in sms_backup_state were derived from address|body|date alone. If
        // the inbox key moved, an app update would re-upload every user's whole inbox.
        val legacy = SmsBackupKey.sha256Hex("+351912345678|Hello|1700000000000")

        assertEquals(legacy, SmsBackupKey.of("+351 912 345 678", "Hello", 1_700_000_000_000))
        assertEquals(legacy, SmsBackupKey.of("+351 912 345 678", "Hello", 1_700_000_000_000, SmsBox.INBOX))
    }

    @Test
    fun `a sent message never shares a key with an identical received one`() {
        // Texting your own number puts the same address and body in both boxes.
        val received = SmsBackupKey.of("+351912345678", "Hello", 1_700_000_000_000, SmsBox.INBOX)
        val sent = SmsBackupKey.of("+351912345678", "Hello", 1_700_000_000_000, SmsBox.SENT)

        assertNotEquals(received, sent)
    }

    @Test
    fun `a sent key is as stable as an inbox key`() {
        assertEquals(
            SmsBackupKey.of("+351 912 345 678", "Hello", 1L, SmsBox.SENT),
            SmsBackupKey.of("+351912345678", "Hello", 1L, SmsBox.SENT),
        )
    }
}
