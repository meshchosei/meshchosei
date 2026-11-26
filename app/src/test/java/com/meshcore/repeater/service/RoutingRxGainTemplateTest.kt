package com.meshcore.repeater.service

import com.meshcore.repeater.ui.screens.RoutingLimits
import com.meshcore.repeater.ui.screens.cliAutocompleteEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.21: Hop-Limits, Timing-Delays, RX-Gain, jhuebert-Filter im Klappmenü und toleranter Template-Import. */
class RoutingRxGainTemplateTest {
    private fun ok(cmd: String, v: String) = QuerySpec.validatorFor(cmd)(v)

    @Test fun hopLimitsReadAndValidated() {
        assertTrue(ok("get flood.max.unscoped", "2"))
        assertTrue(ok("get flood.max.advert", "8"))
        assertTrue(ok("get flood.max", "64"))
        assertFalse(ok("get flood.max.unscoped", "on"))
        assertTrue(RoutingLimits.hopsOk("0")); assertTrue(RoutingLimits.hopsOk("2")); assertTrue(RoutingLimits.hopsOk("64"))
        assertFalse(RoutingLimits.hopsOk("65")); assertFalse(RoutingLimits.hopsOk("")); assertFalse(RoutingLimits.hopsOk("-1"))
    }

    @Test fun timingDelays() {
        assertTrue(RoutingLimits.delayOk("0.5", 2.0)); assertTrue(RoutingLimits.delayOk("2", 2.0))
        assertFalse(RoutingLimits.delayOk("2.5", 2.0))
        assertTrue(RoutingLimits.delayOk("12", 20.0)); assertFalse(RoutingLimits.delayOk("21", 20.0))
        assertFalse("ZephCore-adaptive ist nicht setzbar", RoutingLimits.delayOk("adaptive", 2.0))
        assertTrue(ok("get txdelay", "adaptive (est=1.0 flood=0.50)"))
    }

    @Test fun rxGainReadAsOnOff() {
        assertTrue(ok("get radio.rxgain", "on")); assertTrue(ok("get radio.rxgain", "off"))
        assertTrue(ok("get radio.fem.rxgain", "on"))
        assertFalse(ok("get radio.rxgain", "1.5"))
        val def = MeshCoreBackup.VARS.first { it.key == "radio.rxgain" }
        assertEquals(true, MeshCoreBackup.replyToJson(def, "on"))
        assertTrue(MeshCoreBackup.VARS.any { it.key == "radio.fem.rxgain" && it.type == MeshCoreBackup.VarType.BOOL })
    }

    @Test fun jhuebertFilterCommandsAlwaysInAutocomplete() {
        val byInsert = cliAutocompleteEntries.associateBy { it.insertText.trim() }
        for (c in CliAutocomplete.JHUEBERT_FILTER) {
            val e = byInsert[c] ?: error("$c fehlt in der CLI-Hilfe")
            assertTrue("$c ohne erkannten ZephCore-Filter ausgeblendet", CliAutocomplete.isAvailable(e, supportsBlacklist = false, supportsWhitelist = false, usbDirect = false))
        }
        // ZephCore-Befehle bleiben an die Filtererkennung gebunden
        assertFalse(CliAutocomplete.isAvailable(byInsert.getValue("filter channel add"), false, false, false))
        assertFalse(CliAutocomplete.isAvailable(byInsert.getValue("filter on"), false, false, false))
        val r = CliAutocomplete.suggest("filter a", cliAutocompleteEntries.filter { CliAutocomplete.isAvailable(it, false, false, false) }).map { it.insertText.trim() }
        assertTrue(r.contains("filter add"))
    }

    private val bsmeshTemplate = """
        {"vars": {"radio": {"freq": "869.618", "bw": "62.5", "sf": "8", "cr": "8"}, "tx": 14,
                  "flood.max.unscoped": 2, "flood.max.advert": 8, "txdelay": 0.6, "radio.rxgain": true,
                  "repeat": "on", "multi.acks": "1", "advert.interval": null, "comment": "BSmesh Vorlage", "Bad Key!": 1}}
    """.trimIndent()

    @Test fun templateWithoutRegionsAndFilterIsImported() {
        val p = MeshCoreBackup.parse(bsmeshTemplate)
        val cmds = p.commands(includePrivateKey = false, includeGuestPassword = false)
        assertTrue(cmds.contains("set radio 869.618,62.5,8,8"))
        assertTrue(cmds.contains("set flood.max.unscoped 2"))
        assertTrue(cmds.contains("set flood.max.advert 8"))
        assertTrue(cmds.contains("set radio.rxgain on"))
        assertTrue(cmds.contains("set repeat on"))
        assertTrue(cmds.contains("set txdelay 0.6"))
        // nicht im Template -> nie überschrieben
        assertFalse(cmds.any { it.startsWith("set name") || it.startsWith("set lat") || it.startsWith("set lon") || it.startsWith("set owner.info") })
        // null-Wert wird übergangen, ungültiger Schlüssel nie gesendet
        assertFalse(cmds.any { it.startsWith("set advert.interval") })
        assertFalse(cmds.any { it.contains("Bad Key") })
        assertTrue(p.regionCommands.isEmpty()); assertNull(p.filter)
        assertEquals("set tx 14", cmds.dropLast(1).last())          // Funk/TX am Ende
        assertEquals("set radio 869.618,62.5,8,8", cmds.last())
    }

    @Test fun radioAsStringOrNumbers() {
        val s = MeshCoreBackup.parse("""{"vars": {"radio": "869.618,62.5,8,8"}}""").commands(false, false)
        assertEquals(listOf("set radio 869.618,62.5,8,8"), s)
        val n = MeshCoreBackup.parse("""{"vars": {"radio": {"freq": 869.618, "bw": 62.5, "sf": 8, "cr": 8}}}""").commands(false, false)
        assertEquals(listOf("set radio 869.618,62.5,8,8"), n)
        val bad = MeshCoreBackup.parse("""{"vars": {"radio": "869.618,62.5"}}""")
        assertEquals(MeshCoreBackup.ItemState.INVALID, bad.vars.single().state)
    }

    @Test fun templateWithoutVarsWrapperAndRegionsAsArray() {
        val p = MeshCoreBackup.parse("""{"flood.max.unscoped": 3, "regions": [{"name": "bsmesh", "parent": null, "flood": true}]}""")
        assertTrue(p.commands(false, false).contains("set flood.max.unscoped 3"))
        assertTrue(p.regionCommands.contains("region put bsmesh"))
    }

    @Test fun diffSkipsValuesTheRepeaterAlreadyHas() {
        val cmds = listOf("set flood.max.unscoped 2", "set flood.max.advert 8", "set radio.rxgain on", "set txdelay 0.60",
            "region put bsmesh", "set radio 869.618,62.5,8,8")
        val current = mapOf("flood.max.unscoped" to "2", "flood.max.advert" to "16", "radio.rxgain" to "on", "txdelay" to "0.6",
            "radio" to "869.6180,62.50,8,8")
        val (send, skipped) = MeshCoreBackup.withoutUnchanged(cmds, current)
        assertEquals(listOf("set flood.max.advert 8", "region put bsmesh"), send)
        assertEquals(4, skipped)
        assertEquals(cmds to 0, MeshCoreBackup.withoutUnchanged(cmds, emptyMap()))
    }

    @Test fun invalidJsonStillRejected() {
        assertTrue(runCatching { MeshCoreBackup.parse("not json") }.isFailure)
        assertTrue(runCatching { MeshCoreBackup.parse("""{"foo": 1}""") }.isFailure)
    }
}
