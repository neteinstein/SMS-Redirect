package com.neteinstein.smsredirect

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Listens for incoming notifications from messaging apps (primarily Google Messages)
 * to catch RCS and other internet-based messages that bypass the SMS_RECEIVED broadcast.
 */
class RcsNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "RcsNotificationListener"

        private val MESSAGING_PACKAGES = setOf(
            "com.google.android.apps.messaging"
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val packageName = sbn.packageName
        if (packageName !in MESSAGING_PACKAGES) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        if (body.isNullOrBlank()) {
            Log.d(TAG, "Ignoring notification from $packageName with empty body")
            return
        }

        Log.i(TAG, "RCS/message notification from '$sender' via $packageName, bodyLength=${body.length}")

        ForwardSmsWorker.enqueue(applicationContext, sender, body)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No action needed
    }
}
