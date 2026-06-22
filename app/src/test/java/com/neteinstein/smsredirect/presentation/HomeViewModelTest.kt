package com.neteinstein.smsredirect.presentation

import com.neteinstein.smsredirect.FakeRuleRepository
import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.usecase.DeleteRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.GetRulesUseCase
import com.neteinstein.smsredirect.domain.usecase.ReorderRulesUseCase
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
        val repository = FakeRuleRepository(listOf(Rule(id = 1, senderText = "TEST", targetNumber = "555")))
        val vm = HomeViewModel(GetRulesUseCase(repository), DeleteRuleUseCase(repository), ReorderRulesUseCase(repository))

        advanceUntilIdle()
        assertEquals(1, vm.rules.value.size)

        vm.deleteRule(vm.rules.value.first())
        advanceUntilIdle()

        assertEquals(0, vm.rules.value.size)
    }

    @Test
    fun `moves rule and commits reorder`() = runTest {
        val repository = FakeRuleRepository(listOf(
            Rule(id = 1, name = "First", senderText = "A", targetNumber = "111", sortOrder = 0),
            Rule(id = 2, name = "Second", senderText = "B", targetNumber = "222", sortOrder = 1),
            Rule(id = 3, name = "Third", senderText = "C", targetNumber = "333", sortOrder = 2)
        ))
        val vm = HomeViewModel(GetRulesUseCase(repository), DeleteRuleUseCase(repository), ReorderRulesUseCase(repository))

        advanceUntilIdle()
        assertEquals(3, vm.rules.value.size)

        vm.moveRule(0, 2)
        assertEquals("Second", vm.rules.value[0].name)
        assertEquals("Third", vm.rules.value[1].name)
        assertEquals("First", vm.rules.value[2].name)

        vm.commitReorder()
        advanceUntilIdle()

        val saved = repository.getRulesSnapshot()
        assertEquals(0, saved[0].sortOrder)
        assertEquals(1, saved[1].sortOrder)
        assertEquals(2, saved[2].sortOrder)
    }
}
