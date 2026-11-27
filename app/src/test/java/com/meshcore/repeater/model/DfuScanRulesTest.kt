package com.meshcore.repeater.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DfuScanRulesTest {
    @Test fun classifyKnownDfuAdvertisements() {
        assertEquals(DfuKind.SECURE, DfuScanRules.classify("DfuTarg", emptyList()))
        assertEquals(DfuKind.SECURE, DfuScanRules.classify(null, listOf("0000FE59-0000-1000-8000-00805F9B34FB")))
        assertEquals(DfuKind.LEGACY_BOOTLOADER, DfuScanRules.classify("AdaDFU", emptyList()))
        assertEquals(DfuKind.LEGACY_BOOTLOADER, DfuScanRules.classify(null, listOf(DfuScanRules.LEGACY_DFU_SERVICE)))
        // MeshCore nach "start ota": Name <BOARD>_OTA, Advert ohne Dienst-UUID
        assertEquals(DfuKind.APP_BUTTONLESS, DfuScanRules.classify("RAK4631_OTA", emptyList()))
        assertEquals(DfuKind.APP_BUTTONLESS, DfuScanRules.classify("T114_OTA", listOf(DfuScanRules.LEGACY_DFU_SERVICE)))
    }

    @Test fun normalNodesAreNotDfuTargets() {
        assertNull(DfuScanRules.classify("MeshCore-Le Chemin", listOf("6e400001-b5a3-f393-e0a9-e50e24dcca9e")))
        assertNull(DfuScanRules.classify(null, emptyList()))
        assertNull(DfuScanRules.classify("OTA Repeater", emptyList()))
    }

    @Test fun parseMeshCoreReply() {
        val r = DfuScanRules.parseOtaReply("OK - mac: C2:1F:3A:44:90:0B")
        assertTrue(r.ok); assertEquals("C2:1F:3A:44:90:0B", r.mac); assertNull(r.name)
    }

    @Test fun parseZephCoreReplyAndErrors() {
        val z = DfuScanRules.parseOtaReply("> OK - rebooting to BLE DFU (name: Le Chemin)")
        assertTrue(z.ok); assertEquals("Le Chemin", z.name); assertNull(z.mac)
        val u = DfuScanRules.parseOtaReply("Error: BLE OTA not supported on this platform")
        assertFalse(u.ok); assertTrue(u.unsupported)
        assertFalse(DfuScanRules.parseOtaReply("Error").ok)
        assertFalse(DfuScanRules.parseOtaReply(null).ok)
    }

    @Test fun nextAddressCarriesOver() {
        assertEquals("C2:1F:3A:44:90:0C", DfuScanRules.nextAddress("C2:1F:3A:44:90:0B"))
        assertEquals("C2:1F:3A:44:91:00", DfuScanRules.nextAddress("C2:1F:3A:44:90:FF"))
        assertNull(DfuScanRules.nextAddress("nope"))
    }

    private val a = DfuTarget("C2:1F:3A:44:90:0B", "RAK4631_OTA", -70, DfuKind.APP_BUTTONLESS)
    private val b = DfuTarget("11:22:33:44:55:66", "AdaDFU", -50, DfuKind.LEGACY_BOOTLOADER)

    @Test fun pickByMacThenNameThenSingleNewDevice() {
        val mac = DfuScanRules.parseOtaReply("OK - mac: c2:1f:3a:44:90:0b")
        assertEquals(a, DfuScanRules.pickAfterStartOta(listOf(b, a), mac))
        val secure = DfuTarget("C2:1F:3A:44:90:0C", "DfuTarg", -60, DfuKind.SECURE)
        assertEquals(secure, DfuScanRules.pickAfterStartOta(listOf(b, secure), mac))
        val named = DfuScanRules.parseOtaReply("OK - rebooting to BLE DFU (name: AdaDFU)")
        assertEquals(b, DfuScanRules.pickAfterStartOta(listOf(a, b), named))
        val none = DfuScanRules.parseOtaReply(null)
        assertEquals(b, DfuScanRules.pickAfterStartOta(listOf(a, b), none, knownBefore = setOf(a.address)))
    }

    @Test fun ambiguousResultIsNeverAutoPicked() {
        val none = DfuScanRules.parseOtaReply(null)
        assertNull(DfuScanRules.pickAfterStartOta(listOf(a, b), none))
        assertNull(DfuScanRules.pickAfterStartOta(emptyList(), DfuScanRules.parseOtaReply("OK - mac: C2:1F:3A:44:90:0B")))
    }

    @Test fun mergeKeepsOnePerAddressStrongestFirst() {
        val list = DfuScanRules.merge(DfuScanRules.merge(listOf(a), b), a.copy(rssi = -40))
        assertEquals(listOf("C2:1F:3A:44:90:0B", "11:22:33:44:55:66"), list.map { it.address })
        assertEquals(-40, list.first().rssi)
    }

    @Test fun signalBars() {
        assertEquals(4, DfuScanRules.signalBars(-55)); assertEquals(2, DfuScanRules.signalBars(-80)); assertEquals(0, DfuScanRules.signalBars(-100))
    }
}

class OtaTargetRulesTest {
    @Test fun onlyNrf52IsNordicCapable() {
        assertEquals(true, OtaTargetRules.nordicCapable("nRF52840"))
        assertEquals(false, OtaTargetRules.nordicCapable("ESP32-S3"))
        assertEquals(false, OtaTargetRules.nordicCapable("RP2040"))
        assertNull("Doppelvariante unklar", OtaTargetRules.nordicCapable("nRF52840 / ESP32-S3"))
        assertNull(OtaTargetRules.nordicCapable("—"))
        assertNull(OtaTargetRules.nordicCapable(null))
    }

    @Test fun esp32RepliesAreNotNordic() {
        val wifi = DfuScanRules.parseOtaReply("Started: http://192.168.4.1/update")
        assertTrue(wifi.nonNordic); assertFalse(wifi.ok)
        val mcumgr = DfuScanRules.parseOtaReply("OK - BLE OTA open for 300 s")
        assertTrue(mcumgr.nonNordic); assertFalse(mcumgr.ok)
        assertFalse(DfuScanRules.parseOtaReply("OK - mac: C2:1F:3A:44:90:0B").nonNordic)
    }
}
