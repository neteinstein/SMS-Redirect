package com.neteinstein.smsredirect.data.update

import android.content.SharedPreferences
import androidx.core.content.edit
import com.neteinstein.smsredirect.domain.model.AppRelease
import com.neteinstein.smsredirect.domain.update.AppUpdateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Update-check state lives in SharedPreferences rather than Room: it is a cache of public
 * release metadata, safe to lose, and not worth a schema migration.
 */
class SharedPreferencesAppUpdateRepository(
    private val prefs: SharedPreferences,
) : AppUpdateRepository {
    private val latest = MutableStateFlow(readLatest())

    override val latestRelease: Flow<AppRelease?> = latest.asStateFlow()

    override suspend fun lastCheckEpochMs(): Long = prefs.getLong(KEY_LAST_CHECK, 0L)

    override suspend fun saveCheck(
        checkedAtEpochMs: Long,
        latest: AppRelease,
    ) {
        prefs.edit {
            putLong(KEY_LAST_CHECK, checkedAtEpochMs)
            putString(KEY_VERSION, latest.versionName)
            putString(KEY_RELEASE_URL, latest.releaseUrl)
            putString(KEY_APK_URL, latest.apkUrl)
            putString(KEY_NOTES, latest.notes)
        }
        this.latest.value = latest
    }

    private fun readLatest(): AppRelease? {
        val version = prefs.getString(KEY_VERSION, null) ?: return null
        return AppRelease(
            versionName = version,
            releaseUrl = prefs.getString(KEY_RELEASE_URL, null).orEmpty(),
            apkUrl = prefs.getString(KEY_APK_URL, null),
            notes = prefs.getString(KEY_NOTES, null).orEmpty(),
        )
    }

    companion object {
        const val PREFS_NAME = "app_update"
        private const val KEY_LAST_CHECK = "last_check_epoch_ms"
        private const val KEY_VERSION = "latest_version"
        private const val KEY_RELEASE_URL = "latest_release_url"
        private const val KEY_APK_URL = "latest_apk_url"
        private const val KEY_NOTES = "latest_notes"
    }
}
