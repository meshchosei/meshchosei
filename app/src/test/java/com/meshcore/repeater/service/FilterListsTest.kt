package com.meshcore.repeater.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterListsTest {
    // Referenz: ZephCore Filter::getChannelHash (SHA256(SHA256(name)[0..15])[0], "Public" mit festem Secret)
    @Test fun channelHashMatchesFirmware() {
        assertEquals("11", ChannelHash.hashHex("Public"))
        assertEquals("D9", ChannelHash.hashHex("#test"))
        assertEquals("F6", ChannelHash.hashHex("#mesh-de"))
    }

    @Test fun parsesBlacklistChannelsIncludingPublic() {
        assertEquals(listOf("#test", "Public", "#bot"), FilterListParser.parseBlacklistChannels("#test (d9),Public (11),#bot (ca)"))
        assertTrue(FilterListParser.parseBlacklistChannels("None").isEmpty())
    }

    @Test fun parsesWhitelistHashesAndPaths() {
        assertEquals(listOf("D9", "11"), FilterListParser.parseWhitelistChannelHashes("d9,11"))
        assertEquals(listOf("A1B2", "C3"), FilterListParser.parsePaths("a1b2: 4,c3: 0.."))
    }

    @Test fun listValidatorsRejectForeignReplies() {
        assertTrue(FilterListParser.isValidListReply("filter wl channel list", "d9,11"))
        assertTrue(FilterListParser.isValidListReply("filter wl channel list", "None"))
        assertFalse(FilterListParser.isValidListReply("filter wl channel list", "Filter: mode whitelist"))
        assertFalse(FilterListParser.isValidListReply("filter wl channel list", "OK"))
        assertTrue(FilterListParser.isValidListReply("filter channel list", "#test (d9)"))
        assertFalse(FilterListParser.isValidListReply("filter channel list", "Filter: on"))
        assertTrue(FilterListParser.isValidListReply("filter path list", "a1b2,c3d4"))
        assertFalse(FilterListParser.isValidListReply("filter path list", "Filter: off"))
    }
}
