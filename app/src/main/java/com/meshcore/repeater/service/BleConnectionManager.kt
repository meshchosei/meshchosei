package com.meshcore.repeater.service
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
import java.util.UUID

class BleConnectionManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onDataReceived: (String) -> Unit,
    private val onFrameReceived: (ByteArray) -> Unit,
    private val onError: (String) -> Unit,
    private val onConnectionChanged: (Boolean, String?) -> Unit
) {
    companion object {
        private const val TAG = "BleConnMgr"

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
            onError("BLE-Scan fehlgeschlagen (Fehlercode $errorCode)")
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
            onError("Scan-Start fehlgeschlagen: ${e.message}")
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

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        stopScan()
        val adapter = bluetoothAdapter ?: return
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: Exception) {
            onError("Ungültige BLE-Adresse: $address")
            return
        }

        disconnect()
        activeGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    val name = gatt?.device?.name ?: gatt?.device?.address ?: "BLE-Companion"
                    scope.launch(Dispatchers.Main) {
                        onConnectionChanged(true, name)
                    }
                    gatt?.requestMtu(512)
                    gatt?.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    scope.launch(Dispatchers.Main) {
                        onConnectionChanged(false, null)
                    }
                    activeGatt?.close()
                    activeGatt = null
                }
            } else {
                scope.launch(Dispatchers.Main) {
                    onError("BLE-Verbindungsfehler: Status $status")
                    onConnectionChanged(false, null)
                }
                activeGatt?.close()
                activeGatt = null
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                val nusService = gatt.getService(NUS_SERVICE_UUID)
                if (nusService != null) {
                    rxCharacteristic = nusService.getCharacteristic(NUS_RX_CHAR_UUID)
                    val txCharacteristic = nusService.getCharacteristic(NUS_TX_CHAR_UUID)

                    if (txCharacteristic != null) {
                        gatt.setCharacteristicNotification(txCharacteristic, true)
                        val descriptor = txCharacteristic.getDescriptor(CLIENT_CONFIG_DESCRIPTOR)
                        if (descriptor != null) {
                            @Suppress("DEPRECATION")
                            descriptor.value = android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            gatt.writeDescriptor(descriptor)
                        }
                    }
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            if (characteristic?.uuid == NUS_TX_CHAR_UUID) {
                @Suppress("DEPRECATION")
                val data = characteristic.value ?: return
                if (data.isEmpty()) return

                val opcode = data[0]
                // Prüfen, ob es sich um ein binäres MeshCore Companion-Paket handelt
                val isCompanionPacket = when (opcode) {
                    RepeaterProtocol.PACKET_OK,
                    RepeaterProtocol.PACKET_ERROR,
                    RepeaterProtocol.PACKET_CONTACT_START,
                    RepeaterProtocol.PACKET_CONTACT,
                    RepeaterProtocol.PACKET_CONTACT_END,
                    RepeaterProtocol.PACKET_SELF_INFO,
                    RepeaterProtocol.PACKET_SENT,
                    RepeaterProtocol.PACKET_CONTACT_MSG_V3,
                    RepeaterProtocol.PACKET_CLI_REPLY -> true
                    else -> false
                }

                if (isCompanionPacket) {
                    scope.launch(Dispatchers.Main) {
                        onFrameReceived(data)
                    }
                } else {
                    // Fallback: Als ASCII-Textzeilen parsen
                    val text = String(data, Charsets.UTF_8)
                    synchronized(lineBuffer) {
                        lineBuffer.append(text)
                        while (lineBuffer.contains("\n")) {
                            val line = lineBuffer.substring(0, lineBuffer.indexOf("\n")).trimEnd('\r')
                            lineBuffer.delete(0, lineBuffer.indexOf("\n") + 1)
                            if (line.isNotEmpty()) {
                                scope.launch(Dispatchers.Main) {
                                    onDataReceived(line)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun sendBytes(frame: ByteArray) {
        val gatt = activeGatt
        val rx = rxCharacteristic
        if (gatt == null || rx == null) {
            onError("Kein aktiver BLE-UART-Dienst.")
            return
        }

        scope.launch(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            rx.value = frame
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(rx)
        }
    }

    @SuppressLint("MissingPermission")
    fun send(command: String) {
        val gatt = activeGatt
        val rx = rxCharacteristic
        if (gatt == null || rx == null) {
            onError("Kein aktiver BLE-UART-Dienst.")
            return
        }

        scope.launch(Dispatchers.IO) {
            val toSend = if (command.endsWith("\n")) command else "$command\r\n"
            @Suppress("DEPRECATION")
            rx.value = toSend.toByteArray(Charsets.UTF_8)
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(rx)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            activeGatt?.disconnect()
            activeGatt?.close()
        } catch (_: Exception) {}
        activeGatt = null
        rxCharacteristic = null
        onConnectionChanged(false, null)
    }

    fun destroy() {
        stopScan()
        disconnect()
    }
}
