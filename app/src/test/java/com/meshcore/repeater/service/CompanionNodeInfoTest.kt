package com.meshcore.repeater.service

import com.meshcore.repeater.model.CompanionNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.25: Gerätedaten des Companion-Nodes – Frames wörtlich nach MeshCore companion_radio/MyMesh.cpp und ZephCore CompanionMesh.cpp. */
class CompanionNodeInfoTest {
    private fun le32(v: Long) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte())
    private fun field(s: String, n: Int) = ByteArray(n).also { s.toByteArray().copyInto(it, 0, 0, minOf(n, s.length)) }

    /** [0x05][type][tx][max_tx][pub 32][lat 4][lon 4][multi_acks][loc_policy][telemetry][manual_add][freq 4][bw 4][sf][cr][name] */
    private fun selfInfo(name: String) = byteArrayOf(0x05, 1, 22, 22) + ByteArray(32) { (0x77 + it).toByte() } +
        le32(52_520_000) + le32(13_400_000) + byteArrayOf(0, 0, 0, 0) + le32(869_618) + le32(62_500) + byteArrayOf(8, 8) + name.toByteArray()

    /** [0x0D][fw_ver_code][max_contacts/2][max_channels][ble_pin 4][build_date 12][model 40][version 20][repeat][path_hash] */
    private fun deviceInfo() = byteArrayOf(0x0D, 14, 175.toByte(), 40) + le32(123456) +
        field("13 Nov 2026", 12) + field("Heltec V3", 40) + field("v1.11.0-6d32193", 20) + byteArrayOf(0, 0)

    @Test fun parsesSelfInfoWithoutGps() {
        val c = CompanionProtocol.parseSelfInfo(selfInfo("Andreas Handy-Node"))!!
        assertEquals("Andreas Handy-Node", c.name)
        assertEquals(22, c.txPowerDbm); assertEquals(22, c.maxTxPowerDbm)
        assertEquals(869.618, c.freqMhz!!, 1e-9); assertEquals(62.5, c.bwKhz!!, 1e-9)
        assertEquals(8, c.sf); assertEquals(8, c.cr)
        assertTrue(c.publicKeyHex.startsWith("7778797a"))
        assertFalse("GPS-Position des Companions wird nicht übernommen", c.toString().contains("52.52") || c.toString().contains("5252"))
    }

    @Test fun parsesDeviceInfoWithoutBlePin() {
        val c = CompanionProtocol.parseDeviceInfo(deviceInfo())!!
        assertEquals(14, c.protocolVersion)
        assertEquals(350, c.maxContacts); assertEquals(40, c.maxChannels)
        assertEquals("13 Nov 2026", c.buildDate)
        assertEquals("Heltec V3", c.model)
        assertEquals("v1.11.0-6d32193", c.firmwareVersion)
        assertFalse("BLE-PIN wird nie übernommen", c.toString().contains("123456"))
    }

    @Test fun bothFramesMergeIntoOneState() {
        val a = CompanionProtocol.parseSelfInfo(selfInfo("Node A"))!!
        val b = CompanionProtocol.parseDeviceInfo(deviceInfo(), a)!!
        assertEquals("Node A", b.name); assertEquals("Heltec V3", b.model)
        assertTrue(b.hasSelfInfo && b.hasDeviceInfo)
        val again = CompanionProtocol.parseSelfInfo(selfInfo("Node A"), b)!!
        assertEquals("Keep-Alive-Antwort behält Gerätedaten", "Heltec V3", again.model)
    }

    @Test fun rejectsForeignOrShortFrames() {
        assertNull(CompanionProtocol.parseSelfInfo(byteArrayOf(0x0D, 1, 2, 3)))
        assertNull(CompanionProtocol.parseDeviceInfo(byteArrayOf(0x05, 1, 2, 3)))
        assertNull(CompanionProtocol.parseSelfInfo(byteArrayOf(0x05, 1)))
        assertEquals(CompanionNodeInfo(), CompanionNodeInfo())
    }
}
