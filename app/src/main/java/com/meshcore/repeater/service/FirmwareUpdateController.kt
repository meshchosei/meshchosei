package com.meshcore.repeater.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import com.meshcore.repeater.model.DfuTarget
import com.meshcore.repeater.model.DfuScanRules
import com.meshcore.repeater.model.FirmwareFileRules
import com.meshcore.repeater.model.FirmwarePackage
import com.meshcore.repeater.model.OtaEvent
import com.meshcore.repeater.model.OtaState
import com.meshcore.repeater.model.OtaStateMachine
import com.meshcore.repeater.service.dfu.DfuService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.nordicsemi.android.dfu.DfuProgressListenerAdapter
import no.nordicsemi.android.dfu.DfuServiceController
import no.nordicsemi.android.dfu.DfuServiceInitiator
import no.nordicsemi.android.dfu.DfuServiceListenerHelper
import java.io.File

/**
 * Android-Seite des Firmware-Updaters (v1.26, STYLE_GUIDE §2.16): Paket importieren (Kopie im App-Cache, damit der
 * DFU-Dienst unabhängig von der Dokument-Freigabe lesen kann), BLE-Scan nach DFU-Geräten, Nordic DFU starten/abbrechen
 * und die Fortschrittsereignisse der Bibliothek in [OtaStateMachine] übersetzen.
 *
 * Feste, bewährte DFU-Einstellungen (keine Expertenoptionen in der UI): Packet Receipt Notifications an (alle 12
 * Pakete), `keepBond = true`, `forceDfu = false`, Resume aktiv (Bibliotheksstandard), MTU 247, Vordergrund-Dienst.
 */
