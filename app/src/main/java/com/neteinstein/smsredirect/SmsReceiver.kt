package com.neteinstein.smsredirect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return
        val sender = messages.first().originatingAddress
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }

        val serviceIntent = Intent(context, SmsForwardingService::class.java)
            .putExtra(SmsForwardingService.EXTRA_SENDER, sender)
            .putExtra(SmsForwardingService.EXTRA_BODY, body)
        runCatching { context.startService(serviceIntent) }
    }
}
