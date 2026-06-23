package com.neteinstein.smsredirect.presentation

import com.neteinstein.smsredirect.FakeContactNameResolver
import com.neteinstein.smsredirect.FakeRuleRepository
import com.neteinstein.smsredirect.FakeSettingsRepository
import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.usecase.AddRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.GetDefaultPrefixNowUseCase
import com.neteinstein.smsredirect.domain.usecase.GetRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.UpdateRuleUseCase
import com.neteinstein.smsredirect.presentation.rule.NameField
import com.neteinstein.smsredirect.presentation.rule.NameSource
import com.neteinstein.smsredirect.presentation.rule.RuleViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RuleViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val settingsRepository = FakeSettingsRepository()
    private val getDefaultPrefixNowUseCase = GetDefaultPrefixNowUseCase(settingsRepository)

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun `loads existing rule for editing`() = runTest {
        val repository = FakeRuleRepository(
            listOf(Rule(id = 1, senderText = "BANK", phoneNumber = "123", targetNumber = "777"))
        )
        val vm = RuleViewModel(GetRuleUseCase(repository), AddRuleUseCase(repository), UpdateRuleUseCase(repository), repository, getDefaultPrefixNowUseCase)

        vm.loadRule(1)
        advanceUntilIdle()

        assertEquals("BANK", vm.uiState.value.senderText)
        assertEquals("777", vm.uiState.value.targetNumber)
    }

    @Test
    fun `save validates and reports error`() = runTest {
        val repository = FakeRuleRepository()
        val vm = RuleViewModel(GetRuleUseCase(repository), AddRuleUseCase(repository), UpdateRuleUseCase(repository), repository, getDefaultPrefixNowUseCase)

        vm.saveRule()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.error != null)
    }

    @Test
    fun `loads existing rule with phoneNumber as sender when senderText is blank`() = runTest {
        val repository = FakeRuleRepository(
            listOf(Rule(id = 2, senderText = "", phoneNumber = "+1555123", targetNumber = "999"))
        )
        val vm = RuleViewModel(GetRuleUseCase(repository), AddRuleUseCase(repository), UpdateRuleUseCase(repository), repository, getDefaultPrefixNowUseCase)

        vm.loadRule(2)
        advanceUntilIdle()

        assertEquals("+1555123", vm.uiState.value.senderText)
        assertEquals("999", vm.uiState.value.targetNumber)
    }

    @Test
    fun `save inserts valid rule`() = runTest {
        val repository = FakeRuleRepository()
        val vm = RuleViewModel(GetRuleUseCase(repository), AddRuleUseCase(repository), UpdateRuleUseCase(repository), repository, getDefaultPrefixNowUseCase)

        vm.onSenderChanged("SHOP")
        vm.onTargetChanged("4445")
        vm.saveRule()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun `save generates default name when empty`() = runTest {
        val repository = FakeRuleRepository()
        val vm = RuleViewModel(GetRuleUseCase(repository), AddRuleUseCase(repository), UpdateRuleUseCase(repository), repository, getDefaultPrefixNowUseCase)

        vm.onSenderChanged("TEST")
        vm.onTargetChanged("5556")
        vm.saveRule()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.saved)
        val savedRule = repository.getRulesSnapshot().first()
        assertEquals("Rule #1", savedRule.name)
    }

    @Test
    fun `save preserves custom name`() = runTest {
        val repository = FakeRuleRepository()
        val vm = RuleViewModel(GetRuleUseCase(repository), AddRuleUseCase(repository), UpdateRuleUseCase(repository), repository, getDefaultPrefixNowUseCase)

        vm.onNameChanged("My Custom Rule")
        vm.onSenderChanged("BANK")
        vm.onTargetChanged("9993")
        vm.saveRule()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.saved)
        val savedRule = repository.getRulesSnapshot().first()
        assertEquals("My Custom Rule", savedRule.name)
    }

    @Test
    fun `normalizePhoneNumber prepends country code for local numbers`() {
        assertEquals("+351912345678", RuleViewModel.normalizePhoneNumber("912345678", "+351"))
        assertEquals("+351912345678", RuleViewModel.normalizePhoneNumber("912 345 678", "+351"))
        assertEquals("+351912345678", RuleViewModel.normalizePhoneNumber("912-345-678", "+351"))
    }

    @Test
    fun `normalizePhoneNumber preserves numbers with country code`() {
        assertEquals("+351912345678", RuleViewModel.normalizePhoneNumber("+351912345678", "+351"))
        assertEquals("+44123456789", RuleViewModel.normalizePhoneNumber("+44123456789", "+351"))
    }

    @Test
    fun `normalizePhoneNumber does not modify text senders`() {
        assertEquals("BANK", RuleViewModel.normalizePhoneNumber("BANK", "+351"))
        assertEquals("MyCompany", RuleViewModel.normalizePhoneNumber("MyCompany", "+351"))
        assertEquals("", RuleViewModel.normalizePhoneNumber("", "+351"))
    }

    @Test
    fun `normalizePhoneNumber does not prepend when prefix is empty`() {
        assertEquals("912345678", RuleViewModel.normalizePhoneNumber("912345678", ""))
        assertEquals("912 345 678", RuleViewModel.normalizePhoneNumber("912 345 678", ""))
    }

    @Test
    fun `save prepends country code to target number`() = runTest {
        val repository = FakeRuleRepository()
        val vm = RuleViewModel(GetRuleUseCase(repository), AddRuleUseCase(repository), UpdateRuleUseCase(repository), repository, getDefaultPrefixNowUseCase)

        vm.onSenderChanged("BANK")
        vm.onTargetChanged("912345678")
        vm.saveRule()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.saved)
        val savedRule = repository.getRulesSnapshot().first()
        assertEquals("+351912345678", savedRule.targetNumber)
    }

    @Test
    fun `save prepends +351 to target with spaces`() = runTest {
        val repository = FakeRuleRepository()
        val vm = RuleViewModel(GetRuleUseCase(repository), AddRuleUseCase(repository), UpdateRuleUseCase(repository), repository, getDefaultPrefixNowUseCase)

        vm.onSenderChanged("BANK")
        vm.onTargetChanged("912 345 678")
        vm.saveRule()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.saved)
        assertEquals("+351912345678", repository.getRulesSnapshot().first().targetNumber)
    }

    @Test
    fun `save prepends +351 to target with dashes`() = runTest {
        val repository = FakeRuleRepository()
        val vm = RuleViewModel(GetRuleUseCase(repository), AddRuleUseCase(repository), UpdateRuleUseCase(repository), repository, getDefaultPrefixNowUseCase)

        vm.onSenderChanged("BANK")
        vm.onTargetChanged("912-345-678")
        vm.saveRule()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.saved)
        assertEquals("+351912345678", repository.getRulesSnapshot().first().targetNumber)
    }

    @Test
    fun `save does not double-prepend prefix when target already has country code`() = runTest {
        val repository = FakeRuleRepository()
        val vm = RuleViewModel(GetRuleUseCase(repository), AddRuleUseCase(repository), UpdateRuleUseCase(repository), repository, getDefaultPrefixNowUseCase)

        vm.onSenderChanged("BANK")
        vm.onTargetChanged("+351912345678")
        vm.saveRule()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.saved)
        assertEquals("+351912345678", repository.getRulesSnapshot().first().targetNumber)
    }

    @Test
    fun `save preserves foreign country code on target`() = runTest {
        val repository = FakeRuleRepository()
        val vm = RuleViewModel(GetRuleUseCase(repository), AddRuleUseCase(repository), UpdateRuleUseCase(repository), repository, getDefaultPrefixNowUseCase)

        vm.onSenderChanged("BANK")
        vm.onTargetChanged("+44123456789")
        vm.saveRule()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.saved)
        assertEquals("+44123456789", repository.getRulesSnapshot().first().targetNumber)
    }

    @Test
    fun `save does not prepend prefix when default prefix is None`() = runTest {
        settingsRepository.setDefaultPrefix("")
        val repository = FakeRuleRepository()
        val vm = RuleViewModel(GetRuleUseCase(repository), AddRuleUseCase(repository), UpdateRuleUseCase(repository), repository, getDefaultPrefixNowUseCase)

        vm.onSenderChanged("BANK")
        vm.onTargetChanged("912345678")
        vm.saveRule()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.saved)
        assertEquals("912345678", repository.getRulesSnapshot().first().targetNumber)

        // Reset for other tests
        settingsRepository.setDefaultPrefix("+351")
    }

    @Test
    fun `save rejects blank target with error`() = runTest {
        val repository = FakeRuleRepository()
        val vm = RuleViewModel(GetRuleUseCase(repository), AddRuleUseCase(repository), UpdateRuleUseCase(repository), repository, getDefaultPrefixNowUseCase)

        vm.onSenderChanged("BANK")
        vm.onTargetChanged("")
        vm.saveRule()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.error != null)
        assertEquals(0, repository.getRulesSnapshot().size)
    }

    @Test
    fun `save rejects target with fewer than 4 digits`() = runTest {
        val repository = FakeRuleRepository()
        val vm = RuleViewModel(GetRuleUseCase(repository), AddRuleUseCase(repository), UpdateRuleUseCase(repository), repository, getDefaultPrefixNowUseCase)

        vm.onSenderChanged("BANK")
        vm.onTargetChanged("123")
        vm.saveRule()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.error != null)
        assertEquals(0, repository.getRulesSnapshot().size)
    }

    @Test
    fun `save accepts valid target and senderText produces valid rule`() = runTest {
        val repository = FakeRuleRepository()
        val vm = RuleViewModel(GetRuleUseCase(repository), AddRuleUseCase(repository), UpdateRuleUseCase(repository), repository, getDefaultPrefixNowUseCase)

        vm.onSenderChanged("BANK")
        vm.onTargetChanged("912345678")
        vm.saveRule()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.saved)
        val rule = repository.getRulesSnapshot().first()
        assertTrue(rule.isValid())
        assertEquals("+351912345678", rule.targetNumber)
        assertEquals("BANK", rule.senderText)
    }

    @Test
    fun `save with numeric sender also normalizes sender`() = runTest {
        val repository = FakeRuleRepository()
        val vm = RuleViewModel(GetRuleUseCase(repository), AddRuleUseCase(repository), UpdateRuleUseCase(repository), repository, getDefaultPrefixNowUseCase)

        vm.onSenderChanged("912111222")
        vm.onTargetChanged("933444555")
        vm.saveRule()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.saved)
        val rule = repository.getRulesSnapshot().first()
        assertEquals("+351912111222", rule.senderText)
        assertEquals("+351933444555", rule.targetNumber)
    }

    @Test
    fun `save rejects duplicate rule with same sender and target`() = runTest {
        val repository = FakeRuleRepository(
            listOf(Rule(id = 1, name = "Existing", senderText = "BANK", phoneNumber = "BANK", targetNumber = "+351912345678"))
        )
        val vm = RuleViewModel(GetRuleUseCase(repository), AddRuleUseCase(repository), UpdateRuleUseCase(repository), repository, getDefaultPrefixNowUseCase)

        vm.onSenderChanged("BANK")
        vm.onTargetChanged("+351912345678")
        vm.saveRule(duplicateRuleError = "This rule already exists")
        advanceUntilIdle()

        assertEquals("This rule already exists", vm.uiState.value.error)
        assertEquals(false, vm.uiState.value.saved)
        assertEquals(1, repository.getRulesSnapshot().size)
    }

    @Test
    fun `save allows rule with same sender but different target`() = runTest {
        val repository = FakeRuleRepository(
            listOf(Rule(id = 1, name = "Existing", senderText = "BANK", phoneNumber = "BANK", targetNumber = "+351912345678"))
        )
        val vm = RuleViewModel(GetRuleUseCase(repository), AddRuleUseCase(repository), UpdateRuleUseCase(repository), repository, getDefaultPrefixNowUseCase)

        vm.onSenderChanged("BANK")
        vm.onTargetChanged("+351999888777")
        vm.saveRule()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.saved)
        assertEquals(2, repository.getRulesSnapshot().size)
    }

    @Test
    fun `update existing rule does not flag itself as duplicate`() = runTest {
        val repository = FakeRuleRepository(
            listOf(Rule(id = 5, name = "MyRule", senderText = "SHOP", phoneNumber = "SHOP", targetNumber = "+351911222333"))
        )
        val vm = RuleViewModel(GetRuleUseCase(repository), AddRuleUseCase(repository), UpdateRuleUseCase(repository), repository, getDefaultPrefixNowUseCase)

        vm.loadRule(5)
        advanceUntilIdle()
        vm.onNameChanged("Renamed Rule")
        vm.saveRule()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.saved)
        assertEquals(null, vm.uiState.value.error)
    }
}
