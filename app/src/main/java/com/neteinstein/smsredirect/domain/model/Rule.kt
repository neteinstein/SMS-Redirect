package com.neteinstein.smsredirect.domain.model

import com.neteinstein.smsredirect.domain.messaging.MessagingAddresses

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
     * (ignoring case) or the same phone number, with or without its country code.
     *
     * The rule editor saves a number typed without a country code with [defaultPrefix] in front
     * (`912345678` → `+351912345678`, and a short code `4800` → `+3514800`), but carriers often
     * deliver local senders without it. So a number matches when:
     * - [MessagingAddresses.same] says so: the local form is the tail of the international one
     *   (7+ digits), in either direction, and two numbers that both have a country code must be
     *   equal; or
     * - it is local and, with [defaultPrefix] in front, is exactly the rule's number. This is what
     *   makes a short code match: [MessagingAddresses.same] will not match one by its tail, since
     *   `4800` would then also match every number ending in 4800.
     */
    fun matchesSender(
        sender: String?,
        defaultPrefix: String = "",
    ): Boolean {
        val normalizedSender = sender.orEmpty().trim()
        if (normalizedSender.isEmpty()) return false
        val matchesText =
            senderText.isNotBlank() && normalizedSender.equals(senderText.trim(), ignoreCase = true)
        return matchesText || matchesNumber(normalizedSender, defaultPrefix)
    }

    /** Whether this phone-action rule fires for an SMS from [sender] with [body]; see [matchesSender]. */
    fun triggersPhoneAction(
        sender: String?,
        body: String,
        defaultPrefix: String = "",
    ): Boolean {
        if (type != RuleType.PHONE_ACTION || !isValid()) return false
        val senderOk = !hasSenderCondition || matchesSender(sender, defaultPrefix)
        val messageOk = messageText.isBlank() || body.contains(messageText.trim(), ignoreCase = true)
        return senderOk && messageOk
    }

    private fun matchesNumber(
        sender: String,
        defaultPrefix: String,
    ): Boolean {
        if (phoneNumber.none(Char::isDigit) || sender.none(Char::isDigit)) return false
        if (MessagingAddresses.same(sender, phoneNumber)) return true
        val senderWithPrefix = withDefaultPrefix(sender, defaultPrefix) ?: return false
        return MessagingAddresses.same(senderWithPrefix, phoneNumber)
    }

    private companion object {
        /**
         * [sender] as the rule editor would have saved it: [defaultPrefix] followed by its digits,
         * national trunk "0" included. Null when there is no prefix or [sender] is not a number
         * without a country code.
         */
        fun withDefaultPrefix(
            sender: String,
            defaultPrefix: String,
        ): String? {
            val prefix = defaultPrefix.trim()
            if (!prefix.startsWith("+") || prefix.drop(1).none(Char::isDigit)) return null
            if (sender.startsWith("+") || sender.startsWith("00")) return null
            if (!sender.all { it.isDigit() || it in "-(). " }) return null
            return prefix + sender.filter(Char::isDigit)
        }
    }
}
