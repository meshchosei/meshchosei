package com.meshcore.repeater.model

/**
 * Filterstatus-Erkennung (v1.16). Firmware (ZephCore app/Filter.cpp, MeshCore simple_repeater/Filter.cpp):
 *  - "filter"      -> FilterStat::formatSummary: "> Filter on|off: Blocked [ … ]" (+ " (dry-run)") = Ein/Aus
 *  - "filter mode" -> "> Filter: mode whitelist|blacklist" (nur ZephCore mit Whitelist)
 *  - "filter stats air" -> "> Filter: saved airtime <ms> ms (…)" / "> Filter: no airtime saved yet" (v1.22)
 * Einen Befehl "filter status" gibt es nicht (Antwort "> Filter: command error"); "filter stats" ohne Thema
 * liefert nur den Hilfetext "> filter stats [ hops | rate | … ]" – die Zähler stehen in der Antwort auf "filter".
 */
object FilterStatusRules {
    const val CMD_STATS_AIR = "filter stats air"

    /**
     * Abfragen für die Karte „Filter-Statistiken & Treffer“ (v1.22): "filter" (Blockiert-Zähler + Ein/Aus),
     * "filter mode" nur solange der Modus unbekannt ist, danach "filter stats air".
     */
    fun statsQueries(supportsBlacklist: Boolean, supportsWhitelist: Boolean, modeKnown: Boolean): List<String> {
        if (!(supportsBlacklist || supportsWhitelist)) return emptyList()
        return listOf("filter") + (if (supportsWhitelist && !modeKnown) listOf("filter mode") else emptyList()) + CMD_STATS_AIR
    }

    /** Status-Abfragen passend zu den erkannten Filter-Funktionen. */
    fun statusQueries(supportsBlacklist: Boolean, supportsWhitelist: Boolean): List<String> =
        (if (supportsBlacklist || supportsWhitelist) listOf("filter") else emptyList()) +
            (if (supportsWhitelist) listOf("filter mode") else emptyList())

    /**
     * Nur echte Status-/Schaltantworten auf Ein/Aus/Modus auswerten – nie Hilfetexte ("filter help" enthält
     * "filter on|off") oder Listen.
     */
    fun shouldParseStatus(activeCommand: String?): Boolean {
        val c = activeCommand?.trim()?.lowercase() ?: return false
        if (!c.startsWith("filter")) return false
        return !(c.endsWith(" help") || c == "filter help" || c.endsWith(" list"))
    }

    /** Status gilt als bekannt, wenn Ein/Aus gelesen wurde und – bei Whitelist-Firmware – auch der Modus. */
    fun isKnown(enabledKnown: Boolean, modeKnown: Boolean, supportsWhitelist: Boolean): Boolean =
        enabledKnown && (!supportsWhitelist || modeKnown)
}
