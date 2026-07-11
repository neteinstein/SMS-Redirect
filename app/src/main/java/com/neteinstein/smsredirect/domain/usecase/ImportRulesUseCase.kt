package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.analytics.AnalyticsEvents
import com.neteinstein.smsredirect.domain.analytics.AnalyticsTracker
import com.neteinstein.smsredirect.domain.analytics.NoOpAnalyticsTracker
import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.repository.RuleRepository
import org.json.JSONArray

class ImportRulesUseCase(
    private val repository: RuleRepository,
    private val analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker,
) {
    suspend operator fun invoke(json: String): Int {
        val jsonArray = JSONArray(json)
        val existingRules = repository.getRulesSnapshot()
        var importedCount = 0

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val rule =
                Rule(
                    name = obj.optString("name", ""),
                    senderText = obj.optString("senderText", ""),
                    phoneNumber = obj.optString("phoneNumber", ""),
                    targetNumber = obj.optString("targetNumber", ""),
                    sortOrder = obj.optInt("sortOrder", 0),
                    category = obj.optString("category", ""),
                )

            val isDuplicate =
                existingRules.any { existing ->
                    existing.name == rule.name &&
                        existing.senderText == rule.senderText &&
                        existing.phoneNumber == rule.phoneNumber &&
                        existing.targetNumber == rule.targetNumber
                }

            if (!isDuplicate && rule.isValid()) {
                repository.insert(rule)
                importedCount++
            }
        }

        analyticsTracker.logEvent(
            AnalyticsEvents.RULES_IMPORTED,
            mapOf(AnalyticsEvents.Param.RULE_COUNT to importedCount),
        )
        return importedCount
    }
}
