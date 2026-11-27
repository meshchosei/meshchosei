package com.meshcore.repeater

import com.meshcore.repeater.model.EdgeLightTrigger
import com.meshcore.repeater.model.EdgeRoleWatch
import com.meshcore.repeater.model.EdgeSide
import com.meshcore.repeater.ui.components.EdgeLightRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.24: Türkanten-Lichtschein – Ereignis aus dem ViewModel, Echtzeit-Verlauf. */
class EdgeLightRulesTest {
    @Test fun newTriggerPlaysExactlyOnce() {
        val t = EdgeLightTrigger(id = 5, side = EdgeSide.RIGHT)
        assertTrue(EdgeLightTrigger.shouldPlay(t, lastPlayedId = 0))
        assertFalse("bereits gezeigt (z. B. nach Recomposition)", EdgeLightTrigger.shouldPlay(t, lastPlayedId = 5))
        assertFalse(EdgeLightTrigger.shouldPlay(t, lastPlayedId = 9))
        assertFalse("noch kein Ereignis", EdgeLightTrigger.shouldPlay(null, lastPlayedId = 0))
    }

    /** id = System.nanoTime(): zwei Ereignisse hintereinander (Verbinden, dann Login) sind nie gleich. */
    @Test fun nanoTimeIdsNeverRepeat() {
        val a = EdgeLightTrigger(System.nanoTime(), EdgeSide.RIGHT)
        val b = EdgeLightTrigger(System.nanoTime(), EdgeSide.LEFT)
        assertTrue(b.id >= a.id)
        assertTrue("gleiche Seite, neues Ereignis -> erneut abspielen", EdgeLightTrigger.shouldPlay(EdgeLightTrigger(a.id + 1, EdgeSide.RIGHT), a.id))
    }

    /** Echtzeit-Verlauf: unabhängig von der System-Animationsskala, nach 1,5 s fertig. */
    @Test fun progressFollowsRealTime() {
        assertEquals(0f, EdgeLightRules.progressAt(0f), 0f)
        assertEquals(1f, EdgeLightRules.progressAt(1500f), 1e-4f)
        assertEquals(1f, EdgeLightRules.progressAt(5000f), 0f)
        val mid = EdgeLightRules.progressAt(750f)
        assertTrue("FastOutSlowIn: zur Halbzeit schon über der Hälfte", mid > 0.5f && mid < 1f)
        assertTrue("mitten im Durchlauf sichtbar", EdgeLightRules.envelope(mid) > 0.3f)
    }

    @Test fun envelopeRisesAndFadesOut() {
        assertEquals(0f, EdgeLightRules.envelope(0f), 0f)
        assertEquals(0f, EdgeLightRules.envelope(1f), 0f)
        assertEquals(1f, EdgeLightRules.envelope(0.5f), 1e-4f)
        assertEquals("Ruhezustand zeichnet nichts", 0f, EdgeLightRules.envelope(1.2f), 0f)
    }

    @Test fun coneSweepsDownTheEdgeWithStrongLight() {
        assertEquals(0.30f, EdgeLightRules.sweepCenter(0f), 1e-4f)
        assertEquals(0.70f, EdgeLightRules.sweepCenter(1f), 1e-4f)
        assertEquals(1500, EdgeLightRules.DURATION_MS)
        assertEquals(0.92f, EdgeLightRules.MAX_ALPHA, 0f)
    }

    /**
     * v1.25: Maßgeblich ist das erkannte Gerät, nicht der Transport. Direkter USB-Repeater -> links (grün);
     * Companion-Node (USB oder BLE) -> rechts (blau); anschließender Remote-Admin-Login auf einen Repeater -> links.
     */
    @Test fun sideFollowsDetectedDeviceNotTransport() {
        assertEquals("direkte USB-Verbindung zu einem Repeater", EdgeSide.LEFT,
            EdgeLightTrigger.sideForConnectedDevice(isDirectRepeater = true, isCompanion = false))
        assertEquals("Verbindung mit einem Companion-Node", EdgeSide.RIGHT,
            EdgeLightTrigger.sideForConnectedDevice(isDirectRepeater = false, isCompanion = true))
        assertEquals("danach Remote-Admin-Login auf einen Repeater", EdgeSide.LEFT, EdgeLightTrigger.SIDE_FOR_REPEATER_LOGIN)
        assertEquals("Transport verbunden, Gerät noch nicht erkannt -> kein Schein", null,
            EdgeLightTrigger.sideForConnectedDevice(isDirectRepeater = false, isCompanion = false))
        assertEquals("direkter Repeater hat Vorrang", EdgeSide.LEFT,
            EdgeLightTrigger.sideForConnectedDevice(isDirectRepeater = true, isCompanion = true))
    }

    /** Beobachter: genau ein Schein pro Verbindung, erst wenn Verbindung steht UND Rolle feststeht. */
    @Test fun roleWatchFiresOncePerConnectionAfterRoleIsKnown() {
        val w = EdgeRoleWatch()
        // USB-Repeater direkt: verbunden, Erkennung läuft -> nichts; dann direkter Repeater -> links
        assertEquals(null, w.onState(connected = true, isDirectRepeater = false, isCompanion = false))
        assertEquals(EdgeSide.LEFT, w.onState(true, isDirectRepeater = true, isCompanion = false))
        assertEquals("weitere Zustandswechsel derselben Verbindung -> kein zweiter Schein", null, w.onState(true, true, false))
        // Trennen, dann Companion-Node per USB/BLE -> rechts
        assertEquals(null, w.onState(connected = false, isDirectRepeater = false, isCompanion = false))
        assertEquals(EdgeSide.RIGHT, w.onState(true, isDirectRepeater = false, isCompanion = true))
        assertEquals(null, w.onState(true, false, true))
        // ohne Verbindung nie ein Schein, auch mit alter Rolle
        assertEquals(null, w.onState(false, isDirectRepeater = true, isCompanion = false))
    }
}
