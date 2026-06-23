package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.FakeRuleRepository
import com.neteinstein.smsredirect.domain.model.Rule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderRulesUseCaseTest {
    @Test
    fun `reorder assigns sortOrder based on list position`() =
        runTest {
            val repository =
                FakeRuleRepository(
                    listOf(
                        Rule(id = 1, senderText = "A", targetNumber = "111", sortOrder = 0),
                        Rule(id = 2, senderText = "B", targetNumber = "222", sortOrder = 1),
                        Rule(id = 3, senderText = "C", targetNumber = "333", sortOrder = 2),
                    ),
                )
            val useCase = ReorderRulesUseCase(repository)

            // Reorder: C, A, B
            val reordered =
                listOf(
                    Rule(id = 3, senderText = "C", targetNumber = "333", sortOrder = 2),
                    Rule(id = 1, senderText = "A", targetNumber = "111", sortOrder = 0),
                    Rule(id = 2, senderText = "B", targetNumber = "222", sortOrder = 1),
                )
            useCase(reordered)

            val result = repository.getRulesSnapshot()
            assertEquals(3, result.size)
            assertEquals(3L, result[0].id)
            assertEquals(0, result[0].sortOrder)
            assertEquals(1L, result[1].id)
            assertEquals(1, result[1].sortOrder)
            assertEquals(2L, result[2].id)
            assertEquals(2, result[2].sortOrder)
        }

    @Test
    fun `reorder single rule sets sortOrder to 0`() =
        runTest {
            val repository =
                FakeRuleRepository(
                    listOf(Rule(id = 1, senderText = "A", targetNumber = "111", sortOrder = 5)),
                )
            val useCase = ReorderRulesUseCase(repository)

            useCase(listOf(Rule(id = 1, senderText = "A", targetNumber = "111", sortOrder = 5)))

            val result = repository.getRulesSnapshot()
            assertEquals(1, result.size)
            assertEquals(0, result[0].sortOrder)
        }

    @Test
    fun `reorder empty list produces empty result`() =
        runTest {
            val repository = FakeRuleRepository()
            val useCase = ReorderRulesUseCase(repository)

            useCase(emptyList())

            assertEquals(0, repository.getRulesSnapshot().size)
        }

    @Test
    fun `reorder preserves rule properties`() =
        runTest {
            val repository =
                FakeRuleRepository(
                    listOf(Rule(id = 1, name = "MyRule", senderText = "BANK", phoneNumber = "+1555", targetNumber = "999", sortOrder = 3)),
                )
            val useCase = ReorderRulesUseCase(repository)

            useCase(listOf(Rule(id = 1, name = "MyRule", senderText = "BANK", phoneNumber = "+1555", targetNumber = "999", sortOrder = 3)))

            val result = repository.getRulesSnapshot().first()
            assertEquals("MyRule", result.name)
            assertEquals("BANK", result.senderText)
            assertEquals("+1555", result.phoneNumber)
            assertEquals("999", result.targetNumber)
            assertEquals(0, result.sortOrder)
        }
}
