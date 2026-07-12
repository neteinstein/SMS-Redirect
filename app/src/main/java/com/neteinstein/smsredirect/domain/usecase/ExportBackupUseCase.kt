package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.repository.RuleRepository
import com.neteinstein.smsredirect.domain.repository.ScheduledSmsRepository
import com.neteinstein.smsredirect.domain.repository.SettingsRepository
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exports the full app configuration (redirect rules, scheduled SMS rules and settings, including
 * the keep-history toggles) into a single JSON document that can later be restored with
 * [ImportBackupUseCase].
 */
class ExportBackupUseCase(
    private val ruleRepository: RuleRepository,
    private val scheduledSmsRepository: ScheduledSmsRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(): String {
        val root = JSONObject()
        root.put("version", BACKUP_VERSION)
        root.put("rules", buildRulesJson())
        root.put("scheduledSms", buildScheduledSmsJson())
        root.put("settings", buildSettingsJson())
        return root.toString(2)
    }

    private suspend fun buildRulesJson(): JSONArray {
        val jsonArray = JSONArray()
        for (rule in ruleRepository.getRulesSnapshot()) {
            val obj = JSONObject()
            obj.put("name", rule.name)
            obj.put("senderText", rule.senderText)
            obj.put("phoneNumber", rule.phoneNumber)
            obj.put("targetNumber", rule.targetNumber)
            obj.put("sortOrder", rule.sortOrder)
            obj.put("category", rule.category)
            jsonArray.put(obj)
        }
        return jsonArray
    }

    private suspend fun buildScheduledSmsJson(): JSONArray {
        val jsonArray = JSONArray()
        for (scheduledSms in scheduledSmsRepository.getScheduledSmsSnapshot()) {
            val obj = JSONObject()
            obj.put("name", scheduledSms.name)
            obj.put("category", scheduledSms.category)
            obj.put("contactNumber", scheduledSms.contactNumber)
            obj.put("message", scheduledSms.message)
            obj.put("dateEpochMs", scheduledSms.dateEpochMs)
            obj.put("repeatType", scheduledSms.repeatType.name)
            obj.put("stopAtEpochMs", scheduledSms.stopAtEpochMs ?: JSONObject.NULL)
            obj.put("sortOrder", scheduledSms.sortOrder)
            jsonArray.put(obj)
        }
        return jsonArray
    }

    private suspend fun buildSettingsJson(): JSONObject {
        val obj = JSONObject()
        obj.put("sendWhileRoaming", settingsRepository.getSendWhileRoaming())
        obj.put("keepHistory", settingsRepository.getKeepHistory())
        obj.put("keepScheduleHistory", settingsRepository.getKeepScheduleHistory())
        obj.put("defaultPrefix", settingsRepository.getDefaultPrefix())
        obj.put("forwardPrefix", settingsRepository.getForwardPrefix())
        obj.put("showNotifications", settingsRepository.getShowNotifications())
        return obj
    }

    companion object {
        private const val BACKUP_VERSION = 1
    }
}
