package com.meshcore.repeater.service

/**
 * Datenschutzfreundlicher Diagnose-Bericht für Fehlerberichte (v1.25, STYLE_GUIDE §2.15). Rein und ohne
 * Android-Abhängigkeiten (Gerätewerte kommen als [Input]), damit vollständig unit-getestet.
 *
 * - Wird **nur** auf ausdrückliche Nutzeraktion lokal erzeugt; nichts wird automatisch übertragen.
 * - Markdown für GitHub Issues. Companion-Node und Ziel-Repeater stehen in **getrennten** Abschnitten.
 * - Geheimnisse werden vor der Ausgabe geschwärzt ([sanitize]): privater Schlüssel (`prv.key` und lange Hex-Ketten),
 *   Admin-/Gast-Passwörter, `wifi.pass`, `mqtt.*.password` sowie alle der App bekannten Passwort-Werte.
 */
object DebugReportBuilder {
    const val REDACTED = "[REDACTED]"
    const val ISSUES_URL = "https://github.com/meshchosei/meshchosei/issues/new"

    data class LogLine(val text: String, val isOutgoing: Boolean)

    /** Daten des Companion-Nodes (aus RESP_SELF_INFO / RESP_DEVICE_INFO). */
    data class CompanionNode(
        val connection: String,              // "USB-OTG" / "BLE-OTA" (+ Detail)
        val name: String = "",
        val model: String = "",
        val firmwareVersion: String = "",
        val buildDate: String = "",
        val protocolVersion: Int? = null,
        val publicKeyPrefix: String = "",
        val radio: String = "",              // z. B. "869.618 MHz / 62.5 kHz / SF8 / CR8"
        val txPower: String = "",            // z. B. "22 dBm (max. 22 dBm)"
        val maxContacts: Int? = null,
        val maxChannels: Int? = null
    )

    /** Daten des Ziel-Repeaters (direkt per USB-CLI oder per Remote-Admin über den Companion). */
    data class TargetRepeater(
        val access: String,                  // "direkt per USB-CLI" / "Remote-Admin über Companion"
        val name: String = "",
        val loggedIn: Boolean? = null,       // null = nicht zutreffend (Direktverbindung)
        val board: String = "",
        val radioChip: String = "",
        val firmwareVersion: String = "",
        val bootloader: String = "",
        val capabilities: List<Pair<String, String>> = emptyList()
    )

    data class Input(
        val generatedAt: String,
        // App & Gerät
        val appVersionName: String,
        val appVersionCode: Int,
        val buildType: String,
        val androidRelease: String,
        val androidSdk: Int,
        val manufacturer: String,
        val model: String,
        val appLanguage: String,
        val effectiveLanguage: String,
        val palette: String,
        val themeMode: String,
        /** Verbindungsstatus der App (z. B. "USB-OTG verbunden", "nicht verbunden"). */
        val connectionSummary: String,
        /** Companion-Node, oder null mit Begründung in [companionAbsent]. */
        val companion: CompanionNode? = null,
        val companionAbsent: String = "nicht verbunden",
        /** Ziel-Repeater, oder null mit Begründung in [repeaterAbsent]. */
        val repeater: TargetRepeater? = null,
        val repeaterAbsent: String = "nicht verbunden / nicht eingeloggt",
        // Meldungen & Protokolle
        val messages: List<String> = emptyList(),
        val cliLog: List<LogLine> = emptyList(),
        val systemLog: List<LogLine> = emptyList(),
        /** Der App bekannte Geheimnisse (z. B. gespeicherte Admin-Passwörter): werden überall wörtlich geschwärzt. */
        val knownSecrets: Collection<String> = emptyList()
    )

    /** Schlüssel, deren Wert nie im Bericht stehen darf (CLI-Befehle `set …`, `get …`-Antworten, `key=value`). */
    private val SECRET_KEY = Regex(
        """(?i)\b(prv\.key|private\.key|wifi\.pass(?:word)?|mqtt\.[\w.]*pass(?:word)?|admin\.pass(?:word)?|guest\.pass(?:word)?|password|passwd|pwd)\b(\s*[=:]\s*|\s+)(\S.*)"""
    )
    /** Hex-Ketten ab 96 Zeichen: privater Schlüssel (64 Byte = 128 Hex); Public-Keys (64 Hex) bleiben sichtbar. */
    private val LONG_HEX = Regex("""\b[0-9a-fA-F]{96,}\b""")
    /** Ausgehende Abfrage eines Geheimnisses: die direkt folgende Antwort wird komplett geschwärzt. */
    private val SECRET_QUERY = Regex("""(?i)\bget\s+(prv\.key|private\.key|wifi\.pass\w*|mqtt\.[\w.]*pass\w*|\w*\.?password)\b""")

    /** Geheimnisse in einem Text schwärzen. [knownSecrets] (ab 3 Zeichen) werden zusätzlich wörtlich ersetzt. */
    fun sanitize(text: String, knownSecrets: Collection<String> = emptyList()): String {
        var t = text
        knownSecrets.map { it.trim() }.filter { it.length >= 3 }.distinct().sortedByDescending { it.length }
            .forEach { t = t.replace(it, REDACTED) }
        t = t.lines().joinToString("\n") { line -> SECRET_KEY.replace(line) { m -> "${m.groupValues[1]}${m.groupValues[2]}$REDACTED" } }
        t = LONG_HEX.replace(t, REDACTED)
        return t
    }

