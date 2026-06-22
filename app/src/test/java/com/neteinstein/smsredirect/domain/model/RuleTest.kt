package com.neteinstein.smsredirect.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleTest {

    @Test
    fun `isValid returns true when targetNumber and senderText are not blank`() {
        val rule = Rule(targetNumber = "+351912345678", senderText = "BANK")
        assertTrue(rule.isValid())
    }

    @Test
    fun `isValid returns true when targetNumber and phoneNumber are not blank`() {
        val rule = Rule(targetNumber = "+351912345678", phoneNumber = "+1555123")
        assertTrue(rule.isValid())
    }

    @Test
    fun `isValid returns true when all sender fields are filled`() {
        val rule = Rule(targetNumber = "999", senderText = "SHOP", phoneNumber = "123")
        assertTrue(rule.isValid())
    }

    @Test
    fun `isValid returns false when targetNumber is blank`() {
        val rule = Rule(targetNumber = "", senderText = "BANK")
        assertFalse(rule.isValid())
    }

    @Test
    fun `isValid returns false when targetNumber is whitespace`() {
        val rule = Rule(targetNumber = "   ", senderText = "BANK")
        assertFalse(rule.isValid())
    }

    @Test
    fun `isValid returns false when both senderText and phoneNumber are blank`() {
        val rule = Rule(targetNumber = "999", senderText = "", phoneNumber = "")
        assertFalse(rule.isValid())
    }

    @Test
    fun `isValid returns false when both senderText and phoneNumber are whitespace`() {
        val rule = Rule(targetNumber = "999", senderText = "   ", phoneNumber = "   ")
        assertFalse(rule.isValid())
    }

    @Test
    fun `isValid returns false for default rule`() {
        val rule = Rule()
        assertFalse(rule.isValid())
    }
}
