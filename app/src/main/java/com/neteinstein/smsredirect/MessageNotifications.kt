package com.neteinstein.smsredirect

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.neteinstein.smsredirect.presentation.navigation.Routes

/**
 * Notifications for incoming messages: one per conversation, MessagingStyle, with inline reply and
 * "mark as read". This is a messaging app, so -- unlike the backup notifications -- these do show
 * who wrote and what. On the lock screen only the public version ("New message") is shown.
 *
 * Ids are offset from the other features' (SmsForwarder 0+, ScheduledSmsSender 100_000+,
 * BackupNotifications 200_000+).
 */
object MessageNotifications {
    const val CHANNEL_ID = "messages_channel"
    private const val FAILURE_CHANNEL_ID = "messages_failure_channel"
    private const val ID_OFFSET = 300_000
    private const val SEND_FAILED_ID = 299_998

    /** One notification per undownloaded MMS, so a second failure never hides the first's Retry. */
    private const val MMS_DOWNLOAD_FAILED_ID_BASE = 400_000
    private const val MMS_DOWNLOAD_FAILED_ID_RANGE = 100_000
    const val KEY_REPLY_TEXT = "reply_text"

    /** The conversation the user is looking at right now; its messages need no notification. */
    @Volatile
    var visibleThreadId: Long? = null

    fun showIncoming(
        context: Context,
        threadId: Long,
        senderName: String,
        text: String,
    ) {
        if (visibleThreadId == threadId) return
        val manager = notificationManagerOrNull(context) ?: return
        val id = notificationId(threadId)
        val me = Person.Builder().setName(context.getString(R.string.messages_notification_me)).build()
        val sender = Person.Builder().setName(senderName).build()

        // Add to the conversation's existing notification so a burst reads as a conversation.
        val existing =
            manager.activeNotifications
                .firstOrNull { it.id == id }
                ?.notification
                ?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }
        val style = existing ?: NotificationCompat.MessagingStyle(me)
        style.addMessage(text, System.currentTimeMillis(), sender)

        val publicVersion =
            baseBuilder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.messages_notification_public_title))
                .build()

        val notification =
            baseBuilder(context, CHANNEL_ID)
                .setStyle(style)
                .setContentTitle(senderName)
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setAutoCancel(true)
                .setContentIntent(openConversation(context, threadId))
                .addAction(replyAction(context, threadId))
                .addAction(
                    NotificationCompat.Action
                        .Builder(
                            0,
                            context.getString(R.string.messages_notification_mark_read),
                            actionIntent(context, MessageStatusReceiver.ACTION_MARK_READ, threadId),
                        ).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                        .setShowsUserInterface(false)
                        .build(),
                ).build()
        manager.notify(id, notification)
    }

    fun cancel(
        context: Context,
        threadId: Long,
    ) {
        context.getSystemService(NotificationManager::class.java)?.cancel(notificationId(threadId))
    }

    /** No content, just that a message did not go out and where to retry it. */
    fun showSendFailed(
        context: Context,
        threadId: Long?,
    ) {
        val manager = notificationManagerOrNull(context) ?: return
        val builder =
            baseBuilder(context, FAILURE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(context.getString(R.string.messages_notification_send_failed))
                .setContentText(context.getString(R.string.messages_notification_send_failed_text))
                .setAutoCancel(true)
        // The conversation when it is known, otherwise the app (the Messages tab).
        builder.setContentIntent(threadId?.let { openConversation(context, it) } ?: openApp(context))
        manager.notify(SEND_FAILED_ID, builder.build())
    }

    fun showMmsDownloadFailed(
        context: Context,
        contentLocation: String,
        retry: PendingIntent,
    ) {
        val manager = notificationManagerOrNull(context) ?: return
        val notification =
            baseBuilder(context, FAILURE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(context.getString(R.string.messages_notification_mms_failed))
                .setContentText(context.getString(R.string.messages_notification_mms_failed_text))
                .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.messages_notification_mms_failed_text)))
                // Not auto-cancelled: this notification is the only way back to the message.
                .setContentIntent(retry)
                .addAction(0, context.getString(R.string.messages_notification_retry), retry)
                .build()
        manager.notify(mmsDownloadFailedId(contentLocation), notification)
    }

    fun cancelMmsDownloadFailed(
        context: Context,
        contentLocation: String,
    ) {
        context.getSystemService(NotificationManager::class.java)?.cancel(mmsDownloadFailedId(contentLocation))
    }

    private fun mmsDownloadFailedId(contentLocation: String): Int =
        MMS_DOWNLOAD_FAILED_ID_BASE + Math.floorMod(contentLocation.hashCode(), MMS_DOWNLOAD_FAILED_ID_RANGE)

    /**
     * True for the notifications opening the app must leave alone: unread conversations, and
     * message failures -- an undownloaded MMS exists nowhere but in its Retry notification.
     */
    fun isMessageNotification(notification: Notification): Boolean =
        notification.channelId == CHANNEL_ID || notification.channelId == FAILURE_CHANNEL_ID

    private fun replyAction(
        context: Context,
        threadId: Long,
    ): NotificationCompat.Action {
        val input =
            RemoteInput
                .Builder(KEY_REPLY_TEXT)
                .setLabel(context.getString(R.string.messages_notification_reply_hint))
                .build()
        return NotificationCompat.Action
            .Builder(
                0,
                context.getString(R.string.messages_notification_reply),
                // Mutable: RemoteInput fills the typed reply into this intent.
                actionIntent(context, MessageStatusReceiver.ACTION_REPLY, threadId, mutable = true),
            ).addRemoteInput(input)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .setAllowGeneratedReplies(false)
            .build()
    }

    private fun actionIntent(
        context: Context,
        action: String,
        threadId: Long,
        mutable: Boolean = false,
    ): PendingIntent {
        val intent =
            Intent(action, "smsthread:$threadId".toUri(), context, MessageStatusReceiver::class.java)
                .putExtra(MessageStatusReceiver.EXTRA_THREAD_ID, threadId)
        val mutability = if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutability)
    }

    fun openConversation(
        context: Context,
        threadId: Long,
    ): PendingIntent {
        val intent =
            Intent(
                Intent.ACTION_VIEW,
                "${Routes.DEEP_LINK_URI}/${Routes.conversationRoute(threadId)}".toUri(),
                context,
                MainActivity::class.java,
            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        return PendingIntent.getActivity(
            context,
            notificationId(threadId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openApp(context: Context): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        return PendingIntent.getActivity(context, SEND_FAILED_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun notificationId(threadId: Long): Int = ID_OFFSET + (threadId % Int.MAX_VALUE.minus(ID_OFFSET)).toInt()

    private fun baseBuilder(
        context: Context,
        channelId: String,
    ): NotificationCompat.Builder =
        NotificationCompat
            .Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)

    private fun notificationManagerOrNull(context: Context): NotificationManager? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return null
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.messages_notification_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.messages_notification_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                FAILURE_CHANNEL_ID,
                context.getString(R.string.messages_notification_failure_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        return manager
    }
}
