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
    fun `the two builds differ only in the self-update and SMS access without the role`() {
        assertFalse(AppFeatures.PLAY.selfUpdate)
        assertTrue(AppFeatures.ALL.selfUpdate)
        assertEquals(AppFeatures.ALL, AppFeatures.PLAY.copy(selfUpdate = true, smsPermissionsWithoutRole = true))
    }

    @Test
    fun `the Play build never asks for SMS permissions without offering the role`() {
        assertFalse(AppFeatures.PLAY.smsPermissionsWithoutRole)
        assertTrue(AppFeatures.ALL.smsPermissionsWithoutRole)
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
