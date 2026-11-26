package com.meshcore.repeater.model

/**
 * Kanal-Eingabe im Paket-Filter (v1.23). Firmware (ZephCore/MeshCore `Filter.cpp`): Kanäle heißen `#name`
 * oder exakt `Public` (`strcmp(name, "Public")`, Groß-/Kleinschreibung zählt). Die Whitelist nimmt zusätzlich
 * einen zweistelligen Hex-Hash an (`filter wl channel add d9`).
 */
object ChannelNameRules {
    private val HEX2 = Regex("""^[0-9a-fA-F]{2}$""")
    private val HEX2_0X = Regex("""^0[xX]([0-9a-fA-F]{2})$""")

    /**
     * Getrimmt; `public` (egal wie geschrieben) -> `Public`; sonst `#` voranstellen, falls es fehlt
     * (`test` -> `#test`). Whitelist: Hex-Hash `d9` / `0xD9` bleibt Hash (`d9`). Leer -> "".
     */
    fun normalize(raw: String, whitelist: Boolean = false): String {
        val t = raw.trim()
        if (t.isEmpty()) return ""
        if (t.equals("public", ignoreCase = true)) return "Public"
        if (whitelist) {
            if (HEX2.matches(t)) return t.lowercase()
            HEX2_0X.find(t)?.let { return it.groupValues[1].lowercase() }
        }
        return if (t.startsWith("#")) t else "#$t"
    }

    /** `Public` oder `#Public` in beliebiger Schreibweise. */
    fun isPublic(name: String): Boolean = name.trim().removePrefix("#").equals("public", ignoreCase = true)

    /** Nur im Blacklist-Modus: Sperren von Public braucht eine ausdrückliche Bestätigung. Whitelist: nie. */
    fun needsPublicBlockConfirm(name: String, blacklist: Boolean): Boolean = blacklist && isPublic(name)
}

/**
 * Arbeitszustand in Filterlisten (v1.23): Hinzufügen und Löschen teilen sich je Liste einen BusyKey. Der „+“-Button
 * dreht nur beim Hinzufügen, der Mülleimer nur in der Zeile, die gerade gelöscht wird.
 */
object ListBusyRules {
    fun tag(key: String, id: String): String = "$key:$id"
    fun keyOf(tag: String): String = tag.substringBefore(':')
    fun addButtonBusy(keyBusy: Boolean, deletingId: String?, key: String): Boolean =
        keyBusy && (deletingId == null || keyOf(deletingId) != key)
    fun rowDeleteBusy(keyBusy: Boolean, deletingId: String?, key: String, id: String): Boolean =
        keyBusy && deletingId == tag(key, id)
}
