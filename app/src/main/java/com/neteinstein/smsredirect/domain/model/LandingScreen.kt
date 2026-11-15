package com.neteinstein.smsredirect.domain.model

/**
 * Which tab the app opens on. User-chosen in Settings; declaration order is tab order.
 * Persisted by name, so renaming an entry would reset users' choice.
 */
enum class LandingScreen {
    MESSAGES,
    REDIRECT,
    SCHEDULE,
    BACKUP,
    RESTORE,
}
