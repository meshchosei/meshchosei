package com.meshcore.repeater.service

/**
 * Eindeutige Zuordnung von Remote-CLI-Antworten (LoRa) zum auslösenden Befehl.
 *
 * MeshCore (examples/simple_repeater/MyMesh.cpp) und ZephCore (app/RepeaterMesh.cpp) unterstützen in
 * `handleCommand` ein optionales Präfix "XX|" ("optional prefix (for companion radio CLI)"): Ist
 * `command[2] == '|'` und der Befehl länger als 4 Zeichen, wird das Präfix an den Anfang der Antwort
 * gespiegelt. Jede Antwort kommt als genau EIN Datagramm (Reply-Puffer 160 Zeichen + 3 Byte Präfix).
 *
 * Damit lässt sich eine verspätete Antwort (z. B. auf "get name" nach dessen Timeout) sicher erkennen
 * und verwerfen, statt sie anhand des Inhalts fälschlich dem nächsten Befehl ("get owner.info") zuzuordnen.
 */
object CliReplyTag {
    private const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz"
    /** Companion CMD_SEND_TXT_MSG: Text höchstens MAX_TEXT_LEN = 160 Byte (MeshCore/ZephCore). */
    const val MAX_COMMAND_BYTES = 160
    private val TAGGED = Regex("""^([0-9a-z]{2})\|""")

    /** Zweistelliges Tag aus einer laufenden Nummer (1296 verschiedene Werte, danach Wiederholung). */
    fun tagFor(seq: Int): String {
        val n = Math.floorMod(seq, ALPHABET.length * ALPHABET.length)
        return "${ALPHABET[n / ALPHABET.length]}${ALPHABET[n % ALPHABET.length]}"
    }

    /** Befehl mit Tag versehen – nur, wenn das Ergebnis noch in einen Companion-Textframe passt (sonst null). */
    fun wrap(tag: String, cmd: String): String? {
        val c = cmd.trim()
        val tagged = "$tag|$c"
        return if (c.length > 1 && tagged.toByteArray(Charsets.UTF_8).size <= MAX_COMMAND_BYTES) tagged else null
    }

    /** Zerlegt eine Antwort in (Tag oder null, Rest ohne Tag). */
    fun split(reply: String): Pair<String?, String> {
        val m = TAGGED.find(reply) ?: return null to reply
        return m.groupValues[1] to reply.substring(m.range.last + 1)
    }

    enum class Match { CURRENT, STALE, UNTAGGED }

    /**
     * CURRENT = Antwort gehört zum laufenden Befehl; STALE = Tag eines früheren Befehls (oder keiner aktiv);
     * UNTAGGED = Antwort ohne Tag. Läuft ein getaggter Befehl, ist auch UNTAGGED nicht seine Antwort.
     */
    fun classify(reply: String, expectedTag: String?): Match {
        val (tag, _) = split(reply)
        return when {
            tag == null -> Match.UNTAGGED
            tag == expectedTag -> Match.CURRENT
            else -> Match.STALE
        }
    }
}
