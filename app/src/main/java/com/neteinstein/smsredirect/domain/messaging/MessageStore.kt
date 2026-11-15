package com.neteinstein.smsredirect.domain.messaging

import com.neteinstein.smsredirect.domain.mms.MmsPdu
import com.neteinstein.smsredirect.domain.model.Conversation
import com.neteinstein.smsredirect.domain.model.ConversationMessage
import kotlinx.coroutines.flow.Flow

/**
 * The device's SMS/MMS store (`content://sms`, `content://mms`, `content://mms-sms`), as the
 * messaging feature sees it. The interface lives here and the `ContentResolver` code in
 * `data/messaging/`, so every ViewModel and use case in front of it is JVM-testable.
 *
 * Reads need READ_SMS; writes only take effect while this app is the default SMS app.
 */
interface MessageStore {
    /** Emits once on collection and again whenever anything in the store changes. */
    fun observeChanges(): Flow<Unit>

    suspend fun conversations(): List<Conversation>

    /** Every message in [threadId], oldest first. */
    suspend fun messages(threadId: Long): List<ConversationMessage>

    /** The other participants of [threadId]. Empty if the thread does not exist. */
    suspend fun addresses(threadId: Long): List<String>

    /** The thread for exactly this set of participants, created if needed. */
    suspend fun threadIdFor(addresses: List<String>): Long

    suspend fun markRead(threadId: Long)

    suspend fun deleteConversation(threadId: Long)

    suspend fun deleteMessage(message: ConversationMessage)

    /**
     * Saves an SMS that just arrived. Returns its thread id, or null when the provider refused it
     * (this app is not the default SMS app).
     */
    suspend fun saveIncomingSms(
        address: String,
        body: String,
        dateEpochMs: Long,
        dateSentEpochMs: Long,
        subscriptionId: Int,
    ): Long?

    /**
     * Saves a downloaded MMS to the inbox. Returns its thread id, or null when the provider refused
     * it. The conversation it lands in comes from [com.neteinstein.smsredirect.domain.mms.MmsRecipients].
     */
    suspend fun saveIncomingMms(
        message: MmsPdu.RetrieveConf,
        subscriptionId: Int,
    ): Long?
}
