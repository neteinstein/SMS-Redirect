package com.neteinstein.smsredirect.domain.usecase

import android.util.Log
import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.repository.RuleRepository

class FindMatchingRuleUseCase(
    private val ruleRepository: RuleRepository,
) {
    companion object {
        private const val TAG = "FindMatchingRule"
    }

    suspend operator fun invoke(sender: String?): Rule? {
        val normalizedSender = sender.orEmpty().trim()
        val normalizedSenderDigits = normalizedSender.filter(Char::isDigit)

        val rules = ruleRepository.getRulesSnapshot()
        Log.d(TAG, "Checking ${rules.size} rules against sender='$normalizedSender' digits='$normalizedSenderDigits'")

        return rules.firstOrNull { rule ->
            if (rule.targetNumber.isBlank()) {
                Log.d(TAG, "  Rule id=${rule.id}: skipped (empty targetNumber)")
                return@firstOrNull false
            }

            val matchesText =
                rule.senderText.isNotBlank() &&
                    normalizedSender.equals(rule.senderText.trim(), ignoreCase = true)
            val ruleDigits = rule.phoneNumber.filter(Char::isDigit)
            val matchesNumber =
                ruleDigits.isNotEmpty() &&
                    normalizedSenderDigits.isNotEmpty() &&
                    normalizedSenderDigits == ruleDigits

            Log.d(
                TAG,
                "  Rule id=${rule.id} name='${rule.name}': " +
                    "senderText='${rule.senderText}' matchesText=$matchesText, " +
                    "phoneNumber='${rule.phoneNumber}' matchesNumber=$matchesNumber",
            )

            matchesText || matchesNumber
        }
    }
}
