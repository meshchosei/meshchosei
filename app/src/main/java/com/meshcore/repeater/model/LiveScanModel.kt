package com.meshcore.repeater.model

import kotlin.math.abs

/**
 * Ein bei der Live-Umgebungssuche (v1.24) direkt erreichbarer Repeater. [snr]/[rssi] = beim Companion gemessene
 * Empfangswerte seiner Antwort; [remoteSnr] = wie gut der Repeater unsere Suchanfrage gehört hat.
 */
data class LiveRepeater(
    val pubKey: ByteArray,
    val pubKeyHex: String,
    val snr: Float,
    val rssi: Int,
    val remoteSnr: Float,
    val pathLen: Int = 0,
    val seenAtMs: Long = 0L,
    /** Echter Name, sobald bekannt (die Discover-Antwort selbst enthält keinen; nachgefüllt per `get name`). */
    val name: String? = null
) {
    override fun equals(other: Any?): Boolean = other is LiveRepeater && other.pubKeyHex == pubKeyHex &&
        other.snr == snr && other.rssi == rssi && other.remoteSnr == remoteSnr && other.pathLen == pathLen && other.seenAtMs == seenAtMs &&
        other.name == name
    override fun hashCode(): Int = pubKeyHex.hashCode()
}

/** Zustand der Live-Suche: [tag] der laufenden Suche (0 = keine), [scanning] während des Scan-Fensters. */
data class LiveScanState(
    val scanning: Boolean = false,
    val tag: Long = 0L,
    val results: List<LiveRepeater> = emptyList(),
    val startedAtMs: Long = 0L
)

/** Reine Regeln der Live-Suche, unit-getestet. */
object LiveScanRules {
    /** Scan-Fenster mit Braille-Animation. Spätere Antworten derselben Suche werden weiter übernommen. */
    const val SCAN_WINDOW_MS = 4000L

    /** Stärkstes Signal zuerst: SNR absteigend, bei gleichem SNR RSSI absteigend, dann stabil nach Key. */
    fun sortBySignal(list: List<LiveRepeater>): List<LiveRepeater> =
        list.sortedWith(compareByDescending<LiveRepeater> { it.snr }.thenByDescending { it.rssi }.thenBy { it.pubKeyHex })

    /**
     * Neue Antwort einsortieren: je Repeater nur ein Eintrag (die jüngste Messung), danach nach Signal sortiert.
     * Ein bereits nachgefüllter Name bleibt erhalten.
     */
    fun merge(current: List<LiveRepeater>, incoming: LiveRepeater): List<LiveRepeater> {
        val old = current.firstOrNull { it.pubKeyHex == incoming.pubKeyHex }
        val merged = if (incoming.name == null && old?.name != null) incoming.copy(name = old.name) else incoming
        return sortBySignal(current.filterNot { it.pubKeyHex == incoming.pubKeyHex } + merged)
    }

    /** Platzhalter, solange kein Name bekannt ist: kurzes Key-Präfix, z. B. „cf3a8206…“. */
    fun placeholderName(pubKeyHex: String): String = "${pubKeyHex.take(8).lowercase()}…"

    /**
     * Ist [name] nur ein Platzhalter statt eines echten Repeater-Namens? Leer, unser Key-Präfix „cf3a8206…“ oder der
     * Ersatzname „Node-cf3a8206“, den die App für Kontakte ohne Namen vergibt.
     */
    fun isPlaceholderName(name: String?, pubKeyHex: String): Boolean {
        val n = name?.trim().orEmpty()
        if (n.isEmpty()) return true
        val p = pubKeyHex.take(8).lowercase()
        return n.equals(placeholderName(pubKeyHex), ignoreCase = true) || n.equals("Node-$p", ignoreCase = true)
    }

    /**
     * Anzeigename eines Live-Treffers. Die Discover-Antwort enthält keinen Namen; deshalb sofort der Name aus den
     * gespeicherten Kontakten des Companions (gleicher Public-Key), sonst der Platzhalter.
     */
    fun displayName(r: LiveRepeater, known: MeshContact?): String =
        r.name?.takeUnless { isPlaceholderName(it, r.pubKeyHex) }
            ?: known?.name?.takeUnless { isPlaceholderName(it, r.pubKeyHex) } ?: placeholderName(r.pubKeyHex)

    /**
     * Derselbe Repeater? Public-Keys ohne Groß-/Kleinschreibung über ihr gemeinsames Hex-Präfix verglichen – Listen
     * halten teils nur 8/12 Hex-Zeichen (Präfix) oder den vollen Key (64). Mindestens 8 Hex-Zeichen (4 Byte) nötig.
     */
    fun sameKey(a: String?, b: String?): Boolean {
        val x = a?.trim()?.lowercase().orEmpty(); val y = b?.trim()?.lowercase().orEmpty()
        val n = minOf(x.length, y.length)
        return n >= 8 && x.regionMatches(0, y, 0, n)
    }

