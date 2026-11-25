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
        "clkreboot", "log erase", "set adc.multiplier full", "gps guard accept", "set cad.reset"
    )
    private val NEVER_RETRY_PREFIX = listOf("password ", "set prv.key ", "region remove ", "filter reset ", "time ",
        "set adc.multiplier target ", "region def ")

    fun isRetrySafe(cmd: String): Boolean {
        val c = cmd.trim().lowercase().replace(Regex("\\s+"), " ")
        if (c.isEmpty()) return false
        if (c in NEVER_RETRY_EXACT) return false
        return NEVER_RETRY_PREFIX.none { c.startsWith(it) }
    }
}
