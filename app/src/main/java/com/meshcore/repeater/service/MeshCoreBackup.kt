package com.meshcore.repeater.service

import com.meshcore.repeater.i18n.tr

import org.json.JSONArray
import org.json.JSONObject

/**
 * MeshCore/PEmesh-kompatibles JSON-Backup:
 * { "vars": { "name": ..., "radio": {freq,bw,sf,cr}, ... }, "regions": { "default", "home", "list": [{name,parent,flood}] } }
 */
object MeshCoreBackup {
    enum class VarType { STRING, BOOL, INT, NUMBER, NUMERIC_STRING, RADIO }
    enum class Sensitivity { NONE, PRIVATE_KEY, GUEST_PASSWORD }
    data class VarDef(val key: String, val type: VarType, val sensitivity: Sensitivity = Sensitivity.NONE)

    /** Reihenfolge = Reihenfolge im JSON (wie MeshCore/PEmesh-Export). */
    val VARS = listOf(
        VarDef("name", VarType.STRING), VarDef("repeat", VarType.BOOL), VarDef("allow.read.only", VarType.BOOL),
        VarDef("radio", VarType.RADIO), VarDef("tx", VarType.INT), VarDef("af", VarType.NUMBER),
        VarDef("rxdelay", VarType.NUMBER), VarDef("txdelay", VarType.NUMBER), VarDef("direct.txdelay", VarType.NUMBER),
        VarDef("flood.max", VarType.INT), VarDef("flood.max.unscoped", VarType.INT), VarDef("flood.max.advert", VarType.INT),
        VarDef("flood.advert.interval", VarType.INT), VarDef("advert.interval", VarType.INT),
        VarDef("guest.password", VarType.STRING, Sensitivity.GUEST_PASSWORD), VarDef("lat", VarType.NUMBER), VarDef("lon", VarType.NUMBER),
        VarDef("int.thresh", VarType.INT), VarDef("agc.reset.interval", VarType.INT), VarDef("multi.acks", VarType.NUMERIC_STRING),
        VarDef("owner.info", VarType.STRING), VarDef("path.hash.mode", VarType.INT), VarDef("loop.detect", VarType.STRING),
        VarDef("cad", VarType.BOOL), VarDef("radio.rxgain", VarType.BOOL), VarDef("prv.key", VarType.STRING, Sensitivity.PRIVATE_KEY)
    )
    private val DEFS = VARS.associateBy { it.key }

    private val INT_RE = Regex("""^-?\d+$""")
    private val NUM_RE = Regex("""^-?\d+(\.\d+)?$""")
    private val RADIO_RE = Regex("""^\d+(\.\d+)?,\d+(\.\d+)?,\d+,\d+$""")

    /** "XXX"-Platzhalter (z. B. in geteilten Backups geschwärzte Passwörter/Keys). */
    fun isPlaceholder(v: Any?): Boolean = v is String && Regex("^[Xx]{3,}$").matches(v.trim())

