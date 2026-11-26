package com.meshcore.repeater.service

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Schlanker, offline-sicherer Update-Check (v1.19).
 *
 * Einmal beim App-Start im Hintergrund: GET auf die GitHub-API „latest release“, ausgewertet werden nur
 * `tag_name` und `html_url`. Jeder Fehler (kein Netz, Timeout, HTTP-Fehler, kaputtes JSON) führt still zu `null`.
 * Es werden keine persönlichen Daten übertragen (nur der übliche HTTP-Request mit allgemeinem User-Agent).
 */
object UpdateCheck {
    const val LATEST_API_URL = "https://api.github.com/repos/meshchosei/meshchosei/releases/latest"
    const val RELEASES_PAGE_URL = "https://github.com/meshchosei/meshchosei/releases/latest"
    private const val TIMEOUT_MS = 3_000
    private const val USER_AGENT = "meshchosei-update-check (Android)"

    data class ReleaseInfo(val tagName: String, val htmlUrl: String) {
        /** Anzeige-Version mit führendem „v“, z. B. „v1.20“. */
        val displayVersion: String get() = "v" + tagName.trim().removePrefix("v").removePrefix("V")
    }

    /** „v1.20“ / „1.20“ / „1.20-rc1“ → (1, 20); alles andere → null. Minor wird numerisch verglichen (1.20 > 1.19 > 1.2). */
    fun parseVersion(raw: String?): Pair<Int, Int>? {
        val m = Regex("""^[vV]?(\d+)\.(\d+)""").find(raw?.trim() ?: return null) ?: return null
        val major = m.groupValues[1].toIntOrNull() ?: return null
        val minor = m.groupValues[2].toIntOrNull() ?: return null
        return major to minor
    }

    /** Vergleich Major.Minor: >0 wenn [a] neuer ist als [b], 0 bei Gleichheit, <0 wenn älter; null bei ungültigen Versionen. */
    fun compare(a: String?, b: String?): Int? {
        val va = parseVersion(a) ?: return null
        val vb = parseVersion(b) ?: return null
        return compareValuesBy(va, vb, { it.first }, { it.second })
    }

    /** true nur, wenn [remote] echt neuer als [local] ist (ungültige Werte → false). */
    fun isNewer(remote: String?, local: String?): Boolean = (compare(remote, local) ?: 0) > 0

    /** Antwort der GitHub-API auswerten; ungültiges JSON, fehlendes/ungültiges `tag_name` → null. */
    fun parseLatest(json: String?): ReleaseInfo? {
        if (json.isNullOrBlank()) return null
        return try {
            val o = JSONObject(json)
            val tag = o.optString("tag_name", "").trim()
            if (parseVersion(tag) == null) null
            else ReleaseInfo(tag, o.optString("html_url", "").trim().takeIf { it.startsWith("https://") } ?: RELEASES_PAGE_URL)
        } catch (_: Exception) { null }
    }

    /** Blockierender Abruf (nur auf Dispatchers.IO aufrufen). Liefert ausschließlich bei Erfolg ein Ergebnis, sonst still null. */
    fun fetchLatest(): ReleaseInfo? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(LATEST_API_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) null
            else parseLatest(conn.inputStream.bufferedReader().use { it.readText() })
        } catch (_: Throwable) {
            null
        } finally {
            try { conn?.disconnect() } catch (_: Throwable) {}
        }
    }

    /** Ergebnis nur liefern, wenn es echt neuer als [localVersion] ist. */
    fun newerRelease(info: ReleaseInfo?, localVersion: String): ReleaseInfo? =
        info?.takeIf { isNewer(it.tagName, localVersion) }

    /** Debug-APK vergleicht gegen diese simulierte Version, damit der Hinweis live testbar ist ([DEBUG-Test]). */
    const val DEBUG_SIMULATED_VERSION = "1.00"
}
