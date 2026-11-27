package com.meshcore.repeater.service

import com.meshcore.repeater.model.CompanionNodeInfo
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
    /** MeshCore/ZephCore `OUT_PATH_UNKNOWN`: kein bekannter Pfad -> Flood. */
    const val OUT_PATH_UNKNOWN = 0xFF

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
     * [name(32)][last_advert(4)]`. `out_path_len = OUT_PATH_UNKNOWN` (0xFF): Mit „direkt“ (0) würde MeshCore den Login nur
     * als Zero-Hop senden (`sendLogin` flutet nur bei OUT_PATH_UNKNOWN); hört der Repeater ihn nicht, gäbe es keinen
     * Flood-Versuch. Der Companion lernt den echten Pfad aus der Antwort selbst.
     */
    fun buildAddRepeaterContactFrame(pubKey: ByteArray, name: String, lastAdvertSecs: Long = 0L, outPathLen: Int = OUT_PATH_UNKNOWN): ByteArray {
        val f = ByteArray(1 + PUB_KEY_SIZE + 3 + MAX_PATH_SIZE + NAME_SIZE + 4)
        var i = 0
        f[i++] = CMD_ADD_UPDATE_CONTACT
        System.arraycopy(pubKey, 0, f, i, minOf(pubKey.size, PUB_KEY_SIZE)); i += PUB_KEY_SIZE
        f[i++] = ADV_TYPE_REPEATER.toByte()
        f[i++] = 0             // flags
        f[i++] = outPathLen.toByte()   // OUT_PATH_UNKNOWN (0xFF): Pfad unbekannt -> der Companion darf fluten
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

    // ---- Gerätedaten des Companion-Nodes (v1.25, für den Debug-Bericht) ----
    const val RESP_SELF_INFO = 0x05.toByte()
    const val RESP_DEVICE_INFO = 0x0D.toByte()

    /**
     * `RESP_SELF_INFO`: `[0x05][adv_type][tx_power][max_tx][pub_key(32)][lat(4)][lon(4)][multi_acks][loc_policy]
     * [telemetry][manual_add][freq×1000(4)][bw×1000(4)][sf][cr][name…]`. lat/lon werden übersprungen.
     */
    fun parseSelfInfo(f: ByteArray, current: CompanionNodeInfo = CompanionNodeInfo()): CompanionNodeInfo? {
        if (f.size < 4 + PUB_KEY_SIZE || f[0] != RESP_SELF_INFO) return null
        val key = RepeaterProtocol.bytesToHex(f.copyOfRange(4, 4 + PUB_KEY_SIZE))
        var c = current.copy(txPowerDbm = f[2].toInt(), maxTxPowerDbm = f[3].toInt(), publicKeyHex = key)
        if (f.size >= 58) {
            c = c.copy(
                freqMhz = RepeaterProtocol.getLe32(f, 48) / 1000.0,
                bwKhz = RepeaterProtocol.getLe32(f, 52) / 1000.0,
                sf = f[56].toInt() and 0xFF, cr = f[57].toInt() and 0xFF,
                name = cString(f, 58, f.size - 58)
            )
        }
        return c
    }

    /**
     * `RESP_DEVICE_INFO`: `[0x0D][fw_ver_code][max_contacts/2][max_channels][ble_pin(4)][build_date(12)][model(40)]
     * [version(20)]…`. Die BLE-PIN wird übersprungen.
     */
    fun parseDeviceInfo(f: ByteArray, current: CompanionNodeInfo = CompanionNodeInfo()): CompanionNodeInfo? {
        if (f.size < 4 || f[0] != RESP_DEVICE_INFO) return null
        var c = current.copy(protocolVersion = f[1].toInt() and 0xFF)
        if (f.size >= 4) c = c.copy(maxContacts = (f[2].toInt() and 0xFF) * 2, maxChannels = f[3].toInt() and 0xFF)
        if (f.size >= 20) c = c.copy(buildDate = cString(f, 8, 12))
        if (f.size >= 60) c = c.copy(model = cString(f, 20, 40))
        if (f.size >= 80) c = c.copy(firmwareVersion = cString(f, 60, 20))
        return c
    }

    /** Nullterminierter UTF-8-Text aus einem festen Feld. */
    private fun cString(f: ByteArray, off: Int, len: Int): String {
        if (off >= f.size) return ""
        val end = minOf(f.size, off + len)
        var z = off
        while (z < end && f[z] != 0.toByte()) z++
        return String(f, off, z - off, StandardCharsets.UTF_8).trim()
    }

    // ---- Fehlercodes von RESP_ERR (0x01), wörtlich MeshCore companion_radio/MyMesh.cpp ----
    const val CMD_SEND_LOGIN = 0x1A.toByte()

    /** Klartextname eines Companion-Fehlercodes, z. B. 4 -> "BAD_STATE". */
    fun errorName(code: Int): String = when (code) {
        1 -> "UNSUPPORTED_CMD"
        2 -> "NOT_FOUND"
        3 -> "TABLE_FULL"
        4 -> "BAD_STATE"
        5 -> "FILE_IO_ERROR"
        6 -> "ILLEGAL_ARG"
        else -> "UNKNOWN"
    }

    /** Name des zuletzt an den Companion gesendeten Befehls (für das System-Protokoll). */
    fun commandName(cmd: Byte?): String = when (cmd) {
        null -> "—"
        0x01.toByte() -> "APP_START"
        0x02.toByte() -> "SEND_TXT_MSG"
        0x04.toByte() -> "GET_CONTACTS"
        CMD_ADD_UPDATE_CONTACT -> "ADD_UPDATE_CONTACT"
        0x0A.toByte() -> "SYNC_NEXT_MESSAGE"
        0x16.toByte() -> "DEVICE_QUERY"
        CMD_SEND_LOGIN -> "SEND_LOGIN"
        CMD_SEND_CONTROL_DATA -> "SEND_CONTROL_DATA"
        0x42.toByte() -> "RUN_CLI_COMMAND"
        else -> "0x%02X".format(cmd.toInt() and 0xFF)
    }

    /**
     * Gehört ein RESP_ERR zum laufenden Login? Nur wenn der zuletzt gesendete Befehl `CMD_SEND_LOGIN` war. Ein Fehler
     * auf einen anderen Befehl (z. B. BAD_STATE auf ein doppeltes GET_CONTACTS) darf den Login nicht abbrechen.
     */
    fun errorBelongsToLogin(lastCommand: Byte?): Boolean = lastCommand == CMD_SEND_LOGIN
}
