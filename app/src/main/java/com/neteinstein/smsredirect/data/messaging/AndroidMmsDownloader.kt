package com.neteinstein.smsredirect.data.messaging

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.FileProvider
import com.neteinstein.smsredirect.MessageStatusReceiver
import com.neteinstein.smsredirect.domain.messaging.MessageStore
import com.neteinstein.smsredirect.domain.mms.MmsPdu
import com.neteinstein.smsredirect.domain.mms.MmsPduComposer
import com.neteinstein.smsredirect.domain.mms.MmsPduParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** A downloaded MMS, saved to the store, ready to be announced. */
data class IncomingMms(
    val threadId: Long,
    val from: String?,
    val text: String,
    val hasAttachment: Boolean,
)

/**
 * Receiving an MMS is two steps: a WAP push announces it ([onWapPush]), then the message itself
 * is fetched from the carrier's MMSC over mobile data by the platform's MMS service, which writes
 * it to a file we hand it and reports back to [MessageStatusReceiver] ([onDownloaded]). The
 * default SMS app must then save it to the store itself, and acknowledge it so the MMSC stops
 * holding it.
 */
class AndroidMmsDownloader(
    private val context: Context,
    private val store: MessageStore,
    private val parser: MmsPduParser = MmsPduParser(),
    private val composer: MmsPduComposer = MmsPduComposer(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /** An MMS announcement, and whether its download could be started. */
    data class Announced(
        val notification: MmsPdu.NotificationInd,
        val started: Boolean,
    )

    /** Starts the download of an announced MMS. Null when the push was not an MMS notification. */
    fun onWapPush(
        pdu: ByteArray,
        subscriptionId: Int,
    ): Announced? {
        val notification = parser.parse(pdu) as? MmsPdu.NotificationInd ?: return null
        val started = download(notification.contentLocation, notification.transactionId, notification.from, subscriptionId)
        return Announced(notification, started)
    }

    /** Starts (or retries) a download. Returns false if the platform refused to start it. */
    fun download(
        contentLocation: String,
        transactionId: String?,
        from: String?,
        subscriptionId: Int,
    ): Boolean {
        val file = File(mmsDir(), "download-${UUID.randomUUID()}.pdu")
        return try {
            file.createNewFile()
            val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent =
                // The data only makes each download's intent distinct; it is never resolved.
                Intent(
                    MessageStatusReceiver.ACTION_MMS_DOWNLOADED,
                    Uri.fromParts("mmsdownload", file.name, null),
                    context,
                    MessageStatusReceiver::class.java,
                ).putExtra(MessageStatusReceiver.EXTRA_PDU_FILE, file.absolutePath)
                    .putExtra(MessageStatusReceiver.EXTRA_CONTENT_LOCATION, contentLocation)
                    .putExtra(MessageStatusReceiver.EXTRA_TRANSACTION_ID, transactionId)
                    .putExtra(MessageStatusReceiver.EXTRA_FROM, from)
                    .putExtra(MessageStatusReceiver.EXTRA_SUBSCRIPTION_ID, subscriptionId)
            val pendingIntent =
                PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            smsManager(subscriptionId).downloadMultimediaMessage(context, contentLocation, contentUri, null, pendingIntent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "MMS download could not start: ${e.javaClass.simpleName}")
            file.delete()
            false
        }
    }

    /** Saves a finished download. Null when it failed or the content was unusable. */
    suspend fun onDownloaded(
        resultCode: Int,
        file: File,
        transactionId: String?,
        subscriptionId: Int,
    ): IncomingMms? =
        withContext(dispatcher) {
            try {
                if (resultCode != Activity.RESULT_OK || !file.exists()) return@withContext null
                val message = parser.parse(file.readBytes()) as? MmsPdu.RetrieveConf ?: return@withContext null
                val threadId = store.saveIncomingMms(message, subscriptionId) ?: return@withContext null
                (message.transactionId ?: transactionId)?.let { acknowledge(it, subscriptionId) }
                IncomingMms(
                    threadId = threadId,
                    from = message.from,
                    text = message.text,
                    hasAttachment = message.parts.any { !it.isText && !it.isSmil },
                )
            } finally {
                file.delete()
            }
        }

    /** Best effort: an MMSC that never hears back may redeliver, which is harmless. */
    private fun acknowledge(
        transactionId: String,
        subscriptionId: Int,
    ) {
        val file = File(mmsDir(), "ack-${UUID.randomUUID()}.pdu")
        try {
            file.writeBytes(composer.composeAcknowledgeInd(transactionId))
            val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            smsManager(subscriptionId).sendMultimediaMessage(context, contentUri, null, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "MMS acknowledge failed: ${e.javaClass.simpleName}")
        }
        // The file must outlive the platform's read of it; stale ones are cleared on next start.
    }

    /** Deletes PDU files left behind by sends and downloads that never reported back. */
    fun clearStaleFiles(olderThanMs: Long = STALE_AFTER_MS) {
        val cutoff = System.currentTimeMillis() - olderThanMs
        mmsDir().listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    private fun smsManager(subscriptionId: Int): SmsManager {
        val manager = context.getSystemService(SmsManager::class.java)
        return if (SubscriptionManager.isValidSubscriptionId(subscriptionId)) {
            manager.createForSubscriptionId(subscriptionId)
        } else {
            manager
        }
    }

    private fun mmsDir(): File = File(context.cacheDir, AndroidMessageSender.MMS_CACHE_DIR).apply { mkdirs() }

    private companion object {
        const val TAG = "MmsDownloader"
        const val STALE_AFTER_MS = 24 * 60 * 60 * 1000L
    }
}
