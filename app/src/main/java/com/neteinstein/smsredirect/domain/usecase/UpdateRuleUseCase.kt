package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.repository.RuleRepository

class UpdateRuleUseCase(private val repository: RuleRepository) {
    suspend operator fun invoke(rule: Rule): Result<Unit> {
        if (!rule.isValid()) {
            return Result.failure(
                IllegalArgumentException("Target number and either sender or phone number are required")
            )
        }
        repository.update(rule)
        return Result.success(Unit)
    }
}
