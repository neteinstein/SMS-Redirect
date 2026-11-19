package com.neteinstein.smsredirect.domain.usecase

import android.util.Log
import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.model.RuleType
import com.neteinstein.smsredirect.domain.repository.RuleRepository
import com.neteinstein.smsredirect.domain.repository.SettingsRepository

/**
 * The first redirect rule (by sort order) whose sender matches; phone-action rules are ignored.
 * See [Rule.matchesSender] for how a sender without a country code matches.
 */
class FindMatchingRuleUseCase(
    private val ruleRepository: RuleRepository,
    private val settingsRepository: SettingsRepository,
) {
    companion object {
        private const val TAG = "FindMatchingRule"
    }

    suspend operator fun invoke(sender: String?): Rule? {
        val rules = ruleRepository.getRulesSnapshot().filter { it.type == RuleType.REDIRECT }
        Log.d(TAG, "Checking ${rules.size} redirect rules")
        val defaultPrefix = settingsRepository.getDefaultPrefix()

        return rules.firstOrNull { rule ->
            if (rule.targetNumber.isBlank()) {
                Log.d(TAG, "  Rule id=${rule.id}: skipped (empty targetNumber)")
                return@firstOrNull false
            }
            val matches = rule.matchesSender(sender, defaultPrefix)
            Log.d(TAG, "  Rule id=${rule.id}: matches=$matches")
            matches
        }
    }
}
