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
    private val settings = FakeSettingsRepository()

    @Test
    fun `find matching rule by sender text - case insensitive exact match`() =
        runTest {
            val repository =
                FakeRuleRepository(
                    listOf(Rule(id = 1, senderText = "BANK", targetNumber = "555")),
                )
            val useCase = FindMatchingRuleUseCase(repository, settings)

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
            val useCase = FindMatchingRuleUseCase(repository, settings)

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
            val useCase = FindMatchingRuleUseCase(repository, settings)

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
            val useCase = FindMatchingRuleUseCase(repository, settings)

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
            val useCase = FindMatchingRuleUseCase(repository, settings)

            assertNull(useCase("15551234567"))
        }

    @Test
    fun `rule saved with the country code matches a sender without it`() =
        runTest {
            val rule = Rule(id = 1, senderText = "+351912345678", phoneNumber = "+351912345678", targetNumber = "555")
            val useCase = FindMatchingRuleUseCase(FakeRuleRepository(listOf(rule)), settings)

            assertEquals(rule, useCase("912345678"))
            assertEquals(rule, useCase("+351912345678"))
            assertNull(useCase("+44912345678"))
        }

    @Test
    fun `rule saved without a country code matches a sender with it`() =
        runTest {
            val rule = Rule(id = 1, senderText = "5551234", phoneNumber = "5551234", targetNumber = "555")
            val useCase = FindMatchingRuleUseCase(FakeRuleRepository(listOf(rule)), settings)

            assertEquals(rule, useCase("+3515551234"))
            assertEquals(rule, useCase("5551234"))
        }

    @Test
    fun `short code rule matches using the default prefix from settings`() =
        runTest {
            val rule = Rule(id = 1, senderText = "+3514800", phoneNumber = "+3514800", targetNumber = "555")
            val useCase = FindMatchingRuleUseCase(FakeRuleRepository(listOf(rule)), settings)

            assertEquals(rule, useCase("4800"))
            assertNull(useCase("14800"))

            settings.setDefaultPrefix("+34")
            assertNull(useCase("4800"))
        }

    @Test
    fun `first matching redirect rule wins when local and international forms both match`() =
        runTest {
            val other = Rule(id = 1, phoneNumber = "+351911111111", targetNumber = "000", sortOrder = 0)
            val international = Rule(id = 2, phoneNumber = "+351912345678", targetNumber = "111", sortOrder = 1)
            val local = Rule(id = 3, phoneNumber = "912345678", targetNumber = "222", sortOrder = 2)
            val useCase = FindMatchingRuleUseCase(FakeRuleRepository(listOf(other, international, local)), settings)
            val reversed = FindMatchingRuleUseCase(FakeRuleRepository(listOf(other, local, international)), settings)

            assertEquals(international, useCase("912345678"))
            assertEquals(international, useCase("+351912345678"))
            assertEquals(local, reversed("912345678"))
            assertEquals(local, reversed("+351912345678"))
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
