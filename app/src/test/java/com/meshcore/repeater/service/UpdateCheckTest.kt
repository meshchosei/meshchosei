package com.meshcore.repeater.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    @Test fun newerMinorIsNewer() {
        assertTrue(UpdateCheck.isNewer("1.20", "1.19"))
        assertTrue(UpdateCheck.isNewer("v1.20", "1.19"))
        assertTrue(UpdateCheck.isNewer("v2.00", "1.99"))
    }

    @Test fun sameVersionIsNotNewer() {
        assertEquals(0, UpdateCheck.compare("1.19", "1.19"))
        assertEquals(0, UpdateCheck.compare("v1.19", "1.19"))
        assertFalse(UpdateCheck.isNewer("v1.19", "1.19"))
    }

    @Test fun olderVersionIsNotNewer() {
        assertTrue(UpdateCheck.compare("1.18", "1.19")!! < 0)
        assertFalse(UpdateCheck.isNewer("v1.18", "1.19"))
    }

    @Test fun minorIsComparedNumerically() {
        assertTrue(UpdateCheck.isNewer("1.19", "1.2"))
        assertTrue(UpdateCheck.isNewer("1.10", "1.09"))
    }

    @Test fun invalidVersionsNeverTriggerHint() {
        assertNull(UpdateCheck.parseVersion("latest"))
        assertNull(UpdateCheck.parseVersion(""))
        assertNull(UpdateCheck.parseVersion(null))
        assertFalse(UpdateCheck.isNewer("abc", "1.19"))
        assertFalse(UpdateCheck.isNewer("1.20", "xyz"))
    }

    @Test fun parsesTagAndHtmlUrl() {
        val info = UpdateCheck.parseLatest("""{"tag_name":"v1.20","html_url":"https://github.com/meshchosei/meshchosei/releases/tag/v1.20","name":"x","assets":[]}""")!!
        assertEquals("v1.20", info.tagName)
        assertEquals("https://github.com/meshchosei/meshchosei/releases/tag/v1.20", info.htmlUrl)
        assertEquals("v1.20", info.displayVersion)
    }

    @Test fun missingHtmlUrlFallsBackToReleasesPage() {
        assertEquals(UpdateCheck.RELEASES_PAGE_URL, UpdateCheck.parseLatest("""{"tag_name":"v1.20"}""")!!.htmlUrl)
        assertEquals(UpdateCheck.RELEASES_PAGE_URL, UpdateCheck.parseLatest("""{"tag_name":"v1.20","html_url":"javascript:alert(1)"}""")!!.htmlUrl)
    }

    @Test fun invalidJsonIsIgnoredSilently() {
        assertNull(UpdateCheck.parseLatest("not json"))
        assertNull(UpdateCheck.parseLatest(""))
        assertNull(UpdateCheck.parseLatest(null))
        assertNull(UpdateCheck.parseLatest("""{"message":"API rate limit exceeded"}"""))
        assertNull(UpdateCheck.parseLatest("""{"tag_name":"nightly"}"""))
        assertNull(UpdateCheck.parseLatest("[1,2,3]"))
    }

    @Test fun newerReleaseOnlyWhenRealNewer() {
        val r = UpdateCheck.ReleaseInfo("v1.19", UpdateCheck.RELEASES_PAGE_URL)
        assertNull(UpdateCheck.newerRelease(r, "1.19"))
        assertNull(UpdateCheck.newerRelease(null, "1.19"))
        assertEquals(r, UpdateCheck.newerRelease(r, UpdateCheck.DEBUG_SIMULATED_VERSION))
    }
}
