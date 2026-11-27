package com.meshcore.repeater.service
import com.meshcore.repeater.i18n.tr
import android.os.Build

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import com.meshcore.repeater.model.BleDeviceItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class BleConnectionManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onDataReceived: (String) -> Unit,
    private val onFrameReceived: (ByteArray) -> Unit,
    private val onError: (String) -> Unit,
    private val onConnectionChanged: (Boolean, String?) -> Unit,
    private val onLinkLost: (String) -> Unit = {}
) {
    @Volatile private var linkEstablished = false
    @Volatile private var userDisconnect = false
    companion object {
        private const val TAG = "BleConnMgr"
        private const val GAP_BETWEEN_WRITES_MS = 135L

        // Nordic UART Service (NUS)
        val NUS_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val NUS_RX_CHAR_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // App write
        val NUS_TX_CHAR_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E") // App notify/read
        val CLIENT_CONFIG_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _scannedDevices = MutableStateFlow<List<BleDeviceItem>>(emptyList())
    val scannedDevices: StateFlow<List<BleDeviceItem>> = _scannedDevices

    private var activeGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var scanTimeoutJob: Job? = null

    private val lineBuffer = StringBuilder()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            val name = device.name ?: result.scanRecord?.deviceName
            val address = device.address
            val rssi = result.rssi

            val currentList = _scannedDevices.value.toMutableList()
            val existingIndex = currentList.indexOfFirst { it.address == address }
            val item = BleDeviceItem(name = name, address = address, rssi = rssi)

            if (existingIndex >= 0) {
                currentList[existingIndex] = item
            } else {
                currentList.add(item)
            }
            _scannedDevices.value = currentList
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
            if (errorCode != 1) onError(tr("BLE-Scan fehlgeschlagen (Fehlercode {0})", errorCode))
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (bluetoothAdapter?.isEnabled != true) {
            onError("Bluetooth ist ausgeschaltet. Bitte Bluetooth aktivieren.")
            return
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        val locOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) lm?.isLocationEnabled == true else true
        if (!locOn) {
            onError("Android-Standortdienste (GPS) sind aus – für BLE-Scan bitte Standort aktivieren.")
            return
        }
        if (scanner == null) {
            onError("Bluetooth ist auf diesem Gerät deaktiviert oder nicht verfügbar.")
            return
        }

        _scannedDevices.value = emptyList()
        _isScanning.value = true

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(null, settings, scanCallback)
        } catch (e: Exception) {
            _isScanning.value = false
            onError(tr("Scan-Start fehlgeschlagen: {0}", e.message))
            return
        }

        scanTimeoutJob?.cancel()
        scanTimeoutJob = scope.launch {
            delay(12000)
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanTimeoutJob?.cancel()
        if (_isScanning.value) {
            try {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            } catch (_: Exception) {}
            _isScanning.value = false
        }
    }

    private var connectJob: Job? = null
    @Volatile private var closedGatt: BluetoothGatt? = null
    private var connectRetryAddress: String? = null

    /**
     * Sauberer Verbindungsaufbau (gegen GATT-Status 147/133):
     *  1. laufenden Scan stoppen
     *  2. evtl. noch offene BluetoothGatt-Instanz mit disconnect() + close() schließen
     *  3. mindestens 400 ms warten, bis der Android-BLE-Stack die alte Verbindung freigegeben hat
     *  4. erst dann connectGatt()
     */
    @SuppressLint("MissingPermission")
    fun connect(address: String, isRetry: Boolean = false) {
        val adapter = bluetoothAdapter ?: return
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: Exception) {
            onError(tr("Ungültige BLE-Adresse: {0}", address))
            return
        }
        connectJob?.cancel()
        connectJob = scope.launch(Dispatchers.Main) {
            val hadScan = _isScanning.value
            stopScan()
            val hadGatt = closeGattQuietly()
            delay(if (hadGatt || hadScan || isRetry) 600L else 400L)
            userDisconnect = false
            linkEstablished = false
            connectRetryAddress = if (isRetry) null else address
            synchronized(lineBuffer) { lineBuffer.clear() }
            activeGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }
    }

    /**
     * GATT-Objekt vollständig freigeben (v1.25, STYLE_GUIDE §3.7): erst `disconnect()`, dann **immer** `close()` – jeweils
     * einzeln abgesichert, damit eine Exception in disconnect() das close() nicht überspringt. Das Objekt wird als
     * [closedGatt] gemerkt: späte Callbacks dieser Instanz werden ignoriert (kein doppeltes "Getrennt").
     * Danach hält die App keine Referenz mehr (activeGatt = null), sonst bleibt im Android-Stack bzw. auf dem nRF52-Node
     * ein belegter BLE-Slot hängen.
     */
    @SuppressLint("MissingPermission")
    private fun releaseGatt(g: BluetoothGatt?) {
        if (g == null) return
        closedGatt = g
        try { g.disconnect() } catch (_: Exception) {}
        try { g.close() } catch (_: Exception) {}
        if (activeGatt === g) activeGatt = null
    }

    /** Schließt eine evtl. offene GATT-Instanz OHNE onConnectionChanged-Callback (kein falsches "Getrennt" beim Verbinden). */
    @SuppressLint("MissingPermission")
    private fun closeGattQuietly(): Boolean {
        val g = activeGatt ?: return false
        stopWriter()
        userDisconnect = true
        rxCharacteristic = null
        linkEstablished = false
        releaseGatt(g)
        activeGatt = null
        return true
    }

    // ---------------------------------------------------------------------------------------
    // Streng sequentielle GATT-Initialisierung (nie zwei GATT-Operationen gleichzeitig):
    //   1) CONNECTED          -> requestMtu(247)
    //   2) onMtuChanged       -> discoverServices()
    //   3) onServicesDiscovered -> Notification-Descriptor schreiben
    //   4) onDescriptorWrite  -> EINMALIG onConnectionChanged(true)
    // ---------------------------------------------------------------------------------------
    @Volatile private var negotiatedMtu = 23
    @Volatile private var servicesRequested = false
    @Volatile private var readyNotified = false
    private var mtuFallbackJob: Job? = null

    @SuppressLint("MissingPermission")
    private fun requestServiceDiscovery(gatt: BluetoothGatt) {
        if (servicesRequested) return
        servicesRequested = true
        mtuFallbackJob?.cancel()
        if (!gatt.discoverServices()) failSetup(gatt, "BLE: Dienstsuche konnte nicht gestartet werden.")
    }

    /** Verbindung ist vollständig eingerichtet (Notifications aktiv) – genau einmal melden. */
    private fun markReady(gatt: BluetoothGatt) {
        if (readyNotified) return
        readyNotified = true
        linkEstablished = true
        startWriter()
        val name = gatt.device?.name ?: gatt.device?.address ?: "MeshCore BLE"
        scope.launch(Dispatchers.Main) { onConnectionChanged(true, name) }
    }

    @SuppressLint("MissingPermission")
    private fun failSetup(gatt: BluetoothGatt, msg: String) {
        scope.launch(Dispatchers.Main) { onError(msg) }
        try { gatt.disconnect() } catch (_: Exception) {}
        // Normalerweise schließt der folgende STATE_DISCONNECTED-Callback. Bleibt er aus, nach 2 s selbst freigeben,
        // damit kein offenes Handle hängen bleibt (ohne Callback: der Nutzer sieht den Fehler bereits).
        scope.launch(Dispatchers.Main) {
            delay(2000L)
            if (activeGatt === gatt) {
                stopWriter(); linkEstablished = false; readyNotified = false; rxCharacteristic = null
                releaseGatt(gatt)
                onConnectionChanged(false, null)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            // Späte Callbacks einer bereits geschlossenen bzw. fremden GATT-Instanz ignorieren
            if (gatt != null && (gatt === closedGatt || (activeGatt != null && gatt !== activeGatt))) { try { gatt.close() } catch (_: Exception) {}; return }
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED && gatt != null) {
                // linkEstablished erst in markReady(): Abbruch während der Einrichtung = Verbindungsfehler, kein "Link verloren"
                negotiatedMtu = 23; servicesRequested = false; readyNotified = false
                // Schritt 1: MTU aushandeln. Kommt kein onMtuChanged (manche Stacks), nach 1,5 s trotzdem weiter.
                if (!gatt.requestMtu(247)) { requestServiceDiscovery(gatt); return }
                mtuFallbackJob?.cancel()
                mtuFallbackJob = scope.launch(Dispatchers.Main) { delay(1500L); if (activeGatt === gatt) requestServiceDiscovery(gatt) }
                return
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                val wasUp = linkEstablished
                val byUser = userDisconnect
                linkEstablished = false
                readyNotified = false
                mtuFallbackJob?.cancel()
                stopWriter()
                // Genau die Instanz dieses Callbacks freigeben (auch wenn activeGatt schon null ist) und ggf. die aktive
                val current = activeGatt
                releaseGatt(gatt)
                if (current != null && current !== gatt) releaseGatt(current)
                activeGatt = null
                rxCharacteristic = null
                val retryAddr = connectRetryAddress
                scope.launch(Dispatchers.Main) {
                    if (!byUser && !wasUp && retryAddr != null && (status == 147 || status == 133 || status == 62)) {
                        // Einmaliger automatischer Neuversuch nach Stack-Fehler beim Verbindungsaufbau
                        connectRetryAddress = null
                        connect(retryAddr, isRetry = true)
                        return@launch
                    }
                    when {
                        byUser -> {}
                        wasUp -> onLinkLost(describeGattStatus(status))
                        else -> onError(if (status == 147) "BLE-Verbindungsaufbau fehlgeschlagen (Status 147 – Timeout). Gerät eingeschaltet und in Reichweite?" else tr("BLE-Verbindung fehlgeschlagen (Status {0})", status))
                    }
                    onConnectionChanged(false, null)
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            if (gatt == null || gatt !== activeGatt) return
            if (status == BluetoothGatt.GATT_SUCCESS) negotiatedMtu = mtu
            // Schritt 2: erst nach der MTU-Antwort die Dienste suchen
            requestServiceDiscovery(gatt)
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (gatt == null || gatt !== activeGatt) return
            val nusService = if (status == BluetoothGatt.GATT_SUCCESS) gatt.getService(NUS_SERVICE_UUID) else null
            val rx = nusService?.getCharacteristic(NUS_RX_CHAR_UUID)
            val txCharacteristic = nusService?.getCharacteristic(NUS_TX_CHAR_UUID)
            if (rx == null || txCharacteristic == null) { failSetup(gatt, "Kein aktiver BLE-UART-Dienst."); return }
            rxCharacteristic = rx
            gatt.setCharacteristicNotification(txCharacteristic, true)
            val descriptor = txCharacteristic.getDescriptor(CLIENT_CONFIG_DESCRIPTOR)
            if (descriptor == null) { markReady(gatt); return }
            // Schritt 3: Notifications einschalten
            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
            if (!started) failSetup(gatt, "Kein aktiver BLE-UART-Dienst.")
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: android.bluetooth.BluetoothGattDescriptor?, status: Int) {
            if (gatt == null || gatt !== activeGatt) return
            // Schritt 4: erst jetzt ist die Verbindung nutzbar
            if (status == BluetoothGatt.GATT_SUCCESS) markReady(gatt) else failSetup(gatt, tr("BLE-Verbindung fehlgeschlagen (Status {0})", status))
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            pendingWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        // Android 13+: Wert kommt als Parameter (kein Überschreiben durch die nächste Notification)
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == NUS_TX_CHAR_UUID) handleNotification(value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            if (characteristic?.uuid == NUS_TX_CHAR_UUID) {
                @Suppress("DEPRECATION")
                val data = characteristic.value?.copyOf() ?: return
                handleNotification(data)
            }
        }
    }

    private fun handleNotification(data: ByteArray) {
        if (data.isEmpty()) return
        val opcode = data[0].toInt() and 0xFF
        // MeshCore/ZephCore Companion: alle Response-Codes < 0x20, alle Push-Codes >= 0x80.
        val isCompanionPacket = opcode < 0x20 || opcode >= 0x80
        if (isCompanionPacket) {
            scope.launch(Dispatchers.Main) { onFrameReceived(data) }
        } else {
            // Fallback: Als ASCII-Textzeilen parsen
            val text = String(data, Charsets.UTF_8)
            synchronized(lineBuffer) {
                lineBuffer.append(text)
                while (lineBuffer.contains("\n")) {
                    val line = lineBuffer.substring(0, lineBuffer.indexOf("\n")).trimEnd('\r')
                    lineBuffer.delete(0, lineBuffer.indexOf("\n") + 1)
                    if (line.isNotEmpty()) scope.launch(Dispatchers.Main) { onDataReceived(line) }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Eine einzige Schreib-Warteschlange für ALLE BLE-Schreibvorgänge (Frames und Text).
    // Jeder Schreibvorgang wartet auf onCharacteristicWrite, bevor der nächste startet.
    // Companion-Frames werden nie zerteilt (BLE-Companion = 1 Frame pro Write): passt ein Frame
    // nicht in (MTU − 3), wird er verworfen statt abgeschnitten.
    // ---------------------------------------------------------------------------------------
    private class WriteItem(val data: ByteArray, val isFrame: Boolean)
    private var writeChannel: Channel<WriteItem>? = null
    private var writerJob: Job? = null
    @Volatile private var pendingWrite: CompletableDeferred<Boolean>? = null

    private fun startWriter() {
        stopWriter()
        val ch = Channel<WriteItem>(Channel.UNLIMITED)
        writeChannel = ch
        writerJob = scope.launch(Dispatchers.IO) {
            for (item in ch) {
                val maxLen = (negotiatedMtu - 3).coerceAtLeast(20)
                if (item.isFrame) {
                    if (item.data.size > maxLen) {
                        launch(Dispatchers.Main) { onError(tr("BLE-Frame zu lang für MTU ({0} > {1} Byte) – nicht gesendet.", item.data.size, maxLen)) }
                        continue
                    }
                    writeChunk(item.data)
                } else {
                    var off = 0
                    while (off < item.data.size) {
                        val end = minOf(off + maxLen, item.data.size)
                        if (!writeChunk(item.data.copyOfRange(off, end))) break
                        off = end
                    }
                }
            }
        }
    }

    private fun stopWriter() {
        writeChannel?.close(); writeChannel = null
        writerJob?.cancel(); writerJob = null
        pendingWrite?.complete(false); pendingWrite = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeChunk(chunk: ByteArray): Boolean {
        val gatt = activeGatt ?: return false
        val rx = rxCharacteristic ?: return false
        val done = CompletableDeferred<Boolean>()
        pendingWrite = done
        val started = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(rx, chunk, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) == BluetoothGatt.GATT_SUCCESS
            } else {
                rx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                rx.value = chunk
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(rx)
            }
        } catch (e: Exception) { false }
        if (!started) { pendingWrite = null; delay(60L); return false }
        withTimeoutOrNull(1000L) { done.await() }
        pendingWrite = null
        delay(GAP_BETWEEN_WRITES_MS) // Companion verarbeitet Frames nacheinander
        return true
    }

    fun sendBytes(frame: ByteArray) {
        val ch = writeChannel
        if (activeGatt == null || rxCharacteristic == null || ch == null) {
            onError("Kein aktiver BLE-UART-Dienst.")
            return
        }
        ch.trySend(WriteItem(frame.copyOf(), isFrame = true))
    }

    fun send(command: String) {
        val ch = writeChannel
        if (activeGatt == null || rxCharacteristic == null || ch == null) {
            onError("Kein aktiver BLE-UART-Dienst.")
            return
        }
        val toSend = if (command.endsWith("\n")) command else "$command\r\n"
        ch.trySend(WriteItem(toSend.toByteArray(Charsets.UTF_8), isFrame = false))
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        connectJob?.cancel()
        mtuFallbackJob?.cancel()
        stopWriter()
        readyNotified = false
        connectRetryAddress = null
        userDisconnect = true
        val wasActive = activeGatt != null || linkEstablished
        linkEstablished = false
        releaseGatt(activeGatt)   // disconnect() + close(), späte Callbacks dieser Instanz werden ignoriert
        activeGatt = null
        rxCharacteristic = null
        // Genau ein "Getrennt" pro Verbindung: kein zweites, wenn schon nichts mehr offen war
        if (wasActive) onConnectionChanged(false, null)
    }

    private fun describeGattStatus(status: Int): String = when (status) {
        8 -> "Bluetooth-Verbindung abgerissen (Timeout) – Gerät ausgeschaltet oder außer Reichweite."
        19 -> "Gerät hat die Bluetooth-Verbindung beendet (ausgeschaltet/neu gestartet?)."
        22 -> "Bluetooth-Verbindung vom Smartphone beendet."
        133 -> "Bluetooth-Verbindung verloren (GATT-Fehler 133)."
        147 -> "Bluetooth-Verbindung verloren (Timeout, Status 147)."
        else -> tr("Bluetooth-Verbindung verloren (Status {0}).", status)
    }

    /** App wird geschlossen (ViewModel.onCleared): Scan stoppen, laufenden Verbindungsaufbau abbrechen, GATT freigeben. */
    fun destroy() {
        stopScan()
        connectJob?.cancel()
        disconnect()
    }
}
