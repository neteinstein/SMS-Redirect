package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.FakeRuleRepository
import com.neteinstein.smsredirect.domain.model.Rule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateDeleteRuleUseCaseTest {
    @Test
    fun `updates rule`() =
        runTest {
            val repository =
                FakeRuleRepository(
                    listOf(Rule(id = 1, senderText = "OLD", phoneNumber = "", targetNumber = "111")),
                )
            val update = UpdateRuleUseCase(repository)

            val result = update(Rule(id = 1, senderText = "NEW", phoneNumber = "123", targetNumber = "999"))
            val updated = repository.getRules().first().first()

            assertTrue(result.isSuccess)
            assertEquals("NEW", updated.senderText)
            assertEquals("123", updated.phoneNumber)
            assertEquals("999", updated.targetNumber)
        }

    @Test
    fun `deletes rule`() =
        runTest {
            val repository = FakeRuleRepository(listOf(Rule(id = 1, senderText = "A", targetNumber = "123")))
            val delete = DeleteRuleUseCase(repository)

            delete(Rule(id = 1, senderText = "A", targetNumber = "123"))

            assertTrue(repository.getRules().first().isEmpty())
        }
}
