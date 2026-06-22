package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.repository.RuleRepository

class FindMatchingRuleUseCase(
    private val ruleRepository: RuleRepository
) {
    suspend operator fun invoke(sender: String?): Rule? {
        val normalizedSender = sender.orEmpty().trim()
        val normalizedSenderDigits = normalizedSender.filter(Char::isDigit)

        return ruleRepository.getRulesSnapshot().firstOrNull { rule ->
            if (rule.targetNumber.isBlank()) return@firstOrNull false

            val matchesText = rule.senderText.isNotBlank() &&
                normalizedSender.contains(rule.senderText.trim(), ignoreCase = true)
            val matchesNumber = rule.phoneNumber.isNotBlank() &&
                normalizedSenderDigits == rule.phoneNumber.filter(Char::isDigit)

            matchesText || matchesNumber
        }
    }
}
