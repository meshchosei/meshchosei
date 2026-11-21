package com.meshcore.repeater.model

/**
 * Erkannte Hardware- und Firmware-Fähigkeiten des verbundenen Nodes.
 * Wird nach dem Verbinden per automatischer Auslese-Sequenz befüllt.
 */
data class DeviceCapabilities(
    /** true = Filter.cpp mit 'wl' und 'mode' Unterbefehlen verfügbar (BW2 / ZephCore) */
    val hasFilterBlacklist: Boolean = false,
    val hasFilterWhitelistMode: Boolean = false,
    /** true = 'filter mode' Umschalter unterstützt */
    val hasFilterModeSwitch: Boolean = false,
    /** true = GPS-Hardware vorhanden laut 'get gps' Antwort */
    val hasGps: Boolean = true,
    /** true = Batterie-/Solar-Telemetrie verfügbar */
    val hasBatteryTelemetry: Boolean = true,
    /** true = Fähigkeitserkennung abgeschlossen */
    val detectionComplete: Boolean = false,
    val isCompanion: Boolean = false,
    /** Firmware-Version als String (aus 'ver'-Antwort) */
    val firmwareVersion: String = "",
    /** Letzter Rohtext der 'filter help'-Antwort, für weitere Erkennung */
    val rawFilterHelp: String = ""
)
