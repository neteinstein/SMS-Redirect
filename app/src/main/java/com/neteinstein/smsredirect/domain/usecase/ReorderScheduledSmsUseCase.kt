package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.model.ScheduledSms
import com.neteinstein.smsredirect.domain.repository.ScheduledSmsRepository

class ReorderScheduledSmsUseCase(
    private val repository: ScheduledSmsRepository,
) {
    suspend operator fun invoke(scheduledSmsList: List<ScheduledSms>) {
        val reordered = scheduledSmsList.mapIndexed { index, item -> item.copy(sortOrder = index) }
        repository.updateAll(reordered)
    }
}
