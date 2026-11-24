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
    val allowedPackets: Long = 0L,
    val droppedPackets: Long = 0L,
    val savedAirtimeMs: Long = 0L,
    val lastResetTime: String = "-"
) {
    /** Tatsächlich wirksamer Modus: null = Filter ausgeschaltet ("OFF"). */
    val activeMode: FilterMode?
        get() = if (filterEnabled) mode else null

    val dropRatePercent: Float
        get() {
            val total = allowedPackets + droppedPackets
            return if (total > 0) (droppedPackets.toFloat() / total) * 100f else 0f
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
