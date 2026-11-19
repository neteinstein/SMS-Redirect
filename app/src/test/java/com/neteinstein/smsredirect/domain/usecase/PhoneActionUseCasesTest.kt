package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.FakePhoneActionScheduler
import com.neteinstein.smsredirect.FakeRuleRepository
import com.neteinstein.smsredirect.FakeSettingsRepository
import com.neteinstein.smsredirect.domain.model.PhoneAction
import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.model.RuleType
import com.neteinstein.smsredirect.domain.model.SoundMode
import com.neteinstein.smsredirect.domain.model.VibrationPattern
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneActionUseCasesTest {
    private val redirect =
        Rule(id = 1, name = "Forward bank", senderText = "BANK", phoneNumber = "BANK", targetNumber = "777", sortOrder = 0)
    private val ring =
        Rule(
            id = 2,
            name = "Find phone",
            type = RuleType.PHONE_ACTION,
            messageText = "where are you",
            phoneAction = PhoneAction.Ring(soundUri = "content://song", soundName = "Song"),
            sortOrder = 1,
        )
    private val vibrateFromBank =
        Rule(
            id = 3,
            name = "Bank buzz",
            senderText = "BANK",
            phoneNumber = "BANK",
            type = RuleType.PHONE_ACTION,
            phoneAction = PhoneAction.Vibrate(VibrationPattern.SOS),
            sortOrder = 2,
        )
    private val silenceFromBoss =
        Rule(
            id = 4,
            name = "Meeting",
            senderText = "+351911111111",
            phoneNumber = "+351911111111",
            type = RuleType.PHONE_ACTION,
            messageText = "meeting",
            phoneAction = PhoneAction.SetSoundMode(SoundMode.SILENT),
            sortOrder = 3,
        )
    private val repository = FakeRuleRepository(listOf(redirect, ring, vibrateFromBank, silenceFromBoss))
    private val settings = FakeSettingsRepository()

    @Test
    fun `finds every matching phone action rule, not just the first`() =
        runTest {
            val matches = FindMatchingPhoneActionRulesUseCase(repository, settings)("BANK", "Where are you?")

            assertEquals(listOf(ring, vibrateFromBank), matches)
        }

    @Test
    fun `redirect rules are not phone action matches`() =
        runTest {
            val matches = FindMatchingPhoneActionRulesUseCase(repository, settings)("BANK", "your statement")

            assertEquals(listOf(vibrateFromBank), matches)
        }

    @Test
    fun `trigger schedules each matching action with its rule name`() =
        runTest {
            val scheduler = FakePhoneActionScheduler()
            val useCase = TriggerPhoneActionsUseCase(FindMatchingPhoneActionRulesUseCase(repository, settings), scheduler)

            val count = useCase("+351 911 111 111", "Meeting moved, where are you")

            assertEquals(2, count)
            assertEquals(
                listOf(ring.phoneAction!! to "Find phone", silenceFromBoss.phoneAction!! to "Meeting"),
                scheduler.scheduled,
            )
        }

    @Test
    fun `trigger schedules nothing when no rule matches`() =
        runTest {
            val scheduler = FakePhoneActionScheduler()

            val count = TriggerPhoneActionsUseCase(FindMatchingPhoneActionRulesUseCase(repository, settings), scheduler)("x", "hi")

            assertEquals(0, count)
            assertEquals(emptyList<Any>(), scheduler.scheduled)
        }

    @Test
    fun `phone action rule matches its sender without the country code`() =
        runTest {
            val matches = FindMatchingPhoneActionRulesUseCase(repository, settings)("911111111", "Meeting at 3")

            assertEquals(listOf(silenceFromBoss), matches)
        }

    @Test
    fun `every phone action rule matching the sender in either form runs`() =
        runTest {
            val ringFromBossLocal =
                Rule(
                    id = 5,
                    name = "Boss rings",
                    senderText = "911111111",
                    phoneNumber = "911111111",
                    type = RuleType.PHONE_ACTION,
                    phoneAction = PhoneAction.Ring(),
                    sortOrder = 4,
                )
            val shortCodeBuzz =
                Rule(
                    id = 6,
                    name = "Carrier buzz",
                    senderText = "+3514800",
                    phoneNumber = "+3514800",
                    type = RuleType.PHONE_ACTION,
                    phoneAction = PhoneAction.Vibrate(VibrationPattern.SOS),
                    sortOrder = 5,
                )
            val rules = FakeRuleRepository(listOf(redirect, ring, vibrateFromBank, silenceFromBoss, ringFromBossLocal, shortCodeBuzz))
            val find = FindMatchingPhoneActionRulesUseCase(rules, settings)

            assertEquals(listOf(silenceFromBoss, ringFromBossLocal), find("911111111", "meeting"))
            assertEquals(listOf(silenceFromBoss, ringFromBossLocal), find("+351911111111", "meeting"))
            assertEquals(listOf(shortCodeBuzz), find("4800", "hello"))
            assertEquals(emptyList<Rule>(), find("14800", "hello"))
        }

    @Test
    fun `redirect matching ignores phone action rules even when they come first`() =
        runTest {
            val actionFirst = vibrateFromBank.copy(sortOrder = -1)
            val repo = FakeRuleRepository(listOf(actionFirst, redirect))

            assertEquals(redirect, FindMatchingRuleUseCase(repo, settings)("BANK"))
            assertNull(FindMatchingRuleUseCase(FakeRuleRepository(listOf(vibrateFromBank)), settings)("BANK"))
        }
}
