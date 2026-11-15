package com.neteinstein.smsredirect.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFeaturesTest {
    @Test
    fun `every build opens on Messages and ships all five tabs in order`() {
        val expected =
            listOf(LandingScreen.MESSAGES, LandingScreen.REDIRECT, LandingScreen.SCHEDULE, LandingScreen.BACKUP, LandingScreen.RESTORE)

        assertEquals(expected, AppFeatures.ALL.availableLandingScreens)
        assertEquals(expected, AppFeatures.PLAY.availableLandingScreens)
        assertEquals(LandingScreen.MESSAGES, LandingScreen.entries.first())
    }

    @Test
    fun `the play build never updates itself and never asks for SMS permissions without the role`() {
        assertFalse(AppFeatures.PLAY.selfUpdate)
        assertFalse(AppFeatures.PLAY.smsPermissionsWithoutDefaultRole)
        assertTrue(AppFeatures.ALL.smsPermissionsWithoutDefaultRole)
    }

    @Test
    fun `a build without a tool falls back to Messages`() {
        val features = AppFeatures.PLAY.copy(forwarding = false, scheduling = false)

        assertEquals(LandingScreen.MESSAGES, features.resolveLandingScreen(LandingScreen.REDIRECT))
        assertEquals(LandingScreen.MESSAGES, features.resolveLandingScreen(LandingScreen.SCHEDULE))
        assertEquals(LandingScreen.RESTORE, features.resolveLandingScreen(LandingScreen.RESTORE))
        assertFalse(features.sendsSms)
    }

    @Test
    fun `either tool shows the messaging settings`() {
        assertTrue(AppFeatures.PLAY.copy(forwarding = false).sendsSms)
        assertTrue(AppFeatures.PLAY.copy(scheduling = false).sendsSms)
    }
}
