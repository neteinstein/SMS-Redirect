package com.neteinstein.smsredirect

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.neteinstein.smsredirect.domain.contacts.ContactNameResolver
import com.neteinstein.smsredirect.domain.usecase.CanForwardSmsUseCase
import com.neteinstein.smsredirect.domain.usecase.FindMatchingRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.GetForwardPrefixNowUseCase
import com.neteinstein.smsredirect.domain.usecase.GetKeepHistoryNowUseCase
import com.neteinstein.smsredirect.domain.usecase.RecordForwardHistoryUseCase

class SmsForwarder(
    private val findMatchingRuleUseCase: FindMatchingRuleUseCase,
    private val canForwardSmsUseCase: CanForwardSmsUseCase,
    private val getKeepHistoryNowUseCase: GetKeepHistoryNowUseCase,
    private val recordForwardHistoryUseCase: RecordForwardHistoryUseCase,
    private val getForwardPrefixNowUseCase: GetForwardPrefixNowUseCase,
    private val contactNameResolver: ContactNameResolver,
) {
    companion object {
        private const val TAG = "SmsForwarder"
        private const val CHANNEL_ID = "sms_redirect_channel"
        private var notificationId = 0
    }

    suspend fun forward(
        context: Context,
        sender: String?,
        body: String,
    ) {
        Log.d(TAG, "forward() called — sender='$sender', bodyLength=${body.length}")

        if (body.isBlank()) {
            Log.w(TAG, "Skipping: body is blank")
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Skipping: SEND_SMS permission not granted")
            return
        }

        val isRoaming = isNetworkRoaming(context)
        Log.d(TAG, "Network roaming=$isRoaming")

        val canForward = canForwardSmsUseCase(isRoaming)
        if (!canForward) {
            Log.i(TAG, "Skipping: forwarding disabled while roaming")
            return
        }

        val rule = findMatchingRuleUseCase(sender)
        if (rule == null) {
            Log.i(TAG, "Skipping: no matching rule found for sender='$sender'")
            return
        }

        Log.i(TAG, "Matched rule id=${rule.id} name='${rule.name}' — forwarding to '${rule.targetNumber}'")

        try {
            val forwardPrefix = getForwardPrefixNowUseCase()
            val prefixedBody = if (forwardPrefix.isNotBlank()) "$forwardPrefix $body" else body

            val smsManager = context.getSystemService(SmsManager::class.java)
            if (smsManager == null) {
                Log.e(TAG, "Failed: SmsManager is null (no default subscription?)")
                return
            }

            val parts = smsManager.divideMessage(prefixedBody)
            if (parts.size == 1) {
                smsManager.sendTextMessage(rule.targetNumber, null, prefixedBody, null, null)
            } else {
                Log.d(TAG, "Message split into ${parts.size} parts")
                smsManager.sendMultipartTextMessage(rule.targetNumber, null, parts, null, null)
            }
            Log.i(TAG, "SMS forwarded successfully to '${rule.targetNumber}'")

            showForwardNotification(context, rule.targetNumber, rule.name)

            val keepHistory = getKeepHistoryNowUseCase()
            if (keepHistory) {
                recordForwardHistoryUseCase(
                    sender = sender.orEmpty(),
                    targetNumber = rule.targetNumber,
                    messageBody = body,
                    ruleName = rule.name,
                )
                Log.d(TAG, "Forward saved to history")
            } else {
                Log.d(TAG, "Keep history disabled, skipping history record")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to '${rule.targetNumber}'", e)
        }
    }

    private fun isNetworkRoaming(context: Context): Boolean {
        val manager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        if (manager == null) {
            Log.w(TAG, "TelephonyManager unavailable, assuming not roaming")
            return false
        }
        return manager.isNetworkRoaming
    }

    private suspend fun showForwardNotification(
        context: Context,
        targetNumber: String,
        ruleName: String,
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "POST_NOTIFICATIONS permission not granted, skipping notification")
            return
        }

        val contactDisplay = buildContactDisplay(targetNumber)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "SMS Redirect",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notifications for redirected SMS messages"
            }
        notificationManager.createNotificationChannel(channel)

        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle("SMS Redirected")
                .setContentText("Redirect message to $contactDisplay from Rule: $ruleName")
                .setAutoCancel(true)
                .build()

        notificationManager.notify(notificationId++, notification)
        Log.d(TAG, "Notification shown for redirect to $contactDisplay via rule '$ruleName'")
    }

    private suspend fun buildContactDisplay(targetNumber: String): String {
        val name = contactNameResolver.lookupNameByNumber(targetNumber)
        return if (name != null) {
            "$name ($targetNumber)"
        } else {
            targetNumber
        }
    }
}
