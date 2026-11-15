package com.neteinstein.smsredirect.presentation

import com.neteinstein.smsredirect.FakeContactNameResolver
import com.neteinstein.smsredirect.FakeDefaultSmsAppChecker
import com.neteinstein.smsredirect.FakeMessageSender
import com.neteinstein.smsredirect.FakeMessageStore
import com.neteinstein.smsredirect.domain.messaging.SendFailure
import com.neteinstein.smsredirect.domain.messaging.SendOutcome
import com.neteinstein.smsredirect.domain.model.ConversationMessage
import com.neteinstein.smsredirect.domain.model.MessageStatus
import com.neteinstein.smsredirect.domain.model.OutgoingAttachment
import com.neteinstein.smsredirect.domain.usecase.DeleteMessageUseCase
import com.neteinstein.smsredirect.domain.usecase.GetConversationAddressesUseCase
import com.neteinstein.smsredirect.domain.usecase.GetConversationMessagesUseCase
import com.neteinstein.smsredirect.domain.usecase.GetOrCreateConversationUseCase
import com.neteinstein.smsredirect.domain.usecase.MarkConversationReadUseCase
import com.neteinstein.smsredirect.domain.usecase.SendMessageUseCase
import com.neteinstein.smsredirect.presentation.conversation.ConversationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val ana = "+351912345678"
    private val received = ConversationMessage(1, false, 7, ana, "Olá", 1_000, MessageStatus.RECEIVED)
    private lateinit var store: FakeMessageStore
    private val sender = FakeMessageSender(SendOutcome.Queued(7))
    private val checker = FakeDefaultSmsAppChecker(isDefault = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = FakeMessageStore(messages = listOf(received), threadAddresses = mutableMapOf(7L to listOf(ana)))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        threadId: Long = 7,
        addresses: List<String> = emptyList(),
        draft: String? = null,
        attachment: OutgoingAttachment? = null,
    ) = ConversationViewModel(
        threadId = threadId,
        initialAddresses = addresses,
        initialDraft = draft,
        initialAttachment = attachment,
        getConversationMessagesUseCase = GetConversationMessagesUseCase(store),
        getConversationAddressesUseCase = GetConversationAddressesUseCase(store),
        getOrCreateConversationUseCase = GetOrCreateConversationUseCase(store),
        markConversationReadUseCase = MarkConversationReadUseCase(store),
        sendMessageUseCase = SendMessageUseCase(sender, checker),
        deleteMessageUseCase = DeleteMessageUseCase(store),
        contactNameResolver = FakeContactNameResolver(mapOf(ana to "Ana")),
    )

    @Test
    fun `an existing conversation loads its people, names and messages`() =
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(listOf(ana), state.addresses)
            assertEquals("Ana", state.title)
            assertEquals(listOf(received), state.messages)
        }

    @Test
    fun `messages are marked read only while the conversation is on screen`() =
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()
            assertTrue(store.markedRead.isEmpty())

            vm.onVisible(true)
            advanceUntilIdle()

            assertEquals(listOf(7L), store.markedRead)
        }

    @Test
    fun `a conversation opened by recipients finds its thread and keeps the prefilled draft`() =
        runTest {
            val vm = createViewModel(threadId = 0, addresses = listOf(ana), draft = "from a link")
            advanceUntilIdle()

            assertEquals(7L, vm.uiState.value.threadId)
            assertEquals("from a link", vm.uiState.value.draft)
            assertEquals(listOf(received), vm.uiState.value.messages)
        }

    @Test
    fun `sending clears the composer`() =
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()
            vm.onDraftChanged("Até já")

            vm.send()
            advanceUntilIdle()

            assertEquals(listOf(FakeMessageSender.Sms(ana, "Até já")), sender.sentSms)
            assertEquals("", vm.uiState.value.draft)
            assertFalse(vm.uiState.value.sending)
        }

    @Test
    fun `a picture is sent as MMS and the composer says so beforehand`() =
        runTest {
            val photo = OutgoingAttachment("content://media/9", "image/png", "p.png")
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onAttachmentPicked(photo)
            assertTrue(vm.uiState.value.sendsAsMms)
            vm.send()
            advanceUntilIdle()

            assertEquals(listOf(photo), sender.sentMms.single().attachments)
            assertTrue(
                vm.uiState.value.attachments
                    .isEmpty(),
            )
        }

    @Test
    fun `a failed send keeps the draft and reports why`() =
        runTest {
            sender.outcome = SendOutcome.Failed(SendFailure.NO_SERVICE)
            val vm = createViewModel()
            advanceUntilIdle()
            vm.onDraftChanged("hello")

            vm.send()
            advanceUntilIdle()

            assertEquals("hello", vm.uiState.value.draft)
            assertEquals(SendFailure.NO_SERVICE, vm.uiState.value.error)
        }

    @Test
    fun `nothing can be sent while the composer is empty`() =
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            assertFalse(vm.uiState.value.canSend)
            vm.send()
            advanceUntilIdle()

            assertTrue(sender.sentSms.isEmpty())
        }

    @Test
    fun `retrying a failed SMS resends it and removes the failed copy`() =
        runTest {
            val failed = ConversationMessage(2, false, 7, ana, "again", 2_000, MessageStatus.FAILED)
            store.messageList += failed
            val vm = createViewModel()
            advanceUntilIdle()

            vm.retry(failed)
            advanceUntilIdle()

            assertEquals(listOf(FakeMessageSender.Sms(ana, "again")), sender.sentSms)
            assertEquals(listOf(failed), store.deletedMessages)
        }
}
