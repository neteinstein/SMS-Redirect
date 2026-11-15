package com.neteinstein.smsredirect.domain.model

/**
 * Which features this build ships. The two distributions share one codebase, one
 * `applicationId` and one signing key, and differ only here:
 *
 * - **play** (Google Play): every feature except the self-update, which Play forbids. SMS
 *   permissions are only ever obtained by becoming the default SMS app, as Play's "Default SMS
 *   handler" declaration requires.
 * - **github** (APK attached to each GitHub release): every feature, plus an in-app update check
 *   against GitHub releases, and a fallback for users who decline the SMS role but still want
 *   Redirect/Schedule/Backup next to another messaging app.
 *
 * Messages, Backup and Restore are in every build. The instance comes from the flavor's
 * `DistributionModule`; nothing else should read `BuildConfig.FLAVOR` to decide behaviour.
 */
data class AppFeatures(
    val forwarding: Boolean,
    val scheduling: Boolean,
    val selfUpdate: Boolean,
    /**
     * Whether SMS permissions may be requested directly when the user declines the default SMS
     * role. Never in the Play build: there the role is always requested first and is the only
     * way SMS permissions are obtained.
     */
    val smsPermissionsWithoutDefaultRole: Boolean,
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
        val ALL = AppFeatures(forwarding = true, scheduling = true, selfUpdate = true, smsPermissionsWithoutDefaultRole = true)
        val PLAY = ALL.copy(selfUpdate = false, smsPermissionsWithoutDefaultRole = false)
    }
}
