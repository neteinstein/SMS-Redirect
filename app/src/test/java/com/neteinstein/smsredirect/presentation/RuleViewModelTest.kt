package com.neteinstein.smsredirect.presentation

import com.neteinstein.smsredirect.FakeRuleRepository
import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.usecase.AddRuleUseCase
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
        val repository = FakeRuleRepository(listOf(Rule(id = 1, senderText = "BANK", phoneNumber = "123")))
        val vm = RuleViewModel(GetRuleUseCase(repository), AddRuleUseCase(repository), UpdateRuleUseCase(repository))

        vm.loadRule(1)
        advanceUntilIdle()

        assertEquals("BANK", vm.uiState.value.senderText)
        assertEquals("123", vm.uiState.value.phoneNumber)
    }

    @Test
    fun `save validates and reports error`() = runTest {
        val repository = FakeRuleRepository()
        val vm = RuleViewModel(GetRuleUseCase(repository), AddRuleUseCase(repository), UpdateRuleUseCase(repository))

        vm.saveRule()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.error?.contains("required") == true)
    }

    @Test
    fun `save inserts valid rule`() = runTest {
        val repository = FakeRuleRepository()
        val vm = RuleViewModel(GetRuleUseCase(repository), AddRuleUseCase(repository), UpdateRuleUseCase(repository))

        vm.onSenderChanged("SHOP")
        vm.saveRule()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.saved)
    }
}
