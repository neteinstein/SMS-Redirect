package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.device.PhoneActionScheduler
import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.repository.RuleRepository
import com.neteinstein.smsredirect.domain.repository.SettingsRepository

/**
 * Every phone-action rule an SMS from [sender] with [body] triggers, in sort order. See
 * [Rule.matchesSender] for how a sender without a country code matches.
 */
class FindMatchingPhoneActionRulesUseCase(
    private val ruleRepository: RuleRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(
        sender: String?,
        body: String,
    ): List<Rule> {
        val defaultPrefix = settingsRepository.getDefaultPrefix()
        return ruleRepository.getRulesSnapshot().filter { it.triggersPhoneAction(sender, body, defaultPrefix) }
    }
}

/**
 * Schedules the phone action of every rule an incoming SMS triggers. Unlike redirects, where the
 * first match wins, all matching phone actions run, so one message can both ring and vibrate.
 *
 * Only the actions are handed on, never the message itself: the SMS body is matched here, in
 * memory, and is not written to WorkManager's database.
 *
 * @return how many actions were scheduled.
 */
class TriggerPhoneActionsUseCase(
    private val findMatchingPhoneActionRules: FindMatchingPhoneActionRulesUseCase,
    private val scheduler: PhoneActionScheduler,
) {
    suspend operator fun invoke(
        sender: String?,
        body: String,
    ): Int {
        val rules = findMatchingPhoneActionRules(sender, body)
        rules.forEach { rule -> scheduler.schedule(checkNotNull(rule.phoneAction), rule.name) }
        return rules.size
    }
}
