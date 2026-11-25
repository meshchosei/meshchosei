package com.meshcore.repeater.viewmodel

import com.meshcore.repeater.i18n.tr
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meshcore.repeater.model.*
import com.meshcore.repeater.service.BleConnectionManager
import com.meshcore.repeater.service.ChannelHash
import com.meshcore.repeater.service.FilterListParser
import com.meshcore.repeater.service.InputValidator
import com.meshcore.repeater.service.MeshCoreBackup
import com.meshcore.repeater.service.CliReplyTag
import com.meshcore.repeater.service.QuerySpec
import com.meshcore.repeater.service.RepeaterProtocol
import com.meshcore.repeater.service.SerialConnectionManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.floor
import java.nio.charset.StandardCharsets

data class TerminalEntry(val text: String, val isOutgoing: Boolean)

data class BasicSetupState(
    val publicKeyHex: String = "",
    val antennaGainDbi: String = "3.0",
    val cableLossDb: String = "0.5",
    val freqMhz: String = "869.618",
    val bwKhz: String = "62.5",
    val sf: String = "8",
    val cr: String = "8",
    val txPowerDbm: String = "22",
    val ledsMode: String = "on",
    val nodeName: String = "",
    val ownerInfo: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val dutyCyclePercent: String = "10",
    val repeatEnabled: Boolean = true,
    val allowReadOnly: Boolean = true,
    val pathHashMode: Int = 1,
    val loopDetect: String = "minimal",
    val advertIntervalMin: String = "230",
    val floodAdvertIntervalHours: String = "50",
    val txDelaySec: String = "adaptive",
    val directTxDelaySec: String = "adaptive",
    val rxDelaySec: String = "0",
    val multiAcks: Int = 1,
    val isReading: Boolean = false
)

/**
 * Ein Set-Befehl plus die Übernahme in die "gespeichert"-Basis ([RepeaterViewModel.nodeBaseline]).
 * [commit] wird NUR angewendet, wenn der Repeater den Befehl ohne Fehler bestätigt hat.
 */
/** true, wenn der Funk-Entwurf (Funkparameter, TX, Duty-Cycle) vom bestätigten Stand abweicht. */
fun BasicSetupState.radioDiffersFrom(b: BasicSetupState): Boolean =
    freqMhz != b.freqMhz || bwKhz != b.bwKhz || sf != b.sf || cr != b.cr || txPowerDbm != b.txPowerDbm || dutyCyclePercent != b.dutyCyclePercent

class SetupChange(val command: String, val commit: (BasicSetupState) -> BasicSetupState = { it })

/**
 * Schlüssel für den Arbeitszustand der Speichern-/Senden-Buttons ([RepeaterViewModel.busyActions]).
 * Solange ein Schlüssel aktiv ist, zeigt der Button den Braille-Spinner und nimmt keine Klicks an.
 */
object BusyKey {
    const val RADIO = "radio"
    const val IDENTITY = "identity"
    const val ROUTING = "routing"
    const val ADVERT = "advert"
    const val GPS_SETLOC = "gps_setloc"
    const val PRV_KEY = "prv_key"
    const val REGION = "region"
    const val BACKUP_CREATE = "backup_create"
    const val BACKUP_INSTALL = "backup_install"
    const val WL_CHANNEL = "wl_channel"
    const val WL_PATH = "wl_path"
    const val BL_CHANNEL = "bl_channel"
    const val BL_PATH = "bl_path"
    const val RULE = "rule"
    const val WATCH = "watch"
    const val FILTER_LOAD = "filter_load"
    const val FILTER_REFRESH = "filter_refresh"
    const val POWER = "power"
    const val LEDS = "leds"
    const val FILTER_DRYRUN = "filter_dryrun"
    const val FILTER_HOPS = "filter_hops"
    const val FILTER_AGE = "filter_age"
    const val FILTER_RESET = "filter_reset"
}

class RepeaterViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("repeater_cfg_prefs", Context.MODE_PRIVATE)

    private val _selectedConnectionType = MutableStateFlow(ConnectionType.USB_SERIAL)
    val selectedConnectionType: StateFlow<ConnectionType> = _selectedConnectionType

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _usbDevices = MutableStateFlow<List<UsbSerialDeviceItem>>(emptyList())
    val usbDevices: StateFlow<List<UsbSerialDeviceItem>> = _usbDevices

    private val _usbDebugInfo = MutableStateFlow("")
    val usbDebugInfo: StateFlow<String> = _usbDebugInfo

    private val _meshContacts = MutableStateFlow<List<MeshContact>>(emptyList())
    val meshContacts: StateFlow<List<MeshContact>> = _meshContacts

    private val _isRefreshingContacts = MutableStateFlow(false)
    val isRefreshingContacts: StateFlow<Boolean> = _isRefreshingContacts

    private val _remoteAdminSession = MutableStateFlow(RemoteAdminSession())
    val remoteAdminSession: StateFlow<RemoteAdminSession> = _remoteAdminSession

    private val _terminalLogs = MutableStateFlow<List<TerminalEntry>>(emptyList())
    val terminalLogs: StateFlow<List<TerminalEntry>> = _terminalLogs
    private val _systemLogs = MutableStateFlow<List<TerminalEntry>>(emptyList())
    val systemLogs: StateFlow<List<TerminalEntry>> = _systemLogs
    private var lastCommandWasAuto: Boolean = true
    /** Antwortfenster für einen MANUELL im CLI-Eingabefeld abgeschickten Befehl (nur dann -> CLI-Tab). */
    @Volatile private var manualReplyUntilMs: Long = 0L
    @Volatile private var autoReplyUntilMs: Long = 0L
    private val autoCommandsInFlight = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private var activeAutoUntilMs: Long = 0L
    // === Streng sequentielles Request-Response-Protokoll ===
    // Ein einziger Mutex für ALLE CLI-Befehle (Abfragen, Set-Befehle, Konsole, Pings): es läuft immer
    // genau ein Befehl, und erst nach dessen (validierter) Antwort oder Timeout wird der nächste gesendet.
    private val sequentialQueryMutex = Mutex()
    /** Job, der GERADE unter dem Mutex läuft (nicht nur eingereiht) – Ziel von "Abbrechen" im Overlay. */
    @Volatile private var activeJob: Job? = null
    private var readJob: Job? = null                       // aktueller Auslese-Job (Basis-Setup, Capability, Filter, Telemetrie)
    private class QueryReply(val value: String?, val error: String? = null)   // value == null -> Fehler/"unknown"-Antwort (Text in error)
    @Volatile private var activeQueryCommand: String? = null
    /** LoRa-Remote: Tag ("XX|", [CliReplyTag]) des laufenden Befehls – nur Antworten mit genau diesem Tag zählen. */
    @Volatile private var activeQueryTag: String? = null
    private var cliTagSeq = 0
    private var activeQueryValidator: ((String) -> Boolean)? = null
    private var activeQueryWaiter: CompletableDeferred<QueryReply>? = null
    private val _activeReadStatusText = MutableStateFlow<String?>(null)
    /** Klartext der gerade laufenden Abfrage (null = keine Abfrage aktiv) – für das zentrale Status-Overlay. */
    val activeReadStatusText: StateFlow<String?> = _activeReadStatusText
    private val _readProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val readProgress: StateFlow<Pair<Int, Int>?> = _readProgress
    private val _hasCompletedInitialRead = MutableStateFlow(false)
    /** true, sobald der einmalige Start-Scan dieser Verbindung/Sitzung vollständig durchgelaufen ist. */
    val hasCompletedInitialRead: StateFlow<Boolean> = _hasCompletedInitialRead
    @Volatile private var initialScanRunning = false
    /** Sammelt während einer Abfrage alle Rohzeilen (für mehrzeilige Ausgaben wie den "region"-Baum). */
    private var activeCapture: MutableList<String>? = null

    // === Backup & Wiederherstellung ===
    private val _backupJson = MutableStateFlow<String?>(null)
    /** Fertig erzeugtes Backup-JSON, das der BackupScreen per Dateidialog speichert. */
    val backupJson: StateFlow<String?> = _backupJson
    fun clearBackupJson() { _backupJson.value = null }
    private val _basicSetup = MutableStateFlow(BasicSetupState())
    val basicSetup: StateFlow<BasicSetupState> = _basicSetup
    /** Zuletzt vom Node gelesener bzw. erfolgreich gesendeter Stand – Basis für "nur Änderungen senden". */
    private val _nodeBaseline = MutableStateFlow(BasicSetupState())
    val nodeBaseline: StateFlow<BasicSetupState> = _nodeBaseline

    // === Arbeitszustand der Speichern-/Senden-Buttons (Doppelklick-Sperre) ===
    private val _busyActions = MutableStateFlow<Set<String>>(emptySet())
    /** Aktive [BusyKey]s: Button zeigt Spinner + "Speichere…", bis der Repeater geantwortet hat (oder Timeout). */
    val busyActions: StateFlow<Set<String>> = _busyActions
    /** false, wenn [key] bereits arbeitet (zweiter Klick wird verworfen). */
    private fun tryMarkBusy(key: String): Boolean {
        if (key in _busyActions.value) return false
        _busyActions.value = _busyActions.value + key
        return true
    }
    private fun clearBusy(key: String) { _busyActions.update { it - key } }

    // === Regionen (region …) ===
    private val _regionState = MutableStateFlow(RegionState())
    val regionState: StateFlow<RegionState> = _regionState

    private val _uiEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val uiEvents: SharedFlow<String> = _uiEvents

    /** Feuert bei unerwartetem Verbindungsverlust (USB-Detach, BLE-Abriss, Companion stumm, Remote-Repeater stumm). */
    private val _linkLost = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val linkLost: SharedFlow<Unit> = _linkLost

    // === Verbindungsüberwachung ===
    private var linkWatchJob: Job? = null
    @Volatile private var lastCompanionRxMs = 0L
    @Volatile private var lastRemoteTxMs = 0L
    @Volatile private var lastRemoteRxMs = 0L
    private var remoteMisses = 0
    private var countedRemoteTxMs = 0L
    private var syncInFlightSinceMs = 0L
    @Volatile private var usbArrowRepliesSeen = false
    /** Jede gültige (validierte oder Fehler-)Antwort des Repeaters über USB = Lebenszeichen für den USB-Timeout. */
    @Volatile private var usbReplySeen = false
    private var connectProbeJob: Job? = null

    private val _disclaimerAccepted = MutableStateFlow(prefs.getBoolean("disclaimer_accepted", false))
    val disclaimerAccepted: StateFlow<Boolean> = _disclaimerAccepted

    private val _themeMode = MutableStateFlow(ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.DARK.name) ?: ThemeMode.DARK.name))
    val themeMode: StateFlow<ThemeMode> = _themeMode

    private val _appLanguage = MutableStateFlow(AppLanguage.valueOf(prefs.getString("app_lang", AppLanguage.SYSTEM.name) ?: AppLanguage.SYSTEM.name))
    val appLanguage: StateFlow<AppLanguage> = _appLanguage

    private val _deviceCapabilities = MutableStateFlow(DeviceCapabilities())
    val deviceCapabilities: StateFlow<DeviceCapabilities> = _deviceCapabilities

    private val _radioConfig = MutableStateFlow(RadioAntennaConfig())
    val radioConfig: StateFlow<RadioAntennaConfig> = _radioConfig

    private val _meshCareState = MutableStateFlow(MeshCareState())
    val meshCareState: StateFlow<MeshCareState> = _meshCareState

    private val _telemetry = MutableStateFlow(BatterySolarTelemetry())
    val telemetry: StateFlow<BatterySolarTelemetry> = _telemetry

    private val _hardwareInfo = MutableStateFlow(NodeHardwareInfo())
    val hardwareInfo: StateFlow<NodeHardwareInfo> = _hardwareInfo

    private val _filterStats = MutableStateFlow(FilterStats(filterEnabled = false))
    val filterStats: StateFlow<FilterStats> = _filterStats

    private val _whitelistChannels = MutableStateFlow<List<WhitelistChannel>>(emptyList())
    val whitelistChannels: StateFlow<List<WhitelistChannel>> = _whitelistChannels

    private val _whitelistPaths = MutableStateFlow<List<WhitelistPath>>(emptyList())
    val whitelistPaths: StateFlow<List<WhitelistPath>> = _whitelistPaths

    private val _blockedChannels = MutableStateFlow<List<BlockedChannel>>(emptyList())
    val blockedChannels: StateFlow<List<BlockedChannel>> = _blockedChannels

    private val _blockedPaths = MutableStateFlow<List<BlockedPath>>(emptyList())
    val blockedPaths: StateFlow<List<BlockedPath>> = _blockedPaths

    private val _senderRules = MutableStateFlow<List<FilterRule>>(emptyList())
    val senderRules: StateFlow<List<FilterRule>> = _senderRules

    private val _textRules = MutableStateFlow<List<FilterRule>>(emptyList())
    val textRules: StateFlow<List<FilterRule>> = _textRules

    private val _watchChannels = MutableStateFlow<List<WatchChannel>>(emptyList())
    val watchChannels: StateFlow<List<WatchChannel>> = _watchChannels

    private var meshCareJob: Job? = null
    private var telemetryJob: Job? = null
    private var lastSentCommand: String = ""
    private var pendingReplySignal: CompletableDeferred<Unit>? = null
    // Remote-Login
    private var loginTimeoutJob: Job? = null
    @Volatile private var loginAttemptPending = false
    private var isAutoCommand: Boolean = false

    private val serialManager: SerialConnectionManager = SerialConnectionManager(
        context = application,
        scope = viewModelScope,
        onDataReceived = { line -> if (line.startsWith("[USB-RX")) _usbDebugInfo.value = line; logTerminal(line, false); parseIncomingLine(line) },
        onFrameReceived = { data -> handleIncomingFrame(data) },
        onError = { err -> notifyUser(err) },
        onConnectionChanged = { connected, name ->
            if (connected) {
                _usbDebugInfo.value = ""   // Verbindung steht – kein "Warte auf USB-Daten…" mehr
                _connectionState.value = ConnectionState.Connected(name ?: "USB-Node", ConnectionType.USB_SERIAL, "115200 Baud")
                startLinkWatch()
                _deviceCapabilities.value = DeviceCapabilities(detectionComplete = false)
                _hasCompletedInitialRead.value = false
                _activeReadStatusText.value = "Prüfe Gerät am USB-Port (Repeater-CLI / Companion)…"
                connectProbeJob?.cancel()
                connectProbeJob = viewModelScope.launch {
                    // Zuerst ASCII-CLI ("ver"): hängt ein Repeater direkt am USB-Port, antwortet er sofort im Klartext.
                    // Ein Companion ignoriert Klartext (Frames beginnen mit '<'), deshalb danach das binäre APP_START.
                    // Mehrere Runden, weil manche ESP32-Boards beim Öffnen des Ports neu starten.
                    var directCli = false
                    for (waitMs in listOf(200L, 1200L, 1800L)) {
                        delay(waitMs)
                        if (!isLinkUp() || _deviceCapabilities.value.isCompanion) break
                        val reply = sequentialQueryMutex.withLock { runQuery("ver", timeoutMs = USB_PROBE_TIMEOUT_MS) }
                        if (reply != null || usbReplySeen) { directCli = true; break }   // jede Klartext-Antwort = direkte CLI
                        if (_deviceCapabilities.value.isCompanion) break
                        sendCompanionBytes(RepeaterProtocol.buildAppStartFrame())
                        delay(600L)
                        if (_deviceCapabilities.value.isCompanion) break
                    }
                    when {
                        !isLinkUp() -> Unit
                        _deviceCapabilities.value.isCompanion -> { if (readJob?.isActive != true) _activeReadStatusText.value = null } // Companion: erst Repeater wählen & einloggen
                        directCli -> {
                            // Direkter Repeater am USB: kein Login nötig, sofort vollständig einlesen (wie nach BT-Login)
                            _deviceCapabilities.value = _deviceCapabilities.value.copy(isCompanion = false, isDirectRepeater = true)
                            startInitialScan()
                        }
                        else -> {
                            disconnect()
                            notifyUser("USB-Timeout: Keine Antwort vom Gerät (evtl. nur Debug-USB oder kein Repeater).")
                        }
                    }
                }
            } else {
                connectProbeJob?.cancel()
                stopLinkWatch()
                resetInitialReadState()
                _connectionState.value = ConnectionState.Disconnected
                _usbDebugInfo.value = ""
                _deviceCapabilities.value = DeviceCapabilities()
                _remoteAdminSession.value = RemoteAdminSession()
                _meshContacts.value = emptyList()
                stopMeshCare(restoreTx = false)
            }
        },
        onDevicesChanged = { refreshUsbDevices() },
        onLinkLost = { reason -> handleLinkLost(reason) }
    )

    private lateinit var bleManager: BleConnectionManager
    init { bleManager = BleConnectionManager(
        context = application,
        scope = viewModelScope,
        onDataReceived = { line -> logTerminal(line, false); parseIncomingLine(line) },
        onFrameReceived = { data -> handleIncomingFrame(data) },
        onError = { err -> notifyUser(err) },
        onConnectionChanged = { connected, name ->
            if (connected) {
                _connectionState.value = ConnectionState.Connected(name ?: "BLE-Node", ConnectionType.BLE_COMPANION, "MeshCore NUS")
                startLinkWatch()
                viewModelScope.launch {
                    delay(250L); bleManager.sendBytes(RepeaterProtocol.buildAppStartFrame())
                    delay(450L); refreshMeshContacts()
                }
            } else {
                stopLinkWatch()
                resetInitialReadState()
                _connectionState.value = ConnectionState.Disconnected; _telemetry.value = BatterySolarTelemetry(); _remoteAdminSession.value = RemoteAdminSession(); _meshContacts.value = emptyList(); stopMeshCare(restoreTx = false)
            }
        },
        onLinkLost = { reason -> handleLinkLost(reason) }
    )

    refreshUsbDevices()
    }
    val bleDevices: StateFlow<List<BleDeviceItem>> get() = bleManager.scannedDevices
    val isScanningBle: StateFlow<Boolean> get() = bleManager.isScanning

    private fun logTerminal(text: String, isOutgoing: Boolean, systemOnly: Boolean = false) {
        // CLI-Tab: ausschließlich manuell eingegebene Befehle und deren Antworten; alles andere -> SYSTEM-Tab
        val toCli = !systemOnly && !lastCommandWasAuto && System.currentTimeMillis() < manualReplyUntilMs
        if (!toCli) _systemLogs.value = (_systemLogs.value + TerminalEntry(text, isOutgoing)).takeLast(400)
        else _terminalLogs.value = (_terminalLogs.value + TerminalEntry(text, isOutgoing)).takeLast(400)
    }
    fun clearTerminalLogs() { _terminalLogs.value = emptyList() }
    fun clearSystemLogs() { _systemLogs.value = emptyList() }
    private fun notifyUser(msg: String) { _uiEvents.tryEmit(tr(msg)) }  // statische Texte hier übersetzen
    fun acceptDisclaimer() { prefs.edit().putBoolean("disclaimer_accepted", true).apply(); _disclaimerAccepted.value = true }
    fun setThemeMode(mode: ThemeMode) { prefs.edit().putString("theme_mode", mode.name).apply(); _themeMode.value = mode }
    fun setAppLanguage(lang: AppLanguage) { prefs.edit().putString("app_lang", lang.name).apply(); _appLanguage.value = lang }
    fun selectConnectionType(type: ConnectionType, startScan: Boolean = true) { if (_selectedConnectionType.value == type) return; _selectedConnectionType.value = type; if (type == ConnectionType.USB_SERIAL) { stopBleScan(); refreshUsbDevices() } else if (startScan) { startBleScan() } }
    /** Hinweis aus der UI (deutscher Schlüssel, wird über tr() übersetzt). */
    fun showUserHint(msg: String) { notifyUser(msg) }
    fun notifyBlePermissionDenied() { notifyUser("Bluetooth-Berechtigung verweigert – BLE-Scan nicht möglich.") }
    fun refreshUsbDevices() {
        _usbDevices.value = serialManager.listDevices()
        if (_usbDevices.value.isEmpty() && _connectionState.value !is ConnectionState.Connected) { _usbDebugInfo.value = "" }
        if (_connectionState.value is ConnectionState.Connected && _selectedConnectionType.value == ConnectionType.USB_SERIAL) {
            viewModelScope.launch {
                sendCompanionBytes(RepeaterProtocol.buildAppStartFrame())
                delay(250L)
                refreshMeshContacts()
            }
        }
    }
    fun connectUsb(deviceId: Int, baudRate: Int = 115200) { usbArrowRepliesSeen = false; usbReplySeen = false; _connectionState.value = ConnectionState.Connecting("Verbinde USB..."); serialManager.connect(deviceId, baudRate) }
    fun startBleScan() { bleManager.startScan() }
    fun stopBleScan() { bleManager.stopScan() }
    fun connectBle(address: String) { stopBleScan(); _connectionState.value = ConnectionState.Connecting("Verbinde BLE..."); bleManager.connect(address) }
    fun disconnect() { connectProbeJob?.cancel(); stopLinkWatch(); readJob?.cancel(); loginTimeoutJob?.cancel(); loginAttemptPending = false; serialManager.disconnect(); bleManager.disconnect(); _usbDebugInfo.value = ""; _telemetry.value = BatterySolarTelemetry(); _connectionState.value = ConnectionState.Disconnected }

    // ---------------------------------------------------------------------------------------
    // Verbindungsüberwachung
    //  - USB: Detach / Lese- & Schreibfehler -> SerialConnectionManager.onLinkLost
    //  - BLE: GATT-Disconnect (Timeout 8 / Remote 19 ...) -> BleConnectionManager.onLinkLost
    //  - Companion (USB & BLE): Keep-Alive per CMD_DEVICE_QUERY, wenn länger nichts empfangen wurde
    //  - Remote-Admin (LoRa): unbeantwortete Befehle zählen; nach Leerlauf wird ein "ver"-Ping gesendet
    // ---------------------------------------------------------------------------------------
    private fun handleLinkLost(reason: String) {
        stopLinkWatch()
        readJob?.cancel()
        notifyUser(tr("⚠ Verbindung getrennt: {0}", tr(reason)))
        _linkLost.tryEmit(Unit)
    }

    private fun stopLinkWatch() { linkWatchJob?.cancel(); linkWatchJob = null; syncInFlightSinceMs = 0L }

    private fun startLinkWatch() {
        val now = System.currentTimeMillis()
        lastCompanionRxMs = now; lastRemoteTxMs = 0L; lastRemoteRxMs = now; remoteMisses = 0; countedRemoteTxMs = 0L
        linkWatchJob?.cancel()
        linkWatchJob = viewModelScope.launch {
            var companionPingAt = 0L
            while (isActive && _connectionState.value is ConnectionState.Connected) {
                delay(LINK_WATCH_TICK_MS)
                val t = System.currentTimeMillis()
                // 1) Companion-Keep-Alive (Gerät am USB/BLE selbst)
                if (_deviceCapabilities.value.isCompanion) {
                    val idle = t - lastCompanionRxMs
                    if (idle < COMPANION_IDLE_PING_MS) companionPingAt = 0L
                    else if (idle > COMPANION_DEAD_MS) {
                        handleLinkLost("Companion-Node antwortet nicht mehr (ausgeschaltet oder Verbindung gestört).")
                        disconnect()
                        break
                    } else if (companionPingAt == 0L || t - companionPingAt > COMPANION_PING_RETRY_MS) {
                        companionPingAt = t
                        sendCompanionBytes(RepeaterProtocol.buildDeviceQueryFrame())
                    }
                }
                // 2) Remote-Admin-Link (Repeater über LoRa)
                val session = _remoteAdminSession.value
                if (session.targetRepeater != null && session.isLoggedIn) {
                    val pending = lastRemoteTxMs > lastRemoteRxMs
                    if (pending && t - lastRemoteTxMs > REMOTE_REPLY_TIMEOUT_MS) {
                        if (countedRemoteTxMs != lastRemoteTxMs) { countedRemoteTxMs = lastRemoteTxMs; if (noteRemoteTimeout()) continue }
                        pingRemoteIfIdle()
                    } else if (!pending && t - lastRemoteRxMs > REMOTE_IDLE_PING_MS) {
                        pingRemoteIfIdle()
                    }
                }
            }
        }
    }

    /** Zählt eine unbeantwortete Remote-Anfrage. true = Link gilt als verloren. */
    private fun noteRemoteTimeout(): Boolean {
        remoteMisses++
        if (remoteMisses < REMOTE_MAX_MISSES) return false
        remoteMisses = 0
        val s = _remoteAdminSession.value
        val name = s.targetRepeater?.name ?: "Repeater"
        if (!s.isLoggedIn) return false
        readJob?.cancel()
        _remoteAdminSession.value = s.copy(isLoggedIn = false, isLoggingIn = false, sessionStatus = "Getrennt – keine Antwort")
        _hasCompletedInitialRead.value = false
        notifyUser(tr("⚠ \"{0}\" antwortet nicht mehr (ausgeschaltet oder außer Funkreichweite). Remote-Admin-Sitzung beendet.", name))
        _linkLost.tryEmit(Unit)
        return true
    }

    private fun onRemoteTrafficReceived() { lastRemoteRxMs = System.currentTimeMillis(); remoteMisses = 0 }

    /** Holt die nächste Nachricht aus der Offline-Queue des Companions (Remote-CLI-Antworten liegen dort!). */
    private fun requestNextMessage(fromPush: Boolean) {
        val t = System.currentTimeMillis()
        if (fromPush && syncInFlightSinceMs != 0L && t - syncInFlightSinceMs < 3000L) return
        syncInFlightSinceMs = t
        sendCompanionBytes(RepeaterProtocol.buildSyncNextMessageFrame())
    }

    private fun handleRemoteMessage(prefix: ByteArray, txt: String, meta: String) {
        val target = _remoteAdminSession.value.targetRepeater
        val fromTarget = target == null || target.pubKey.size < 6 || target.pubKey.copyOfRange(0, 6).contentEquals(prefix)
        val (tag, body) = CliReplyTag.split(txt)
        logTerminal("< [OTA ${RepeaterProtocol.bytesToHex(prefix, 4)}${tag?.let { " #$it" } ?: ""}$meta] $body", false, systemOnly = (lastCommandWasAuto || System.currentTimeMillis() < activeAutoUntilMs))
        if (fromTarget) { onRemoteTrafficReceived(); onRemoteReply(txt) }
    }
    private fun sendCompanionBytes(frame: ByteArray) {
        if (_selectedConnectionType.value == ConnectionType.USB_SERIAL) serialManager.sendBytes(frame)
        else bleManager.sendBytes(frame)
    }
    fun enableCompanionMode() {
        if (_deviceCapabilities.value.isDirectRepeater) return   // direkter USB-Repeater ist nie ein Companion
        _deviceCapabilities.value = _deviceCapabilities.value.copy(isCompanion = true, detectionComplete = true)
        viewModelScope.launch {
            sendCompanionBytes(RepeaterProtocol.buildAppStartFrame())
            delay(200L)
            refreshMeshContacts()
        }
    }
    fun refreshMeshContacts() { _isRefreshingContacts.value = true; sendCompanionBytes(RepeaterProtocol.buildGetContactsFrame()) }
    fun selectTargetRepeater(contact: MeshContact?) {
        if (contact?.pubKeyHex != _remoteAdminSession.value.targetRepeater?.pubKeyHex) { readJob?.cancel(); _hasCompletedInitialRead.value = false; loginTimeoutJob?.cancel(); loginAttemptPending = false }
        if (contact == null) { _remoteAdminSession.value = _remoteAdminSession.value.copy(targetRepeater = null, isLoggedIn = false, isLoggingIn = false); return }
        _remoteAdminSession.value = RemoteAdminSession(targetRepeater = contact, savedPassword = prefs.getString("pwd_${contact.pubKeyHex}", "") ?: ""); _basicSetup.value = _basicSetup.value.copy(publicKeyHex = contact.pubKeyHex) }
    fun setRemoteAdminPassword(password: String) { val t = _remoteAdminSession.value.targetRepeater ?: return; prefs.edit().putString("pwd_${t.pubKeyHex}", password).apply(); _remoteAdminSession.value = _remoteAdminSession.value.copy(savedPassword = password) }
    /**
     * Remote-Login: der Companion bestätigt mit RESP_CODE_SENT (0x06) nur das VERSENDEN (inkl. est_timeout).
     * Eingeloggt ist die Sitzung erst mit PUSH_LOGIN_SUCCESS (0x85) – auch wenn diese bei Multi-Hop-LoRa
     * erst nach dem Timeout eintrifft (loginAttemptPending bleibt bis Erfolg/Fehlschlag/Zielwechsel gesetzt).
     */
    fun startRemoteLogin() {
        val s = _remoteAdminSession.value; val t = s.targetRepeater ?: return
        lastRemoteRxMs = System.currentTimeMillis(); lastRemoteTxMs = 0L; countedRemoteTxMs = 0L; remoteMisses = 0
        loginAttemptPending = true
        _remoteAdminSession.value = s.copy(isLoggedIn = false, isLoggingIn = true, sessionStatus = "Login gesendet...")
        sendCompanionBytes(RepeaterProtocol.buildLoginFrame(t.pubKey, s.savedPassword))
        armLoginTimeout(LOGIN_DEFAULT_TIMEOUT_MS) // bis RESP_CODE_SENT den geschätzten Timeout liefert
    }

    private fun armLoginTimeout(ms: Long) {
        loginTimeoutJob?.cancel()
        loginTimeoutJob = viewModelScope.launch {
            delay(ms)
            if (_remoteAdminSession.value.isLoggingIn) {
                // loginAttemptPending bleibt gesetzt: ein verspätetes PUSH_LOGIN_SUCCESS wird trotzdem angenommen
                _remoteAdminSession.value = _remoteAdminSession.value.copy(isLoggingIn = false, sessionStatus = "Keine Antwort (Timeout)")
            }
        }
    }

    /** Stammt ein Login-Push (0x85/0x86: [code][perm][pubkey_prefix(6)]…) vom aktuellen Ziel-Repeater? */
    private fun isLoginPushForTarget(data: ByteArray): Boolean {
        val t = _remoteAdminSession.value.targetRepeater ?: return false
        if (data.size < 8 || t.pubKey.size < 6) return true // Altformat ohne Präfix: akzeptieren
        return data.copyOfRange(2, 8).contentEquals(t.pubKey.copyOfRange(0, 6))
    }

    private fun onLoginSuccess() {
        loginTimeoutJob?.cancel()
        loginAttemptPending = false
        onRemoteTrafficReceived()
        _remoteAdminSession.value = _remoteAdminSession.value.copy(isLoggedIn = true, isLoggingIn = false, sessionStatus = "Eingeloggt")
        logTerminal(tr("< [Mesh] Admin-Login bestätigt!"), false, systemOnly = true)
        startInitialScan()
    }

    private fun onLoginFailed() {
        loginTimeoutJob?.cancel()
        loginAttemptPending = false
        readJob?.cancel()
        _hasCompletedInitialRead.value = false
        _remoteAdminSession.value = _remoteAdminSession.value.copy(isLoggedIn = false, isLoggingIn = false, sessionStatus = "Login abgelehnt (Passwort?)")
        logTerminal(tr("< [Mesh] Admin-Login abgelehnt!"), false, systemOnly = true)
        notifyUser("Login vom Repeater abgelehnt – Passwort prüfen.")
    }
    /** Konsole: Befehl hinten in die sequentielle Warteschlange stellen (wartet auf laufende Abfragen). */
    fun sendManualCommand(cmd: String) { enqueueCommands(listOf(cmd), isAuto = false) }
    /** Einzelner Set-/Aktionsbefehl aus der UI – ebenfalls streng sequentiell. */
    /** Von UI-Buttons ausgelöste Befehle gelten als System-Befehle (SYSTEM-Tab); nur manualCli = true landet im CLI-Tab. */
    fun sendCommand(cmd: String, manualCli: Boolean = false, busyKey: String? = null) {
        if (busyKey == null) { enqueueCommands(listOf(cmd), isAuto = !manualCli); return }
        sendBusy(busyKey, cmd) { ok -> if (!ok) notifyUser(tr("\"{0}\" vom Repeater nicht bestätigt.", cmd.substringBefore(' ', cmd))) }
    }

    private fun isLinkUp() = _connectionState.value is ConnectionState.Connected

    private fun queryTimeoutMs(): Long = when {
        _remoteAdminSession.value.targetRepeater != null -> 4500L          // LoRa-Roundtrip
        _deviceCapabilities.value.isCompanion -> 1500L                     // lokale Companion-CLI
        _selectedConnectionType.value == ConnectionType.BLE_COMPANION -> 1500L
        else -> 900L                                                       // USB-Direkt
    }

    /**
     * Sendet GENAU EINEN Befehl und wartet exklusiv auf dessen Antwort (Format-Validator) oder Timeout.
     * Muss unter [sequentialQueryMutex] aufgerufen werden. Rückgabe: null = Timeout, sonst QueryReply.
     */
    private suspend fun runQuery(cmd: String, isAuto: Boolean = true, timeoutMs: Long = queryTimeoutMs(), capture: MutableList<String>? = null, trailingMs: Long? = null): QueryReply? {
        if (cmd.isBlank() || !isLinkUp()) return null
        val c = cmd.trim()
        val key = c.lowercase()
        // Sauberer Start: kein Rest eines früheren Befehls im Sammelpuffer
        capture?.clear()
        // LoRa-Remote: eindeutiges Tag, das die Firmware an den Antwortanfang spiegelt (verspätete Antworten erkennbar)
        val tag = if (_remoteAdminSession.value.targetRepeater != null) CliReplyTag.tagFor(cliTagSeq++).takeIf { CliReplyTag.wrap(it, c) != null } else null
        val waiter = CompletableDeferred<QueryReply>()
        activeQueryTag = tag
        activeQueryValidator = QuerySpec.validatorFor(key)
        activeQueryWaiter = waiter
        activeQueryCommand = key
        // steuert die Zuordnung mehrzeiliger Filterlisten – bei jedem anderen Befehl sofort zurücksetzen
        lastFilterQuery = if (key.startsWith("filter")) key else ""
        activeCapture = capture
        try {
            sendCommandInternal(c, isAuto, tag)
            val reply = withTimeoutOrNull(timeoutMs) { waiter.await() }
            if (reply == null) {
                // Timeout: Zuordnung SOFORT aufheben – eine später eintreffende Antwort gehört zu keinem Befehl mehr
                activeQueryCommand = null; activeQueryValidator = null; activeQueryWaiter = null; activeCapture = null; activeQueryTag = null
                return null
            }
            if (QuerySpec.isMultiLine(key)) delay(trailingMs ?: if (_remoteAdminSession.value.targetRepeater != null) 1200L else 350L) // Folgezeilen einsammeln
            reply.value?.let { applyQueryResult(key, it) }
            return reply
        } finally {
            activeQueryCommand = null; activeQueryValidator = null; activeQueryWaiter = null; activeCapture = null; activeQueryTag = null
        }
    }

    /**
     * Mehrere Befehle als einen zusammenhängenden Block streng nacheinander senden.
     * [onDone] erhält die Anzahl BESTÄTIGTER Befehle (Antwort erhalten und keine Fehlerantwort),
     * [onResults] je Befehl true/false in der Reihenfolge von [cmds] (nicht gesendete = false).
     */
    private fun enqueueCommands(cmds: List<String>, isAuto: Boolean = true, busyKey: String? = null, onResults: ((List<Boolean>) -> Unit)? = null, onDone: ((Int) -> Unit)? = null): Boolean {
        val list = cmds.map { it.trim() }
        if (list.none { it.isNotEmpty() }) return false
        // Doppelklick-Sperre: läuft dieselbe Aktion noch, wird der zweite Klick verworfen
        if (busyKey != null && !tryMarkBusy(busyKey)) return false
        val job = viewModelScope.launch {
            val results = BooleanArray(list.size)
            sequentialQueryMutex.withLock {
                for ((i, c) in list.withIndex()) {
                    if (c.isEmpty()) continue
                    if (!isLinkUp()) break
                    results[i] = runQuery(c, isAuto)?.value != null   // null = Timeout, value null = Fehlerantwort
                }
            }
            onResults?.invoke(results.toList())
            onDone?.invoke(results.count { it })
        }
        // auch bei Abbruch (Trennung) zuverlässig freigeben
        if (busyKey != null) job.invokeOnCompletion { clearBusy(busyKey) }
        return true
    }

    /** Einzelnen Befehl mit Arbeitszustand [key] senden; onDone(true) = vom Repeater bestätigt. false = nicht gesendet (busy/getrennt). */
    private fun sendBusy(key: String, cmd: String, onDone: ((Boolean) -> Unit)? = null): Boolean {
        if (!isLinkUp()) { notifyUser("Nicht verbunden."); return false }
        return enqueueCommands(listOf(cmd), busyKey = key) { n -> onDone?.invoke(n > 0) }
    }

    /**
     * Startet einen Auslese-Vorgang exklusiv: ein evtl. laufender alter Auslese-Job wird per cancelAndJoin()
     * beendet, danach läuft [block] unter dem zentralen Mutex. Das Status-Overlay wird automatisch gepflegt.
     */
    private fun startRead(cancelPrevious: Boolean = true, block: suspend () -> Unit): Job {
        val previous = readJob
        val job = viewModelScope.launch {
            if (cancelPrevious) previous?.cancelAndJoin() else previous?.join()
            sequentialQueryMutex.withLock {
                val self = coroutineContext[Job]
                activeJob = self
                try { block() } finally {
                    if (activeJob === self) activeJob = null
                    _activeReadStatusText.value = null; _readProgress.value = null
                }
            }
        }
        readJob = job
        return job
    }

    /** Abfrageliste nacheinander abarbeiten, mit Overlay-Text & Fortschritt. false = Remote-Link verloren / getrennt. */
    /**
     * [phaseText] != null: fester Text für die ganze Phase (statt Text je Befehl);
     * [progressOffset]/[progressTotal]: durchgehender Fortschritt über mehrere Phasen.
     */
    private suspend fun runReadSequence(
        cmds: List<String>, retriesWireless: Int = 2, phaseText: String? = null,
        progressOffset: Int = 0, progressTotal: Int = cmds.size, onStepDone: ((Int) -> Unit)? = null
    ): Boolean {
        val wireless = _remoteAdminSession.value.targetRepeater != null
        cmds.forEachIndexed { i, cmd ->
            if (!isLinkUp()) return false
            _activeReadStatusText.value = phaseText ?: QuerySpec.statusTextFor(cmd)
            _readProgress.value = (progressOffset + i + 1) to progressTotal
            var reply: QueryReply? = null
            for (attempt in 1..(if (wireless) retriesWireless else 1)) {
                reply = runQuery(cmd)
                if (reply != null) break
            }
            if (wireless && reply == null && noteRemoteTimeout()) return false
            onStepDone?.invoke(i)
        }
        return true
    }

    /**
     * Backup erstellen: liest alle MeshCore-Variablen (+ optional prv.key) und den Regions-Baum streng
     * sequentiell aus und legt das JSON in [backupJson] ab.
     */
    /**
     * Backup erstellen. Ablauf streng sequentiell unter dem zentralen Mutex:
     *  1) Regionen ("region", "region home", "region default") -> [regionState]
     *  2) Paketfilter (Status, dry-run, Modus, alle Blacklist-/Whitelist-Listen) -> Filter-State-Flows
     *  3) alle MeshCore-Variablen
     * Erst danach wird das JSON aus genau diesem frisch gelesenen Stand gebaut. Bleibt eine Region- oder
     * Filterabfrage trotz Wiederholung unbeantwortet, wird abgebrochen statt ein unvollständiges Backup zu speichern.
     */
    fun createBackup(includePrivateKey: Boolean) {
        if (!isLinkUp()) { notifyUser("Nicht verbunden."); return }
        if (!tryMarkBusy(BusyKey.BACKUP_CREATE)) return
        startRead(cancelPrevious = false) {
            val defs = MeshCoreBackup.VARS.filter { includePrivateKey || it.sensitivity != MeshCoreBackup.Sensitivity.PRIVATE_KEY }
            val wireless = _remoteAdminSession.value.targetRepeater != null

            // 1) Regionen
            _activeReadStatusText.value = "Backup: Regionen werden gelesen…"
            _readProgress.value = null
            // Anzeige NICHT vorab leeren: erst bei erfolgreichem Auslesen ersetzen, bei Fehler bleibt der alte Stand
            val (linkOk, fresh) = fetchRegions()
            if (!linkOk) { notifyUser("Backup abgebrochen – Repeater antwortet nicht."); return@startRead }
            if (fresh == null) { notifyUser("Backup abgebrochen – Regionen konnten nicht gelesen werden."); return@startRead }
            _regionState.value = fresh
            val rs = fresh

            // 2) Paketfilter (nur wenn die Firmware Filter kann)
            var filter: MeshCoreBackup.FilterBackup? = null
            if (_deviceCapabilities.value.supportsBlacklist) {
                filter = readFilterSnapshot(tr("Backup"))
                if (filter == null) { if (isLinkUp()) notifyUser("Backup abgebrochen – Paketfilter konnte nicht vollständig gelesen werden."); return@startRead }
            }

            // 3) Variablen
            val vars = LinkedHashMap<String, Any>()
            for ((i, def) in defs.withIndex()) {
                if (!isLinkUp()) return@startRead
                _activeReadStatusText.value = tr("Backup: \"{0}\" wird gelesen…", def.key)
                _readProgress.value = (i + 1) to defs.size
                var reply: QueryReply? = null
                for (attempt in 1..(if (wireless) 2 else 1)) { reply = runQuery("get ${def.key}"); if (reply != null) break }
                if (wireless && reply == null && noteRemoteTimeout()) { notifyUser("Backup abgebrochen – Repeater antwortet nicht."); return@startRead }
                reply?.value?.let { MeshCoreBackup.replyToJson(def, it) }?.let { vars[def.key] = it }
            }
            if (vars.isEmpty()) { notifyUser("Backup fehlgeschlagen – keine Werte gelesen."); return@startRead }
            if (includePrivateKey && "prv.key" !in vars) notifyUser("Hinweis: Private Key ist nur über USB-Direktverbindung auslesbar – nicht im Backup enthalten.")

            // JSON erst jetzt – ausschließlich aus dem frisch gelesenen State
            val regions = if (rs.supported == true) MeshCoreBackup.regionEntries(rs) else null
            _backupJson.value = MeshCoreBackup.buildJson(vars, regions, rs.home, rs.default, filter)
        }.invokeOnCompletion { clearBusy(BusyKey.BACKUP_CREATE) }
    }

    /** Backup installieren: Befehle streng sequentiell senden, abgelehnte/unbeantwortete Befehle melden. */
    fun installBackup(planCmds: List<String>, filter: MeshCoreBackup.FilterBackup? = null) {
        if (!isLinkUp()) { notifyUser("Nicht verbunden."); return }
        if (planCmds.isEmpty() && filter == null) { notifyUser("Keine übertragbaren Einstellungen im Backup."); return }
        if (!tryMarkBusy(BusyKey.BACKUP_INSTALL)) return
        startRead(cancelPrevious = false) {
            // Funk/TX/Key bleiben am Ende (können die Verbindung beenden) – der Paketfilter kommt davor
            val isTail: (String) -> Boolean = { it.startsWith("set radio ") || it.startsWith("set tx ") || it.startsWith("set prv.key ") }
            var cmds = planCmds.filterNot(isTail)
            if (filter != null) {
                val caps = _deviceCapabilities.value
                if (!caps.supportsBlacklist) notifyUser("Paketfilter übersprungen – die Firmware dieses Repeaters hat keinen Filter.")
                else {
                    val current = readFilterSnapshot(tr("Backup"))
                    if (current == null) { if (isLinkUp()) notifyUser("Paketfilter übersprungen – aktueller Filter-Stand nicht vollständig lesbar.") }
                    else {
                        cmds = cmds + MeshCoreBackup.filterCommands(filter, current, caps.supportsWhitelist)
                        if (filter.hasWhitelist && filter.whitelistCount > 0 && !caps.supportsWhitelist) notifyUser("Hinweis: Whitelist-Regeln übersprungen – diese Firmware kennt nur die Blacklist.")
                    }
                }
            }
            cmds = cmds + planCmds.filter(isTail)
            val failed = mutableListOf<String>()
            for ((i, c) in cmds.withIndex()) {
                if (!isLinkUp()) { failed += cmds.drop(i).map { it.substringAfter(' ').substringBefore(' ') }; break }
                val label = c.substringBefore(' ', c) + " " + c.substringAfter(' ').substringBefore(' ')
                _activeReadStatusText.value = tr("Backup wird installiert: {0}…", label)
                _readProgress.value = (i + 1) to cmds.size
                val r = runQuery(c, isAuto = true, timeoutMs = queryTimeoutMs() + 600L)
                if (r?.value == null) failed += label.trim()
            }
            _hasCompletedInitialRead.value = false
            notifyUser(if (failed.isEmpty()) tr("Backup installiert ({0} Befehle). Funk-/Key-Änderungen ggf. erst nach Neustart aktiv – danach \"Neu einlesen\".", cmds.size)
                       else tr("Backup installiert, {0} von {1} abgelehnt/ohne Antwort: {2}", failed.size, cmds.size, failed.take(6).joinToString(", ") + if (failed.size > 6) " …" else ""))
        }.invokeOnCompletion { clearBusy(BusyKey.BACKUP_INSTALL) }
    }

    /** Bricht den tatsächlich laufenden Vorgang ab; eingereihte Vorgänge starten danach regulär. */
    fun cancelActiveRead() { (activeJob ?: readJob)?.cancel(); notifyUser("Auslesen abgebrochen.") }

    private suspend fun pingRemoteIfIdle() {
        if (!sequentialQueryMutex.tryLock()) return
        try { runQuery(RepeaterProtocol.CMD_VER) } finally { sequentialQueryMutex.unlock() }
    }

    /** Validierte Antwort in den passenden Zustand schreiben – ausschließlich für den Befehl, der sie angefordert hat. */
    private fun applyQueryResult(cmd: String, v: String) {
        val s = _basicSetup.value
        fun adaptiveOr(x: String) = if (x.startsWith("adaptive", true)) "adaptive" else x
        when (cmd) {
            "board" -> { _hardwareInfo.value = _hardwareInfo.value.copy(boardModel = v); applyDetectedBoard(v) }
            "ver" -> { _hardwareInfo.value = _hardwareInfo.value.copy(firmwareVersion = v); _deviceCapabilities.value = _deviceCapabilities.value.copy(firmwareVersion = v) }
            "get public.key" -> _basicSetup.value = s.copy(publicKeyHex = v.uppercase(java.util.Locale.ROOT))
            "power" -> BatterySolarTelemetry.parsePowerReply(v)?.let { _telemetry.value = it }
            "get radio" -> {
                val p = v.split(",").map { it.trim() }
                val freq = p[0].toDoubleOrNull(); val bw = p[1].toDoubleOrNull(); val sf = p[2].toIntOrNull(); val cr = p[3].toIntOrNull()
                if (freq != null && bw != null && sf != null && cr != null) {
                    _radioConfig.value = _radioConfig.value.copy(frequencyMhz = freq, bandwidthKhz = bw, spreadingFactor = sf.coerceIn(5, 12), codingRate = "4/$cr")
                    _basicSetup.value = s.copy(freqMhz = p[0], bwKhz = p[1], sf = p[2], cr = p[3])
                }
            }
            "get tx" -> { _radioConfig.value = _radioConfig.value.copy(txPowerDbm = v.toDouble()); _basicSetup.value = s.copy(txPowerDbm = v) }
            "get dutycycle" -> QuerySpec.parseDutyCyclePercent(v)?.let { p -> _basicSetup.value = s.copy(dutyCyclePercent = if (p.endsWith(".0")) p.dropLast(2) else p) }
            "get name" -> _basicSetup.value = s.copy(nodeName = v)
            "get owner.info" -> _basicSetup.value = s.copy(ownerInfo = v)
            "get lat" -> _basicSetup.value = s.copy(latitude = v)
            "get lon" -> _basicSetup.value = s.copy(longitude = v)
            "get repeat" -> _basicSetup.value = s.copy(repeatEnabled = v.equals("on", true))
            "get allow.read.only" -> _basicSetup.value = s.copy(allowReadOnly = v.equals("on", true))
            "get path.hash.mode" -> _basicSetup.value = s.copy(pathHashMode = v.toInt())
            "get loop.detect" -> _basicSetup.value = s.copy(loopDetect = v.lowercase())
            "get advert.interval" -> _basicSetup.value = s.copy(advertIntervalMin = v)
            "get flood.advert.interval" -> _basicSetup.value = s.copy(floodAdvertIntervalHours = v)
            "get txdelay" -> _basicSetup.value = s.copy(txDelaySec = adaptiveOr(v))
            "get direct.txdelay" -> _basicSetup.value = s.copy(directTxDelaySec = adaptiveOr(v))
            "get rxdelay" -> _basicSetup.value = s.copy(rxDelaySec = adaptiveOr(v))
            "get multi.acks" -> _basicSetup.value = s.copy(multiAcks = v.toInt())
            "get leds" -> {
                val mode = v.lowercase().substringBefore(' ')
                _basicSetup.value = s.copy(ledsMode = mode)
            }
            "get gps" -> {
                Regex("lat=([-0-9.]+)").find(v)?.groupValues?.getOrNull(1)?.let { _basicSetup.value = _basicSetup.value.copy(latitude = it) }
                Regex("lon=([-0-9.]+)").find(v)?.groupValues?.getOrNull(1)?.let { _basicSetup.value = _basicSetup.value.copy(longitude = it) }
            }
        }
    }
    private fun sendCommandInternal(cmd: String, isAuto: Boolean, tag: String? = null) {
        if (cmd.isBlank()) return
        lastCommandWasAuto = isAuto
        if (isAuto) autoReplyUntilMs = System.currentTimeMillis() + 6500L else autoReplyUntilMs = 0L
        if (isAuto) { activeAutoUntilMs = System.currentTimeMillis() + 6000L; manualReplyUntilMs = 0L }
        else { activeAutoUntilMs = 0L; manualReplyUntilMs = System.currentTimeMillis() + 15_000L }
        lastSentCommand = cmd.trim().lowercase()
        if (_selectedConnectionType.value == ConnectionType.USB_SERIAL && (_deviceCapabilities.value.isCompanion != true)) { logTerminal("> $cmd", true); serialManager.send(cmd) }
        else {
            val t = _remoteAdminSession.value.targetRepeater
            if (t != null) {
                lastRemoteTxMs = System.currentTimeMillis()
                val wire = tag?.let { CliReplyTag.wrap(it, cmd) } ?: cmd
                logTerminal("> [OTA -> ${t.name}${tag?.let { " #$it" } ?: ""}] $cmd", true)
                sendCompanionBytes(RepeaterProtocol.buildRemoteCliFrame(t.pubKey.copyOfRange(0, minOf(6, t.pubKey.size)), wire))
            }
            else { logTerminal("> [Companion] $cmd", true); sendCompanionBytes(RepeaterProtocol.buildLocalCliFrame(cmd)) }
        }
    }
    private fun resetInitialReadState() {
        readJob?.cancel()
        initialScanRunning = false
        filterAutoLoadDone.clear()
        _hasCompletedInitialRead.value = false
        _activeReadStatusText.value = null
        _readProgress.value = null
    }

    /**
     * EIN vollständiger, streng sequentieller Start-Durchlauf: Geräte-/Rollenerkennung + alle Parameter
     * (+ Filterlisten, falls die Firmware Filter kann). Danach hasCompletedInitialRead = true; ein erneuter
     * Durchlauf erfolgt nur über "Neu einlesen" (readBasicSetupFromNode) oder nach einem Verbindungsabbruch.
     */
    private fun startInitialScan() {
        if (initialScanRunning && readJob?.isActive == true) return // z. B. doppelte Login-Bestätigung
        initialScanRunning = true
        _hasCompletedInitialRead.value = false
        _deviceCapabilities.value = _deviceCapabilities.value.copy(detectionComplete = false)
        startRead {
            // Remote: Key des Ziel-Repeaters; USB-direkt: leeren und per "get public.key" neu einlesen (kein Key vom vorigen Gerät)
            val targetKey = _remoteAdminSession.value.targetRepeater?.pubKeyHex ?: ""
            _basicSetup.value = _basicSetup.value.copy(isReading = true, publicKeyHex = targetKey)
            _regionState.value = RegionState()
            filterAutoLoadDone.clear()
            try {
                _activeReadStatusText.value = "Verbindung zum Repeater wird vorbereitet…"
                _readProgress.value = null
                val isWireless = _remoteAdminSession.value.targetRepeater != null
                if (_deviceCapabilities.value.isCompanion) {
                    // Alte, noch in der Companion-Queue liegende Antworten abholen, bevor die Abfrage startet
                    requestNextMessage(fromPush = false)
                    delay(400L)
                }
                if (isWireless) delay(900L)
                // Ein durchgehender Ablauf in klaren Phasen; Menü/Setup werden erst nach dem LETZTEN Schritt freigegeben
                // 1) Hardware & Firmware ZUERST (board/ver = schnelles Lebenszeichen, u. a. für den USB-Timeout)
                val detectSteps = 2 // "filter help" + "filter wl help"
                val maxScanSteps = HARDWARE_QUERIES.size + detectSteps + BL_STATUS_QUERIES.size + BL_LIST_QUERIES.size + WL_STATUS_QUERIES.size + WL_LIST_QUERIES.size + BASIC_SETUP_QUERIES.size + REGION_STEPS
                var ok = runReadSequence(HARDWARE_QUERIES, phaseText = "Lese Hardware & Firmware…", progressTotal = maxScanSteps)
                var done = HARDWARE_QUERIES.size
                // 2) Blacklist / Whitelist getrennt und strikt anhand der Firmware-Antworten erkennen
                var supportsBl = false; var supportsWl = false
                if (ok) {
                    _activeReadStatusText.value = "Prüfe Geräte- & Filter-Funktionen…"
                    _readProgress.value = ++done to maxScanSteps
                    val bl = queryWithRetries("filter help")
                    ok = bl.first
                    supportsBl = QuerySpec.isBlacklistHelp(bl.second?.value)
                }
                if (ok && supportsBl) {
                    _readProgress.value = ++done to maxScanSteps
                    val wl = queryWithRetries("filter wl help")
                    ok = wl.first
                    supportsWl = QuerySpec.isWhitelistHelp(wl.second?.value)
                } else done++
                _deviceCapabilities.value = _deviceCapabilities.value.copy(supportsBlacklist = supportsBl, supportsWhitelist = supportsWl, detectionComplete = true)
                val filterQueries = filterQueriesFor(supportsBl, supportsWl)
                val total = HARDWARE_QUERIES.size + detectSteps + filterQueries.size + BASIC_SETUP_QUERIES.size + REGION_STEPS
                // 3) Paket-Filter: Listen NUR für die jeweils unterstützte Funktion abfragen
                if (ok && filterQueries.isNotEmpty()) {
                    ok = runFilterListQueries(filterQueries, progressOffset = done, progressTotal = total)
                    done += filterQueries.size
                }
                // 4) Funk- & Routing-Parameter
                if (ok) ok = runReadSequence(BASIC_SETUP_QUERIES, phaseText = "Lese Funk- & Routing-Parameter…", progressOffset = done, progressTotal = total)
                done += BASIC_SETUP_QUERIES.size
                // 5) Regionen & Scope-Routing (region / region home / region default)
                if (ok) ok = readRegions(progressOffset = done, progressTotal = total)
                if (ok) {
                    _nodeBaseline.value = _basicSetup.value.copy(isReading = false)
                    _activeReadStatusText.value = "✓ Alle Start-Abfragen abgeschlossen"
                    _readProgress.value = total to total
                    delay(700L)
                    _hasCompletedInitialRead.value = true
                    restorePendingMeshCareTx()
                }
            } finally {
                initialScanRunning = false
                _deviceCapabilities.value = _deviceCapabilities.value.copy(detectionComplete = true)
                _basicSetup.value = _basicSetup.value.copy(isReading = false)
            }
        }
    }

    /** Nur starten, wenn der Start-Scan noch fehlt und gerade keiner läuft (z. B. "Geführtes Setup" nach abgebrochenem Scan). */
    fun ensureInitialRead() {
        if (_hasCompletedInitialRead.value || readJob?.isActive == true || !isLinkUp()) return
        val caps = _deviceCapabilities.value
        if (caps.isCompanion && !_remoteAdminSession.value.isLoggedIn) return
        startInitialScan()
    }

    /** Status- und Listenabfragen passend zu den erkannten Filter-Funktionen. */
    private fun filterQueriesFor(bl: Boolean, wl: Boolean): List<String> =
        (if (bl) BL_STATUS_QUERIES + BL_LIST_QUERIES else emptyList()) + (if (wl) WL_STATUS_QUERIES + WL_LIST_QUERIES else emptyList())

    /** Ein Befehl mit Wiederholung über LoRa. first = false -> Remote-Link verloren. */
    private suspend fun queryWithRetries(cmd: String): Pair<Boolean, QueryReply?> {
        val wireless = _remoteAdminSession.value.targetRepeater != null
        var reply: QueryReply? = null
        for (attempt in 1..(if (wireless) 2 else 1)) { reply = runQuery(cmd); if (reply != null) break }
        if (wireless && reply == null && noteRemoteTimeout()) return false to null
        return true to reply
    }

    /**
     * Filter-Status und -Listen streng nacheinander lesen. Listen ("… list") bekommen über LoRa ein deutlich
     * größeres Empfangsfenster (Multi-Hop) und eine Wiederholung; nur das passende Listenformat wird als
     * Antwort akzeptiert, die Liste wird danach komplett ersetzt (gelöschte Einträge verschwinden).
     */
    private suspend fun runFilterListQueries(qs: List<String>, progressOffset: Int = 0, progressTotal: Int = qs.size): Boolean {
        val wireless = _remoteAdminSession.value.targetRepeater != null
        for ((i, q) in qs.withIndex()) {
            if (!isLinkUp()) return false
            lastFilterQuery = q
            _activeReadStatusText.value = "Lese Paket-Filter (Whitelist/Blacklist)…"
            _readProgress.value = (progressOffset + i + 1) to progressTotal
            val isList = q.endsWith(" list")
            var reply: QueryReply? = null
            for (attempt in 1..(if (wireless) 2 else 1)) {
                reply = if (isList) runQuery(q, timeoutMs = filterListTimeoutMs(), trailingMs = if (wireless) FILTER_LIST_TRAILING_LORA_MS else null)
                        else runQuery(q)
                if (reply != null) break
            }
            if (wireless && reply == null && noteRemoteTimeout()) return false
            reply?.value?.let { if (isList) applyFilterList(q, it) }
        }
        return true
    }

    /** Antwortfenster für Filterlisten: über LoRa deutlich länger als für Einzelwerte. */
    private fun filterListTimeoutMs(): Long =
        if (_remoteAdminSession.value.targetRepeater != null) queryTimeoutMs() + FILTER_LIST_EXTRA_LORA_MS else queryTimeoutMs() + 600L

    /** Bekannte Kanalnamen (Presets + früher hinzugefügte) – für die Zuordnung der Whitelist-Hashes. */
    private fun knownChannelNames(): List<String> =
        (PRESET_CHANNEL_NAMES + (prefs.getStringSet(PREF_KNOWN_CHANNELS, emptySet()) ?: emptySet()) +
            _blockedChannels.value.map { it.name } + _whitelistChannels.value.filter { it.name.startsWith("#") }.map { it.name }).distinct()

    private fun rememberChannelName(name: String) {
        val n = name.trim(); if (n.isEmpty() || FilterListParser.parseWhitelistChannelHashes(n).isNotEmpty()) return
        val set = (prefs.getStringSet(PREF_KNOWN_CHANNELS, emptySet()) ?: emptySet()).toMutableSet()
        if (set.add(n)) prefs.edit().putStringSet(PREF_KNOWN_CHANNELS, set).apply()
    }

    /** Validierte Listenantwort übernehmen (ersetzt die bisherige Liste). */
    private fun applyFilterList(cmd: String, v: String) {
        when (cmd) {
            RepeaterProtocol.CMD_FILTER_CHANNEL_LIST -> _blockedChannels.value = FilterListParser.parseBlacklistChannels(v).map { BlockedChannel(it) }
            RepeaterProtocol.CMD_FILTER_PATH_LIST -> { val old = _blockedPaths.value; _blockedPaths.value = FilterListParser.parsePaths(v).map { p -> BlockedPath(p, old.firstOrNull { it.prefixHex == p }?.comment ?: "") } }
            RepeaterProtocol.CMD_FILTER_WL_CHANNEL_LIST -> {
                // Firmware speichert nur den 1-Byte-Hash -> Namen über bekannte Kanäle zuordnen
                val names = knownChannelNames()
                val old = _whitelistChannels.value
                _whitelistChannels.value = FilterListParser.parseWhitelistChannelHashes(v).map { h ->
                    val prev = old.firstOrNull { it.hashHex.equals(h, true) }
                    val name = prev?.name?.takeIf { it.startsWith("#") || it == "Public" } ?: names.firstOrNull { ChannelHash.hashHex(it) == h } ?: "0x$h"
                    WhitelistChannel(name, hashHex = h, isPreset = name in PRESET_CHANNEL_NAMES)
                }
            }
            RepeaterProtocol.CMD_FILTER_WL_PATH_LIST -> { val old = _whitelistPaths.value; _whitelistPaths.value = FilterListParser.parsePaths(v).map { p -> WhitelistPath(p, old.firstOrNull { it.prefixHex == p }?.comment ?: "") } }
        }
    }

    /** Antwort auf einen Filter-Befehl (Listen mit langem LoRa-Fenster). first = false -> Remote-Link verloren. */
    private suspend fun queryFilterValue(cmd: String): Pair<Boolean, String?> {
        val wireless = _remoteAdminSession.value.targetRepeater != null
        val isList = cmd.endsWith(" list")
        var reply: QueryReply? = null
        for (attempt in 1..(if (wireless) 2 else 1)) {
            reply = if (isList) runQuery(cmd, timeoutMs = filterListTimeoutMs(), trailingMs = if (wireless) FILTER_LIST_TRAILING_LORA_MS else null) else runQuery(cmd)
            if (reply != null) break
        }
        if (wireless && reply == null && noteRemoteTimeout()) return false to null
        return true to reply?.value
    }

    /**
     * Kompletten Filter-Stand frisch lesen (Status, dry-run, Modus, alle Listen). null = nicht vollständig lesbar
     * (dann wird weder exportiert noch abgeglichen – sonst würden Regeln verloren gehen). Muss unter dem Mutex laufen.
     * Befehle laut MeshCore simple_repeater/Filter.cpp bzw. ZephCore app/Filter.cpp; Whitelist & "filter mode" nur ZephCore.
     */
    private suspend fun readFilterSnapshot(label: String): MeshCoreBackup.FilterBackup? {
        val caps = _deviceCapabilities.value
        if (!caps.supportsBlacklist) return null
        val wl = caps.supportsWhitelist
        val cmds = buildList {
            add("filter"); add("filter dryrun"); if (wl) add(RepeaterProtocol.CMD_FILTER_MODE_QUERY)
            addAll(listOf(RepeaterProtocol.CMD_FILTER_CHANNEL_LIST, RepeaterProtocol.CMD_FILTER_PATH_LIST, "filter sender list", "filter text list", "filter watch list"))
            if (wl) { add(RepeaterProtocol.CMD_FILTER_WL_CHANNEL_LIST); add(RepeaterProtocol.CMD_FILTER_WL_PATH_LIST) }
        }
        val res = HashMap<String, String>()
        for ((i, c) in cmds.withIndex()) {
            if (!isLinkUp()) return null
            _activeReadStatusText.value = tr("{0}: Paketfilter wird gelesen ({1}/{2})…", label, i + 1, cmds.size)
            var (ok, v) = queryFilterValue(c)
            if (ok && v == null) { val again = queryFilterValue(c); ok = again.first; v = again.second }   // eine Wiederholung je Befehl
            if (!ok || v == null) return null
            res[c] = v
            if (c.endsWith(" list")) applyFilterList(c, v)
        }
        val enabled = Regex("""^filter on\b""", RegexOption.IGNORE_CASE).containsMatchIn(res.getValue("filter"))
        val wlMode = wl && res[RepeaterProtocol.CMD_FILTER_MODE_QUERY]?.contains("whitelist", true) == true
        val senders = FilterListParser.parseRules(res.getValue("filter sender list"))
        val texts = FilterListParser.parseRules(res.getValue("filter text list"))
        val watch = FilterListParser.parseWatch(res.getValue("filter watch list"))
        _senderRules.value = senders.map { FilterRule(it.pattern, it.secs, it.prob) }
        _textRules.value = texts.map { FilterRule(it.pattern, it.secs, it.prob) }
        _watchChannels.value = watch.map { WatchChannel(it) }
        return MeshCoreBackup.FilterBackup(
            mode = if (!enabled) "off" else if (wlMode) "whitelist" else "blacklist",
            dryrun = Regex("""dry-?run on""", RegexOption.IGNORE_CASE).containsMatchIn(res.getValue("filter dryrun")),
            blChannels = _blockedChannels.value.map { it.name },
            blPaths = _blockedPaths.value.map { it.prefixHex },
            senders = senders, texts = texts, watch = watch,
            wlChannels = if (wl) _whitelistChannels.value.map { MeshCoreBackup.WlChannelEntry(it.hashHex, it.name.takeIf { n -> n.startsWith("#") || n == "Public" }) } else null,
            wlPaths = if (wl) _whitelistPaths.value.map { it.prefixHex } else null
        )
    }

    // Beim Öffnen von Blacklist/Whitelist einmalig nachladen, falls der Filter aktiv, die Kanalliste aber leer ist
    private val filterAutoLoadDone = java.util.Collections.synchronizedSet(mutableSetOf<FilterMode>())
    fun ensureFilterChannelsLoaded(view: FilterMode) {
        if (!isLinkUp() || !_hasCompletedInitialRead.value || readJob?.isActive == true) return
        if (view in filterAutoLoadDone) return
        val caps = _deviceCapabilities.value
        val isWl = view == FilterMode.WHITELIST
        val supported = if (isWl) caps.supportsWhitelist else caps.supportsBlacklist
        val empty = if (isWl) _whitelistChannels.value.isEmpty() else _blockedChannels.value.isEmpty()
        if (!supported || _filterStats.value.activeMode != view || !empty) return
        // Erst als "erledigt" markieren, wenn der Ladevorgang wirklich startet (sonst beim nächsten Öffnen erneut versuchen)
        if (!tryMarkBusy(BusyKey.FILTER_LOAD)) return
        filterAutoLoadDone += view
        startRead(cancelPrevious = false) {
            runFilterListQueries(listOf(if (isWl) RepeaterProtocol.CMD_FILTER_WL_CHANNEL_LIST else RepeaterProtocol.CMD_FILTER_CHANNEL_LIST))
            _activeReadStatusText.value = null
        }.invokeOnCompletion { clearBusy(BusyKey.FILTER_LOAD) }
    }

    // ---------------------------------------------------------------------------------------
    // Regionen & Scope-Routing (MeshCore/ZephCore CommonCLI::handleRegionCmd)
    // ---------------------------------------------------------------------------------------
    /** "region", "region home", "region default" lesen. false = Remote-Link verloren. Muss unter dem Mutex laufen. */
    private suspend fun readRegions(progressOffset: Int? = null, progressTotal: Int = 0): Boolean {
        val (linkOk, state) = fetchRegions(progressOffset, progressTotal)
        state?.let { _regionState.value = it }   // nur bei erfolgreichem Auslesen ersetzen
        return linkOk
    }

    /**
     * Liest den Regionen-Stand, OHNE den State zu verändern. first = false: Remote-Link verloren;
     * second = null: kein vollständiges Ergebnis (Timeout) – der bisherige Stand bleibt erhalten.
     */
    private suspend fun fetchRegions(progressOffset: Int? = null, progressTotal: Int = 0): Pair<Boolean, RegionState?> {
        val wireless = _remoteAdminSession.value.targetRepeater != null
        if (progressOffset != null) { _activeReadStatusText.value = QuerySpec.statusTextFor("region"); _readProgress.value = (progressOffset + 1) to progressTotal }
        val lines = mutableListOf<String>()
        var tree: QueryReply? = null
        for (attempt in 1..(if (wireless) 2 else 1)) {
            lines.clear()
            tree = runQuery("region", capture = lines, timeoutMs = queryTimeoutMs() + if (wireless) 1500L else 300L,
                trailingMs = if (wireless) REGION_TRAILING_LORA_MS else REGION_TRAILING_USB_MS)   // alle Baumzeilen abwarten
            if (tree != null) break
        }
        if (tree == null) return !(wireless && noteRemoteTimeout()) to null   // keine Antwort: bisheriger Zustand bleibt
        if (tree.value == null) return true to RegionState(supported = false)   // Firmware ohne Regionen
        val (list, wildcardFlood, homeMark) = RegionParser.parseTree(lines)
        if (progressOffset != null) _readProgress.value = (progressOffset + 2) to progressTotal
        val h = queryWithRetries("region home"); if (!h.first) return false to null
        if (progressOffset != null) _readProgress.value = (progressOffset + 3) to progressTotal
        val d = queryWithRetries("region default"); if (!d.first) return false to null
        val home = if (RegionParser.isHomeReply(h.second?.value)) RegionParser.parseHome(h.second?.value) else homeMark
        return true to RegionState(
            supported = true, regions = list, wildcardFlood = wildcardFlood,
            home = home, default = RegionParser.parseDefault(d.second?.value)
        )
    }

    /** "Aktualisieren" in der Regionen-Sektion. */
    fun refreshRegions() {
        if (!isLinkUp()) { notifyUser("Nicht verbunden."); return }
        if (!tryMarkBusy(BusyKey.REGION)) return
        startRead(cancelPrevious = false) { readRegions() }.invokeOnCompletion { clearBusy(BusyKey.REGION) }
    }

    /**
     * Regionen-Befehle streng nacheinander senden; beim ersten Fehler abbrechen (Fehlertext der Firmware
     * wird angezeigt). Jeder Befehl wartet auf seine echte Bestätigung ([QuerySpec.regionActionValidator]:
     * "OK…", " home is now …", " default scope is now …"). Danach folgt ein Nachlauf ([REGION_SETTLE_LORA_MS]
     * bzw. [REGION_SETTLE_USB_MS]), in dem verspätete LoRa-Antworten abfließen, und erst dann wird der
     * Regionen-Stand neu eingelesen – so überschreibt kein zu frühes Nachlesen die gerade gesendete Änderung.
     */
    private fun runRegionCommands(cmds: List<String>, successMsg: String) {
        if (!isLinkUp()) { notifyUser("Nicht verbunden."); return }
        if (!tryMarkBusy(BusyKey.REGION)) return
        startRead(cancelPrevious = false) {
            var err: String? = null
            for (c in cmds) {
                if (!isLinkUp()) { err = tr("Verbindung getrennt"); break }
                val r = runQuery(c, timeoutMs = queryTimeoutMs() + 600L)
                if (r == null) { err = tr("keine Antwort auf \"{0}\"", c); break }
                if (r.value == null) { err = "${r.error ?: "Err"} ($c)"; break }
            }
            delay(if (_remoteAdminSession.value.targetRepeater != null) REGION_SETTLE_LORA_MS else REGION_SETTLE_USB_MS)
            if (isLinkUp()) readRegions()
            _uiEvents.tryEmit(if (err == null) tr(successMsg) else tr("Regionen: nicht übernommen – {0}", err))
        }.invokeOnCompletion { clearBusy(BusyKey.REGION) }
    }

    private fun regionArg(name: String) = name.trim().removePrefix("#")

    fun setHomeRegion(name: String) {
        val n = regionArg(name)
        if (n != "*" && !RegionParser.isValidName(n)) { notifyUser("Ungültiger Regionsname (keine Leerzeichen, max. 30 Zeichen)."); return }
        runRegionCommands(listOf("region home $n", "region save"), "Home-Region gesetzt und gespeichert.")
    }

    /** [name] = null -> "region default <null>" (kein Default-Scope). */
    fun setDefaultRegion(name: String?) {
        val n = name?.let(::regionArg)
        if (n != null && !RegionParser.isValidName(n)) { notifyUser("Ungültiger Regionsname (keine Leerzeichen, max. 30 Zeichen)."); return }
        runRegionCommands(listOf("region default ${n ?: "<null>"}"), "Default-Region gesetzt und gespeichert.")
    }

    fun setRegionFlood(name: String, allow: Boolean) {
        val n = regionArg(name)
        runRegionCommands(listOf("region ${if (allow) "allowf" else "denyf"} $n", "region save"), if (allow) "Flood für Region erlaubt." else "Flood für Region gesperrt.")
    }

    fun removeRegion(name: String) {
        val n = regionArg(name)
        if (n == "*") return
        runRegionCommands(listOf("region remove $n", "region save"), "Region gelöscht.")
    }

    fun addRegion(name: String, parent: String, flood: Boolean) {
        val n = regionArg(name); val p = regionArg(parent).ifBlank { "*" }
        if (!RegionParser.isValidName(n) || (p != "*" && !RegionParser.isValidName(p))) { notifyUser("Ungültiger Regionsname (keine Leerzeichen, max. 30 Zeichen)."); return }
        val cmds = buildList {
            add(if (p == "*") "region put $n" else "region put $n $p")
            if (!flood) add("region denyf $n")   // "region put" erlaubt Flood standardmäßig
            add("region save")
        }
        runRegionCommands(cmds, "Region hinzugefügt und gespeichert.")
    }
    private fun handleIncomingFrame(data: ByteArray) {
        if (data.isEmpty()) return
        // Direkter USB-Repeater (ASCII-CLI): vermeintliche Binär-Frames sind Störbytes – nie auf Companion umschalten
        if (_deviceCapabilities.value.isDirectRepeater) { logTerminal(tr("[ignoriert: Binärdaten von direktem USB-Repeater] {0} Byte", data.size), false, systemOnly = true); return }
        lastCompanionRxMs = System.currentTimeMillis()
        if (!_deviceCapabilities.value.isCompanion) {
            _deviceCapabilities.value = _deviceCapabilities.value.copy(isCompanion = true, detectionComplete = true)
            refreshMeshContacts()
        }
        when (data[0]) {
            RepeaterProtocol.PACKET_SELF_INFO, RepeaterProtocol.PACKET_DEVICE_INFO -> { if (!_deviceCapabilities.value.isCompanion) { _deviceCapabilities.value = _deviceCapabilities.value.copy(isCompanion = true, detectionComplete = true); refreshMeshContacts() } }
            // RESP_CODE_SENT: [0x06][flood][tag(4)][est_timeout_ms(4, LE)] – nur "unterwegs", KEIN Login-Erfolg
            RepeaterProtocol.PACKET_SENT -> {
                if (_remoteAdminSession.value.isLoggingIn) {
                    val est = if (data.size >= 10) ((data[6].toLong() and 0xFF) or ((data[7].toLong() and 0xFF) shl 8) or ((data[8].toLong() and 0xFF) shl 16) or ((data[9].toLong() and 0xFF) shl 24)) else 0L
                    val timeout = (est + LOGIN_TIMEOUT_BUFFER_MS).coerceIn(LOGIN_DEFAULT_TIMEOUT_MS, LOGIN_MAX_TIMEOUT_MS)
                    _remoteAdminSession.value = _remoteAdminSession.value.copy(sessionStatus = "Login unterwegs…")
                    armLoginTimeout(timeout)
                }
            }
            RepeaterProtocol.PACKET_CONTACT_START -> { _deviceCapabilities.value = _deviceCapabilities.value.copy(isCompanion = true, detectionComplete = true); _isRefreshingContacts.value = true; _meshContacts.value = emptyList() }
            RepeaterProtocol.PACKET_CONTACT -> RepeaterProtocol.parseContactFrame(data)?.let { ct -> val l = _meshContacts.value.toMutableList(); val i = l.indexOfFirst { it.pubKeyHex == ct.pubKeyHex }; if (i >= 0) l[i] = ct else l.add(ct); _meshContacts.value = l.sortedWith(compareByDescending<MeshContact> { it.isRepeater }.thenBy { it.name }) }
            RepeaterProtocol.PACKET_CONTACT_END -> _isRefreshingContacts.value = false
            // Remote-CLI-Antworten landen in der Offline-Queue des Companions -> per CMD_SYNC_NEXT_MESSAGE abholen
            RepeaterProtocol.PUSH_MSG_WAITING -> requestNextMessage(fromPush = true)
            RepeaterProtocol.PACKET_NO_MORE_MESSAGES -> syncInFlightSinceMs = 0L
            RepeaterProtocol.PACKET_CONTACT_MSG_V3 -> { RepeaterProtocol.parseContactMsgV3(data)?.let { (p, txt, snr) -> handleRemoteMessage(p, txt, " | SNR ${"%.1f".format(snr)}") }; requestNextMessage(fromPush = false) }
            RepeaterProtocol.PACKET_CONTACT_MSG_V2 -> { RepeaterProtocol.parseContactMsgV2(data)?.let { (p, txt) -> handleRemoteMessage(p, txt, "") }; requestNextMessage(fromPush = false) }
            0x08.toByte(), 0x11.toByte(), 0x1B.toByte() -> requestNextMessage(fromPush = false) // Kanal-Nachrichten: Queue weiter leeren
            // Login-Erfolg NUR über PUSH_LOGIN_SUCCESS – auch verspätet nach dem Timeout (Multi-Hop-LoRa)
            RepeaterProtocol.PUSH_LOGIN_SUCCESS -> if (isLoginPushForTarget(data) && (loginAttemptPending || _remoteAdminSession.value.isLoggingIn) && !_remoteAdminSession.value.isLoggedIn) onLoginSuccess()
            RepeaterProtocol.PUSH_LOGIN_FAIL -> if (isLoginPushForTarget(data) && (loginAttemptPending || _remoteAdminSession.value.isLoggingIn)) onLoginFailed()
            RepeaterProtocol.PACKET_CLI_REPLY -> { pendingReplySignal?.complete(Unit); val r = String(data, 1, data.size - 1, StandardCharsets.UTF_8).trimEnd('\u0000', '\r', '\n'); logTerminal("< [Companion] $r", false, systemOnly = (lastCommandWasAuto || System.currentTimeMillis() < activeAutoUntilMs)); parseIncomingLine(r) }
            RepeaterProtocol.PACKET_ERROR -> { val code = if (data.size > 1) data[1].toInt() and 0xFF else -1; logTerminal(tr("< [Fehler 0x01] Code={0}", code), false, systemOnly = true); if (_remoteAdminSession.value.isLoggingIn) { loginTimeoutJob?.cancel(); loginAttemptPending = false; _remoteAdminSession.value = _remoteAdminSession.value.copy(isLoggingIn = false, sessionStatus = tr("Fehler ({0})", code)) } }
        }
    }
    /**
     * Remote-CLI-Antwort (LoRa). Login wird ausschließlich über PUSH_LOGIN_SUCCESS bestätigt.
     * Läuft ein getaggter Befehl, zählt nur eine Antwort mit genau diesem Tag; Antworten mit fremdem Tag
     * (verspätet, z. B. "get name" nach Timeout) oder ohne Tag werden verworfen und landen nie im Folgebefehl.
     */
    private fun onRemoteReply(txt: String) {
        val expected = activeQueryTag
        val (tag, body) = CliReplyTag.split(txt)
        when (CliReplyTag.classify(txt, expected)) {
            CliReplyTag.Match.CURRENT -> parseIncomingLine(body)
            CliReplyTag.Match.STALE -> logTerminal(tr("[verspätet verworfen: #{0} gehört nicht zu \"{1}\"] {2}", tag, activeQueryCommand ?: "-", body.trim()), false, systemOnly = true)
            CliReplyTag.Match.UNTAGGED ->
                if (expected == null) parseIncomingLine(txt)
                else logTerminal(tr("[ignoriert: ohne Tag #{0} für \"{1}\"] {2}", expected, activeQueryCommand ?: "-", txt.trim()), false, systemOnly = true)
        }
    }
    private fun parseIncomingLine(line: String) {
        activeCapture?.let { cap ->
            for (sub in line.split('\n')) {
                val l = sub.trimEnd('\r').replaceFirst(Regex("""^\s*-> ?"""), "")
                if (l.isNotBlank() && !l.trim().equals(lastSentCommand, ignoreCase = true)) cap += l
            }
        }
        if (!_deviceCapabilities.value.isDirectRepeater && (line.contains("_Update_Part") || line.contains("Companion"))) {
            if (!_deviceCapabilities.value.isCompanion) {
                _deviceCapabilities.value = _deviceCapabilities.value.copy(isCompanion = true, detectionComplete = true)
                viewModelScope.launch {
                    sendCompanionBytes(RepeaterProtocol.buildAppStartFrame())
                    delay(250L)
                    refreshMeshContacts()
                }
            }
            return
        }
        // Antwortformate:
        //  - USB-Direkt (MeshCore/ZephCore Serial-CLI): Echo "get name", danach "  -> > MeinRepeater"
        //  - Remote-Admin (LoRa) / Companion-CLI:       "> MeinRepeater"
        val raw = line.trim()
        val isArrowReply = raw.startsWith("->")
        val body = raw.removePrefix("->").trim().removePrefix(">").trim()
        if (body.isEmpty()) return
        // Echo des gesendeten Befehls (USB-Serial) ignorieren – sonst würde z. B. "get name" als Name übernommen
        if (body.equals(lastSentCommand, ignoreCase = true)) return
        val usbDirect = _selectedConnectionType.value == ConnectionType.USB_SERIAL && !_deviceCapabilities.value.isCompanion
        if (usbDirect && isArrowReply) { usbArrowRepliesSeen = true; usbReplySeen = true }
        // Über USB-Direkt nur echte "->"-Antwortzeilen als Antwort werten (keine Log-/Bootzeilen),
        // solange die Firmware dieses Format nachweislich verwendet.
        val acceptAsReply = !usbDirect || isArrowReply || raw.startsWith(">") || !usbArrowRepliesSeen
        // Antwort ausschließlich dem aktiven Befehl zuordnen – und nur, wenn sie dessen Format erfüllt.
        if (acceptAsReply) activeQueryWaiter?.let { w ->
            when {
                w.isCompleted -> {}
                QuerySpec.isErrorReply(body) -> { usbReplySeen = true; w.complete(QueryReply(null, body)) }
                activeQueryValidator?.invoke(body) == true -> { usbReplySeen = true; w.complete(QueryReply(body)) }
                else -> logTerminal(tr("[ignoriert: passt nicht zu \"{0}\"] {1}", activeQueryCommand, body), false, systemOnly = true)
            }
        }
        // Nach einem Umschalten veraltete Status-Antworten 1,5 s lang nicht auf Modus/Ein-Aus anwenden (kein Hin- und Herspringen)
        // Solange ein Ein/Aus-Befehl läuft, eingehende Status-Updates für Modus und Ein/Aus ignorieren (kein Zurückspringen)
        // Nur Antworten auf "filter…"-Befehle auswerten (keine Verwechslung durch fremde Zeilen)
        val parsedStats = if (activeQueryCommand?.startsWith("filter") == true) RepeaterProtocol.parseFilterStats(body, _filterStats.value) else _filterStats.value
        _filterStats.value = if (_filterSwitchPending.value) parsedStats.copy(mode = _filterStats.value.mode, filterEnabled = _filterStats.value.filterEnabled) else parsedStats

        // 1. Telemetrie ("power") wird ausschließlich in applyQueryResult() übernommen.
        // 3.–5. Feldwerte (TX, Name, Owner, Board, ...) werden NUR noch über applyQueryResult() gesetzt.

        // 6. Filter-Listen werden ausschließlich in runFilterListQueries() aus der validierten Antwort übernommen.
        // Filter-Fähigkeiten werden ausschließlich im Start-Scan über "filter help" / "filter wl help" erkannt
    }

    fun setRegion(region: CountryRegion) { _radioConfig.value = _radioConfig.value.copy(region = region, frequencyMhz = region.frequencyMhz, bandwidthKhz = region.bandwidthKhz, spreadingFactor = region.spreadingFactor, codingRate = region.codingRate) }
    fun setCustomRadioParams(freq: Double, bw: Double, sf: Int, cr: String) { _radioConfig.value = _radioConfig.value.copy(frequencyMhz = freq, bandwidthKhz = bw, spreadingFactor = sf.coerceIn(5, 12), codingRate = cr) }
    fun setAntennaPreset(preset: AntennaPreset) { _radioConfig.value = _radioConfig.value.copy(antennaPreset = preset) }
    fun setCustomAntennaGain(gain: Double) { _radioConfig.value = _radioConfig.value.copy(customAntennaGainDbi = gain) }
    fun setCableLoss(loss: Double) { _radioConfig.value = _radioConfig.value.copy(cableLossDb = loss, cableLossUserSet = true) }

    /** "board"-Antwort gegen die BoardDatabase matchen; unbekannt -> generisches Profil (manuell wählbar). */
    private fun applyDetectedBoard(board: String) {
        val match = BoardDatabase.match(board)
        val rc = _radioConfig.value
        val profile = match ?: BoardDatabase.GENERIC
        _radioConfig.value = rc.copy(
            board = profile, boardAutoDetected = match != null,
            cableLossDb = if (rc.cableLossUserSet) rc.cableLossDb else profile.defaultPigtailLossDb
        )
    }

    /** Manuelle Board-Auswahl (wenn die automatische Erkennung nicht greift). */
    fun selectBoardProfile(profile: BoardProfile) {
        val rc = _radioConfig.value
        _radioConfig.value = rc.copy(
            board = profile, boardAutoDetected = false,
            cableLossDb = if (rc.cableLossUserSet) rc.cableLossDb else profile.defaultPigtailLossDb
        )
    }

    /** Mit aktueller Antenne/Kabel/Region empfohlener (max. legaler) Transceiver-TX-Wert, begrenzt auf den Hardwarebereich. */
    fun recommendedTxDbm(rc: RadioAntennaConfig = _radioConfig.value): Int =
        floor(rc.recommendedMaxTxDbm).toInt().coerceIn(rc.region.minTxPowerDbm, floor(rc.hardwareMaxTxDbm).toInt())

    /** "Empfohlenen TX-Wert übernehmen": trägt den Wert nur in den Entwurf (basicSetup.txPowerDbm) ein – gespeichert wird unter "Funk & Region". */
    fun adoptRecommendedTx() {
        val tx = recommendedTxDbm()
        _basicSetup.value = _basicSetup.value.copy(txPowerDbm = tx.toString())
        _radioConfig.value = _radioConfig.value.copy(txPowerDbm = tx.toDouble())
        notifyUser(tr("TX-Empfehlung {0} dBm übernommen – unter \"Funk & Region\" anpassen und speichern.", tx))
    }

    /** Regions-Preset: Werte in die editierbaren Felder übernehmen (TX-Leistung bleibt unverändert). */
    fun applyRegionPreset(region: CountryRegion) {
        setRegion(region)
        val bw = java.math.BigDecimal(region.bandwidthKhz).stripTrailingZeros().toPlainString()
        _basicSetup.value = _basicSetup.value.copy(
            freqMhz = String.format(java.util.Locale.US, "%.3f", region.frequencyMhz),
            bwKhz = bw,
            sf = region.spreadingFactor.toString(),
            cr = region.codingRateNum.toString(),
            dutyCyclePercent = if (region == CountryRegion.US915) "100" else "10"
        )
    }

    /** Editierte Funkfelder übernehmen und – soweit gültig – in radioConfig spiegeln (für die ERP-Rechnung). */
    fun updateRadioDraft(updated: BasicSetupState) {
        _basicSetup.value = updated
        val rc = _radioConfig.value
        _radioConfig.value = rc.copy(
            frequencyMhz = updated.freqMhz.toDoubleOrNull()?.takeIf { it in 137.0..1020.0 } ?: rc.frequencyMhz,
            bandwidthKhz = updated.bwKhz.toDoubleOrNull()?.takeIf { it in 7.8..500.0 } ?: rc.bandwidthKhz,
            spreadingFactor = updated.sf.toIntOrNull()?.takeIf { it in 5..12 } ?: rc.spreadingFactor,
            codingRate = updated.cr.toIntOrNull()?.takeIf { it in 5..8 }?.let { "4/$it" } ?: rc.codingRate,
            txPowerDbm = updated.txPowerDbm.toIntOrNull()?.toDouble() ?: rc.txPowerDbm
        )
    }

    /** "Funk & Region" speichern: nur geänderte Werte (gegenüber dem vom Node gelesenen Stand) senden. */
    fun saveRadioDraft() {
        val s = _basicSetup.value; val b = _nodeBaseline.value
        val freq = s.freqMhz.toDoubleOrNull(); val bw = s.bwKhz.toDoubleOrNull(); val sf = s.sf.toIntOrNull(); val cr = s.cr.toIntOrNull()
        val tx = s.txPowerDbm.toIntOrNull(); val duty = s.dutyCyclePercent.toDoubleOrNull()
        if (freq == null || freq !in 137.0..1020.0 || bw == null || bw !in 7.8..500.0 || sf == null || sf !in 5..12 || cr == null || cr !in 5..8 || tx == null || InputValidator.validateTxPowerDbm(tx.toDouble(), _radioConfig.value.maxTxInputDbm.toDouble()).isError || duty == null || duty !in 1.0..100.0) {
            notifyUser("Bitte ungültige Funkwerte korrigieren (rot markierte Felder)."); return
        }
        val changes = mutableListOf<SetupChange>()
        val radioChanged = s.freqMhz != b.freqMhz || s.bwKhz != b.bwKhz || s.sf != b.sf || s.cr != b.cr
        if (radioChanged) changes.add(SetupChange(RepeaterProtocol.buildSetRadio(freq, bw, sf, cr)) { it.copy(freqMhz = s.freqMhz, bwKhz = s.bwKhz, sf = s.sf, cr = s.cr) })
        if (s.txPowerDbm != b.txPowerDbm) changes.add(SetupChange(RepeaterProtocol.buildSetTx(tx)) { it.copy(txPowerDbm = s.txPowerDbm) })
        if (s.dutyCyclePercent != b.dutyCyclePercent) changes.add(SetupChange("set dutycycle ${s.dutyCyclePercent.trim()}") { it.copy(dutyCyclePercent = s.dutyCyclePercent) })
        if (changes.isEmpty()) return // still: keine Änderungen (z. B. "Weiter" in der Tour)
        if (!_radioConfig.value.isCompliant) notifyUser("Hinweis: ERP-Grenzwert mit aktueller Antenne überschritten (siehe Hardware & Antenne).")
        // Basis erst nach Bestätigung aktualisieren (nur bestätigte Befehle)
        enqueueCommands(changes.map { it.command }, busyKey = BusyKey.RADIO, onResults = { ok -> commitConfirmed(changes, ok) }) { confirmed ->
            notifyUser(when {
                confirmed < changes.size -> tr("{0} von {1} Einstellung(en) bestätigt – bitte prüfen.", confirmed, changes.size)
                radioChanged -> tr("{0} Funkeinstellung(en) übertragen – neue Funkparameter ggf. erst nach Neustart aktiv.", changes.size)
                else -> tr("{0} Funkeinstellung(en) übertragen.", changes.size)
            })
        }
    }

    fun applyRadioSettings(testMode: Boolean = false, testTimeoutMins: Int = 5) {
        val rc = _radioConfig.value; val cr = rc.codingRate.substringAfter('/').toIntOrNull() ?: 5
        val cmds = listOf(
            if (testMode) RepeaterProtocol.buildTempRadio(rc.frequencyMhz, rc.bandwidthKhz, rc.spreadingFactor, cr, testTimeoutMins) else RepeaterProtocol.buildSetRadio(rc.frequencyMhz, rc.bandwidthKhz, rc.spreadingFactor, cr),
            RepeaterProtocol.buildSetTx(rc.txPowerDbm.toInt())
        )
        enqueueCommands(cmds) { notifyUser(if (testMode) tr("Temporärer Funk-Test ({0} Min.)", testTimeoutMins) else "Funkwerte dauerhaft im Flash gespeichert!") }
    }
    // ---------------------------------------------------------------------------------------
    // Mesh-Schonmodus
    // Firmware-Fakt (MeshCore & ZephCore CommonCLI): "tempradio" ändert nur freq/bw/sf/cr – NICHT die
    // Sendeleistung – und lehnt 0 Minuten ab. Die Drosselung erfolgt daher per "set tx <reduziert>",
    // die Rücksetzung per "set tx <original>" (Ablauf, Beenden). Der Originalwert wird pro Repeater in den
    // Prefs gemerkt: bricht die Verbindung ab, wird die Rücksetzung beim nächsten Verbinden nachgeholt.
    // ---------------------------------------------------------------------------------------
    // Remote: Pubkey des Ziel-Repeaters; USB-direkt: eigener Pubkey bzw. Repeater-Name (nicht pauschal "local")
    private fun meshCareKey(): String = "meshcare_restore_" + (_remoteAdminSession.value.targetRepeater?.pubKeyHex
        ?: _basicSetup.value.publicKeyHex.ifBlank { "local_" + _basicSetup.value.nodeName.trim().ifBlank { "unknown" } })

    fun startMeshCare(duration: WatchdogDuration, testTxPowerDbm: Double = 5.0) {
        if (!isLinkUp()) { notifyUser("Nicht verbunden."); return }
        val cur = _meshCareState.value
        // Original-TX: bei bereits aktivem Modus den gemerkten Wert behalten, sonst den vom Repeater gelesenen Stand
        val original = if (cur.isActive) cur.savedOriginalTxPowerDbm.toInt()
            else (_nodeBaseline.value.txPowerDbm.toIntOrNull() ?: _basicSetup.value.txPowerDbm.toIntOrNull() ?: _radioConfig.value.txPowerDbm.toInt())
        val reduced = testTxPowerDbm.toInt()
        if (reduced >= original) { notifyUser(tr("Die Drosselung ({0} dBm) muss unter der aktuellen Sendeleistung ({1} dBm) liegen.", reduced, original)); return }
        prefs.edit().putInt(meshCareKey(), original).apply()
        meshCareJob?.cancel()
        val t = duration.minutes * 60
        _meshCareState.value = MeshCareState(isActive = true, reducedTxPowerDbm = reduced.toDouble(), savedOriginalTxPowerDbm = original.toDouble(),
            duration = duration, remainingSeconds = t, totalSeconds = t, lastKeepAliveMillis = System.currentTimeMillis())
        enqueueCommands(listOf(RepeaterProtocol.buildSetTx(reduced))) { answered ->
            if (answered == 0) {
                meshCareJob?.cancel(); _meshCareState.value = MeshCareState(); prefs.edit().remove(meshCareKey()).apply()
                notifyUser("Schonmodus nicht gestartet – Repeater hat \"set tx\" nicht bestätigt.")
            } else notifyUser(tr("Schonmodus aktiv: TX {0} dBm für {1} Min. (danach zurück auf {2} dBm).", reduced, duration.minutes, original))
        }
        meshCareJob = viewModelScope.launch {
            while (_meshCareState.value.isActive && _meshCareState.value.remainingSeconds > 0) {
                delay(1000L)
                _meshCareState.value = _meshCareState.value.copy(remainingSeconds = _meshCareState.value.remainingSeconds - 1)
            }
            if (_meshCareState.value.isActive) stopMeshCare(restoreTx = true)
        }
    }

    /** Verlängern: nur die App-Zeit (die Sendeleistung bleibt reduziert, kein erneuter Befehl nötig). */
    fun extendMeshCare(addMinutes: Int = 10) {
        if (!_meshCareState.value.isActive) return
        val a = addMinutes * 60
        _meshCareState.value = _meshCareState.value.copy(remainingSeconds = _meshCareState.value.remainingSeconds + a, totalSeconds = _meshCareState.value.totalSeconds + a)
        notifyUser(tr("Schonmodus um {0} Min. verlängert.", addMinutes))
    }

    /**
     * Schonmodus beenden. [restoreTx] = true: ursprüngliche Sendeleistung per "set tx" zurückschreiben
     * (bei Verbindungsabbruch false – dann holt [restorePendingMeshCareTx] das beim nächsten Verbinden nach).
     */
    fun stopMeshCare(restoreTx: Boolean = true) {
        val st = _meshCareState.value
        meshCareJob?.cancel()
        _meshCareState.value = MeshCareState()
        if (!st.isActive || !restoreTx) return
        if (!isLinkUp()) { notifyUser("Schonmodus beendet – Sendeleistung wird beim nächsten Verbinden zurückgesetzt."); return }
        val key = meshCareKey()
        val original = st.savedOriginalTxPowerDbm.toInt()
        enqueueCommands(listOf(RepeaterProtocol.buildSetTx(original))) { answered ->
            if (answered > 0) { prefs.edit().remove(key).apply(); notifyUser(tr("Schonmodus beendet: TX wieder {0} dBm.", original)) }
            else notifyUser("Rücksetzen der Sendeleistung nicht bestätigt – wird beim nächsten Verbinden erneut versucht.")
        }
    }

    /** Nach dem Start-Scan: offene Schonmodus-Rücksetzung (z. B. nach Verbindungsabbruch) nachholen. */
    private fun restorePendingMeshCareTx() {
        val key = meshCareKey()
        if (!prefs.contains(key) || _meshCareState.value.isActive) return
        val original = prefs.getInt(key, -100).takeIf { it in -9.._radioConfig.value.maxTxInputDbm } ?: run { prefs.edit().remove(key).apply(); return }
        enqueueCommands(listOf(RepeaterProtocol.buildSetTx(original))) { answered ->
            if (answered > 0) {
                prefs.edit().remove(key).apply()
                _basicSetup.value = _basicSetup.value.copy(txPowerDbm = original.toString())
                _nodeBaseline.value = _nodeBaseline.value.copy(txPowerDbm = original.toString())
                notifyUser(tr("Schonmodus-Rücksetzung nachgeholt: TX wieder {0} dBm.", original))
            }
        }
    }
    /** "Versorgung abfragen" (Nutzeraktion): genau ein "power"-Befehl, kein Polling. */
    fun refreshPowerStatus() {
        if (!isLinkUp()) return
        if (!tryMarkBusy(BusyKey.POWER)) return
        startRead(cancelPrevious = false) {
            _activeReadStatusText.value = QuerySpec.statusTextFor(RepeaterProtocol.CMD_POWER)
            runQuery(RepeaterProtocol.CMD_POWER)
        }.invokeOnCompletion { clearBusy(BusyKey.POWER) }
    }
    fun saveConfigToFlash() { notifyUser("Alle Parameter werden sofort im Flash gespeichert.") }
    private val _filterSwitchPending = MutableStateFlow(false)
    /** true, solange ein Blacklist/Whitelist-Ein/Aus-Befehl auf die Bestätigung des Repeaters wartet. */
    val filterSwitchPending: StateFlow<Boolean> = _filterSwitchPending

    /**
     * Blacklist/Whitelist ein- oder ausschalten ([target] = null -> aus).
     * Firmware (Filter.cpp): Ein/Aus = "filter on|off", Modus getrennt per "filter mode blacklist|whitelist"
     * ("filter mode off" existiert nicht). Der Zustand wird sofort optimistisch gesetzt; Status-Updates
     * werden bis zur Bestätigung pausiert und danach EINMAL nachgelesen.
     */
    fun setFilterActive(target: FilterMode?) {
        if (!isLinkUp() || _filterSwitchPending.value) return
        val caps = _deviceCapabilities.value
        val cmds = if (target == null) listOf(RepeaterProtocol.CMD_FILTER_OFF) else buildList {
            if (caps.supportsWhitelist) add(if (target == FilterMode.WHITELIST) RepeaterProtocol.CMD_FILTER_MODE_WHITELIST else RepeaterProtocol.CMD_FILTER_MODE_BLACKLIST)
            add(RepeaterProtocol.CMD_FILTER_ON)
        }
        _filterSwitchPending.value = true
        _filterStats.value = if (target == null) _filterStats.value.copy(filterEnabled = false) else _filterStats.value.copy(filterEnabled = true, mode = target)
        enqueueCommands(cmds) { answered ->
            _filterSwitchPending.value = false
            if (answered < cmds.size) notifyUser("Filter-Umschaltung nicht vollständig bestätigt – Status wird neu gelesen.")
            // einmalig den echten Zustand nachlesen
            startRead(cancelPrevious = false) {
                _activeReadStatusText.value = "Filter-Status wird abgefragt…"
                runQuery("filter")
                if (caps.supportsWhitelist) runQuery("filter mode")
            }
        }
    }
    fun setFilterEnabled(enabled: Boolean) { _filterStats.value = _filterStats.value.copy(filterEnabled = enabled); sendCommand(if (enabled) RepeaterProtocol.CMD_FILTER_ON else RepeaterProtocol.CMD_FILTER_OFF) }
    // Dry-Run / Max-Hops / Alter: Anzeige erst nach Bestätigung durch den Repeater ändern (STYLE_GUIDE §2.2)
    fun setFilterDryRun(dryRun: Boolean) {
        val cmd = if (dryRun) RepeaterProtocol.CMD_FILTER_DRYRUN_ON else RepeaterProtocol.CMD_FILTER_DRYRUN_OFF
        sendBusy(BusyKey.FILTER_DRYRUN, cmd) { ok -> if (ok) _filterStats.value = _filterStats.value.copy(dryRun = dryRun) else notConfirmed(cmd) }
    }
    /** Optimistisch sofort umschalten; Status-Antworten der nächsten 1,5 s überschreiben den Modus nicht. */
    fun setFilterMode(mode: FilterMode) { _filterStats.value = _filterStats.value.copy(mode = mode); sendCommand(if (mode == FilterMode.WHITELIST) RepeaterProtocol.CMD_FILTER_MODE_WHITELIST else RepeaterProtocol.CMD_FILTER_MODE_BLACKLIST) }
    fun setFilterMaxHops(hops: Int) {
        val cmd = RepeaterProtocol.buildFilterHops(0, hops)
        sendBusy(BusyKey.FILTER_HOPS, cmd) { ok -> if (ok) _filterStats.value = _filterStats.value.copy(maxHops = hops) else notConfirmed(cmd) }
    }
    fun setFilterAge(mins: Int) {
        val cmd = RepeaterProtocol.buildFilterAge(mins)
        sendBusy(BusyKey.FILTER_AGE, cmd) { ok -> if (ok) _filterStats.value = _filterStats.value.copy(ageMinutes = mins.coerceAtLeast(0)) else notConfirmed(cmd) }
    }
    private var lastFilterQuery = ""
    fun updateBasicSetupDraft(updated: BasicSetupState) { _basicSetup.value = updated }
    /** Expliziter Klick auf "Neu einlesen": kompletten Start-Scan erneut ausführen. */
    fun readBasicSetupFromNode() {
        if (!isLinkUp()) return
        initialScanRunning = false
        startInitialScan()
    }
    /**
     * Sendet [changes] streng nacheinander. Die "gespeichert"-Basis wird erst NACH der Bestätigung durch den
     * Repeater und nur für die bestätigten Befehle aktualisiert – fehlgeschlagene Werte bleiben "geändert"
     * und werden beim nächsten Speichern erneut gesendet.
     */
    fun applyBasicSetupChanges(changes: List<SetupChange>, busyKey: String? = null) {
        if (changes.isEmpty()) return // still: z. B. "Weiter" in der Tour ohne Änderungen
        enqueueCommands(changes.map { it.command }, busyKey = busyKey, onResults = { ok -> commitConfirmed(changes, ok) }) { confirmed ->
            notifyUser(if (confirmed == changes.size) tr("{0} Einstellung(en) übertragen!", changes.size) else tr("{0} von {1} Einstellung(en) bestätigt – bitte prüfen.", confirmed, changes.size))
        }
    }

    /**
     * Status-LEDs (Hardware-Screen): sendet "set leds <mode>", sofern vom bestätigten Stand abweichend.
     * ZephCore: on/off/default; Standard-MeshCore kennt "set leds" nicht; "boot" = Custom-Erweiterung.
     */
    fun applyLedMode(busyKey: String = BusyKey.LEDS) {
        val mode = _basicSetup.value.ledsMode.trim()
        if (mode.isEmpty() || mode == _nodeBaseline.value.ledsMode) return
        applyBasicSetupChanges(listOf(SetupChange("set leds $mode") { it.copy(ledsMode = mode) }), busyKey)
    }

    private fun commitConfirmed(changes: List<SetupChange>, ok: List<Boolean>) {
        var b = _nodeBaseline.value
        changes.forEachIndexed { i, c -> if (ok.getOrElse(i) { false }) b = c.commit(b) }
        _nodeBaseline.value = b
    }
    fun refreshAllFiltersFromNode() {
        if (!isLinkUp()) { notifyUser("Nicht verbunden."); return }
        if (!tryMarkBusy(BusyKey.FILTER_REFRESH)) return
        startRead(cancelPrevious = false) {
            _activeReadStatusText.value = "Filter-Status wird abgefragt…"
            val c = _deviceCapabilities.value
            runFilterListQueries(filterQueriesFor(c.supportsBlacklist, c.supportsWhitelist))
        }.invokeOnCompletion { clearBusy(BusyKey.FILTER_REFRESH) }
    }
    /** "filter RESET" (Großschreibung beabsichtigt); Listen werden erst nach Bestätigung geleert. */
    fun executeFilterReset() {
        sendBusy(BusyKey.FILTER_RESET, RepeaterProtocol.CMD_FILTER_RESET) { ok ->
            if (!ok) return@sendBusy notConfirmed(RepeaterProtocol.CMD_FILTER_RESET)
            _blockedChannels.value = emptyList(); _blockedPaths.value = emptyList(); _whitelistChannels.value = emptyList(); _whitelistPaths.value = emptyList()
            _senderRules.value = emptyList(); _textRules.value = emptyList(); _watchChannels.value = emptyList()
        }
    }
    // Hinzufügen/Löschen: Button arbeitet (Spinner), bis der Repeater bestätigt; erst dann wird die Liste geändert.
    // Rückgabe false = nicht gesendet (z. B. Doppelklick während der Übertragung).
    private fun notConfirmed(what: String) = notifyUser(tr("\"{0}\" vom Repeater nicht bestätigt.", what))

    fun addWhitelistChannel(name: String, isPreset: Boolean = false): Boolean {
        val n = name.trim(); if (n.isEmpty()) return false
        return sendBusy(BusyKey.WL_CHANNEL, RepeaterProtocol.buildWlChannelAdd(n)) { ok ->
            if (!ok) return@sendBusy notConfirmed(n)
            rememberChannelName(n)
            val h = FilterListParser.parseWhitelistChannelHashes(n).firstOrNull() ?: ChannelHash.hashHex(n)
            if (_whitelistChannels.value.none { it.hashHex.equals(h, true) }) _whitelistChannels.value += WhitelistChannel(n, hashHex = h, isPreset = isPreset)
        }
    }
    fun removeWhitelistChannel(ch: WhitelistChannel): Boolean {
        // per Hash löschen – funktioniert auch für Einträge, deren Name unbekannt ist ("0xD9")
        val arg = ch.hashHex.takeIf { it.length == 2 }?.lowercase() ?: ch.name
        return sendBusy(BusyKey.WL_CHANNEL, RepeaterProtocol.buildWlChannelDel(arg)) { ok ->
            if (ok) _whitelistChannels.value = _whitelistChannels.value.filterNot { it.name == ch.name && it.hashHex == ch.hashHex } else notConfirmed(ch.name)
        }
    }
    fun addWhitelistPath(prefix: String, comment: String = ""): Boolean {
        val p = prefix.trim().uppercase(); if (p.isEmpty()) return false
        return sendBusy(BusyKey.WL_PATH, RepeaterProtocol.buildWlPathAdd(p)) { ok ->
            if (!ok) return@sendBusy notConfirmed(p)
            if (_whitelistPaths.value.none { it.prefixHex == p }) _whitelistPaths.value += WhitelistPath(p, comment)
        }
    }
    fun removeWhitelistPath(p: WhitelistPath): Boolean = sendBusy(BusyKey.WL_PATH, RepeaterProtocol.buildWlPathDel(p.prefixHex)) { ok ->
        if (ok) _whitelistPaths.value = _whitelistPaths.value.filterNot { it.prefixHex == p.prefixHex } else notConfirmed(p.prefixHex)
    }
    fun addBlockedChannel(name: String): Boolean {
        val n = name.trim(); if (n.isEmpty()) return false
        return sendBusy(BusyKey.BL_CHANNEL, RepeaterProtocol.buildChannelAdd(n)) { ok ->
            if (!ok) return@sendBusy notConfirmed(n)
            rememberChannelName(n)
            if (_blockedChannels.value.none { it.name == n }) _blockedChannels.value += BlockedChannel(n)
        }
    }
    fun removeBlockedChannel(name: String): Boolean = sendBusy(BusyKey.BL_CHANNEL, RepeaterProtocol.buildChannelRemove(name)) { ok ->
        if (ok) _blockedChannels.value = _blockedChannels.value.filterNot { it.name == name } else notConfirmed(name)
    }
    fun addBlockedPath(prefix: String, comment: String = ""): Boolean {
        val p = prefix.trim().uppercase(); if (p.isEmpty()) return false
        return sendBusy(BusyKey.BL_PATH, RepeaterProtocol.buildPathAdd(p)) { ok ->
            if (!ok) return@sendBusy notConfirmed(p)
            if (_blockedPaths.value.none { it.prefixHex == p }) _blockedPaths.value += BlockedPath(p, comment)
        }
    }
    fun removeBlockedPath(prefix: String): Boolean = sendBusy(BusyKey.BL_PATH, RepeaterProtocol.buildPathRemove(prefix)) { ok ->
        if (ok) _blockedPaths.value = _blockedPaths.value.filterNot { it.prefixHex == prefix } else notConfirmed(prefix)
    }
    fun addSenderRule(pattern: String, secs: Int = 0, prob: Int = 100): Boolean = sendBusy(BusyKey.RULE, RepeaterProtocol.buildSenderRuleAdd(pattern, secs, prob)) { ok ->
        if (ok) _senderRules.value += FilterRule(pattern, secs, prob) else notConfirmed(pattern)
    }
    fun removeSenderRule(pattern: String): Boolean = sendBusy(BusyKey.RULE, RepeaterProtocol.buildSenderRuleRemove(pattern)) { ok ->
        if (ok) _senderRules.value = _senderRules.value.filterNot { it.pattern == pattern } else notConfirmed(pattern)
    }
    fun addTextRule(pattern: String, secs: Int = 0, prob: Int = 100): Boolean = sendBusy(BusyKey.RULE, RepeaterProtocol.buildTextRuleAdd(pattern, secs, prob)) { ok ->
        if (ok) _textRules.value += FilterRule(pattern, secs, prob) else notConfirmed(pattern)
    }
    fun removeTextRule(pattern: String): Boolean = sendBusy(BusyKey.RULE, RepeaterProtocol.buildTextRuleRemove(pattern)) { ok ->
        if (ok) _textRules.value = _textRules.value.filterNot { it.pattern == pattern } else notConfirmed(pattern)
    }
    fun addWatchChannel(name: String): Boolean = sendBusy(BusyKey.WATCH, RepeaterProtocol.buildWatchAdd(name)) { ok ->
        if (ok) _watchChannels.value += WatchChannel(name) else notConfirmed(name)
    }
    fun removeWatchChannel(name: String): Boolean = sendBusy(BusyKey.WATCH, RepeaterProtocol.buildWatchRemove(name)) { ok ->
        if (ok) _watchChannels.value = _watchChannels.value.filterNot { it.name == name } else notConfirmed(name)
    }
    override fun onCleared() {
        connectProbeJob?.cancel(); loginTimeoutJob?.cancel(); meshCareJob?.cancel(); stopLinkWatch()
        // destroy(): trennt UND meldet den USB-BroadcastReceiver ab bzw. stoppt BLE-Scan/Schreib-Queue (kein Leak)
        try { serialManager.destroy() } catch (_: Exception) {}
        try { bleManager.destroy() } catch (_: Exception) {}
        super.onCleared()
    }

    private companion object {
        /** Geräte-/Rollenerkennung (Teil 1 des Start-Scans). */
        val INITIAL_DETECTION_QUERIES = listOf("board", "ver", "filter", "filter help", "get gps")
        /** Start-Scan in Phasen (Reihenfolge wie im Status-Fenster angezeigt). */
        // DDMC-Filter (ZephCore/MeshCore Filter.cpp): Sperrlisten = "filter channel|path list", Freigaben = "filter wl channel|path list"
        val BL_STATUS_QUERIES = listOf("filter")
        val BL_LIST_QUERIES = listOf("filter channel list", "filter path list")
        val WL_STATUS_QUERIES = listOf("filter mode")
        val WL_LIST_QUERIES = listOf("filter wl channel list", "filter wl path list")
        val HARDWARE_QUERIES = listOf("board", "ver", "get gps", "power")
        /** Reihenfolge der Basis-Abfragen – jeweils genau ein Befehl, dann Antwort abwarten. */
        val BASIC_SETUP_QUERIES = listOf(
            "get radio", "get tx", "get dutycycle",
            "get public.key", "get name", "get owner.info", "get lat", "get lon",
            "get repeat", "get path.hash.mode", "get loop.detect",
            "get advert.interval", "get flood.advert.interval",
            "get txdelay", "get direct.txdelay", "get rxdelay", "get multi.acks", "get leds"
        )
        /** Start-Scan-Schritte für "region", "region home", "region default". */
        const val REGION_STEPS = 3
        const val REGION_TRAILING_USB_MS = 800L      // USB: Baumzeilen kommen einzeln nach der "->"-Zeile
        const val REGION_TRAILING_LORA_MS = 2_000L
        /** Nachlauf nach Regionen-Schreibbefehlen, bevor "region" neu gelesen wird (verspätete Antworten abfließen lassen). */
        const val REGION_SETTLE_LORA_MS = 1_500L
        const val REGION_SETTLE_USB_MS = 250L
        const val FILTER_LIST_EXTRA_LORA_MS = 3_500L   // Listen über LoRa: 4,5 s + 3,5 s = 8 s Antwortfenster
        const val FILTER_LIST_TRAILING_LORA_MS = 2_000L // Nachlauf für evtl. Folgezeilen über LoRa
        const val PREF_KNOWN_CHANNELS = "known_channel_names"
        val PRESET_CHANNEL_NAMES = listOf("Public", "#mesh-de", "#notfunk", "#test", "#spam-bot", "#crypto-ad", "#test-flood")
        const val LINK_WATCH_TICK_MS = 5_000L
        const val USB_PROBE_TIMEOUT_MS = 1_200L      // Antwortfenster für das "ver" der USB-Erkennung
        const val COMPANION_IDLE_PING_MS = 20_000L   // nach 20 s Funkstille: Keep-Alive an den Companion
        const val COMPANION_PING_RETRY_MS = 8_000L
        const val COMPANION_DEAD_MS = 40_000L        // 40 s ohne jedes Frame (trotz Pings) -> getrennt
        const val REMOTE_IDLE_PING_MS = 60_000L      // nach 60 s ohne Remote-Antwort: "ver"-Ping über LoRa
        const val REMOTE_REPLY_TIMEOUT_MS = 15_000L
        const val REMOTE_MAX_MISSES = 3
        const val LOGIN_DEFAULT_TIMEOUT_MS = 8_000L   // bis RESP_CODE_SENT den geschätzten Timeout liefert
        const val LOGIN_TIMEOUT_BUFFER_MS = 4_000L    // Puffer auf est_timeout des Companions
        const val LOGIN_MAX_TIMEOUT_MS = 60_000L
    }
}
