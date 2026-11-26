package com.meshcore.repeater.ui.components

import kotlin.math.abs

/**
 * Regeln des interaktiven Bauhaus-Drawers (v1.22), rein und unit-getestet. Position als Anteil
 * `fraction` (0 = geschlossen, 1 = offen); Strecken in px, Geschwindigkeit in px/s, [density] = px pro dp.
 */
object DrawerGesture {
    /** Slop, ab dem der Drawer eine waagerechte Geste übernimmt. */
    const val SLOP_DP = 12f
    /** Eindeutig waagerecht: |dx| > |dy| · 1,4. */
    const val HORIZONTAL_RATIO = 1.4f
    /** Einrasten ab 35 % Weg (Öffnen wie Schließen). */
    const val SNAP_FRACTION = 0.35f
    /** Zügiges Wischen: ab dieser Geschwindigkeit entscheidet die Richtung. */
    const val FLING_DP_PER_S = 400f

    /**
     * Übernimmt der Drawer die Geste? Nur waagerecht ab dem Slop und nur in eine Richtung, in die er sich
     * bewegen kann (nach rechts, solange nicht ganz offen; nach links, solange nicht ganz zu).
     * [childConsumed]: ein Kind (Slider, Slide-to-Confirm, Karte, vertikaler Scroller) hat die Geste schon -> nie.
     * [startedInExcludedArea]: Finger setzte in einer reservierten Fläche auf (v1.23) -> bei geschlossenem Menü nie.
     */
    fun shouldClaim(dx: Float, dy: Float, density: Float, fraction: Float, childConsumed: Boolean, startedInExcludedArea: Boolean = false): Boolean {
        // Geste begann auf einem Slider o. Ä. (excludeFromDrawerGesture), solange das Menü zu ist -> nie aufziehen
        if (startedInExcludedArea && fraction <= 0f) return false
        if (childConsumed) return false
        if (abs(dx) < SLOP_DP * density || abs(dx) <= abs(dy) * HORIZONTAL_RATIO) return false
        return (dx > 0f && fraction < 1f) || (dx < 0f && fraction > 0f)
    }

    /** Position 1:1 am Finger: Startposition plus zurückgelegter Weg, begrenzt auf 0..1. */
    fun fractionFor(startFraction: Float, dx: Float, widthPx: Float): Float =
        if (widthPx <= 0f) startFraction else (startFraction + dx / widthPx).coerceIn(0f, 1f)

    /**
     * Nach dem Loslassen: true = offen einrasten. Zügiger Wisch entscheidet nach Richtung; sonst ab 35 % Weg
     * in Gestenrichtung (geschlossen gestartet: ab 35 % offen; offen gestartet: ab 35 % nach links zu).
     */
    fun settleOpen(startOpen: Boolean, fraction: Float, velocityPxPerS: Float, density: Float): Boolean {
        val v = velocityPxPerS / density
        if (v > FLING_DP_PER_S) return true
        if (v < -FLING_DP_PER_S) return false
        return if (startOpen) fraction > 1f - SNAP_FRACTION else fraction >= SNAP_FRACTION
    }
}
