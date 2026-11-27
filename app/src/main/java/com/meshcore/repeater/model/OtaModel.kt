package com.meshcore.repeater.model

import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Firmware-/OTA-Update über Nordic BLE DFU (v1.26, STYLE_GUIDE §2.16). Rein und ohne Android-Abhängigkeiten,
 * damit Zustandsmaschine, Dateiprüfung und Gerätewahl vollständig unit-getestet sind.
 *
 * Firmware-Realität (Quellen: MeshCore `src/helpers/NRF52Board.cpp`, `CommonCLI.cpp`; ZephCore
 * `adapters/board/ZephyrBoard.cpp`, `adapters/ble/ble_dfu.cpp`):
 * - **MeshCore nRF52** `start ota`: bleibt in der App, startet BLE mit dem Legacy-DFU-Dienst von Adafruit
 *   (`00001530-1212-efde-1523-785feabcd123`) unter dem Namen `<BOARD>_OTA` und antwortet `OK - mac: AA:BB:…`.
 *   Den Sprung in den Bootloader macht die Nordic-Bibliothek selbst („buttonless“).
 * - **ZephCore nRF52** `start ota`: setzt GPREGRET = 0xA8 und startet direkt im BLE-OTA-Bootloader neu
 *   (`OK - rebooting to BLE DFU (name: …)`; die Antwort kommt wegen des Neustarts oft nicht mehr an).
 * - Nordic-SDK-Bootloader melden sich als `DfuTarg` (Secure DFU `FE59`), Adafruit-Bootloader als `AdaDFU`.
 */
enum class OtaPhase { IDLE, ENABLING_BOOTLOADER, DFU_INIT, UPLOADING, COMPLETED, ABORTED, ERROR;
    val isRunning get() = this == ENABLING_BOOTLOADER || this == DFU_INIT || this == UPLOADING
    val isTerminal get() = this == COMPLETED || this == ABORTED || this == ERROR
    /** Nummer des sichtbaren Schritts 1–4 in der Fortschrittsanzeige (0 = noch nicht gestartet). */
    val step get() = when (this) { IDLE -> 0; ENABLING_BOOTLOADER -> 1; DFU_INIT -> 2; UPLOADING -> 3; COMPLETED -> 4; ABORTED, ERROR -> 0 }
}

/** Geprüftes Firmware-Paket (Kopie im App-Cache). */
data class FirmwarePackage(val displayName: String, val sizeBytes: Long, val localPath: String)

/** Gefundenes BLE-Gerät im DFU-/OTA-Modus. */
data class DfuTarget(val address: String, val name: String, val rssi: Int, val kind: DfuKind, val seenAtMs: Long = 0L)

enum class DfuKind {
    /** Nordic Secure DFU (`FE59`, z. B. `DfuTarg`). */
    SECURE,
    /** Legacy-DFU-Bootloader (Adafruit `AdaDFU`, Dienst `00001530-…`). */
    LEGACY_BOOTLOADER,
    /** Anwendung mit Buttonless-DFU (MeshCore nach `start ota`: `<BOARD>_OTA`). */
    APP_BUTTONLESS
}

data class OtaState(
    val phase: OtaPhase = OtaPhase.IDLE,
    val firmware: FirmwarePackage? = null,
    val target: DfuTarget? = null,
    val percent: Int = 0,
    /** Aktuelle Übertragungsrate in kB/s (Nordic: `speed` in B/ms = kB/s). */
    val speedKbs: Float = 0f,
    val avgSpeedKbs: Float = 0f,
    val currentPart: Int = 1,
    val partsTotal: Int = 1,
    val message: String? = null,
    /** Schritt (1–4), in dem abgebrochen wurde bzw. der Fehler auftrat – für die Phasenanzeige. */
    val stoppedAtStep: Int = 0
) {
    val canStart get() = firmware != null && target != null && !phase.isRunning
}

