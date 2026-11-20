package com.meshcore.repeater.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meshcore.repeater.model.*
import com.meshcore.repeater.service.BleConnectionManager
import com.meshcore.repeater.service.RepeaterProtocol
import com.meshcore.repeater.service.SerialConnectionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

data class TerminalEntry(val text: String, val isOutgoing: Boolean)

class RepeaterViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("repeater_cfg_prefs", Context.MODE_PRIVATE)

    private val _selectedConnectionType = MutableStateFlow(ConnectionType.USB_SERIAL)
    val selectedConnectionType: StateFlow<ConnectionType> = _selectedConnectionType

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _usbDevices = MutableStateFlow<List<UsbSerialDeviceItem>>(emptyList())
    val usbDevices: StateFlow<List<UsbSerialDeviceItem>> = _usbDevices

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

    private val serialManager = SerialConnectionManager(
        context = application,
        scope = viewModelScope,
        onDataReceived = { line -> logTerminal(line, false); parseIncomingLine(line) },
        onError = { err -> notifyUser(err) },
        onConnectionChanged = { connected, name ->
            if (connected) {
                _connectionState.value = ConnectionState.Connected(name ?: "USB-Node", ConnectionType.USB_SERIAL, "115200 Baud")
                startCapabilityDetection()
            } else {
                _connectionState.value = ConnectionState.Disconnected; stopMeshCare()
            }
        }
    )

    private val bleManager = BleConnectionManager(
        context = application,
        scope = viewModelScope,
        onDataReceived = { line -> logTerminal(line, false); parseIncomingLine(line) },
        onFrameReceived = { data -> handleIncomingFrame(data) },
        onError = { err -> notifyUser(err) },
        onConnectionChanged = { connected, name ->
            if (connected) {
                _connectionState.value = ConnectionState.Connected(name ?: "BLE-Node", ConnectionType.BLE_COMPANION, "MeshCore NUS")
                viewModelScope.launch {
                    delay(250L); bleManager.sendBytes(RepeaterProtocol.buildAppStartFrame())
                    delay(450L); refreshMeshContacts()
                }
            } else {
                _connectionState.value = ConnectionState.Disconnected; _remoteAdminSession.value = RemoteAdminSession(); _meshContacts.value = emptyList(); stopMeshCare()
            }
        }
    )

    val bleDevices: StateFlow<List<BleDeviceItem>> = bleManager.scannedDevices
    val isScanningBle: StateFlow<Boolean> = bleManager.isScanning

    init { refreshUsbDevices() }
