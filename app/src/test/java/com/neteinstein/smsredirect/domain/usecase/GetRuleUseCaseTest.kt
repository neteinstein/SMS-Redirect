package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.FakeRuleRepository
import com.neteinstein.smsredirect.domain.model.Rule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetRuleUseCaseTest {
    @Test
    fun `returns rule by valid id`() =
        runTest {
            val repository =
                FakeRuleRepository(
                    listOf(Rule(id = 1, senderText = "BANK", targetNumber = "555")),
                )
            val useCase = GetRuleUseCase(repository)

            val result = useCase(1L)

            assertEquals(1L, result?.id)
            assertEquals("BANK", result?.senderText)
        }

    @Test
    fun `returns null when rule does not exist`() =
        runTest {
            val repository =
                FakeRuleRepository(
                    listOf(Rule(id = 1, senderText = "BANK", targetNumber = "555")),
                )
            val useCase = GetRuleUseCase(repository)

            val result = useCase(99L)

            assertNull(result)
        }

    @Test
    fun `returns null for empty repository`() =
        runTest {
            val repository = FakeRuleRepository()
            val useCase = GetRuleUseCase(repository)

            val result = useCase(1L)

            assertNull(result)
        }
}
