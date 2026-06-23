package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.FakeRuleRepository
import com.neteinstein.smsredirect.domain.model.Rule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddRuleUseCaseTest {
    @Test
    fun `returns error when target missing or sender and phone are blank`() =
        runTest {
            val useCase = AddRuleUseCase(FakeRuleRepository())

            val result = useCase(Rule(senderText = " ", phoneNumber = "", targetNumber = ""))

            assertTrue(result.isFailure)
        }

    @Test
    fun `inserts valid rule`() =
        runTest {
            val repository = FakeRuleRepository()
            val useCase = AddRuleUseCase(repository)

            val result = useCase(Rule(senderText = "BANK", targetNumber = "5551234"))
            val rules = repository.getRulesValue()

            assertTrue(result.isSuccess)
            assertEquals(1, rules.size)
            assertEquals("BANK", rules.first().senderText)
            assertEquals("5551234", rules.first().targetNumber)
        }
}

private suspend fun FakeRuleRepository.getRulesValue() = getRules().first()
