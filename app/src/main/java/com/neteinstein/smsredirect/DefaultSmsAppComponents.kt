package com.neteinstein.smsredirect

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/*
 * The four components Android requires of a default SMS app. SMS Toolbox is a full messaging app,
 * so each does real work:
 *
 * - SmsDeliverReceiver: while this app holds the SMS role, incoming SMS are delivered *only* here
 *   (SMS_DELIVER). It saves each one to the inbox and notifies the user.
 * - MmsDeliverReceiver: the WAP push announcing an MMS; starts the download (AndroidMmsDownloader).
 * - ComposeSmsActivity: "send a message" from other apps (sms:/smsto:/mms: links, Share).
 * - HeadlessSmsSendService: "respond via message" from the incoming-call screen.
 *
 * Forwarding and Gmail backup run off SMS_RECEIVED (SmsReceiver), which is broadcast to every app
 * alongside SMS_DELIVER, so they are unaffected by who holds the role.
 */

/** Saves each incoming SMS to the inbox and shows it as a conversation notification. */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts =
            Telephony.Sms.Intents
                .getMessagesFromIntent(intent)
                ?.filterNotNull()
                .orEmpty()
        val first = parts.firstOrNull() ?: return
        val address = first.displayOriginatingAddress.orEmpty()
        val body = parts.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
        val subscriptionId = intent.subscriptionId()
        val container = (context.applicationContext as SmsRedirectApplication).appContainer
        val pending = goAsync()
        receiverScope.launch {
            try {
                val threadId =
                    container.messageStore.saveIncomingSms(
                        address = address,
                        body = body,
                        // Device receive time, matching what every messaging app stores in DATE, so
                        // the backup key derived from it later is the one any other app would produce.
                        dateEpochMs = System.currentTimeMillis(),
                        dateSentEpochMs = first.timestampMillis,
                        subscriptionId = subscriptionId,
                    )
                if (threadId == null) {
                    Log.w(TAG, "Incoming SMS could not be saved")
                    return@launch
                }
                val name = container.contactNameResolver.lookupNameByNumber(address) ?: address
                MessageNotifications.showIncoming(context, threadId, name, body)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SmsDeliverReceiver"
        val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/** An MMS was announced; fetch it. The download result arrives at [MessageStatusReceiver]. */
class MmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        val pdu = intent.getByteArrayExtra("data") ?: return
        val container = (context.applicationContext as SmsRedirectApplication).appContainer
        val subscriptionId = intent.subscriptionId()
        val pending = goAsync()
        receiverScope.launch {
            try {
                // Saves the announcement as a "not downloaded" message first, then downloads it.
                val announced = container.mmsDownloader.onWapPush(pdu, subscriptionId) ?: return@launch
                // The download could not even start: no callback will come, so offer Retry now.
                if (!announced.started) {
                    val notification = announced.notification
                    MessageStatusReceiver.showMmsDownloadFailed(
                        context,
                        MessageStatusReceiver.Companion.FailedMmsDownload(
                            contentLocation = notification.contentLocation,
                            placeholderId = announced.pending?.messageId,
                            threadId = announced.pending?.threadId,
                            transactionId = notification.transactionId,
                            from = notification.from,
                            subscriptionId = subscriptionId,
                        ),
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/**
 * "Send a message" from another app: an `sms:`/`smsto:`/`mms:`/`mmsto:` link (a contact's message
 * button, a web page), or Share. Opens the conversation for those recipients with the text and
 * attachment filled in; nothing is sent until the user taps Send.
 */
class ComposeSmsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = intent
        val recipients =
            source.data
                ?.takeIf { it.scheme in COMPOSE_SCHEMES }
                ?.let(::recipientsOf)
                .orEmpty()
        val body =
            source.getStringExtra("sms_body")
                ?: source.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                ?: composeLinkBody(source.data?.encodedSchemeSpecificPart)
        val attachment =
            if (source.action == Intent.ACTION_SEND) source.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else null

        val forward =
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_COMPOSE)
                .putStringArrayListExtra(MainActivity.EXTRA_COMPOSE_ADDRESSES, ArrayList(recipients))
                .putExtra(MainActivity.EXTRA_COMPOSE_BODY, body)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (attachment != null && isShareableAttachment(attachment.scheme, attachment.encodedAuthority, packageName)) {
            // Pass our read grant on to the screen that will send it.
            forward.putExtra(MainActivity.EXTRA_COMPOSE_ATTACHMENT, attachment.toString())
            forward.putExtra(MainActivity.EXTRA_COMPOSE_ATTACHMENT_TYPE, source.type)
            forward.clipData = ClipData.newRawUri(null, attachment)
            forward.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(forward)
        finish()
    }

    private companion object {
        val COMPOSE_SCHEMES = setOf("sms", "smsto", "mms", "mmsto")
    }
}

/** "Respond via message" from the incoming-call screen: sends the quick reply as an SMS. */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val recipients = intent?.data?.let(::recipientsOf).orEmpty()
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        if (intent?.action != ACTION_RESPOND_VIA_MESSAGE || recipients.isEmpty() || text.isBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val sender = (application as SmsRedirectApplication).appContainer.messageSender
        serviceScope.launch {
            try {
                // One SMS per recipient, as the phone app expects; each lands in its own conversation.
                recipients.forEach { sender.sendSms(it, text) }
            } catch (e: Exception) {
                // Never crash the phone app's quick-reply path; the user can resend from the app.
                Log.w(TAG, "Quick reply failed: ${e.javaClass.simpleName}")
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private companion object {
        const val TAG = "HeadlessSmsSendService"
        const val ACTION_RESPOND_VIA_MESSAGE = "android.intent.action.RESPOND_VIA_MESSAGE"
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/** `smsto:+351912345678,+351913333333?body=hi` -> the two numbers. */
internal fun recipientsOf(uri: Uri): List<String> =
    uri.schemeSpecificPart
        ?.substringBefore('?')
        ?.split(',', ';')
        ?.map { Uri.decode(it).trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()

private fun Intent.subscriptionId(): Int =
    getIntExtra(
        SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
        getIntExtra("subscription", SubscriptionManager.INVALID_SUBSCRIPTION_ID),
    )
