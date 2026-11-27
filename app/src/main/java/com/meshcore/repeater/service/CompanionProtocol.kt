package com.meshcore.repeater.service

import com.meshcore.repeater.model.LiveRepeater
import java.nio.charset.StandardCharsets

/**
 * Companion-Binärprotokoll für die Live-Umgebungssuche (v1.24). Quellen:
 * MeshCore `examples/companion_radio/MyMesh.cpp` (CMD_SEND_CONTROL_DATA, onControlDataRecv, CMD_ADD_UPDATE_CONTACT),
 * `examples/simple_repeater/MyMesh.cpp` (CTL_TYPE_NODE_DISCOVER_REQ/RESP), ZephCore `app/CompanionMesh.cpp` /
 * `app/RepeaterMesh.cpp` (gleiches Format).
 *
 * Ablauf:
 *  1. App -> Companion: `[0x37][0x80][filter = 1<<ADV_TYPE_REPEATER][tag(4, LE)][since(4, LE) = 0]`.
 *     Der Companion sendet das als PAYLOAD_TYPE_CONTROL **Zero-Hop** (nur direkt erreichbare Knoten) und antwortet OK.
 *  2. Jeder Repeater in direkter Reichweite antwortet nach zufälliger Verzögerung (Zero-Hop) mit
 *     `[0x90 | ADV_TYPE_REPEATER][seine Empfangs-SNR×4][tag(4)][pub_key(32)]`.
 *  3. Companion -> App: `PUSH_CODE_CONTROL_DATA [0x8E][SNR×4 (int8)][RSSI (int8)][path_len][payload…]` – SNR/RSSI
 *     sind die beim Companion gemessenen Werte dieser Antwort.
 * Repeater mit `disable_fwd`, versteckte Knoten und ein voller Discover-Limiter antworten nicht.
 */
object CompanionProtocol {
    const val CMD_ADD_UPDATE_CONTACT = 0x09.toByte()
    const val CMD_SEND_CONTROL_DATA = 0x37.toByte()
    const val PUSH_CODE_CONTROL_DATA = 0x8E.toByte()

    const val CTL_TYPE_NODE_DISCOVER_REQ = 0x80
    const val CTL_TYPE_NODE_DISCOVER_RESP = 0x90
    const val ADV_TYPE_REPEATER = 2

    private const val PUB_KEY_SIZE = 32
    private const val MAX_PATH_SIZE = 64
    private const val NAME_SIZE = 32

    /** Suchanfrage nur nach Repeatern, mit vollem Public-Key in der Antwort (prefix_only = 0), ohne „since“-Grenze. */
    fun buildDiscoverRepeatersFrame(tag: Long): ByteArray {
        val f = ByteArray(1 + 1 + 1 + 4 + 4)
        f[0] = CMD_SEND_CONTROL_DATA
        f[1] = CTL_TYPE_NODE_DISCOVER_REQ.toByte()
        f[2] = (1 shl ADV_TYPE_REPEATER).toByte()
        RepeaterProtocol.putLe32(f, 3, tag)
        RepeaterProtocol.putLe32(f, 7, 0L)
        return f
    }

    /**
     * Wertet `PUSH_CODE_CONTROL_DATA` aus. Liefert nur eine Antwort eines **Repeaters** auf genau unsere Suche
     * ([expectedTag]) mit vollem Public-Key – alles andere (andere Knotentypen, fremde/alte Suche, Anfragen,
     * gekürzte Frames) ist null.
     */
    fun parseDiscoverResponse(frame: ByteArray, expectedTag: Long, nowMs: Long = System.currentTimeMillis()): LiveRepeater? {
        if (frame.size < 4 + 6 + PUB_KEY_SIZE || frame[0] != PUSH_CODE_CONTROL_DATA) return null
        val p = 4
        val head = frame[p].toInt() and 0xFF
        if ((head and 0xF0) != CTL_TYPE_NODE_DISCOVER_RESP) return null
        if ((head and 0x0F) != ADV_TYPE_REPEATER) return null
        if (RepeaterProtocol.getLe32(frame, p + 2) != (expectedTag and 0xFFFFFFFFL)) return null
        val key = frame.copyOfRange(p + 6, p + 6 + PUB_KEY_SIZE)
        return LiveRepeater(
            pubKey = key,
            pubKeyHex = RepeaterProtocol.bytesToHex(key),
            snr = frame[1] / 4f,
            rssi = frame[2].toInt(),
            remoteSnr = frame[p + 1] / 4f,
            pathLen = frame[3].toInt() and 0xFF,
            seenAtMs = nowMs
        )
    }

    /**
     * Kontakt im Companion anlegen/aktualisieren, damit `CMD_SEND_LOGIN` ihn findet (der Companion sucht den Login-
     * Empfänger per `lookupContactByPubKey`). Format: `[0x09][pub_key(32)][type][flags][out_path_len][out_path(64)]
     * [name(32)][last_advert(4)]`. `out_path_len = 0`: direkt (Zero-Hop gefunden).
     */
    fun buildAddRepeaterContactFrame(pubKey: ByteArray, name: String, lastAdvertSecs: Long = 0L): ByteArray {
        val f = ByteArray(1 + PUB_KEY_SIZE + 3 + MAX_PATH_SIZE + NAME_SIZE + 4)
        var i = 0
        f[i++] = CMD_ADD_UPDATE_CONTACT
        System.arraycopy(pubKey, 0, f, i, minOf(pubKey.size, PUB_KEY_SIZE)); i += PUB_KEY_SIZE
        f[i++] = ADV_TYPE_REPEATER.toByte()
        f[i++] = 0             // flags
        f[i++] = 0             // out_path_len: direkt
        i += MAX_PATH_SIZE     // out_path leer
        val nb = truncateUtf8(name, NAME_SIZE - 1)
        System.arraycopy(nb, 0, f, i, nb.size); i += NAME_SIZE
        RepeaterProtocol.putLe32(f, i, lastAdvertSecs)
        return f
    }

    /** UTF-8 auf höchstens [max] Bytes kürzen, ohne ein Zeichen zu zerschneiden. */
    private fun truncateUtf8(s: String, max: Int): ByteArray {
        var b = s.toByteArray(StandardCharsets.UTF_8)
        var t = s
        while (b.size > max && t.isNotEmpty()) { t = t.dropLast(1); b = t.toByteArray(StandardCharsets.UTF_8) }
        return b
    }
}
