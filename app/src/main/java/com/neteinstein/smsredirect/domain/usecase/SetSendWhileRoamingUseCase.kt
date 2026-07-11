package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.analytics.AnalyticsEvents
import com.neteinstein.smsredirect.domain.analytics.AnalyticsTracker
import com.neteinstein.smsredirect.domain.analytics.NoOpAnalyticsTracker
import com.neteinstein.smsredirect.domain.repository.SettingsRepository

class SetSendWhileRoamingUseCase(
    private val repository: SettingsRepository,
    private val analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker,
) {
    suspend operator fun invoke(enabled: Boolean) {
        repository.setSendWhileRoaming(enabled)
        analyticsTracker.logEvent(
            AnalyticsEvents.SETTING_CHANGED,
            mapOf(
                AnalyticsEvents.Param.SETTING_NAME to "send_while_roaming",
                AnalyticsEvents.Param.SETTING_VALUE to enabled,
            ),
        )
    }
}
