package com.neteinstein.smsredirect.domain.usecase

import android.util.Log
import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.model.RuleType
import com.neteinstein.smsredirect.domain.repository.RuleRepository

/** The first redirect rule (by sort order) whose sender matches; phone-action rules are ignored. */
class FindMatchingRuleUseCase(
    private val ruleRepository: RuleRepository,
) {
    companion object {
        private const val TAG = "FindMatchingRule"
    }

    suspend operator fun invoke(sender: String?): Rule? {
        val rules = ruleRepository.getRulesSnapshot().filter { it.type == RuleType.REDIRECT }
        Log.d(TAG, "Checking ${rules.size} redirect rules")

        return rules.firstOrNull { rule ->
            if (rule.targetNumber.isBlank()) {
                Log.d(TAG, "  Rule id=${rule.id}: skipped (empty targetNumber)")
                return@firstOrNull false
            }
            val matches = rule.matchesSender(sender)
            Log.d(TAG, "  Rule id=${rule.id}: matches=$matches")
            matches
        }
    }
}
