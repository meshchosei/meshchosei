package com.meshcore.repeater.service

import com.meshcore.repeater.model.FilterMode
import com.meshcore.repeater.model.FilterStats
import com.meshcore.repeater.model.GpsMode
import com.meshcore.repeater.model.LedMode
import com.meshcore.repeater.model.MeshContact
import java.nio.charset.StandardCharsets
import java.util.Locale

object RepeaterProtocol {

    // === Echte ZephCore / MeshCore CLI-Befehle (RepeaterMesh.cpp & CommonCLI.cpp) ===
    const val CMD_VER = "ver"
    const val CMD_BOARD = "board"
    const val CMD_POWER = "power"
    const val CMD_REBOOT = "reboot"
    const val CMD_POWEROFF = "poweroff"
    const val CMD_START_DFU = "start dfu"
    const val CMD_START_OTA = "start ota"
    const val CMD_STOP_OTA = "stop ota"

    const val CMD_CLOCK = "clock"
    const val CMD_CLOCK_SYNC = "clock sync"
    const val CMD_ADVERT = "advert"
    const val CMD_ADVERT_ZEROHOP = "advert.zerohop"
    const val CMD_NEIGHBORS = "neighbors"
    const val CMD_DISCOVER_NEIGHBORS = "discover.neighbors"

    const val CMD_GET_RADIO = "get radio"
    const val CMD_GET_TX = "get tx"
    const val CMD_GET_FLOOD_MAX = "get flood.max"
    const val CMD_GET_FLOOD_ADVERT_INTERVAL = "get flood.advert.interval"
    const val CMD_GET_LEDS = "get leds"
    const val CMD_GET_GPS = "get gps"
    const val CMD_GET_CAD = "get cad.stats"
    const val CMD_CLEAR_STATS = "clear stats"

    // === Filter & Whitelist Commands (Filter.cpp) ===
    const val CMD_FILTER_STATUS = "filter"
    const val CMD_FILTER_STATS = "filter stats"
    const val CMD_FILTER_ON = "filter on"
    const val CMD_FILTER_OFF = "filter off"
    const val CMD_FILTER_DRYRUN_ON = "filter dryrun on"
    const val CMD_FILTER_DRYRUN_OFF = "filter dryrun off"
    const val CMD_FILTER_MODE_BLACKLIST = "filter mode blacklist"
    const val CMD_FILTER_MODE_WHITELIST = "filter mode whitelist"
    const val CMD_FILTER_RESET = "filter RESET" // Großbuchstaben erforderlich für Reset

    fun buildWlChannelAdd(channelOrName: String): String = "filter wl channel add $channelOrName"
    fun buildWlChannelDel(channelOrName: String): String = "filter wl channel del $channelOrName"
    const val CMD_FILTER_WL_CHANNEL_LIST = "filter wl channel list"

    fun buildWlPathAdd(prefixHex: String): String = "filter wl path add $prefixHex"
    fun buildWlPathDel(prefixHex: String): String = "filter wl path del $prefixHex"
    const val CMD_FILTER_WL_PATH_LIST = "filter wl path list"

    // === Blacklist & Content Rules (Filter.cpp) ===
    fun buildChannelAdd(name: String): String = "filter channel add $name"
    fun buildChannelRemove(name: String): String = "filter channel remove $name"
    const val CMD_FILTER_CHANNEL_LIST = "filter channel list"

    fun buildPathAdd(prefixHex: String): String = "filter path add $prefixHex"
    fun buildPathRemove(prefixHex: String): String = "filter path remove $prefixHex"
    const val CMD_FILTER_PATH_LIST = "filter path list"

    fun buildSenderRuleAdd(pattern: String, secs: Int = 0, prob: Int = 100): String =
        if (secs > 0) "filter sender add $pattern $secs $prob" else "filter sender add $pattern"
    fun buildSenderRuleRemove(pattern: String): String = "filter sender remove $pattern"
    const val CMD_FILTER_SENDER_LIST = "filter sender list"

    fun buildTextRuleAdd(pattern: String, secs: Int = 0, prob: Int = 100): String =
        if (secs > 0) "filter text add $pattern $secs $prob" else "filter text add $pattern"
    fun buildTextRuleRemove(pattern: String): String = "filter text remove $pattern"
    const val CMD_FILTER_TEXT_LIST = "filter text list"

