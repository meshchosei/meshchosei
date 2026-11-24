package com.meshcore.repeater.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterMenuRulesTest {
    @Test
    fun activeModeIsNullWhenFilterOff() {
        assertNull(FilterStats(filterEnabled = false, mode = FilterMode.WHITELIST).activeMode)
        assertEquals(FilterMode.WHITELIST, FilterStats(filterEnabled = true, mode = FilterMode.WHITELIST).activeMode)
    }

    @Test
    fun blacklistModeEnablesOnlyBlacklist() {
        assertTrue(FilterMenuRules.blacklistEnabled(true, FilterMode.BLACKLIST))
        assertFalse(FilterMenuRules.whitelistEnabled(true, FilterMode.BLACKLIST))
    }

    @Test
    fun whitelistModeEnablesOnlyWhitelist() {
        assertFalse(FilterMenuRules.blacklistEnabled(true, FilterMode.WHITELIST))
        assertTrue(FilterMenuRules.whitelistEnabled(true, FilterMode.WHITELIST))
    }

    @Test
    fun filterOffEnablesAllSupported() {
        assertTrue(FilterMenuRules.blacklistEnabled(true, null))
        assertTrue(FilterMenuRules.whitelistEnabled(true, null))
        // Nicht unterstützte Funktionen bleiben immer gesperrt
        assertFalse(FilterMenuRules.whitelistEnabled(false, null))
        assertFalse(FilterMenuRules.blacklistEnabled(false, FilterMode.BLACKLIST))
    }

    @Test
    fun firmwareStatusLinesAreParsed() {
        // ZephCore FilterStats.h formatSummary / Filter.cpp (nach Entfernen von "> ")
        val on = com.meshcore.repeater.service.RepeaterProtocol.parseFilterStats("Filter on: Blocked [ Hops: 0 | Rate: 0 | Channel: 3 | Hash: 0 | Malformed: 0 ]", FilterStats())
        assertTrue(on.filterEnabled)
        val off = com.meshcore.repeater.service.RepeaterProtocol.parseFilterStats("Filter off: Blocked [ Hops: 0 | Rate: 0 | Channel: 0 | Hash: 0 | Malformed: 0 ]", on)
        assertFalse(off.filterEnabled)
        assertEquals(FilterMode.WHITELIST, com.meshcore.repeater.service.RepeaterProtocol.parseFilterStats("Filter: mode whitelist", FilterStats()).mode)
        assertEquals(FilterMode.BLACKLIST, com.meshcore.repeater.service.RepeaterProtocol.parseFilterStats("Filter: mode blacklist", FilterStats(mode = FilterMode.WHITELIST)).mode)
    }

    @Test
    fun parserNeverConfusesBlacklistAndWhitelist() {
        val p = com.meshcore.repeater.service.RepeaterProtocol
        val wl = FilterStats(mode = FilterMode.WHITELIST, filterEnabled = true)
        // Hilfetexte enthalten beide Wörter -> Modus darf sich NICHT ändern
        assertEquals(FilterMode.WHITELIST, p.parseFilterStats("filter mode [blacklist|whitelist]", wl).mode)
        assertEquals(FilterMode.WHITELIST, p.parseFilterStats("Filter: mode whitelist", FilterStats()).mode)
        assertEquals(FilterMode.BLACKLIST, p.parseFilterStats("> Filter: mode blacklist", wl).mode)
        // "Filter: off" schaltet nur aus, Modus bleibt
        val off = p.parseFilterStats("Filter: off", wl)
        assertFalse(off.filterEnabled); assertNull(off.activeMode); assertEquals(FilterMode.WHITELIST, off.mode)
        assertTrue(p.parseFilterStats("Filter on: Blocked [ Hops: 0 ] (dry-run)", FilterStats()).filterEnabled)
    }
}
