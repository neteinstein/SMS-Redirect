package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.messaging.MessageStore
import com.neteinstein.smsredirect.domain.mms.MmsPdu
import com.neteinstein.smsredirect.domain.model.Conversation
import com.neteinstein.smsredirect.domain.model.ConversationMessage
import com.neteinstein.smsredirect.domain.model.MessageStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory SMS/MMS store. Every change bumps [changes], like the provider's ContentObserver. */
class FakeMessageStore(
    conversations: List<Conversation> = emptyList(),
    messages: List<ConversationMessage> = emptyList(),
    private val threadAddresses: MutableMap<Long, List<String>> = mutableMapOf(),
) : MessageStore {
    val conversationList = conversations.toMutableList()
    val messageList = messages.toMutableList()
    private val changes = MutableStateFlow(0)
    val markedRead = mutableListOf<Long>()
    val deletedThreads = mutableListOf<Long>()
    val deletedMessages = mutableListOf<ConversationMessage>()
    private var nextThreadId = 100L

    fun notifyChanged() {
        changes.value++
    }

    override fun observeChanges(): Flow<Unit> = changes.map { }

    override suspend fun conversations(): List<Conversation> = conversationList.toList()

    override suspend fun messages(threadId: Long): List<ConversationMessage> = messageList.filter { it.threadId == threadId }

    override suspend fun addresses(threadId: Long): List<String> = threadAddresses[threadId].orEmpty()

    override suspend fun threadIdFor(addresses: List<String>): Long =
        threadAddresses.entries.firstOrNull { it.value.toSet() == addresses.toSet() }?.key
            ?: nextThreadId++.also { threadAddresses[it] = addresses }

    override suspend fun markRead(threadId: Long) {
        markedRead += threadId
    }

    override suspend fun deleteConversation(threadId: Long) {
        deletedThreads += threadId
        conversationList.removeAll { it.threadId == threadId }
        notifyChanged()
    }

    override suspend fun deleteMessage(message: ConversationMessage) {
        deletedMessages += message
        messageList.remove(message)
        notifyChanged()
    }

    override suspend fun saveIncomingSms(
        address: String,
        body: String,
        dateEpochMs: Long,
        dateSentEpochMs: Long,
        subscriptionId: Int,
    ): Long {
        val threadId = threadIdFor(listOf(address))
        messageList += ConversationMessage(messageList.size + 1L, false, threadId, address, body, dateEpochMs, MessageStatus.RECEIVED)
        notifyChanged()
        return threadId
    }

    override suspend fun saveIncomingMms(
        message: MmsPdu.RetrieveConf,
        subscriptionId: Int,
    ): Long = threadIdFor(listOfNotNull(message.from))
}
