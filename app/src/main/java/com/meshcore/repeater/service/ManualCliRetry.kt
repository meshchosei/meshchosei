package com.meshcore.repeater.service

/**
 * Einmaliger automatischer Retry für manuelle CLI-Befehle über Remote-Admin (LoRa).
 *
 * Firmware-Hintergrund (MeshCore simple_repeater / ZephCore RepeaterMesh::onPeerDataRecv): Der Companion
 * vergibt für jeden CLI-Frame einen neuen Zeitstempel (getCurrentTimeUnique), der Repeater führt einen
 * erneut gesendeten Befehl also ein ZWEITES MAL aus, wenn nur die Antwort verloren ging. Deshalb wird nur
 * bei Befehlen automatisch wiederholt, deren doppelte Ausführung harmlos ist (Abfragen, "set …", Filter-
 * und Regionen-Einträge). Nicht-idempotente oder riskante Befehle werden nie automatisch wiederholt.
 */
object ManualCliRetry {
    /** Ein Befehl → genau ein Retry; die Antwort muss dasselbe "XX|"-Tag tragen. */
    const val MAX_RETRIES = 1

    private val NEVER_RETRY_EXACT = setOf(
        "reboot", "poweroff", "shutdown", "erase", "start ota", "stop ota", "start dfu",
        "advert", "advert.zerohop", "discover.neighbors", "clear stats", "filter reset", "region load",
        "clkreboot", "log erase", "set adc.multiplier full", "gps guard accept", "set cad.reset",
        // jhuebert-Fork (v1.21): OTA-Sitzung, Filter-Regelwerk, Fleet, MQTT-Observer
        "ota apply", "ota abort", "ota rollback", "ota stage", "ota fetch start", "filter clear", "filter stats reset",
        "filter ratelimit clear", "fleet chan clear", "fleet tag clear", "mqtt recover",
        // Sweden-Mesh LOU (v1.21): signierter Pull-OTA, Ende-zu-Ende-Ping
        "ota update", "cmd ping"
    )
    private val NEVER_RETRY_PREFIX = listOf("password ", "set prv.key ", "region remove ", "filter reset ", "time ",
        "set adc.multiplier target ", "region def ",
        // Forks (v1.21): Regeln anlegen/verschieben/löschen sind nicht idempotent, trigger sendet, Schlüssel nie doppelt
        "filter add ", "filter del ", "filter move ", "filter chan del ", "fleet forget ", "fleet chan set ",
        "trigger ", "set cmd.pubkey ")

    fun isRetrySafe(cmd: String): Boolean {
        val c = cmd.trim().lowercase().replace(Regex("\\s+"), " ")
        if (c.isEmpty()) return false
        if (c in NEVER_RETRY_EXACT) return false
        return NEVER_RETRY_PREFIX.none { c.startsWith(it) }
    }
}
