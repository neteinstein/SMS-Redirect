package com.neteinstein.smsredirect.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionsTest {
    @Test
    fun `higher patch minor or major is newer`() {
        assertTrue(AppVersions.isNewer("2.0.11", "2.0.10"))
        assertTrue(AppVersions.isNewer("2.1.0", "2.0.99"))
        assertTrue(AppVersions.isNewer("3.0.0", "2.9.9"))
    }

    @Test
    fun `numeric not lexicographic comparison`() {
        assertTrue(AppVersions.isNewer("2.0.10", "2.0.9"))
        assertFalse(AppVersions.isNewer("2.0.9", "2.0.10"))
    }

    @Test
    fun `same or older version is not newer`() {
        assertFalse(AppVersions.isNewer("2.0.10", "2.0.10"))
        assertFalse(AppVersions.isNewer("2.0.9", "2.0.10"))
        assertFalse(AppVersions.isNewer("2.0", "2.0.0"))
    }

    @Test
    fun `tag prefix and suffixes are ignored`() {
        assertTrue(AppVersions.isNewer("v2.0.11", "2.0.10"))
        assertFalse(AppVersions.isNewer("v2.0.10-beta", "2.0.10"))
        assertEquals(listOf(2, 0, 11), AppVersions.parse("V2.0.11+build.5"))
    }

    @Test
    fun `malformed candidate is never newer`() {
        assertFalse(AppVersions.isNewer("latest", "2.0.10"))
        assertFalse(AppVersions.isNewer("", "2.0.10"))
        assertFalse(AppVersions.isNewer("2.x.1", "2.0.10"))
        assertNull(AppVersions.parse("2..1"))
    }
}
