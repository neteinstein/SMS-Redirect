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
}
