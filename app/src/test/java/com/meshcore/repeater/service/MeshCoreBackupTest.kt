package com.meshcore.repeater.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshCoreBackupTest {
    @Test fun regionTreeFromFirmwareExport() {
        // Format von RegionMap::printChildRegions: Einrückung = Ebene, "^" = Home, " F" = Flood erlaubt
        val (list, home) = MeshCoreBackup.parseRegionTree(listOf("* F", " bsmesh^ F", "  harz", " other F"))
        assertEquals(4, list.size)
        assertEquals(MeshCoreBackup.RegionEntry("*", null, true), list[0])
        assertEquals(MeshCoreBackup.RegionEntry("bsmesh", "*", true), list[1])
        assertEquals(MeshCoreBackup.RegionEntry("harz", "bsmesh", false), list[2])
        assertEquals(MeshCoreBackup.RegionEntry("other", "*", true), list[3])
        assertEquals("bsmesh", home)
    }
    @Test fun placeholders() {
        assertTrue(MeshCoreBackup.isPlaceholder("XXX"))
        assertTrue(MeshCoreBackup.isPlaceholder("xxxxxxxx"))
        assertFalse(MeshCoreBackup.isPlaceholder("Max"))
        assertFalse(MeshCoreBackup.isPlaceholder(22))
    }
    @Test fun replyConversion() {
        val d = MeshCoreBackup.VARS.associateBy { it.key }
        assertEquals(true, MeshCoreBackup.replyToJson(d.getValue("repeat"), "on"))
        assertEquals(22, MeshCoreBackup.replyToJson(d.getValue("tx"), "22"))
        assertEquals(0.5, MeshCoreBackup.replyToJson(d.getValue("txdelay"), "0.5"))
        assertNull(MeshCoreBackup.replyToJson(d.getValue("txdelay"), "adaptive (est=0.3 flood=0.50)"))
        assertNull(MeshCoreBackup.replyToJson(d.getValue("agc.reset.interval"), "Removed - Automatic AGC reset is on"))
        assertEquals("1", MeshCoreBackup.replyToJson(d.getValue("multi.acks"), "1"))
    }

    // ---------------- "filter"-Block ----------------
    private val sample = MeshCoreBackup.FilterBackup(
        mode = "whitelist", dryrun = false,
        blChannels = listOf("#spam", "Public"), blPaths = listOf("A1B2"),
        senders = listOf(MeshCoreBackup.RuleEntry("bot"), MeshCoreBackup.RuleEntry("spammer", 30, 50)),
        texts = listOf(MeshCoreBackup.RuleEntry("crypto")), watch = listOf("#mesh-de"),
        wlChannels = listOf(MeshCoreBackup.WlChannelEntry("D9", "#test"), MeshCoreBackup.WlChannelEntry("11", "Public")),
        wlPaths = listOf("C3")
    )

    @Test fun filterExportImportRoundTrip() {
        val json = MeshCoreBackup.buildJson(linkedMapOf<String, Any>("name" to "Test"), null, null, null, sample)
        val plan = MeshCoreBackup.parse(json)
        assertEquals(sample, plan.filter)
        assertEquals(7, plan.filter!!.blacklistCount)
        assertEquals(3, plan.filter!!.whitelistCount)
        assertTrue(json.contains("\"filter\""))
    }

    @Test fun backupWithoutFilterStaysCompatible() {
        // offizieller MeshCore-/PEmesh-Export: nur "vars" (+ optional "regions")
        val plan = MeshCoreBackup.parse("""{"vars":{"name":"Rpt","tx":22},"regions":{"default":null,"home":null,"list":[]}}""")
        assertNull(plan.filter)
        assertEquals(listOf("set name Rpt", "region default <null>", "region save", "set tx 22"), plan.commands(false, false))
        assertFalse(MeshCoreBackup.buildJson(linkedMapOf<String, Any>("name" to "Rpt"), null, null, null).contains("\"filter\""))
    }

    @Test fun filterImportSanitizesEntries() {
        val f = MeshCoreBackup.parseFilter(org.json.JSONObject(
            """{"mode":"blacklist","blacklist":{"channels":["#ok","bad name","XXX"],"paths":["zz","a1"],"senders":[{"pattern":"x","secs":70000}]},"whitelist":{"channels":["#test","ab"]}}"""))!!
        assertEquals(listOf("#ok"), f.blChannels)
        assertEquals(listOf("A1"), f.blPaths)
        assertTrue(f.senders.isEmpty())
        assertEquals(listOf("D9", "AB"), f.wlChannels!!.map { it.hash })
        assertNull(MeshCoreBackup.parseFilter(org.json.JSONObject("""{"mode":"weird"}""")))
    }

    @Test fun filterCommandsSyncToBackupState() {
        val current = MeshCoreBackup.FilterBackup(
            mode = "off", blChannels = listOf("#spam", "#old"), senders = listOf(MeshCoreBackup.RuleEntry("spammer")),
            wlChannels = listOf(MeshCoreBackup.WlChannelEntry("EE")), wlPaths = emptyList()
        )
        val cmds = MeshCoreBackup.filterCommands(sample, current, supportsWhitelist = true)
        assertTrue("filter channel remove #old" in cmds)
        assertFalse(cmds.any { it == "filter channel add #spam" })   // schon vorhanden
        assertTrue("filter channel add Public" in cmds)
        assertTrue("filter path add A1B2" in cmds)
        assertTrue("filter sender add bot" in cmds)
        // geänderte Parameter: alte Regel entfernen, neu mit secs/prob anlegen
        assertTrue("filter sender remove spammer" in cmds)
        assertTrue("filter sender add spammer 30 50" in cmds)
        assertTrue("filter watch add #mesh-de" in cmds)
        assertTrue("filter wl channel del ee" in cmds)
        assertTrue("filter wl channel add d9" in cmds)
        assertTrue("filter wl path add C3" in cmds)
        assertEquals(listOf("filter dryrun off", "filter mode whitelist", "filter on"), cmds.takeLast(3))
    }

    @Test fun filterCommandsWithoutWhitelistFirmware() {
        // Standard-MeshCore (simple_repeater/Filter.cpp): kein "filter wl", kein "filter mode"
        val cmds = MeshCoreBackup.filterCommands(sample, MeshCoreBackup.FilterBackup("off"), supportsWhitelist = false)
        assertFalse(cmds.any { it.startsWith("filter wl") || it.startsWith("filter mode") })
        assertFalse("filter on" in cmds)   // Whitelist-Backup darf auf Blacklist-Firmware nicht die Blacklist scharf schalten
        val bl = MeshCoreBackup.filterCommands(sample.copy(mode = "blacklist"), null, supportsWhitelist = false)
        assertEquals("filter on", bl.last())
        assertEquals("filter off", MeshCoreBackup.filterCommands(MeshCoreBackup.FilterBackup("off"), null, false).single())
    }

    @Test fun exportFromStateHasNonEmptyVarsRegionsAndFilter() {
        // So baut createBackup() das JSON: aus dem frisch gelesenen Regionen-State + Filter-Snapshot
        val state = com.meshcore.repeater.model.RegionState(
            supported = true, wildcardFlood = true, home = "de-ni", default = "de-ni-bs",
            regions = listOf(
                com.meshcore.repeater.model.RegionInfo("de", "*", true, 1),
                com.meshcore.repeater.model.RegionInfo("de-ni", "de", true, 2),
                com.meshcore.repeater.model.RegionInfo("de-ni-bs", "de-ni", false, 3)
            )
        )
        val vars = linkedMapOf<String, Any>("name" to "Rpt", "tx" to 22)
        val json = MeshCoreBackup.buildJson(vars, MeshCoreBackup.regionEntries(state), state.home, state.default, sample)
        val root = org.json.JSONObject(json)

        assertTrue(root.getJSONObject("vars").length() > 0)
        val regions = root.getJSONObject("regions")
        assertEquals(4, regions.getJSONArray("list").length())          // "*" + 3 eigene
        assertEquals("de-ni", regions.getString("home"))
        assertEquals("de-ni-bs", regions.getString("default"))
        val filter = root.getJSONObject("filter")
        assertEquals("whitelist", filter.getString("mode"))
        assertTrue(filter.getJSONObject("blacklist").getJSONArray("channels").length() > 0)
        assertTrue(filter.getJSONObject("whitelist").getJSONArray("channels").length() > 0)

        // und zurück: der Import erzeugt die passenden Region-Befehle
        val plan = MeshCoreBackup.parse(json)
        assertTrue("region put de-ni de" in plan.regionCommands)
        assertTrue("region denyf de-ni-bs" in plan.regionCommands)
        assertTrue("region home de-ni" in plan.regionCommands)
        assertEquals("region save", plan.regionCommands.last())
        assertEquals(sample, plan.filter)
    }
}
