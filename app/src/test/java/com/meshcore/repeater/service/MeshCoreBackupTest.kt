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
}
