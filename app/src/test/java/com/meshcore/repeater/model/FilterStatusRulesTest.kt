package com.meshcore.repeater.model

import com.meshcore.repeater.service.QuerySpec
import com.meshcore.repeater.service.RepeaterProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterStatusRulesTest {
    @Test fun statusQueriesMatchFirmware() {
        assertEquals(listOf("filter"), FilterStatusRules.statusQueries(supportsBlacklist = true, supportsWhitelist = false))
        assertEquals(listOf("filter", "filter mode"), FilterStatusRules.statusQueries(true, true))
        assertEquals("Ein/Aus auch bei reiner Whitelist-Erkennung", listOf("filter", "filter mode"), FilterStatusRules.statusQueries(false, true))
        assertTrue(FilterStatusRules.statusQueries(false, false).isEmpty())
        assertFalse("kein 'filter status' in der Firmware", FilterStatusRules.statusQueries(true, true).contains("filter status"))
    }

    /** Ursache v1.16: die Hilfe ("filter on|off") setzte "Filter an", obwohl der Status unbekannt war. */
    @Test fun helpAndListRepliesAreNotStatus() {
        assertFalse(FilterStatusRules.shouldParseStatus("filter help"))
        assertFalse(FilterStatusRules.shouldParseStatus("filter wl help"))
        assertFalse(FilterStatusRules.shouldParseStatus("filter channel list"))
        assertFalse(FilterStatusRules.shouldParseStatus("get name"))
        assertFalse(FilterStatusRules.shouldParseStatus(null))
        for (c in listOf("filter", "filter mode", "filter on", "filter off", "filter mode whitelist", "filter dryrun on"))
            assertTrue(c, FilterStatusRules.shouldParseStatus(c))
    }

    @Test fun knownOnlyAfterAllRequiredReplies() {
        assertFalse(FilterStatusRules.isKnown(enabledKnown = false, modeKnown = false, supportsWhitelist = false))
        assertTrue(FilterStatusRules.isKnown(enabledKnown = true, modeKnown = false, supportsWhitelist = false))
        assertFalse(FilterStatusRules.isKnown(enabledKnown = true, modeKnown = false, supportsWhitelist = true))
        assertTrue(FilterStatusRules.isKnown(enabledKnown = true, modeKnown = true, supportsWhitelist = true))
    }

    /** Antworten laut FilterStat::formatSummary / Filter.cpp werden validiert und richtig gelesen. */
    @Test fun firmwareRepliesValidateAndParse() {
        val on = "Filter on: Blocked [ Hops: 0 | Rate: 0 | Channel: 3 | Hash: 0 | Malformed: 0 ]"
        assertTrue(QuerySpec.validatorFor("filter")(on))
        assertTrue(QuerySpec.validatorFor("filter mode")("Filter: mode whitelist"))
        assertFalse(QuerySpec.validatorFor("filter")("Filter: command error"))
        val s = RepeaterProtocol.parseFilterStats(on, FilterStats())
        assertTrue(s.filterEnabled); assertFalse(s.dryRun)
        val dry = RepeaterProtocol.parseFilterStats("$on (dry-run)", FilterStats())
        assertTrue(dry.filterEnabled); assertTrue(dry.dryRun)
        val ws = RepeaterProtocol.parseFilterStats("Filter: mode whitelist", s)
        assertEquals(FilterMode.WHITELIST, ws.activeMode)
    }
}
