package com.meshcore.repeater.ui

import com.meshcore.repeater.service.CliAutocomplete
import com.meshcore.repeater.service.ManualCliRetry
import com.meshcore.repeater.ui.screens.cliAutocompleteEntries
import com.meshcore.repeater.ui.screens.cliHelpCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.21: CLI-Befehle der Community-Forks jhuebert/MeshCore und Sweden-Mesh LOU, ohne Duplikate, mit Herkunftsvermerk. */
class ForkCliCommandsTest {
    private val all = cliHelpCategories.flatMap { it.commands }
    private val forkCats = cliHelpCategories.filter { it.title.contains("jhuebert") || it.title.contains("Sweden-Mesh") }
    private val syntaxes = all.map { it.syntax }

    @Test fun noDuplicateCommandsInHelp() {
        val dupSyntax = syntaxes.groupBy { it.lowercase() }.filter { it.value.size > 1 }.keys
        assertTrue("Doppelte Syntax: $dupSyntax", dupSyntax.isEmpty())
        // Autocomplete übernimmt den Befehl ohne Platzhalter: auch dort keine zwei gleichen Einträge
        assertEquals(all.size, cliAutocompleteEntries.distinctBy { it.insertText.lowercase() }.size)
    }

    @Test fun forkInsertTextMatchesSyntax() {
        for (c in forkCats.flatMap { it.commands }) assertEquals(c.syntax, CliAutocomplete.insertTextFor(c.syntax), c.insertText)
    }

    @Test fun forkCategoriesExistAndAreMarked() {
        assertEquals(3, forkCats.size)
        for (cat in forkCats) for (c in cat.commands) {
            val origin = if (cat.title.contains("Sweden-Mesh")) "(Sweden-Mesh)" else "(jhuebert"
            assertTrue("Herkunft fehlt: ${c.syntax}", c.description.contains(origin))
        }
        // Stock-Kategorien tragen keinen Fork-Vermerk
        val stock = cliHelpCategories - forkCats.toSet()
        assertFalse(stock.flatMap { it.commands }.any { it.description.contains("(jhuebert") || it.description.contains("(Sweden-Mesh)") })
    }

    @Test fun jhuebertCommandsPresent() {
        for (c in listOf("filter add ", "filter list ", "filter get ", "filter move ", "filter chan add ", "filter ratelimit advert ",
                "battery", "battery off", "get radio.rxps", "set radio.rxps ", "get cpu.freq", "set cpu.freq ",
                "radio2", "radio2 off", "radio2 policy ", "ham", "ham callsign ", "ham tx ", "clock mesh", "clock mesh slots ",
                "fleet", "fleet tag add ", "fleet forget ", "bot", "bot chan ", "ota status", "ota apply", "ota abort", "ota rollback",
                "ota fetch start", "get wifi.status", "set wifi.ssid ", "get mqtt.status", "set mqtt1.server ", "mqtt recover"))
            assertTrue("$c fehlt", all.any { it.insertText == c })
    }

    @Test fun swedenMeshCommandsPresent() {
        for (c in listOf("get autoreply", "set autoreply ", "set autoreply.region ", "set autoreply.window ", "set autoreply.mode ",
                "trigger test ", "trigger private ", "get cmd", "set cmd ", "set cmd.pubkey ", "set cmd.mesh ", "cmd ping",
                "set relay ", "set alive.interval ", "get sensors", "ota check", "ota update", "set ota.auto "))
            assertTrue("$c fehlt", all.any { it.insertText == c })
    }

    @Test fun criticalForkCommandsNeverRetriedAutomatically() {
        for (c in listOf("ota apply", "ota abort", "ota rollback", "ota stage", "ota fetch start", "ota update", "filter clear",
                "filter add type=advert", "filter del 0", "filter move 1 0", "fleet forget all", "fleet chan clear", "mqtt recover",
                "trigger test", "set cmd.pubkey 00", "cmd ping"))
            assertFalse("$c darf nicht automatisch wiederholt werden", ManualCliRetry.isRetrySafe(c))
        for (c in listOf("ota status", "get autoreply", "radio2", "ham", "fleet", "get mqtt.status", "battery"))
            assertTrue("$c darf wiederholt werden", ManualCliRetry.isRetrySafe(c))
    }

    @Test fun dangerousAndSerialOnlyForkCommands() {
        val byInsert = cliAutocompleteEntries.associateBy { it.insertText.trim() }
        for (c in listOf("ota apply", "ota rollback", "ota update", "filter clear", "set cmd.pubkey")) assertTrue("$c ohne ⚠", byInsert.getValue(c).dangerous)
        assertFalse(byInsert.getValue("ota status").dangerous)
        val debug = byInsert.getValue("set cmd.debug")
        assertFalse(CliAutocomplete.isAvailable(debug, true, true, usbDirect = false))
        assertTrue(CliAutocomplete.isAvailable(debug, true, true, usbDirect = true))
    }

    @Test fun autocompleteKeepsTwoStageAlphabeticalOrderForForkCommands() {
        val r = CliAutocomplete.suggest("ota", cliAutocompleteEntries).map { it.insertText.trim() }
        val prefix = r.takeWhile { it.startsWith("ota") }
        assertEquals(prefix.sortedBy { it.lowercase() }, prefix)
        assertTrue(prefix.containsAll(listOf("ota abort", "ota apply", "ota check", "ota status")))
        val rest = r.drop(prefix.size)
        assertTrue(rest.none { it.startsWith("ota") })
        assertTrue(rest.containsAll(listOf("start ota", "stop ota", "get ota.auto")))
        assertEquals(rest.sortedBy { it.lowercase() }, rest)
    }
}
