package com.neteinstein.smsredirect.domain.messaging

/** A web link found in a message body. */
data class MessageLink(
    /** Where the link starts in the text (inclusive). */
    val start: Int,
    /** Where the link ends in the text (exclusive). */
    val end: Int,
    /** The link as written, e.g. `bit.ly/x`. */
    val text: String,
    /** The link with a scheme, ready to open: `https://bit.ly/x`. */
    val url: String,
    /** Lower-case host without credentials or port, e.g. `bit.ly`. */
    val host: String,
)

/**
 * Finds the web links in a message. Pure Kotlin (not `android.util.Patterns`) so it behaves the
 * same in unit tests and on every device.
 *
 * Three forms count: `http(s)://...`, `www....`, and a bare `domain.tld/...`. Scam messages
 * usually use the bare form (`ctt-pt.info/pay`). A bare domain needs a [KNOWN_TLDS] ending, so
 * that `e.g.`, `file.txt` or `3.5` are not taken for links.
 */
object MessageLinks {
    private val LINK =
        Regex(
            """(?i)\bhttps?://[^\s<>"']+""" +
                """|\bwww\.[^\s<>"']+""" +
                """|(?<![@\w.-])(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+([a-z]{2,24})\b(?::\d{1,5})?(?:/[^\s<>"']*)?""",
        )

    /** Dropped from the end of a link: sentence punctuation is almost never part of it. */
    private const val TRAILING = ".,;:!?)]}'\""

    fun find(text: String): List<MessageLink> =
        LINK
            .findAll(text)
            .mapNotNull { match ->
                val bareTld = match.groups[1]?.value
                if (bareTld != null && bareTld.lowercase() !in KNOWN_TLDS) return@mapNotNull null
                val raw = match.value.trimEnd { it in TRAILING }
                if (raw.isEmpty()) return@mapNotNull null
                val url = if (raw.contains("://")) raw else "https://$raw"
                val host = hostOf(url) ?: return@mapNotNull null
                MessageLink(match.range.first, match.range.first + raw.length, raw, url, host)
            }.toList()

    /** `https://user@Example.com:8080/x` -> `example.com`; null when there is no host. */
    fun hostOf(url: String): String? {
        val authority =
            url
                .substringAfter("://")
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('#')
        val hostAndPort = authority.substringAfterLast('@')
        val host =
            if (hostAndPort.startsWith("[")) {
                hostAndPort.substringBefore(']') + "]"
            } else {
                hostAndPort.substringBefore(':')
            }
        return host.lowercase().trimEnd('.').ifEmpty { null }
    }

    /** True when the link hides credentials before the host, as in `https://bank.com@evil.net`. */
    fun hasUserInfo(url: String): Boolean = '@' in url.substringAfter("://").substringBefore('/')

    /**
     * Endings accepted for a bare `domain.tld`: the generic ones, every TLD in
     * [SpamFraudDetector.RISKY_TLDS], and the country codes seen in links in practice.
     */
    val KNOWN_TLDS: Set<String> =
        setOf(
            "com",
            "net",
            "org",
            "info",
            "biz",
            "edu",
            "gov",
            "int",
            "mil",
            "app",
            "dev",
            "io",
            "ai",
            "co",
            "me",
            "ly",
            "gl",
            "gd",
            "to",
            "cc",
            "tv",
            "ws",
            "su",
            "eu",
            "online",
            "site",
            "xyz",
            "top",
            "club",
            "live",
            "icu",
            "buzz",
            "click",
            "link",
            "shop",
            "store",
            "work",
            "rest",
            "cyou",
            "sbs",
            "cfd",
            "bond",
            "monster",
            "quest",
            "mom",
            "lol",
            "tk",
            "ml",
            "ga",
            "cf",
            "gq",
            "win",
            "vip",
            "loan",
            "pro",
            "news",
            "page",
            "help",
            "support",
            "services",
            "digital",
            "website",
            "space",
            "pt",
            "br",
            "uk",
            "us",
            "de",
            "fr",
            "es",
            "it",
            "nl",
            "be",
            "ch",
            "at",
            "ie",
            "ca",
            "au",
            "nz",
            "in",
            "jp",
            "kr",
            "cn",
            "ru",
            "mx",
            "ar",
            "cl",
            "pe",
            "ao",
            "mz",
            "cv",
            "pl",
            "se",
            "no",
            "dk",
            "fi",
            "cz",
            "gr",
            "ro",
            "hu",
            "tr",
            "za",
            "ng",
            "ke",
            "sg",
            "hk",
            "tw",
            "id",
            "ph",
            "vn",
            "th",
            "my",
            "ae",
            "sa",
            "il",
            "ua",
        )
}
