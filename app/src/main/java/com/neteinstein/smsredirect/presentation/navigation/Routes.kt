package com.neteinstein.smsredirect.presentation.navigation

object Routes {
    const val Splash = "splash"
    const val Home = "home"
    const val Settings = "settings"
    const val Rule = "rule"
    const val RuleIdArg = "ruleId"
    const val RuleWithArg = "$Rule?$RuleIdArg={$RuleIdArg}"

    fun ruleRoute(ruleId: Long? = null): String = if (ruleId == null) Rule else "$Rule?$RuleIdArg=$ruleId"
}
