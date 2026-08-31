package com.neteinstein.smsredirect.data.repository

import com.neteinstein.smsredirect.data.local.BackupSettingsDao
import com.neteinstein.smsredirect.data.local.BackupSettingsEntity
import com.neteinstein.smsredirect.domain.model.GmailCredentials
import com.neteinstein.smsredirect.domain.model.SmsBackupSettings
import com.neteinstein.smsredirect.domain.repository.SmsBackupSettingsRepository
import com.neteinstein.smsredirect.domain.security.SecretCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SmsBackupSettingsRepositoryImpl(
    private val dao: BackupSettingsDao,
    private val secretCipher: SecretCipher,
) : SmsBackupSettingsRepository {
    override fun observeSettings(): Flow<SmsBackupSettings> = dao.observeSettings().map { it.toDomain() }

    override suspend fun getSettings(): SmsBackupSettings = dao.getSettings().toDomain()

    override suspend fun getCredentials(): GmailCredentials? {
        val entity = dao.getSettings() ?: return null
        if (entity.gmailAddress.isBlank() || entity.gmailAppPasswordCipher.isBlank()) return null
        // Null here means the keystore key is gone -- a restored backup on a new device. Report
        // "no credentials" so the UI asks for the password again instead of blaming Gmail.
        val password = secretCipher.decrypt(entity.gmailAppPasswordCipher) ?: return null
        return GmailCredentials(entity.gmailAddress, password)
    }

    override suspend fun setCredentials(
        address: String,
        appPassword: String,
    ) {
        val current = dao.getSettings() ?: BackupSettingsEntity()
        dao.upsert(
            current.copy(
                gmailAddress = address,
                gmailAppPasswordCipher = secretCipher.encrypt(appPassword),
            ),
        )
    }

    override suspend fun clearCredentials() {
        val current = dao.getSettings() ?: BackupSettingsEntity()
        dao.upsert(
            current.copy(
                gmailAddress = "",
                gmailAppPasswordCipher = "",
                backupEnabled = false,
            ),
        )
    }

    override suspend fun setSubjectTemplate(template: String) {
        val current = dao.getSettings() ?: BackupSettingsEntity()
        dao.upsert(current.copy(subjectTemplate = template))
    }

    override suspend fun setGmailLabel(label: String) {
        val current = dao.getSettings() ?: BackupSettingsEntity()
        dao.upsert(current.copy(gmailLabel = label))
    }

    override suspend fun setBackupPreviousEnabled(enabled: Boolean) {
        val current = dao.getSettings() ?: BackupSettingsEntity()
        dao.upsert(current.copy(backupPreviousEnabled = enabled))
    }

    override suspend fun setBackupEnabled(
        enabled: Boolean,
        startedAtMillis: Long,
    ) {
        val current = dao.getSettings() ?: BackupSettingsEntity()
        dao.upsert(current.copy(backupEnabled = enabled, backupStartedAtMillis = startedAtMillis))
    }

    /**
     * Blank columns mean "use the domain default". The defaults live here rather than in the SQL
     * schema because the subject template contains `$` and parentheses, and a default expression
     * like that is exactly what produces an unexplainable Room schema-identity mismatch between
     * the entity annotation and the migration DDL.
     */
    private fun BackupSettingsEntity?.toDomain(): SmsBackupSettings =
        SmsBackupSettings(
            gmailAddress = this?.gmailAddress.orEmpty(),
            hasAppPassword = !this?.gmailAppPasswordCipher.isNullOrBlank(),
            subjectTemplate =
                this?.subjectTemplate?.takeIf { it.isNotBlank() }
                    ?: SmsBackupSettings.DEFAULT_SUBJECT_TEMPLATE,
            gmailLabel = this?.gmailLabel?.takeIf { it.isNotBlank() } ?: SmsBackupSettings.DEFAULT_LABEL,
            backupPreviousEnabled = this?.backupPreviousEnabled ?: false,
            backupEnabled = this?.backupEnabled ?: false,
            backupStartedAtMillis = this?.backupStartedAtMillis ?: 0L,
        )
}
