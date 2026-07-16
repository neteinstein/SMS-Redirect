package com.neteinstein.smsredirect.presentation.navigation

object Routes {
    const val Splash = "splash"
    const val Permissions = "permissions"
    const val PermissionsStandaloneArg = "standalone"
    const val PermissionsWithArg = "$Permissions?$PermissionsStandaloneArg={$PermissionsStandaloneArg}"
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

    /**
     * [standalone] is true when the Permissions screen is opened from Settings ("Validate
     * Permissions") to fix an optional permission after onboarding, rather than during the
     * initial onboarding flow. It shows a back button and skips the auto-advance-to-Home
     * behavior used during onboarding.
     */
    fun permissionsRoute(standalone: Boolean = false): String =
        if (!standalone) Permissions else "$Permissions?$PermissionsStandaloneArg=$standalone"
}
