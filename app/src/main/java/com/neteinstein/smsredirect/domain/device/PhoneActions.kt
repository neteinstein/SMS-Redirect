package com.neteinstein.smsredirect.domain.device

import com.neteinstein.smsredirect.domain.model.PhoneAction

/** Queues a [PhoneAction] to run in the background, so a ring outlives the screen or receiver. */
interface PhoneActionScheduler {
    fun schedule(
        action: PhoneAction,
        ruleName: String,
    )
}

/** The result of running one [PhoneAction]. */
sealed interface PhoneActionOutcome {
    data object Done : PhoneActionOutcome

    data class Failed(
        val reason: PhoneActionFailure,
    ) : PhoneActionOutcome
}

enum class PhoneActionFailure {
    /** Switching to or from silent needs the user to grant "Do Not Disturb" access. */
    SOUND_MODE_ACCESS_DENIED,
    NO_VIBRATOR,
    NO_PLAYABLE_SOUND,
    UNAVAILABLE,
}

/** Performs a [PhoneAction] on the device: audio, vibration and ringer mode. */
interface PhoneActionExecutor {
    /** Runs [action]; a ring suspends until it is stopped or times out. */
    suspend fun execute(
        action: PhoneAction,
        ruleName: String,
    ): PhoneActionOutcome
}

/**
 * Whether the app may switch the ringer to or from silent, which Android ties to the
 * "Do Not Disturb" (notification policy) access the user grants in system settings.
 */
interface SoundModeAccessChecker {
    fun canChangeSoundMode(): Boolean
}
