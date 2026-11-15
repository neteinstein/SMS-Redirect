package com.neteinstein.smsredirect.domain.usecase

import app.cash.turbine.test
import com.neteinstein.smsredirect.FakeDefaultSmsAppChecker
import com.neteinstein.smsredirect.FakeMessageSender
import com.neteinstein.smsredirect.FakeMessageStore
import com.neteinstein.smsredirect.FakeMmsDownloadRequester
import com.neteinstein.smsredirect.domain.messaging.SendFailure
import com.neteinstein.smsredirect.domain.messaging.SendOutcome
import com.neteinstein.smsredirect.domain.model.Conversation
import com.neteinstein.smsredirect.domain.model.ConversationMessage
import com.neteinstein.smsredirect.domain.model.MessageStatus
import com.neteinstein.smsredirect.domain.model.OutgoingAttachment
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagingUseCasesTest {
    private val sender = FakeMessageSender()
    private val checker = FakeDefaultSmsAppChecker(isDefault = true)
    private val send = SendMessageUseCase(sender, checker)
    private val picture = OutgoingAttachment("content://media/1", "image/jpeg", "beach.jpg")

    @Test
    fun `plain text to one person is an SMS`() =
        runTest {
            val outcome = send(listOf("+351912345678"), "  Olá  ")

            assertEquals(SendOutcome.Queued(1L), outcome)
            assertEquals(listOf(FakeMessageSender.Sms("+351912345678", "Olá")), sender.sentSms)
            assertTrue(sender.sentMms.isEmpty())
        }

    @Test
    fun `an attachment makes it an MMS, even without text`() =
        runTest {
            send(listOf("+351912345678"), "", listOf(picture))

            assertEquals(listOf(FakeMessageSender.Mms(listOf("+351912345678"), "", listOf(picture))), sender.sentMms)
            assertTrue(sender.sentSms.isEmpty())
        }

    @Test
    fun `more than one recipient is a group MMS`() =
        runTest {
            send(listOf("+351912345678", "+351930000000"), "Jantar?")

            assertEquals(listOf("+351912345678", "+351930000000"), sender.sentMms.single().addresses)
        }

    @Test
    fun `the same person typed twice is still one recipient`() =
        runTest {
            send(listOf("+351 912 345 678", "912345678", " "), "hi")

            assertEquals(1, sender.sentSms.size)
            assertTrue(sender.sentMms.isEmpty())
        }

    @Test
    fun `text to an email address is an MMS, since SMS cannot reach it`() =
        runTest {
            send(listOf("ana@example.com"), "hi")

            assertEquals(listOf("ana@example.com"), sender.sentMms.single().addresses)
            assertTrue(sender.sentSms.isEmpty())
        }

    @Test
    fun `numbers sharing their last digits but not their country are two recipients`() =
        runTest {
            send(listOf("+12025550123", "+442025550123"), "hi")

            assertEquals(listOf("+12025550123", "+442025550123"), sender.sentMms.single().addresses)
        }

    @Test
    fun `nothing is sent unless this app is the default SMS app`() =
        runTest {
            checker.isDefault = false

            val outcome = send(listOf("+351912345678"), "hi")

            assertEquals(SendOutcome.Failed(SendFailure.NOT_DEFAULT_SMS_APP), outcome)
            assertTrue(sender.sentSms.isEmpty())
        }

    @Test
    fun `an empty message or no recipient is refused`() =
        runTest {
            assertEquals(SendOutcome.Failed(SendFailure.EMPTY_MESSAGE), send(listOf("+351912345678"), "   "))
            assertEquals(SendOutcome.Failed(SendFailure.NO_RECIPIENTS), send(listOf(" "), "hi"))
            assertTrue(sender.sentSms.isEmpty() && sender.sentMms.isEmpty())
        }

    @Test
    fun `conversations are newest first and refresh when the store changes`() =
        runTest {
            val store =
                FakeMessageStore(
                    conversations =
                        listOf(
                            Conversation(1, listOf("a"), "old", 1_000, unread = false, hasAttachment = false),
                            Conversation(2, listOf("b"), "new", 2_000, unread = true, hasAttachment = false),
                        ),
                )

            GetConversationsUseCase(store)().test {
                assertEquals(listOf(2L, 1L), awaitItem().map { it.threadId })
                store.conversationList += Conversation(3, listOf("c"), "newest", 3_000, unread = true, hasAttachment = false)
                store.notifyChanged()
                assertEquals(listOf(3L, 2L, 1L), awaitItem().map { it.threadId })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `messages of a conversation are oldest first`() =
        runTest {
            val store =
                FakeMessageStore(
                    messages =
                        listOf(
                            ConversationMessage(2, true, 7, "a", "later", 2_000, MessageStatus.RECEIVED),
                            ConversationMessage(1, false, 7, "a", "earlier", 1_000, MessageStatus.SENT),
                            ConversationMessage(3, false, 8, "b", "other thread", 500, MessageStatus.SENT),
                        ),
                )

            GetConversationMessagesUseCase(store)(7).test {
                assertEquals(listOf("earlier", "later"), awaitItem().map { it.body })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a conversation for recipients is found or created, never for nobody`() =
        runTest {
            val store = FakeMessageStore()
            val useCase = GetOrCreateConversationUseCase(store)

            val first = useCase(listOf("+351912345678"))
            val again = useCase(listOf(" +351912345678 "))

            assertEquals(first, again)
            assertNull(useCase(listOf("", " ")))
        }

    @Test
    fun `only a not-downloaded MMS is downloaded`() =
        runTest {
            val requester = FakeMmsDownloadRequester()
            val download = DownloadMmsUseCase(requester)
            val pending = ConversationMessage(5, true, 7, "a", "", 1_000, MessageStatus.NOT_DOWNLOADED)

            assertTrue(download(pending))
            assertFalse(download(pending.copy(status = MessageStatus.DOWNLOADING)))
            assertFalse(download(pending.copy(isMms = false)))
            assertEquals(listOf(5L), requester.requested)
        }
}
