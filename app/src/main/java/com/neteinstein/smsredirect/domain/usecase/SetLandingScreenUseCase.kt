package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.model.LandingScreen
import com.neteinstein.smsredirect.domain.repository.SettingsRepository

class SetLandingScreenUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(screen: LandingScreen) = repository.setLandingScreen(screen)
}
