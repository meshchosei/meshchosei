package com.meshcore.repeater.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Dynamische Maximalhöhe von Klappmenüs nach unten (v1.27, STYLE_GUIDE §2.10): Die Liste darf bis knapp vor den
 * unteren Bildschirmrand reichen statt an einer festen Höhe zu enden. Rein und unit-getestet.
 */
object DropdownHeightRules {
    /** Untergrenze, damit die Liste auch bei wenig Platz (Tastatur offen, Querformat) bedienbar bleibt. */
    const val MIN_DP = 120f
    /** Sicherheitsabstand zum unteren Rand (über Navigationsleiste bzw. Tastatur). */
    const val BOTTOM_MARGIN_DP = 16f
    /** Bis die Position bekannt ist (erster Frame), die bisherige feste Höhe. */
    const val FALLBACK_DP = 240f

    /**
     * @param windowHeightPx Fensterhöhe, @param topPx Oberkante der Liste im Fenster (null = noch unbekannt),
     * @param bottomInsetPx Navigationsleiste bzw. Tastatur (das Größere). Ergebnis in dp.
     */
    fun maxHeightDp(windowHeightPx: Int, topPx: Float?, bottomInsetPx: Int, density: Float, marginDp: Float = BOTTOM_MARGIN_DP, minDp: Float = MIN_DP): Float {
        if (topPx == null || density <= 0f || windowHeightPx <= 0) return FALLBACK_DP
        val availablePx = windowHeightPx - topPx - bottomInsetPx - marginDp * density
        return maxOf(minDp, availablePx / density)
    }
}

/**
 * `heightIn(max = …)` bis knapp vor den unteren Bildschirmrand. Wenige Einträge -> die Liste schrumpft auf ihren
 * Inhalt (nur Maximalhöhe, keine feste Höhe); viele Einträge -> sie reicht bis [margin] über Navigationsleiste/Tastatur.
 */
fun Modifier.heightToScreenBottom(margin: Dp = DropdownHeightRules.BOTTOM_MARGIN_DP.dp, min: Dp = DropdownHeightRules.MIN_DP.dp): Modifier = composed {
    val view = LocalView.current
    val density = LocalDensity.current
    var topPx by remember { mutableStateOf<Float?>(null) }
    val bottomInset = maxOf(WindowInsets.navigationBars.getBottom(density), WindowInsets.ime.getBottom(density))
    val maxDp = DropdownHeightRules.maxHeightDp(view.rootView.height, topPx, bottomInset, density.density, margin.value, min.value)
    this.onGloballyPositioned { topPx = it.positionInWindow().y }.heightIn(max = maxDp.dp)
}
