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
 * Finds the web links in a message. Domain code (not `android.util.Patterns`) so the rules are
 * unit-tested; note that on a device `java.util.regex` is ICU-based, where `\s`, `\w` and `\b`
 * are Unicode-aware rather than ASCII.
 *
 * Three forms count: `http(s)://...`, `www....`, and a bare `domain.tld/...`. Scam messages
 * usually use the bare form (`ctt-pt.info/pay`). A bare domain needs a [KNOWN_TLDS] ending, so
 * that `e.g.`, `file.txt` or `3.5` are not taken for links. Every link opens as `http(s)`.
 *
 * The text is attacker-controlled (an MMS can carry hundreds of kilobytes), so the link pattern
 * only ever runs on one whitespace-free token of at most [MAX_TOKEN_LENGTH] characters: a long
 * `a.a.a.a...` run would otherwise exhaust the regex engine's backtracking stack. Longer tokens
 * are never links, and at most [MAX_LINKS] are returned.
 */
object MessageLinks {
    const val MAX_TOKEN_LENGTH = 512
    const val MAX_LINKS = 100

    /** A plain character-class loop: linear, no backtracking stack. */
    private val TOKEN = Regex("""\S+""")

    /** Zero-width and bidirectional controls, used to make a link read as something else. */
    private val INVISIBLE = Regex("[\u200B-\u200F\u202A-\u202E\u2060-\u2069\uFEFF]")

    private val LINK =
        Regex(
            """(?i)\bhttps?://[^\s<>"']+""" +
                """|\bwww\.[^\s<>"']+""" +
                """|(?<![@\w.-])(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+([a-z]{2,24})\b(?::\d{1,5})?(?:[/?#][^\s<>"']*)?""",
        )

    /** Dropped from the end of a link: sentence punctuation is almost never part of it. */
    private const val TRAILING = ".,;:!?)]}'\""

    private val OPENERS = mapOf(')' to '(', ']' to '[', '}' to '{')

    fun find(text: String): List<MessageLink> {
        val links = ArrayList<MessageLink>()
        for (token in TOKEN.findAll(text)) {
            if (token.value.length > MAX_TOKEN_LENGTH) continue
            for (match in LINK.findAll(token.value)) {
                val link = toLink(match, offset = token.range.first) ?: continue
                links += link
                if (links.size == MAX_LINKS) return links
            }
        }
        return links
    }

    private fun toLink(
        match: MatchResult,
        offset: Int,
    ): MessageLink? {
        val bareTld = match.groups[1]?.value
        if (bareTld != null && bareTld.lowercase() !in KNOWN_TLDS) return null
        val raw = trimTrailing(match.value)
        if (raw.isEmpty()) return null
        // Only `http(s)` ever opens: anything else (`com.app:1/x://y`) gets `https://` in front.
        val url =
            if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
                raw.substringBefore("://").lowercase() + "://" + raw.substringAfter("://")
            } else {
                "https://$raw"
            }
        val host = hostOf(url) ?: return null
        val start = offset + match.range.first
        return MessageLink(start, start + raw.length, raw, url, host)
    }

    /**
     * Drops sentence punctuation after a link, but keeps a closing bracket the link itself opened:
     * `.../Kotlin_(programming_language)` and `http://[2001:db8::1]` stay whole, `(see x.com)` loses
     * its `)`.
     */
    private fun trimTrailing(link: String): String {
        var end = link.length
        while (end > 0 && link[end - 1] in TRAILING) {
            val prefix = link.substring(0, end)
            val closer = link[end - 1]
            val opener = OPENERS[closer]
            if (opener != null && prefix.count { it == opener } >= prefix.count { it == closer }) break
            end--
        }
        return link.substring(0, end)
    }

    /**
     * `https://user@Example.com:8080/x` -> `example.com`; null when there is no host. A backslash
     * ends the host, as it does in browsers: `https://evil.net\.bank.com` goes to `evil.net`.
     */
    fun hostOf(url: String): String? {
        val authority = authorityOf(url)
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
    fun hasUserInfo(url: String): Boolean = '@' in authorityOf(url)

    /**
     * True when the link is written to read as something other than where it goes: credentials
     * before the host, a backslash, percent-escapes or non-ASCII letters in the host (`pаypal.com`
     * with a Cyrillic `а`), or invisible characters in the text.
     */
    fun isObfuscated(link: MessageLink): Boolean =
        hasUserInfo(link.url) ||
            '\\' in link.url.substringAfter("://").substringBefore('/') ||
            link.host.any { it.code > 0x7F || it == '%' } ||
            INVISIBLE.containsMatchIn(link.text)

    private fun authorityOf(url: String): String =
        url
            .substringAfter("://")
            .substringBefore('/')
            .substringBefore('\\')
            .substringBefore('?')
            .substringBefore('#')

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
