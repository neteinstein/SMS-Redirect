package com.neteinstein.smsredirect

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.net.toUri
import com.neteinstein.smsredirect.domain.model.SmsBox
import com.neteinstein.smsredirect.domain.model.SmsMessageToRestore
import com.neteinstein.smsredirect.presentation.navigation.Routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/*
 * Android only offers the SMS role to an app that declares all four of these components, and
 * only the SMS-role holder may write to `content://sms`. That is the sole reason they exist:
 * restoring a backup means briefly becoming the default SMS app.
 *
 * While the app holds the role, incoming SMS are delivered *only* to the role holder via
 * SMS_DELIVER -- the user's usual messaging app gets nothing. So these are not stubs where it
 * matters: an incoming SMS is written to the inbox so nothing is lost, and quick-reply sends are
 * honoured. MMS is out of scope: it cannot be downloaded here, and the user is told so.
 *
 * Forwarding and Gmail backup keep working unchanged meanwhile, because SMS_RECEIVED is still
 * broadcast to every app alongside SMS_DELIVER.
 */

/** Saves each incoming SMS to the inbox while this app is the default SMS app. */
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
        val message =
            SmsMessageToRestore(
                address = first.displayOriginatingAddress.orEmpty(),
                body = parts.joinToString(separator = "") { it.displayMessageBody.orEmpty() },
                // Device receive time, matching what every messaging app stores in DATE, so the
                // backup key derived from it later is the same one any other app would produce.
                dateEpochMs = System.currentTimeMillis(),
                dateSentEpochMs = first.timestampMillis,
                box = SmsBox.INBOX,
            )
        val writer = (context.applicationContext as SmsRedirectApplication).appContainer.smsMessageWriter
        val pending = goAsync()
        receiverScope.launch {
            try {
                if (!writer.insert(message, read = false)) Log.w(TAG, "Incoming SMS could not be saved")
                BackupNotifications.showStillDefaultSmsApp(context, mmsMissed = false)
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

/** MMS is out of scope; the user is warned that one arrived and could not be received. */
class MmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        BackupNotifications.showStillDefaultSmsApp(context, mmsMissed = true)
    }
}

/**
 * Handles "send SMS" intents from other apps (e.g. a contact's message button). There is no
 * composer here, so it opens the Backup screen, which explains how to switch back.
 */
class ComposeSmsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(Intent.ACTION_VIEW, "${Routes.DEEP_LINK_URI}/${Routes.Home}".toUri(), this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        finish()
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
        try {
            val recipients =
                intent
                    ?.data
                    ?.schemeSpecificPart
                    ?.substringBefore('?')
                    ?.split(',', ';')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()
            val text = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            if (intent?.action == ACTION_RESPOND_VIA_MESSAGE && recipients.isNotEmpty() && text.isNotBlank()) {
                val smsManager = getSystemService(SmsManager::class.java)
                val writer = (application as SmsRedirectApplication).appContainer.smsMessageWriter
                for (recipient in recipients) {
                    smsManager.sendMultipartTextMessage(recipient, null, smsManager.divideMessage(text), null, null)
                    // The role holder owns the sent box too; without this the reply would be
                    // missing from the conversation once the user switches back.
                    serviceScope.launch {
                        writer.insert(
                            SmsMessageToRestore(recipient, text, System.currentTimeMillis(), 0L, SmsBox.SENT),
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Never crash the phone app's quick-reply path; the user can resend from their app.
            Log.w(TAG, "Quick reply failed", e)
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private companion object {
        const val TAG = "HeadlessSmsSendService"
        const val ACTION_RESPOND_VIA_MESSAGE = "android.intent.action.RESPOND_VIA_MESSAGE"
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
