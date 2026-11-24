package com.meshcore.repeater.model

enum class LedMode(val cliCommand: String, val label: String) {
    NORMAL("set leds on", "Normal (Aktivität & Status)"),
    STEALTH("set leds off", "Stealth (Alle LEDs Aus)"),
    BOOT_ONLY("set leds boot", "Nur Boot-Blinken (2x 150ms)")
}

enum class GpsMode(val cliCommand: String, val label: String) {
    OFF("set gps off", "GPS Deaktiviert"),
    INTERVAL_15M("set gps 900", "Intervall (Alle 15 Min)"),
    INTERVAL_60M("set gps 3600", "Intervall (Jede Stunde)"),
    ALWAYS_ON("set gps on", "Dauerhaft Aktiv (Always-On)")
}

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
    val boardModel: String = "—",
    val radioChip: String = "—",
    val uptimeSeconds: Long = 0L,
    val freeHeapBytes: Int = 0
)
