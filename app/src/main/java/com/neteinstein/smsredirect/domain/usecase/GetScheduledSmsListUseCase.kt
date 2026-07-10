package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.model.ScheduledSms
import com.neteinstein.smsredirect.domain.repository.ScheduledSmsRepository
import kotlinx.coroutines.flow.Flow

class GetScheduledSmsListUseCase(
    private val repository: ScheduledSmsRepository,
) {
    operator fun invoke(): Flow<List<ScheduledSms>> = repository.getScheduledSmsList()
}
