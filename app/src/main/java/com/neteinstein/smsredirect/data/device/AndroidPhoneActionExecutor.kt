package com.neteinstein.smsredirect.data.device

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import com.neteinstein.smsredirect.PhoneActionNotifications
import com.neteinstein.smsredirect.domain.device.PhoneActionExecutor
import com.neteinstein.smsredirect.domain.device.PhoneActionFailure
import com.neteinstein.smsredirect.domain.device.PhoneActionOutcome
import com.neteinstein.smsredirect.domain.device.SoundModeAccessChecker
import com.neteinstein.smsredirect.domain.model.PhoneAction
import com.neteinstein.smsredirect.domain.model.SoundMode
import com.neteinstein.smsredirect.domain.model.VibrationPattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The single ring the process may be playing. A new ring, or [stop] (the notification's Stop
 * button), ends the one before it. Holds the user's alarm volume from before the first of a run
 * of overlapping rings, so the last one to finish can put it back.
 */
object ActiveRing {
    private val current = MutableStateFlow<Long?>(null)
    private var counter = 0L
    private var savedVolume: Int? = null

    @Synchronized
    internal fun begin(currentVolume: () -> Int): Long {
        if (savedVolume == null) savedVolume = currentVolume()
        counter++
        current.value = counter
        return counter
    }

    fun stop() {
        current.value = null
    }

    internal suspend fun awaitEnd(token: Long) {
        current.first { it != token }
    }

    /**
     * Ends ring [token]. Returns the volume to restore, or null when a newer ring is playing
     * and now owns the volume and the notification.
     */
    @Synchronized
    internal fun end(token: Long): Int? {
        val playing = current.value
        if (playing != null && playing != token) return null
        current.value = null
        return savedVolume.also { savedVolume = null }
    }
}

class AndroidSoundModeAccessChecker(
    private val context: Context,
) : SoundModeAccessChecker {
    override fun canChangeSoundMode(): Boolean =
        context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true
}

