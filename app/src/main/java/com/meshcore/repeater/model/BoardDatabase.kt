package com.meshcore.repeater.model

import java.util.Locale

/**
 * Hardware-Profil eines MeshCore-/ZephCore-Repeater-Boards.
 * [maxTxDbm] = maximal einstellbare Ausgangsleistung laut Hardware (SX1262 ohne PA: 22 dBm).
 * [defaultPigtailLossDb] = typische Dämpfung zwischen Funkchip und Antennenbuchse (Pigtail/U.FL-Adapter).
 */
data class BoardProfile(
    val id: String,
    val displayName: String,
    val mcu: String,
    val transceiver: String,
    val maxTxDbm: Double,
    val connector: String,
    val defaultPigtailLossDb: Double,
    val hasExternalPa: Boolean = false,
    /** Normalisierte Teilstrings (nur a–z/0–9), die im "board"-String der Firmware vorkommen. */
    val matchKeys: List<String> = emptyList(),
    /** Normalisierte Teilstrings, die einen Treffer ausschließen (z. B. 1-W-Varianten mit PA). */
    val excludeKeys: List<String> = emptyList()
)

/**
 * Kleine Board-Datenbank der gängigsten Repeater-Boards. Die Match-Keys basieren auf den
 * `getManufacturerName()`-Strings aus MeshCore/ZephCore (z. B. "Heltec V3", "RAK 4631",
 * "Station G2", "LilyGo T3S3", "Seeed SenseCap Solar", "Elecrow ThinkNode-M1", "Seeed Xiao-nrf52").
 */
object BoardDatabase {
    val GENERIC = BoardProfile(
        id = "generic_sx1262", displayName = "Generisches SX1262-Board", mcu = "—", transceiver = "SX1262",
        maxTxDbm = 22.0, connector = "—", defaultPigtailLossDb = 0.3
    )

    val profiles: List<BoardProfile> = listOf(
        BoardProfile("heltec_v3", "Heltec WiFi LoRa 32 V3 / V3.2", "ESP32-S3", "SX1262", 22.0, "U.FL/IPEX", 0.4,
            matchKeys = listOf("heltecv3", "wifilora32v3", "heltecv32")),
        BoardProfile("rak4631", "RAK4631 / WisBlock", "nRF52840", "SX1262", 22.0, "U.FL/IPEX", 0.4,
            matchKeys = listOf("rak4631", "wisblock")),
        BoardProfile("xiao_wio", "Seeed XIAO nRF52840 / ESP32S3 + Wio-SX1262", "nRF52840 / ESP32-S3", "SX1262", 22.0, "U.FL/IPEX", 0.3,
            matchKeys = listOf("xiaonrf52", "xiaos3", "xiaoesp32s3"), excludeKeys = listOf("e22", "30dbm")),
        BoardProfile("sensecap_solar", "Seeed SenseCAP Solar Node P1 / P1-Pro", "nRF52840", "SX1262", 22.0, "N-Typ/SMA", 0.2,
            matchKeys = listOf("sensecapsolar", "solarnode")),
        BoardProfile("tbeam", "LilyGO T-Beam / T-Beam Supreme", "ESP32 / ESP32-S3", "SX1262", 22.0, "SMA direkt", 0.0,
            matchKeys = listOf("tbeam"), excludeKeys = listOf("1w")),
        BoardProfile("t3s3", "LilyGO LoRa32 T3-S3", "ESP32-S3", "SX1262", 22.0, "U.FL/SMA", 0.3,
            matchKeys = listOf("t3s3")),
        BoardProfile("thinknode", "Elecrow ThinkNode M1 / M2", "nRF52840 / ESP32-S3", "SX1262", 22.0, "SMA/U.FL", 0.3,
            matchKeys = listOf("thinknodem1", "thinknodem2")),
        BoardProfile("station_g2", "B&Q Station G2", "ESP32-S3", "SX1262 + PA", 35.0, "SMA direkt", 0.0, hasExternalPa = true,
            matchKeys = listOf("stationg2")),
        // Hauptgerät dieses Projekts (ZephCore-BW-Builds): Seeed Wio Tracker L1
        BoardProfile("wio_tracker_l1", "Seeed Wio Tracker L1", "nRF52840", "SX1262", 22.0, "U.FL/IPEX", 0.3,
            // auch kurze Ausgaben wie "Wio L1", "Wio-L1", "Seeed Wio L1" – 1-W-Varianten ("Pro 1W") ausgeschlossen
            matchKeys = listOf("wiotrackerl1", "wiol1"), excludeKeys = listOf("1w")),
        GENERIC
    )

    private fun normalize(s: String) = s.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

    /** Board-String der Firmware ("board"-Antwort) einem Profil zuordnen; null = nicht erkannt. */
    fun match(board: String?): BoardProfile? {
        if (board.isNullOrBlank()) return null
        val n = normalize(board)
        return profiles.firstOrNull { p ->
            p !== GENERIC && p.matchKeys.any { n.contains(it) } && p.excludeKeys.none { n.contains(it) }
        }
    }

    fun byId(id: String?): BoardProfile? = profiles.firstOrNull { it.id == id }
}