    /** CLI-Antwort von "get <key>" -> JSON-Wert (null = nicht übernehmen, z. B. "adaptive" oder "Removed"). */
    fun replyToJson(def: VarDef, reply: String): Any? {
        val v = reply.trim()
        return when (def.type) {
            VarType.STRING -> v
            VarType.BOOL -> when (v.lowercase().substringBefore(' ')) { "on", "1", "true" -> true; "off", "0", "false" -> false; else -> null }
            VarType.INT -> if (INT_RE.matches(v)) v.toLong().let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it } else null
            VarType.NUMBER -> if (NUM_RE.matches(v)) { if (INT_RE.matches(v)) v.toInt() else v.toDouble() } else null
            VarType.NUMERIC_STRING -> if (INT_RE.matches(v)) v else null
            VarType.RADIO -> if (RADIO_RE.matches(v)) {
                val p = v.split(",")
                JSONObject().put("freq", p[0]).put("bw", p[1]).put("sf", p[2]).put("cr", p[3])
            } else null
        }
    }

    data class RegionEntry(val name: String, val parent: String?, val flood: Boolean)

    /**
     * Ausgabe von "region" (Baum mit Einrückung, "^" = Home, " F" = Flood erlaubt), z. B.
     *   * F
     *    bsmesh^ F
     *     sub
     */
    fun parseRegionTree(lines: List<String>): Pair<List<RegionEntry>, String?> {
        val out = mutableListOf<RegionEntry>()
        val stack = ArrayList<Pair<Int, String>>() // (indent, name)
        var home: String? = null
        for (raw in lines) {
            if (raw.isBlank()) continue
            val indent = raw.length - raw.trimStart().length
            var t = raw.trim()
            if (t.startsWith("Err", true) || t.startsWith("OK", true) || t.startsWith(">")) continue
            val flood = t.endsWith(" F") || t == "F"
            if (flood) t = t.removeSuffix("F").trim()
            val isHome = t.endsWith("^")
            t = t.removeSuffix("^").trim()
            if (t.isEmpty() || t.contains(' ')) continue
            while (stack.isNotEmpty() && stack.last().first >= indent) stack.removeAt(stack.lastIndex)
            val parent = stack.lastOrNull()?.second
            out += RegionEntry(t, parent, flood)
            if (isHome) home = t
            stack += indent to t
        }
        return out to home
    }

    fun buildJson(vars: LinkedHashMap<String, Any>, regions: List<RegionEntry>?, home: String?, default: String?): String {
        val v = JSONObject()
        for (def in VARS) vars[def.key]?.let { v.put(def.key, it) }
        vars.keys.filter { it !in DEFS }.forEach { v.put(it, vars[it]) }
        val root = JSONObject().put("vars", v)
        if (regions != null && regions.isNotEmpty()) {
            val list = JSONArray()
            regions.forEach { r -> list.put(JSONObject().put("name", r.name).put("parent", r.parent ?: JSONObject.NULL).put("flood", r.flood)) }
            root.put("regions", JSONObject()
                .put("default", default ?: JSONObject.NULL)
                .put("home", home ?: JSONObject.NULL)
                .put("list", list))
        }
        return root.toString(2).replace("\\/", "/")
    }

    // ---------------- Wiederherstellung ----------------

    enum class ItemState { APPLY, PLACEHOLDER, INVALID }
    data class VarItem(val key: String, val display: String, val command: String?, val state: ItemState, val sensitivity: Sensitivity)
    data class Plan(
        val vars: List<VarItem>,
        val regions: List<RegionEntry>,
        val regionHome: String?,
        val regionDefault: String?,
        val hasRegionDefault: Boolean,
        val regionCommands: List<String>
    ) {
        fun commands(includePrivateKey: Boolean, includeGuestPassword: Boolean): List<String> {
            val chosen = vars.filter { it.state == ItemState.APPLY && it.command != null }.filter {
                when (it.sensitivity) {
                    Sensitivity.PRIVATE_KEY -> includePrivateKey
                    Sensitivity.GUEST_PASSWORD -> includeGuestPassword
                    Sensitivity.NONE -> true
                }
            }
            // Funkparameter und Identität zuletzt: sie können die (Remote-)Verbindung beenden
            val late = setOf("radio", "tx", "prv.key")
            val early = chosen.filter { it.key !in late }.map { it.command!! }
            val tail = listOf("tx", "radio", "prv.key").mapNotNull { k -> chosen.firstOrNull { it.key == k }?.command }
            return early + regionCommands + tail
        }
    }

    private fun fmt(v: Any?): String = when (v) {
        null, JSONObject.NULL -> ""
        is Double -> if (v == Math.floor(v) && !v.isInfinite() && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()
        else -> v.toString()
    }

    /** Wirft IllegalArgumentException bei ungültiger Datei. */
    fun parse(json: String): Plan {
        val root = try { JSONObject(json) } catch (e: Exception) { throw IllegalArgumentException(tr("Keine gültige JSON-Datei: {0}", e.message)) }
        val vars = root.optJSONObject("vars") ?: throw IllegalArgumentException(tr("Abschnitt \"vars\" fehlt – kein MeshCore-Backup?"))
        val items = mutableListOf<VarItem>()
        val keys = vars.keys().asSequence().toList().sortedBy { k -> VARS.indexOfFirst { it.key == k }.let { if (it < 0) Int.MAX_VALUE else it } }
        for (key in keys) {
            val raw = vars.opt(key)
            val def = DEFS[key]
            val sens = def?.sensitivity ?: Sensitivity.NONE
            if (isPlaceholder(raw)) { items += VarItem(key, fmt(raw), null, ItemState.PLACEHOLDER, sens); continue }
            val (display, cmd) = when {
                key == "radio" -> {
                    val r = raw as? JSONObject
                    val f = r?.opt("freq")?.let(::fmt); val bw = r?.opt("bw")?.let(::fmt); val sf = r?.opt("sf")?.let(::fmt); val cr = r?.opt("cr")?.let(::fmt)
                    if (f.isNullOrBlank() || bw.isNullOrBlank() || sf.isNullOrBlank() || cr.isNullOrBlank()) "${fmt(raw)}" to null
                    else "$f MHz, BW $bw, SF $sf, CR $cr" to "set radio $f,$bw,$sf,$cr"
                }
                raw is Boolean -> (if (raw) "an" else "aus") to "set $key ${if (raw) "on" else "off"}"
                raw is JSONObject || raw is JSONArray || raw == null || raw == JSONObject.NULL -> fmt(raw) to null
                else -> {
                    val s = fmt(raw)
                    val display = if (sens != Sensitivity.NONE && s.isNotEmpty()) "•".repeat(minOf(12, s.length)) else s
                    display to "set $key $s".trimEnd().let { if (key == "guest.password") "set guest.password $s" else it }
                }
            }
            items += VarItem(key, display, cmd, if (cmd == null) ItemState.INVALID else ItemState.APPLY, sens)
        }

        val regionsObj = root.optJSONObject("regions")
        val regions = mutableListOf<RegionEntry>()
        var home: String? = null; var def: String? = null; var hasDefault = false
        if (regionsObj != null) {
            regionsObj.optJSONArray("list")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = o.optString("name").trim()
                    if (name.isEmpty() || isPlaceholder(name)) continue
                    val parent = if (o.isNull("parent")) null else o.optString("parent").trim().ifEmpty { null }
                    regions += RegionEntry(name, parent, o.optBoolean("flood", true))
                }
            }
            home = if (regionsObj.isNull("home")) null else regionsObj.optString("home").trim().ifEmpty { null }
            hasDefault = regionsObj.has("default")
            def = if (regionsObj.isNull("default")) null else regionsObj.optString("default").trim().ifEmpty { null }
        }
        val rc = mutableListOf<String>()
        if (regions.isNotEmpty() || home != null || hasDefault) {
            regions.filter { it.name != "*" }.forEach { r ->
                rc += if (r.parent == null || r.parent == "*") "region put ${r.name}" else "region put ${r.name} ${r.parent}"
            }
            regions.forEach { r -> rc += "region ${if (r.flood) "allowf" else "denyf"} ${r.name}" }
            home?.let { rc += "region home $it" }
            if (hasDefault) rc += "region default ${def ?: "<null>"}"
            rc += "region save"
        }
        return Plan(items, regions, home, def, hasDefault, rc)
    }
}
