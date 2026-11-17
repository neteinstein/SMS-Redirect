package com.neteinstein.smsredirect.domain.model

/** What a rule does when an incoming SMS matches it. */
enum class RuleType {
    /** Forward the message to another number (the original Redirect feature). */
    REDIRECT,

    /** Do something on this phone: ring, vibrate or change the sound mode. */
    PHONE_ACTION,
}

/**
 * Something an automation rule makes the phone do. Nothing here leaves the device.
 *
 * Persisted (Room `rules` table, the configuration export and WorkManager input) as the three
 * strings [kind], [value] and [label]; [decode] reads them back. Renaming a [kind] or an enum
 * constant breaks rules users already saved, so add new ones instead.
 */
sealed interface PhoneAction {
    val kind: String
    val value: String
    val label: String get() = ""

    /**
     * Ring at the alarm stream's maximum volume, even in silent mode, until stopped or for
     * [RING_DURATION_MILLIS]. A blank [soundUri] means the device's default alarm sound.
     */
    data class Ring(
        val soundUri: String = "",
        val soundName: String = "",
    ) : PhoneAction {
        override val kind: String get() = KIND_RING
        override val value: String get() = soundUri
        override val label: String get() = soundName
    }

    data class Vibrate(
        val pattern: VibrationPattern = VibrationPattern.LONG,
    ) : PhoneAction {
        override val kind: String get() = KIND_VIBRATE
        override val value: String get() = pattern.name
    }

    data class SetSoundMode(
        val mode: SoundMode = SoundMode.RING,
    ) : PhoneAction {
        override val kind: String get() = KIND_SOUND_MODE
        override val value: String get() = mode.name
    }

    companion object {
        const val KIND_RING = "RING"
        const val KIND_VIBRATE = "VIBRATE"
        const val KIND_SOUND_MODE = "SOUND_MODE"

        /** How long a ring lasts when nobody stops it. */
        const val RING_DURATION_MILLIS = 60_000L

        /** How long a ring lasts when its notification, the only Stop button, cannot be shown. */
        const val RING_WITHOUT_STOP_DURATION_MILLIS = 15_000L

        /**
         * An action that could not start within this long of its SMS arriving is dropped:
         * WorkManager may defer work once the expedited quota runs out, and a full-volume ring or
         * a sound-mode change long after the message is worse than none.
         */
        const val MAX_START_DELAY_MILLIS = 2 * 60_000L

        fun isTooLateToRun(
            queuedAtMillis: Long,
            nowMillis: Long,
        ): Boolean = nowMillis - queuedAtMillis > MAX_START_DELAY_MILLIS

        /**
         * Reads back what [kind], [value] and [label] wrote. Unknown kinds give null; an
         * unknown pattern or mode falls back to the default so a rule never silently vanishes.
         */
        fun decode(
            kind: String,
            value: String,
            label: String = "",
        ): PhoneAction? =
            when (kind) {
                KIND_RING -> {
                    Ring(soundUri = value, soundName = label)
                }

                KIND_VIBRATE -> {
                    Vibrate(VibrationPattern.entries.firstOrNull { it.name == value } ?: VibrationPattern.LONG)
                }

                KIND_SOUND_MODE -> {
                    SetSoundMode(SoundMode.entries.firstOrNull { it.name == value } ?: SoundMode.RING)
                }

                else -> {
                    null
                }
            }
    }
}

/**
 * Vibration patterns as alternating off/on durations in milliseconds, starting with an off
 * period (the format of `VibrationEffect.createWaveform`).
 */
enum class VibrationPattern(
    val timings: List<Long>,
) {
    SHORT(listOf(0, 400)),
    LONG(listOf(0, 2_000)),
    DOUBLE(listOf(0, 400, 250, 400)),
    HEARTBEAT(listOf(0, 150, 120, 150, 700, 150, 120, 150, 700, 150, 120, 150)),

    /** · · · − − − · · · (dots 200 ms, dashes 600 ms, 200 ms apart, 600 ms between letters). */
    SOS(listOf(0L) + MORSE_S + 600L + MORSE_O + 600L + MORSE_S),

    /** 800 ms on, 400 ms off, for about ten seconds: hard to miss with the phone in a pocket. */
    ALARM(listOf(0L) + List(8) { listOf(800L, 400L) }.flatten() + 800L),
    ;

    val durationMillis: Long get() = timings.sum()
}

private val MORSE_S = listOf(200L, 200L, 200L, 200L, 200L)
private val MORSE_O = listOf(600L, 200L, 600L, 200L, 600L)

/** The ringer mode a [PhoneAction.SetSoundMode] switches to. */
enum class SoundMode {
    RING,
    VIBRATE,
    SILENT,

    /** Silent when currently ringing, otherwise ring. */
    TOGGLE_RING_SILENT,
}
