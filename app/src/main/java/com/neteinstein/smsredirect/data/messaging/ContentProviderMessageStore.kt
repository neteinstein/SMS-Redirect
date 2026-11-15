package com.neteinstein.smsredirect.data.messaging

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import androidx.core.net.toUri
import com.neteinstein.smsredirect.domain.messaging.MessageStore
import com.neteinstein.smsredirect.domain.mms.MmsCharsets
import com.neteinstein.smsredirect.domain.mms.MmsHeaders
import com.neteinstein.smsredirect.domain.mms.MmsPart
import com.neteinstein.smsredirect.domain.mms.MmsPdu
import com.neteinstein.smsredirect.domain.mms.MmsRecipients
import com.neteinstein.smsredirect.domain.model.Conversation
import com.neteinstein.smsredirect.domain.model.ConversationMessage
import com.neteinstein.smsredirect.domain.model.MessageAttachment
import com.neteinstein.smsredirect.domain.model.MessageStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

/**
 * [MessageStore] over the Telephony provider. Every query is defensive: OEM providers differ in
 * which columns they expose and some throw on projections they do not know, so a failed read
 * degrades to "nothing" rather than crashing the Messages tab.
 *
 * Never logs addresses or message text.
 */
class ContentProviderMessageStore(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MessageStore {
    private val resolver: ContentResolver get() = context.contentResolver

    override fun observeChanges(): Flow<Unit> =
        callbackFlow {
            val observer =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        trySend(Unit)
                    }
                }
            for (uri in listOf(Telephony.MmsSms.CONTENT_URI, Telephony.Sms.CONTENT_URI, Telephony.Mms.CONTENT_URI)) {
                runCatching { resolver.registerContentObserver(uri, true, observer) }
            }
            trySend(Unit)
            awaitClose { resolver.unregisterContentObserver(observer) }
        }.conflate()

    override suspend fun conversations(): List<Conversation> =
        withContext(dispatcher) {
            val canonical = canonicalAddresses()
            query(
                THREADS_URI,
                arrayOf(
                    Telephony.Threads._ID,
                    Telephony.Threads.DATE,
                    Telephony.Threads.MESSAGE_COUNT,
                    Telephony.Threads.RECIPIENT_IDS,
                    Telephony.Threads.SNIPPET,
                    Telephony.Threads.READ,
                    Telephony.Threads.HAS_ATTACHMENT,
                ),
            ) { cursor ->
                val count = cursor.long(Telephony.Threads.MESSAGE_COUNT)
                // An empty thread is one "new message" created but nothing was ever sent in.
                if (count == 0L) return@query null
                Conversation(
                    threadId = cursor.long(Telephony.Threads._ID),
                    addresses = recipientAddresses(cursor.string(Telephony.Threads.RECIPIENT_IDS), canonical),
                    snippet = cursor.string(Telephony.Threads.SNIPPET).orEmpty(),
                    dateEpochMs = cursor.long(Telephony.Threads.DATE),
                    unread = cursor.long(Telephony.Threads.READ) == 0L,
                    hasAttachment = cursor.long(Telephony.Threads.HAS_ATTACHMENT) == 1L,
                )
            }
        }

    override suspend fun messages(threadId: Long): List<ConversationMessage> =
        withContext(dispatcher) {
            val selection = "thread_id = ?"
            val args = arrayOf(threadId.toString())
            val sms =
                query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf(
                        Telephony.Sms._ID,
                        Telephony.Sms.ADDRESS,
                        Telephony.Sms.BODY,
                        Telephony.Sms.DATE,
                        Telephony.Sms.TYPE,
                    ),
                    selection,
                    args,
                ) { cursor ->
                    val status = smsStatus(cursor.long(Telephony.Sms.TYPE).toInt()) ?: return@query null
                    ConversationMessage(
                        id = cursor.long(Telephony.Sms._ID),
                        isMms = false,
                        threadId = threadId,
                        address = cursor.string(Telephony.Sms.ADDRESS).orEmpty(),
                        body = cursor.string(Telephony.Sms.BODY).orEmpty(),
                        dateEpochMs = cursor.long(Telephony.Sms.DATE),
                        status = status,
                    )
                }
            sms + mmsMessages(threadId, selection, args)
        }

    private fun mmsMessages(
        threadId: Long,
        selection: String,
        args: Array<String>,
    ): List<ConversationMessage> {
        data class Row(
            val id: Long,
            val dateSeconds: Long,
            val status: MessageStatus,
            val subject: String?,
        )
        val rows =
            query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(
                    Telephony.Mms._ID,
                    Telephony.Mms.DATE,
                    Telephony.Mms.MESSAGE_BOX,
                    Telephony.Mms.MESSAGE_TYPE,
                    Telephony.Mms.SUBJECT,
                    Telephony.Mms.STATUS,
                ),
                selection,
                args,
            ) { cursor ->
                // An announced MMS not downloaded yet (ours, or left by another messaging app):
                // shown as "not downloaded", with a Download button.
                val isNotification = cursor.long(Telephony.Mms.MESSAGE_TYPE).toInt() == MmsHeaders.MESSAGE_TYPE_NOTIFICATION_IND
                val status =
                    if (isNotification) {
                        MmsDownloadState.toMessageStatus(cursor.long(Telephony.Mms.STATUS).toInt())
                    } else {
                        mmsStatus(cursor.long(Telephony.Mms.MESSAGE_BOX).toInt()) ?: return@query null
                    }
                Row(
                    id = cursor.long(Telephony.Mms._ID),
                    dateSeconds = cursor.long(Telephony.Mms.DATE),
                    status = status,
                    subject = cursor.string(Telephony.Mms.SUBJECT)?.takeIf { it.isNotBlank() },
                )
            }
        if (rows.isEmpty()) return emptyList()

        val partsByMessage = mmsParts(rows.map { it.id })
        val threadAddresses by lazy { addressesBlocking(threadId) }
        return rows.map { row ->
            val parts = partsByMessage[row.id].orEmpty()
            ConversationMessage(
                id = row.id,
                isMms = true,
                threadId = threadId,
                address =
                    if (row.status == MessageStatus.RECEIVED || row.status == MessageStatus.NOT_DOWNLOADED ||
                        row.status == MessageStatus.DOWNLOADING
                    ) {
                        mmsSender(row.id)
                    } else {
                        threadAddresses.joinToString(", ")
                    },
                body = parts.filter { it.first }.joinToString("\n") { it.second.orEmpty() }.trim(),
                dateEpochMs = row.dateSeconds * 1000,
                status = row.status,
                attachments = parts.mapNotNull { it.third },
                subject = row.subject,
            )
        }
    }

    /** Per message: (isText, text, attachment) for every part except the SMIL layout. */
    private fun mmsParts(messageIds: List<Long>): Map<Long, List<Triple<Boolean, String?, MessageAttachment?>>> {
        val result = mutableMapOf<Long, MutableList<Triple<Boolean, String?, MessageAttachment?>>>()
        messageIds.chunked(QUERY_CHUNK).forEach { chunk ->
            query(
                PART_URI,
                arrayOf(
                    Telephony.Mms.Part._ID,
                    Telephony.Mms.Part.MSG_ID,
                    Telephony.Mms.Part.CONTENT_TYPE,
                    Telephony.Mms.Part.TEXT,
                    Telephony.Mms.Part.NAME,
                    Telephony.Mms.Part.FILENAME,
                ),
                "${Telephony.Mms.Part.MSG_ID} IN (${chunk.joinToString(",")})",
                null,
            ) { cursor ->
                val partId = cursor.long(Telephony.Mms.Part._ID)
                val type = cursor.string(Telephony.Mms.Part.CONTENT_TYPE).orEmpty().lowercase()
                val entry =
                    when {
                        type == MmsHeaders.CONTENT_TYPE_SMIL -> {
                            null
                        }

                        type == MmsHeaders.CONTENT_TYPE_TEXT -> {
                            Triple(true, cursor.string(Telephony.Mms.Part.TEXT), null)
                        }

                        else -> {
                            Triple(
                                false,
                                null,
                                MessageAttachment(
                                    uri = ContentUris.withAppendedId(PART_URI, partId).toString(),
                                    contentType = type,
                                    name = cursor.string(Telephony.Mms.Part.NAME) ?: cursor.string(Telephony.Mms.Part.FILENAME),
                                ),
                            )
                        }
                    }
                entry?.let { result.getOrPut(cursor.long(Telephony.Mms.Part.MSG_ID)) { mutableListOf() } += it }
                null
            }
        }
        return result
    }

    private fun mmsSender(messageId: Long): String =
        query(
            "content://mms/$messageId/addr".toUri(),
            arrayOf(Telephony.Mms.Addr.ADDRESS, Telephony.Mms.Addr.TYPE),
            "${Telephony.Mms.Addr.TYPE} = $ADDR_TYPE_FROM",
            null,
        ) { it.string(Telephony.Mms.Addr.ADDRESS) }.firstOrNull().orEmpty()

    override suspend fun addresses(threadId: Long): List<String> = withContext(dispatcher) { addressesBlocking(threadId) }

    private fun addressesBlocking(threadId: Long): List<String> {
        val canonical = canonicalAddresses()
        return query(
            THREADS_URI,
            arrayOf(Telephony.Threads._ID, Telephony.Threads.RECIPIENT_IDS),
            "${Telephony.Threads._ID} = ?",
            arrayOf(threadId.toString()),
        ) { recipientAddresses(it.string(Telephony.Threads.RECIPIENT_IDS), canonical) }.firstOrNull().orEmpty()
    }

    override suspend fun threadIdFor(addresses: List<String>): Long =
        withContext(dispatcher) { Telephony.Threads.getOrCreateThreadId(context, addresses.toSet()) }

    override suspend fun markRead(threadId: Long) {
        withContext(dispatcher) {
            val values =
                ContentValues().apply {
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.SEEN, 1)
                }
            val where = "thread_id = ? AND (read = 0 OR seen = 0)"
            val args = arrayOf(threadId.toString())
            safely { resolver.update(Telephony.Sms.CONTENT_URI, values, where, args) }
            safely { resolver.update(Telephony.Mms.CONTENT_URI, values, where, args) }
        }
    }

    override suspend fun deleteConversation(threadId: Long) {
        withContext(dispatcher) {
            safely { resolver.delete(ContentUris.withAppendedId(Telephony.Threads.CONTENT_URI, threadId), null, null) }
        }
    }

    override suspend fun deleteMessage(message: ConversationMessage) {
        withContext(dispatcher) {
            val base = if (message.isMms) Telephony.Mms.CONTENT_URI else Telephony.Sms.CONTENT_URI
            safely { resolver.delete(ContentUris.withAppendedId(base, message.id), null, null) }
        }
    }

    override suspend fun saveIncomingSms(
        address: String,
        body: String,
        dateEpochMs: Long,
        dateSentEpochMs: Long,
        subscriptionId: Int,
    ): Long? =
        withContext(dispatcher) {
            val threadId = safely { Telephony.Threads.getOrCreateThreadId(context, address) } ?: return@withContext null
            val values =
                ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, address)
                    put(Telephony.Sms.BODY, body)
                    put(Telephony.Sms.DATE, dateEpochMs)
                    if (dateSentEpochMs > 0) put(Telephony.Sms.DATE_SENT, dateSentEpochMs)
                    put(Telephony.Sms.READ, 0)
                    put(Telephony.Sms.SEEN, 0)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                    put(Telephony.Sms.THREAD_ID, threadId)
                    put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
                }
            safely { resolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) } ?: return@withContext null
            threadId
        }

    override suspend fun saveIncomingMms(
        message: MmsPdu.RetrieveConf,
        subscriptionId: Int,
    ): Long? =
        withContext(dispatcher) {
            val participants =
                MmsRecipients
                    .conversationAddresses(message.from, message.to, message.cc, ownNumbers = emptyList())
                    .ifEmpty { listOf(message.from ?: UNKNOWN_SENDER) }
            val threadId = safely { Telephony.Threads.getOrCreateThreadId(context, participants.toSet()) } ?: return@withContext null
            val nowSeconds = System.currentTimeMillis() / 1000
            val values =
                ContentValues().apply {
                    put(Telephony.Mms.THREAD_ID, threadId)
                    put(Telephony.Mms.DATE, nowSeconds)
                    put(Telephony.Mms.DATE_SENT, message.dateEpochSeconds ?: nowSeconds)
                    put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_INBOX)
                    put(Telephony.Mms.READ, 0)
                    put(Telephony.Mms.SEEN, 0)
                    put(Telephony.Mms.MESSAGE_TYPE, MmsHeaders.MESSAGE_TYPE_RETRIEVE_CONF)
                    put(Telephony.Mms.MMS_VERSION, MmsHeaders.MMS_VERSION_1_2)
                    put(Telephony.Mms.CONTENT_TYPE, message.contentType)
                    message.messageId?.let { put(Telephony.Mms.MESSAGE_ID, it) }
                    message.transactionId?.let { put(Telephony.Mms.TRANSACTION_ID, it) }
                    message.subject?.let {
                        put(Telephony.Mms.SUBJECT, it)
                        put(Telephony.Mms.SUBJECT_CHARSET, MmsCharsets.UTF_8)
                    }
                    put(Telephony.Mms.SUBSCRIPTION_ID, subscriptionId)
                }
            val messageUri = safely { resolver.insert(Telephony.Mms.Inbox.CONTENT_URI, values) } ?: return@withContext null
            val messageId = ContentUris.parseId(messageUri)
            message.parts.forEachIndexed { index, part -> insertPart(messageId, part, index) }
            message.from?.let { insertAddress(messageId, it, ADDR_TYPE_FROM) }
            message.to.forEach { insertAddress(messageId, it, ADDR_TYPE_TO) }
            message.cc.forEach { insertAddress(messageId, it, ADDR_TYPE_CC) }
            threadId
        }

    /** What is needed to (re)start the download of an announced MMS. */
    data class PendingMmsDownload(
        val messageId: Long,
        val threadId: Long,
        val contentLocation: String,
        val transactionId: String?,
        val from: String?,
        val subscriptionId: Int,
    )

    /**
     * Saves an MMS announcement as a "not downloaded" row in the sender's conversation, before
     * the download starts. Until the download succeeds, this row is the message: it survives a
     * failed download, a dismissed notification and a reboot, and its Download button retries.
     */
    suspend fun saveMmsNotification(
        notification: MmsPdu.NotificationInd,
        subscriptionId: Int,
    ): PendingMmsDownload? =
        withContext(dispatcher) {
            val from = notification.from?.takeIf { it.isNotBlank() } ?: UNKNOWN_SENDER
            val threadId = safely { Telephony.Threads.getOrCreateThreadId(context, from) } ?: return@withContext null
            val values =
                ContentValues().apply {
                    put(Telephony.Mms.THREAD_ID, threadId)
                    put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000)
                    put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_INBOX)
                    put(Telephony.Mms.READ, 0)
                    put(Telephony.Mms.SEEN, 0)
                    put(Telephony.Mms.MESSAGE_TYPE, MmsHeaders.MESSAGE_TYPE_NOTIFICATION_IND)
                    put(Telephony.Mms.MMS_VERSION, MmsHeaders.MMS_VERSION_1_2)
                    put(Telephony.Mms.CONTENT_LOCATION, notification.contentLocation)
                    notification.transactionId?.let { put(Telephony.Mms.TRANSACTION_ID, it) }
                    put(Telephony.Mms.MESSAGE_SIZE, notification.messageSize)
                    notification.expirySeconds?.let { put(Telephony.Mms.EXPIRY, it) }
                    notification.subject?.let {
                        put(Telephony.Mms.SUBJECT, it)
                        put(Telephony.Mms.SUBJECT_CHARSET, MmsCharsets.UTF_8)
                    }
                    put(Telephony.Mms.STATUS, MmsDownloadState.UNSTARTED)
                    put(Telephony.Mms.SUBSCRIPTION_ID, subscriptionId)
                }
            val uri = safely { resolver.insert(Telephony.Mms.Inbox.CONTENT_URI, values) } ?: return@withContext null
            val messageId = ContentUris.parseId(uri)
            insertAddress(messageId, from, ADDR_TYPE_FROM)
            PendingMmsDownload(
                messageId,
                threadId,
                notification.contentLocation,
                notification.transactionId,
                notification.from,
                subscriptionId,
            )
        }

    /** The announcement behind a "not downloaded" row, or null if it is gone or already downloaded. */
    suspend fun pendingMmsDownload(messageId: Long): PendingMmsDownload? =
        withContext(dispatcher) {
            query(
                ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, messageId),
                arrayOf(
                    Telephony.Mms.THREAD_ID,
                    Telephony.Mms.MESSAGE_TYPE,
                    Telephony.Mms.CONTENT_LOCATION,
                    Telephony.Mms.TRANSACTION_ID,
                    Telephony.Mms.SUBSCRIPTION_ID,
                ),
            ) { cursor ->
                if (cursor.long(Telephony.Mms.MESSAGE_TYPE).toInt() != MmsHeaders.MESSAGE_TYPE_NOTIFICATION_IND) return@query null
                val location = cursor.string(Telephony.Mms.CONTENT_LOCATION)?.takeIf { it.isNotBlank() } ?: return@query null
                PendingMmsDownload(
                    messageId = messageId,
                    threadId = cursor.long(Telephony.Mms.THREAD_ID),
                    contentLocation = location,
                    transactionId = cursor.string(Telephony.Mms.TRANSACTION_ID),
                    from = null,
                    subscriptionId = cursor.long(Telephony.Mms.SUBSCRIPTION_ID).toInt(),
                )
            }.firstOrNull()?.let { it.copy(from = mmsSender(messageId).ifBlank { null }) }
        }

    suspend fun setMmsDownloadState(
        messageId: Long,
        state: Int,
    ) {
        withContext(dispatcher) {
            val values = ContentValues().apply { put(Telephony.Mms.STATUS, state) }
            safely { resolver.update(ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, messageId), values, null, null) }
        }
    }

    /** Removes the "not downloaded" row once the real message has been saved. */
    suspend fun deleteMmsNotification(messageId: Long) {
        withContext(dispatcher) {
            safely {
                resolver.delete(
                    ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, messageId),
                    "${Telephony.Mms.MESSAGE_TYPE} = ?",
                    arrayOf(MmsHeaders.MESSAGE_TYPE_NOTIFICATION_IND.toString()),
                )
            }
        }
    }

    /**
     * A download "in progress" when the process starts cannot be tracked any more (its callback
     * may still come, and is handled); show those rows as not downloaded so they can be retried.
     */
    suspend fun resetInterruptedMmsDownloads() {
        withContext(dispatcher) {
            val values = ContentValues().apply { put(Telephony.Mms.STATUS, MmsDownloadState.TRANSIENT_FAILURE) }
            safely {
                resolver.update(
                    Telephony.Mms.CONTENT_URI,
                    values,
                    "${Telephony.Mms.MESSAGE_TYPE} = ? AND ${Telephony.Mms.STATUS} = ?",
                    arrayOf(MmsHeaders.MESSAGE_TYPE_NOTIFICATION_IND.toString(), MmsDownloadState.DOWNLOADING.toString()),
                )
            }
        }
    }

    /** Writes one MMS part: text inline in the row, anything else as the part's file. */
    internal fun insertPart(
        messageId: Long,
        part: MmsPart,
        index: Int,
    ) {
        val values =
            ContentValues().apply {
                put(Telephony.Mms.Part.MSG_ID, messageId)
                put(Telephony.Mms.Part.CONTENT_TYPE, part.contentType.lowercase())
                put(Telephony.Mms.Part.SEQ, index)
                part.contentId?.let { put(Telephony.Mms.Part.CONTENT_ID, "<$it>") }
                put(Telephony.Mms.Part.CONTENT_LOCATION, part.contentLocation ?: part.name ?: "part_$index")
                part.name?.let { put(Telephony.Mms.Part.NAME, it) }
                part.fileName?.let { put(Telephony.Mms.Part.FILENAME, it) }
                if (part.isText || part.isSmil) {
                    put(Telephony.Mms.Part.CHARSET, MmsCharsets.UTF_8)
                    put(Telephony.Mms.Part.TEXT, part.textOrEmpty())
                }
            }
        val partUri = safely { resolver.insert("content://mms/$messageId/part".toUri(), values) } ?: return
        if (!part.isText && !part.isSmil) {
            safely { resolver.openOutputStream(partUri)?.use { it.write(part.data) } }
        }
    }

    internal fun insertAddress(
        messageId: Long,
        address: String,
        type: Int,
    ) {
        val values =
            ContentValues().apply {
                put(Telephony.Mms.Addr.ADDRESS, address)
                put(Telephony.Mms.Addr.TYPE, type)
                put(Telephony.Mms.Addr.CHARSET, MmsCharsets.UTF_8)
            }
        safely { resolver.insert("content://mms/$messageId/addr".toUri(), values) }
    }

    private fun canonicalAddresses(): Map<Long, String> =
        query(CANONICAL_ADDRESSES_URI, arrayOf("_id", "address")) { cursor ->
            cursor.long("_id") to cursor.string("address").orEmpty()
        }.toMap()

    private fun recipientAddresses(
        recipientIds: String?,
        canonical: Map<Long, String>,
    ): List<String> =
        recipientIds
            .orEmpty()
            .split(' ')
            .mapNotNull { it.toLongOrNull()?.let(canonical::get) }
            .filter { it.isNotBlank() }

    private fun <T> query(
        uri: Uri,
        projection: Array<String>,
        selection: String? = null,
        args: Array<String>? = null,
        map: (Cursor) -> T?,
    ): List<T> =
        safely {
            resolver.query(uri, projection, selection, args, null)?.use { cursor ->
                val result = ArrayList<T>(cursor.count)
                while (cursor.moveToNext()) map(cursor)?.let(result::add)
                result
            }
        }.orEmpty()

    private inline fun <T> safely(block: () -> T?): T? =
        try {
            block()
        } catch (e: SecurityException) {
            // No READ_SMS (not the default SMS app, or the permission was revoked).
            Log.w(TAG, "Message store access denied: ${e.javaClass.simpleName}")
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Message store rejected a request: ${e.javaClass.simpleName}")
            null
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Message store unavailable: ${e.javaClass.simpleName}")
            null
        } catch (e: java.io.IOException) {
            Log.w(TAG, "Message store I/O failed: ${e.javaClass.simpleName}")
            null
        }

    private fun Cursor.long(column: String): Long = getColumnIndex(column).takeIf { it >= 0 }?.let { getLong(it) } ?: 0L

    private fun Cursor.string(column: String): String? = getColumnIndex(column).takeIf { it >= 0 }?.let { getString(it) }

    companion object {
        private const val TAG = "MessageStore"
        private const val QUERY_CHUNK = 200
        private const val UNKNOWN_SENDER = "unknown"

        /** PduHeaders.FROM / TO / CC, the `type` column of `content://mms/<id>/addr`. */
        const val ADDR_TYPE_FROM = 137
        const val ADDR_TYPE_TO = 151
        const val ADDR_TYPE_CC = 130

        private val THREADS_URI: Uri = "content://mms-sms/conversations?simple=true".toUri()
        private val CANONICAL_ADDRESSES_URI: Uri = "content://mms-sms/canonical-addresses".toUri()
        private val PART_URI: Uri = "content://mms/part".toUri()

        fun smsStatus(type: Int): MessageStatus? =
            when (type) {
                Telephony.Sms.MESSAGE_TYPE_INBOX -> MessageStatus.RECEIVED
                Telephony.Sms.MESSAGE_TYPE_SENT -> MessageStatus.SENT
                Telephony.Sms.MESSAGE_TYPE_OUTBOX, Telephony.Sms.MESSAGE_TYPE_QUEUED -> MessageStatus.SENDING
                Telephony.Sms.MESSAGE_TYPE_FAILED -> MessageStatus.FAILED
                else -> null // drafts
            }

        fun mmsStatus(box: Int): MessageStatus? =
            when (box) {
                Telephony.Mms.MESSAGE_BOX_INBOX -> MessageStatus.RECEIVED
                Telephony.Mms.MESSAGE_BOX_SENT -> MessageStatus.SENT
                Telephony.Mms.MESSAGE_BOX_OUTBOX -> MessageStatus.SENDING
                Telephony.Mms.MESSAGE_BOX_FAILED -> MessageStatus.FAILED
                else -> null
            }
    }
}
