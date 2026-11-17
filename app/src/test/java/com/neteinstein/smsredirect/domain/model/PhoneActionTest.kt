package com.neteinstein.smsredirect.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneActionTest {
    @Test
    fun `every action survives an encode and decode round trip`() {
        val actions =
            listOf(
                PhoneAction.Ring(),
                PhoneAction.Ring(soundUri = "content://media/audio/42", soundName = "Song"),
            ) + VibrationPattern.entries.map { PhoneAction.Vibrate(it) } +
                SoundMode.entries.map { PhoneAction.SetSoundMode(it) }

        actions.forEach { action ->
            assertEquals(action, PhoneAction.decode(action.kind, action.value, action.label))
        }
    }

    @Test
    fun `unknown kind decodes to null`() {
        assertNull(PhoneAction.decode("FLASHLIGHT", "", ""))
        assertNull(PhoneAction.decode("", "", ""))
    }

    @Test
    fun `unknown pattern or mode falls back to the default instead of dropping the rule`() {
        assertEquals(PhoneAction.Vibrate(VibrationPattern.LONG), PhoneAction.decode(PhoneAction.KIND_VIBRATE, "WOBBLE"))
        assertEquals(PhoneAction.SetSoundMode(SoundMode.RING), PhoneAction.decode(PhoneAction.KIND_SOUND_MODE, "LOUD"))
    }

    @Test
    fun `persisted names are stable`() {
        // Saved in Room, exports and WorkManager input: renaming any of these breaks saved rules.
        assertEquals("RING", PhoneAction.KIND_RING)
        assertEquals("VIBRATE", PhoneAction.KIND_VIBRATE)
        assertEquals("SOUND_MODE", PhoneAction.KIND_SOUND_MODE)
        assertEquals(
            listOf("SHORT", "LONG", "DOUBLE", "HEARTBEAT", "SOS", "ALARM"),
            VibrationPattern.entries.map { it.name },
        )
        assertEquals(listOf("RING", "VIBRATE", "SILENT", "TOGGLE_RING_SILENT"), SoundMode.entries.map { it.name })
        assertEquals(listOf("REDIRECT", "PHONE_ACTION"), RuleType.entries.map { it.name })
    }

    @Test
    fun `vibration patterns are valid waveforms`() {
        VibrationPattern.entries.forEach { pattern ->
            assertEquals("$pattern starts with an off period", 0L, pattern.timings.first())
            assertTrue("$pattern vibrates", pattern.timings.size >= 2 && pattern.durationMillis > 0)
            assertTrue("$pattern has no negative timing", pattern.timings.all { it >= 0 })
        }
        assertEquals(10_400L, VibrationPattern.ALARM.durationMillis)
        assertEquals(
            listOf<Long>(0, 200, 200, 200, 200, 200, 600, 600, 200, 600, 200, 600, 600, 200, 200, 200, 200, 200),
            VibrationPattern.SOS.timings,
        )
    }

    @Test
    fun `an action that starts more than two minutes late is too late to run`() {
        val queued = 1_000_000L

        assertFalse(PhoneAction.isTooLateToRun(queued, queued))
        assertFalse(PhoneAction.isTooLateToRun(queued, queued + 120_000L))
        assertTrue(PhoneAction.isTooLateToRun(queued, queued + 120_001L))
        // Work queued without a timestamp never runs.
        assertTrue(PhoneAction.isTooLateToRun(0L, queued))
    }
}
