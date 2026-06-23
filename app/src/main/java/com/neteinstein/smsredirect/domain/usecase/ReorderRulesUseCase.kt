package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.repository.RuleRepository

class ReorderRulesUseCase(
    private val repository: RuleRepository,
) {
    suspend operator fun invoke(rules: List<Rule>) {
        val reordered = rules.mapIndexed { index, rule -> rule.copy(sortOrder = index) }
        repository.updateAll(reordered)
    }
}