sealed class OtaEvent {
    data class FirmwareChosen(val pkg: FirmwarePackage) : OtaEvent()
    object FirmwareCleared : OtaEvent()
    data class TargetChosen(val target: DfuTarget) : OtaEvent()
    object TargetCleared : OtaEvent()
    /** Nutzer hat per Schieberegler bestätigt. */
    object Start : OtaEvent()
    /** Nordic: `onDeviceConnecting` / `onEnablingDfuMode`. */
    object EnablingBootloader : OtaEvent()
    /** Nordic: `onDfuProcessStarting` / `onDfuProcessStarted` / `onFirmwareValidating`. */
    object DfuInit : OtaEvent()
    data class Progress(val percent: Int, val speedKbs: Float, val avgSpeedKbs: Float, val part: Int = 1, val partsTotal: Int = 1) : OtaEvent()
    object Completed : OtaEvent()
    object Aborted : OtaEvent()
    data class Failed(val message: String) : OtaEvent()
    /** Komplett zurück auf Anfang: Paket, Ziel, Fortschritt und Status werden geleert (nicht während eines Laufs). */
    object Reset : OtaEvent()
}

/**
 * Zustandsmaschine. Regeln:
 * - Start nur mit Paket + Ziel und wenn nichts läuft.
 * - Phasen laufen vorwärts. Einzige erlaubte Rückkehr: DFU-Init → Bootloader aktivieren, solange noch kein Byte
 *   übertragen ist (Buttonless-Sprung: die Bibliothek startet, merkt „App-Modus“ und schaltet erst dann um).
 * - Prozent steigen nur (0–100); während des Uploads bleibt die Phase UPLOADING.
 * - Nach Fertig/Abbruch/Fehler zählen nur Reset und neue Auswahl; späte Bibliotheksereignisse werden ignoriert.
 * - Reset leert alles (Paket, Ziel, Fortschritt, Status).
 * - Paket/Ziel lassen sich während eines laufenden Updates nicht ändern.
 */
object OtaStateMachine {
    fun reduce(s: OtaState, e: OtaEvent): OtaState = when (e) {
        is OtaEvent.FirmwareChosen -> if (s.phase.isRunning) s else s.copy(firmware = e.pkg, phase = OtaPhase.IDLE, percent = 0, message = null)
        OtaEvent.FirmwareCleared -> if (s.phase.isRunning) s else s.copy(firmware = null, phase = OtaPhase.IDLE, percent = 0, message = null)
        is OtaEvent.TargetChosen -> if (s.phase.isRunning) s else s.copy(target = e.target, phase = OtaPhase.IDLE, percent = 0, message = null)
        OtaEvent.TargetCleared -> if (s.phase.isRunning) s else s.copy(target = null, phase = OtaPhase.IDLE, percent = 0, message = null)
        OtaEvent.Start -> if (!s.canStart) s else s.copy(phase = OtaPhase.ENABLING_BOOTLOADER, percent = 0, speedKbs = 0f, avgSpeedKbs = 0f, currentPart = 1, partsTotal = 1, message = null, stoppedAtStep = 0)
        OtaEvent.EnablingBootloader -> when (s.phase) {
            OtaPhase.ENABLING_BOOTLOADER -> s
            OtaPhase.DFU_INIT -> if (s.percent == 0) s.copy(phase = OtaPhase.ENABLING_BOOTLOADER) else s
            else -> s
        }
        OtaEvent.DfuInit -> if (s.phase == OtaPhase.ENABLING_BOOTLOADER) s.copy(phase = OtaPhase.DFU_INIT) else s
        is OtaEvent.Progress -> if (s.phase != OtaPhase.ENABLING_BOOTLOADER && s.phase != OtaPhase.DFU_INIT && s.phase != OtaPhase.UPLOADING) s else {
            val partChanged = e.part != s.currentPart
            val p = e.percent.coerceIn(0, 100)
            s.copy(
                phase = OtaPhase.UPLOADING,
                // mehrteilige Pakete (SoftDevice + App): je Teil wieder ab 0 %, innerhalb eines Teils nur steigend
                percent = if (partChanged) p else maxOf(s.percent, p),
                speedKbs = e.speedKbs.coerceAtLeast(0f), avgSpeedKbs = e.avgSpeedKbs.coerceAtLeast(0f),
                currentPart = e.part.coerceAtLeast(1), partsTotal = e.partsTotal.coerceAtLeast(1)
            )
        }
        OtaEvent.Completed -> if (s.phase.isRunning) s.copy(phase = OtaPhase.COMPLETED, percent = 100, speedKbs = 0f) else s
        OtaEvent.Aborted -> if (s.phase.isRunning) s.copy(phase = OtaPhase.ABORTED, speedKbs = 0f, stoppedAtStep = s.phase.step) else s
        is OtaEvent.Failed -> if (s.phase.isRunning || s.phase == OtaPhase.IDLE) s.copy(phase = OtaPhase.ERROR, speedKbs = 0f, message = e.message, stoppedAtStep = s.phase.step) else s
        // v1.26 (Live-Test): Zurücksetzen leert ALLES – Paket, Ziel, Fortschritt, Status – für einen sauberen neuen Durchlauf
        OtaEvent.Reset -> if (s.phase.isRunning) s else OtaState()
    }
}

