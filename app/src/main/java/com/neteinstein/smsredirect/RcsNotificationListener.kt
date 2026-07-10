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
 * new messages from the style first, falling back to EXTRA_TEXT/EXTRA_BIG_TEXT.
 *
 * This is a best-effort safety net, not a guaranteed delivery channel: the system only
 * invokes this service while Notification Access is granted and bound (see
 * [onListenerConnected]/[onListenerDisconnected] below to verify binding via logcat), and
 * Android 15+ redacts one-time-passcode content from notifications before listeners ever
 * see it (see Architecture.md for details and mitigations).
 */
class RcsNotificationListener : NotificationListenerService() {
    /** A single message extracted from a notification, with its post timestamp. */
    internal data class TimestampedMessage(
        val timestamp: Long,
        val sender: String?,
        val body: String,
    )

    companion object {
        private const val TAG = "RcsNotificationListener"

        /** SharedPreferences file used to expose listener health for diagnostics/self-healing. */
        internal const val PREFS_NAME = "rcs_listener_diagnostics"
        internal const val KEY_LAST_CONNECTED_AT = "last_connected_at"
        internal const val KEY_LAST_EVENT_AT = "last_event_at"

        /** Caps in-memory per-conversation tracking so a long-lived process can't grow unbounded. */
        private const val MAX_TRACKED_CONVERSATIONS = 300

        private val MESSAGING_PACKAGES =
            setOf(
                "com.google.android.apps.messaging", // Google Messages
                "com.samsung.android.messaging", // Samsung Messages
                "com.android.messaging", // AOSP Messages
            )

        /**
         * Pure decision helper: given every message currently present in a MessagingStyle
         * notification and the timestamp of the last message we already forwarded for that
         * notification, returns only the messages that are new, oldest first.
         *
         * A notification can accumulate more than one unread message before
         * [onNotificationPosted] fires again — for example, two RCS messages arriving in quick
         * succession can be coalesced by the system into a single update. Forwarding only the
         * latest message in that case would silently drop the earlier one, so we forward
         * everything newer than what we've already sent instead of just `messages.last()`.
         */
        internal fun selectNewMessages(
            messages: List<TimestampedMessage>,
            lastSeenTimestamp: Long,
        ): List<TimestampedMessage> =
            messages
                .filter { it.timestamp > lastSeenTimestamp }
                .sortedBy { it.timestamp }
    }

    /** Tracks the timestamp of the last message forwarded per notification key (per conversation). */
    private val lastSeenTimestampByKey = mutableMapOf<String, Long>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Listener connected — system has bound the service and will deliver notifications")
        recordDiagnosticTimestamp(KEY_LAST_CONNECTED_AT)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Listener disconnected — system unbound the service, no notifications will arrive until rebind")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val packageName = sbn.packageName
        val notification = sbn.notification

        // Logged for every notification (before any filtering) so logcat can distinguish
        // "listener never invoked" (permission/binding problem) from "invoked but filtered out"
        // (package/category/flags mismatch below).
        Log.d(
            TAG,
            "onNotificationPosted — package=$packageName, category=${notification?.category}, " +
                "flags=${notification?.flags}, key=${sbn.key}",
        )

        if (packageName !in MESSAGING_PACKAGES) return
        if (notification == null) return

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

        // Try MessagingStyle first (used by Google Messages for RCS). If this is a
        // MessagingStyle notification at all, it fully owns the outcome — including the
        // "nothing new to forward" case — so we never fall through to plain extras for it.
        if (handleMessagingStyle(sbn.key, notification, packageName)) return

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
        recordDiagnosticTimestamp(KEY_LAST_EVENT_AT)
        ForwardSmsWorker.enqueue(applicationContext, sender, body)
    }

    /**
     * Extracts and forwards any new messages from a MessagingStyle notification (the style
     * Google Messages uses for RCS chats).
     *
     * @return true if [notification] was a MessagingStyle notification — regardless of whether
     * it contained a new message — so the caller knows not to fall back to extras extraction.
     */
    private fun handleMessagingStyle(
        key: String,
        notification: Notification,
        packageName: String,
    ): Boolean {
        val style =
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
                ?: return false

        val allMessages =
            style.messages.mapNotNull { message ->
                val body = message.text?.toString()
                if (body.isNullOrBlank()) return@mapNotNull null
                TimestampedMessage(
                    timestamp = message.timestamp,
                    sender = message.person?.name?.toString() ?: style.conversationTitle?.toString(),
                    body = body,
                )
            }

        val lastSeen = lastSeenTimestampByKey[key] ?: 0L
        val newMessages = selectNewMessages(allMessages, lastSeen)

        if (newMessages.isEmpty()) {
            Log.d(TAG, "MessagingStyle notification from $packageName had no new message since last seen")
            return true
        }

        newMessages.forEach { message ->
            Log.i(
                TAG,
                "RCS MessagingStyle from '${message.sender}' via $packageName, bodyLength=${message.body.length}",
            )
            ForwardSmsWorker.enqueue(applicationContext, message.sender, message.body)
        }

        rememberLastSeenTimestamp(key, newMessages.last().timestamp)
        recordDiagnosticTimestamp(KEY_LAST_EVENT_AT)
        return true
    }

    private fun rememberLastSeenTimestamp(
        key: String,
        timestamp: Long,
    ) {
        if (lastSeenTimestampByKey.size > MAX_TRACKED_CONVERSATIONS) {
            lastSeenTimestampByKey.clear()
        }
        lastSeenTimestampByKey[key] = timestamp
    }

    /** Best-effort write so MainActivity can tell "granted but never bound" apart from "working". */
    private fun recordDiagnosticTimestamp(key: String) {
        runCatching {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putLong(key, System.currentTimeMillis())
                .apply()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No action needed
    }
}
