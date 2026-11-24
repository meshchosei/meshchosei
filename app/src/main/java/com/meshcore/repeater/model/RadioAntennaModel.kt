package com.meshcore.repeater.model

import java.util.Locale
import kotlin.math.pow

enum class CountryRegion(
    val countryCode: String,
    val regionName: String,
    val frequencyMhz: Double,
    val bandwidthKhz: Double,
    val spreadingFactor: Int,
    val codingRate: String,
    val codingRateNum: Int, // 8 für 4/8, 5 für 4/5
    val maxLegalErpDbm: Double, // Gesetzliches ERP Limit
    val description: String,
    // Standardwerte aus RepeaterMesh.cpp
    val defaultFloodMax: Int = 64,
    val defaultFloodMaxUnscoped: Int = 64,
    val defaultFloodMaxAdvert: Int = 8,
    val defaultFloodAdvertIntervalHours: Int = 47,
    val defaultLocalAdvertIntervalMins: Int = 0,
    val minTxPowerDbm: Int = -9,
    val maxTxPowerDbm: Int = 22,
    val defaultTxPowerDbm: Int = 14
) {
    DE_STANDARD(
        countryCode = "DE",
        regionName = "DE Standard (MeshCore EU868)",
        frequencyMhz = 869.618,
        bandwidthKhz = 62.5,
        spreadingFactor = 8,
        codingRate = "4/8",
        codingRateNum = 8,
        maxLegalErpDbm = 27.0, // 869.4–869.65 MHz Subband bis 500 mW ERP (27 dBm)
        description = "Aktueller MeshCore EU868 Standard: 869.618 MHz, BW 62.5 kHz, SF 8, CR 4/8 (500 mW ERP, 10% Duty-Cycle)"
    ),
    DE_HIGHSPEED(
        countryCode = "DE-HS",
        regionName = "DE High-Speed Test (Hamburg-Testnetz)",
        frequencyMhz = 869.618,
        bandwidthKhz = 62.5,
        spreadingFactor = 7,
        codingRate = "4/8",
        codingRateNum = 8,
        maxLegalErpDbm = 27.0,
        description = "High-Speed Mesh (z. B. Hamburg-Testnetz): 869.618 MHz, BW 62.5 kHz, SF 7, CR 4/8"
    ),
    EU868_CLASSIC(
        countryCode = "EU-W",
        regionName = "EU868 Classic / Wide",
        frequencyMhz = 869.525,
        bandwidthKhz = 250.0,
        spreadingFactor = 11,
        codingRate = "4/5",
        codingRateNum = 5,
        maxLegalErpDbm = 27.0,
        description = "Klassisches Wideband EU868: 869.525 MHz, BW 250 kHz, SF 11, CR 4/5"
    ),
    US915(
        countryCode = "US",
        regionName = "USA / FCC (US915)",
        frequencyMhz = 915.000,
        bandwidthKhz = 500.0,
        spreadingFactor = 9,
        codingRate = "4/5",
        codingRateNum = 5,
        maxLegalErpDbm = 30.0,
        description = "FCC Part 15: 902–928 MHz, bis zu 30 dBm TX"
    ),
    UK868(
        countryCode = "GB",
        regionName = "Großbritannien (UK868)",
        frequencyMhz = 869.525,
        bandwidthKhz = 250.0,
        spreadingFactor = 11,
        codingRate = "4/5",
        codingRateNum = 5,
        maxLegalErpDbm = 27.0,
        description = "OFCOM IR2030: 869.525 MHz Subband, max. 27 dBm ERP"
    ),
    CUSTOM(
        countryCode = "CUSTOM",
        regionName = "Benutzerdefiniert / Individuell",
        frequencyMhz = 869.618,
        bandwidthKhz = 62.5,
        spreadingFactor = 8,
        codingRate = "4/8",
        codingRateNum = 8,
        maxLegalErpDbm = 27.0,
        description = "Manuelle Frequenz- und Leistungsparameter"
    );

    companion object {
        fun detectDefault(): CountryRegion {
            val country = Locale.getDefault().country.uppercase(Locale.ROOT)
            return when (country) {
                "GB" -> UK868
                "US", "CA" -> US915
                else -> DE_STANDARD
            }
        }
    }
}

