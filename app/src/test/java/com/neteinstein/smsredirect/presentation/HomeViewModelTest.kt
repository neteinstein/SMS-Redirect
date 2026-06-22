package com.neteinstein.smsredirect.presentation

import com.neteinstein.smsredirect.FakeRuleRepository
import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.usecase.DeleteRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.GetRulesUseCase
import com.neteinstein.smsredirect.presentation.home.HomeViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
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
    fun `loads and deletes rules`() = runTest {
        val repository = FakeRuleRepository(listOf(Rule(id = 1, senderText = "TEST")))
        val vm = HomeViewModel(GetRulesUseCase(repository), DeleteRuleUseCase(repository))

        advanceUntilIdle()
        assertEquals(1, vm.rules.value.size)

        vm.deleteRule(vm.rules.value.first())
        advanceUntilIdle()

        assertEquals(0, vm.rules.value.size)
    }
}
