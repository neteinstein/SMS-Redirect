package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.FakeRuleRepository
import com.neteinstein.smsredirect.FakeSettingsRepository
import com.neteinstein.smsredirect.domain.model.Rule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardingUseCasesTest {
    @Test
    fun `find matching rule by sender text`() =
        runTest {
            val repository =
                FakeRuleRepository(
                    listOf(Rule(id = 1, senderText = "BANK", targetNumber = "555")),
                )
            val useCase = FindMatchingRuleUseCase(repository)

            val result = useCase("BANK-ALERT")

            assertNotNull(result)
            assertEquals("555", result?.targetNumber)
        }

    @Test
    fun `find matching rule by phone number`() =
        runTest {
            val repository =
                FakeRuleRepository(
                    listOf(Rule(id = 1, phoneNumber = "+1 (555) 222-3333", targetNumber = "999")),
                )
            val useCase = FindMatchingRuleUseCase(repository)

            val result = useCase("15552223333")

            assertNotNull(result)
            assertEquals("999", result?.targetNumber)
        }

    @Test
    fun `string sender rule does not match other text-only senders`() =
        runTest {
            val repository =
                FakeRuleRepository(
                    listOf(Rule(id = 1, senderText = "Amazon", phoneNumber = "Amazon", targetNumber = "555")),
                )
            val useCase = FindMatchingRuleUseCase(repository)

            // Should match the exact sender
            assertNotNull(useCase("Amazon"))
            // Should NOT match a different text sender
            assertNull(useCase("Google"))
            assertNull(useCase("MyBank"))
        }

    @Test
    fun `string sender rule with no digits does not match digit-only senders`() =
        runTest {
            val repository =
                FakeRuleRepository(
                    listOf(Rule(id = 1, senderText = "Amazon", phoneNumber = "Amazon", targetNumber = "555")),
                )
            val useCase = FindMatchingRuleUseCase(repository)

            assertNull(useCase("15551234567"))
        }

    @Test
    fun `can forward in roaming only when enabled`() =
        runTest {
            val settingsRepository = FakeSettingsRepository(initial = false)
            val useCase = CanForwardSmsUseCase(settingsRepository)

            assertFalse(useCase(isRoaming = true))

            settingsRepository.setSendWhileRoaming(true)
            assertTrue(useCase(isRoaming = true))
            assertTrue(useCase(isRoaming = false))
        }
}