/** Prüfung des gewählten Firmware-Pakets. */
object FirmwareFileRules {
    /** nRF52840: 1 MB Flash; großzügige Obergrenze, damit nichts Abwegiges (Backup-Archive o. Ä.) geflasht wird. */
    const val MAX_SIZE_BYTES = 4L * 1024 * 1024

    sealed class Check {
        object Ok : Check()
        object NotZip : Check()
        object Empty : Check()
        object TooLarge : Check()
        /** ZIP, aber kein Nordic-DFU-Paket (fehlt `manifest.json` bzw. `.bin` + `.dat`). */
        object NotDfuPackage : Check()
        /** Das ZIP ließ sich nicht lesen. */
        object Corrupt : Check()
    }

    fun hasZipExtension(name: String): Boolean = name.trim().lowercase(Locale.ROOT).endsWith(".zip")

    /** Name und Größe prüfen (vor dem Kopieren). */
    fun checkMeta(name: String, sizeBytes: Long): Check = when {
        !hasZipExtension(name) -> Check.NotZip
        sizeBytes == 0L -> Check.Empty
        sizeBytes > MAX_SIZE_BYTES -> Check.TooLarge
        else -> Check.Ok
    }

    /** Inhalt prüfen: Nordic-DFU-Pakete enthalten `manifest.json` oder mindestens ein `.bin`/`.hex` plus `.dat`. */
    fun checkEntries(entries: List<String>): Check {
        if (entries.isEmpty()) return Check.NotDfuPackage
        val n = entries.map { it.substringAfterLast('/').lowercase(Locale.ROOT) }
        if ("manifest.json" in n) return Check.Ok
        val hasImage = n.any { it.endsWith(".bin") || it.endsWith(".hex") }
        val hasInit = n.any { it.endsWith(".dat") }
        return if (hasImage && hasInit) Check.Ok else Check.NotDfuPackage
    }

    /** Einträge eines ZIP-Streams lesen; null = kein gültiges ZIP. */
    fun listEntries(input: InputStream): List<String>? = try {
        ZipInputStream(input).use { z ->
            val out = mutableListOf<String>()
            while (true) { val e = z.nextEntry ?: break; if (!e.isDirectory) out += e.name; if (out.size > 64) break }
            out
        }
    } catch (_: Exception) { null }

    fun checkZip(input: InputStream): Check = listEntries(input)?.let { if (it.isEmpty()) Check.Corrupt else checkEntries(it) } ?: Check.Corrupt

    /** Größe lesbar, z. B. „412 kB“ / „1.2 MB“. */
    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${(bytes + 512) / 1024} kB"
        else -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

/** Erkennung von DFU-Geräten im BLE-Scan und Auswahl nach `start ota`. */
object DfuScanRules {
    const val SECURE_DFU_UUID16 = "fe59"
    const val LEGACY_DFU_SERVICE = "00001530-1212-efde-1523-785feabcd123"
    private val MAC = Regex("""(?i)\b([0-9a-f]{2}(?::[0-9a-f]{2}){5})\b""")
    private val NAME_IN_REPLY = Regex("""\(name:\s*([^)]+)\)""")

    /** Gehört der Scan-Treffer zu einem Gerät im DFU-/OTA-Modus? null = nein. */
    fun classify(name: String?, serviceUuids: Collection<String>): DfuKind? {
        val u = serviceUuids.map { it.lowercase(Locale.ROOT) }
        val n = name?.trim().orEmpty()
        return when {
            u.any { it == SECURE_DFU_UUID16 || it.startsWith("0000$SECURE_DFU_UUID16-") } || n.equals("DfuTarg", ignoreCase = true) -> DfuKind.SECURE
            u.any { it == LEGACY_DFU_SERVICE } && !n.endsWith("_OTA", ignoreCase = true) -> DfuKind.LEGACY_BOOTLOADER
            n.equals("AdaDFU", ignoreCase = true) -> DfuKind.LEGACY_BOOTLOADER
            n.endsWith("_OTA", ignoreCase = true) -> DfuKind.APP_BUTTONLESS
            else -> null
        }
    }

