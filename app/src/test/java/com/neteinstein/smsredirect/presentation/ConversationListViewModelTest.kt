package com.neteinstein.smsredirect.presentation

import com.neteinstein.smsredirect.FakeContactNameResolver
import com.neteinstein.smsredirect.FakeMessageStore
import com.neteinstein.smsredirect.domain.model.Conversation
import com.neteinstein.smsredirect.domain.usecase.DeleteConversationUseCase
import com.neteinstein.smsredirect.domain.usecase.GetConversationsUseCase
import com.neteinstein.smsredirect.presentation.messages.ConversationListViewModel
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
class ConversationListViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store =
        FakeMessageStore(
            conversations =
                listOf(
                    Conversation(1, listOf("+351912345678"), "Olá", 1_000, unread = true, hasAttachment = false),
                    Conversation(2, listOf("+351930000000", "+351910000000"), "", 2_000, unread = false, hasAttachment = true),
                ),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        ConversationListViewModel(
            GetConversationsUseCase(store),
            DeleteConversationUseCase(store),
            FakeContactNameResolver(mapOf("+351912345678" to "Ana")),
        )

    @Test
    fun `nothing is read before start, which waits for the SMS role`() =
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            assertFalse(vm.uiState.value.loaded)
            assertTrue(
                vm.uiState.value.conversations
                    .isEmpty(),
            )
        }

    @Test
    fun `lists conversations newest first with contact names`() =
        runTest {
            val vm = createViewModel()

            vm.start()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.loaded)
            assertEquals(listOf(2L, 1L), state.conversations.map { it.threadId })
            assertEquals(mapOf("+351912345678" to "Ana"), state.contactNames)
        }

    @Test
    fun `deleting a conversation removes it from the list`() =
        runTest {
            val vm = createViewModel()
            vm.start()
            advanceUntilIdle()

            vm.deleteConversation(1)
            advanceUntilIdle()

            assertEquals(listOf(1L), store.deletedThreads)
            assertEquals(
                listOf(2L),
                vm.uiState.value.conversations
                    .map { it.threadId },
            )
        }
}
