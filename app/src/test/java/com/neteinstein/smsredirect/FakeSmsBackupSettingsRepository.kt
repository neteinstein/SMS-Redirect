package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.model.GmailCredentials
import com.neteinstein.smsredirect.domain.model.SmsBackupSettings
import com.neteinstein.smsredirect.domain.repository.SmsBackupSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSmsBackupSettingsRepository(
    initial: SmsBackupSettings = SmsBackupSettings(),
) : SmsBackupSettingsRepository {
    val state = MutableStateFlow(initial)

    /** Stored separately, mirroring the real repo where the UI never sees the plaintext. */
    var storedPassword: String? = null

    /** Simulates a database restored onto a device whose keystore lacks the key. */
    var credentialsUndecryptable: Boolean = false

    override fun observeSettings(): Flow<SmsBackupSettings> = state

    override suspend fun getSettings(): SmsBackupSettings = state.value

    override suspend fun getCredentials(): GmailCredentials? {
        if (credentialsUndecryptable) return null
        val password = storedPassword ?: return null
        val address = state.value.gmailAddress.takeIf { it.isNotBlank() } ?: return null
        return GmailCredentials(address, password)
    }

    override suspend fun setCredentials(
        address: String,
        appPassword: String,
    ) {
        storedPassword = appPassword
        state.value = state.value.copy(gmailAddress = address, hasAppPassword = true)
    }

    override suspend fun clearCredentials() {
        storedPassword = null
        state.value = state.value.copy(gmailAddress = "", hasAppPassword = false, backupEnabled = false)
    }

    override suspend fun setSubjectTemplate(template: String) {
        state.value = state.value.copy(subjectTemplate = template)
    }

    override suspend fun setGmailLabel(label: String) {
        state.value = state.value.copy(gmailLabel = label)
    }

    override suspend fun setBackupPreviousEnabled(enabled: Boolean) {
        state.value = state.value.copy(backupPreviousEnabled = enabled)
    }

    override suspend fun setBackupEnabled(
        enabled: Boolean,
        startedAtMillis: Long,
    ) {
        state.value = state.value.copy(backupEnabled = enabled, backupStartedAtMillis = startedAtMillis)
    }
}
