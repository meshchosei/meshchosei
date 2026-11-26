package com.meshcore.repeater.model

import com.meshcore.repeater.i18n.tr

enum class ConnectionType(val displayName: String) {
    USB_SERIAL("USB-OTG"),
    BLE_COMPANION("BLE-OTA")
}

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    data class Connecting(val message: String) : ConnectionState()
    data class Connected(
        val deviceName: String,
        val connectionType: ConnectionType,
        val details: String
    ) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

data class UsbSerialDeviceItem(
    val deviceId: Int,
    val deviceName: String,
    val manufacturerName: String?,
    val productName: String?,
    val vendorId: Int,
    val productId: Int,
    val portNumber: Int = 0
) {
    val displayName: String
        get() = productName ?: manufacturerName ?: tr("USB-Gerät ({0}:{1})", vendorId, productId)
}

data class BleDeviceItem(
    val name: String?,
    val address: String,
    val rssi: Int
) {
    val displayName: String
        get() = if (name.isNullOrBlank()) tr("Unbekannter Node ({0})", address) else name
}

data class TerminalLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val text: String,
    val isOutgoing: Boolean
)

data class MeshContact(
    val pubKey: ByteArray,
    val pubKeyHex: String,
    val keyPrefix: String, // z. B. erste 12 Zeichen
    val type: Int, // 1 = CHAT, 2 = REPEATER, 3 = ROOM, 4 = SENSOR
    val flags: Int = 0,
    val outPathLen: Int = 0, // Hops (0 = direkt, -1 = kein bekannter Pfad / Flood)
    val name: String,
    val lastAdvertTimestamp: Long = 0L,
    val gpsLat: Double = 0.0,
    val gpsLon: Double = 0.0,
    val snr: Float? = null,
    val rssi: Int? = null,
    val lastSeenMillis: Long = System.currentTimeMillis()
) {
    val isRepeater: Boolean
        get() = type == 2 // ADV_TYPE_REPEATER

    val isRoomServer: Boolean
        get() = type == 3 // ADV_TYPE_ROOM

    val typeLabel: String
        get() = when (type) {
            2 -> "Repeater"
            3 -> "Room Server"
            1 -> "Client"
            4 -> "Sensor"
            else -> "Node"
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MeshContact) return false
        return pubKeyHex == other.pubKeyHex
    }

    override fun hashCode(): Int = pubKeyHex.hashCode()
}

data class RemoteAdminSession(
    val targetRepeater: MeshContact? = null,
    val savedPassword: String = "password",
    val isLoggedIn: Boolean = false,
    val isLoggingIn: Boolean = false,
    val sessionStatus: String = "Nicht eingeloggt"
)
