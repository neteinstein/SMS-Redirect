package com.neteinstein.smsredirect.domain.analytics

/**
 * Central catalog of analytics event names and parameter keys used across
 * the app. Keeping them here avoids typos and makes it easy to audit that no
 * personal data (phone numbers, contact names, message bodies) is ever sent.
 */
object AnalyticsEvents {
    const val RULE_CREATED = "rule_created"
    const val RULE_UPDATED = "rule_updated"
    const val RULE_DELETED = "rule_deleted"
    const val RULES_REORDERED = "rules_reordered"
    const val RULES_EXPORTED = "rules_exported"
    const val RULES_IMPORTED = "rules_imported"

    const val SMS_FORWARDED = "sms_forwarded"

    const val SCHEDULED_SMS_CREATED = "scheduled_sms_created"
    const val SCHEDULED_SMS_UPDATED = "scheduled_sms_updated"
    const val SCHEDULED_SMS_DELETED = "scheduled_sms_deleted"
    const val SCHEDULED_SMS_SENT = "scheduled_sms_sent"

    const val SETTING_CHANGED = "setting_changed"

    object Param {
        const val SUCCESS = "success"
        const val RULE_COUNT = "rule_count"
        const val REPEAT_TYPE = "repeat_type"
        const val SETTING_NAME = "setting_name"
        const val SETTING_VALUE = "setting_value"
    }
}
