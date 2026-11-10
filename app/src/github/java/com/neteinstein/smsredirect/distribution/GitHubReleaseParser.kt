package com.neteinstein.smsredirect.distribution

import com.neteinstein.smsredirect.domain.model.AppRelease
import org.json.JSONObject

/** Parses the GitHub REST API's `GET /repos/{owner}/{repo}/releases/latest` response. */
object GitHubReleaseParser {
    /**
     * Only URLs under this repository's own GitHub pages are accepted, so a tampered or
     * unexpected response can not point the installer at an arbitrary host.
     */
    fun parse(
        json: String,
        repository: String,
    ): AppRelease {
        val release = JSONObject(json)
        val tag = release.getString("tag_name")
        val repoUrl = "https://github.com/$repository"
        val releaseUrl =
            release
                .optString("html_url")
                .takeIf { it.startsWith("$repoUrl/releases/") }
                ?: "$repoUrl/releases/tag/$tag"

        val downloadPrefix = "$repoUrl/releases/download/"
        val assets = release.optJSONArray("assets")
        val apkUrl =
            (0 until (assets?.length() ?: 0))
                .mapNotNull { assets?.optJSONObject(it) }
                .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
                .map { it.optString("browser_download_url") }
                .firstOrNull { it.startsWith(downloadPrefix) }

        return AppRelease(
            versionName = tag.removePrefix("v").removePrefix("V"),
            releaseUrl = releaseUrl,
            apkUrl = apkUrl,
            notes = release.optString("body").takeUnless { release.isNull("body") }.orEmpty(),
        )
    }
}
