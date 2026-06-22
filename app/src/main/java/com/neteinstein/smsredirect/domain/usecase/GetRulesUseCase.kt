package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.repository.RuleRepository
import kotlinx.coroutines.flow.Flow

class GetRulesUseCase(private val repository: RuleRepository) {
    operator fun invoke(): Flow<List<Rule>> = repository.getRules()
}
