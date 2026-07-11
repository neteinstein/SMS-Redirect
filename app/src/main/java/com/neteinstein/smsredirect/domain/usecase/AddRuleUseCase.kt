package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.analytics.AnalyticsEvents
import com.neteinstein.smsredirect.domain.analytics.AnalyticsTracker
import com.neteinstein.smsredirect.domain.analytics.NoOpAnalyticsTracker
import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.repository.RuleRepository

class AddRuleUseCase(
    private val repository: RuleRepository,
    private val analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker,
) {
    suspend operator fun invoke(rule: Rule): Result<Unit> {
        if (!rule.isValid()) {
            return Result.failure(
                IllegalArgumentException("Target number and either sender or phone number are required"),
            )
        }
        repository.insert(rule)
        analyticsTracker.logEvent(AnalyticsEvents.RULE_CREATED)
        return Result.success(Unit)
    }
}
