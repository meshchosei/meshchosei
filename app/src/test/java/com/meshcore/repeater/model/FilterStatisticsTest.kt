package com.meshcore.repeater.model

import com.meshcore.repeater.service.QuerySpec
import com.meshcore.repeater.service.RepeaterProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.22: Karte „Filter-Statistiken & Treffer“. Antworten wörtlich aus ZephCore `app/FilterStats.h`
 * (formatSummary / formatStatsAir), `app/Filter.cpp` (FILTER_STATS_HELP) und MeshCore
 * `docs/packet_filter_reference.md`.
 */
class FilterStatisticsTest {
    private val start = FilterStats()

    private fun parse(reply: String, current: FilterStats = start) = RepeaterProtocol.parseFilterStats(reply, current)

    @Test fun summaryFillsAllCounters() {
        val s = parse("> Filter on: Blocked [ Hops: 326 | Rate: 0 | Channel: 0 | Hash: 8097 | Malformed: 0 ]")
        assertEquals(FilterCounters(hops = 326, rate = 0, channel = 0, hash = 8097, malformed = 0), s.counters)
        assertEquals(8423L, s.counters!!.total)
        assertTrue(s.filterEnabled)
        assertFalse(s.dryRun)
    }

    /** Echte Live-Antwort eines Repeaters auf "filter" (v1.22, vom Gerät). */
    @Test fun realLiveRepeaterReply() {
        val s = parse("> Filter on: Blocked [ Hops: 1943 | Rate: 6 | Channel: 981 | Hash: 0 | Malformed: 0 ]")
        assertEquals(FilterCounters(hops = 1943, rate = 6, channel = 981, hash = 0, malformed = 0), s.counters)
        assertEquals(2930L, s.counters!!.total)
        assertTrue(s.filterEnabled)
        assertFalse(s.dryRun)
        assertTrue(QuerySpec.validatorFor("filter")("Filter on: Blocked [ Hops: 1943 | Rate: 6 | Channel: 981 | Hash: 0 | Malformed: 0 ]"))
        // gleiche Zeile in beiden Ansichten: Whitelist-Modus behält die Zähler
        val wl = parse("> Filter on: Blocked [ Hops: 1943 | Rate: 6 | Channel: 981 | Hash: 0 | Malformed: 0 ]", start.copy(mode = FilterMode.WHITELIST))
        assertEquals(FilterMode.WHITELIST, wl.mode)
        assertEquals(s.counters, wl.counters)
    }

    @Test fun summaryWithEveryFieldNonZeroAndDryRun() {
        val s = parse("> Filter on: Blocked [ Hops: 3 | Rate: 12 | Channel: 1 | Hash: 0 | Malformed: 2 ] (dry-run)")
        val c = s.counters!!
        assertEquals(3L, c.hops); assertEquals(12L, c.rate); assertEquals(1L, c.channel); assertEquals(0L, c.hash); assertEquals(2L, c.malformed)
        assertEquals(18L, c.total)
        assertTrue(s.dryRun)
    }

    @Test fun summaryWhileFilterOffStillCounts() {
        val s = parse("> Filter off: Blocked [ Hops: 0 | Rate: 0 | Channel: 0 | Hash: 0 | Malformed: 0 ]", start.copy(filterEnabled = true))
        assertFalse(s.filterEnabled)
        assertEquals(0L, s.counters!!.total)
    }

    /** Über USB-Direkt kommen Antworten mit "-> " bzw. ohne ">" an; über LoRa mit ">" – beides gleich auswerten. */
    @Test fun summaryWithoutPromptAndLargeValues() {
        val s = parse("Filter on: Blocked [ Hops: 4294967295 | Rate: 7 | Channel: 65 | Hash: 1 | Malformed: 9 ]")
        assertEquals(4294967295L, s.counters!!.hops)
        assertEquals(65L, s.counters!!.channel)
    }

    @Test fun truncatedSummaryKeepsTheFieldsThatArrived() {
        val s = parse("> Filter on: Blocked [ Hops: 5 | Rate: 6 | Channel: 7 | Ha..")
        assertEquals(FilterCounters(hops = 5, rate = 6, channel = 7), s.counters)
    }

