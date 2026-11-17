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
}
