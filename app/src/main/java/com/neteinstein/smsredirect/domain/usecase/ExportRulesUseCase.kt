package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.repository.RuleRepository
import org.json.JSONArray
import org.json.JSONObject

class ExportRulesUseCase(
    private val repository: RuleRepository,
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
            jsonArray.put(obj)
        }
        return jsonArray.toString(2)
    }
}
