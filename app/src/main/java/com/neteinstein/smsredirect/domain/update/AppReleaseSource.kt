package com.neteinstein.smsredirect.domain.update

import com.neteinstein.smsredirect.domain.model.AppRelease

/** Where the latest published release is looked up (GitHub releases in the github build). */
interface AppReleaseSource {
    /** The latest published release. Throws on any network or parse failure. */
    suspend fun fetchLatestRelease(): AppRelease
}
