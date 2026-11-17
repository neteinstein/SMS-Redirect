package com.neteinstein.smsredirect.domain.model

/**
 * Which features this build ships. The two distributions share one codebase, one
 * `applicationId` and one signing key, and differ only in the self-update and in how the tools
 * get SMS access:
 *
 * - **play** (Google Play): every feature, no self-update (Play forbids it). SMS permissions come
 *   after the default SMS role is offered (Play's "Default SMS handler" policy).
 * - **github** (APK attached to each GitHub release): every feature, plus an in-app update check
 *   against GitHub releases, shown as the Updates section in Settings. Automate, Schedule and
 *   Backup ask for their SMS permissions directly, without offering the role first.
 *
 * Messages, Backup and Restore are in every build. The instance comes from the flavor's
 * `DistributionModule`; nothing else should read `BuildConfig.FLAVOR` to decide behaviour.
 */
data class AppFeatures(
    val forwarding: Boolean,
    val scheduling: Boolean,
    val selfUpdate: Boolean,
    /**
     * Whether Automate, Schedule and Backup ask for their SMS permissions directly, without
     * offering the default SMS role first. Never in the Play build: an app declaring the
     * "Default SMS handler" use case must hold the role before asking for SMS permissions.
     */
    val smsPermissionsWithoutRole: Boolean,
) {
    /** Whether Redirect or Schedule exist, i.e. whether their settings are shown. */
    val sendsSms: Boolean get() = forwarding || scheduling

    /** The landing screen to actually open, falling back to Messages when [requested] is not shipped. */
    fun resolveLandingScreen(requested: LandingScreen): LandingScreen =
        when (requested) {
            LandingScreen.MESSAGES, LandingScreen.BACKUP, LandingScreen.RESTORE -> requested
            LandingScreen.REDIRECT -> if (forwarding) requested else LandingScreen.MESSAGES
            LandingScreen.SCHEDULE -> if (scheduling) requested else LandingScreen.MESSAGES
        }

    /** The landing screens a user can pick from, in tab order. */
    val availableLandingScreens: List<LandingScreen>
        get() = LandingScreen.entries.filter { resolveLandingScreen(it) == it }

    companion object {
        val ALL = AppFeatures(forwarding = true, scheduling = true, selfUpdate = true, smsPermissionsWithoutRole = true)
        val PLAY = ALL.copy(selfUpdate = false, smsPermissionsWithoutRole = false)
    }
}
