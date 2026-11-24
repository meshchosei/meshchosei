package com.meshcore.repeater.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionParserTest {
    // Format aus RegionMap::printChildRegions: Einrückung = Tiefe, "^" = Home, " F" = Flood erlaubt
    @Test fun parsesTreeWithParentsFloodAndHome() {
        val (list, wildcardFlood, home) = RegionParser.parseTree(listOf("* F", " de F", "  de-ni^ F", "   de-ni-bs", " nl F"))
        assertTrue(wildcardFlood)
        assertEquals(listOf("de", "de-ni", "de-ni-bs", "nl"), list.map { it.name })
        assertEquals(listOf("*", "de", "de-ni", "*"), list.map { it.parent })
        assertEquals(listOf(true, true, false, true), list.map { it.floodAllowed })
        assertEquals(listOf(1, 2, 3, 1), list.map { it.depth })
        assertEquals("de-ni", home)
    }

    @Test fun wildcardDeniedAndHashNamesStripped() {
        val (list, wildcardFlood, _) = RegionParser.parseTree(listOf("*", " #bsmesh F"))
        assertFalse(wildcardFlood)
        assertEquals("bsmesh", list.single().name)
    }

    @Test fun homeAndDefaultReplies() {
        assertEquals("bsmesh", RegionParser.parseHome("home is bsmesh"))
        assertEquals("bsmesh", RegionParser.parseHome("home is now #bsmesh"))
        assertNull(RegionParser.parseHome("home is *"))
        assertTrue(RegionParser.isHomeReply("home is *"))
        assertEquals("de-ni-bs", RegionParser.parseDefault("default scope is de-ni-bs"))
        assertNull(RegionParser.parseDefault("default scope is <null>"))
    }

    @Test fun nameValidationFollowsFirmware() {
        assertTrue(RegionParser.isValidName("de-ni-bs"))
        assertTrue(RegionParser.isValidName("#Braunschweig"))
        assertFalse(RegionParser.isValidName("de ni"))
        assertFalse(RegionParser.isValidName("*"))
        assertFalse(RegionParser.isValidName("a".repeat(31)))
        assertFalse(RegionParser.isValidName("de(ni)"))
    }
}
