package com.meshcore.repeater.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.meshcore.repeater.model.EdgeLightTrigger
import com.meshcore.repeater.model.EdgeSide
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/** Reine Regeln des Türkanten-Lichtscheins (v1.24), unit-getestet. [p] = Animationsfortschritt 0..1. */
object EdgeLightRules {
    const val DURATION_MS = 1500
    /** Höchste Deckkraft des Lichtkegels direkt an der Kante (läuft nach innen auf 0). */
    const val MAX_ALPHA = 0.92f
    /** Höchste Deckkraft der hellen Kernlinie an der äußersten Kante. */
    const val LINE_ALPHA = 0.95f

    /**
     * Fortschritt nach [elapsedMs] Echtzeit, mit `FastOutSlowInEasing`. Bewusst ohne Compose-`Animatable`: Das würde
     * die System-Animationsskala übernehmen („Animator-Dauer aus“ / Energiesparmodus) und sofort ans Ende springen –
     * dann wäre auf dem Gerät nichts zu sehen.
     */
    fun progressAt(elapsedMs: Float): Float =
        if (elapsedMs <= 0f) 0f else FastOutSlowInEasing.transform((elapsedMs / DURATION_MS).coerceAtMost(1f))

    /** Helligkeit: sanft auf, sanft ab (Halbwelle), außerhalb 0..1 aus. */
    fun envelope(p: Float): Float = if (p <= 0f || p >= 1f) 0f else sin(PI * p).toFloat()

    /** Mitte des Lichtkegels in Bildhöhe (0 = oben): streicht von 30 % nach 70 % die Kante entlang. */
    fun sweepCenter(p: Float): Float = 0.30f + 0.40f * p.coerceIn(0f, 1f)
}

private val GlowWidth = 64.dp
private val LineWidth = 3.dp

/**
 * Lichtspalt-Overlay „Taschenlampe hinter einer geschlossenen Tür“ (STYLE_GUIDE §2.13). Gehört als letztes Kind in
 * die oberste Root-Box (`Modifier.fillMaxSize().zIndex(999f)` beim Aufrufer). Kein pointerInput, keine Semantik:
 * fängt keine Touches ab und verschiebt kein Layout. Jedes neue Ereignis (`id > lastPlayedId`) spielt einmal ab.
 */
@Composable
fun EdgeLightEffect(trigger: EdgeLightTrigger?, rightColor: Color, leftColor: Color, isDark: Boolean, modifier: Modifier = Modifier) {
    val right = remember { mutableFloatStateOf(1f) }
    val left = remember { mutableFloatStateOf(1f) }
    var lastPlayedId by rememberSaveable { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    val jobs = remember { HashMap<EdgeSide, Job>() }

    LaunchedEffect(trigger?.id) {
        val t = trigger ?: return@LaunchedEffect
        if (!EdgeLightTrigger.shouldPlay(t, lastPlayedId)) return@LaunchedEffect
        lastPlayedId = t.id
        val progress = if (t.side == EdgeSide.RIGHT) right else left
        // Eigener Job je Seite: ein Ereignis der anderen Seite bricht diese Animation nicht ab
        jobs[t.side]?.cancel()
        jobs[t.side] = scope.launch { playEdgeLight(progress) }
    }

    // Offscreen-Ebene, damit die vertikale Ausblendung (DstIn) nur den Schein maskiert, nicht die App darunter
    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        drawEdgeGlow(EdgeSide.RIGHT, right.floatValue, rightColor, isDark)
        drawEdgeGlow(EdgeSide.LEFT, left.floatValue, leftColor, isDark)
    }
}

/** Ein Durchlauf über die echte Bildzeit (withFrameNanos), unabhängig von der System-Animationsskala. */
private suspend fun playEdgeLight(progress: MutableFloatState) {
    try {
        progress.floatValue = 0f
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val elapsedMs = (now - start) / 1_000_000f
            progress.floatValue = EdgeLightRules.progressAt(elapsedMs)
            if (elapsedMs >= EdgeLightRules.DURATION_MS) break
        }
    } finally {
        progress.floatValue = 1f   // auch bei Abbruch nie halb sichtbar stehen bleiben
    }
}

private fun DrawScope.drawEdgeGlow(side: EdgeSide, p: Float, color: Color, isDark: Boolean) {
    val e = EdgeLightRules.envelope(p)
    if (e <= 0f) return
    val glow = GlowWidth.toPx().coerceAtMost(size.width / 2f)
    val line = LineWidth.toPx()
    val x0 = if (side == EdgeSide.LEFT) 0f else size.width - glow
    val a = e * EdgeLightRules.MAX_ALPHA
    // Waagerecht über die volle Bildhöhe: am Rand kräftige Kantenfarbe, ins Bild hinein komplett transparent
    val stops = arrayOf(
        0f to color.copy(alpha = a),
        0.30f to color.copy(alpha = a * 0.55f),
        0.65f to color.copy(alpha = a * 0.18f),
        1f to Color.Transparent
    )
    val brush = if (side == EdgeSide.LEFT) Brush.horizontalGradient(*stops, startX = 0f, endX = glow)
                else Brush.horizontalGradient(*stops, startX = size.width, endX = size.width - glow)
    drawRect(brush, topLeft = Offset(x0, 0f), size = Size(glow, size.height))
    // Helle Kernlinie direkt an der äußersten Kante (links x = 0, rechts bis x = size.width). Im Hellmodus
    // wäre reines Weiß auf weißem Grund unsichtbar – dort leuchtet sie als aufgehellte Kantenfarbe.
    val lineColor = if (isDark) Color.White else lerp(color, Color.White, 0.25f)
    val lx = if (side == EdgeSide.LEFT) 0f else size.width - line
    drawRect(lineColor.copy(alpha = e * EdgeLightRules.LINE_ALPHA), topLeft = Offset(lx, 0f), size = Size(line, size.height))
    // Senkrecht: Kegel um die wandernde Mitte, nach oben und unten weich ausgeblendet
    val c = EdgeLightRules.sweepCenter(p)
    val mask = Brush.verticalGradient(
        0f to Color.Transparent,
        (c - 0.38f).coerceIn(0.005f, 0.98f) to Color.Transparent,
        c to Color.Black,
        (c + 0.38f).coerceIn(0.02f, 0.995f) to Color.Transparent,
        1f to Color.Transparent
    )
    drawRect(mask, topLeft = Offset(x0, 0f), size = Size(glow, size.height), blendMode = BlendMode.DstIn)
}
