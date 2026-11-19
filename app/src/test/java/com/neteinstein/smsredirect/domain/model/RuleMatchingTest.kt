package com.neteinstein.smsredirect.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleMatchingTest {
    private val ring = PhoneAction.Ring()

    private fun actionRule(
        sender: String = "",
        messageText: String = "",
    ) = Rule(
        senderText = sender,
        phoneNumber = sender,
        type = RuleType.PHONE_ACTION,
        messageText = messageText,
        phoneAction = ring,
    )

    @Test
    fun `phone action rule needs an action and a sender or a message text`() {
        assertTrue(actionRule(messageText = "find my phone").isValid())
        assertTrue(actionRule(sender = "+351911111111").isValid())
        assertTrue(actionRule(sender = "+351911111111", messageText = "ring").isValid())
        assertFalse(actionRule().isValid())
        assertFalse(actionRule(messageText = "ring").copy(phoneAction = null).isValid())
    }

    @Test
    fun `phone action rule needs no target number`() {
        assertTrue(actionRule(messageText = "ring").copy(targetNumber = "").isValid())
    }

    @Test
    fun `redirect rule validation is unchanged`() {
        assertTrue(Rule(senderText = "BANK", targetNumber = "777").isValid())
        assertFalse(Rule(senderText = "BANK").isValid())
        assertFalse(Rule(targetNumber = "777").isValid())
    }

    @Test
    fun `message only rule matches any sender whose message contains the text, ignoring case`() {
        val rule = actionRule(messageText = "Find My Phone")

        assertTrue(rule.triggersPhoneAction("+351911111111", "please find my phone now"))
        assertTrue(rule.triggersPhoneAction(null, "FIND MY PHONE"))
        assertFalse(rule.triggersPhoneAction("+351911111111", "find my keys"))
    }

    @Test
    fun `sender and message rule needs both`() {
        val rule = actionRule(sender = "+351 911 111 111", messageText = "ring")

        assertTrue(rule.triggersPhoneAction("+351911111111", "Ring please"))
        assertFalse(rule.triggersPhoneAction("+351922222222", "Ring please"))
        assertFalse(rule.triggersPhoneAction("+351911111111", "hello"))
    }

    @Test
    fun `sender only rule matches every message from that sender`() {
        val rule = actionRule(sender = "MyBank")

        assertTrue(rule.triggersPhoneAction("MYBANK", "anything at all"))
        assertFalse(rule.triggersPhoneAction("OtherBank", "anything at all"))
    }

    @Test
    fun `redirect rules never trigger phone actions`() {
        val rule = Rule(senderText = "BANK", phoneNumber = "BANK", targetNumber = "777", messageText = "x")

        assertFalse(rule.triggersPhoneAction("BANK", "x"))
    }

    @Test
    fun `invalid phone action rule never triggers`() {
        assertFalse(actionRule().triggersPhoneAction("+351911111111", "anything"))
    }

    private fun redirectRule(sender: String) = Rule(senderText = sender, phoneNumber = sender, targetNumber = "+351999999999")

    @Test
    fun `number saved with a country code matches the sender without it`() {
        val rule = redirectRule("+351912345678")

        assertTrue(rule.matchesSender("912345678"))
        assertTrue(rule.matchesSender("912 345 678"))
        assertTrue(rule.matchesSender("+351912345678"))
        assertTrue(rule.matchesSender("00351912345678"))
        assertTrue(redirectRule("+3515551234").matchesSender("5551234"))
    }

    @Test
    fun `number saved without a country code matches the sender with it`() {
        val rule = redirectRule("912345678")

        assertTrue(rule.matchesSender("+351912345678"))
        assertTrue(rule.matchesSender("00351912345678"))
        assertTrue(rule.matchesSender("912345678"))
        assertTrue(redirectRule("5551234").matchesSender("+3515551234"))
    }

    @Test
    fun `numbers with different country codes never match`() {
        val rule = redirectRule("+351912345678")

        assertFalse(rule.matchesSender("+44912345678", defaultPrefix = "+351"))
        assertFalse(redirectRule("+44912345678").matchesSender("+351912345678", defaultPrefix = "+351"))
    }

    @Test
    fun `a different local number does not match`() {
        assertFalse(redirectRule("+351912345678").matchesSender("912345679", defaultPrefix = "+351"))
        assertFalse(redirectRule("912345678").matchesSender("+351912345679", defaultPrefix = "+351"))
    }

    @Test
    fun `a short sender does not match a longer number by its tail`() {
        assertFalse(redirectRule("+351912344567").matchesSender("4567", defaultPrefix = "+351"))
        assertFalse(redirectRule("912344567").matchesSender("4567", defaultPrefix = "+351"))
    }

    @Test
    fun `short code saved with the default prefix matches the short code`() {
        // What the rule editor saves for "4800" while the default prefix is +351.
        val rule = redirectRule("+3514800")

        assertTrue(rule.matchesSender("4800", defaultPrefix = "+351"))
        assertTrue(rule.matchesSender("+3514800", defaultPrefix = "+351"))
    }

    @Test
    fun `short code match needs exactly the default prefix in front`() {
        val rule = redirectRule("+3514800")

        // "+351" + "14800" is not "+351" + "4800", although "14800" is also a tail of 3514800.
        assertFalse(rule.matchesSender("14800", defaultPrefix = "+351"))
        assertFalse(rule.matchesSender("800", defaultPrefix = "+351"))
        assertFalse(rule.matchesSender("4800", defaultPrefix = "+34"))
        assertFalse(rule.matchesSender("4800", defaultPrefix = ""))
    }

    @Test
    fun `short code saved without a prefix matches only itself`() {
        val rule = redirectRule("4800")

        assertTrue(rule.matchesSender("4800", defaultPrefix = "+351"))
        assertFalse(rule.matchesSender("14800", defaultPrefix = "+351"))
    }

    @Test
    fun `alphanumeric senders still compare ignoring case and get no prefix`() {
        val rule = redirectRule("MyBank")

        assertTrue(rule.matchesSender("MYBANK", defaultPrefix = "+351"))
        assertFalse(rule.matchesSender("+351MyBank", defaultPrefix = "+351"))
        assertTrue(redirectRule("Bank24").matchesSender("BANK24", defaultPrefix = "+351"))
        assertFalse(redirectRule("Bank24").matchesSender("24", defaultPrefix = "+351"))
    }

    @Test
    fun `a numeric rule never matches a missing or alphanumeric sender`() {
        val rule = redirectRule("+351912345678")

        assertFalse(rule.matchesSender(null, defaultPrefix = "+351"))
        assertFalse(rule.matchesSender("", defaultPrefix = "+351"))
        assertFalse(rule.matchesSender("MyBank", defaultPrefix = "+351"))
    }

    @Test
    fun `phone action sender matches with or without the country code`() {
        val saved = actionRule(sender = "+351911111111", messageText = "meeting")
        val savedLocal = actionRule(sender = "911111111", messageText = "meeting")
        val shortCode = actionRule(sender = "+3514800")

        assertTrue(saved.triggersPhoneAction("911111111", "Meeting now", defaultPrefix = "+351"))
        assertTrue(savedLocal.triggersPhoneAction("+351911111111", "Meeting now", defaultPrefix = "+351"))
        assertTrue(shortCode.triggersPhoneAction("4800", "anything", defaultPrefix = "+351"))
        assertFalse(saved.triggersPhoneAction("+44911111111", "Meeting now", defaultPrefix = "+351"))
        assertFalse(shortCode.triggersPhoneAction("14800", "anything", defaultPrefix = "+351"))
    }
}
