package com.neteinstein.smsredirect.domain.model

/** A published release of the app, as offered by the update source (GitHub releases). */
data class AppRelease(
    /** Version name without any leading `v`, e.g. `2.0.11`. Compared against `BuildConfig.VERSION_NAME`. */
    val versionName: String,
    /** Human-readable release page, for the release notes. */
    val releaseUrl: String,
    /** Direct download of the installable APK, or null when the release carries none. */
    val apkUrl: String?,
    val notes: String,
)
