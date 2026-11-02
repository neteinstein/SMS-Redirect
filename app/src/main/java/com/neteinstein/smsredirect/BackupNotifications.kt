package com.neteinstein.smsredirect

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.neteinstein.smsredirect.presentation.navigation.Routes

/**
 * Notifications for the Gmail backup feature.
 *
 * The other two features build their channels inline at post time (see [SmsForwarder] and
 * [ScheduledSmsSender]); this is the first one collected in a helper. Worth migrating those two
 * here eventually.
 */
object BackupNotifications {
    const val CHANNEL_ID = "sms_backup_channel"

    // Continues the existing offset convention: SmsForwarder ids start at 0 and
    // ScheduledSmsSender at 100_000, so the three features never collide.
    private const val PROGRESS_NOTIFICATION_ID = 200_000
    private const val AUTH_FAILED_NOTIFICATION_ID = 200_001
    private const val RESTORE_NOTIFICATION_ID = 200_002
    private const val DEFAULT_SMS_APP_NOTIFICATION_ID = 200_003

    fun showProgress(
        context: Context,
        done: Int,
        total: Int,
    ) {
        val manager = notificationManagerOrNull(context) ?: return
        val text = context.getString(R.string.backup_notification_progress, done, total)
        val notification =
            baseBuilder(context)
                .setContentTitle(text)
                .setProgress(total.coerceAtLeast(1), done, false)
                .setOngoing(true)
                // Progress ticks constantly; alerting once keeps it from buzzing per message.
                .setOnlyAlertOnce(true)
                .setContentIntent(deepLink(context, Routes.Home))
                .build()
        manager.notify(PROGRESS_NOTIFICATION_ID, notification)
    }

    fun showComplete(
        context: Context,
        total: Int,
    ) {
        val manager = notificationManagerOrNull(context) ?: return
        manager.cancel(PROGRESS_NOTIFICATION_ID)
        if (total <= 0) return
        val notification =
            baseBuilder(context)
                .setContentTitle(context.getString(R.string.backup_notification_complete, total))
                .setAutoCancel(true)
                .setContentIntent(deepLink(context, Routes.Home))
                .build()
        manager.notify(PROGRESS_NOTIFICATION_ID, notification)
    }

    fun showAuthFailed(context: Context) {
        val manager = notificationManagerOrNull(context) ?: return
        manager.cancel(PROGRESS_NOTIFICATION_ID)
        val text = context.getString(R.string.backup_notification_auth_failed)
        val notification =
            baseBuilder(context)
                .setContentTitle(context.getString(R.string.backup_notification_auth_failed_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(deepLink(context, Routes.Settings))
                .build()
        manager.notify(AUTH_FAILED_NOTIFICATION_ID, notification)
    }

    fun cancelProgress(context: Context) {
        notificationManagerOrNull(context)?.cancel(PROGRESS_NOTIFICATION_ID)
    }

    fun showRestoreProgress(
        context: Context,
        processed: Int,
        total: Int,
    ) {
        val manager = notificationManagerOrNull(context) ?: return
        val notification =
            baseBuilder(context)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(context.getString(R.string.restore_notification_progress, processed, total))
                .setProgress(total.coerceAtLeast(1), processed, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(deepLink(context, Routes.Home))
                .build()
        manager.notify(RESTORE_NOTIFICATION_ID, notification)
    }

    /**
     * Ends on the step users are most likely to forget: handing the SMS role back. Until they do,
     * their usual app receives nothing and MMS cannot be delivered at all.
     */
    fun showRestoreComplete(
        context: Context,
        restored: Int,
    ) {
        val manager = notificationManagerOrNull(context) ?: return
        val text = context.getString(R.string.restore_notification_complete_text)
        val notification =
            baseBuilder(context)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(context.resources.getQuantityString(R.plurals.restore_notification_complete, restored, restored))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(deepLink(context, Routes.Home))
                .build()
        manager.notify(RESTORE_NOTIFICATION_ID, notification)
    }

    fun showRestoreFailed(context: Context) {
        val manager = notificationManagerOrNull(context) ?: return
        val notification =
            baseBuilder(context)
                .setContentTitle(context.getString(R.string.restore_notification_failed))
                .setAutoCancel(true)
                .setContentIntent(deepLink(context, Routes.Home))
                .build()
        manager.notify(RESTORE_NOTIFICATION_ID, notification)
    }

    fun cancelRestoreProgress(context: Context) {
        notificationManagerOrNull(context)?.cancel(RESTORE_NOTIFICATION_ID)
    }

    /**
     * Shown when something arrives that only a full messaging app could handle -- a new SMS (which
     * is saved, but this app has no conversation view to show it in) or an MMS (which cannot be
     * saved at all). Deliberately carries no sender or message text.
     */
    fun showStillDefaultSmsApp(
        context: Context,
        mmsMissed: Boolean,
    ) {
        val manager = notificationManagerOrNull(context) ?: return
        val text =
            context.getString(
                if (mmsMissed) R.string.default_sms_notification_mms_text else R.string.default_sms_notification_sms_text,
            )
        val notification =
            baseBuilder(context)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(context.getString(R.string.default_sms_notification_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(deepLink(context, Routes.Home))
                .build()
        manager.notify(DEFAULT_SMS_APP_NOTIFICATION_ID, notification)
    }

    private fun baseBuilder(context: Context): NotificationCompat.Builder =
        NotificationCompat
            .Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)

    private fun deepLink(
        context: Context,
        route: String,
    ): PendingIntent {
        val intent =
            Intent(
                Intent.ACTION_VIEW,
                "${Routes.DEEP_LINK_URI}/$route".toUri(),
                context,
                MainActivity::class.java,
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        return PendingIntent.getActivity(
            context,
            route.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Returns null when POST_NOTIFICATIONS is not granted, so every call site degrades to a
     * silent no-op rather than throwing. Matches the guard in [SmsForwarder].
     */
    private fun notificationManagerOrNull(context: Context): NotificationManager? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return null
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.backup_notification_channel_name),
                // LOW: progress updates should not make a sound on every message.
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.backup_notification_channel_description)
            },
        )
        return manager
    }
}
