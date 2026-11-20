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
    FULL("Vollständig geladen"),
    UNKNOWN("Unbekannt")
}

data class BatterySolarTelemetry(
    val batteryVoltage: Float = 0f,
    val batteryPercent: Int = 0,
    val solarVoltage: Float = 0f,
    val solarCurrentMa: Float = 0f,
    val temperatureC: Float = 0f,
    val chargingState: BatteryChargingState = BatteryChargingState.UNKNOWN
) {
    val solarPowerWatts: Float
        get() = (solarVoltage * (solarCurrentMa / 1000f)).coerceAtLeast(0f)
}

data class NodeHardwareInfo(
    val firmwareVersion: String = "—",
    val boardModel: String = "—",
    val radioChip: String = "—",
    val uptimeSeconds: Long = 0L,
    val freeHeapBytes: Int = 0
)
