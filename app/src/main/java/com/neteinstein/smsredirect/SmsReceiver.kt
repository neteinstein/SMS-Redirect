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

    override fun onReceive(context: Context, intent: Intent?) {
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

        ForwardSmsWorker.enqueue(context, sender, body)
    }
}