    /** Protokoll schwärzen: Zeilen + die Antwort direkt nach `get prv.key`/`get …password` u. Ä. */
    fun sanitizeLog(lines: List<LogLine>, knownSecrets: Collection<String> = emptyList()): List<LogLine> {
        var redactNextReply = false
        return lines.map { l ->
            val out = when {
                redactNextReply && !l.isOutgoing -> { redactNextReply = false; l.copy(text = replyPrefix(l.text) + REDACTED) }
                else -> l.copy(text = sanitize(l.text, knownSecrets))
            }
            if (l.isOutgoing && SECRET_QUERY.containsMatchIn(l.text)) redactNextReply = true
            out
        }
    }

    /** Präfix einer Antwortzeile („< [Mesh] “, „> “) erhalten, damit das Protokoll lesbar bleibt. */
    private fun replyPrefix(s: String): String = Regex("""^\s*(<\s*(\[[^\]]*]\s*)?)?(->\s*)?(>\s*)?""").find(s)?.value.orEmpty()

    /** Firmware-Typ aus der Versionsangabe (`ver` bzw. DEVICE_INFO): ZephCore, sonst MeshCore. */
    fun firmwareType(version: String): String = when {
        version.isBlank() || version == "—" -> "—"
        version.contains("zeph", ignoreCase = true) -> "ZephCore"
        else -> "MeshCore"
    }

    fun build(i: Input): String {
        val sec = i.knownSecrets
        fun v(s: String) = sanitize(s.ifBlank { "—" }, sec)
        fun n(x: Int?) = x?.toString() ?: "—"
        val sb = StringBuilder()
        sb.appendLine("# meshchōsei Debug-Bericht")
        sb.appendLine()
        sb.appendLine("_Lokal erstellt am ${i.generatedAt}. Passwörter und private Schlüssel sind durch `$REDACTED` ersetzt. Entferne vor dem Teilen jede Zeile, die du nicht zeigen möchtest._")
        sb.appendLine()
        sb.appendLine("## App & Gerät")
        sb.appendLine("- meshchōsei: ${v(i.appVersionName)} (${i.appVersionCode}, ${v(i.buildType)})")
        sb.appendLine("- Android: ${v(i.androidRelease)} (SDK ${i.androidSdk})")
        sb.appendLine("- Gerät: ${v("${i.manufacturer} ${i.model}".trim())}")
        sb.appendLine("- App-Sprache: ${v(i.appLanguage)} (aktiv: ${v(i.effectiveLanguage)})")
        sb.appendLine("- Palette / Farbschema: ${v(i.palette)} / ${v(i.themeMode)}")
        sb.appendLine("- Verbindung: ${v(i.connectionSummary)}")
        sb.appendLine()
        sb.appendLine("## Companion-Node")
        val c = i.companion
        if (c == null) sb.appendLine("- ${v(i.companionAbsent)}")
        else {
            sb.appendLine("- Verbindungsart: ${v(c.connection)}")
            sb.appendLine("- Node-Name: ${v(c.name)}")
            sb.appendLine("- Board / Modell: ${v(c.model)}")
            sb.appendLine("- Firmware: ${v(firmwareType(c.firmwareVersion))} ${v(c.firmwareVersion)}")
            sb.appendLine("- Build-Datum: ${v(c.buildDate)}")
            sb.appendLine("- Protokollversion: ${n(c.protocolVersion)}")
            sb.appendLine("- Public-Key: ${v(c.publicKeyPrefix)}")
            sb.appendLine("- Funk: ${v(c.radio)}")
            sb.appendLine("- Sendeleistung: ${v(c.txPower)}")
            sb.appendLine("- Max. Kontakte / Kanäle: ${n(c.maxContacts)} / ${n(c.maxChannels)}")
        }
        sb.appendLine()
        sb.appendLine("## Ziel-Repeater")
        val r = i.repeater
        if (r == null) sb.appendLine("- ${v(i.repeaterAbsent)}")
        else {
            sb.appendLine("- Zugang: ${v(r.access)}")
            if (r.name.isNotBlank()) sb.appendLine("- Repeater: ${v(r.name)}")
            r.loggedIn?.let { sb.appendLine("- Eingeloggt: ${if (it) "ja" else "nein"}") }
            sb.appendLine("- Board: ${v(r.board)}")
            sb.appendLine("- Funkchip: ${v(r.radioChip)}")
            sb.appendLine("- Firmware: ${v(firmwareType(r.firmwareVersion))} ${v(r.firmwareVersion)}")
            sb.appendLine("- Bootloader: ${v(r.bootloader)}")
            sb.appendLine("- Fähigkeiten: " + if (r.capabilities.isEmpty()) "—" else r.capabilities.joinToString(", ") { (k, x) -> "$k=${v(x)}" })
        }
        sb.appendLine()
        sb.appendLine("## Meldungen (${i.messages.size})")
        appendBlock(sb, i.messages.map { sanitize(it, sec) })
        val cli = sanitizeLog(i.cliLog, sec)
        sb.appendLine("## CLI-Protokoll (${cli.size} Zeilen)")
        appendBlock(sb, cli.map { it.text })
        val sys = sanitizeLog(i.systemLog, sec)
        sb.appendLine("## System-Protokoll (${sys.size} Zeilen)")
        appendBlock(sb, sys.map { it.text })
        return sb.toString().trimEnd() + "\n"
    }

    /** Code-Block; dreifache Backticks im Inhalt entschärft, damit der Block nicht vorzeitig endet. */
    private fun appendBlock(sb: StringBuilder, lines: List<String>) {
        sb.appendLine("```")
        if (lines.isEmpty()) sb.appendLine("(leer)") else lines.forEach { sb.appendLine(it.replace("```", "ˋˋˋ")) }
        sb.appendLine("```")
        sb.appendLine()
    }

    /** Dateiname für „Als Datei speichern“, z. B. `meshchosei-debug-20261127-0912.txt`. */
    fun fileName(stamp: String): String = "meshchosei-debug-${stamp.filter { it.isLetterOrDigit() || it == '-' }}.txt"
}
