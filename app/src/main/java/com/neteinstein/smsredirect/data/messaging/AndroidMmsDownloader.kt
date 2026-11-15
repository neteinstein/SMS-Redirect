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
import com.neteinstein.smsredirect.domain.messaging.MmsDownloadRequester
import com.neteinstein.smsredirect.domain.messaging.MmsReadReportSender
import com.neteinstein.smsredirect.domain.mms.MmsPdu
import com.neteinstein.smsredirect.domain.mms.MmsPduComposer
import com.neteinstein.smsredirect.domain.mms.MmsPduParser
import com.neteinstein.smsredirect.domain.repository.MessagingSettingsRepository
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

/** How a download that reported back ended. */
sealed interface MmsDownloadResult {
    data class Saved(
        val message: IncomingMms,
    ) : MmsDownloadResult

    /** Not downloaded; the "not downloaded" row, if any, stays so the user can retry. */
    data class Failed(
        val threadId: Long?,
    ) : MmsDownloadResult

    /** A second callback for a message already saved (e.g. a retry that raced the first try). */
    data object AlreadySaved : MmsDownloadResult
}

/**
 * Receiving an MMS is two steps: a WAP push announces it ([onWapPush]), then the message itself
 * is fetched from the carrier's MMSC over mobile data by the platform's MMS service, which writes
 * it to a file we hand it and reports back to [MessageStatusReceiver] ([onDownloaded]). The
 * default SMS app must then save it to the store itself, and acknowledge it so the MMSC stops
 * holding it.
 *
 * The announcement is saved first, as a "not downloaded" row in the sender's conversation
 * ([ContentProviderMessageStore.saveMmsNotification]), so a message that cannot be downloaded --
 * no mobile data, an MMSC error, a reboot mid-download -- is never lost: the row's Download
 * button ([download]) tries again, and the row is replaced by the real message once it arrives.
 */
