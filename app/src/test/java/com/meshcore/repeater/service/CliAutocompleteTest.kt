package com.meshcore.repeater.service

import com.meshcore.repeater.service.CliAutocomplete.Entry
import com.meshcore.repeater.ui.screens.cliAutocompleteEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** CLI-Autocomplete (v1.18): Präfix-/Teilwort-Suche, Übernahmetext und Capability-Filter. */
class CliAutocompleteTest {

    private val all = cliAutocompleteEntries
    private fun available(bl: Boolean = true, wl: Boolean = true, usb: Boolean = true) =
        all.filter { CliAutocomplete.isAvailable(it, bl, wl, usb) }
    private fun suggest(input: String, entries: List<Entry> = available(), limit: Int = Int.MAX_VALUE) =
        CliAutocomplete.suggest(input, entries, limit).map { it.insertText.trim() }

    @Test fun insertTextDropsPlaceholdersAndAddsSpace() {
        assertEquals("set dutycycle ", CliAutocomplete.insertTextFor("set dutycycle <prozent>"))
        assertEquals("set radio ", CliAutocomplete.insertTextFor("set radio <freq>,<bw>,<sf>,<cr>"))
        assertEquals("tempradio ", CliAutocomplete.insertTextFor("tempradio <freq>,<bw>,<sf>,<cr>,<min>"))
        assertEquals("region home ", CliAutocomplete.insertTextFor("region home [<name>]"))
        assertEquals("get radio", CliAutocomplete.insertTextFor("get radio"))
        assertEquals("reboot", CliAutocomplete.insertTextFor("reboot"))
    }

    @Test fun prefixSearch() {
        val r = suggest("get r")
        assertTrue(r.containsAll(listOf("get radio", "get repeat", "get rxdelay", "get radio.rxgain")))
        assertTrue(r.all { it.startsWith("get r") })
    }

    @Test fun partialWordSearchFindsDutycycle() {
        val r = suggest("duty")
        assertTrue(r.containsAll(listOf("get dutycycle", "set dutycycle")))
        assertTrue(r.all { it.contains("duty") })
    }

    /** Prüft: erst alle Präfix-Treffer alphabetisch, danach alle Teilwort-Treffer alphabetisch. */
    private fun assertTwoStageOrder(q: String, r: List<String>) {
        val (prefix, rest) = r.partition { it.startsWith(q, ignoreCase = true) }
        assertEquals("Präfix-Treffer zuerst", prefix + rest, r)
        assertEquals(prefix.sortedBy { it.lowercase() }, prefix)
        assertEquals(rest.sortedBy { it.lowercase() }, rest)
    }

    @Test fun prefixMatchesFirstThenSubstringMatchesEachAlphabetical() {
        val v = suggest("ver")
        assertEquals("ver", v.first())
        assertTrue(v.containsAll(listOf("advert", "get advert.interval", "get bootloader.ver")))
        assertTwoStageOrder("ver", v)
        val r = suggest("radio")
        assertTrue(r.contains("get radio") && r.contains("set radio") && r.contains("tempradio"))
        assertTwoStageOrder("radio", r)
        val c = suggest("cl")
        assertEquals(listOf("clear stats", "clkreboot", "clock", "clock mesh"), c.take(4))
        assertTwoStageOrder("cl", c)
        assertEquals(listOf("get dutycycle", "get gps duty", "get rxduty", "set dutycycle", "set gps duty", "set rxduty"), suggest("duty"))
        val g = suggest("gps")
        assertEquals("gps", g.first())
        assertTrue(g.indexOf("gps sync") < g.indexOf("get gps"))
        assertTwoStageOrder("gps", g)
    }

    @Test fun caseInsensitiveAndNoDuplicates() {
        val r = suggest("GET N")
        assertTrue(r.contains("get name"))
        assertEquals(r.size, r.toSet().size)
        val dup = CliAutocomplete.suggest("x", listOf(Entry("x <a>", "x "), Entry("x <b>", "x ")))
        assertTrue(dup.size <= 1)
    }

    @Test fun emptyInputShowsNothing() {
        assertTrue(suggest("").isEmpty())
        assertTrue(suggest("   ").isEmpty())
        assertFalse(suggest("f").isEmpty())
    }

    @Test fun closesOnCompleteCommandOrParameters() {
        assertTrue("einziger Treffer = Eingabe", suggest("get name").isEmpty())
        assertTrue(suggest("clock sync").isEmpty())
        assertTrue(suggest("set dutycycle 10").isEmpty())
        assertFalse("übernommener Text nicht erneut", suggest("set dutycycle ").contains("set dutycycle"))
        assertTrue("filter setzt sich fort", suggest("filter").contains("filter on"))
    }

    @Test fun filterCommandsRequireCapabilities() {
        val none = available(bl = false, wl = false)
        // v1.21: nur die jhuebert-Regelbefehle bleiben ohne erkannten ZephCore-Filter sichtbar
        assertTrue(none.filter { it.insertText.startsWith("filter") }.all { it.insertText.trim() in CliAutocomplete.JHUEBERT_FILTER })
        val blOnly = available(bl = true, wl = false)
        assertTrue(blOnly.any { it.insertText == "filter on" })
        assertTrue(blOnly.none { it.insertText.startsWith("filter wl") || it.insertText.startsWith("filter mode") })
        assertTrue(available(bl = true, wl = true).any { it.insertText.startsWith("filter wl channel add") })
    }

    @Test fun privateKeyOnlyOverUsb() {
        assertFalse(suggest("prv", available(usb = false)).contains("get prv.key"))
        assertTrue(suggest("prv", available(usb = true)).contains("get prv.key"))
    }

    @Test fun dangerousCommandsMarked() {
        for (c in listOf("reboot", "erase", "start ota", "filter RESET")) assertTrue(c, all.first { it.insertText == c }.dangerous)
        assertFalse(all.first { it.insertText == "get radio" }.dangerous)
    }

    @Test fun powerManagementCommandsFromFirmware() {
        val r = suggest("pwrmgt")
        for (c in listOf("get pwrmgt.support", "get pwrmgt.source", "get pwrmgt.bootreason", "get pwrmgt.bootmv"))
            assertTrue("$c fehlt", r.contains(c))
        assertEquals(listOf("get pwrmgt.bootreason"), suggest("bootreason"))
    }

    @Test fun serialOnlyCommandsRequireUsb() {
        val remote = available(usb = false).map { it.insertText.trim() }
        for (c in listOf("get prv.key", "erase", "log", "stats-core", "stats-radio", "stats-packets", "set freq", "get acl"))
            assertFalse("$c remote sichtbar", remote.contains(c))
        // nur der reine Ausgabe-Befehl "log" ist seriell, Start/Stop/Löschen gehen auch remote
        assertTrue(remote.containsAll(listOf("log start", "log stop", "log erase", "get freq")))
        assertTrue(available(usb = true).any { it.insertText == "stats-core" })
    }

    @Test fun singleCharacterOpensWithManyMatches() {
        val r = suggest("g")
        assertTrue(r.size > 100)
        assertTwoStageOrder("g", r)
    }

    @Test fun everyEntryHasShortDescription() {
        assertTrue(all.all { it.description.isNotBlank() })
    }

    @Test fun newDangerousSystemCommandsMarked() {
        for (c in listOf("shutdown", "clkreboot")) assertTrue(c, all.first { it.insertText == c }.dangerous)
    }
}

