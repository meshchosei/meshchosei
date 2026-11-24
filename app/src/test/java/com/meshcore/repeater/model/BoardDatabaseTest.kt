package com.meshcore.repeater.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoardDatabaseTest {
    private fun id(board: String) = BoardDatabase.match(board)?.id

    @Test
    fun matchesRealFirmwareBoardStrings() {
        // getManufacturerName()-Strings aus MeshCore/ZephCore
        assertEquals("heltec_v3", id("Heltec V3"))
        assertEquals("rak4631", id("RAK 4631"))
        assertEquals("station_g2", id("Station G2"))
        assertEquals("station_g2", id("BQ Station G2"))
        assertEquals("t3s3", id("LilyGo T3S3 (SX1262)"))
        assertEquals("t3s3", id("LilyGo T3 S3"))
        assertEquals("sensecap_solar", id("Seeed SenseCap Solar"))
        assertEquals("thinknode", id("Elecrow ThinkNode-M1"))
        assertEquals("thinknode", id("Elecrow ThinkNode M2"))
        assertEquals("xiao_wio", id("Seeed Xiao-nrf52"))
        assertEquals("xiao_wio", id("Xiao S3 WIO"))
        assertEquals("tbeam", id("LilyGo T-Beam"))
        assertEquals("wio_tracker_l1", id("Seeed Wio Tracker L1"))
    }

    @Test
    fun paVariantsAndUnknownBoardsAreNotMisdetected() {
        assertNull(id("LilyGo T-Beam 1W"))                          // 1-W-PA, nicht mit 22-dBm-Profil verwechseln
        assertNull(id("Seeed Wio Tracker L1 Pro 1W"))
        assertNull(id("Ikoka Handheld E22 30dBm (Xiao_nrf52)"))     // E22-30-Modul mit PA
        assertNull(id("Heltec T114"))
        assertNull(id(""))
    }

    @Test
    fun recommendationIsCappedByHardwareMaximum() {
        // 0 dBi Antenne, 3 dB Kabel -> gesetzlich ~32 dBm möglich, Hardware begrenzt auf 22 dBm
        val rc = RadioAntennaConfig(antennaPreset = AntennaPreset.CUSTOM, customAntennaGainDbi = 0.0, cableLossDb = 3.0, board = BoardDatabase.GENERIC)
        assertEquals(22.0, rc.recommendedMaxTxDbm, 0.001)
    }

    @Test
    fun txInputLimitFollowsBoardProfile() {
        assertEquals(30, RadioAntennaConfig(board = BoardDatabase.GENERIC).maxTxInputDbm)            // Standard: 30 dBm
        assertEquals(30, RadioAntennaConfig(board = BoardDatabase.byId("heltec_v3")!!).maxTxInputDbm) // 22-dBm-Board: nie unter 30
        assertEquals(35, RadioAntennaConfig(board = BoardDatabase.byId("station_g2")!!).maxTxInputDbm) // Station G2 mit PA
    }

    @Test
    fun matchesShortWioL1Names() {
        assertEquals("wio_tracker_l1", id("Wio L1"))
        assertEquals("wio_tracker_l1", id("Wio-L1"))
        assertEquals("wio_tracker_l1", id("Seeed Wio L1"))
        assertEquals("wio_tracker_l1", id("Wio Tracker L1"))      // ZephCore CONFIG_ZEPHCORE_BOARD_NAME
        assertNull(id("Wio L1 Pro 1W"))
        assertNull(id("Seeed Wio-L1 1W"))
    }
}