class AndroidMmsDownloader(
    private val context: Context,
    private val store: ContentProviderMessageStore,
    private val messagingSettings: MessagingSettingsRepository,
    private val parser: MmsPduParser = MmsPduParser(),
    private val composer: MmsPduComposer = MmsPduComposer(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MmsDownloadRequester,
    MmsReadReportSender {
    /** An MMS announcement, and whether its download could be started. */
    data class Announced(
        val notification: MmsPdu.NotificationInd,
        val pending: ContentProviderMessageStore.PendingMmsDownload?,
        val started: Boolean,
    )

    /**
     * Handles an MMS WAP push. An announcement is saved and its download started (returned, so
     * the caller can offer Retry if it did not start); delivery and read reports for MMS we sent
     * are recorded on the sent message. Null for anything that is not an announcement.
     */
    suspend fun onWapPush(
        pdu: ByteArray,
        subscriptionId: Int,
    ): Announced? {
        val notification =
            when (val parsed = parser.parse(pdu)) {
                is MmsPdu.NotificationInd -> {
                    parsed
                }

                is MmsPdu.DeliveryInd -> {
                    store.recordMmsDelivery(parsed.messageId, parsed.status)
                    return null
                }

                is MmsPdu.ReadOrigInd -> {
                    store.recordMmsRead(parsed.messageId, parsed.readStatus)
                    return null
                }

                else -> {
                    return null
                }
            }
        val pending = store.saveMmsNotification(notification, subscriptionId)
        val started =
            if (pending != null) {
                start(pending)
            } else {
                // Could not save the placeholder (not the default SMS app any more?): still try.
                startDownload(null, notification.contentLocation, notification.transactionId, notification.from, subscriptionId)
            }
        return Announced(notification, pending, started)
    }

    /** The conversation's Download button, and the notification's Retry. */
    override suspend fun download(messageId: Long): Boolean {
        val pending = store.pendingMmsDownload(messageId) ?: return false
        return start(pending)
    }

    private suspend fun start(pending: ContentProviderMessageStore.PendingMmsDownload): Boolean {
        store.setMmsDownloadState(pending.messageId, MmsDownloadState.DOWNLOADING)
        val started = startDownload(pending.messageId, pending.contentLocation, pending.transactionId, pending.from, pending.subscriptionId)
        if (!started) store.setMmsDownloadState(pending.messageId, MmsDownloadState.TRANSIENT_FAILURE)
        return started
    }

    /** Starts a download. Returns false if the platform refused to start it. */
    fun startDownload(
        placeholderId: Long?,
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
                    .putExtra(MessageStatusReceiver.EXTRA_PLACEHOLDER_ID, placeholderId ?: NO_PLACEHOLDER)
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

    /** Saves a finished download in place of its "not downloaded" row. */
    suspend fun onDownloaded(
        resultCode: Int,
        file: File,
        transactionId: String?,
        subscriptionId: Int,
        placeholderId: Long?,
    ): MmsDownloadResult =
        withContext(dispatcher) {
            try {
                val pending = placeholderId?.let { store.pendingMmsDownload(it) }
                // The placeholder is deleted once its message is saved; a second callback for it
                // must not save the message twice.
                if (placeholderId != null && pending == null) return@withContext MmsDownloadResult.AlreadySaved
                val message =
                    if (resultCode == Activity.RESULT_OK && file.exists()) parser.parse(file.readBytes()) as? MmsPdu.RetrieveConf else null
                val threadId = message?.let { store.saveIncomingMms(it, subscriptionId) }
                if (message == null || threadId == null) {
                    placeholderId?.let { store.setMmsDownloadState(it, MmsDownloadState.TRANSIENT_FAILURE) }
                    return@withContext MmsDownloadResult.Failed(pending?.threadId)
                }
                placeholderId?.let { store.deleteMmsNotification(it) }
                (message.transactionId ?: transactionId)?.let {
                    acknowledge(it, subscriptionId, reportAllowed = messagingSettings.get().mmsDeliveryReports)
                }
                MmsDownloadResult.Saved(
                    IncomingMms(
                        threadId = threadId,
                        from = message.from,
                        text = message.text,
                        hasAttachment = message.parts.any { !it.isText && !it.isSmil },
                    ),
                )
            } finally {
                file.delete()
            }
        }

    /** Best effort: an MMSC that never hears back may redeliver, which is harmless. */
    private fun acknowledge(
        transactionId: String,
        subscriptionId: Int,
        reportAllowed: Boolean,
    ) {
        sendPdu(composer.composeAcknowledgeInd(transactionId, reportAllowed), subscriptionId, "ack")
    }

    /**
     * Tells the senders of this conversation's unread MMS that asked for it that they were read.
     * Called (by MarkConversationReadUseCase) only when the user turned MMS read reports on.
     */
    override suspend fun sendReadReports(threadId: Long) {
        withContext(dispatcher) {
            for (request in store.pendingReadReports(threadId)) {
                // Another mark-read may be sending this same report right now: only one claims it.
                if (!store.claimReadReport(request.rowId)) continue
                val pdu =
                    composer.composeReadRecInd(
                        messageId = request.messageId,
                        to = request.from,
                        dateEpochSeconds = System.currentTimeMillis() / 1000,
                    )
                // Recorded as sent only when the MMS service confirms (onReadReportSent).
                val result =
                    Intent(
                        MessageStatusReceiver.ACTION_MMS_READ_REPORT_SENT,
                        Uri.fromParts("mmsreadreport", request.rowId.toString(), null),
                        context,
                        MessageStatusReceiver::class.java,
                    ).putExtra(MessageStatusReceiver.EXTRA_MMS_ROW_ID, request.rowId)
                val started = sendPdu(pdu, request.subscriptionId, "read", result)
                if (!started) store.finishReadReport(request.rowId, sent = false)
            }
        }
    }

    /** The MMS service reported on a read report sent by [sendReadReports]. */
    suspend fun onReadReportSent(
        rowId: Long,
        resultCode: Int,
    ) {
        store.finishReadReport(rowId, sent = resultCode == Activity.RESULT_OK)
    }

    /**
     * Hands a small PDU (acknowledge, read report) to the MMS service. With [resultIntent], the
     * service's verdict is broadcast to it; without one, this is fire-and-forget.
     */
    private fun sendPdu(
        pdu: ByteArray,
        subscriptionId: Int,
        kind: String,
        resultIntent: Intent? = null,
    ): Boolean {
        val file = File(mmsDir(), "$kind-${UUID.randomUUID()}.pdu")
        return try {
            file.writeBytes(pdu)
            val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val sentIntent =
                resultIntent?.let {
                    PendingIntent.getBroadcast(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                }
            smsManager(subscriptionId).sendMultimediaMessage(context, contentUri, null, null, sentIntent)
            // The file must outlive the platform's read of it; stale ones are cleared on next start.
            true
        } catch (e: Exception) {
            Log.w(TAG, "MMS $kind PDU not sent: ${e.javaClass.simpleName}")
            false
        }
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

    companion object {
        private const val TAG = "MmsDownloader"
        const val NO_PLACEHOLDER = -1L
        private const val STALE_AFTER_MS = 24 * 60 * 60 * 1000L
    }
}
