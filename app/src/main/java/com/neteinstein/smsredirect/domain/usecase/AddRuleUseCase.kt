package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.repository.RuleRepository

class AddRuleUseCase(private val repository: RuleRepository) {
    suspend operator fun invoke(rule: Rule): Result<Unit> {
        if (!rule.isValid()) {
            return Result.failure(IllegalArgumentException("Either sender or phone number is required"))
        }
        repository.insert(rule)
        return Result.success(Unit)
    }
}
