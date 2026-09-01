package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.model.LandingScreen
import com.neteinstein.smsredirect.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

class GetLandingScreenUseCase(
    private val repository: SettingsRepository,
) {
    operator fun invoke(): Flow<LandingScreen> = repository.observeLandingScreen()
}
