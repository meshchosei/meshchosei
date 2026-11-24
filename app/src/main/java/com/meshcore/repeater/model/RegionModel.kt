package com.meshcore.repeater.model

/**
 * MeshCore-/ZephCore-Regionen (CommonCLI::handleRegionCmd, RegionMap).
 *
 * Firmware-Fakten:
 *  - `region`                 -> Baum, je Zeile "name[^][ F]", Einrückung = Tiefe, Wurzel "*" (Wildcard),
 *                                "^" = Home-Region, " F" = Flood erlaubt. Antwort max. 160 Zeichen.
 *  - `region home`            -> " home is <name>"   ("*" = keine Home-Region)
 *  - `region default`         -> " default scope is <name>|<null>"
 *  - `region home <name>`     -> Region muss existieren ("Err - unknown region"), NICHT persistent bis `region save`
 *  - `region default <name>`  -> legt die Region bei Bedarf an, erlaubt Flood und speichert sofort
 *  - `region put <name> [parent]` -> neue Region mit Flood erlaubt; bestehende wird umgehängt
 *  - `region allowf|denyf <name>` / `region remove <name>` ("Err - not empty" bei Unterregionen)
 *  - Änderungen über put/remove/allowf/denyf/home werden erst mit `region save` dauerhaft.
 */
data class RegionInfo(
    val name: String,
    /** Name der Eltern-Region; "*" = direkt unter der Wildcard. */
    val parent: String,
    val floodAllowed: Boolean,
    /** Tiefe im Baum (1 = direkt unter "*"). */
    val depth: Int = 1
)

data class RegionState(
    /** null = noch nicht abgefragt, false = Firmware kennt keine Regionen. */
    val supported: Boolean? = null,
    val regions: List<RegionInfo> = emptyList(),
    /** Flood-Status der Wildcard "*" (Wurzel). */
    val wildcardFlood: Boolean = true,
    /** Home-Region; null = keine ("*"). */
    val home: String? = null,
    /** Default-Scope; null = "<null>". */
    val default: String? = null
)

object RegionParser {
    const val MAX_NAME_LEN = 30   // RegionEntry.name[31]

    private val HOME = Regex("""home is (?:now )?(\S+)""")
    private val DEFAULT = Regex("""default scope is (?:now )?(\S+)""")

    private fun clean(n: String) = n.trim().removePrefix("#")

    /** "home is bsmesh" -> "bsmesh"; "home is *" -> null. */
    fun parseHome(reply: String?): String? =
        reply?.let { HOME.find(it)?.groupValues?.get(1) }?.let(::clean)?.takeIf { it.isNotEmpty() && it != "*" }

    /** "default scope is bsmesh" -> "bsmesh"; "<null>" -> null. */
    fun parseDefault(reply: String?): String? =
        reply?.let { DEFAULT.find(it)?.groupValues?.get(1) }?.let(::clean)?.takeIf { it.isNotEmpty() && it != "<null>" && it != "*" }

    /** true, wenn die Antwort überhaupt ein "home is …" enthält (auch "*"). */
    fun isHomeReply(reply: String?) = reply != null && HOME.containsMatchIn(reply)
    fun isDefaultReply(reply: String?) = reply != null && DEFAULT.containsMatchIn(reply)

    /**
     * Baum aus "region" (Zeilen ohne "-> "-Präfix) in eine flache Liste wandeln.
     * Rückgabe: Regionen ohne Wildcard, Flood-Status der Wildcard, Home-Markierung ("^").
     */
    fun parseTree(lines: List<String>): Triple<List<RegionInfo>, Boolean, String?> {
        val out = mutableListOf<RegionInfo>()
        val stack = ArrayList<Pair<Int, String>>()   // (Einrückung, Name)
        var wildcardFlood = true
        var home: String? = null
        for (raw in lines) {
            if (raw.isBlank()) continue
            val indent = raw.length - raw.trimStart().length
            var t = raw.trim()
            if (t.startsWith("Err", true) || t.startsWith("OK", true) || t.startsWith(">") || t.startsWith("->")) continue
            val flood = t.endsWith(" F") || t == "F"
            if (flood) t = t.removeSuffix("F").trim()
            val isHome = t.endsWith("^")
            t = clean(t.removeSuffix("^"))
            if (t.isEmpty() || t.contains(' ')) continue
            while (stack.isNotEmpty() && stack.last().first >= indent) stack.removeAt(stack.lastIndex)
            if (t == "*") {
                wildcardFlood = flood
            } else {
                val parent = stack.lastOrNull()?.second ?: "*"
                out += RegionInfo(t, parent, flood, depth = stack.size.coerceAtLeast(1))
                if (isHome) home = t
            }
            stack += indent to t
        }
        return Triple(out, wildcardFlood, home)
    }

    /** Firmware RegionMap::is_name_char: '-', '$', '#', Ziffern, Buchstaben (inkl. Umlaute); keine Leerzeichen. */
    fun isValidName(name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty() || n.length > MAX_NAME_LEN || n == "*") return false
        return n.all { it == '-' || it == '$' || it == '#' || it == '_' || it.isLetterOrDigit() }
    }
}
