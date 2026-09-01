package com.neteinstein.smsredirect.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.ui.graphics.vector.ImageVector
import com.neteinstein.smsredirect.R

/**
 * The top-level destinations in the home screen's bottom navigation bar.
 *
 * BACKUP is first because it is the leftmost tab and the one the app opens on. `rememberSaveable`
 * persists an enum by name, so reordering these is safe for existing saved state.
 */
enum class MainTab(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    BACKUP(R.string.main_tab_backup, Icons.Default.Backup),
    REDIRECT(R.string.main_tab_redirect, Icons.AutoMirrored.Filled.Forward),
    SCHEDULE(R.string.main_tab_schedule, Icons.Default.Schedule),
}
