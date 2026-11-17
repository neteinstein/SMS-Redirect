package com.neteinstein.smsredirect.data.repository

import com.neteinstein.smsredirect.data.local.RuleEntity
import com.neteinstein.smsredirect.domain.model.PhoneAction
import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.model.RuleType
import com.neteinstein.smsredirect.domain.model.SoundMode
import com.neteinstein.smsredirect.domain.model.VibrationPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleMappersTest {
    @Test
    fun `phone action rules round trip through the entity`() {
        val rules =
            listOf(
                PhoneAction.Ring("content://media/1", "Song"),
                PhoneAction.Vibrate(VibrationPattern.HEARTBEAT),
                PhoneAction.SetSoundMode(SoundMode.TOGGLE_RING_SILENT),
            ).map { action ->
                Rule(
                    id = 5,
                    name = "n",
                    senderText = "BANK",
                    phoneNumber = "BANK",
                    sortOrder = 2,
                    category = "c",
                    type = RuleType.PHONE_ACTION,
                    messageText = "ring",
                    phoneAction = action,
                )
            }

        rules.forEach { rule -> assertEquals(rule, rule.toEntity().toDomain()) }
    }

    @Test
    fun `redirect rule stores no phone action`() {
        val rule = Rule(id = 1, senderText = "BANK", phoneNumber = "BANK", targetNumber = "777")

        val entity = rule.toEntity()

        assertEquals("REDIRECT", entity.ruleType)
        assertEquals("", entity.phoneAction)
        assertEquals(rule, entity.toDomain())
    }

    @Test
    fun `row migrated from v11 reads as a redirect rule`() {
        val migrated = RuleEntity(id = 1, senderText = "BANK", phoneNumber = "BANK", targetNumber = "777")

        val rule = migrated.toDomain()

        assertEquals(RuleType.REDIRECT, rule.type)
        assertNull(rule.phoneAction)
    }

    @Test
    fun `unknown rule type reads as redirect instead of crashing`() {
        val entity = RuleEntity(senderText = "", phoneNumber = "", targetNumber = "", ruleType = "FUTURE")

        assertEquals(RuleType.REDIRECT, entity.toDomain().type)
    }
}
