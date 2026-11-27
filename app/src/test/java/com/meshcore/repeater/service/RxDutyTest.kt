package com.meshcore.repeater.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.27: RX-Duty-Cycle (`rxduty`, ZephCore CommonCLI.cpp) – Lesen, Parsen, Setzen, Backup und Diff-Abgleich. */
class RxDutyTest {
    @Test fun parsesZephCoreGetReply() {
        // ZephCore: snprintf(reply, "> %d", rx_duty_cycle)
        assertEquals(true, RepeaterProtocol.parseRxDuty("> 1"))
        assertEquals(false, RepeaterProtocol.parseRxDuty("> 0"))
        assertEquals(true, RepeaterProtocol.parseRxDuty("1"))
        assertEquals(false, RepeaterProtocol.parseRxDuty("0"))
        assertEquals(true, RepeaterProtocol.parseRxDuty("on")); assertEquals(false, RepeaterProtocol.parseRxDuty("OFF"))
        assertEquals(true, RepeaterProtocol.parseRxDuty("true"))
    }

    @Test fun unknownFormatOrUnsupportedFirmwareIsNull() {
        assertNull(RepeaterProtocol.parseRxDuty("??: rxduty"))
        assertNull(RepeaterProtocol.parseRxDuty("Error: unknown config"))
        assertNull(RepeaterProtocol.parseRxDuty("2"))
        assertNull(RepeaterProtocol.parseRxDuty(""))
        assertNull(RepeaterProtocol.parseRxDuty(null))
    }

    @Test fun queryIsInStartScanAndValidated() {
        assertTrue(RepeaterProtocol.CMD_GET_RXDUTY in listOf("get rxduty"))
        val ok = QuerySpec.validatorFor(RepeaterProtocol.CMD_GET_RXDUTY)
        assertTrue(ok("1")); assertTrue(ok("0")); assertTrue(ok("on"))
        assertFalse("keine fremde Antwort (z. B. Funkwerte) annehmen", ok("869.618,62.5,8,8"))
        assertFalse(ok("2"))
    }

    @Test fun buildsSetCommandAndReadsConfirmation() {
        assertEquals("set rxduty on", RepeaterProtocol.buildSetRxDuty(true))
        assertEquals("set rxduty off", RepeaterProtocol.buildSetRxDuty(false))
        assertEquals(true, RepeaterProtocol.parseSetRxDutyReply("OK - rxduty=1 (reboot to apply)"))
        assertEquals(false, RepeaterProtocol.parseSetRxDutyReply("OK - rxduty=0 (reboot to apply)"))
        assertNull(RepeaterProtocol.parseSetRxDutyReply("Error: must be 0, 1, on, or off"))
    }

    @Test fun backupStoresRxDutyAsBoolean() {
        val def = MeshCoreBackup.VARS.first { it.key == "rxduty" }
        assertEquals(MeshCoreBackup.VarType.BOOL, def.type)
        assertEquals(true, MeshCoreBackup.replyToJson(def, "1"))
        assertEquals(false, MeshCoreBackup.replyToJson(def, "0"))
        assertNull("MeshCore ohne rxduty: nicht ins Backup", MeshCoreBackup.replyToJson(def, "??: rxduty"))
    }

    @Test fun restoreSendsSetRxDutyAndDiffSkipsUnchanged() {
        val cmds = MeshCoreBackup.parse("""{"vars": {"rxduty": true, "tx": 14}}""").commands(includePrivateKey = false, includeGuestPassword = false)
        assertTrue(cmds.contains("set rxduty on"))
        assertTrue(MeshCoreBackup.parse("""{"vars": {"rxduty": false}}""").commands(false, false).contains("set rxduty off"))
        // Diff-Abgleich: Repeater hat schon "on" -> übersprungen; hat "off" -> gesendet
        val (same, skipped) = MeshCoreBackup.withoutUnchanged(listOf("set rxduty on"), mapOf("rxduty" to "on"))
        assertTrue(same.isEmpty()); assertEquals(1, skipped)
        val (diff, skipped2) = MeshCoreBackup.withoutUnchanged(listOf("set rxduty on"), mapOf("rxduty" to "off"))
        assertEquals(listOf("set rxduty on"), diff); assertEquals(0, skipped2)
        // Firmware ohne rxduty (kein aktueller Wert): Befehl bleibt, die Firmware lehnt ihn ab
        assertEquals(listOf("set rxduty on"), MeshCoreBackup.withoutUnchanged(listOf("set rxduty on"), emptyMap()).first)
    }
}
