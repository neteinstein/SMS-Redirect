package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.repository.RuleRepository

class GetRuleUseCase(
    private val repository: RuleRepository,
) {
    suspend operator fun invoke(id: Long): Rule? = repository.getRule(id)
}
