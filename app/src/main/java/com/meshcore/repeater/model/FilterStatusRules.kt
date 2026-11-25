package com.meshcore.repeater.model

/**
 * Filterstatus-Erkennung (v1.16). Firmware (ZephCore app/Filter.cpp, MeshCore simple_repeater/Filter.cpp):
 *  - "filter"      -> FilterStat::formatSummary: "> Filter on|off: Blocked [ … ]" (+ " (dry-run)") = Ein/Aus
 *  - "filter mode" -> "> Filter: mode whitelist|blacklist" (nur ZephCore mit Whitelist)
 * Einen Befehl "filter status" gibt es nicht (Antwort "> Filter: command error").
 */
object FilterStatusRules {
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
