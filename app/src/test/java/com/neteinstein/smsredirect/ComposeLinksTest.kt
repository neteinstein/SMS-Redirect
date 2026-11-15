package com.neteinstein.smsredirect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeLinksTest {
    @Test
    fun `the body of an opaque sms link is read`() {
        assertEquals("hello", composeLinkBody("+351912345678?body=hello"))
        assertEquals("Olá, até já", composeLinkBody("+351912345678?body=Ol%C3%A1%2C%20at%C3%A9%20j%C3%A1"))
    }

    @Test
    fun `an encoded ampersand stays part of the text and a plus stays a plus`() {
        assertEquals("a&b", composeLinkBody("123?body=a%26b&other=1"))
        assertEquals("call +351", composeLinkBody("123?body=call%20+351"))
    }

    @Test
    fun `links without a body have none`() {
        assertNull(composeLinkBody("+351912345678"))
        assertNull(composeLinkBody("+351912345678?subject=x"))
        assertNull(composeLinkBody(null))
    }

    @Test
    fun `attachments only this app can read are refused`() {
        val own = "com.neteinstein.smsredirect"
        assertFalse(isShareableAttachment("content", "mms", own))
        assertFalse(isShareableAttachment("content", "MMS", own))
        assertFalse(isShareableAttachment("content", "0@mms", own))
        assertFalse(isShareableAttachment("content", "10@media", own))
        assertFalse(isShareableAttachment("content", "$own.fileprovider", own))
        assertFalse(isShareableAttachment("file", "", own))
        assertTrue(isShareableAttachment("content", "media", own))
        assertTrue(isShareableAttachment("content", "com.google.android.apps.photos.contentprovider", own))
    }
}