    /** Gespeicherten Kontakt zum Live-Treffer finden (gleicher Public-Key, Groß-/Kleinschreibung und Präfix egal). */
    fun findKnown(r: LiveRepeater, contacts: List<MeshContact>): MeshContact? =
        contacts.firstOrNull { sameKey(it.pubKeyHex, r.pubKeyHex) }

    /**
     * Angezeigter Name des gewählten Repeaters im Verbindungs-Screen: Der live per `get name` gelesene Name
     * ([readName], gelesen für [readKey]) hat Vorrang, wenn er nicht leer ist und zum gewählten Repeater gehört.
     */
    fun shownName(target: MeshContact, readName: String?, readKey: String?): String {
        val n = readName?.trim().orEmpty()
        return if (n.isNotEmpty() && sameKey(readKey, target.pubKeyHex)) n else target.name
    }

    /**
     * Nach dem Login vom Repeater gelesener Name (`get name`): übernehmen, wenn er gültig ist und sich vom
     * angezeigten unterscheidet. null = nichts zu ändern.
     */
    fun nameAfterGetName(current: MeshContact, fetched: String?): MeshContact? {
        val n = fetched?.trim().orEmpty()
        if (n.isEmpty() || n.length > 32 || isPlaceholderName(n, current.pubKeyHex) || n == current.name) return null
        return current.copy(name = n)
    }

    /** Ergebnis von [applyFetchedName]: neuer gewählter Repeater, Live-Liste und Kontaktliste. */
    data class NameUpdate(val target: MeshContact, val live: List<LiveRepeater>, val contacts: List<MeshContact>)

    /**
     * `get name` des gewählten Repeaters überall nachziehen (v1.24): im Login-Bereich ([target]), im Live-Eintrag
     * mit gleichem Public-Key und im Kontakt mit gleichem Public-Key. null = nichts zu ändern (ungültiger Name,
     * schon aktuell).
     */
    fun applyFetchedName(target: MeshContact, live: List<LiveRepeater>, contacts: List<MeshContact>, fetched: String?): NameUpdate? {
        val n = fetched?.trim().orEmpty()
        if (n.isEmpty() || n.length > 32 || isPlaceholderName(n, target.pubKeyHex)) return null
        val key = target.pubKeyHex
        val newLive = live.map { if (sameKey(it.pubKeyHex, key) && it.name != n) it.copy(name = n) else it }
        val newContacts = contacts.map { if (sameKey(it.pubKeyHex, key) && it.name != n) it.copy(name = n) else it }
        if (target.name == n && newLive == live && newContacts == contacts) return null
        return NameUpdate(target.copy(name = n), newLive, newContacts)
    }

    /**
     * Beim (Neu-)Einlesen der Kontakte: Steht im Companion nur ein Platzhalter, aber der echte Name wurde schon per
     * `get name` gelernt, bleibt der gelernte Name (sonst springt die Anzeige zurück auf „cf3a8206…“).
     */
    fun keepLearnedName(contact: MeshContact, learned: String?): MeshContact =
        if (learned != null && isPlaceholderName(contact.name, contact.pubKeyHex)) contact.copy(name = learned) else contact

    /** Kontakt für Login-Bereich: bekannter Kontakt behält Name/Pfad und bekommt die Live-Werte, sonst neu (direkt). */
    fun toContact(r: LiveRepeater, known: MeshContact?): MeshContact =
        known?.copy(name = displayName(r, known), snr = r.snr, rssi = r.rssi, lastSeenMillis = r.seenAtMs) ?: MeshContact(
            pubKey = r.pubKey, pubKeyHex = r.pubKeyHex, keyPrefix = r.pubKeyHex.take(12), type = 2,
            outPathLen = 0, name = displayName(r, null), snr = r.snr, rssi = r.rssi, lastSeenMillis = r.seenAtMs
        )

    /** „SNR +11,5 dB“ – Dezimalkomma außer im Englischen. */
    fun formatSnr(snr: Float, decimalComma: Boolean = true): String {
        val sign = if (snr > 0f) "+" else if (snr < 0f) "−" else "±"
        val tenths = Math.round(abs(snr) * 10f)
        val txt = "${tenths / 10}${if (decimalComma) "," else "."}${tenths % 10}"
        return "SNR $sign$txt dB"
    }

    /** „−87 dBm“ (typografisches Minus). */
    fun formatRssi(rssi: Int): String = if (rssi < 0) "−${-rssi} dBm" else "$rssi dBm"
}