    @Test fun airtimeFromFilterStatsAir() {
        assertEquals(214500L, parse("> Filter: saved airtime 214500 ms (3m 34s)").savedAirtimeMs)
        assertEquals(812L, parse("> Filter: saved airtime 812 ms (0s)").savedAirtimeMs)
        assertEquals(7_384_000L, parse("> Filter: saved airtime 7384000 ms (2h 3m)").savedAirtimeMs)
        assertEquals(0L, parse("> Filter: no airtime saved yet").savedAirtimeMs)
    }

    /** "filter stats" ohne Thema ist nur Hilfe – darf keinen Zähler, Ein/Aus oder Modus verändern. */
    @Test fun filterStatsHelpChangesNothing() {
        val before = start.copy(filterEnabled = true, counters = FilterCounters(hops = 1), savedAirtimeMs = 5L)
        val s = parse("> filter stats [ hops | rate | channel | hash | malformed | top | advert | path | air | sender | text | age ]", before)
        assertEquals(before, s)
    }

    /** Andere Statistik-Themen ("Blocked 8097 [1B:…", "dropped 5") sind keine Zusammenfassung. */
    @Test fun otherStatsTopicsDoNotOverwriteCounters() {
        val before = start.copy(counters = FilterCounters(hash = 10))
        assertEquals(before, parse("> Blocked 8097 [1B:8000 2B:97 3B:0]", before))
        assertEquals(before, parse("> Blocked 4 [ short:1 time:2 empty:0 utf8:1 ]", before))
        assertEquals(before, parse("> Advert origins: window 2h, dropped 5, cache 3/32", before))
        assertEquals(before, parse("> Message age: max 60m, dropped 3", before))
        assertEquals(before, parse("> Filter: no hop drops recorded", before))
    }

    @Test fun modeReplyKeepsCounters() {
        val before = start.copy(counters = FilterCounters(hops = 2), savedAirtimeMs = 1000L)
        val s = parse("> Filter: mode whitelist", before)
        assertEquals(FilterMode.WHITELIST, s.mode)
        assertEquals(before.counters, s.counters)
        assertEquals(1000L, s.savedAirtimeMs)
    }

    @Test fun unreadValuesAreNull() {
        assertNull(start.counters)
        assertNull(start.savedAirtimeMs)
        assertEquals("—", FilterCounters.formatAirtime(null))
    }

    @Test fun airtimeFormatting() {
        assertEquals("0.0 s", FilterCounters.formatAirtime(0))
        assertEquals("12.5 s", FilterCounters.formatAirtime(12_500))
        assertEquals("3m 34s", FilterCounters.formatAirtime(214_500))
        assertEquals("2h 3m", FilterCounters.formatAirtime(7_384_000))
    }

    @Test fun statsQueriesForBothViews() {
        assertEquals(listOf("filter", "filter stats air"), FilterStatusRules.statsQueries(true, false, modeKnown = false))
        assertEquals(listOf("filter", "filter mode", "filter stats air"), FilterStatusRules.statsQueries(true, true, modeKnown = false))
        assertEquals(listOf("filter", "filter stats air"), FilterStatusRules.statsQueries(true, true, modeKnown = true))
        assertEquals(listOf("filter", "filter mode", "filter stats air"), FilterStatusRules.statsQueries(false, true, modeKnown = false))
        assertTrue(FilterStatusRules.statsQueries(false, false, modeKnown = false).isEmpty())
        assertTrue("Antworten auf 'filter stats air' werden ausgewertet", FilterStatusRules.shouldParseStatus("filter stats air"))
    }

    @Test fun validatorsAcceptRealRepliesOnly() {
        val air = QuerySpec.validatorFor("filter stats air")
        assertTrue(air("Filter: saved airtime 214500 ms (3m 34s)"))
        assertTrue(air("Filter: no airtime saved yet"))
        assertTrue("ältere Firmware ohne 'air' -> Hilfe, kein Warten bis Timeout",
            air("filter stats [ hops | rate | channel | hash | malformed | top ]"))
        assertFalse(air("Filter on: Blocked [ Hops: 1 | Rate: 0 | Channel: 0 | Hash: 0 | Malformed: 0 ]"))
        assertFalse(air("869.618,62.5,8,8"))
        val summary = QuerySpec.validatorFor("filter")
        assertTrue(summary("Filter on: Blocked [ Hops: 326 | Rate: 0 | Channel: 0 | Hash: 8097 | Malformed: 0 ]"))
        assertFalse(summary("Filter: saved airtime 214500 ms (3m 34s)"))
    }
}