    /** Antwort auf `start ota` auswerten. */
    data class OtaReply(val ok: Boolean, val mac: String?, val name: String?, val unsupported: Boolean, val nonNordic: Boolean = false)

    fun parseOtaReply(reply: String?): OtaReply {
        val r = reply?.trim().orEmpty()
        val mac = MAC.find(r)?.groupValues?.get(1)?.uppercase(Locale.ROOT)
        val name = NAME_IN_REPLY.find(r)?.groupValues?.get(1)?.trim()
        val unsupported = r.contains("not supported", ignoreCase = true)
        // ESP32: WLAN-/HTTP-OTA ("Started: http://…/update", AP) bzw. MCUmgr-over-BLE ("BLE OTA open for … s") – kein Nordic DFU
        val nonNordic = r.contains("http", ignoreCase = true) || r.contains("BLE OTA open", ignoreCase = true) || r.startsWith("Started", ignoreCase = true)
        val ok = r.startsWith("OK", ignoreCase = true) || r.contains("OK -", ignoreCase = true)
        return OtaReply(ok = ok && !unsupported && !nonNordic, mac = mac, name = name, unsupported = unsupported, nonNordic = nonNordic)
    }

    /** Adresse +1 im letzten Byte (Nordic-Secure-Bootloader melden sich mit der nächsten Adresse). */
    fun nextAddress(mac: String): String? {
        val parts = mac.split(':'); if (parts.size != 6) return null
        val v = parts.joinToString("").toLongOrNull(16) ?: return null
        val n = (v + 1) and 0xFFFFFFFFFFFFL
        return (0 until 6).joinToString(":") { i -> "%02X".format(Locale.ROOT, (n shr (8 * (5 - i))) and 0xFF) }
    }

    /**
     * Automatische Zielwahl nach `start ota`: nur wenn eindeutig. Zuerst die gemeldete MAC (oder MAC+1), dann der
     * gemeldete Name; sonst genau ein neu aufgetauchtes DFU-Gerät. Mehrdeutig -> null (der Nutzer wählt selbst –
     * nie ein falsches Gerät flashen).
     */
    fun pickAfterStartOta(candidates: List<DfuTarget>, reply: OtaReply, knownBefore: Set<String> = emptySet()): DfuTarget? {
        reply.mac?.let { mac ->
            candidates.firstOrNull { it.address.equals(mac, true) }?.let { return it }
            nextAddress(mac)?.let { n -> candidates.firstOrNull { it.address.equals(n, true) }?.let { return it } }
        }
        reply.name?.let { nm -> candidates.filter { it.name.equals(nm, true) }.singleOrNull()?.let { return it } }
        val fresh = candidates.filter { it.address.uppercase(Locale.ROOT) !in knownBefore.map { k -> k.uppercase(Locale.ROOT) } }
        return fresh.singleOrNull()
    }

    /** Liste stärkstes Signal zuerst; dasselbe Gerät (Adresse) nur einmal, neuester Treffer gewinnt. */
    fun merge(list: List<DfuTarget>, hit: DfuTarget): List<DfuTarget> =
        (list.filterNot { it.address.equals(hit.address, true) } + hit).sortedByDescending { it.rssi }

    /** Signalbalken 0–4 aus RSSI. */
    fun signalBars(rssi: Int): Int = when { rssi >= -60 -> 4; rssi >= -70 -> 3; rssi >= -80 -> 2; rssi >= -90 -> 1; else -> 0 }
}

/** Option A: `start ota` senden und das anschließend erscheinende DFU-Gerät suchen. */
sealed class OtaBootloaderRequest {
    object Idle : OtaBootloaderRequest()
    object Sending : OtaBootloaderRequest()
    data class Searching(val mac: String?) : OtaBootloaderRequest()
    data class Found(val target: DfuTarget) : OtaBootloaderRequest()
    object NotFound : OtaBootloaderRequest()
    data class Failed(val reply: String) : OtaBootloaderRequest()
    val isBusy get() = this is Sending || this is Searching
}

/** Welche Boards Nordic BLE DFU können (nur nRF52); ESP32/RP2040/STM32 haben eigene OTA-Wege. */
object OtaTargetRules {
    /** true = nRF52, false = sicher kein Nordic-DFU-Board, null = unbekannt (erlaubt, mit Warnhinweis). */
    fun nordicCapable(mcu: String?): Boolean? {
        val m = mcu?.trim().orEmpty()
        if (m.isEmpty() || m == "—") return null
        if (m.contains("nrf52", ignoreCase = true)) return if (m.contains("/")) null else true   // "nRF52840 / ESP32-S3": Variante unklar
        return if (listOf("esp32", "rp2040", "stm32").any { m.contains(it, ignoreCase = true) }) false else null
    }
}

/** Verbindung, zu der die App nach einem erfolgreichen Update zurückkehrt (vor `start ota` bzw. dem Trennen gemerkt). */
data class OtaReconnectPlan(
    val type: ConnectionType,
    val bleAddress: String? = null,
    val usbDeviceId: Int? = null,
    val usbVendorId: Int? = null,
    val usbProductId: Int? = null,
    val usbBaud: Int = 115200,
    /** Vorher eingeloggter Remote-Repeater (über den Companion) – wird nach dem Neuverbinden wieder angemeldet. */
    val repeater: MeshContact? = null
)

/** Ablaufregeln rund um den OTA-Screen (v1.26, Live-Test): Menü, Navigation, Auto-Reconnect, Aufräumen. */
object OtaFlowRules {
    /** Pause nach „Fertig“, bis das Gerät mit der neuen Firmware hochgefahren ist. */
    const val RECONNECT_DELAY_MS = 2_500L

