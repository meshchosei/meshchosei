package com.meshcore.repeater.model

/**
 * Vom Companion-Node selbst gemeldete Gerätedaten (v1.25), aus `RESP_SELF_INFO` (0x05, Antwort auf APP_START) und
 * `RESP_DEVICE_INFO` (0x0D, Antwort auf CMD_DEVICE_QUERY). Gilt nur für die laufende Node-Verbindung und wird beim
 * Trennen geleert.
 *
 * Bewusst **nicht** übernommen: die BLE-PIN (DEVICE_INFO Byte 4–7) und die GPS-Position des Companions
 * (SELF_INFO Byte 36–43, meist der Standort des Nutzers).
 */
data class CompanionNodeInfo(
    // RESP_SELF_INFO
    val name: String = "",
    val publicKeyHex: String = "",
    val txPowerDbm: Int? = null,
    val maxTxPowerDbm: Int? = null,
    val freqMhz: Double? = null,
    val bwKhz: Double? = null,
    val sf: Int? = null,
    val cr: Int? = null,
    // RESP_DEVICE_INFO
    val protocolVersion: Int? = null,
    val maxContacts: Int? = null,
    val maxChannels: Int? = null,
    val buildDate: String = "",
    val model: String = "",
    val firmwareVersion: String = ""
) {
    val hasSelfInfo: Boolean get() = publicKeyHex.isNotEmpty()
    val hasDeviceInfo: Boolean get() = protocolVersion != null
}
