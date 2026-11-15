package com.neteinstein.smsredirect.domain.model

/**
 * Which features this build ships. The two distributions share one codebase, one
 * `applicationId` and one signing key, and differ only in the self-update:
 *
 * - **play** (Google Play): every feature, no self-update (Play forbids it).
 * - **github** (APK attached to each GitHub release): every feature, plus an in-app update check
 *   against GitHub releases, shown as the Updates section in Settings.
 *
 * Messages, Backup and Restore are in every build. The instance comes from the flavor's
 * `DistributionModule`; nothing else should read `BuildConfig.FLAVOR` to decide behaviour.
 */
data class AppFeatures(
    val forwarding: Boolean,
    val scheduling: Boolean,
    val selfUpdate: Boolean,
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
        val ALL = AppFeatures(forwarding = true, scheduling = true, selfUpdate = true)
        val PLAY = ALL.copy(selfUpdate = false)
    }
}
