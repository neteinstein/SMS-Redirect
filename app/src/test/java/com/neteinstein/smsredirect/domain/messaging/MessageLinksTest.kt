package com.neteinstein.smsredirect.domain.messaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageLinksTest {
    private fun urls(text: String) = MessageLinks.find(text).map { it.url }

    @Test
    fun `finds links with a scheme, with www and bare domains`() {
        assertEquals(
            listOf("https://example.com/a?b=1", "https://www.ctt.pt", "https://ctt-pt.info/pay"),
            urls("Go to https://example.com/a?b=1 or www.ctt.pt, then ctt-pt.info/pay"),
        )
    }

    @Test
    fun `keeps http links as they are`() {
        assertEquals(listOf("http://192.168.1.10/login"), urls("Open http://192.168.1.10/login now"))
    }

    @Test
    fun `positions point at the link as written, without trailing punctuation`() {
        val text = "See bit.ly/abc."
        val link = MessageLinks.find(text).single()
        assertEquals("bit.ly/abc", text.substring(link.start, link.end))
        assertEquals("bit.ly/abc", link.text)
        assertEquals("bit.ly", link.host)
    }

    @Test
    fun `ignores abbreviations, file names, decimals and email addresses`() {
        assertEquals(emptyList<String>(), urls("e.g. the file.txt costs 3.5 euros, mail ana@example.com"))
    }

    @Test
    fun `a bare word ending in an unknown tld is not a link`() {
        assertEquals(emptyList<String>(), urls("Hello.World and report.final"))
    }

    @Test
    fun `host drops credentials, port and case`() {
        assertEquals("evil.net", MessageLinks.hostOf("https://bank.com@Evil.NET:8443/x"))
        assertEquals("[::1]", MessageLinks.hostOf("http://[::1]:80/"))
    }

    @Test
    fun `detects credentials hidden before the host`() {
        assertTrue(MessageLinks.hasUserInfo("https://bank.com@evil.net/login"))
        assertFalse(MessageLinks.hasUserInfo("https://example.com/path@user"))
    }

    @Test
    fun `a huge dotted run does not exhaust the regex stack`() {
        // 100k characters of `a.a.a...`: on one regex pass this overflows the backtracking stack.
        val attack = "a.".repeat(50_000) + "1"
        assertEquals(emptyList<String>(), urls(attack))
        assertEquals(listOf("https://x.com"), urls("$attack x.com"))
    }

    @Test
    fun `tokens longer than the limit are never links`() {
        val long = "https://example.com/" + "a".repeat(MessageLinks.MAX_TOKEN_LENGTH)
        assertEquals(emptyList<String>(), urls(long))
    }

    @Test
    fun `at most MAX_LINKS links are returned`() {
        assertEquals(MessageLinks.MAX_LINKS, MessageLinks.find("x.co ".repeat(500)).size)
    }

    @Test
    fun `every link opens as http or https`() {
        assertEquals(listOf("https://com.example.app:1/x://y"), urls("tap com.example.app:1/x://y"))
        assertEquals(listOf("https://Example.com/A"), urls("HTTPS://Example.com/A"))
    }

    @Test
    fun `a backslash ends the host as in browsers`() {
        assertEquals("evil.net", MessageLinks.hostOf("https://evil.net\\.bank.com/x"))
    }

    @Test
    fun `disguised links are obfuscated`() {
        fun obfuscated(text: String) = MessageLinks.isObfuscated(MessageLinks.find(text).single())
        assertTrue(obfuscated("https://p\u0430ypal.com/login"))
        assertTrue(obfuscated("https://bit%2Ely/x"))
        assertTrue(obfuscated("https://evil.net\\.bank.com/x"))
        assertTrue(obfuscated("https://bank.com\u202Eevil.net"))
        assertFalse(obfuscated("https://www.paypal.com/login"))
    }

    @Test
    fun `no links in plain text`() {
        assertEquals(emptyList<String>(), urls("Olá! Chego às 19h, até já."))
    }
}
