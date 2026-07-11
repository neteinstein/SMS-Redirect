package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.repository.ScheduledSmsRepository
import kotlinx.coroutines.flow.Flow

class GetScheduledSmsCategoriesUseCase(
    private val repository: ScheduledSmsRepository,
) {
    operator fun invoke(): Flow<List<String>> = repository.getCategories()
}