class AndroidPhoneActionExecutor(
    private val context: Context,
    private val soundModeAccessChecker: SoundModeAccessChecker = AndroidSoundModeAccessChecker(context),
) : PhoneActionExecutor {
    private companion object {
        const val TAG = "PhoneActionExecutor"
    }

    override suspend fun execute(
        action: PhoneAction,
        ruleName: String,
    ): PhoneActionOutcome =
        when (action) {
            is PhoneAction.Ring -> ring(action, ruleName)
            is PhoneAction.Vibrate -> vibrate(action.pattern)
            is PhoneAction.SetSoundMode -> setSoundMode(action.mode, ruleName)
        }

    /**
     * Plays on the alarm stream at full volume: that stream ignores the ringer mode, so the
     * phone rings even when silent or on vibrate. The user's alarm volume is put back after.
     * No audio focus is requested; Android 15 refuses it to background apps, and an alarm
     * should not wait for other audio anyway.
     */
    private suspend fun ring(
        action: PhoneAction.Ring,
        ruleName: String,
    ): PhoneActionOutcome {
        val audioManager =
            context.getSystemService(AudioManager::class.java)
                ?: return PhoneActionOutcome.Failed(PhoneActionFailure.UNAVAILABLE)
        val player =
            withContext(Dispatchers.IO) { preparePlayer(action.soundUri) }
                ?: return PhoneActionOutcome.Failed(PhoneActionFailure.NO_PLAYABLE_SOUND)

        val token = ActiveRing.begin { audioManager.getStreamVolume(AudioManager.STREAM_ALARM) }
        setAlarmVolume(audioManager, audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM))
        try {
            PhoneActionNotifications.showRinging(context, ruleName)
            player.start()
            withTimeoutOrNull(PhoneAction.RING_DURATION_MILLIS) { ActiveRing.awaitEnd(token) }
        } finally {
            withContext(NonCancellable) {
                runCatching { player.stop() }
                player.release()
                // Null when a newer ring has taken over the volume and the notification.
                ActiveRing.end(token)?.let { previousVolume ->
                    setAlarmVolume(audioManager, previousVolume)
                    PhoneActionNotifications.cancelRinging(context)
                }
            }
        }
        return PhoneActionOutcome.Done
    }

    /** The chosen sound, falling back to the default alarm or ringtone if it is gone. */
    private fun preparePlayer(soundUri: String): MediaPlayer? {
        val candidates =
            listOfNotNull(
                soundUri.takeIf { it.isNotBlank() }?.toUri(),
                RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM),
                RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE),
                Settings.System.DEFAULT_ALARM_ALERT_URI,
            )
        for (uri in candidates) {
            preparePlayer(uri)?.let { return it }
        }
        return null
    }

    private fun preparePlayer(uri: Uri): MediaPlayer? {
        val player = MediaPlayer()
        return try {
            player.setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            player.setDataSource(context, uri)
            player.isLooping = true
            player.prepare()
            player
        } catch (e: Exception) {
            Log.w(TAG, "Sound not playable, trying the next one", e)
            player.release()
            null
        }
    }

    private fun setAlarmVolume(
        audioManager: AudioManager,
        volume: Int,
    ) {
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, volume, 0)
        } catch (e: SecurityException) {
            // Some devices refuse while Do Not Disturb is on; ring at the current volume.
            Log.w(TAG, "Could not change the alarm volume", e)
        }
    }

    /** Alarm usage, because Android drops other vibrations from apps in the background. */
    private suspend fun vibrate(pattern: VibrationPattern): PhoneActionOutcome {
        val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        if (vibrator == null || !vibrator.hasVibrator()) {
            return PhoneActionOutcome.Failed(PhoneActionFailure.NO_VIBRATOR)
        }
        vibrator.vibrate(
            VibrationEffect.createWaveform(pattern.timings.toLongArray(), -1),
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
        )
        // Keep the worker, and with it the process, alive until the pattern has played.
        delay(pattern.durationMillis)
        return PhoneActionOutcome.Done
    }

    /**
     * Since Android 7 any switch that touches silent toggles Do Not Disturb, which needs the
     * notification policy access the user grants in settings. Ring <-> vibrate does not.
     */
    private fun setSoundMode(
        mode: SoundMode,
        ruleName: String,
    ): PhoneActionOutcome {
        val audioManager =
            context.getSystemService(AudioManager::class.java)
                ?: return PhoneActionOutcome.Failed(PhoneActionFailure.UNAVAILABLE)
        val current = audioManager.ringerMode
        val target =
            when (mode) {
                SoundMode.RING -> {
                    AudioManager.RINGER_MODE_NORMAL
                }

                SoundMode.VIBRATE -> {
                    AudioManager.RINGER_MODE_VIBRATE
                }

                SoundMode.SILENT -> {
                    AudioManager.RINGER_MODE_SILENT
                }

                SoundMode.TOGGLE_RING_SILENT -> {
                    if (current == AudioManager.RINGER_MODE_NORMAL) {
                        AudioManager.RINGER_MODE_SILENT
                    } else {
                        AudioManager.RINGER_MODE_NORMAL
                    }
                }
            }
        if (target == current) return PhoneActionOutcome.Done

        val touchesSilent = target == AudioManager.RINGER_MODE_SILENT || current == AudioManager.RINGER_MODE_SILENT
        if (touchesSilent && !soundModeAccessChecker.canChangeSoundMode()) {
            PhoneActionNotifications.showSoundModeAccessDenied(context, ruleName)
            return PhoneActionOutcome.Failed(PhoneActionFailure.SOUND_MODE_ACCESS_DENIED)
        }
        return try {
            audioManager.ringerMode = target
            PhoneActionOutcome.Done
        } catch (e: SecurityException) {
            Log.w(TAG, "Ringer mode change refused", e)
            PhoneActionNotifications.showSoundModeAccessDenied(context, ruleName)
            PhoneActionOutcome.Failed(PhoneActionFailure.SOUND_MODE_ACCESS_DENIED)
        }
    }
}
