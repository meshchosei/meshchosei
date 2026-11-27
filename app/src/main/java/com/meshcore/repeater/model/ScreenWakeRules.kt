package com.meshcore.repeater.model

/**
 * Bildschirm wach halten (v1.25, STYLE_GUIDE §3.7): Sperrt sich das Smartphone mitten in einer BLE-Sitzung, beendet
 * Android die GATT-Verbindung oft hart, und auf dem nRF52-Node bleibt ein belegter BLE-Slot hängen. Deshalb bleibt der
 * Bildschirm an, solange eine Verbindung aufgebaut wird oder steht oder ein Einlesen/Scan läuft.
 * v1.26: ebenso während eines Firmware-Updates (DFU-Scan, Bootloader-Suche, Upload) – auch ohne App-Verbindung.
 */
object ScreenWakeRules {
    fun keepScreenOn(connection: ConnectionState, readingOrScanning: Boolean, firmwareUpdateActive: Boolean = false): Boolean =
        connection is ConnectionState.Connected || connection is ConnectionState.Connecting || readingOrScanning || firmwareUpdateActive
}
