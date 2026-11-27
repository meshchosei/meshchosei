package com.meshcore.repeater.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.26 Live-Test: Menü, Navigation bei gewolltem OTA-Disconnect, Auto-Reconnect, Aufräumen beim Verlassen. */
class OtaFlowRulesTest {
    private val idle = OtaBootloaderRequest.Idle

    @Test fun menuGreyedOutUntilRepeaterReadyUnlessOtaActiveOrUnfinished() {
        assertFalse(OtaFlowRules.menuEnabled(isRepeaterReady = false, otaActiveOrFinished = false))
        assertTrue(OtaFlowRules.menuEnabled(isRepeaterReady = true, otaActiveOrFinished = false))
        assertTrue("Rückkehr in den OTA-Screen während des Updates", OtaFlowRules.menuEnabled(isRepeaterReady = false, otaActiveOrFinished = true))
    }

    @Test fun onlyCompanionConnectedIsNotRepeaterReady() {
        assertFalse("nur Companion, kein Login", OtaFlowRules.isRepeaterReady(isConnected = true, isDirectRepeater = false, remoteLoggedIn = false))
        assertTrue("Repeater direkt per USB", OtaFlowRules.isRepeaterReady(isConnected = true, isDirectRepeater = true, remoteLoggedIn = false))
        assertTrue("Remote-Admin eingeloggt", OtaFlowRules.isRepeaterReady(isConnected = true, isDirectRepeater = false, remoteLoggedIn = true))
        assertFalse("nicht verbunden", OtaFlowRules.isRepeaterReady(isConnected = false, isDirectRepeater = true, remoteLoggedIn = true))
        // Companion verbunden, kein Login, kein OTA -> Menüpunkt ausgegraut
        assertFalse(OtaFlowRules.menuEnabled(OtaFlowRules.isRepeaterReady(true, false, false), otaActiveOrFinished = false))
    }

    @Test fun activeOrFinishedCoversRequestRunAndUnresetResult() {
        assertFalse(OtaFlowRules.isActiveOrFinished(OtaPhase.IDLE, idle, sessionStarted = false))
        assertTrue(OtaFlowRules.isActiveOrFinished(OtaPhase.IDLE, OtaBootloaderRequest.Sending, false))
        assertTrue(OtaFlowRules.isActiveOrFinished(OtaPhase.IDLE, OtaBootloaderRequest.NotFound, false))
        assertTrue("start ota gesendet, Gerät startet neu", OtaFlowRules.isActiveOrFinished(OtaPhase.IDLE, idle, sessionStarted = true))
        assertTrue(OtaFlowRules.isActiveOrFinished(OtaPhase.UPLOADING, idle, false))
        assertTrue("Fertig, aber noch nicht zurückgesetzt", OtaFlowRules.isActiveOrFinished(OtaPhase.COMPLETED, idle, false))
    }

    @Test fun intendedOtaDisconnectKeepsUserOnOtaScreen() {
        assertFalse(OtaFlowRules.resetToConnectionOnDisconnect(onOtaScreen = true, otaActiveOrFinished = true))
        // ohne OTA-Vorgang bzw. auf anderen Screens: wie bisher zurück zum Verbindungs-Screen
        assertTrue(OtaFlowRules.resetToConnectionOnDisconnect(onOtaScreen = true, otaActiveOrFinished = false))
        assertTrue(OtaFlowRules.resetToConnectionOnDisconnect(onOtaScreen = false, otaActiveOrFinished = true))
        assertTrue(OtaFlowRules.resetToConnectionOnDisconnect(onOtaScreen = false, otaActiveOrFinished = false))
    }

    @Test fun leavingClearsOnlyAfterCompletion() {
        assertTrue(OtaFlowRules.clearOnLeave(OtaPhase.COMPLETED))
        assertTrue(OtaFlowRules.clearOnLeave(OtaPhase.ABORTED))
        assertTrue(OtaFlowRules.clearOnLeave(OtaPhase.ERROR))
        assertFalse("mitten im Upload nie leeren", OtaFlowRules.clearOnLeave(OtaPhase.UPLOADING))
        assertFalse(OtaFlowRules.clearOnLeave(OtaPhase.ENABLING_BOOTLOADER))
        assertFalse("Auswahl vor dem Start bleibt erhalten", OtaFlowRules.clearOnLeave(OtaPhase.IDLE))
    }

    private val blePlan = OtaReconnectPlan(ConnectionType.BLE_COMPANION, bleAddress = "C2:1F:3A:44:90:0B")

    @Test fun autoReconnectOnlyAfterSuccessWithSwitchOnAndOnce() {
        assertTrue(OtaFlowRules.shouldAutoReconnect(true, OtaPhase.COMPLETED, blePlan, alreadyStarted = false, isConnected = false))
        assertFalse("Schalter aus", OtaFlowRules.shouldAutoReconnect(false, OtaPhase.COMPLETED, blePlan, false, false))
        assertFalse("Fehler", OtaFlowRules.shouldAutoReconnect(true, OtaPhase.ERROR, blePlan, false, false))
        assertFalse("Abbruch", OtaFlowRules.shouldAutoReconnect(true, OtaPhase.ABORTED, blePlan, false, false))
        assertFalse("keine gemerkte Verbindung", OtaFlowRules.shouldAutoReconnect(true, OtaPhase.COMPLETED, null, false, false))
        assertFalse("nur einmal (Fertig + Verlassen)", OtaFlowRules.shouldAutoReconnect(true, OtaPhase.COMPLETED, blePlan, true, false))
        assertFalse("schon wieder verbunden", OtaFlowRules.shouldAutoReconnect(true, OtaPhase.COMPLETED, blePlan, false, true))
        assertEquals(2_500L, OtaFlowRules.RECONNECT_DELAY_MS)
    }

    private fun usb(id: Int, vid: Int, pid: Int) = UsbSerialDeviceItem(id, "/dev/bus/usb/001/00$id", null, null, vid, pid)

    @Test fun usbDeviceIsFoundAgainAfterReEnumeration() {
        val plan = OtaReconnectPlan(ConnectionType.USB_SERIAL, usbDeviceId = 1003, usbVendorId = 0x239A, usbProductId = 0x8029)
        // neue Geräte-ID nach dem Neustart, gleiches Gerät
        assertEquals(1007, OtaFlowRules.pickUsbDevice(listOf(usb(1005, 0x1A86, 0x7523), usb(1007, 0x239A, 0x8029)), plan))
        assertEquals(1003, OtaFlowRules.pickUsbDevice(listOf(usb(1003, 0x239A, 0x8029), usb(1007, 0x239A, 0x8029)), plan))
        assertEquals("einziges Gerät", 1009, OtaFlowRules.pickUsbDevice(listOf(usb(1009, 0x10C4, 0xEA60)), plan))
        assertNull("mehrdeutig, kein passendes", OtaFlowRules.pickUsbDevice(listOf(usb(1, 1, 1), usb(2, 2, 2)), plan))
        assertNull(OtaFlowRules.pickUsbDevice(emptyList(), plan))
    }
}
