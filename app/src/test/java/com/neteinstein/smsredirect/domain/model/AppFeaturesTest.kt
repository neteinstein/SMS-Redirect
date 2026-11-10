package com.neteinstein.smsredirect.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFeaturesTest {
    @Test
    fun `backup only build offers just the Backup landing screen`() {
        val features = AppFeatures.BACKUP_ONLY

        assertEquals(listOf(LandingScreen.BACKUP), features.availableLandingScreens)
        assertEquals(LandingScreen.BACKUP, features.resolveLandingScreen(LandingScreen.REDIRECT))
        assertEquals(LandingScreen.BACKUP, features.resolveLandingScreen(LandingScreen.SCHEDULE))
        assertFalse(features.sendsSms)
    }

    @Test
    fun `full build keeps the chosen landing screen`() {
        val features = AppFeatures.ALL

        assertEquals(LandingScreen.entries, features.availableLandingScreens)
        assertEquals(LandingScreen.SCHEDULE, features.resolveLandingScreen(LandingScreen.SCHEDULE))
        assertTrue(features.sendsSms)
    }

    @Test
    fun `either messaging feature needs SEND_SMS`() {
        assertTrue(AppFeatures.BACKUP_ONLY.copy(scheduling = true).sendsSms)
        assertEquals(
            listOf(LandingScreen.BACKUP, LandingScreen.REDIRECT),
            AppFeatures.BACKUP_ONLY.copy(forwarding = true).availableLandingScreens,
        )
    }
}