enum class AntennaPreset(
    val label: String,
    val gainDbi: Double,
    val isCustom: Boolean = false,
    val note: String = ""
) {
    STOCK_2DBI("Stock-Antenne (~2.0 dBi)", 2.0, false, "Standard Rundstrahler"),
    RAK_5_8DBI("RAK Glasfaser Omni (5.8 dBi)", 5.8, false, "Optimierte Mastantenne"),
    ALLNET_7_3DBI("Allnet High-Gain (7.3 dBi)", 7.3, false, "Hoher Antennengewinn"),
    HIGH_GAIN_12DBI("High-Gain / Yagi (12.0 dBi)", 12.0, false, "Richtfunk / Sehr hohe Bündelung"),
    UNKNOWN_SAFE("Unbekannt (+3.0 dBi Puffer)", 3.0, false, "Konservativer Sicherheitszuschlag"),
    CUSTOM("Eigene Antenne (Benutzerdefiniert)", 0.0, true, "Freie dBi-Eingabe");
}

data class RadioAntennaConfig(
    val region: CountryRegion = CountryRegion.DE_STANDARD,
    val frequencyMhz: Double = 869.618,
    val bandwidthKhz: Double = 62.5,
    val spreadingFactor: Int = 8,
    val codingRate: String = "4/8",
    val codingRateNum: Int = 8,
    val txPowerDbm: Double = 14.0, // Aktuelle Transceiver-Einstellung
    val floodMax: Int = 64,
    val floodMaxUnscoped: Int = 64,
    val floodMaxAdvert: Int = 8,
    val floodAdvertIntervalHours: Int = 47,
    val localAdvertIntervalMins: Int = 0,
    val antennaPreset: AntennaPreset = AntennaPreset.STOCK_2DBI,
    val customAntennaGainDbi: Double = 2.0,
    val cableLossDb: Double = 0.5, // Typische Dämpfung von Pigtail/Kabel
    val complianceUnlocked: Boolean = false, // Sicherheitsfreigabe für Überschreitungen
    /** Hardware-Profil aus der BoardDatabase (automatisch per "board" erkannt oder manuell gewählt) */
    val board: BoardProfile = BoardDatabase.GENERIC,
    val boardAutoDetected: Boolean = false,
    /** true, sobald der Nutzer die Kabeldämpfung selbst eingegeben hat (dann nicht mehr durch Board-Standard überschreiben) */
    val cableLossUserSet: Boolean = false
) {
    /** Hardware-Maximum des Boards (Transceiver bzw. mit externem PA). */
    val hardwareMaxTxDbm: Double
        get() = board.maxTxDbm

    val effectiveGainDbi: Double
        get() = if (antennaPreset.isCustom) customAntennaGainDbi else antennaPreset.gainDbi

    // EIRP = P_TX + Gain - CableLoss
    val calculatedEirpDbm: Double
        get() = txPowerDbm + effectiveGainDbi - cableLossDb

    val calculatedEirpMw: Double
        get() = 10.0.pow(calculatedEirpDbm / 10.0)

    // ERP = EIRP - 2.15 dB
    val calculatedErpDbm: Double
        get() = calculatedEirpDbm - 2.15

    val calculatedErpMw: Double
        get() = 10.0.pow(calculatedErpDbm / 10.0)

    // Gesetzlich maximal erlaubte TX-Sendeleistung am Transceiver:
    // P_TX_max = Limit_ERP + 2.15 - Gain + CableLoss
    val maxAllowedTxPowerDbm: Double
        get() = region.maxLegalErpDbm + 2.15 - effectiveGainDbi + cableLossDb

    /** Obergrenze für die TX-Eingabe: mindestens 30 dBm, bei Boards mit PA (z. B. Station G2, 35 dBm) entsprechend höher. */
    val maxTxInputDbm: Int
        get() = kotlin.math.floor(maxOf(30.0, board.maxTxDbm)).toInt()

    /** Empfehlung = min(gesetzliches Maximum, Hardware-Maximum des Boards). */
    val recommendedMaxTxDbm: Double
        get() = minOf(maxAllowedTxPowerDbm, hardwareMaxTxDbm)

    // Ist das aktuelle Setup legal ohne Override?
    val isCompliant: Boolean
        get() = calculatedErpDbm <= region.maxLegalErpDbm + 0.01

    // Exzessive Leistung über dem Limit in dB
    val excessDbm: Double
        get() = (calculatedErpDbm - region.maxLegalErpDbm).coerceAtLeast(0.0)
}
