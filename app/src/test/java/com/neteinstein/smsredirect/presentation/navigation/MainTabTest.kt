package com.neteinstein.smsredirect.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class MainTabTest {
    @Test
    fun `play build without SMS sending shows only Backup`() {
        assertEquals(listOf(MainTab.BACKUP), MainTab.available(smsSendingEnabled = false))
    }

    @Test
    fun `full build shows every tab with Backup first`() {
        assertEquals(
            listOf(MainTab.BACKUP, MainTab.REDIRECT, MainTab.SCHEDULE),
            MainTab.available(smsSendingEnabled = true),
        )
    }
}
