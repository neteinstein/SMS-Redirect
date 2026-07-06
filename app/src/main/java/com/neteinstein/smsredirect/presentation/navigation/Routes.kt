package com.neteinstein.smsredirect.presentation.navigation

object Routes {
    const val Splash = "splash"
    const val Home = "home"
    const val Settings = "settings"
    const val History = "history"
    const val Rule = "rule"
    const val RuleIdArg = "ruleId"
    const val RuleWithArg = "$Rule?$RuleIdArg={$RuleIdArg}"

    const val DEEP_LINK_URI = "smsredirect://app"

    fun ruleRoute(ruleId: Long? = null): String = if (ruleId == null) Rule else "$Rule?$RuleIdArg=$ruleId"
}
