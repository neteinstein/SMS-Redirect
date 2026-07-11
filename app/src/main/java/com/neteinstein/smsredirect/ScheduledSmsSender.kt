package com.neteinstein.smsredirect

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.neteinstein.smsredirect.domain.contacts.ContactNameResolver
import com.neteinstein.smsredirect.domain.model.RepeatType
import com.neteinstein.smsredirect.domain.model.ScheduledSms
import com.neteinstein.smsredirect.domain.scheduling.RecurrenceCalculator
import com.neteinstein.smsredirect.domain.scheduling.ScheduledSmsScheduler
import com.neteinstein.smsredirect.domain.usecase.GetKeepScheduleHistoryNowUseCase
import com.neteinstein.smsredirect.domain.usecase.GetScheduledSmsUseCase
import com.neteinstein.smsredirect.domain.usecase.GetShowNotificationsNowUseCase
import com.neteinstein.smsredirect.domain.usecase.RecordScheduledSmsHistoryUseCase
import com.neteinstein.smsredirect.presentation.navigation.Routes

class ScheduledSmsSender(
    private val getScheduledSmsUseCase: GetScheduledSmsUseCase,
    private val recordScheduledSmsHistoryUseCase: RecordScheduledSmsHistoryUseCase,
    private val getKeepScheduleHistoryNowUseCase: GetKeepScheduleHistoryNowUseCase,
    private val getShowNotificationsNowUseCase: GetShowNotificationsNowUseCase,
    private val contactNameResolver: ContactNameResolver,
    private val scheduler: ScheduledSmsScheduler,
) {
    companion object {
        private const val TAG = "ScheduledSmsSender"
        private const val CHANNEL_ID = "scheduled_sms_channel"

        // Offset from SmsForwarder's notification id counter so the two features never collide.
        private var notificationId = 100_000
    }

    suspend fun send(
        context: Context,
        scheduledSmsId: Long,
    ) {
        Log.d(TAG, "send() called — id=$scheduledSmsId")

        val scheduledSms = getScheduledSmsUseCase(scheduledSmsId)
        if (scheduledSms == null) {
            Log.w(TAG, "Skipping: no scheduled sms found for id=$scheduledSmsId")
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Skipping send: SEND_SMS permission not granted")
            scheduleNextIfNeeded(scheduledSms)
            return
        }

        try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            if (smsManager == null) {
                Log.e(TAG, "Failed: SmsManager is null (no default subscription?)")
                return
            }

            val parts = smsManager.divideMessage(scheduledSms.message)
            if (parts.size == 1) {
                smsManager.sendTextMessage(scheduledSms.contactNumber, null, scheduledSms.message, null, null)
            } else {
                Log.d(TAG, "Message split into ${parts.size} parts")
                smsManager.sendMultipartTextMessage(scheduledSms.contactNumber, null, parts, null, null)
            }
            Log.i(TAG, "Scheduled SMS sent successfully to '${scheduledSms.contactNumber}'")

            val contactDisplay = buildContactDisplay(scheduledSms.contactNumber)

            if (getShowNotificationsNowUseCase()) {
                showSentNotification(context, contactDisplay)
            }

            if (getKeepScheduleHistoryNowUseCase()) {
                recordScheduledSmsHistoryUseCase(
                    scheduledSmsId = scheduledSms.id,
                    scheduledSmsName = scheduledSms.name,
                    contactNumber = scheduledSms.contactNumber,
                    messageBody = scheduledSms.message,
                )
                Log.d(TAG, "Scheduled SMS saved to history")
            } else {
                Log.d(TAG, "Keep schedule history disabled, skipping history record")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send scheduled SMS to '${scheduledSms.contactNumber}'", e)
        }

        scheduleNextIfNeeded(scheduledSms)
    }

    private fun scheduleNextIfNeeded(scheduledSms: ScheduledSms) {
        if (scheduledSms.repeatType == RepeatType.NONE) return
        val next =
            RecurrenceCalculator.nextOccurrence(
                anchorEpochMs = scheduledSms.dateEpochMs,
                repeatType = scheduledSms.repeatType,
                afterEpochMs = System.currentTimeMillis(),
                stopAtEpochMs = scheduledSms.stopAtEpochMs,
            )
        if (next != null) {
            scheduler.scheduleNext(scheduledSms.id, next)
            Log.d(TAG, "Scheduled next occurrence for id=${scheduledSms.id} at $next")
        } else {
            Log.d(TAG, "No further occurrences for id=${scheduledSms.id} (stopped or reached stopAt)")
        }
    }

    private fun showSentNotification(
        context: Context,
        contactDisplay: String,
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "POST_NOTIFICATIONS permission not granted, skipping notification")
            return
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Scheduled SMS",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notifications for sent scheduled SMS messages"
            }
        notificationManager.createNotificationChannel(channel)

        val deepLinkIntent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("${Routes.DEEP_LINK_URI}/${Routes.ScheduleHistory}"),
                context,
                MainActivity::class.java,
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                deepLinkIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val contentText = "Scheduled SMS was sent to $contactDisplay"
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle("Scheduled SMS Sent")
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

        notificationManager.notify(notificationId++, notification)
        Log.d(TAG, "Notification shown for scheduled SMS sent to $contactDisplay")
    }

    private suspend fun buildContactDisplay(contactNumber: String): String {
        val name = contactNameResolver.lookupNameByNumber(contactNumber)
        return if (name != null) {
            "$name ($contactNumber)"
        } else {
            contactNumber
        }
    }
}
