package com.meshcore.repeater.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/** Reine Sichtbarkeitsregel des Scroll-Hinweises (STYLE_GUIDE.md §2.9). */
object ScrollHintRule {
    /** Sichtbar, solange weiter nach unten gescrollt werden kann – bei Screens nur im Querformat, bei Dialogen immer. */
    fun visible(canScrollForward: Boolean, isLandscape: Boolean, always: Boolean): Boolean =
        canScrollForward && (always || isLandscape)
}

/**
 * Dezenter Scroll-Hinweis am unteren Rand des sichtbaren Bereichs (STYLE_GUIDE.md §2.9): Fade-Kante in der
 * Hintergrundfarbe, feine 1-dp-Linie und kleiner Pfeil nach unten. Verschwindet (animiert), sobald das Ende
 * erreicht ist (`canScrollForward == false`). Muss in der Modifier-Kette VOR `verticalScroll` stehen, damit
 * am Viewport (nicht am gescrollten Inhalt) gezeichnet wird – am einfachsten über [hintedVerticalScroll].
 *
 * @param always true = auch im Hochformat (Dialoge); false = nur im Querformat (Screens)
 * @param fadeColor Hintergrundfarbe des Containers (Screens: background, Dialoge/Menü: surface)
 */
fun Modifier.scrollHint(state: ScrollState, always: Boolean = false, fadeColor: Color? = null): Modifier = composed {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val show = ScrollHintRule.visible(state.canScrollForward, landscape, always)
    val alpha by animateFloatAsState(if (show) 1f else 0f, label = "scrollHint")
    val fade = fadeColor ?: MaterialTheme.colorScheme.background
    val line = MaterialTheme.colorScheme.outline
    val arrow = MaterialTheme.colorScheme.onSurfaceVariant
    drawWithContent {
        drawContent()
        if (alpha <= 0f) return@drawWithContent
        val fadeH = 28.dp.toPx()
        val top = size.height - fadeH
        drawRect(
            brush = Brush.verticalGradient(listOf(fade.copy(alpha = 0f), fade.copy(alpha = 0.92f * alpha)), startY = top, endY = size.height),
            topLeft = Offset(0f, top), size = Size(size.width, fadeH)
        )
        val y = size.height - 0.5.dp.toPx()
        drawLine(line.copy(alpha = alpha), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        // kleiner Pfeil ↓ (Chevron) mittig über der Linie
        val cx = size.width / 2f; val w = 6.dp.toPx(); val cy = size.height - 9.dp.toPx()
        val p = Path().apply { moveTo(cx - w, cy - w / 2f); lineTo(cx, cy + w / 2f); lineTo(cx + w, cy - w / 2f) }
        drawPath(p, arrow.copy(alpha = alpha), style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Square))
    }
}

/** `scrollHint(state) + verticalScroll(state)` in der richtigen Reihenfolge. */
fun Modifier.hintedVerticalScroll(state: ScrollState, always: Boolean = false, fadeColor: Color? = null): Modifier =
    this.scrollHint(state, always, fadeColor).verticalScroll(state)

