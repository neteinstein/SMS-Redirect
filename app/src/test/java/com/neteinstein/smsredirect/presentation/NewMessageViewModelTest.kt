package com.neteinstein.smsredirect.presentation

import com.neteinstein.smsredirect.presentation.newmessage.NewMessageViewModel
import com.neteinstein.smsredirect.presentation.newmessage.Recipient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewMessageViewModelTest {
    @Test
    fun `a typed number can be used without committing it first`() {
        val vm = NewMessageViewModel()

        vm.onInputChanged("+351 912 345 678")

        assertTrue(vm.uiState.value.canProceed)
        assertEquals(listOf("+351 912 345 678"), vm.uiState.value.effectiveAddresses)
    }

    @Test
    fun `a comma commits the recipient`() {
        val vm = NewMessageViewModel()

        vm.onInputChanged("912345678,")

        assertEquals(listOf(Recipient("912345678")), vm.uiState.value.recipients)
        assertEquals("", vm.uiState.value.input)
    }

    @Test
    fun `text that is not a number or email is rejected`() {
        val vm = NewMessageViewModel()
        vm.onInputChanged("pizza")

        assertFalse(vm.commitInput())
        assertTrue(vm.uiState.value.invalidInput)
        assertFalse(vm.uiState.value.canProceed)
    }

    @Test
    fun `the same person picked twice is added once`() {
        val vm = NewMessageViewModel()

        vm.onContactPicked("+351912345678", "Ana")
        vm.onContactPicked("912345678", "Ana")

        assertEquals(listOf(Recipient("+351912345678", "Ana")), vm.uiState.value.recipients)
    }

    @Test
    fun `email recipients are accepted`() {
        val vm = NewMessageViewModel()
        vm.onInputChanged("ana@example.com")

        assertTrue(vm.commitInput())
        assertEquals(listOf("ana@example.com"), vm.uiState.value.effectiveAddresses)
    }

    @Test
    fun `different international numbers with the same last digits are both kept`() {
        val vm = NewMessageViewModel()

        vm.onContactPicked("+12025550123", null)
        vm.onContactPicked("+442025550123", null)

        assertEquals(2, vm.uiState.value.recipients.size)
    }
}
