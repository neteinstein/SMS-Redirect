package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.FakeContactNameResolver
import com.neteinstein.smsredirect.FakeTrustedSenderRepository
import com.neteinstein.smsredirect.domain.messaging.SpamFraudDetector
import com.neteinstein.smsredirect.domain.model.ConversationMessage
import com.neteinstein.smsredirect.domain.model.MessageStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpamFraudUseCasesTest {
    private val scam = "Your parcel is held. Pay the fee at bit.ly/abc"
    private val stranger = "+351910000000"
    private val friend = "+351912345678"

    private fun message(
        id: Long,
        address: String,
        body: String,
        status: MessageStatus = MessageStatus.RECEIVED,
    ) = ConversationMessage(id, false, 1, address, body, id, status)

    @Test
    fun `a scam from a stranger is flagged for the notification`() =
        runTest {
            // Given
            val useCase = AssessIncomingMessageUseCase(SpamFraudDetector(), FakeTrustedSenderRepository(), FakeContactNameResolver())

            // When
            val result = useCase(stranger, scam)

            // Then
            assertTrue(result.suspicious)
        }

    @Test
    fun `contacts and trusted senders are never flagged`() =
        runTest {
            val useCase =
                AssessIncomingMessageUseCase(
                    SpamFraudDetector(),
                    FakeTrustedSenderRepository(setOf("910000000")),
                    FakeContactNameResolver(mapOf(friend to "Ana")),
                )

            assertFalse(useCase(friend, scam).suspicious)
            // Trusted as a local number, received with the country code: still the same sender.
            assertFalse(useCase(stranger, scam).suspicious)
        }

    @Test
    fun `only received scam messages of a conversation are flagged`() =
        runTest {
            // Given
            val useCase = AssessConversationRiskUseCase(SpamFraudDetector(), FakeContactNameResolver())
            val messages =
                listOf(
                    message(1, stranger, "Hello"),
                    message(2, stranger, scam),
                    message(3, stranger, scam, status = MessageStatus.SENT),
                )

            // When
            val risk = useCase(messages, trusted = emptySet())

            // Then
            assertEquals(setOf("sms-2"), risk.flaggedMessageKeys)
            assertEquals(listOf(stranger), risk.flaggedSenders)
            assertTrue(risk.isFlagged)
        }

    @Test
    fun `trusting the sender clears the conversation`() =
        runTest {
            // Given
            val repository = FakeTrustedSenderRepository()
            val assess = AssessConversationRiskUseCase(SpamFraudDetector(), FakeContactNameResolver())
            val messages = listOf(message(1, stranger, scam))

            // When
            TrustSendersUseCase(repository)(listOf(stranger))

            // Then
            assertEquals(ConversationRisk.NONE, assess(messages, repository.get()))
        }

    @Test
    fun `trusting a sender twice keeps one entry`() =
        runTest {
            val repository = FakeTrustedSenderRepository(setOf(stranger))

            TrustSendersUseCase(repository)(listOf("00351910000000", stranger))

            assertEquals(setOf(stranger), repository.get())
        }

    @Test
    fun `a contact's scam-looking message is not flagged`() =
        runTest {
            val useCase = AssessConversationRiskUseCase(SpamFraudDetector(), FakeContactNameResolver(mapOf(friend to "Ana")))

            assertEquals(ConversationRisk.NONE, useCase(listOf(message(1, friend, scam)), trusted = emptySet()))
        }
}
