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
    fun `find matching rule by sender text - case insensitive exact match`() =
        runTest {
            val repository =
                FakeRuleRepository(
                    listOf(Rule(id = 1, senderText = "BANK", targetNumber = "555")),
                )
            val useCase = FindMatchingRuleUseCase(repository)

            // Exact match (case insensitive)
            assertNotNull(useCase("BANK"))
            assertNotNull(useCase("bank"))
            assertNotNull(useCase("Bank"))
            // Substring should NOT match
            assertNull(useCase("BANK-ALERT"))
            assertNull(useCase("MY-BANK"))
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

            // Case insensitive exact match
            assertNotNull(useCase("Amazon"))
            assertNotNull(useCase("AMAZON"))
            assertNotNull(useCase("amazon"))
            // Different senders should NOT match
            assertNull(useCase("Google"))
            assertNull(useCase("MyBank"))
            // Substrings should NOT match
            assertNull(useCase("Amazon X"))
            assertNull(useCase("NotAmazon"))
        }

    @Test
    fun `sender with leading or trailing spaces matches trimmed rule`() =
        runTest {
            val repository =
                FakeRuleRepository(
                    listOf(Rule(id = 1, senderText = "  Amazon  ", phoneNumber = "Amazon", targetNumber = "555")),
                )
            val useCase = FindMatchingRuleUseCase(repository)

            // Sender with spaces should still match after trimming
            assertNotNull(useCase("Amazon"))
            assertNotNull(useCase("  Amazon  "))
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
