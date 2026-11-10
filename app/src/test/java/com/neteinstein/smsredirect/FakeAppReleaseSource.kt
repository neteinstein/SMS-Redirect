package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.model.AppRelease
import com.neteinstein.smsredirect.domain.update.AppReleaseSource
import java.io.IOException

class FakeAppReleaseSource(
    var release: AppRelease? = null,
) : AppReleaseSource {
    var fetchCalls = 0
        private set

    override suspend fun fetchLatestRelease(): AppRelease {
        fetchCalls++
        return release ?: throw IOException("offline")
    }
}
