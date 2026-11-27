package com.meshcore.repeater.model

import com.meshcore.repeater.service.CompanionProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.25: ThinkNode M1 meldete `ERR_CODE_BAD_STATE` (4). Firmware-Befund: Code 4 kommt von den App-Befehlen nur auf ein
 * `CMD_GET_CONTACTS`, während der Kontakt-Iterator noch läuft (beim BLE-Connect wurde GET_CONTACTS doppelt gesendet).
 */
class CompanionHandshakeTest {
    @Test fun deviceQueryOnlyAfterSelfInfoAndEndOfContacts() {
        val h = CompanionHandshake()
        assertFalse("vor allem: nein", h.takeDeviceQuery())
        h.onSelfInfo()
        assertFalse("nur SELF_INFO: noch nicht", h.takeDeviceQuery())
        assertTrue(h.tryStartContactSync(0))
        h.onContactFrame(100)
        assertFalse("Kontakte laufen noch: noch nicht", h.takeDeviceQuery())
        h.onContactsEnd()
        assertTrue("SELF_INFO + END_OF_CONTACTS: jetzt", h.takeDeviceQuery())
        assertFalse("genau einmal pro Verbindung", h.takeDeviceQuery())
        h.reset()
        h.onContactsEnd()
        assertFalse("END ohne SELF_INFO: nein", h.takeDeviceQuery())
    }

    /** Doppeltes GET_CONTACTS beim BLE-Connect (erster Frame + fester 700-ms-Aufruf) wird unterdrückt. */
    @Test fun onlyOneGetContactsAtATime() {
        val h = CompanionHandshake(syncTimeoutMs = 15_000)
        assertTrue("erste Anfrage", h.tryStartContactSync(300))
        assertFalse("zweite Anfrage 400 ms später -> sonst BAD_STATE", h.tryStartContactSync(700))
        h.onContactFrame(5_000)
        assertFalse("Synchronisation liefert noch Kontakte", h.tryStartContactSync(19_000))
        h.onContactsEnd()
        assertTrue("nach END_OF_CONTACTS wieder erlaubt (↻)", h.tryStartContactSync(20_000))
    }

    @Test fun appStartResetsIteratorAndTimeoutUnblocks() {
        val h = CompanionHandshake(syncTimeoutMs = 15_000)
        assertTrue(h.tryStartContactSync(0))
        h.onAppStartSent()
        assertTrue("APP_START beendet den Iterator in der Firmware", h.tryStartContactSync(10))
        assertFalse(h.tryStartContactSync(20))
        assertTrue("ohne Lebenszeichen nach Timeout wieder erlaubt", h.tryStartContactSync(15_100))
    }

    @Test fun errorCodesInPlainText() {
        assertEquals("UNSUPPORTED_CMD", CompanionProtocol.errorName(1))
        assertEquals("NOT_FOUND", CompanionProtocol.errorName(2))
        assertEquals("TABLE_FULL", CompanionProtocol.errorName(3))
        assertEquals("BAD_STATE", CompanionProtocol.errorName(4))
        assertEquals("FILE_IO_ERROR", CompanionProtocol.errorName(5))
        assertEquals("ILLEGAL_ARG", CompanionProtocol.errorName(6))
        assertEquals("UNKNOWN", CompanionProtocol.errorName(99))
        assertEquals("GET_CONTACTS", CompanionProtocol.commandName(0x04))
        assertEquals("SEND_LOGIN", CompanionProtocol.commandName(0x1A))
        assertEquals("DEVICE_QUERY", CompanionProtocol.commandName(0x16))
        assertEquals("ADD_UPDATE_CONTACT", CompanionProtocol.commandName(0x09))
    }

    /** Ein BAD_STATE auf GET_CONTACTS darf einen laufenden Login nicht abbrechen; nur ein Fehler auf SEND_LOGIN. */
    @Test fun onlyLoginErrorsAbortLogin() {
        assertTrue(CompanionProtocol.errorBelongsToLogin(0x1A))
        assertFalse(CompanionProtocol.errorBelongsToLogin(0x04))
        assertFalse(CompanionProtocol.errorBelongsToLogin(0x16))
        assertFalse(CompanionProtocol.errorBelongsToLogin(null))
    }
}
