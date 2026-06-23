package com.neteinstein.smsredirect.presentation

import com.neteinstein.smsredirect.presentation.rule.RuleViewModel
import org.junit.Assert.assertEquals
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

    // --- normalizeTargetNumber ---

    @Test
    fun `normalizeTargetNumber prepends prefix to local number`() {
        assertEquals("+351912345678", RuleViewModel.normalizeTargetNumber("912345678", "+351"))
    }

    @Test
    fun `normalizeTargetNumber prepends prefix to number with spaces`() {
        assertEquals("+351912345678", RuleViewModel.normalizeTargetNumber("912 345 678", "+351"))
    }

    @Test
    fun `normalizeTargetNumber prepends prefix to number with dashes`() {
        assertEquals("+351912345678", RuleViewModel.normalizeTargetNumber("912-345-678", "+351"))
    }

    @Test
    fun `normalizeTargetNumber prepends prefix to number with parens`() {
        assertEquals("+351912345678", RuleViewModel.normalizeTargetNumber("(912) 345-678", "+351"))
    }

    @Test
    fun `normalizeTargetNumber does not modify number starting with plus`() {
        assertEquals("+44123456789", RuleViewModel.normalizeTargetNumber("+44123456789", "+351"))
    }

    @Test
    fun `normalizeTargetNumber does not modify number with plus and spaces`() {
        assertEquals("+44 123 456 789", RuleViewModel.normalizeTargetNumber("+44 123 456 789", "+351"))
    }

    @Test
    fun `normalizeTargetNumber returns as-is when prefix is empty`() {
        assertEquals("912345678", RuleViewModel.normalizeTargetNumber("912345678", ""))
    }

    @Test
    fun `normalizeTargetNumber returns as-is when fewer than 4 digits`() {
        assertEquals("123", RuleViewModel.normalizeTargetNumber("123", "+351"))
    }

    @Test
    fun `normalizeTargetNumber handles number with stray plus in middle`() {
        // Even with unexpected + not at start, still extracts digits and prepends prefix
        assertEquals("+351912345678", RuleViewModel.normalizeTargetNumber("9+12345678", "+351"))
    }
}
