package com.neteinstein.smsredirect.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.neteinstein.smsredirect.domain.repository.KeepAliveReminderRepository

/** One device-local flag; SharedPreferences, not Room (see the Room trap in CLAUDE.md). */
class SharedPreferencesKeepAliveReminderRepository(
    private val prefs: SharedPreferences,
) : KeepAliveReminderRepository {
    override suspend fun isUnusedAppInfoShown(): Boolean = prefs.getBoolean(KEY_INFO_SHOWN, false)

    override suspend fun markUnusedAppInfoShown() {
        prefs.edit { putBoolean(KEY_INFO_SHOWN, true) }
    }

    companion object {
        const val PREFS_NAME = "keep_alive_reminder"
        private const val KEY_INFO_SHOWN = "unused_app_info_shown"
    }
}
