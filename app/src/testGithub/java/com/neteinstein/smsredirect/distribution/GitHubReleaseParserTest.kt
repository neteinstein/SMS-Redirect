package com.neteinstein.smsredirect.distribution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubReleaseParserTest {
    private val repo = "neteinstein/SMS-Redirect"

    @Test
    fun `parses tag notes release page and apk asset`() {
        val json =
            """
            {
              "tag_name": "v2.0.11",
              "html_url": "https://github.com/$repo/releases/tag/v2.0.11",
              "body": "What changed",
              "assets": [
                {"name": "notes.txt", "browser_download_url": "https://github.com/$repo/releases/download/v2.0.11/notes.txt"},
                {"name": "SMS-Toolbox_v2.0.11.apk", "browser_download_url": "https://github.com/$repo/releases/download/v2.0.11/SMS-Toolbox_v2.0.11.apk"}
              ]
            }
            """.trimIndent()

        val release = GitHubReleaseParser.parse(json, repo)

        assertEquals("2.0.11", release.versionName)
        assertEquals("https://github.com/$repo/releases/tag/v2.0.11", release.releaseUrl)
        assertEquals("https://github.com/$repo/releases/download/v2.0.11/SMS-Toolbox_v2.0.11.apk", release.apkUrl)
        assertEquals("What changed", release.notes)
    }

    @Test
    fun `apk hosted outside this repository's releases is ignored`() {
        val json =
            """
            {
              "tag_name": "v2.0.11",
              "html_url": "https://evil.example/releases/tag/v2.0.11",
              "body": null,
              "assets": [
                {"name": "SMS-Toolbox.apk", "browser_download_url": "https://evil.example/SMS-Toolbox.apk"}
              ]
            }
            """.trimIndent()

        val release = GitHubReleaseParser.parse(json, repo)

        assertNull(release.apkUrl)
        assertEquals("https://github.com/$repo/releases/tag/v2.0.11", release.releaseUrl)
        assertEquals("", release.notes)
    }

    @Test
    fun `release without assets has no apk`() {
        val release = GitHubReleaseParser.parse("""{"tag_name": "2.1.0"}""", repo)

        assertEquals("2.1.0", release.versionName)
        assertNull(release.apkUrl)
    }
}
