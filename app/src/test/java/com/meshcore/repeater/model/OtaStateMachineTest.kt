package com.meshcore.repeater.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OtaStateMachineTest {
    private val fw = FirmwarePackage("RAK4631_repeater.zip", 412_000, "/cache/dfu/firmware.zip")
    private val target = DfuTarget("AA:BB:CC:DD:EE:FF", "RAK4631_OTA", -61, DfuKind.APP_BUTTONLESS)
    private fun run(vararg e: OtaEvent, from: OtaState = OtaState()) = e.fold(from) { s, ev -> OtaStateMachine.reduce(s, ev) }
    private val ready = run(OtaEvent.FirmwareChosen(fw), OtaEvent.TargetChosen(target))

    @Test fun startNeedsFirmwareAndTarget() {
        assertEquals(OtaPhase.IDLE, run(OtaEvent.Start).phase)
        assertEquals(OtaPhase.IDLE, run(OtaEvent.FirmwareChosen(fw), OtaEvent.Start).phase)
        assertEquals(OtaPhase.IDLE, run(OtaEvent.TargetChosen(target), OtaEvent.Start).phase)
        assertTrue(ready.canStart)
        assertEquals(OtaPhase.ENABLING_BOOTLOADER, run(OtaEvent.Start, from = ready).phase)
    }

    @Test fun fullHappyPathThroughFourPhases() {
        var s = run(OtaEvent.Start, from = ready)
        assertEquals(1, s.phase.step)
        s = run(OtaEvent.DfuInit, from = s); assertEquals(OtaPhase.DFU_INIT, s.phase); assertEquals(2, s.phase.step)
        s = run(OtaEvent.Progress(37, 4.2f, 3.9f), from = s)
        assertEquals(OtaPhase.UPLOADING, s.phase); assertEquals(3, s.phase.step); assertEquals(37, s.percent); assertEquals(4.2f, s.speedKbs, 0.001f)
        s = run(OtaEvent.Completed, from = s)
        assertEquals(OtaPhase.COMPLETED, s.phase); assertEquals(4, s.phase.step); assertEquals(100, s.percent); assertFalse(s.phase.isRunning)
    }

    @Test fun buttonlessJumpMayReturnFromInitToBootloaderBeforeUpload() {
        // Nordic legacy buttonless: onDfuProcessStarting -> onEnablingDfuMode -> (Neuverbindung) -> onDfuProcessStarting -> Upload
        var s = run(OtaEvent.Start, OtaEvent.DfuInit, OtaEvent.EnablingBootloader, from = ready)
        assertEquals(OtaPhase.ENABLING_BOOTLOADER, s.phase)
        s = run(OtaEvent.DfuInit, OtaEvent.Progress(5, 1f, 1f), from = s)
        assertEquals(OtaPhase.UPLOADING, s.phase)
        // nach Upload-Beginn nie zurück
        assertEquals(OtaPhase.UPLOADING, run(OtaEvent.EnablingBootloader, OtaEvent.DfuInit, from = s).phase)
    }

    @Test fun percentOnlyRisesAndIsClamped() {
        var s = run(OtaEvent.Start, OtaEvent.Progress(60, 1f, 1f), from = ready)
        s = run(OtaEvent.Progress(40, 1f, 1f), from = s); assertEquals(60, s.percent)
        s = run(OtaEvent.Progress(150, 1f, 1f), from = s); assertEquals(100, s.percent)
        assertEquals(0f, run(OtaEvent.Progress(100, -3f, -1f), from = s).speedKbs, 0f)
    }

    @Test fun multiPartPackageRestartsPercentPerPart() {
        var s = run(OtaEvent.Start, OtaEvent.Progress(100, 1f, 1f, part = 1, partsTotal = 2), from = ready)
        s = run(OtaEvent.Progress(3, 1f, 1f, part = 2, partsTotal = 2), from = s)
        assertEquals(3, s.percent); assertEquals(2, s.currentPart); assertEquals(2, s.partsTotal)
    }

    @Test fun abortAndErrorRememberTheStep() {
        val uploading = run(OtaEvent.Start, OtaEvent.DfuInit, OtaEvent.Progress(20, 1f, 1f), from = ready)
        val a = run(OtaEvent.Aborted, from = uploading)
        assertEquals(OtaPhase.ABORTED, a.phase); assertEquals(3, a.stoppedAtStep)
        val e = run(OtaEvent.Failed("GATT ERROR (#133)"), from = run(OtaEvent.Start, from = ready))
        assertEquals(OtaPhase.ERROR, e.phase); assertEquals(1, e.stoppedAtStep); assertEquals("GATT ERROR (#133)", e.message)
    }

    @Test fun lateLibraryEventsAfterTerminalStateAreIgnored() {
        val done = run(OtaEvent.Start, OtaEvent.Progress(100, 1f, 1f), OtaEvent.Completed, from = ready)
        assertEquals(done, run(OtaEvent.Progress(10, 1f, 1f), OtaEvent.Aborted, OtaEvent.Failed("x"), OtaEvent.DfuInit, from = done))
        val aborted = run(OtaEvent.Start, OtaEvent.Aborted, from = ready)
        assertEquals(OtaPhase.ABORTED, run(OtaEvent.Failed("late"), OtaEvent.Completed, from = aborted).phase)
    }

    @Test fun selectionIsLockedWhileRunning() {
        val s = run(OtaEvent.Start, from = ready)
        val other = DfuTarget("11:22:33:44:55:66", "DfuTarg", -50, DfuKind.SECURE)
        assertEquals(target, run(OtaEvent.TargetChosen(other), OtaEvent.TargetCleared, from = s).target)
        assertEquals(fw, run(OtaEvent.FirmwareCleared, from = s).firmware)
        assertEquals(s, run(OtaEvent.Start, OtaEvent.Reset, from = s))   // kein Doppelstart, kein Reset mitten im Upload
    }

    @Test fun resetClearsEverythingForACleanNextRun() {
        // v1.26 Live-Test: Zurücksetzen leert Paket, Ziel, Fortschritt und Status vollständig
        val done = run(OtaEvent.Start, OtaEvent.Progress(100, 3f, 3f), OtaEvent.Completed, from = ready)
        assertEquals(OtaState(), run(OtaEvent.Reset, from = done))
        val failed = run(OtaEvent.Start, OtaEvent.Failed("x"), from = ready)
        val r = run(OtaEvent.Reset, from = failed)
        assertEquals(OtaPhase.IDLE, r.phase); assertNull(r.firmware); assertNull(r.target); assertNull(r.message); assertEquals(0, r.percent); assertEquals(0, r.stoppedAtStep)
        assertFalse(r.canStart)
    }

    @Test fun startFailureBeforeLibraryRunsIsAnError() {
        // z. B. DfuServiceInitiator wirft – Fehler auch aus IDLE sichtbar
        assertEquals(OtaPhase.ERROR, run(OtaEvent.Failed("no service"), from = ready).phase)
    }
}
