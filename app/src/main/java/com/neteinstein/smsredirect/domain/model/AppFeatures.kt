package com.neteinstein.smsredirect.domain.model

/**
 * Which features this build ships. The two distributions share one codebase, one
 * `applicationId` and one signing key, and differ only here:
 *
 * - **play** (Google Play): Backup and Restore only. It must not declare `SEND_SMS`, so
 *   forwarding and scheduling are absent, and Play forbids self-updates outside Play.
 * - **github** (APK attached to each GitHub release): every feature, plus an in-app update
 *   check against GitHub releases.
 *
 * The instance comes from the flavor's `DistributionModule`; nothing else should read
 * `BuildConfig.FLAVOR` to decide behaviour.
 */
data class AppFeatures(
    val forwarding: Boolean,
    val scheduling: Boolean,
    val selfUpdate: Boolean,
) {
    /** Whether any shipped feature sends SMS, i.e. whether `SEND_SMS` is declared and requested. */
    val sendsSms: Boolean get() = forwarding || scheduling

    /** The landing screen to actually open, falling back to Backup when [requested] is not shipped. */
    fun resolveLandingScreen(requested: LandingScreen): LandingScreen =
        when (requested) {
            LandingScreen.BACKUP -> LandingScreen.BACKUP
            LandingScreen.REDIRECT -> if (forwarding) requested else LandingScreen.BACKUP
            LandingScreen.SCHEDULE -> if (scheduling) requested else LandingScreen.BACKUP
        }

    /** The landing screens a user can pick from, in tab order. */
    val availableLandingScreens: List<LandingScreen>
        get() = LandingScreen.entries.filter { resolveLandingScreen(it) == it }

    companion object {
        val ALL = AppFeatures(forwarding = true, scheduling = true, selfUpdate = true)
        val BACKUP_ONLY = AppFeatures(forwarding = false, scheduling = false, selfUpdate = false)
    }
}
