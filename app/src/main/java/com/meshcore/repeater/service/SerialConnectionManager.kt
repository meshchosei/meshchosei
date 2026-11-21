package com.meshcore.repeater.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.meshcore.repeater.model.UsbSerialDeviceItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

class SerialConnectionManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onDataReceived: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onConnectionChanged: (Boolean, String?) -> Unit,
    private val onFrameReceived: (ByteArray) -> Unit = {},
    private val onDevicesChanged: () -> Unit = {}
) {
    companion object {
        private const val TAG = "SerialConnMgr"
        private const val ACTION_USB_PERMISSION = "com.meshcore.repeater.USB_PERMISSION"
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
    private var usbSerialPort: UsbSerialPort? = null
    private var readJob: Job? = null

    private var pendingConnectDevice: UsbDevice? = null
    private var pendingBaudRate: Int = 115200

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED || intent?.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                scope.launch(Dispatchers.Main) {
                    if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED && usbSerialPort != null) disconnect()
                    onDevicesChanged()
                }
                return
            }
            if (intent?.action == ACTION_USB_PERMISSION) {
                synchronized(this) {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted && device != null) {
                        connectToDeviceInternal(device, pendingBaudRate)
                    } else {
                        onError("USB-Berechtigung wurde verweigert.")
                        onConnectionChanged(false, null)
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter(ACTION_USB_PERMISSION).apply { addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED); addAction(UsbManager.ACTION_USB_DEVICE_DETACHED) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED) else context.registerReceiver(usbReceiver, filter)
        }
    }

    fun listDevices(): List<UsbSerialDeviceItem> {
        val manager = usbManager ?: return emptyList()
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        return availableDrivers.flatMap { driver ->
            val dev = driver.device
            driver.ports.indices.map { portIdx ->
                UsbSerialDeviceItem(
                    deviceId = dev.deviceId,
                    deviceName = dev.deviceName,
                    manufacturerName = dev.manufacturerName,
                    productName = dev.productName,
                    vendorId = dev.vendorId,
                    productId = dev.productId,
                    portNumber = portIdx
                )
            }
        }
    }

    fun connect(deviceId: Int, baudRate: Int = 115200) {
        val manager = usbManager ?: run {
            onError("USB-Manager ist auf diesem Gerät nicht verfügbar.")
            return
        }

        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        val driver = availableDrivers.firstOrNull { it.device.deviceId == deviceId }

        if (driver == null) {
            onError("Kein passender USB-Treiber für dieses Gerät gefunden.")
            return
        }

        val device = driver.device
        if (!manager.hasPermission(device)) {
            pendingConnectDevice = device
            pendingBaudRate = baudRate
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                0
            }
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION).setPackage(context.packageName).setPackage(context.packageName),
                flags
            )
            manager.requestPermission(device, permissionIntent)
        } else {
            connectToDeviceInternal(device, baudRate)
        }
    }

    private fun connectToDeviceInternal(device: UsbDevice, baudRate: Int) {
        val manager = usbManager ?: return
        try {
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
            val driver = availableDrivers.firstOrNull { it.device.deviceId == device.deviceId }
                ?: throw IOException("Treiber nicht gefunden.")

            val connection = manager.openDevice(driver.device)
                ?: throw IOException("Konnte USB-Verbindung nicht öffnen. Berechtigung fehlt?")

            val port = driver.ports[0]
            port.open(connection)
            port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            port.dtr = true
            port.rts = true

            usbSerialPort = port
            val name = device.productName ?: device.deviceName ?: "USB-Node"
            onConnectionChanged(true, name)

            startReading(port)
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting USB", e)
            onError("USB-Verbindung fehlgeschlagen: ${e.message}")
            disconnect()
        }
    }

    private fun startReading(port: UsbSerialPort) {
        readJob?.cancel()
        readJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(2048)
            val lineBuffer = StringBuilder()
            val rawBytes = java.io.ByteArrayOutputStream()

            while (isActive && usbSerialPort != null) {
                try {
                    val len = port.read(buffer, 100)
                    if (len > 0) {
                        rawBytes.write(buffer, 0, len)
                        var arr = rawBytes.toByteArray()
                        var offset = 0
                        while (offset + 3 <= arr.size && arr[offset] == 0x3E.toByte()) {
                            val pLen = (arr[offset + 1].toInt() and 0xFF) or ((arr[offset + 2].toInt() and 0xFF) shl 8)
                            if (offset + 3 + pLen <= arr.size) {
                                val payload = arr.copyOfRange(offset + 3, offset + 3 + pLen)
                                scope.launch(Dispatchers.Main) { onFrameReceived(payload) }
                                offset += 3 + pLen
                            } else break
                        }
                        if (offset > 0) {
                            rawBytes.reset()
                            if (offset < arr.size) rawBytes.write(arr, offset, arr.size - offset)
                            continue
                        }
                        if (arr.isNotEmpty() && arr[0] != 0x3E.toByte()) {
                            rawBytes.reset()
                            val chunk = String(arr, Charsets.UTF_8)
                            lineBuffer.append(chunk)

                        while (lineBuffer.contains("\n")) {
                            val line = lineBuffer.substring(0, lineBuffer.indexOf("\n")).trimEnd('\r')
                            lineBuffer.delete(0, lineBuffer.indexOf("\n") + 1)
                            if (line.isNotEmpty()) {
                                launch(Dispatchers.Main) {
                                    onDataReceived(line)
                                }
                            }
                        }
                    }
                } catch (e: IOException) {
                    if (isActive) {
                        launch(Dispatchers.Main) {
                            onError("USB-Leseabbruch: ${e.message}")
                            disconnect()
                        }
                    }
                    break
                }
                delay(20)
            }
        }
    }

    fun sendBytes(frame: ByteArray) {
        val port = usbSerialPort ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val wrapped = ByteArray(3 + frame.size)
                wrapped = 0x3C.toByte()
                wrapped = (frame.size and 0xFF).toByte()
                wrapped = ((frame.size shr 8) and 0xFF).toByte()
                System.arraycopy(frame, 0, wrapped, 3, frame.size)
                port.write(wrapped, 500)
            } catch (e: Exception) { Log.e(TAG, "USB frame write error", e) }
        }
    }

    fun send(command: String) {
        scope.launch(Dispatchers.IO) {
            val port = usbSerialPort
            if (port == null) {
                launch(Dispatchers.Main) { onError("Nicht mit USB verbunden.") }
                return@launch
            }
            try {
                val toSend = if (command.endsWith("\n")) command else "$command\r\n"
                port.write(toSend.toByteArray(Charsets.UTF_8), 2000)
            } catch (e: Exception) {
                launch(Dispatchers.Main) { onError("Senden fehlgeschlagen: ${e.message}") }
            }
        }
    }

    fun disconnect() {
        readJob?.cancel()
        readJob = null
        try {
            usbSerialPort?.close()
        } catch (_: Exception) {}
        usbSerialPort = null
        onConnectionChanged(false, null)
    }

    fun destroy() {
        disconnect()
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (_: Exception) {}
    }
}
