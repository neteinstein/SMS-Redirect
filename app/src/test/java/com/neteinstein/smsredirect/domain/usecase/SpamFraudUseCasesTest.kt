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
    fun `an mms notification is checked with its subject`() =
        runTest {
            val useCase = AssessIncomingMessageUseCase(SpamFraudDetector(), FakeTrustedSenderRepository(), FakeContactNameResolver())

            assertFalse(useCase(stranger, "https://example.com").suspicious)
            assertTrue(useCase(stranger, "https://example.com", subject = "Verify your account").suspicious)
        }

    @Test
    fun `distinct senders whose addresses differ only in punctuation are both listed`() =
        runTest {
            val useCase = AssessConversationRiskUseCase(SpamFraudDetector(), FakeContactNameResolver())
            val messages = listOf(message(1, "ann.lee@company.com", scam), message(2, "annlee@company.com", scam))

            val risk = useCase(messages, trusted = emptySet())

            assertEquals(listOf("ann.lee@company.com", "annlee@company.com"), risk.flaggedSenders)
        }

    @Test
    fun `the result lists every message it covered`() =
        runTest {
            val useCase = AssessConversationRiskUseCase(SpamFraudDetector(), FakeContactNameResolver())
            val messages = listOf(message(1, stranger, "Hello"), message(2, stranger, "Hi", status = MessageStatus.SENT))

            assertEquals(setOf("sms-1", "sms-2"), useCase(messages, trusted = emptySet()).assessedMessageKeys)
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
    fun `links in a long subject do not hide a scam link in the body`() =
        runTest {
            val useCase = AssessConversationRiskUseCase(SpamFraudDetector(), FakeContactNameResolver())
            val mms = message(1, stranger, scam).copy(isMms = true, subject = "example.com ".repeat(200))

            assertEquals(setOf("mms-1"), useCase(listOf(mms), trusted = emptySet()).flaggedMessageKeys)
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
            assertFalse(assess(messages, repository.get()).isFlagged)
        }

    @Test
    fun `trusting a sender twice keeps one entry`() =
        runTest {
            val repository = FakeTrustedSenderRepository(setOf(stranger))

            TrustSendersUseCase(repository)(listOf("00351910000000", stranger))

            assertEquals(setOf(stranger), repository.get())
        }

    @Test
    fun `stop trusting removes the sender in whatever form it was stored`() =
        runTest {
            // Given
            val repository = FakeTrustedSenderRepository(setOf("910000000", friend))

            // When
            UntrustSendersUseCase(repository)(listOf(stranger))

            // Then
            assertEquals(setOf(friend), repository.get())
        }

    @Test
    fun `finds which people of a conversation are trusted`() {
        assertEquals(listOf(stranger), GetTrustedAmongUseCase()(listOf(stranger, friend), setOf("910000000")))
    }

    @Test
    fun `a contact's scam-looking message is not flagged`() =
        runTest {
            val useCase = AssessConversationRiskUseCase(SpamFraudDetector(), FakeContactNameResolver(mapOf(friend to "Ana")))

            val risk = useCase(listOf(message(1, friend, scam)), trusted = emptySet())

            assertFalse(risk.isFlagged)
            assertEquals(setOf("sms-1"), risk.assessedMessageKeys)
        }
}
