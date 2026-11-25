package com.meshcore.repeater.model

enum class BatteryChargingState(val label: String) {
    SOLAR_CHARGING("Solar-Ladung aktiv"),
    DISCHARGING("Batteriebetrieb (Entladen)"),
    CHARGING("Wird geladen"),
    EXTERNAL("Externe Versorgung"),
    UNKNOWN("Unbekannt")
}

/** Hauptversorgung laut "power"-Antwort der Firmware. */
enum class PowerSource { UNKNOWN, USB, EXTERNAL, SOLAR, BATTERY }

data class BatterySolarTelemetry(
    val batteryVoltage: Float = 0f,
    val batteryPercent: Int = 0,
    val solarVoltage: Float = 0f,
    val solarCurrentMa: Float = 0f,
    val temperatureC: Float = 0f,
    val chargingState: BatteryChargingState = BatteryChargingState.UNKNOWN,
    /** true, sobald eine gültige "power"-Antwort ausgewertet wurde. */
    val queried: Boolean = false,
    /** null = Board meldet "n/a" (keine Erkennung vorhanden). */
    val usbPowered: Boolean? = null,
    val solarCharging: Boolean? = null,
    val externalPowered: Boolean = false,
    val charging: Boolean = false
) {
    val solarPowerWatts: Float
        get() = (solarVoltage * (solarCurrentMa / 1000f)).coerceAtLeast(0f)

    val hasBattery: Boolean get() = batteryVoltage >= 2.5f

    val powerSource: PowerSource
        get() = when {
            !queried -> PowerSource.UNKNOWN
            solarCharging == true -> PowerSource.SOLAR
            usbPowered == true -> PowerSource.USB
            externalPowered -> PowerSource.EXTERNAL
            hasBattery -> PowerSource.BATTERY
            else -> PowerSource.UNKNOWN
        }

    companion object {
        private val LINE = Regex("""batt:\s*(\d+)\s*mV""", RegexOption.IGNORE_CASE)
        private fun flag(line: String, key: String): Boolean? =
            Regex("""\b$key:\s*(yes|no|n/a)\b""", RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1)?.lowercase()?.let {
                when (it) { "yes" -> true; "no" -> false; else -> null }
            }

        /** Li-Ion-Näherung: 3,2 V = 0 %, 4,2 V = 100 %. */
        fun percentFromMv(mv: Int): Int = ((mv - 3200) * 100 / 1000).coerceIn(0, 100)

        /**
         * Parst die MeshCore/ZephCore-Antwort auf "power" (CommonCLI):
         * "batt:4073 mV usb:no solar_chg:n/a ext:no charger:no". null = keine power-Zeile.
         */
        fun parsePowerReply(line: String): BatterySolarTelemetry? {
            val mv = LINE.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            val usb = flag(line, "usb"); val solar = flag(line, "solar_chg")
            val ext = flag(line, "ext") == true; val chg = flag(line, "charger") == true
            val state = when {
                solar == true -> BatteryChargingState.SOLAR_CHARGING
                chg -> BatteryChargingState.CHARGING
                usb == true || ext -> BatteryChargingState.EXTERNAL
                mv >= 2500 -> BatteryChargingState.DISCHARGING
                else -> BatteryChargingState.UNKNOWN
            }
            return BatterySolarTelemetry(
                batteryVoltage = mv / 1000f, batteryPercent = if (mv >= 2500) percentFromMv(mv) else 0,
                chargingState = state, queried = true, usbPowered = usb, solarCharging = solar,
                externalPowered = ext, charging = chg
            )
        }
    }
}

data class NodeHardwareInfo(
    val firmwareVersion: String = "—",
    /** UF2-Bootloader-Version aus `get bootloader.ver` (nur nRF52), leer = nicht geliefert. */
    val bootloaderVersion: String = "",
    val boardModel: String = "—",
    val radioChip: String = "—",
    val uptimeSeconds: Long = 0L,
    val freeHeapBytes: Int = 0
)

/**
 * Prüfung der UF2-Bootloader-Version (`get bootloader.ver`, nRF52) auf BLE-OTA-Tauglichkeit (v1.18).
 *
 * Firmware: MeshCore `NRF52Board::getBootloaderVersion` / ZephCore `ZephyrBoard::getBootloaderVersion` lesen den
 * Text hinter "UF2 Bootloader " aus info.txt im Flash, z. B. "0.9.2-OTAFIX2.2-BP1.3". Ohne nRF52 bzw. ohne
 * Fundstelle antwortet die Firmware "unknown" bzw. "Error: unsupported" – dann gibt es keine Bewertung.
 *
 * Mindestens erforderlich: 0.9.2-OTAFIX2.2-BP1.3. Ältere Bootloader (ohne OTAFIX oder OTAFIX < 2.2) können beim
 * BLE-OTA-Update in einer Bootschleife hängen bleiben.
 */
object BootloaderCheck {
    const val MIN_OTA_BOOTLOADER = "0.9.2-OTAFIX2.2-BP1.3"

    private val BASE = Regex("""^v?(\d+)\.(\d+)\.(\d+)""", RegexOption.IGNORE_CASE)
    private val OTAFIX = Regex("""OTAFIX(\d+)\.(\d+)""", RegexOption.IGNORE_CASE)
    private val BP = Regex("""BP(\d+)\.(\d+)""", RegexOption.IGNORE_CASE)

    /** Nur echte Versionsangaben (beginnen mit einer Zahl); "unknown"/"unsupported"/Fehler → null. */
    fun normalize(reply: String?): String? {
        val v = reply?.trim()?.removePrefix(">")?.trim().orEmpty()
        return v.takeIf { BASE.containsMatchIn(it) }
    }

    private fun ints(m: MatchResult) = m.groupValues.drop(1).map { it.toInt() }
    private fun cmp(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until minOf(a.size, b.size)) if (a[i] != b[i]) return a[i].compareTo(b[i])
        return a.size.compareTo(b.size)
    }

    /**
     * true = Bootloader ist älter als [MIN_OTA_BOOTLOADER] → OTA-Bootloop-Warnung anzeigen.
     * false = ausreichend oder keine bewertbare Versionsangabe (dann keine Warnung).
     */
    fun needsOtaWarning(version: String?): Boolean {
        val v = normalize(version) ?: return false
        val base = BASE.find(v)?.let(::ints) ?: return false
        if (cmp(base, listOf(0, 9, 2)) < 0) return true
        val otafix = OTAFIX.find(v)?.let(::ints) ?: return true   // nRF52 ohne OTAFIX-Patch
        val c = cmp(otafix, listOf(2, 2))
        if (c != 0) return c < 0
        val bp = BP.find(v)?.let(::ints) ?: return false          // OTAFIX2.2 ohne BP-Angabe gilt als ausreichend
        return cmp(bp, listOf(1, 3)) < 0
    }
}
