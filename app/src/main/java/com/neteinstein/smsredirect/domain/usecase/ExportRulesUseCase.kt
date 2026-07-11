package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.analytics.AnalyticsEvents
import com.neteinstein.smsredirect.domain.analytics.AnalyticsTracker
import com.neteinstein.smsredirect.domain.analytics.NoOpAnalyticsTracker
import com.neteinstein.smsredirect.domain.repository.RuleRepository
import org.json.JSONArray
import org.json.JSONObject

class ExportRulesUseCase(
    private val repository: RuleRepository,
    private val analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker,
) {
    suspend operator fun invoke(): String {
        val rules = repository.getRulesSnapshot()
        val jsonArray = JSONArray()
        for (rule in rules) {
            val obj = JSONObject()
            obj.put("name", rule.name)
            obj.put("senderText", rule.senderText)
            obj.put("phoneNumber", rule.phoneNumber)
            obj.put("targetNumber", rule.targetNumber)
            obj.put("sortOrder", rule.sortOrder)
            obj.put("category", rule.category)
            jsonArray.put(obj)
        }
        analyticsTracker.logEvent(
            AnalyticsEvents.RULES_EXPORTED,
            mapOf(AnalyticsEvents.Param.RULE_COUNT to rules.size),
        )
        return jsonArray.toString(2)
    }
}
