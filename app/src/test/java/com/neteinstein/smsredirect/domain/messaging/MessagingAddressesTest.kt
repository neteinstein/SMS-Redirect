package com.neteinstein.smsredirect.domain.messaging

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagingAddressesTest {
    @Test
    fun `formatting never matters`() {
        assertTrue(MessagingAddresses.same("+351 912-345-678", "+351912345678"))
        assertTrue(MessagingAddresses.same("(912) 345 678", "912345678"))
    }

    @Test
    fun `a local number matches the same number with its country code`() {
        assertTrue(MessagingAddresses.same("912345678", "+351912345678"))
        assertTrue(MessagingAddresses.same("00351912345678", "+351912345678"))
    }

    @Test
    fun `different explicit country codes are different people`() {
        assertFalse(MessagingAddresses.same("+12025550123", "+442025550123"))
        assertFalse(MessagingAddresses.same("+351912345678", "+244912345678"))
    }

    @Test
    fun `short codes do not match longer numbers by suffix`() {
        assertFalse(MessagingAddresses.same("4567", "+351912344567"))
        assertTrue(MessagingAddresses.same("4567", "4567"))
    }

    @Test
    fun `emails and alphanumeric senders compare exactly, ignoring case only`() {
        assertTrue(MessagingAddresses.same("Ann.Lee@Company.com", "ann.lee@company.com"))
        assertFalse(MessagingAddresses.same("ann.lee@company.com", "annlee@company.com"))
        assertTrue(MessagingAddresses.same("Vodafone", "VODAFONE"))
        assertFalse(MessagingAddresses.same("", ""))
    }
}
