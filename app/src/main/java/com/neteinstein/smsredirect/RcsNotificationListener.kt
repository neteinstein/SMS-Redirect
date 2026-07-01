package com.neteinstein.smsredirect

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Listens for incoming notifications from messaging apps (primarily Google Messages)
 * to catch RCS and other internet-based messages that bypass the SMS_RECEIVED broadcast.
 *
 * Google Messages uses MessagingStyle notifications for RCS chats, so we extract
 * the latest message from the style first, falling back to EXTRA_TEXT/EXTRA_BIG_TEXT.
 */
class RcsNotificationListener : NotificationListenerService() {
    companion object {
        private const val TAG = "RcsNotificationListener"

        private val MESSAGING_PACKAGES =
            setOf(
                "com.google.android.apps.messaging", // Google Messages
                "com.samsung.android.messaging", // Samsung Messages
                "com.android.messaging", // AOSP Messages
            )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val packageName = sbn.packageName
        if (packageName !in MESSAGING_PACKAGES) return

        val notification = sbn.notification ?: return

        // Skip group summary notifications — we process individual conversation notifications
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
            Log.d(TAG, "Skipping group summary notification from $packageName")
            return
        }

        // Skip foreground service notifications (e.g. "Messages is doing work in the background")
        if (notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) {
            Log.d(TAG, "Skipping foreground service notification from $packageName")
            return
        }

        // Skip ongoing notifications (progress indicators, background tasks)
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) {
            Log.d(TAG, "Skipping ongoing notification from $packageName")
            return
        }

        // Only process message-category notifications when category is set
        val category = notification.category
        if (category != null && category != Notification.CATEGORY_MESSAGE) {
            Log.d(TAG, "Skipping non-message category '$category' notification from $packageName")
            return
        }

        // Try MessagingStyle first (used by Google Messages for RCS)
        val messagingResult = extractFromMessagingStyle(notification)
        if (messagingResult != null) {
            val (sender, body) = messagingResult
            Log.i(TAG, "RCS MessagingStyle from '$sender' via $packageName, bodyLength=${body.length}")
            ForwardSmsWorker.enqueue(applicationContext, sender, body)
            return
        }

        // Fall back to standard notification extras
        val extras = notification.extras ?: return
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val body =
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        if (body.isNullOrBlank()) {
            Log.d(TAG, "Ignoring notification from $packageName with empty body")
            return
        }

        Log.i(TAG, "RCS/message notification from '$sender' via $packageName, bodyLength=${body.length}")
        ForwardSmsWorker.enqueue(applicationContext, sender, body)
    }

    /**
     * Extracts the most recent message from a MessagingStyle notification.
     * Returns a Pair(sender, body) or null if not a MessagingStyle notification.
     */
    private fun extractFromMessagingStyle(notification: Notification): Pair<String?, String>? {
        val style =
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
                ?: return null

        val messages = style.messages
        if (messages.isEmpty()) return null

        val latest = messages.last()
        val body = latest.text?.toString()
        if (body.isNullOrBlank()) return null

        val sender = latest.person?.name?.toString() ?: style.conversationTitle?.toString()
        return Pair(sender, body)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No action needed
    }
}
