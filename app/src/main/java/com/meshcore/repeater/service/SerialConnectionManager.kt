package com.meshcore.repeater.service

import com.meshcore.repeater.i18n.tr
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
import kotlinx.coroutines.channels.Channel
import java.io.IOException

class SerialConnectionManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onDataReceived: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onConnectionChanged: (Boolean, String?) -> Unit,
    private val onFrameReceived: (ByteArray) -> Unit = {},
    private val onDevicesChanged: () -> Unit = {},
    private val onLinkLost: (String) -> Unit = {}
) {
    companion object {
        private const val TAG = "SerialConnMgr"
        private const val ACTION_USB_PERMISSION = "com.meshcore.repeater.USB_PERMISSION"
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
    private var usbSerialPort: UsbSerialPort? = null
    private var connectedDeviceId: Int? = null
    private var readJob: Job? = null

    private var pendingConnectDevice: UsbDevice? = null
    private var pendingBaudRate: Int = 115200

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED || intent?.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                val dev: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                scope.launch(Dispatchers.Main) {
                    if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED && usbSerialPort != null &&
                        (dev == null || connectedDeviceId == null || dev.deviceId == connectedDeviceId)) {
                        linkLost("USB-Gerät wurde getrennt (Kabel abgezogen oder Repeater ausgeschaltet).")
                    }
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
            context.registerReceiver(usbReceiver, filter)
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
                ?: throw IOException(tr("Treiber nicht gefunden."))

            val connection = manager.openDevice(driver.device)
                ?: throw IOException(tr("Konnte USB-Verbindung nicht öffnen. Berechtigung fehlt?"))

            val port = driver.ports[0]
            port.open(connection)
            port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            port.dtr = true
            port.rts = true

            generation++
            usbSerialPort = port
            connectedDeviceId = device.deviceId
            val name = device.productName ?: device.deviceName ?: "USB-Node"
            onConnectionChanged(true, name)

            startReading(port)
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting USB", e)
            onError(tr("USB-Verbindung fehlgeschlagen: {0}", e.message))
            disconnect()
        }
    }

    /**
     * Liest USB-Serial und trennt sauber zwischen
     *  - Companion-Frames:  '>' (0x3E) + len_lo + len_hi + payload   (len 1..1024, payload[0] = Response < 0x20 / Push >= 0x80)
     *  - CLI-Textzeilen:    z. B. Echo "get name" und Antwort "  -> > MeinRepeater"
     * Früher galt jedes '>' als Frame-Start: Das '>' in "  -> > Name" wurde verschluckt und der Rest
     * blieb bis zum nächsten USB-Paket im Puffer hängen – Name/Owner kamen dadurch nie sauber an.
     */
    private fun startReading(port: UsbSerialPort) {
        readJob?.cancel()
        readJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(2048)
            val pending = java.io.ByteArrayOutputStream()   // noch nicht entschiedene Bytes (angefangener Frame)
            val text = java.io.ByteArrayOutputStream()      // aktuelle Textzeile
            var pendingSince = 0L

            fun flushLine() {
                val line = String(text.toByteArray(), Charsets.UTF_8).trimEnd('\r', '\n')
                text.reset()
                if (line.isNotBlank()) launch(Dispatchers.Main) { onDataReceived(line) }
            }

            while (isActive && usbSerialPort != null) {
                try {
                    val len = port.read(buffer, 100)
                    if (len > 0) { pending.write(buffer, 0, len); pendingSince = System.currentTimeMillis() }
                    val arr = pending.toByteArray()
                    var i = 0
                    var waitForMore = false
                    while (i < arr.size) {
                        val b = arr[i]
                        if (b == 0x3E.toByte()) {
                            if (arr.size - i < 4) {
                                // Zu wenig Bytes, um zu entscheiden: kurz auf mehr Daten warten, sonst als Text behandeln
                                if (System.currentTimeMillis() - pendingSince < 300L) { waitForMore = true; break }
                            } else {
                                val pLen = (arr[i + 1].toInt() and 0xFF) or ((arr[i + 2].toInt() and 0xFF) shl 8)
                                val code = arr[i + 3].toInt() and 0xFF
                                if (pLen in 1..1024 && (code < 0x20 || code >= 0x80)) {
                                    if (i + 3 + pLen > arr.size) {
                                        if (System.currentTimeMillis() - pendingSince < 1500L) { waitForMore = true; break }
                                    } else {
                                        val payload = arr.copyOfRange(i + 3, i + 3 + pLen)
                                        launch(Dispatchers.Main) { onFrameReceived(payload) }
                                        i += 3 + pLen
                                        continue
                                    }
                                }
                            }
                        }
                        // normales Textzeichen
                        if (b == 0x0A.toByte()) flushLine() else text.write(b.toInt())
                        i++
                    }
                    pending.reset()
                    if (waitForMore) pending.write(arr, i, arr.size - i)
                    // Prompt/Teilzeile ohne Zeilenende (z. B. "> ") nach kurzer Ruhe trotzdem ausgeben
                    if (len <= 0 && !waitForMore && text.size() > 0 && System.currentTimeMillis() - pendingSince > 400L) flushLine()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    if (isActive && usbSerialPort != null) {
                        launch(Dispatchers.Main) {
                            linkLost(tr("USB-Lesefehler – Verbindung zum Gerät verloren ({0}).", e.message ?: e.javaClass.simpleName))
                        }
                    }
                    break
                }
                delay(20)
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Alle USB-Schreibvorgänge laufen über EINEN Schreib-Channel mit genau einem Consumer:
    // Reihenfolge bleibt erhalten, Frames/Textzeilen können sich nicht überholen oder verschachteln.
    // Jeder Eintrag trägt die Verbindungs-Generation -> nach Trennen/Neuverbinden wird nichts Altes geschrieben.
    // ---------------------------------------------------------------------------------------
    private class UsbWrite(val generation: Int, val bytes: ByteArray, val timeoutMs: Int, val isFrame: Boolean)
    private val writeChannel = Channel<UsbWrite>(Channel.UNLIMITED)
    @Volatile private var generation = 0
    private val writerJob: Job = scope.launch(Dispatchers.IO) {
        for (w in writeChannel) {
            val port = usbSerialPort
            if (port == null || w.generation != generation) continue
            try {
                port.write(w.bytes, w.timeoutMs)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "USB write error", e)
                val reason = if (w.isFrame) tr("USB-Schreibfehler – Gerät antwortet nicht mehr ({0}).", e.message ?: e.javaClass.simpleName)
                             else tr("USB-Senden fehlgeschlagen – Verbindung verloren ({0}).", e.message ?: e.javaClass.simpleName)
                launch(Dispatchers.Main) { linkLost(reason) }
            }
        }
    }

    fun sendBytes(frame: ByteArray) {
        if (usbSerialPort == null) return
        val wrapped = ByteArray(3 + frame.size)
        wrapped[0] = 0x3C.toByte()
        wrapped[1] = (frame.size and 0xFF).toByte()
        wrapped[2] = ((frame.size shr 8) and 0xFF).toByte()
        System.arraycopy(frame, 0, wrapped, 3, frame.size)
        writeChannel.trySend(UsbWrite(generation, wrapped, 500, isFrame = true))
    }

    fun send(command: String) {
        if (usbSerialPort == null) { onError("Nicht mit USB verbunden."); return }
        val toSend = if (command.endsWith("\n")) command else "$command\r\n"
        writeChannel.trySend(UsbWrite(generation, toSend.toByteArray(Charsets.UTF_8), 2000, isFrame = false))
    }

    /** Unerwarteter Verbindungsabbruch: einmalig melden und sauber trennen. */
    private fun linkLost(reason: String) {
        if (usbSerialPort == null) return
        onLinkLost(reason)
        disconnect()
    }

    fun disconnect() {
        generation++ // noch wartende Schreibvorgänge der alten Verbindung verwerfen
        connectedDeviceId = null
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
        writeChannel.close()
        writerJob.cancel()
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (_: Exception) {}
    }
}
