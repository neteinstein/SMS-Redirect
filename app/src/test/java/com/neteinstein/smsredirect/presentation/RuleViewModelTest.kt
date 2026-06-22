package com.neteinstein.smsredirect.presentation

import com.neteinstein.smsredirect.FakeRuleRepository
import com.neteinstein.smsredirect.FakeSettingsRepository
import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.usecase.AddRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.GetDefaultPrefixNowUseCase
import com.neteinstein.smsredirect.domain.usecase.GetRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.UpdateRuleUseCase
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
    fun `loads existing rule for editing`() =
        runTest {
            val repository =
                FakeRuleRepository(
                    listOf(Rule(id = 1, senderText = "BANK", phoneNumber = "123", targetNumber = "777")),
                )
            val vm =
                RuleViewModel(
                    GetRuleUseCase(repository),
                    AddRuleUseCase(repository),
                    UpdateRuleUseCase(repository),
                    repository,
                    getDefaultPrefixNowUseCase,
                )

            vm.loadRule(1)
            advanceUntilIdle()

            assertEquals("BANK", vm.uiState.value.senderText)
            assertEquals("777", vm.uiState.value.targetNumber)
        }

    @Test
    fun `save validates and reports error`() =
        runTest {
            val repository = FakeRuleRepository()
            val vm =
                RuleViewModel(
                    GetRuleUseCase(repository),
                    AddRuleUseCase(repository),
                    UpdateRuleUseCase(repository),
                    repository,
                    getDefaultPrefixNowUseCase,
                )

            vm.saveRule()
            advanceUntilIdle()

            assertTrue(vm.uiState.value.error != null)
        }

    @Test
    fun `loads existing rule with phoneNumber as sender when senderText is blank`() =
        runTest {
            val repository =
                FakeRuleRepository(
                    listOf(Rule(id = 2, senderText = "", phoneNumber = "+1555123", targetNumber = "999")),
                )
            val vm =
                RuleViewModel(
                    GetRuleUseCase(repository),
                    AddRuleUseCase(repository),
                    UpdateRuleUseCase(repository),
                    repository,
                    getDefaultPrefixNowUseCase,
                )

            vm.loadRule(2)
            advanceUntilIdle()

            assertEquals("+1555123", vm.uiState.value.senderText)
            assertEquals("999", vm.uiState.value.targetNumber)
        }

    @Test
    fun `save inserts valid rule`() =
        runTest {
            val repository = FakeRuleRepository()
            val vm =
                RuleViewModel(
                    GetRuleUseCase(repository),
                    AddRuleUseCase(repository),
                    UpdateRuleUseCase(repository),
                    repository,
                    getDefaultPrefixNowUseCase,
                )

            vm.onSenderChanged("SHOP")
            vm.onTargetChanged("4445")
            vm.saveRule()
            advanceUntilIdle()

            assertTrue(vm.uiState.value.saved)
        }

    @Test
    fun `save generates default name when empty`() =
        runTest {
            val repository = FakeRuleRepository()
            val vm =
                RuleViewModel(
                    GetRuleUseCase(repository),
                    AddRuleUseCase(repository),
                    UpdateRuleUseCase(repository),
                    repository,
                    getDefaultPrefixNowUseCase,
                )

            vm.onSenderChanged("TEST")
            vm.onTargetChanged("5556")
            vm.saveRule()
            advanceUntilIdle()

            assertTrue(vm.uiState.value.saved)
            val savedRule = repository.getRulesSnapshot().first()
            assertEquals("Rule #1", savedRule.name)
        }

    @Test
    fun `save preserves custom name`() =
        runTest {
            val repository = FakeRuleRepository()
            val vm =
                RuleViewModel(
                    GetRuleUseCase(repository),
                    AddRuleUseCase(repository),
                    UpdateRuleUseCase(repository),
                    repository,
                    getDefaultPrefixNowUseCase,
                )

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
    fun `save prepends country code to target number`() =
        runTest {
            val repository = FakeRuleRepository()
            val vm =
                RuleViewModel(
                    GetRuleUseCase(repository),
                    AddRuleUseCase(repository),
                    UpdateRuleUseCase(repository),
                    repository,
                    getDefaultPrefixNowUseCase,
                )

            vm.onSenderChanged("BANK")
            vm.onTargetChanged("912345678")
            vm.saveRule()
            advanceUntilIdle()

            assertTrue(vm.uiState.value.saved)
            val savedRule = repository.getRulesSnapshot().first()
            assertEquals("+351912345678", savedRule.targetNumber)
        }
}
