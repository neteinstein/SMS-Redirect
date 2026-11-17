package com.neteinstein.smsredirect.domain.model

/**
 * An automation rule, matched against every incoming SMS.
 *
 * - [RuleType.REDIRECT] forwards the message to [targetNumber] when the sender matches. Only the
 *   first matching redirect rule (by [sortOrder]) forwards.
 * - [RuleType.PHONE_ACTION] runs [phoneAction] when the sender matches (if [senderText] is set)
 *   and the body contains [messageText] (if set). Every matching phone-action rule runs.
 */
data class Rule(
    val id: Long = 0,
    val name: String = "",
    val senderText: String = "",
    val phoneNumber: String = "",
    val targetNumber: String = "",
    val sortOrder: Int = 0,
    val category: String = "",
    val type: RuleType = RuleType.REDIRECT,
    /** Phone actions only: case-insensitive "contains" match on the message body. */
    val messageText: String = "",
    /** Phone actions only. */
    val phoneAction: PhoneAction? = null,
) {
    private val hasSenderCondition: Boolean get() = senderText.isNotBlank() || phoneNumber.isNotBlank()

    fun isValid(): Boolean =
        when (type) {
            RuleType.REDIRECT -> targetNumber.isNotBlank() && hasSenderCondition
            RuleType.PHONE_ACTION -> phoneAction != null && (hasSenderCondition || messageText.isNotBlank())
        }

    /**
     * Whether [sender] is the sender this rule names: either the same alphanumeric sender ID
     * (ignoring case) or the same digits of a phone number.
     */
    fun matchesSender(sender: String?): Boolean {
        val normalizedSender = sender.orEmpty().trim()
        val matchesText =
            senderText.isNotBlank() && normalizedSender.equals(senderText.trim(), ignoreCase = true)
        val senderDigits = normalizedSender.filter(Char::isDigit)
        val ruleDigits = phoneNumber.filter(Char::isDigit)
        val matchesNumber = ruleDigits.isNotEmpty() && senderDigits.isNotEmpty() && senderDigits == ruleDigits
        return matchesText || matchesNumber
    }

    /** Whether this phone-action rule fires for an SMS from [sender] with [body]. */
    fun triggersPhoneAction(
        sender: String?,
        body: String,
    ): Boolean {
        if (type != RuleType.PHONE_ACTION || !isValid()) return false
        val senderOk = !hasSenderCondition || matchesSender(sender)
        val messageOk = messageText.isBlank() || body.contains(messageText.trim(), ignoreCase = true)
        return senderOk && messageOk
    }
}
