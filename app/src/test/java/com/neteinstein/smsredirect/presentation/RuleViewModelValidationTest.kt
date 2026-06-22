package com.neteinstein.smsredirect.presentation

import com.neteinstein.smsredirect.presentation.rule.RuleViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleViewModelValidationTest {

    @Test
    fun `isValidPhoneNumber accepts digits only`() {
        assertTrue(RuleViewModel.isValidPhoneNumber("912345678"))
    }

    @Test
    fun `isValidPhoneNumber accepts number with country code`() {
        assertTrue(RuleViewModel.isValidPhoneNumber("+351912345678"))
    }

    @Test
    fun `isValidPhoneNumber accepts number with spaces`() {
        assertTrue(RuleViewModel.isValidPhoneNumber("912 345 678"))
    }

    @Test
    fun `isValidPhoneNumber accepts number with dashes`() {
        assertTrue(RuleViewModel.isValidPhoneNumber("912-345-678"))
    }

    @Test
    fun `isValidPhoneNumber accepts number with parentheses`() {
        assertTrue(RuleViewModel.isValidPhoneNumber("+1 (555) 123-4567"))
    }

    @Test
    fun `isValidPhoneNumber rejects blank string`() {
        assertFalse(RuleViewModel.isValidPhoneNumber(""))
        assertFalse(RuleViewModel.isValidPhoneNumber("   "))
    }

    @Test
    fun `isValidPhoneNumber rejects text sender`() {
        assertFalse(RuleViewModel.isValidPhoneNumber("BANK"))
        assertFalse(RuleViewModel.isValidPhoneNumber("MyCompany"))
    }

    @Test
    fun `isValidPhoneNumber rejects number with fewer than 4 digits`() {
        assertFalse(RuleViewModel.isValidPhoneNumber("123"))
        assertFalse(RuleViewModel.isValidPhoneNumber("+1"))
    }

    @Test
    fun `isValidPhoneNumber accepts exactly 4 digits`() {
        assertTrue(RuleViewModel.isValidPhoneNumber("1234"))
    }

    @Test
    fun `isValidPhoneNumber rejects alphanumeric`() {
        assertFalse(RuleViewModel.isValidPhoneNumber("555ABC1234"))
    }

    @Test
    fun `isValidPhoneNumber rejects special characters`() {
        assertFalse(RuleViewModel.isValidPhoneNumber("555@1234"))
        assertFalse(RuleViewModel.isValidPhoneNumber("555#1234"))
    }
}