    /** Läuft ein OTA-Vorgang oder ist er noch nicht abgeschlossen (also nicht zurückgesetzt)? */
    fun isActiveOrFinished(phase: OtaPhase, request: OtaBootloaderRequest, sessionStarted: Boolean): Boolean =
        sessionStarted || phase != OtaPhase.IDLE || request !is OtaBootloaderRequest.Idle

    /**
     * Ziel-Repeater bereit: direkt per USB verbunden oder per Remote-Admin eingeloggt. Nur dann weiß die App, wohin
     * `start ota` geht – ein bloß verbundener Companion-Node reicht nicht.
     */
    fun isRepeaterReady(isConnected: Boolean, isDirectRepeater: Boolean, remoteLoggedIn: Boolean): Boolean =
        isConnected && (isDirectRepeater || remoteLoggedIn)

    /** Menüpunkt „Firmware / OTA“: `isRepeaterReady || otaActiveOrFinished` (Rückkehr während/nach einem Update). */
    fun menuEnabled(isRepeaterReady: Boolean, otaActiveOrFinished: Boolean): Boolean = isRepeaterReady || otaActiveOrFinished

    /**
     * Verbindungsabbruch: zurück zum Verbindungs-Screen – außer der Nutzer ist im OTA-Screen und ein OTA-Vorgang läuft
     * (der Abbruch durch `start ota` bzw. das Trennen vor dem DFU ist gewollt).
     */
    fun resetToConnectionOnDisconnect(onOtaScreen: Boolean, otaActiveOrFinished: Boolean): Boolean = !(onOtaScreen && otaActiveOrFinished)

    /** Beim Verlassen des OTA-Screens aufräumen? Nur nach Abschluss (Fertig/Abbruch/Fehler), nie mitten im Update. */
    fun clearOnLeave(phase: OtaPhase): Boolean = phase.isTerminal

    /** Automatisch neu verbinden? Nur nach erfolgreichem Update, mit gemerkter Verbindung, Schalter an, einmal. */
    fun shouldAutoReconnect(enabled: Boolean, phase: OtaPhase, plan: OtaReconnectPlan?, alreadyStarted: Boolean, isConnected: Boolean): Boolean =
        enabled && phase == OtaPhase.COMPLETED && plan != null && !alreadyStarted && !isConnected

    /**
     * USB-Gerät für das Neuverbinden: Nach dem Neustart vergibt Android oft eine neue Geräte-ID. Deshalb zuerst
     * dasselbe Gerät (Vendor/Product-ID), dann dieselbe ID, sonst das einzige vorhandene Gerät.
     */
    fun pickUsbDevice(devices: List<UsbSerialDeviceItem>, plan: OtaReconnectPlan): Int? {
        if (plan.usbVendorId != null && plan.usbProductId != null)
            devices.filter { it.vendorId == plan.usbVendorId && it.productId == plan.usbProductId }.let { m ->
                m.firstOrNull { it.deviceId == plan.usbDeviceId }?.let { return it.deviceId }
                m.firstOrNull()?.let { return it.deviceId }
            }
        devices.firstOrNull { it.deviceId == plan.usbDeviceId }?.let { return it.deviceId }
        return devices.singleOrNull()?.deviceId
    }
}
