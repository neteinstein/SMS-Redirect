package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.repository.RuleRepository

class DeleteRuleUseCase(
    private val repository: RuleRepository,
) {
    suspend operator fun invoke(rule: Rule) = repository.delete(rule)
}