    fun buildWatchAdd(name: String): String = "filter watch add $name"
    fun buildWatchRemove(name: String): String = "filter watch remove $name"
    const val CMD_FILTER_WATCH_LIST = "filter watch list"

    fun buildFilterHops(type: Int, maxHops: Int): String = "filter hops $type $maxHops"
    fun buildFilterAge(minutes: Int): String = if (minutes <= 0) "filter age off" else "filter age $minutes"

    // === Radio-Befehle (kommagetrennt nach CommonCLI.cpp) ===
    // tempradio <freq>,<bw>,<sf>,<cr>,<timeout_mins>
    fun buildTempRadio(
        freqMhz: Double,
        bwKhz: Double,
        sf: Int,
        cr: Int, // 5=4/5, 8=4/8
        timeoutMinutes: Int
    ): String = String.format(Locale.US, "tempradio %.3f,%.1f,%d,%d,%d", freqMhz, bwKhz, sf, cr, timeoutMinutes)

    // set radio <freq>,<bw>,<sf>,<cr>
    fun buildSetRadio(
        freqMhz: Double,
        bwKhz: Double,
        sf: Int,
        cr: Int
    ): String = String.format(Locale.US, "set radio %.3f,%.1f,%d,%d", freqMhz, bwKhz, sf, cr)

    fun buildSetTx(powerDbm: Int): String = "set tx $powerDbm"
    fun buildSetFloodMax(hops: Int): String = "set flood.max $hops"
    fun buildSetFloodAdvertInterval(hours: Int): String = "set flood.advert.interval $hours"
    fun buildSetPassword(newPass: String): String = "password $newPass"

    // === Hardware-Befehle ===
    fun buildSetLed(mode: LedMode): String = mode.cliCommand
    fun buildSetGps(mode: GpsMode): String = mode.cliCommand

    // =========================================================================
    // MeshCore Companion BLE Protocol Framing (CompanionProtocol.h)
    // =========================================================================
    const val CMD_APP_START = 0x01.toByte()
    const val CMD_SEND_TXT_MSG = 0x02.toByte()
    const val CMD_GET_CONTACTS = 0x04.toByte()
    const val CMD_SEND_LOGIN = 0x1A.toByte()
    const val CMD_RUN_CLI_COMMAND = 0x42.toByte()

    const val PACKET_OK = 0x00.toByte()
    const val PACKET_ERROR = 0x01.toByte()
    const val PACKET_CONTACT_START = 0x02.toByte()
    const val PACKET_CONTACT = 0x03.toByte()
    const val PACKET_CONTACT_END = 0x04.toByte()
    const val PACKET_SELF_INFO = 0x05.toByte()
    const val PACKET_SENT = 0x06.toByte()
    const val PACKET_CONTACT_MSG_V3 = 0x10.toByte()
    const val PACKET_CLI_REPLY = 0x1D.toByte()

    const val TXT_TYPE_PLAIN = 0.toByte()
    const val TXT_TYPE_CLI_DATA = 1.toByte() // Standard MeshCore Remote-CLI Nachrichtentyp
    const val TXT_TYPE_CLI_COMMAND = 3.toByte()

    const val PUB_KEY_SIZE = 32
    const val MAX_PATH_SIZE = 64

