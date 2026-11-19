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
    fun `no links in plain text`() {
        assertEquals(emptyList<String>(), urls("Olá! Chego às 19h, até já."))
    }
}
