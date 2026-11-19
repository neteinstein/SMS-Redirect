package com.neteinstein.smsredirect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SmsReceiver"
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        Log.d(TAG, "onReceive triggered — action=${intent?.action}")

        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.d(TAG, "Ignoring non-SMS intent: ${intent?.action}")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) {
            Log.w(TAG, "SMS_RECEIVED but no messages in intent")
            return
        }

        val sender = messages.first().originatingAddress
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }

        Log.i(TAG, "SMS received — hasSender=${!sender.isNullOrBlank()}, bodyLength=${body.length}")

        val container = (context.applicationContext as SmsRedirectApplication).appContainer
        if (container.features.forwarding) {
            ForwardSmsWorker.enqueue(context, sender, body)

            // Automation phone actions (ring, vibrate, sound mode). Matched here so that only the
            // matching rules' actions are queued, not the message body.
            val pending = goAsync()
            receiverScope.launch {
                try {
                    val scheduled = container.triggerPhoneActionsUseCase(sender, body)
                    if (scheduled > 0) Log.i(TAG, "Scheduled $scheduled phone action(s)")
                } catch (e: Exception) {
                    Log.e(TAG, "Could not match phone-action rules: ${e.javaClass.simpleName}")
                } finally {
                    pending.finish()
                }
            }
        }

        // Backup is triggered for every incoming SMS, independent of whether a redirect rule
        // matched -- SmsForwarder early-returns on missing SEND_SMS, on roaming and on no rule
        // match, none of which should stop a backup.
        //
        // Nothing from this broadcast is passed along. The worker re-reads the message from the
        // content provider, so the live path and the historical backfill derive identical keys;
        // the broadcast's SMSC timestamp and the provider's receive time differ by seconds to
        // minutes, and hashing one against the other would back the same message up twice.
        BackupSmsWorker.enqueueForIncomingSms(context)
    }
}
