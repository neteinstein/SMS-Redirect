package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.analytics.AnalyticsEvents
import com.neteinstein.smsredirect.domain.analytics.AnalyticsTracker
import com.neteinstein.smsredirect.domain.analytics.NoOpAnalyticsTracker
import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.repository.RuleRepository

class ReorderRulesUseCase(
    private val repository: RuleRepository,
    private val analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker,
) {
    suspend operator fun invoke(rules: List<Rule>) {
        val reordered = rules.mapIndexed { index, rule -> rule.copy(sortOrder = index) }
        repository.updateAll(reordered)
        analyticsTracker.logEvent(
            AnalyticsEvents.RULES_REORDERED,
            mapOf(AnalyticsEvents.Param.RULE_COUNT to reordered.size),
        )
    }
}
