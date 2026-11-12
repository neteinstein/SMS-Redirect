package com.neteinstein.smsredirect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SmsReceiver"
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

        Log.i(TAG, "SMS received from='$sender', bodyLength=${body.length}")

        // The Play build has no Redirect feature and no SEND_SMS, so there is nothing to forward.
        if (BuildConfig.SMS_SENDING_ENABLED) {
            ForwardSmsWorker.enqueue(context, sender, body)
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
