package com.meshcore.repeater.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meshcore.repeater.model.*
import com.meshcore.repeater.service.BleConnectionManager
import com.meshcore.repeater.service.RepeaterProtocol
import com.meshcore.repeater.service.SerialConnectionManager
import com.meshcore.repeater.ui.theme.AppLanguage
import com.meshcore.repeater.ui.theme.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

data class TerminalEntry(val text: String, val isOutgoing: Boolean)

class RepeaterViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("repeater_cfg_prefs", Context.MODE_PRIVATE)
    private val serialManager = SerialConnectionManager(application)
    private val bleManager = BleConnectionManager(application)

    private val _selectedConnectionType = MutableStateFlow(ConnectionType.USB_SERIAL)
    val selectedConnectionType: StateFlow<ConnectionType> = _selectedConnectionType

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _usbDevices = MutableStateFlow<List<UsbDeviceItem>>(emptyList())
    val usbDevices: StateFlow<List<UsbDeviceItem>> = _usbDevices

    val bleDevices: StateFlow<List<BleDeviceItem>> = bleManager.scannedDevices
    val isScanningBle: StateFlow<Boolean> = bleManager.isScanning

    private val _meshContacts = MutableStateFlow<List<MeshContact>>(emptyList())
    val meshContacts: StateFlow<List<MeshContact>> = _meshContacts

    private val _isRefreshingContacts = MutableStateFlow(false)
    val isRefreshingContacts: StateFlow<Boolean> = _isRefreshingContacts

    private val _remoteAdminSession = MutableStateFlow(RemoteAdminSession())
    val remoteAdminSession: StateFlow<RemoteAdminSession> = _remoteAdminSession

    private val _terminalLogs = MutableStateFlow<List<TerminalEntry>>(emptyList())
    val terminalLogs: StateFlow<List<TerminalEntry>> = _terminalLogs

    private val _uiEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val uiEvents: SharedFlow<String> = _uiEvents

    private val _disclaimerAccepted = MutableStateFlow(prefs.getBoolean("disclaimer_accepted", false))
    val disclaimerAccepted: StateFlow<Boolean> = _disclaimerAccepted

    private val _offlinePreviewEnabled = MutableStateFlow(prefs.getBoolean("offline_preview", false))
    val offlinePreviewEnabled: StateFlow<Boolean> = _offlinePreviewEnabled

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

    private val _ledMode = MutableStateFlow(LedMode.NORMAL)
    val ledMode: StateFlow<LedMode> = _ledMode

    private val _gpsMode = MutableStateFlow(GpsMode.OFF)
    val gpsMode: StateFlow<GpsMode> = _gpsMode

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

    init {
        serialManager.onDataReceived = { line ->
            logTerminal(line, false)
            parseIncomingLine(line)
        }
        serialManager.onError = { err -> notifyUser(err) }
        serialManager.onConnectionChanged = { connected, name ->
            if (connected) {
                _connectionState.value = ConnectionState.Connected(name ?: "USB-Node", ConnectionType.USB_SERIAL, "115200 Baud")
                startCapabilityDetection()
            } else {
                _connectionState.value = ConnectionState.Disconnected
                stopMeshCare()
            }
        }
        bleManager.onFrameReceived = { data -> handleIncomingFrame(data) }
        bleManager.onError = { err -> notifyUser(err) }
        bleManager.onConnectionChanged = { connected, name ->
            if (connected) {
                _connectionState.value = ConnectionState.Connected(name ?: "BLE-Node", ConnectionType.BLE_COMPANION, "MeshCore NUS")
                viewModelScope.launch {
                    delay(250L)
                    bleManager.sendBytes(RepeaterProtocol.buildAppStartFrame())
                    delay(450L)
                    refreshMeshContacts()
                }
            } else {
                _connectionState.value = ConnectionState.Disconnected
                _remoteAdminSession.value = RemoteAdminSession()
                _meshContacts.value = emptyList()
                stopMeshCare()
            }
        }
        refreshUsbDevices()
    }

    private fun logTerminal(text: String, isOutgoing: Boolean) {
        _terminalLogs.value = (_terminalLogs.value + TerminalEntry(text, isOutgoing)).takeLast(400)
    }
    private fun notifyUser(msg: String) { _uiEvents.tryEmit(msg) }

    fun acceptDisclaimer() { prefs.edit().putBoolean("disclaimer_accepted", true).apply(); _disclaimerAccepted.value = true }
    fun setOfflinePreview(enabled: Boolean) { prefs.edit().putBoolean("offline_preview", enabled).apply(); _offlinePreviewEnabled.value = enabled }
    fun setThemeMode(mode: ThemeMode) { prefs.edit().putString("theme_mode", mode.name).apply(); _themeMode.value = mode }
    fun setAppLanguage(lang: AppLanguage) { prefs.edit().putString("app_lang", lang.name).apply(); _appLanguage.value = lang }

    fun selectConnectionType(type: ConnectionType) {
        _selectedConnectionType.value = type
        if (type == ConnectionType.USB_SERIAL) refreshUsbDevices() else startBleScan()
    }
    fun refreshUsbDevices() { _usbDevices.value = serialManager.listDevices() }
    fun connectUsb(deviceId: Int, baudRate: Int = 115200) {
        _connectionState.value = ConnectionState.Connecting("Verbinde USB...")
        serialManager.connect(deviceId, baudRate)
    }
    fun startBleScan() { bleManager.startScan() }
    fun stopBleScan() { bleManager.stopScan() }
    fun connectBle(address: String) {
        _connectionState.value = ConnectionState.Connecting("Verbinde BLE...")
        bleManager.connect(address)
    }
    fun disconnect() { serialManager.disconnect(); bleManager.disconnect(); _connectionState.value = ConnectionState.Disconnected }

    fun refreshMeshContacts() { _isRefreshingContacts.value = true; bleManager.sendBytes(RepeaterProtocol.buildGetContactsFrame()) }
    fun selectTargetRepeater(contact: MeshContact) {
        val saved = prefs.getString("pwd_${contact.pubKeyHex}", "") ?: ""
        _remoteAdminSession.value = RemoteAdminSession(targetRepeater = contact, savedPassword = saved)
    }
    fun setRemoteAdminPassword(password: String) {
        val t = _remoteAdminSession.value.targetRepeater ?: return
        prefs.edit().putString("pwd_${t.pubKeyHex}", password).apply()
        _remoteAdminSession.value = _remoteAdminSession.value.copy(savedPassword = password)
    }
    fun startRemoteLogin() {
        val s = _remoteAdminSession.value; val t = s.targetRepeater ?: return
        bleManager.sendBytes(RepeaterProtocol.buildLoginFrame(t.pubKey, s.savedPassword))
        _remoteAdminSession.value = s.copy(isLoggingIn = true, sessionStatus = "Login gesendet...")
        viewModelScope.launch {
            delay(5000L)
            if (_remoteAdminSession.value.isLoggingIn) {
                _remoteAdminSession.value = _remoteAdminSession.value.copy(isLoggingIn = false, sessionStatus = "Keine Antwort (Timeout)")
            }
        }
    }

    fun sendCommand(cmd: String) {
        if (cmd.isBlank()) return
        if (_selectedConnectionType.value == ConnectionType.USB_SERIAL) {
            logTerminal("> $cmd", true); serialManager.send(cmd)
        } else {
            val t = _remoteAdminSession.value.targetRepeater
            if (t != null) {
                val p6 = t.pubKey.copyOfRange(0, minOf(6, t.pubKey.size))
                logTerminal("> [OTA -> ${t.name}] $cmd", true)
                bleManager.sendBytes(RepeaterProtocol.buildRemoteCliFrame(p6, cmd))
            } else {
                logTerminal("> [Companion] $cmd", true)
                bleManager.sendBytes(RepeaterProtocol.buildLocalCliFrame(cmd))
            }
        }
    }

    private fun startCapabilityDetection() {
        viewModelScope.launch {
            val d = if (_selectedConnectionType.value == ConnectionType.BLE_COMPANION) 1100L else 280L
            for (c in listOf(RepeaterProtocol.CMD_VER, RepeaterProtocol.CMD_BOARD, RepeaterProtocol.CMD_FILTER_STATUS, "filter help", RepeaterProtocol.CMD_GET_RADIO, RepeaterProtocol.CMD_GET_TX, RepeaterProtocol.CMD_GET_LEDS, RepeaterProtocol.CMD_GET_GPS)) {
                sendCommand(c); delay(d)
            }
            _deviceCapabilities.value = _deviceCapabilities.value.copy(detectionComplete = true)
        }
    }

    private fun handleIncomingFrame(data: ByteArray) {
        if (data.isEmpty()) return
        when (data) {
            RepeaterProtocol.PACKET_SELF_INFO -> refreshMeshContacts()
            RepeaterProtocol.PACKET_CONTACT_START -> { _isRefreshingContacts.value = true; _meshContacts.value = emptyList() }
            RepeaterProtocol.PACKET_CONTACT -> RepeaterProtocol.parseContactFrame(data)?.let { ct ->
                val list = _meshContacts.value.toMutableList()
                val idx = list.indexOfFirst { it.pubKeyHex == ct.pubKeyHex }
                if (idx >= 0) list[idx] = ct else list.add(ct)
                _meshContacts.value = list.sortedWith(compareByDescending<MeshContact> { it.isRepeater }.thenBy { it.name })
            }
            RepeaterProtocol.PACKET_CONTACT_END -> _isRefreshingContacts.value = false
            RepeaterProtocol.PACKET_CONTACT_MSG_V3 -> RepeaterProtocol.parseContactMsgV3(data)?.let { (p, txt, snr) ->
                logTerminal("< [OTA ${RepeaterProtocol.bytesToHex(p, 4)} | SNR ${"%.1f".format(snr)}] $txt", false)
                onRemoteReply(txt)
            }
            0x07.toByte() -> if (data.size > 11) {
                val msg = String(data, 11, data.size - 11, StandardCharsets.UTF_8).trimEnd('\u0000', '\r', '\n')
                logTerminal("< [OTA] $msg", false); onRemoteReply(msg)
            }
            0x12.toByte() -> {
                _remoteAdminSession.value = _remoteAdminSession.value.copy(isLoggedIn = true, isLoggingIn = false, sessionStatus = "Admin aktiv (0x12)")
                logTerminal("< [Mesh] Admin-Login bestätigt!", false); startCapabilityDetection()
            }
            RepeaterProtocol.PACKET_CLI_REPLY -> {
                val r = String(data, 1, data.size - 1, StandardCharsets.UTF_8).trimEnd('\u0000', '\r', '\n')
                logTerminal("< [Companion] $r", false); parseIncomingLine(r)
            }
            RepeaterProtocol.PACKET_ERROR -> {
                val code = if (data.size > 1) data.toInt() and 0xFF else -1
                logTerminal("< [Fehler 0x01] Code=$code", false)
                if (_remoteAdminSession.value.isLoggingIn) _remoteAdminSession.value = _remoteAdminSession.value.copy(isLoggingIn = false, sessionStatus = "Fehler ($code)")
            }
        }
    }

    private fun onRemoteReply(txt: String) {
        if (_remoteAdminSession.value.isLoggingIn) {
            _remoteAdminSession.value = _remoteAdminSession.value.copy(isLoggedIn = true, isLoggingIn = false, sessionStatus = "Admin aktiv")
            startCapabilityDetection()
        }
        parseIncomingLine(txt)
    }

    private fun parseIncomingLine(line: String) {
        RepeaterProtocol.parseVersion(line)?.let { ver ->
            _hardwareInfo.value = _hardwareInfo.value.copy(firmwareVersion = ver)
            _deviceCapabilities.value = _deviceCapabilities.value.copy(firmwareVersion = ver)
        }
        _filterStats.value = RepeaterProtocol.parseFilterStats(line, _filterStats.value)
        val lo = line.lowercase()
        if (lo.contains("filter") || lo.contains("[list|add|del]") || lo.contains("wl")) {
            _deviceCapabilities.value = _deviceCapabilities.value.copy(
                hasFilterBlacklist = lo.contains("channel") || lo.contains("sender") || lo.contains("filter"),
                hasFilterWhitelistMode = lo.contains(" wl") || lo.contains("[wl"),
                hasFilterModeSwitch = lo.contains("mode")
            )
        }
        if (lo.contains("unknown command") || lo.contains("unknown cmd")) {
            _deviceCapabilities.value = _deviceCapabilities.value.copy(hasFilterWhitelistMode = false, hasFilterModeSwitch = false)
        }
    }

    fun setRegion(region: CountryRegion) {
        _radioConfig.value = _radioConfig.value.copy(region = region, frequencyMhz = region.frequencyMhz, bandwidthKhz = region.bandwidthKhz, spreadingFactor = region.defaultSf, codingRate = region.defaultCr)
    }
    fun setCustomRadioParams(freq: Double, bw: Double, sf: Int, cr: String) {
        _radioConfig.value = _radioConfig.value.copy(frequencyMhz = freq, bandwidthKhz = bw, spreadingFactor = sf.coerceIn(5, 12), codingRate = cr)
    }
    fun setAntennaPreset(preset: AntennaPreset) { _radioConfig.value = _radioConfig.value.copy(antennaPreset = preset) }
    fun setCustomAntennaGain(gain: Double) { _radioConfig.value = _radioConfig.value.copy(customAntennaGainDbi = gain) }
    fun setCableLoss(loss: Double) { _radioConfig.value = _radioConfig.value.copy(cableLossDb = loss) }
    fun setTxPower(power: Double) { _radioConfig.value = _radioConfig.value.copy(txPowerDbm = power) }
    fun setComplianceUnlocked(unlocked: Boolean) { _radioConfig.value = _radioConfig.value.copy(complianceUnlocked = unlocked) }
    fun applyRadioSettings(testMode: Boolean = false, testTimeoutMins: Int = 5) {
        val rc = _radioConfig.value; val crInt = rc.codingRate.substringAfter('/').toIntOrNull() ?: 5
        sendCommand(if (testMode) RepeaterProtocol.buildTempRadio(rc.frequencyMhz, rc.bandwidthKhz, rc.spreadingFactor, crInt, testTimeoutMins) else RepeaterProtocol.buildSetRadio(rc.frequencyMhz, rc.bandwidthKhz, rc.spreadingFactor, crInt))
        sendCommand(RepeaterProtocol.buildSetTx(rc.txPowerDbm.toInt()))
        notifyUser(if (testMode) "Temporärer Funk-Test aktiv ($testTimeoutMins Min.)" else "Funkwerte dauerhaft im Flash gespeichert!")
    }

    fun startMeshCare(minutes: Int = 15) {
        meshCareJob?.cancel(); val total = minutes * 60
        _meshCareState.value = MeshCareState(isActive = true, remainingSeconds = total, totalSeconds = total)
        meshCareJob = viewModelScope.launch {
            while (_meshCareState.value.remainingSeconds > 0 && _meshCareState.value.isActive) { delay(1000L); _meshCareState.value = _meshCareState.value.copy(remainingSeconds = _meshCareState.value.remainingSeconds - 1) }
            if (_meshCareState.value.isActive) stopMeshCare()
        }
    }
    fun extendMeshCare(addMinutes: Int = 10) { val a = addMinutes * 60; _meshCareState.value = _meshCareState.value.copy(remainingSeconds = _meshCareState.value.remainingSeconds + a, totalSeconds = _meshCareState.value.totalSeconds + a) }
    fun stopMeshCare() { meshCareJob?.cancel(); _meshCareState.value = MeshCareState() }

    fun setLedMode(mode: LedMode) { _ledMode.value = mode; sendCommand(RepeaterProtocol.buildSetLed(mode)) }
    fun setGpsMode(mode: GpsMode) { _gpsMode.value = mode; sendCommand(RepeaterProtocol.buildSetGps(mode)) }
    fun requestTelemetry() { sendCommand(RepeaterProtocol.CMD_POWER); sendCommand(RepeaterProtocol.CMD_CLOCK); sendCommand(RepeaterProtocol.CMD_VER) }
    fun rebootNode() { sendCommand(RepeaterProtocol.CMD_REBOOT) }
    fun saveConfigToFlash() { notifyUser("Alle Parameter werden sofort im Flash gespeichert.") }

    fun setFilterEnabled(enabled: Boolean) { _filterStats.value = _filterStats.value.copy(filterEnabled = enabled); sendCommand(if (enabled) RepeaterProtocol.CMD_FILTER_ON else RepeaterProtocol.CMD_FILTER_OFF) }
    fun setFilterDryRun(dryRun: Boolean) { _filterStats.value = _filterStats.value.copy(dryRun = dryRun); sendCommand(if (dryRun) RepeaterProtocol.CMD_FILTER_DRYRUN_ON else RepeaterProtocol.CMD_FILTER_DRYRUN_OFF) }
    fun setFilterMode(mode: FilterMode) { _filterStats.value = _filterStats.value.copy(mode = mode); sendCommand(if (mode == FilterMode.WHITELIST) RepeaterProtocol.CMD_FILTER_MODE_WHITELIST else RepeaterProtocol.CMD_FILTER_MODE_BLACKLIST) }
    fun setFilterMaxHops(hops: Int) { _filterStats.value = _filterStats.value.copy(maxHops = hops); sendCommand(RepeaterProtocol.buildFilterHops(0, hops)) }
    fun setFilterAge(mins: Int) { _filterStats.value = _filterStats.value.copy(maxAgeMinutes = mins); sendCommand(RepeaterProtocol.buildFilterAge(mins)) }
    fun executeFilterReset() { sendCommand(RepeaterProtocol.CMD_FILTER_RESET); _blockedChannels.value = emptyList(); _blockedPaths.value = emptyList(); _whitelistChannels.value = emptyList(); _whitelistPaths.value = emptyList() }

    fun addWhitelistChannel(name: String, isPreset: Boolean = false) { _whitelistChannels.value = _whitelistChannels.value + WhitelistChannel(name, isPreset = isPreset); sendCommand(RepeaterProtocol.buildWlChannelAdd(name)) }
    fun removeWhitelistChannel(name: String) { _whitelistChannels.value = _whitelistChannels.value.filterNot { it.name == name }; sendCommand(RepeaterProtocol.buildWlChannelDel(name)) }
    fun addWhitelistPath(prefix: String, comment: String = "") { _whitelistPaths.value = _whitelistPaths.value + WhitelistPath(prefix, comment); sendCommand(RepeaterProtocol.buildWlPathAdd(prefix)) }
    fun removeWhitelistPath(prefix: String) { _whitelistPaths.value = _whitelistPaths.value.filterNot { it.prefixHex == prefix }; sendCommand(RepeaterProtocol.buildWlPathDel(prefix)) }

    fun addBlockedChannel(name: String) { _blockedChannels.value = _blockedChannels.value + BlockedChannel(name); sendCommand(RepeaterProtocol.buildChannelAdd(name)) }
    fun removeBlockedChannel(name: String) { _blockedChannels.value = _blockedChannels.value.filterNot { it.name == name }; sendCommand(RepeaterProtocol.buildChannelRemove(name)) }
    fun addBlockedPath(prefix: String, comment: String = "") { _blockedPaths.value = _blockedPaths.value + BlockedPath(prefix, comment); sendCommand(RepeaterProtocol.buildPathAdd(prefix)) }
    fun removeBlockedPath(prefix: String) { _blockedPaths.value = _blockedPaths.value.filterNot { it.prefixHex == prefix }; sendCommand(RepeaterProtocol.buildPathRemove(prefix)) }

    fun addSenderRule(pattern: String, secs: Int = 0, prob: Int = 100) { _senderRules.value = _senderRules.value + FilterRule(pattern, secs, prob); sendCommand(RepeaterProtocol.buildSenderRuleAdd(pattern, secs, prob)) }
    fun removeSenderRule(pattern: String) { _senderRules.value = _senderRules.value.filterNot { it.pattern == pattern }; sendCommand(RepeaterProtocol.buildSenderRuleRemove(pattern)) }
    fun addTextRule(pattern: String, secs: Int = 0, prob: Int = 100) { _textRules.value = _textRules.value + FilterRule(pattern, secs, prob); sendCommand(RepeaterProtocol.buildTextRuleAdd(pattern, secs, prob)) }
    fun removeTextRule(pattern: String) { _textRules.value = _textRules.value.filterNot { it.pattern == pattern }; sendCommand(RepeaterProtocol.buildTextRuleRemove(pattern)) }

    fun addWatchChannel(name: String) { _watchChannels.value = _watchChannels.value + WatchChannel(name); sendCommand(RepeaterProtocol.buildWatchAdd(name)) }
    fun removeWatchChannel(name: String) { _watchChannels.value = _watchChannels.value.filterNot { it.name == name }; sendCommand(RepeaterProtocol.buildWatchRemove(name)) }

    fun buildBackupScript(): String {
        val rc = _radioConfig.value; val fs = _filterStats.value
        val lines = mutableListOf("# RepeaterConfigurator Backup", RepeaterProtocol.buildSetRadio(rc.frequencyMhz, rc.bandwidthKhz, rc.spreadingFactor, rc.codingRate.substringAfter('/').toIntOrNull() ?: 5), RepeaterProtocol.buildSetTx(rc.txPowerDbm.toInt()), if (fs.filterEnabled) RepeaterProtocol.CMD_FILTER_ON else RepeaterProtocol.CMD_FILTER_OFF, if (fs.mode == FilterMode.WHITELIST) RepeaterProtocol.CMD_FILTER_MODE_WHITELIST else RepeaterProtocol.CMD_FILTER_MODE_BLACKLIST)
        _blockedChannels.value.forEach { lines.add(RepeaterProtocol.buildChannelAdd(it.name)) }
        _blockedPaths.value.forEach { lines.add(RepeaterProtocol.buildPathAdd(it.prefixHex)) }
        _whitelistChannels.value.forEach { lines.add(RepeaterProtocol.buildWlChannelAdd(it.name)) }
        _whitelistPaths.value.forEach { lines.add(RepeaterProtocol.buildWlPathAdd(it.prefixHex)) }
        return lines.joinToString("\n")
    }
    fun restoreBackupScript(script: String) {
        val cmds = script.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        viewModelScope.launch { for (c in cmds) { sendCommand(c); delay(400L) }; notifyUser("Backup-Profil (${cmds.size} Befehle) eingespielt!") }
    }
    override fun onCleared() { super.onCleared(); serialManager.disconnect(); bleManager.disconnect() }
}
