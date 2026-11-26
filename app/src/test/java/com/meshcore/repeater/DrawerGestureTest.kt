package com.meshcore.repeater

import com.meshcore.repeater.ui.components.DrawerGesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.22: interaktiver Bauhaus-Drawer. Dichte 3 px/dp, Menübreite 316 dp. */
class DrawerGestureTest {
    private val d = 3f
    private val w = 316f * d

    @Test fun claimsClearHorizontalGestureFromAnywhere() {
        assertTrue(DrawerGesture.shouldClaim(13f * d, 0f, d, fraction = 0f, childConsumed = false))
        assertTrue("leicht schräg", DrawerGesture.shouldClaim(20f * d, 14f * d, d, 0f, false))
        assertFalse("unter Slop", DrawerGesture.shouldClaim(11f * d, 0f, d, 0f, false))
        assertFalse("zu schräg", DrawerGesture.shouldClaim(20f * d, 15f * d, d, 0f, false))
        assertFalse("vertikal", DrawerGesture.shouldClaim(13f * d, 40f * d, d, 0f, false))
        assertFalse("geschlossen nach links: nichts zu bewegen", DrawerGesture.shouldClaim(-20f * d, 0f, d, 0f, false))
        assertTrue("offen nach links: Zuziehen", DrawerGesture.shouldClaim(-20f * d, 0f, d, 1f, false))
        assertFalse("offen nach rechts", DrawerGesture.shouldClaim(20f * d, 0f, d, 1f, false))
    }

    /** Slider, Slide-to-Confirm, Karte oder Scroller haben die Geste schon -> Drawer bleibt unbeteiligt. */
    @Test fun consumedByChildNeverClaims() {
        assertFalse(DrawerGesture.shouldClaim(100f * d, 0f, d, 0f, childConsumed = true))
        assertFalse(DrawerGesture.shouldClaim(-100f * d, 0f, d, 1f, childConsumed = true))
    }

    @Test fun followsFinger1to1() {
        assertEquals(0.5f, DrawerGesture.fractionFor(0f, w / 2, w), 0.0001f)
        assertEquals(0.25f, DrawerGesture.fractionFor(1f, -0.75f * w, w), 0.0001f)
        assertEquals(1f, DrawerGesture.fractionFor(0f, 2 * w, w), 0.0001f)
        assertEquals(0f, DrawerGesture.fractionFor(0.2f, -w, w), 0.0001f)
        assertEquals("Breite unbekannt", 0f, DrawerGesture.fractionFor(0f, 100f, 0f), 0.0001f)
    }

    @Test fun opensFrom35Percent() {
        assertTrue(DrawerGesture.settleOpen(startOpen = false, fraction = 0.35f, velocityPxPerS = 0f, density = d))
        assertTrue(DrawerGesture.settleOpen(false, 0.45f, 0f, d))   // Material würde hier zurückschnappen
        assertFalse(DrawerGesture.settleOpen(false, 0.30f, 0f, d))
        assertTrue("zügiger Wisch nach rechts", DrawerGesture.settleOpen(false, 0.10f, 500f * d, d))
    }

    @Test fun closesFrom35PercentLeft() {
        assertFalse(DrawerGesture.settleOpen(startOpen = true, fraction = 0.65f, velocityPxPerS = 0f, density = d))
        assertFalse(DrawerGesture.settleOpen(true, 0.50f, 0f, d))
        assertTrue(DrawerGesture.settleOpen(true, 0.70f, 0f, d))
        assertFalse("zügiger Wisch nach links", DrawerGesture.settleOpen(true, 0.95f, -500f * d, d))
    }

    @Test fun constants() {
        assertEquals(0.35f, DrawerGesture.SNAP_FRACTION, 0.0001f)
        assertEquals(1.4f, DrawerGesture.HORIZONTAL_RATIO, 0.0001f)
        assertEquals(12f, DrawerGesture.SLOP_DP, 0.0001f)
    }
}
