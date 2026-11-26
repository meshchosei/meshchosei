package com.meshcore.repeater.model

import com.meshcore.repeater.R

enum class FilterMode(val cliName: String, val titleRes: Int, val descRes: Int) {
    BLACKLIST("blacklist", R.string.filter_mode_blacklist, R.string.filter_mode_blacklist_desc),
    WHITELIST("whitelist", R.string.filter_mode_whitelist, R.string.filter_mode_whitelist_desc)
}

data class WhitelistChannel(
    val name: String,
    val hashHex: String = "",
    val isPreset: Boolean = false
)

data class WhitelistPath(
    val prefixHex: String,
    val comment: String = ""
)

// === Blacklist Models (Filter.cpp) ===
data class BlockedChannel(
    val name: String
)

data class BlockedPath(
    val prefixHex: String,
    val comment: String = ""
)

data class FilterRule(
    val pattern: String,
    val timeoutSecs: Int = 0, // 0 = permanent / until reboot, 0-65535
    val dropProbabilityPercent: Int = 100 // 1-100%
)

data class WatchChannel(
    val name: String
)

/**
 * Exklusive Menü-Freigabe für Blacklist/Whitelist:
 *  - Filter aus (activeMode == null): alle von der Firmware unterstützten Filter-Menüs aktiv
 *  - Filter an im Blacklist-Modus: nur Blacklist; im Whitelist-Modus: nur Whitelist
 */
object FilterMenuRules {
    fun blacklistEnabled(supportsBlacklist: Boolean, activeMode: FilterMode?): Boolean =
        supportsBlacklist && (activeMode == null || activeMode == FilterMode.BLACKLIST)
    fun whitelistEnabled(supportsWhitelist: Boolean, activeMode: FilterMode?): Boolean =
        supportsWhitelist && (activeMode == null || activeMode == FilterMode.WHITELIST)
}

data class FilterStats(
    val filterEnabled: Boolean = false,
    val dryRun: Boolean = false,
    val mode: FilterMode = FilterMode.BLACKLIST,
    val maxHops: Int = 7,
    val ageMinutes: Int = 0, // 0 = off, 1-10080
    /** Blockiert-Zähler aus "filter" (FilterStat::formatSummary); null = noch nicht gelesen. */
    val counters: FilterCounters? = null,
    /** Geschätzte gesparte Sendezeit aus "filter stats air" in ms; null = noch nicht gelesen / nicht unterstützt. */
    val savedAirtimeMs: Long? = null
) {
    /** Tatsächlich wirksamer Modus: null = Filter ausgeschaltet ("OFF"). */
    val activeMode: FilterMode?
        get() = if (filterEnabled) mode else null
}

/**
 * Zähler der Filter-Zusammenfassung (v1.22), Firmware ZephCore `app/FilterStats.h` / MeshCore
 * `simple_repeater/FilterStats.h`: "> Filter on|off: Blocked [ Hops: n | Rate: n | Channel: n | Hash: n | Malformed: n ]".
 * Einen Zähler für durchgelassene Pakete gibt die Firmware nicht aus; Whitelist-Ablehnungen zählt sie in keiner
 * Kategorie (nur in der gesparten Airtime).
 */
data class FilterCounters(
    val hops: Long = 0L,
    val rate: Long = 0L,
    val channel: Long = 0L,
    val hash: Long = 0L,
    val malformed: Long = 0L
) {
    val total: Long get() = hops + rate + channel + hash + malformed

    companion object {
        /** Gesparte Airtime lesbar: < 60 s mit einer Nachkommastelle („12.5 s“), sonst „3m 34s“ bzw. „2h 5m“. null -> „—“. */
        fun formatAirtime(ms: Long?): String {
            if (ms == null) return "—"
            val secs = ms / 1000L
            return when {
                secs < 60L -> String.format(java.util.Locale.ROOT, "%.1f s", ms / 1000.0)
                secs < 3600L -> "${secs / 60L}m ${secs % 60L}s"
                else -> "${secs / 3600L}h ${(secs / 60L) % 60L}m"
            }
        }
    }
}

// === Theme & Localization Enums ===
enum class ThemeMode(val titleRes: Int) {
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark)
}

enum class AppLanguage(val code: String, val titleRes: Int) {
    SYSTEM("system", R.string.lang_system),
    DE("de", R.string.lang_de),
    EN("en", R.string.lang_en),
    NL("nl", R.string.lang_nl),
    FR("fr", R.string.lang_fr)
}
