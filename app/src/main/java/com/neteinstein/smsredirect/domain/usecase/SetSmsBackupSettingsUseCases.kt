package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.analytics.AnalyticsEvents
import com.neteinstein.smsredirect.domain.analytics.AnalyticsTracker
import com.neteinstein.smsredirect.domain.analytics.NoOpAnalyticsTracker
import com.neteinstein.smsredirect.domain.repository.SmsBackupSettingsRepository

class SetBackupSubjectTemplateUseCase(
    private val repository: SmsBackupSettingsRepository,
) {
    suspend operator fun invoke(template: String) = repository.setSubjectTemplate(template.trim())
}

class SetBackupGmailLabelUseCase(
    private val repository: SmsBackupSettingsRepository,
) {
    /**
     * Gmail nests labels with `/`, so the separator is allowed, but a leading, trailing or empty
     * segment produces a folder name the server rejects. Normalize rather than fail.
     */
    suspend operator fun invoke(label: String) {
        val normalized =
            label
                .split('/')
                .map { segment -> segment.trim() }
                .filter { segment -> segment.isNotEmpty() }
                .joinToString("/")
        repository.setGmailLabel(normalized)
    }
}

class SetBackupPreviousEnabledUseCase(
    private val repository: SmsBackupSettingsRepository,
    private val analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker,
) {
    suspend operator fun invoke(enabled: Boolean) {
        repository.setBackupPreviousEnabled(enabled)
        analyticsTracker.logEvent(
            AnalyticsEvents.SETTING_CHANGED,
            mapOf(
                AnalyticsEvents.Param.SETTING_NAME to "backup_previous",
                AnalyticsEvents.Param.SETTING_VALUE to enabled,
            ),
        )
    }
}

class SetBackupEnabledUseCase(
    private val repository: SmsBackupSettingsRepository,
    private val analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /**
     * Records a watermark when backup is switched on. With "back up all previous" off, only
     * messages at or after that moment are eligible; with it on the watermark is cleared so the
     * entire inbox becomes eligible.
     */
    suspend operator fun invoke(
        enabled: Boolean,
        backupPrevious: Boolean,
    ) {
        val watermark = if (enabled && !backupPrevious) now() else 0L
        repository.setBackupEnabled(enabled, watermark)
        analyticsTracker.logEvent(
            AnalyticsEvents.SETTING_CHANGED,
            mapOf(
                AnalyticsEvents.Param.SETTING_NAME to "backup_enabled",
                AnalyticsEvents.Param.SETTING_VALUE to enabled,
            ),
        )
    }
}

class ClearGmailCredentialsUseCase(
    private val repository: SmsBackupSettingsRepository,
) {
    suspend operator fun invoke() = repository.clearCredentials()
}
