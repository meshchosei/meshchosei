package com.meshcore.repeater.model

/**
 * Erkannte Hardware- und Firmware-Fähigkeiten des verbundenen Nodes.
 * Wird nach dem Verbinden per automatischer Auslese-Sequenz befüllt.
 */
data class DeviceCapabilities(
    /** true = Firmware beantwortet 'filter help' mit gültiger Filter-Hilfe (Kanal-/Pfad-Sperrlisten, DDMC-Filter) */
    val supportsBlacklist: Boolean = false,
    /** true = Firmware beantwortet 'filter wl help' mit gültiger Whitelist-Hilfe (inkl. 'filter mode') */
    val supportsWhitelist: Boolean = false,
    /** true = GPS-Hardware vorhanden laut 'get gps' Antwort */
    val hasGps: Boolean = true,
    /** true = Batterie-/Solar-Telemetrie verfügbar */
    val hasBatteryTelemetry: Boolean = true,
    /** true = Fähigkeitserkennung abgeschlossen */
    val detectionComplete: Boolean = false,
    val isCompanion: Boolean = false,
    /** true = Repeater hängt direkt am USB-Port (ASCII-CLI hat geantwortet) – wird nie als Companion geführt */
    val isDirectRepeater: Boolean = false,
    /** Firmware-Version als String (aus 'ver'-Antwort) */
    val firmwareVersion: String = "",
    /** Letzter Rohtext der 'filter help'-Antwort, für weitere Erkennung */
    val rawFilterHelp: String = ""
)
