package com.neteinstein.smsredirect.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.neteinstein.smsredirect.domain.model.MessagingSettings
import com.neteinstein.smsredirect.domain.repository.MessagingSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Receipt preferences live in SharedPreferences rather than Room: three device-local switches,
 * not worth a schema migration (see the Room trap in CLAUDE.md).
 */
class SharedPreferencesMessagingSettingsRepository(
    private val prefs: SharedPreferences,
) : MessagingSettingsRepository {
    private val state = MutableStateFlow(read())

    override fun observe(): Flow<MessagingSettings> = state.asStateFlow()

    override suspend fun get(): MessagingSettings = state.value

    override suspend fun set(settings: MessagingSettings) {
        prefs.edit {
            putBoolean(KEY_SMS_DELIVERY, settings.smsDeliveryReports)
            putBoolean(KEY_MMS_DELIVERY, settings.mmsDeliveryReports)
            putBoolean(KEY_MMS_READ, settings.mmsReadReports)
        }
        state.value = settings
    }

    private fun read(): MessagingSettings =
        MessagingSettings(
            smsDeliveryReports = prefs.getBoolean(KEY_SMS_DELIVERY, false),
            mmsDeliveryReports = prefs.getBoolean(KEY_MMS_DELIVERY, false),
            mmsReadReports = prefs.getBoolean(KEY_MMS_READ, false),
        )

    companion object {
        const val PREFS_NAME = "messaging_settings"
        private const val KEY_SMS_DELIVERY = "sms_delivery_reports"
        private const val KEY_MMS_DELIVERY = "mms_delivery_reports"
        private const val KEY_MMS_READ = "mms_read_reports"
    }
}
