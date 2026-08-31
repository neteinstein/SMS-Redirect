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
