package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.model.ScheduledSms
import com.neteinstein.smsredirect.domain.repository.ScheduledSmsRepository

class GetScheduledSmsUseCase(
    private val repository: ScheduledSmsRepository,
) {
    suspend operator fun invoke(id: Long): ScheduledSms? = repository.getScheduledSms(id)
}
