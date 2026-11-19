package com.neteinstein.smsredirect.presentation

import com.neteinstein.smsredirect.domain.model.ConversationMessage
import com.neteinstein.smsredirect.domain.model.MessageStatus
import com.neteinstein.smsredirect.domain.usecase.ConversationRisk
import com.neteinstein.smsredirect.presentation.conversation.ConversationUiState
import com.neteinstein.smsredirect.presentation.conversation.MessageLinkState
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationLinkStateTest {
    private fun message(
        id: Long,
        status: MessageStatus = MessageStatus.RECEIVED,
    ) = ConversationMessage(id, false, 1, "+351910000000", "see x.com", id, status)

    @Test
    fun `a received message's links stay closed until a check has covered it`() {
        // Given: the latest check ran before message 2 arrived and flagged message 3.
        val state =
            ConversationUiState(
                risk = ConversationRisk(setOf("sms-3"), listOf("+351910000000"), assessedMessageKeys = setOf("sms-1", "sms-3")),
            )

        // Then
        assertEquals(MessageLinkState.OPEN, state.linkStateOf(message(1)))
        assertEquals(MessageLinkState.PENDING, state.linkStateOf(message(2)))
        assertEquals(MessageLinkState.BLOCKED, state.linkStateOf(message(3)))
    }

    @Test
    fun `links in sent messages always open`() {
        val state = ConversationUiState()

        assertEquals(MessageLinkState.OPEN, state.linkStateOf(message(4, MessageStatus.SENT)))
    }
}
