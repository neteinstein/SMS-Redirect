package com.neteinstein.smsredirect

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.neteinstein.smsredirect.data.device.ActiveRing

/** Notifications for automation phone actions: the "ringing, tap to stop" one and failures. */
object PhoneActionNotifications {
    private const val CHANNEL_ID = "phone_action_channel"
    private const val TAG = "phone_action"

    // Continues the per-feature offset convention (see BackupNotifications).
    private const val RINGING_NOTIFICATION_ID = 300_000
    private const val SOUND_MODE_DENIED_NOTIFICATION_ID = 300_001

    /**
     * Shown while a ring plays; tapping, swiping it away or "Stop" all end it. Not ongoing, so
     * it can be swiped on Android 13 too. Returns false when the user cannot see it (permission
     * denied, notifications or this channel turned off): then there is no Stop button.
     */
    fun showRinging(
        context: Context,
        ruleName: String,
    ): Boolean {
        val manager = notificationManagerOrNull(context) ?: return false
        if (!manager.areNotificationsEnabled()) return false
        if (manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val stop = stopRingingIntent(context)
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentTitle(context.getString(R.string.phone_action_notification_ringing_title))
                .setContentText(context.getString(R.string.phone_action_notification_ringing_text, ruleName))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                // The ring itself is the sound; the notification must not add a second one.
                .setSilent(true)
                .setContentIntent(stop)
                .setDeleteIntent(stop)
                .addAction(0, context.getString(R.string.phone_action_notification_stop), stop)
                .build()
        manager.notify(TAG, RINGING_NOTIFICATION_ID, notification)
        return true
    }

    fun cancelRinging(context: Context) {
        notificationManagerOrNull(context)?.cancel(TAG, RINGING_NOTIFICATION_ID)
    }

    /** A sound-mode rule fired but the user has not granted "Do Not Disturb" access. */
    fun showSoundModeAccessDenied(
        context: Context,
        ruleName: String,
    ) {
        val manager = notificationManagerOrNull(context) ?: return
        val text = context.getString(R.string.phone_action_notification_access_denied_text, ruleName)
        val openSettings =
            PendingIntent.getActivity(
                context,
                SOUND_MODE_DENIED_NOTIFICATION_ID,
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(context.getString(R.string.phone_action_notification_access_denied_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(openSettings)
                .build()
        manager.notify(TAG, SOUND_MODE_DENIED_NOTIFICATION_ID, notification)
    }

    private fun stopRingingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            RINGING_NOTIFICATION_ID,
            Intent(context, StopRingingReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun notificationManagerOrNull(context: Context): NotificationManager? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return null
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.phone_action_notification_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { setSound(null, null) },
        )
        return manager
    }
}

/** Stops the current ring. Only reachable through this app's own PendingIntents. */
class StopRingingReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        ActiveRing.stop()
        PhoneActionNotifications.cancelRinging(context)
    }
}
