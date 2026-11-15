package com.neteinstein.smsredirect.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.ui.graphics.vector.ImageVector
import com.neteinstein.smsredirect.R
import com.neteinstein.smsredirect.domain.model.AppFeatures
import com.neteinstein.smsredirect.domain.model.LandingScreen

/**
 * The top-level destinations in the home screen's bottom navigation bar, in display order.
 *
 * MESSAGES is first: SMS Toolbox is a messaging app (Play's "Default SMS handler" use case), and
 * it is the tab the app opens on by default. `rememberSaveable` persists an enum by name, so
 * reordering these is safe for existing saved state.
 */
enum class MainTab(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    MESSAGES(R.string.main_tab_messages, Icons.AutoMirrored.Filled.Chat),
    REDIRECT(R.string.main_tab_redirect, Icons.AutoMirrored.Filled.Forward),
    SCHEDULE(R.string.main_tab_schedule, Icons.Default.Schedule),
    BACKUP(R.string.main_tab_backup, Icons.Default.Backup),
    RESTORE(R.string.main_tab_restore, Icons.Default.Restore),
}

/** The tabs [this] build ships, in display order. */
fun AppFeatures.availableTabs(): List<MainTab> = availableLandingScreens.map { it.toMainTab() }

fun LandingScreen.toMainTab(): MainTab =
    when (this) {
        LandingScreen.MESSAGES -> MainTab.MESSAGES
        LandingScreen.REDIRECT -> MainTab.REDIRECT
        LandingScreen.SCHEDULE -> MainTab.SCHEDULE
        LandingScreen.BACKUP -> MainTab.BACKUP
        LandingScreen.RESTORE -> MainTab.RESTORE
    }
