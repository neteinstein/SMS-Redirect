package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.repository.SettingsRepository

class GetShowNotificationsNowUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(): Boolean = repository.getShowNotifications()
}
