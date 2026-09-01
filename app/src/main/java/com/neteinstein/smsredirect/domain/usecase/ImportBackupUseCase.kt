package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.analytics.AnalyticsEvents
import com.neteinstein.smsredirect.domain.analytics.AnalyticsTracker
import com.neteinstein.smsredirect.domain.analytics.NoOpAnalyticsTracker
import com.neteinstein.smsredirect.domain.model.LandingScreen
import com.neteinstein.smsredirect.domain.model.RepeatType
import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.model.ScheduledSms
import com.neteinstein.smsredirect.domain.repository.RuleRepository
import com.neteinstein.smsredirect.domain.repository.ScheduledSmsRepository
import com.neteinstein.smsredirect.domain.repository.SettingsRepository
import com.neteinstein.smsredirect.domain.scheduling.ScheduledSmsScheduler
import org.json.JSONArray
import org.json.JSONObject

/** Result of a [ImportBackupUseCase] run, reporting how many entries of each kind were restored. */
data class BackupImportResult(
    val importedRules: Int,
    val importedScheduledSms: Int,
)

/**
 * Restores a full app configuration backup produced by [ExportBackupUseCase]: redirect rules,
 * scheduled SMS rules and settings (including the keep-history toggles). Also supports the
 * legacy rules-only export format for backward compatibility.
 */
class ImportBackupUseCase(
    private val ruleRepository: RuleRepository,
    private val scheduledSmsRepository: ScheduledSmsRepository,
    private val settingsRepository: SettingsRepository,
    private val scheduledSmsScheduler: ScheduledSmsScheduler,
    private val analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker,
) {
    suspend operator fun invoke(json: String): BackupImportResult {
        val trimmed = json.trim()
        if (trimmed.startsWith("[")) {
            // Legacy export format: a bare JSON array of rules.
            val importedRules = importRules(JSONArray(trimmed))
            analyticsTracker.logEvent(
                AnalyticsEvents.RULES_IMPORTED,
                mapOf(AnalyticsEvents.Param.RULE_COUNT to importedRules),
            )
            return BackupImportResult(
                importedRules = importedRules,
                importedScheduledSms = 0,
            )
        }

        val root = JSONObject(trimmed)
        val importedRules = if (root.has("rules")) importRules(root.getJSONArray("rules")) else 0
        val importedScheduledSms =
            if (root.has("scheduledSms")) importScheduledSms(root.getJSONArray("scheduledSms")) else 0
        if (root.has("settings")) {
            importSettings(root.getJSONObject("settings"))
        }
        analyticsTracker.logEvent(
            AnalyticsEvents.RULES_IMPORTED,
            mapOf(AnalyticsEvents.Param.RULE_COUNT to importedRules),
        )
        return BackupImportResult(importedRules, importedScheduledSms)
    }

    private suspend fun importRules(jsonArray: JSONArray): Int {
        val existingRules = ruleRepository.getRulesSnapshot()
        var importedCount = 0

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val rule =
                Rule(
                    name = obj.optString("name", ""),
                    senderText = obj.optString("senderText", ""),
                    phoneNumber = obj.optString("phoneNumber", ""),
                    targetNumber = obj.optString("targetNumber", ""),
                    sortOrder = obj.optInt("sortOrder", 0),
                    category = obj.optString("category", ""),
                )

            val isDuplicate =
                existingRules.any { existing ->
                    existing.name == rule.name &&
                        existing.senderText == rule.senderText &&
                        existing.phoneNumber == rule.phoneNumber &&
                        existing.targetNumber == rule.targetNumber
                }

            if (!isDuplicate && rule.isValid()) {
                ruleRepository.insert(rule)
                importedCount++
            }
        }

        return importedCount
    }

    private suspend fun importScheduledSms(jsonArray: JSONArray): Int {
        val existingScheduledSms = scheduledSmsRepository.getScheduledSmsSnapshot()
        var importedCount = 0

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val repeatType =
                runCatching {
                    RepeatType.valueOf(obj.optString("repeatType", RepeatType.NONE.name))
                }.getOrDefault(RepeatType.NONE)
            val scheduledSms =
                ScheduledSms(
                    name = obj.optString("name", ""),
                    category = obj.optString("category", ""),
                    contactNumber = obj.optString("contactNumber", ""),
                    message = obj.optString("message", ""),
                    dateEpochMs = obj.optLong("dateEpochMs", 0),
                    repeatType = repeatType,
                    stopAtEpochMs = if (obj.isNull("stopAtEpochMs")) null else obj.optLong("stopAtEpochMs"),
                    sortOrder = obj.optInt("sortOrder", 0),
                )

            val isDuplicate =
                existingScheduledSms.any { existing ->
                    existing.name == scheduledSms.name &&
                        existing.contactNumber == scheduledSms.contactNumber &&
                        existing.message == scheduledSms.message &&
                        existing.dateEpochMs == scheduledSms.dateEpochMs
                }

            if (!isDuplicate && scheduledSms.isValid()) {
                val id = scheduledSmsRepository.insert(scheduledSms)
                scheduledSmsScheduler.scheduleInitial(scheduledSms.copy(id = id))
                importedCount++
            }
        }

        return importedCount
    }

    private suspend fun importSettings(settings: JSONObject) {
        if (settings.has("sendWhileRoaming")) {
            settingsRepository.setSendWhileRoaming(settings.optBoolean("sendWhileRoaming"))
        }
        if (settings.has("keepHistory")) {
            settingsRepository.setKeepHistory(settings.optBoolean("keepHistory"))
        }
        if (settings.has("keepScheduleHistory")) {
            settingsRepository.setKeepScheduleHistory(settings.optBoolean("keepScheduleHistory"))
        }
        if (settings.has("defaultPrefix")) {
            settingsRepository.setDefaultPrefix(settings.optString("defaultPrefix"))
        }
        if (settings.has("forwardPrefix")) {
            settingsRepository.setForwardPrefix(settings.optString("forwardPrefix"))
        }
        if (settings.has("showNotifications")) {
            settingsRepository.setShowNotifications(settings.optBoolean("showNotifications"))
        }
        if (settings.has("landingScreen")) {
            val screen =
                runCatching { LandingScreen.valueOf(settings.optString("landingScreen")) }
                    .getOrDefault(LandingScreen.BACKUP)
            settingsRepository.setLandingScreen(screen)
        }
    }
}
