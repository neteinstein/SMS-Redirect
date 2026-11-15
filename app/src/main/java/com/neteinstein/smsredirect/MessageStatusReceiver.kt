package com.neteinstein.smsredirect

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.RemoteInput
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
                        if (failed) MessageNotifications.showSendFailed(context, threadId = null)
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
                        if (failed) MessageNotifications.showSendFailed(context, threadId = null)
                    }

                    ACTION_MMS_DOWNLOADED -> {
                        onMmsDownloaded(context, intent, resultCode)
                    }

                    ACTION_MMS_RETRY_DOWNLOAD -> {
                        MessageNotifications.cancelMmsDownloadFailed(context)
                        val location = intent.getStringExtra(EXTRA_CONTENT_LOCATION) ?: return@launch
                        container.mmsDownloader.download(
                            contentLocation = location,
                            transactionId = intent.getStringExtra(EXTRA_TRANSACTION_ID),
                            from = intent.getStringExtra(EXTRA_FROM),
                            subscriptionId = intent.getIntExtra(EXTRA_SUBSCRIPTION_ID, -1),
                        )
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
        val incoming = container.mmsDownloader.onDownloaded(resultCode, file, intent.getStringExtra(EXTRA_TRANSACTION_ID), subscriptionId)
        if (incoming == null) {
            // Most often no mobile data. Offer a retry rather than silently losing the message.
            val retry =
                Intent(intent)
                    .setAction(ACTION_MMS_RETRY_DOWNLOAD)
                    .setClass(context, MessageStatusReceiver::class.java)
            MessageNotifications.showMmsDownloadFailed(
                context,
                PendingIntent.getBroadcast(context, 0, retry, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
            return
        }
        val sender = incoming.from.orEmpty()
        val name =
            container.contactNameResolver.lookupNameByNumber(sender)
                ?: sender.ifBlank { context.getString(R.string.messages_unknown_sender) }
        val text =
            incoming.text.ifBlank {
                if (incoming.hasAttachment) context.getString(R.string.messages_picture_message) else ""
            }
        MessageNotifications.showIncoming(context, incoming.threadId, name, text)
    }

    companion object {
        private const val TAG = "MessageStatusReceiver"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        const val ACTION_SMS_SENT = "com.neteinstein.smsredirect.action.SMS_SENT"
        const val ACTION_MMS_SENT = "com.neteinstein.smsredirect.action.MMS_SENT"
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
    }
}