class FirmwareUpdateController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit
) {
    private val _state = MutableStateFlow(OtaState())
    val state: StateFlow<OtaState> = _state.asStateFlow()

    private val _devices = MutableStateFlow<List<DfuTarget>>(emptyList())
    val devices: StateFlow<List<DfuTarget>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var dfuController: DfuServiceController? = null
    private var scanStopJob: Job? = null

    private fun dispatch(e: OtaEvent) { _state.update { OtaStateMachine.reduce(it, e) } }

    // ---------------- Nordic-Fortschritt -> Zustandsmaschine ----------------
    private val progressListener = object : DfuProgressListenerAdapter() {
        override fun onDeviceConnecting(deviceAddress: String) { dispatch(OtaEvent.EnablingBootloader) }
        override fun onEnablingDfuMode(deviceAddress: String) { log("[DFU] Bootloader wird aktiviert…"); dispatch(OtaEvent.EnablingBootloader) }
        override fun onDfuProcessStarting(deviceAddress: String) { log("[DFU] Initialisierung…"); dispatch(OtaEvent.DfuInit) }
        override fun onDfuProcessStarted(deviceAddress: String) { dispatch(OtaEvent.DfuInit) }
        override fun onFirmwareValidating(deviceAddress: String) { dispatch(OtaEvent.DfuInit) }
        override fun onProgressChanged(deviceAddress: String, percent: Int, speed: Float, avgSpeed: Float, currentPart: Int, partsTotal: Int) {
            dispatch(OtaEvent.Progress(percent, speed, avgSpeed, currentPart, partsTotal))
        }
        override fun onDfuCompleted(deviceAddress: String) { log("[DFU] Fertig – das Gerät startet neu."); dispatch(OtaEvent.Completed); dfuController = null }
        override fun onDfuAborted(deviceAddress: String) { log("[DFU] Abgebrochen."); dispatch(OtaEvent.Aborted); dfuController = null }
        override fun onError(deviceAddress: String, error: Int, errorType: Int, message: String?) {
            val m = "${message ?: "DFU error"} (#$error)"
            log("[DFU] Fehler: $m"); dispatch(OtaEvent.Failed(m)); dfuController = null
        }
    }

    init {
        DfuServiceListenerHelper.registerProgressListener(context, progressListener)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) runCatching { DfuServiceInitiator.createDfuNotificationChannel(context) }
    }

    // ---------------- Firmware-Paket ----------------
    /** Kopiert das gewählte Paket in den App-Cache und prüft es. Ergebnis: Prüfstatus (Ok -> im Zustand gesetzt). */
    suspend fun importFirmware(uri: Uri): FirmwareFileRules.Check = withContext(Dispatchers.IO) {
        if (_state.value.phase.isRunning) return@withContext FirmwareFileRules.Check.Corrupt
        val (name, size) = queryNameAndSize(uri)
        // Größe unbekannt (Anbieter liefert keine): erst nach dem Kopieren prüfen
        val meta = FirmwareFileRules.checkMeta(name, size ?: 1L)
        if (meta !is FirmwareFileRules.Check.Ok) return@withContext meta
        val dir = File(context.cacheDir, "dfu").apply { mkdirs() }
        val dest = File(dir, "firmware.zip")
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } } ?: -1L
        }.getOrDefault(-1L)
        if (copied <= 0L) return@withContext FirmwareFileRules.Check.Corrupt
        val sizeCheck = FirmwareFileRules.checkMeta(name, copied)
        if (sizeCheck !is FirmwareFileRules.Check.Ok) { dest.delete(); return@withContext sizeCheck }
        val content = dest.inputStream().use { FirmwareFileRules.checkZip(it) }
        if (content !is FirmwareFileRules.Check.Ok) { dest.delete(); return@withContext content }
        dispatch(OtaEvent.FirmwareChosen(FirmwarePackage(name, copied, dest.absolutePath)))
        content
    }

    fun clearFirmware() { dispatch(OtaEvent.FirmwareCleared) }

    private fun queryNameAndSize(uri: Uri): Pair<String, Long?> {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "firmware.zip"
        var size: Long? = null
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                    val si = c.getColumnIndex(OpenableColumns.SIZE); if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        }
        return name to size
    }

    // ---------------- Zielgerät ----------------
    fun chooseTarget(t: DfuTarget) { dispatch(OtaEvent.TargetChosen(t)) }
    fun clearTarget() { dispatch(OtaEvent.TargetCleared) }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val rec = result.scanRecord
            val name = rec?.deviceName ?: runCatching { result.device.name }.getOrNull()
            val uuids = rec?.serviceUuids?.map { it.uuid.toString() }.orEmpty()
            val kind = DfuScanRules.classify(name, uuids) ?: return
            val t = DfuTarget(result.device.address, name ?: result.device.address, result.rssi, kind, System.currentTimeMillis())
            _devices.update { DfuScanRules.merge(it, t) }
        }
        override fun onScanFailed(errorCode: Int) { _isScanning.value = false; log("[DFU] BLE-Scan fehlgeschlagen (Code $errorCode)") }
    }

    /** Sucht [durationMs] lang nach Geräten im DFU-/OTA-Modus (Liste bleibt danach sichtbar). */
    @SuppressLint("MissingPermission")
    fun startScan(durationMs: Long = 20_000L, clear: Boolean = true): Boolean {
        val scanner = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
            ?: run { log("[DFU] Bluetooth ist aus oder nicht verfügbar."); return false }
        stopScan()
        if (clear) _devices.value = emptyList()
        val ok = runCatching {
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
        }.onFailure { Log.w(TAG, "startScan", it) }.isSuccess
        _isScanning.value = ok
        if (ok) scanStopJob = scope.launch { delay(durationMs); stopScan() }
        return ok
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanStopJob?.cancel(); scanStopJob = null
        if (!_isScanning.value) return
        runCatching { (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        _isScanning.value = false
    }

    // ---------------- DFU starten / abbrechen ----------------
    /** Startet den Nordic-DFU-Dienst. Bestehende GATT-Verbindungen müssen vorher getrennt sein (ViewModel). */
    fun start(): Boolean {
        val s = _state.value
        val fw = s.firmware ?: return false
        val t = s.target ?: return false
        if (!s.canStart) return false
        stopScan()
        if (!File(fw.localPath).exists()) { dispatch(OtaEvent.FirmwareCleared); log("[DFU] Firmware-Datei nicht mehr vorhanden."); return false }
        dispatch(OtaEvent.Start)
        return runCatching {
            val starter = DfuServiceInitiator(t.address)
                .setDeviceName(t.name)
                .setKeepBond(true)
                .setForceDfu(false)
                .setPacketsReceiptNotificationsEnabled(true)
                .setPacketsReceiptNotificationsValue(PRN_PACKETS)
                .setMtu(DFU_MTU)
                .setForeground(true)
                .setZip(fw.localPath)
            log("[DFU] Start: ${fw.displayName} -> ${t.name} (${t.address})")
            dfuController = starter.start(context, DfuService::class.java)
        }.onFailure { dispatch(OtaEvent.Failed(it.message ?: it.javaClass.simpleName)) }.isSuccess
    }

    fun abort() { runCatching { dfuController?.abort() } }

    /** Alles leeren (Paket, Ziel, Fortschritt, Status, Trefferliste) und die Cache-Kopie löschen – nicht während eines Laufs. */
    fun reset() {
        if (_state.value.phase.isRunning) return
        stopScan()
        dispatch(OtaEvent.Reset)
        _devices.value = emptyList()
        runCatching { File(File(context.cacheDir, "dfu"), "firmware.zip").delete() }
    }

    fun destroy() {
        stopScan()
        runCatching { DfuServiceListenerHelper.unregisterProgressListener(context, progressListener) }
    }

    companion object {
        private const val TAG = "FirmwareUpdate"
        const val PRN_PACKETS = 12
        const val DFU_MTU = 247
    }
}
