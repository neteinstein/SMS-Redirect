package com.neteinstein.smsredirect.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsEntityTest {
    @Test
    fun `default AppSettingsEntity has showNotifications disabled`() {
        val entity = AppSettingsEntity()

        assertFalse(entity.showNotifications)
    }

    @Test
    fun `default AppSettingsEntity matches expected defaults for all fields`() {
        val entity = AppSettingsEntity()

        assertEquals(1, entity.id)
        assertFalse(entity.sendWhileRoaming)
        assertTrue(entity.keepHistory)
        assertEquals("+351", entity.defaultPrefix)
        assertEquals("SMS Redirect:", entity.forwardPrefix)
        assertFalse(entity.showNotifications)
        assertTrue(entity.keepScheduleHistory)
    }
}