    fun putLe32(buf: ByteArray, offset: Int, v: Long) {
        buf[offset] = (v and 0xFF).toByte()
        buf[offset + 1] = ((v shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((v shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((v shr 24) and 0xFF).toByte()
    }

    fun getLe32(buf: ByteArray, offset: Int): Long {
        if (offset + 4 > buf.size) return 0L
        return (buf[offset].toLong() and 0xFF) or
                ((buf[offset + 1].toLong() and 0xFF) shl 8) or
                ((buf[offset + 2].toLong() and 0xFF) shl 16) or
                ((buf[offset + 3].toLong() and 0xFF) shl 24)
    }

    fun bytesToHex(bytes: ByteArray, maxBytes: Int = bytes.size): String {
        val limit = minOf(bytes.size, maxBytes)
        val sb = StringBuilder(limit * 2)
        for (i in 0 until limit) {
            sb.append(String.format("%02x", bytes[i]))
        }
        return sb.toString()
    }

    // CMD_APP_START: cmd(1) + ver(1) + res(2) + timestamp(4) = 8 Bytes
    fun buildAppStartFrame(appVer: Int = 3): ByteArray {
        val frame = ByteArray(8)
        frame[0] = CMD_APP_START
        frame[1] = (appVer and 0xFF).toByte()
        frame[2] = 0
        frame[3] = 0
        putLe32(frame, 4, System.currentTimeMillis() / 1000L)
        return frame
    }

    // CMD_GET_CONTACTS: cmd(1) + since_timestamp(4) = 5 Bytes
    fun buildGetContactsFrame(since: Long = 0L): ByteArray {
        val frame = ByteArray(5)
        frame[0] = CMD_GET_CONTACTS
        putLe32(frame, 1, since)
        return frame
    }

    // CMD_SEND_LOGIN: cmd(1) + pub_key(32) + password(N)
    fun buildLoginFrame(pubKey: ByteArray, password: String): ByteArray {
        val pwBytes = (password + "\u0000").toByteArray(StandardCharsets.UTF_8)
        val frame = ByteArray(1 + PUB_KEY_SIZE + pwBytes.size)
        frame[0] = CMD_SEND_LOGIN
        val copyKeyLen = minOf(pubKey.size, PUB_KEY_SIZE)
        System.arraycopy(pubKey, 0, frame, 1, copyKeyLen)
        System.arraycopy(pwBytes, 0, frame, 1 + PUB_KEY_SIZE, pwBytes.size)
        return frame
    }

    // CMD_SEND_TXT_MSG (Remote Admin CLI über Mesh):
    // cmd(1) + txt_type(1) + attempt(1) + timestamp(4) + pub_key_prefix(6) + text(N)
    fun buildRemoteCliFrame(pubKeyPrefix6: ByteArray, command: String): ByteArray {
        val cmdBytes = command.toByteArray(StandardCharsets.UTF_8)
        val frame = ByteArray(1 + 1 + 1 + 4 + 6 + cmdBytes.size)
        var i = 0
        frame[i++] = CMD_SEND_TXT_MSG
        frame[i++] = TXT_TYPE_CLI_DATA // 1
        frame[i++] = 0.toByte() // attempt
        putLe32(frame, i, System.currentTimeMillis() / 1000L)
        i += 4
        val copyLen = minOf(pubKeyPrefix6.size, 6)
        System.arraycopy(pubKeyPrefix6, 0, frame, i, copyLen)
        i += 6
        System.arraycopy(cmdBytes, 0, frame, i, cmdBytes.size)
        return frame
    }

    // CMD_RUN_CLI_COMMAND (Lokal am Companion-Node): cmd(1) + text(N)
    fun buildLocalCliFrame(command: String): ByteArray {
        val cmdBytes = command.toByteArray(StandardCharsets.UTF_8)
        val frame = ByteArray(1 + cmdBytes.size)
        frame[0] = CMD_RUN_CLI_COMMAND
        System.arraycopy(cmdBytes, 0, frame, 1, cmdBytes.size)
        return frame
    }

    // Parse PACKET_CONTACT: [0x03][pub_key(32)][type(1)][flags(1)][out_path_len(1)][out_path(64)][name(32)][last_adv(4)][lat(4)][lon(4)][lastmod(4)]
    fun parseContactFrame(data: ByteArray): MeshContact? {
        if (data.size < 1 + PUB_KEY_SIZE + 1 + 1 + 1 + MAX_PATH_SIZE + 32) return null
        if (data[0] != PACKET_CONTACT) return null

        var offset = 1
        val pubKey = ByteArray(PUB_KEY_SIZE)
        System.arraycopy(data, offset, pubKey, 0, PUB_KEY_SIZE)
        offset += PUB_KEY_SIZE

        val type = data[offset++].toInt() and 0xFF
        val flags = data[offset++].toInt() and 0xFF
        val outPathLen = data[offset++].toInt() and 0xFF

        offset += MAX_PATH_SIZE // Path bytes überspringen

        // Name: bis zu 32 Bytes nullterminiert
        val nameBytes = ByteArray(32)
        val nameCopyLen = minOf(32, data.size - offset)
        System.arraycopy(data, offset, nameBytes, 0, nameCopyLen)
        offset += 32

        val rawName = String(nameBytes, StandardCharsets.UTF_8).trimEnd('\u0000', ' ')
        val nodeName = if (rawName.isBlank()) "Node-${bytesToHex(pubKey, 4)}" else rawName

        var lastAdv = 0L
        var lat = 0.0
        var lon = 0.0
        if (offset + 12 <= data.size) {
            lastAdv = getLe32(data, offset)
            offset += 4
            val rawLat = getLe32(data, offset).toInt()
            offset += 4
            val rawLon = getLe32(data, offset).toInt()
            lat = rawLat / 1000000.0
            lon = rawLon / 1000000.0
        }

        val fullHex = bytesToHex(pubKey)
        val prefix = fullHex.take(12)

        return MeshContact(
            pubKey = pubKey,
            pubKeyHex = fullHex,
            keyPrefix = prefix,
            type = type,
            flags = flags,
            outPathLen = outPathLen,
            name = nodeName,
            lastAdvertTimestamp = lastAdv,
            gpsLat = lat,
            gpsLon = lon
        )
    }

    // Parse PACKET_CONTACT_MSG_V3: [0x10][SNR*4][0][0][6-byte prefix][path_len][txt_type][timestamp(4)][text...]
    fun parseContactMsgV3(data: ByteArray): Triple<ByteArray, String, Float>? {
        if (data.size < 16 || data[0] != PACKET_CONTACT_MSG_V3) return null
        val snr = (data[1].toInt()).toFloat() / 4.0f
        val prefix = ByteArray(6)
        System.arraycopy(data, 4, prefix, 0, 6)
        val textBytes = ByteArray(data.size - 16)
        System.arraycopy(data, 16, textBytes, 0, textBytes.size)
        val text = String(textBytes, StandardCharsets.UTF_8).trimEnd('\u0000', '\r', '\n')
        return Triple(prefix, text, snr)
    }

    // === Text-Antworten parsen ===
    fun parseVersion(response: String): String? {
        val line = response.lineSequence().firstOrNull { it.contains("zephcore", ignoreCase = true) || it.contains("meshcore", ignoreCase = true) || Regex("""\d+\.\d+\.\d+""").containsMatchIn(it) }
        return line?.trim()
    }

    fun parseFilterStats(text: String, current: FilterStats): FilterStats {
        var updated = current
        val lines = text.lines()
        for (line in lines) {
            val lower = line.lowercase(Locale.ROOT)
            when {
                lower.contains("filter: enabled") || lower.contains("filter is on") || lower.contains("filter: on") || lower.contains("enabled: 1") || lower.contains("enabled: true") -> {
                    updated = updated.copy(filterEnabled = true)
                }
                lower.contains("filter: disabled") || lower.contains("filter is off") || lower.contains("filter: off") || lower.contains("enabled: 0") || lower.contains("enabled: false") -> {
                    updated = updated.copy(filterEnabled = false)
                }
                lower.contains("dryrun: on") || lower.contains("dry-run on") || lower.contains("dryrun: enabled") -> {
                    updated = updated.copy(dryRun = true)
                }
                lower.contains("dryrun: off") || lower.contains("dry-run off") || lower.contains("dryrun: disabled") -> {
                    updated = updated.copy(dryRun = false)
                }
                lower.contains("mode: whitelist") || lower.contains("mode whitelist") -> {
                    updated = updated.copy(mode = FilterMode.WHITELIST)
                }
                lower.contains("mode: blacklist") || lower.contains("mode blacklist") -> {
                    updated = updated.copy(mode = FilterMode.BLACKLIST)
                }
                lower.contains("allowed:") -> {
                    val count = line.substringAfter("allowed:").trim().split(" ").firstOrNull()?.toLongOrNull()
                    if (count != null) updated = updated.copy(allowedPackets = count)
                }
                lower.contains("dropped:") || lower.contains("blocked:") -> {
                    val count = line.substringAfter(":").trim().split(" ").firstOrNull()?.toLongOrNull()
                    if (count != null) updated = updated.copy(droppedPackets = count)
                }
            }
        }
        return updated
    }
}
