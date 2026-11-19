package com.neteinstein.smsredirect

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.RemoteInput
import com.neteinstein.smsredirect.data.messaging.AndroidMmsDownloader
import com.neteinstein.smsredirect.data.messaging.MmsDownloadResult
import com.neteinstein.smsredirect.domain.messaging.SendOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * The app's own callbacks: SMS/MMS send results, finished MMS downloads, and the notification
 * actions. Not exported -- every intent reaching it is an explicit PendingIntent this app created.
 */
class MessageStatusReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val container = (context.applicationContext as SmsRedirectApplication).appContainer
        val resultCode = resultCode
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_SMS_SENT -> {
                        val uri = intent.data ?: return@launch
                        val failed =
                            container.messageSender.onSmsSent(
                                messageUri = uri,
                                resultCode = resultCode,
                                partIndex = intent.getIntExtra(EXTRA_PART_INDEX, 0),
                                partCount = intent.getIntExtra(EXTRA_PART_COUNT, 1),
                            )
                        if (failed) MessageNotifications.showSendFailed(context, container.messageSender.threadIdOf(uri))
                    }

                    ACTION_SMS_DELIVERED -> {
                        val uri = intent.data ?: return@launch
                        container.messageSender.onSmsDelivered(uri, intent.getByteArrayExtra("pdu"), intent.getStringExtra("format"))
                    }

                    ACTION_MMS_READ_REPORT_SENT -> {
                        val rowId = intent.getLongExtra(EXTRA_MMS_ROW_ID, -1L)
                        if (rowId > 0) container.mmsDownloader.onReadReportSent(rowId, resultCode)
                    }

                    ACTION_MMS_SENT -> {
                        val uri = intent.data ?: return@launch
                        val failed =
                            container.messageSender.onMmsSent(
                                messageUri = uri,
                                resultCode = resultCode,
                                response = intent.getByteArrayExtra(SmsManager.EXTRA_MMS_DATA),
                                pduFile = intent.getStringExtra(EXTRA_PDU_FILE)?.let(::File),
                            )
                        if (failed) MessageNotifications.showSendFailed(context, container.messageSender.threadIdOf(uri))
                    }

                    ACTION_MMS_DOWNLOADED -> {
                        onMmsDownloaded(context, intent, resultCode)
                    }

                    ACTION_MMS_RETRY_DOWNLOAD -> {
                        val failed = FailedMmsDownload.from(intent) ?: return@launch
                        MessageNotifications.cancelMmsDownloadFailed(context, failed.contentLocation)
                        val started =
                            if (failed.placeholderId != null) {
                                container.mmsDownloader.download(failed.placeholderId)
                            } else {
                                container.mmsDownloader.startDownload(
                                    null,
                                    failed.contentLocation,
                                    failed.transactionId,
                                    failed.from,
                                    failed.subscriptionId,
                                )
                            }
                        // No callback comes for a download that never started: keep the way back.
                        if (!started) showMmsDownloadFailed(context, failed)
                    }

                    ACTION_REPLY -> {
                        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1)
                        val text =
                            RemoteInput
                                .getResultsFromIntent(
                                    intent,
                                )?.getCharSequence(MessageNotifications.KEY_REPLY_TEXT)
                                ?.toString()
                        if (threadId < 0 || text.isNullOrBlank()) return@launch
                        val addresses = container.getConversationAddressesUseCase(threadId)
                        val outcome = container.sendMessageUseCase(addresses, text)
                        container.markConversationReadUseCase(threadId)
                        MessageNotifications.cancel(context, threadId)
                        if (outcome is SendOutcome.Failed) MessageNotifications.showSendFailed(context, threadId)
                    }

                    ACTION_MARK_READ -> {
                        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1)
                        if (threadId < 0) return@launch
                        container.markConversationReadUseCase(threadId)
                        MessageNotifications.cancel(context, threadId)
                    }
                }
            } catch (e: Exception) {
                // A status callback must never crash the app; the message row keeps its state.
                Log.w(TAG, "Handling ${intent.action} failed: ${e.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun onMmsDownloaded(
        context: Context,
        intent: Intent,
        resultCode: Int,
    ) {
        val container = (context.applicationContext as SmsRedirectApplication).appContainer
        val file = intent.getStringExtra(EXTRA_PDU_FILE)?.let(::File) ?: return
        val subscriptionId = intent.getIntExtra(EXTRA_SUBSCRIPTION_ID, -1)
        val failedDownload = FailedMmsDownload.from(intent)
        val result =
            container.mmsDownloader.onDownloaded(
                resultCode,
                file,
                intent.getStringExtra(EXTRA_TRANSACTION_ID),
                subscriptionId,
                failedDownload?.placeholderId,
            )
        val incoming =
            when (result) {
                is MmsDownloadResult.Saved -> {
                    result.message
                }

                MmsDownloadResult.AlreadySaved -> {
                    return
                }

                is MmsDownloadResult.Failed -> {
                    // Most often no mobile data. The conversation keeps a "not downloaded" row with
                    // a Download button; the notification says so and offers Retry too.
                    failedDownload?.let { showMmsDownloadFailed(context, it.copy(threadId = result.threadId)) }
                    return
                }
            }
        failedDownload?.let { MessageNotifications.cancelMmsDownloadFailed(context, it.contentLocation) }
        val sender = incoming.from.orEmpty()
        val name =
            container.contactNameResolver.lookupNameByNumber(sender)
                ?: sender.ifBlank { context.getString(R.string.messages_unknown_sender) }
        val text =
            incoming.text.ifBlank {
                if (incoming.hasAttachment) context.getString(R.string.messages_picture_message) else ""
            }
        val suspicious = container.assessIncomingMessageUseCase(sender, incoming.text).suspicious
        MessageNotifications.showIncoming(context, incoming.threadId, name, text, suspicious)
    }

    companion object {
        private const val TAG = "MessageStatusReceiver"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Everything needed to retry a download, carried in the notification's Retry intent. */
        data class FailedMmsDownload(
            val contentLocation: String,
            val placeholderId: Long?,
            val threadId: Long?,
            val transactionId: String?,
            val from: String?,
            val subscriptionId: Int,
        ) {
            fun writeTo(intent: Intent): Intent =
                intent
                    .putExtra(EXTRA_CONTENT_LOCATION, contentLocation)
                    .putExtra(EXTRA_PLACEHOLDER_ID, placeholderId ?: AndroidMmsDownloader.NO_PLACEHOLDER)
                    .putExtra(EXTRA_THREAD_ID, threadId ?: -1L)
                    .putExtra(EXTRA_TRANSACTION_ID, transactionId)
                    .putExtra(EXTRA_FROM, from)
                    .putExtra(EXTRA_SUBSCRIPTION_ID, subscriptionId)

            companion object {
                fun from(intent: Intent): FailedMmsDownload? {
                    val location = intent.getStringExtra(EXTRA_CONTENT_LOCATION) ?: return null
                    return FailedMmsDownload(
                        contentLocation = location,
                        placeholderId = intent.getLongExtra(EXTRA_PLACEHOLDER_ID, AndroidMmsDownloader.NO_PLACEHOLDER).takeIf { it > 0 },
                        threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L).takeIf { it > 0 },
                        transactionId = intent.getStringExtra(EXTRA_TRANSACTION_ID),
                        from = intent.getStringExtra(EXTRA_FROM),
                        subscriptionId = intent.getIntExtra(EXTRA_SUBSCRIPTION_ID, -1),
                    )
                }
            }
        }

        /**
         * A "not downloaded" notification. Tapping it opens the conversation, where the message
         * waits with a Download button; its Retry action starts the same download again.
         */
        fun showMmsDownloadFailed(
            context: Context,
            failed: FailedMmsDownload,
        ) {
            val retry =
                // The data makes each MMS's retry intent distinct; it is never resolved.
                failed.writeTo(
                    Intent(
                        ACTION_MMS_RETRY_DOWNLOAD,
                        Uri.fromParts("mmsretry", failed.contentLocation, null),
                        context,
                        MessageStatusReceiver::class.java,
                    ),
                )
            MessageNotifications.showMmsDownloadFailed(
                context,
                failed.contentLocation,
                PendingIntent.getBroadcast(context, 0, retry, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
                failed.threadId,
            )
        }

        const val ACTION_SMS_SENT = "com.neteinstein.smsredirect.action.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.neteinstein.smsredirect.action.SMS_DELIVERED"
        const val ACTION_MMS_SENT = "com.neteinstein.smsredirect.action.MMS_SENT"
        const val ACTION_MMS_READ_REPORT_SENT = "com.neteinstein.smsredirect.action.MMS_READ_REPORT_SENT"
        const val EXTRA_MMS_ROW_ID = "mms_row_id"
        const val ACTION_MMS_DOWNLOADED = "com.neteinstein.smsredirect.action.MMS_DOWNLOADED"
        const val ACTION_MMS_RETRY_DOWNLOAD = "com.neteinstein.smsredirect.action.MMS_RETRY_DOWNLOAD"
        const val ACTION_REPLY = "com.neteinstein.smsredirect.action.REPLY"
        const val ACTION_MARK_READ = "com.neteinstein.smsredirect.action.MARK_READ"

        const val EXTRA_PART_INDEX = "part_index"
        const val EXTRA_PART_COUNT = "part_count"
        const val EXTRA_PDU_FILE = "pdu_file"
        const val EXTRA_CONTENT_LOCATION = "content_location"
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        const val EXTRA_FROM = "from"
        const val EXTRA_SUBSCRIPTION_ID = "subscription_id"
        const val EXTRA_THREAD_ID = "thread_id"
        const val EXTRA_PLACEHOLDER_ID = "placeholder_id"
    }
}
