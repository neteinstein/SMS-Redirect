package com.neteinstein.smsredirect.domain.repository

import com.neteinstein.smsredirect.domain.model.GmailCredentials
import com.neteinstein.smsredirect.domain.model.SmsBackupSettings
import kotlinx.coroutines.flow.Flow

/**
 * Backup configuration and Gmail credentials.
 *
 * Exposes one aggregate [observeSettings] rather than the observe/get/set-per-field style of
 * [SettingsRepository]; that interface is already 18 flat methods and this feature would have
 * added another two dozen.
 */
interface SmsBackupSettingsRepository {
    fun observeSettings(): Flow<SmsBackupSettings>

    suspend fun getSettings(): SmsBackupSettings

    /**
     * Returns null when no address is stored, or when the stored password cannot be decrypted.
     *
     * The second case is real: the Room database is cloud-backed-up and device-transferred but
     * the keystore key is not, so on a new device the ciphertext is undecryptable. Callers must
     * present that as "re-enter your password", not as an authentication failure.
     */
    suspend fun getCredentials(): GmailCredentials?

    suspend fun setCredentials(
        address: String,
        appPassword: String,
    )

    suspend fun clearCredentials()

    suspend fun setSubjectTemplate(template: String)

    suspend fun setGmailLabel(label: String)

    suspend fun setBackupPreviousEnabled(enabled: Boolean)

    suspend fun setBackupEnabled(
        enabled: Boolean,
        startedAtMillis: Long,
    )
}
