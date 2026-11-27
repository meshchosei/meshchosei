package com.meshcore.repeater.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.25: Bildschirm bleibt an, solange verbunden/verbindend oder eingelesen/gescannt wird – sonst aus. */
class ScreenWakeRulesTest {
    private val ble = ConnectionState.Connected("ThinkNode M1", ConnectionType.BLE_COMPANION, "MeshCore NUS")

    @Test fun keepsScreenOnWhileConnectedOrConnecting() {
        assertTrue(ScreenWakeRules.keepScreenOn(ble, readingOrScanning = false))
        assertTrue(ScreenWakeRules.keepScreenOn(ConnectionState.Connecting("Verbinde BLE..."), false))
        assertTrue(ScreenWakeRules.keepScreenOn(ConnectionState.Connected("USB-Node", ConnectionType.USB_SERIAL, "115200 Baud"), false))
    }

    @Test fun keepsScreenOnWhileReadingOrScanningEvenIfDisconnected() {
        assertTrue(ScreenWakeRules.keepScreenOn(ConnectionState.Disconnected, readingOrScanning = true))
    }

    @Test fun releasesWhenDisconnectedAndIdle() {
        assertFalse(ScreenWakeRules.keepScreenOn(ConnectionState.Disconnected, readingOrScanning = false))
        assertFalse(ScreenWakeRules.keepScreenOn(ConnectionState.Error("BLE-Verbindung fehlgeschlagen"), false))
    }

    @Test fun staysAwakeDuringFirmwareUpdateEvenWithoutConnection() {
        // v1.26: vor dem DFU wird die Verbindung getrennt – der Bildschirm muss trotzdem an bleiben
        assertTrue(ScreenWakeRules.keepScreenOn(ConnectionState.Disconnected, readingOrScanning = false, firmwareUpdateActive = true))
        assertFalse(ScreenWakeRules.keepScreenOn(ConnectionState.Disconnected, readingOrScanning = false, firmwareUpdateActive = false))
    }
}
