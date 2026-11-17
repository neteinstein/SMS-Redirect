package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.FakeRuleRepository
import com.neteinstein.smsredirect.FakeScheduledSmsRepository
import com.neteinstein.smsredirect.FakeScheduledSmsScheduler
import com.neteinstein.smsredirect.FakeSettingsRepository
import com.neteinstein.smsredirect.domain.model.LandingScreen
import com.neteinstein.smsredirect.domain.model.PhoneAction
import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.model.RuleType
import com.neteinstein.smsredirect.domain.model.ScheduledSms
import com.neteinstein.smsredirect.domain.model.VibrationPattern
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupUseCasesTest {
    @Test
    fun `export includes rules, scheduled sms and settings`() =
        runTest {
            val ruleRepository = FakeRuleRepository()
            val scheduledSmsRepository = FakeScheduledSmsRepository()
            val settingsRepository = FakeSettingsRepository()
            ruleRepository.insert(Rule(senderText = "BANK", targetNumber = "5551234"))
            scheduledSmsRepository.insert(
                ScheduledSms(contactNumber = "5559876", message = "Hello", dateEpochMs = 1000L),
            )
            settingsRepository.setSendWhileRoaming(true)
            settingsRepository.setForwardPrefix("Custom:")

            val json = ExportBackupUseCase(ruleRepository, scheduledSmsRepository, settingsRepository)()

            assertTrue(json.contains("\"rules\""))
            assertTrue(json.contains("\"scheduledSms\""))
            assertTrue(json.contains("\"settings\""))
            assertTrue(json.contains("BANK"))
            assertTrue(json.contains("Hello"))
            assertTrue(json.contains("Custom:"))
        }

    @Test
    fun `import restores rules, scheduled sms and settings`() =
        runTest {
            val ruleRepository = FakeRuleRepository()
            val scheduledSmsRepository = FakeScheduledSmsRepository()
            val settingsRepository = FakeSettingsRepository()
            val scheduler = FakeScheduledSmsScheduler()
            val sourceRuleRepository = FakeRuleRepository()
            val sourceScheduledSmsRepository = FakeScheduledSmsRepository()
            val sourceSettingsRepository = FakeSettingsRepository()
            sourceRuleRepository.insert(Rule(senderText = "BANK", targetNumber = "5551234"))
            sourceScheduledSmsRepository.insert(
                ScheduledSms(contactNumber = "5559876", message = "Hello", dateEpochMs = 1000L),
            )
            sourceSettingsRepository.setSendWhileRoaming(true)
            sourceSettingsRepository.setKeepScheduleHistory(false)
            sourceSettingsRepository.setLandingScreen(LandingScreen.SCHEDULE)
            val json =
                ExportBackupUseCase(sourceRuleRepository, sourceScheduledSmsRepository, sourceSettingsRepository)()

            val result =
                ImportBackupUseCase(ruleRepository, scheduledSmsRepository, settingsRepository, scheduler)(json)

            assertEquals(1, result.importedRules)
            assertEquals(1, result.importedScheduledSms)
            assertEquals(1, ruleRepository.getRulesSnapshot().size)
            assertEquals(1, scheduledSmsRepository.getScheduledSmsSnapshot().size)
            assertTrue(settingsRepository.getSendWhileRoaming())
            assertFalse(settingsRepository.getKeepScheduleHistory())
            assertEquals(LandingScreen.SCHEDULE, settingsRepository.getLandingScreen())
            assertEquals(1, scheduler.scheduleInitialCalls.size)
        }

    @Test
    fun `import skips duplicate rules and scheduled sms`() =
        runTest {
            val ruleRepository = FakeRuleRepository()
            val scheduledSmsRepository = FakeScheduledSmsRepository()
            val settingsRepository = FakeSettingsRepository()
            val scheduler = FakeScheduledSmsScheduler()
            val rule = Rule(senderText = "BANK", targetNumber = "5551234")
            val scheduledSms = ScheduledSms(contactNumber = "5559876", message = "Hello", dateEpochMs = 1000L)
            ruleRepository.insert(rule)
            scheduledSmsRepository.insert(scheduledSms)
            val json = ExportBackupUseCase(ruleRepository, scheduledSmsRepository, settingsRepository)()

            val result =
                ImportBackupUseCase(ruleRepository, scheduledSmsRepository, settingsRepository, scheduler)(json)

            assertEquals(0, result.importedRules)
            assertEquals(0, result.importedScheduledSms)
            assertEquals(1, ruleRepository.getRulesSnapshot().size)
            assertEquals(1, scheduledSmsRepository.getScheduledSmsSnapshot().size)
        }

    @Test
    fun `import supports legacy rules-only array format`() =
        runTest {
            val ruleRepository = FakeRuleRepository()
            val scheduledSmsRepository = FakeScheduledSmsRepository()
            val settingsRepository = FakeSettingsRepository()
            val scheduler = FakeScheduledSmsScheduler()
            val legacyArray = JSONArray()
            legacyArray.put(
                org.json.JSONObject().apply {
                    put("name", "Legacy")
                    put("senderText", "BANK")
                    put("targetNumber", "5551234")
                },
            )

            val result =
                ImportBackupUseCase(
                    ruleRepository,
                    scheduledSmsRepository,
                    settingsRepository,
                    scheduler,
                )(legacyArray.toString())

            assertEquals(1, result.importedRules)
            assertEquals(0, result.importedScheduledSms)
            assertEquals(1, ruleRepository.getRulesSnapshot().size)
        }

    @Test
    fun `phone action rules survive an export and import`() =
        runTest {
            val actionRule =
                Rule(
                    id = 9,
                    name = "Bank buzz",
                    senderText = "BANK",
                    phoneNumber = "BANK",
                    category = "Alerts",
                    type = RuleType.PHONE_ACTION,
                    messageText = "fraud",
                    phoneAction = PhoneAction.Vibrate(VibrationPattern.SOS),
                )
            val json =
                ExportBackupUseCase(
                    FakeRuleRepository(listOf(actionRule)),
                    FakeScheduledSmsRepository(),
                    FakeSettingsRepository(),
                )()
            val ruleRepository = FakeRuleRepository()

            val result =
                ImportBackupUseCase(
                    ruleRepository,
                    FakeScheduledSmsRepository(),
                    FakeSettingsRepository(),
                    FakeScheduledSmsScheduler(),
                )(json)

            assertEquals(1, result.importedRules)
            assertEquals(actionRule.copy(id = 1), ruleRepository.getRulesSnapshot().single())
        }

    @Test
    fun `rules from an export made before automation import as redirect rules`() =
        runTest {
            val legacyArray = JSONArray()
            legacyArray.put(
                org.json.JSONObject().apply {
                    put("name", "Legacy")
                    put("senderText", "BANK")
                    put("targetNumber", "5551234")
                },
            )
            val ruleRepository = FakeRuleRepository()

            ImportBackupUseCase(
                ruleRepository,
                FakeScheduledSmsRepository(),
                FakeSettingsRepository(),
                FakeScheduledSmsScheduler(),
            )(legacyArray.toString())

            val rule = ruleRepository.getRulesSnapshot().single()
            assertEquals(RuleType.REDIRECT, rule.type)
            assertEquals(null, rule.phoneAction)
        }
}
