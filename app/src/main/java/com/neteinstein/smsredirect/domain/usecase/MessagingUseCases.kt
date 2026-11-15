package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.messaging.MessageStore
import com.neteinstein.smsredirect.domain.messaging.MmsDownloadRequester
import com.neteinstein.smsredirect.domain.model.Conversation
import com.neteinstein.smsredirect.domain.model.ConversationMessage
import com.neteinstein.smsredirect.domain.model.MessageStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The conversation list, newest first, refreshed whenever the store changes. */
class GetConversationsUseCase(
    private val messageStore: MessageStore,
) {
    operator fun invoke(): Flow<List<Conversation>> =
        messageStore.observeChanges().map { messageStore.conversations().sortedByDescending { it.dateEpochMs } }
}

/** One conversation's messages, oldest first, refreshed whenever the store changes. */
class GetConversationMessagesUseCase(
    private val messageStore: MessageStore,
) {
    operator fun invoke(threadId: Long): Flow<List<ConversationMessage>> =
        messageStore.observeChanges().map { messageStore.messages(threadId).sortedBy { it.dateEpochMs } }
}

/** Who a conversation is with. */
class GetConversationAddressesUseCase(
    private val messageStore: MessageStore,
) {
    suspend operator fun invoke(threadId: Long): List<String> = messageStore.addresses(threadId)
}

/** The conversation for a set of recipients, e.g. after "new message" or a `smsto:` link. */
class GetOrCreateConversationUseCase(
    private val messageStore: MessageStore,
) {
    suspend operator fun invoke(addresses: List<String>): Long? {
        val recipients = normalizeRecipients(addresses)
        if (recipients.isEmpty()) return null
        return messageStore.threadIdFor(recipients)
    }
}

/** Downloads an MMS that arrived while it could not be fetched, e.g. with mobile data off. */
class DownloadMmsUseCase(
    private val mmsDownloadRequester: MmsDownloadRequester,
) {
    suspend operator fun invoke(message: ConversationMessage): Boolean {
        if (!message.isMms || message.status != MessageStatus.NOT_DOWNLOADED) return false
        return mmsDownloadRequester.download(message.id)
    }
}

class MarkConversationReadUseCase(
    private val messageStore: MessageStore,
) {
    suspend operator fun invoke(threadId: Long) = messageStore.markRead(threadId)
}

class DeleteConversationUseCase(
    private val messageStore: MessageStore,
) {
    suspend operator fun invoke(threadId: Long) = messageStore.deleteConversation(threadId)
}

class DeleteMessageUseCase(
    private val messageStore: MessageStore,
) {
    suspend operator fun invoke(message: ConversationMessage) = messageStore.deleteMessage(message)
}
