package com.neteinstein.smsredirect.presentation.navigation

object Routes {
    const val Splash = "splash"
    const val Permissions = "permissions"
    const val Home = "home"
    const val Settings = "settings"
    const val History = "history"
    const val Rule = "rule"
    const val RuleIdArg = "ruleId"
    const val RuleWithArg = "$Rule?$RuleIdArg={$RuleIdArg}"
    const val ScheduleHistory = "schedule_history"
    const val Schedule = "schedule"
    const val ScheduleIdArg = "scheduleId"
    const val ScheduleWithArg = "$Schedule?$ScheduleIdArg={$ScheduleIdArg}"

    const val DEEP_LINK_URI = "smsredirect://app"

    fun ruleRoute(ruleId: Long? = null): String = if (ruleId == null) Rule else "$Rule?$RuleIdArg=$ruleId"

    fun scheduleRoute(scheduleId: Long? = null): String = if (scheduleId == null) Schedule else "$Schedule?$ScheduleIdArg=$scheduleId"
}
