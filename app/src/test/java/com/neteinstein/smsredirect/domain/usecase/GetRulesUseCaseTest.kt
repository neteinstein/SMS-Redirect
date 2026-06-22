package com.neteinstein.smsredirect.domain.usecase

import app.cash.turbine.test
import com.neteinstein.smsredirect.FakeRuleRepository
import com.neteinstein.smsredirect.domain.model.Rule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetRulesUseCaseTest {
    @Test
    fun `returns flow of rules from repository`() =
        runTest {
            val repository =
                FakeRuleRepository(
                    listOf(
                        Rule(id = 1, senderText = "A", targetNumber = "111"),
                        Rule(id = 2, senderText = "B", targetNumber = "222"),
                    ),
                )
            val useCase = GetRulesUseCase(repository)

            useCase().test {
                val items = awaitItem()
                assertEquals(2, items.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `emits empty list when no rules exist`() =
        runTest {
            val repository = FakeRuleRepository()
            val useCase = GetRulesUseCase(repository)

            useCase().test {
                assertEquals(emptyList<Rule>(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `emits updated list when rules change`() =
        runTest {
            val repository = FakeRuleRepository()
            val useCase = GetRulesUseCase(repository)

            useCase().test {
                assertEquals(0, awaitItem().size)
                repository.insert(Rule(senderText = "NEW", targetNumber = "999"))
                assertEquals(1, awaitItem().size)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
