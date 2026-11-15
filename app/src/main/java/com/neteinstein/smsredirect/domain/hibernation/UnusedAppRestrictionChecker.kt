package com.neteinstein.smsredirect.domain.hibernation

/** Whether Android may reset this app's permissions and free its space when it goes unused. */
enum class UnusedAppRestrictionStatus {
    /** "Manage app if unused" is on: months without opening the app can revoke permissions. */
    ENABLED,

    /** The user turned it off: the app is exempt and needs no reminder. */
    DISABLED,

    /** The platform would not say. Treated like [ENABLED], since a reminder is harmless. */
    UNKNOWN,
}

/** Keeps `PackageManager` out of the domain so the reminder rules are testable. */
interface UnusedAppRestrictionChecker {
    fun status(): UnusedAppRestrictionStatus
}
