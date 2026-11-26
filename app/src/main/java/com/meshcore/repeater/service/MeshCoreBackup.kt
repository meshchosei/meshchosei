package com.meshcore.repeater.service

import com.meshcore.repeater.i18n.tr

import org.json.JSONArray
import org.json.JSONObject

/**
 * MeshCore/PEmesh-kompatibles JSON-Backup:
 * { "vars": { "name": ..., "radio": {freq,bw,sf,cr}, ... }, "regions": { "default", "home", "list": [{name,parent,flood}] },
 *   "filter": { "mode": "off|blacklist|whitelist", "dryrun", "blacklist": {...}, "whitelist": {...} } }
 * Der "filter"-Block ist eine Erweiterung dieser App (MeshCore simple_repeater/ZephCore Filter.cpp); Dateien ohne ihn
 * (offizielle MeshCore-/PEmesh-Exporte) werden unverändert gelesen und installiert.
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
        VarDef("cad", VarType.BOOL), VarDef("radio.rxgain", VarType.BOOL), VarDef("radio.fem.rxgain", VarType.BOOL), VarDef("prv.key", VarType.STRING, Sensitivity.PRIVATE_KEY)
    )
    private val DEFS = VARS.associateBy { it.key }

    private val INT_RE = Regex("""^-?\d+$""")
    private val NUM_RE = Regex("""^-?\d+(\.\d+)?$""")
    private val RADIO_RE = Regex("""^\d+(\.\d+)?,\d+(\.\d+)?,\d+,\d+$""")
    /** Gültiger Variablenname (verhindert beliebige "set …"-Befehle aus Metadaten-Feldern eines Templates). */
    private val KEY_RE = Regex("""^[a-z0-9][a-z0-9_.]*$""")
    /** Abschnitte, die kein Konfigurationswert sind (bei Templates ohne "vars"-Hülle). */
    private val NON_VAR_SECTIONS = setOf("regions", "filter")

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

    // ---------------- Paketfilter (Blacklist/Whitelist) ----------------

    /** Sender-/Text-Regel: secs = 0 -> "block", sonst "throttle"; prob = Verwurfwahrscheinlichkeit 1..100. */
    data class RuleEntry(val pattern: String, val secs: Int = 0, val prob: Int = 100)
    /** Whitelist-Kanal: die Firmware speichert nur den 1-Byte-Hash; der Name ist eine Komfort-Angabe. */
    data class WlChannelEntry(val hash: String, val name: String? = null)

    data class FilterBackup(
        /** "off", "blacklist" oder "whitelist". */
        val mode: String,
        val dryrun: Boolean? = null,
        val blChannels: List<String> = emptyList(),
        val blPaths: List<String> = emptyList(),
        val senders: List<RuleEntry> = emptyList(),
        val texts: List<RuleEntry> = emptyList(),
        val watch: List<String> = emptyList(),
        /** null = Firmware ohne Whitelist (Standard-MeshCore) bzw. kein Whitelist-Abschnitt. */
        val wlChannels: List<WlChannelEntry>? = null,
        val wlPaths: List<String>? = null
    ) {
        val blacklistCount get() = blChannels.size + blPaths.size + senders.size + texts.size + watch.size
        val whitelistCount get() = (wlChannels?.size ?: 0) + (wlPaths?.size ?: 0)
        val hasWhitelist get() = wlChannels != null || wlPaths != null
    }

    private val FILTER_MODES = setOf("off", "blacklist", "whitelist")
    private val PATH_HEX = Regex("""^[0-9a-fA-F]{2,8}$""")
    private val HASH_HEX = Regex("""^[0-9a-fA-F]{2}$""")
    private fun isToken(s: String) = s.isNotBlank() && s.none { it.isWhitespace() } && !isPlaceholder(s)

    private fun rulesToJson(rules: List<RuleEntry>) = JSONArray().also { a -> rules.forEach { a.put(JSONObject().put("pattern", it.pattern).put("secs", it.secs).put("prob", it.prob)) } }

    fun filterToJson(f: FilterBackup): JSONObject {
        val o = JSONObject().put("mode", f.mode)
        f.dryrun?.let { o.put("dryrun", it) }
        o.put("blacklist", JSONObject()
            .put("channels", JSONArray(f.blChannels)).put("paths", JSONArray(f.blPaths))
            .put("senders", rulesToJson(f.senders)).put("texts", rulesToJson(f.texts)).put("watch", JSONArray(f.watch)))
        if (f.hasWhitelist) {
            val ch = JSONArray().also { a -> f.wlChannels.orEmpty().forEach { c -> a.put(JSONObject().put("hash", c.hash.lowercase()).apply { c.name?.let { put("name", it) } }) } }
            o.put("whitelist", JSONObject().put("channels", ch).put("paths", JSONArray(f.wlPaths.orEmpty())))
        }
        return o
    }

    private fun strings(a: JSONArray?, valid: (String) -> Boolean): List<String> =
        if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.opt(it) as? String }.map { it.trim() }.filter(valid).distinct()

    private fun rules(a: JSONArray?): List<RuleEntry> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { i ->
        val o = a.optJSONObject(i) ?: return@mapNotNull null
        val p = o.optString("pattern").trim()
        val secs = o.optInt("secs", 0); val prob = o.optInt("prob", 100)
        if (!isToken(p) || secs !in 0..65535 || prob !in 1..100) null else RuleEntry(p, secs, prob)
    }.distinctBy { it.pattern }

    /** "filter"-Block lesen; null = nicht vorhanden/unbrauchbar (Abwärtskompatibilität). */
    fun parseFilter(o: JSONObject?): FilterBackup? {
        if (o == null) return null
        val mode = o.optString("mode", "").trim().lowercase().takeIf { it in FILTER_MODES } ?: return null
        val bl = o.optJSONObject("blacklist"); val wl = o.optJSONObject("whitelist")
        val wlCh = wl?.optJSONArray("channels")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val e = a.opt(i)
                when (e) {
                    is JSONObject -> e.optString("hash").trim().takeIf { HASH_HEX.matches(it) }?.let { WlChannelEntry(it.uppercase(), e.optString("name").trim().ifEmpty { null }) }
                    is String -> e.trim().let { t -> if (HASH_HEX.matches(t)) WlChannelEntry(t.uppercase()) else if (isToken(t)) WlChannelEntry(ChannelHash.hashHex(t), t) else null }
                    else -> null
                }
            }.distinctBy { it.hash }
        }
        return FilterBackup(
            mode = mode,
            dryrun = if (o.has("dryrun") && !o.isNull("dryrun")) o.optBoolean("dryrun") else null,
            blChannels = strings(bl?.optJSONArray("channels"), ::isToken),
            blPaths = strings(bl?.optJSONArray("paths")) { PATH_HEX.matches(it) }.map { it.uppercase() },
            senders = rules(bl?.optJSONArray("senders")),
            texts = rules(bl?.optJSONArray("texts")),
            watch = strings(bl?.optJSONArray("watch")) { isToken(it) && it != "Public" },
            wlChannels = if (wl != null) wlCh.orEmpty() else null,
            wlPaths = if (wl != null) strings(wl.optJSONArray("paths")) { PATH_HEX.matches(it) }.map { it.uppercase() } else null
        )
    }

    /**
     * CLI-Befehle, die den Repeater-Stand [current] (frisch ausgelesen) in den Backup-Stand [target] überführen:
     * überzählige Einträge entfernen, fehlende hinzufügen, danach dryrun, Modus und Ein/Aus.
     * [supportsWhitelist] = false (Standard-MeshCore): Whitelist-Befehle und "filter mode" entfallen.
     */
    fun filterCommands(target: FilterBackup, current: FilterBackup?, supportsWhitelist: Boolean): List<String> {
        val cur = current ?: FilterBackup("off")
        val c = mutableListOf<String>()
        fun <T> sync(have: List<T>, want: List<T>, key: (T) -> String, rm: (T) -> String, add: (T) -> String) {
            val wantKeys = want.map(key).toSet(); val haveKeys = have.map(key).toSet()
            have.filter { key(it) !in wantKeys }.forEach { c += rm(it) }
            want.filter { key(it) !in haveKeys }.forEach { c += add(it) }
        }
        sync(cur.blChannels, target.blChannels, { it }, { "filter channel remove $it" }, { "filter channel add $it" })
        sync(cur.blPaths, target.blPaths, { it.uppercase() }, { "filter path remove $it" }, { "filter path add $it" })
        // Regeln mit geänderten Parametern: entfernen und neu anlegen (Schlüssel inkl. secs/prob)
        val ruleKey: (RuleEntry) -> String = { "${it.pattern}|${it.secs}|${it.prob}" }
        fun ruleAdd(kind: String, r: RuleEntry) = "filter $kind add ${r.pattern}" + if (r.secs != 0 || r.prob != 100) " ${r.secs}" + (if (r.prob != 100) " ${r.prob}" else "") else ""
        sync(cur.senders, target.senders, ruleKey, { "filter sender remove ${it.pattern}" }, { ruleAdd("sender", it) })
        sync(cur.texts, target.texts, ruleKey, { "filter text remove ${it.pattern}" }, { ruleAdd("text", it) })
        sync(cur.watch, target.watch, { it }, { "filter watch remove $it" }, { "filter watch add $it" })
        if (supportsWhitelist && target.hasWhitelist) {
            sync(cur.wlChannels.orEmpty(), target.wlChannels.orEmpty(), { it.hash.uppercase() }, { "filter wl channel del ${it.hash.lowercase()}" }, { "filter wl channel add ${it.hash.lowercase()}" })
            sync(cur.wlPaths.orEmpty(), target.wlPaths.orEmpty(), { it.uppercase() }, { "filter wl path del $it" }, { "filter wl path add $it" })
        }
        target.dryrun?.let { c += "filter dryrun ${if (it) "on" else "off"}" }
        when (target.mode) {
            "off" -> c += "filter off"
            "blacklist" -> { if (supportsWhitelist) c += "filter mode blacklist"; c += "filter on" }
            "whitelist" -> if (supportsWhitelist) { c += "filter mode whitelist"; c += "filter on" }   // ohne Whitelist-Firmware: Ein/Aus unverändert lassen
        }
        return c
    }

    /** Regionen-State (Start-Scan/Backup-Einlesen) -> JSON-Liste inkl. Wildcard "*" als Wurzel. */
    fun regionEntries(state: com.meshcore.repeater.model.RegionState): List<RegionEntry> =
        listOf(RegionEntry("*", null, state.wildcardFlood)) + state.regions.map { RegionEntry(it.name, it.parent, it.floodAllowed) }

    fun buildJson(vars: LinkedHashMap<String, Any>, regions: List<RegionEntry>?, home: String?, default: String?, filter: FilterBackup? = null): String {
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
        filter?.let { root.put("filter", filterToJson(it)) }
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
        val regionCommands: List<String>,
        /** null = Backup ohne "filter"-Block (z. B. offizieller MeshCore-/PEmesh-Export). */
        val filter: FilterBackup? = null
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

    /** "radio" als Objekt {freq,bw,sf,cr} (Strings oder Zahlen) oder als String "869.618,62.5,8,8" -> [freq, bw, sf, cr]. */
    fun radioValue(raw: Any?): List<String>? {
        val parts = when (raw) {
            is JSONObject -> listOf("freq", "bw", "sf", "cr").map { k -> raw.opt(k)?.let(::fmt)?.trim().orEmpty() }
            is String -> raw.split(",").map { it.trim() }
            else -> return null
        }
        return parts.takeIf { it.size == 4 && RADIO_RE.matches(it.joinToString(",")) }
    }

    /** Boolean aus true/false, "on"/"off", "true"/"false", 1/0 (Templates sind hier uneinheitlich). */
    fun boolValue(raw: Any?): Boolean? = when (raw) {
        is Boolean -> raw
        is Number -> when (raw.toInt()) { 1 -> true; 0 -> false; else -> null }
        is String -> when (raw.trim().lowercase()) { "on", "true", "1", "yes" -> true; "off", "false", "0", "no" -> false; else -> null }
        else -> null
    }

    private fun sameValue(key: String, a: String, b: String): Boolean {
        val x = a.trim(); val y = b.trim()
        if (x.equals(y, ignoreCase = true)) return true
        if (key == "radio") {
            val p = x.split(","); val q = y.split(",")
            return p.size == 4 && q.size == 4 && p.zip(q).all { (u, v) -> u.trim().toDoubleOrNull()?.let { it == v.trim().toDoubleOrNull() } == true }
        }
        val dx = x.toDoubleOrNull(); val dy = y.toDoubleOrNull()
        return dx != null && dy != null && kotlin.math.abs(dx - dy) < 1e-6
    }

    /**
     * Diff-Abgleich (v1.21): entfernt "set <key> <wert>"-Befehle, deren Wert der Repeater laut [current] (Start-Scan,
     * Schlüssel = Backup-Variable, Wert wie im CLI) schon hat. Regionen-, Filter- und unbekannte Befehle bleiben.
     * Liefert (zu sendende Befehle, Anzahl übersprungener).
     */
    fun withoutUnchanged(commands: List<String>, current: Map<String, String>): Pair<List<String>, Int> {
        var skipped = 0
        val out = commands.filter { c ->
            if (!c.startsWith("set ")) return@filter true
            val key = c.removePrefix("set ").substringBefore(' ')
            val value = c.removePrefix("set ").substringAfter(' ', "")
            val cur = current[key] ?: return@filter true
            if (sameValue(key, value, cur)) { skipped++; false } else true
        }
        return out to skipped
    }

    /** Wirft IllegalArgumentException bei ungültiger Datei. */
    fun parse(json: String): Plan {
        val root = try { JSONObject(json) } catch (e: Exception) { throw IllegalArgumentException(tr("Keine gültige JSON-Datei: {0}", e.message)) }
        // Community-Templates (z. B. BSmesh) bringen teils nur einen Teil der Werte mit; ohne "vars"-Hülle zählen bekannte Schlüssel auf oberster Ebene
        val vars = root.optJSONObject("vars")
            ?: JSONObject().also { v -> root.keys().forEach { k -> if (k in DEFS && k !in NON_VAR_SECTIONS) v.put(k, root.opt(k)) } }.takeIf { it.length() > 0 }
            ?: throw IllegalArgumentException(tr("Abschnitt \"vars\" fehlt – kein MeshCore-Backup?"))
        val items = mutableListOf<VarItem>()
        val keys = vars.keys().asSequence().toList().sortedBy { k -> VARS.indexOfFirst { it.key == k }.let { if (it < 0) Int.MAX_VALUE else it } }
        for (key in keys) {
            val raw = vars.opt(key)
            val def = DEFS[key]
            val sens = def?.sensitivity ?: Sensitivity.NONE
            // im Template nicht gesetzt (null/leer): nicht senden, nichts überschreiben
            if (raw == null || raw == JSONObject.NULL || (raw is String && raw.isBlank())) continue
            if (isPlaceholder(raw)) { items += VarItem(key, fmt(raw), null, ItemState.PLACEHOLDER, sens); continue }
            val (display, cmd) = when {
                !KEY_RE.matches(key) -> fmt(raw) to null
                key == "radio" -> radioValue(raw)?.let { (f, bw, sf, cr) -> "$f MHz, BW $bw, SF $sf, CR $cr" to "set radio $f,$bw,$sf,$cr" } ?: (fmt(raw) to null)
                def?.type == VarType.BOOL -> boolValue(raw)?.let { b -> (if (b) "an" else "aus") to "set $key ${if (b) "on" else "off"}" } ?: (fmt(raw) to null)
                raw is Boolean -> (if (raw) "an" else "aus") to "set $key ${if (raw) "on" else "off"}"
                raw is JSONObject || raw is JSONArray -> fmt(raw) to null
                (def?.type == VarType.INT || def?.type == VarType.NUMERIC_STRING) && !INT_RE.matches(fmt(raw).trim()) -> fmt(raw) to null
                def?.type == VarType.NUMBER && !NUM_RE.matches(fmt(raw).trim()) -> fmt(raw) to null
                else -> {
                    val s = fmt(raw).trim()
                    val display = if (sens != Sensitivity.NONE && s.isNotEmpty()) "•".repeat(minOf(12, s.length)) else s
                    display to "set $key $s"
                }
            }
            items += VarItem(key, display, cmd, if (cmd == null) ItemState.INVALID else ItemState.APPLY, sens)
        }

        // "regions" als Objekt {default, home, list} oder direkt als Liste
        val regionsObj = root.optJSONObject("regions") ?: root.optJSONArray("regions")?.let { JSONObject().put("list", it) }
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
        return Plan(items, regions, home, def, hasDefault, rc, parseFilter(root.optJSONObject("filter")))
    }
}
