package com.meshcore.repeater.service

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Kanal-Hash wie in ZephCore Filter::getChannelHash():
 * secret = "Public" ? PUBLIC_CHANNEL_SECRET : SHA256(name)[0..15];  hash = SHA256(secret)[0].
 * Die Whitelist speichert im Repeater NUR diesen 1-Byte-Hash – "filter wl channel list" liefert z. B. "d9,11".
 */
object ChannelHash {
    private val PUBLIC_SECRET = byteArrayOf(
        0x8B.toByte(), 0x33, 0x87.toByte(), 0xE9.toByte(), 0xC5.toByte(), 0xCD.toByte(), 0xEA.toByte(), 0x6A,
        0xC9.toByte(), 0xE5.toByte(), 0xED.toByte(), 0xBA.toByte(), 0xA1.toByte(), 0x15, 0xCD.toByte(), 0x72
    )

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)

    /** Hash als zweistellige Großbuchstaben-Hex-Zahl, z. B. "#test" -> "D9", "Public" -> "11". */
    fun hashHex(name: String): String {
        val n = name.trim()
        val secret = if (n == "Public") PUBLIC_SECRET else sha256(n.toByteArray(StandardCharsets.UTF_8)).copyOf(16)
        return "%02X".format(sha256(secret)[0].toInt() and 0xFF)
    }
}

/**
 * Antworten der Filter-Listenbefehle (ZephCore Filter.cpp, Antwort max. 160 Zeichen, EINE Zeile):
 *  - "filter channel list"     -> "#test (d9),Public (11)"   bzw. "None"
 *  - "filter wl channel list"  -> "d9,11"                    bzw. "None"
 *  - "filter path list" / "filter wl path list" -> "a1b2,c3d4" (ggf. ": <Zähler>", ".." = gekürzt) bzw. "None"
 *  - "filter sender|text list" -> "bot block,spam throttle 30s 50%" bzw. "None" (FilterStat::formatRuleList)
 *  - "filter watch list"       -> "Public (11),#x (ab)" ("Public" ist immer enthalten und fest)
 */
object FilterListParser {
    private val BL_CHANNEL = Regex("""^(\S+)\s*\(([0-9a-fA-F]{2})\)""")
    private val HASH = Regex("""^[0-9a-fA-F]{2}$""")
    private val PATH = Regex("""^[0-9a-fA-F]{2,16}$""")

    private fun tokens(v: String): List<String> =
        v.trim().removePrefix(">").trim().split(',').map { it.trim().removeSuffix("..").trim() }.filter { it.isNotEmpty() }

    fun isNone(v: String) = v.trim().removePrefix(">").trim().equals("none", ignoreCase = true)

    fun parseBlacklistChannels(v: String): List<String> = tokens(v).mapNotNull { t ->
        BL_CHANNEL.find(t)?.groupValues?.get(1) ?: t.substringBefore(' ').takeIf { it.startsWith("#") && it.length > 1 }
    }.distinct()

    /** Hashes in Großbuchstaben. */
    fun parseWhitelistChannelHashes(v: String): List<String> =
        tokens(v).filter { HASH.matches(it) }.map { it.uppercase() }.distinct()

    fun parsePaths(v: String): List<String> =
        tokens(v).map { it.substringBefore(':').trim() }.filter { PATH.matches(it) }.map { it.uppercase() }.distinct()

    private val RULE = Regex("""^(\S+) (?:block|throttle (\d+)s)(?: (\d+)%)?$""")

    /** Sender-/Text-Regeln: (Muster, Sekunden, Wahrscheinlichkeit). */
    fun parseRules(v: String): List<MeshCoreBackup.RuleEntry> = tokens(v).mapNotNull { t ->
        RULE.find(t)?.groupValues?.let { g -> MeshCoreBackup.RuleEntry(g[1], g[2].toIntOrNull() ?: 0, g[3].toIntOrNull() ?: 100) }
    }.distinctBy { it.pattern }

    /** Watch-Liste ohne den festen Eintrag "Public". */
    fun parseWatch(v: String): List<String> = parseBlacklistChannels(v).filter { it != "Public" }

    /** Gültige (nicht fremde) Antwort auf den jeweiligen Listenbefehl. */
    fun isValidListReply(cmd: String, v: String): Boolean {
        if (isNone(v)) return true
        if (v.trim().startsWith("filter", ignoreCase = true)) return false   // Status-/Hilfe-/Bestätigungszeilen
        return when (cmd.trim().lowercase()) {
            "filter channel list" -> parseBlacklistChannels(v).isNotEmpty()
            "filter wl channel list" -> Regex("""^[0-9a-fA-F]{2}(,[0-9a-fA-F]{2})*(\.\.)?$""").matches(v.trim())
            "filter path list", "filter wl path list" -> tokens(v).all { PATH.matches(it.substringBefore(':').trim()) } && parsePaths(v).isNotEmpty()
            "filter sender list", "filter text list" -> parseRules(v).isNotEmpty()
            "filter watch list" -> v.trim().removePrefix(">").trim().startsWith("Public")
            else -> true
        }
    }
}
