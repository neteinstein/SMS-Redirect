package com.neteinstein.smsredirect.distribution

import com.neteinstein.smsredirect.domain.model.AppRelease
import com.neteinstein.smsredirect.domain.update.AppReleaseSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads the latest release from the public GitHub API. Unauthenticated: nothing about the
 * user or device is sent beyond what any HTTPS request carries (IP address, User-Agent).
 */
class GitHubReleaseSource(
    private val repository: String,
) : AppReleaseSource {
    override suspend fun fetchLatestRelease(): AppRelease =
        withContext(Dispatchers.IO) {
            val connection = URL("https://api.github.com/repos/$repository/releases/latest").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                // GitHub rejects API requests without a User-Agent.
                connection.setRequestProperty("User-Agent", "SMS-Toolbox-Android")
                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK) throw IOException("GitHub releases returned HTTP $code")
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                GitHubReleaseParser.parse(body, repository)
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        const val TIMEOUT_MS = 15_000
    }
}
