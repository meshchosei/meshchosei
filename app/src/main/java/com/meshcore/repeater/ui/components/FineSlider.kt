package com.meshcore.repeater.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshcore.repeater.ui.theme.BauhausCornerShape
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Reine Werte-Regeln des [FineSlider] (v1.23), unit-getestet. */
object SliderValueRules {
    /** Slider-Position auf das Raster runden (ohne Float-Abschneiden: 34,9999 -> 35) und begrenzen. */
    fun snap(v: Float, min: Int, max: Int, step: Int): Int {
        val s = step.coerceAtLeast(1)
        val snapped = min + ((v - min) / s).roundToInt() * s
        return snapped.coerceIn(min, max)
    }

    /** Feineinstellung `-`/`+` um [delta], begrenzt auf [min]..[fineMax] (darf über das Slider-Ende hinaus). */
    fun fine(value: Int, delta: Int, min: Int, fineMax: Int): Int = (value + delta).coerceIn(min, fineMax)
}

/** Wartezeit nach dem letzten `-`/`+`-Tipp, bevor einmalig gesendet wird (mehrere Tipps = ein Befehl). */
private const val FINE_COMMIT_DELAY_MS = 700L

/**
 * Regler mit lokalem Zustand und Feineinstellung (v1.23, STYLE_GUIDE §2.3.1):
 *  - Während des Ziehens ändert `onValueChange` nur den lokalen Wert – kein Befehl, kein Reload.
 *  - Gesendet wird einmalig beim Loslassen (`onValueChangeFinished`) bzw. ~0,7 s nach dem letzten `-`/`+`.
 *  - Solange gesendet wird ([busy]), überschreibt der alte Wert vom Repeater den lokalen nicht. Danach gilt der
 *    bestätigte Wert [persisted] (bei Fehler also wieder der alte).
 *  - Oben Titel + Wert (feste Breite), darunter `−  [Slider]  +` (rahmenlos, 40-dp-Touch-Fläche) um [fineStep].
 *  - Die Slider-Zeile ist für die Drawer-Geste reserviert (`excludeFromDrawerGesture`).
 * [onCommit] liefert true, wenn wirklich gesendet wurde; sonst springt der Regler auf [persisted] zurück.
 */
@Composable
fun FineSlider(
    label: String,
    persisted: Int,
    min: Int,
    max: Int,
    step: Int,
    busy: Boolean,
    color: Color,
    onCommit: (Int) -> Boolean,
    valueText: (Int) -> String,
    fineStep: Int = 1,
    fineMax: Int = max
) {
    var local by remember { mutableIntStateOf(persisted) }
    var dragging by remember { mutableStateOf(false) }
    var fineTicks by remember { mutableIntStateOf(0) }
    val commit by rememberUpdatedState(onCommit)
    val persistedNow by rememberUpdatedState(persisted)

    // Bestätigter Wert übernimmt nur, wenn weder gezogen noch gesendet wird
    LaunchedEffect(persisted, busy) { if (!busy && !dragging && fineTicks == 0) local = persisted }
    // Feineinstellung: einmal senden, kurz nach dem letzten Tipp
    LaunchedEffect(fineTicks) {
        if (fineTicks == 0) return@LaunchedEffect
        delay(FINE_COMMIT_DELAY_MS)
        val v = local
        fineTicks = 0
        if (v != persistedNow && !commit(v)) local = persistedNow
    }

    Column(Modifier.fillMaxWidth()) {
        // Oben ruhig: Titel links, Wert rechts in fester Breite (nichts verschiebt sich bei 1- bis 5-stelligen Zahlen)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(
                valueText(local), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                color = color, maxLines = 1, textAlign = TextAlign.End,
                modifier = Modifier.width(ValueWidth)
            )
        }
        // Darunter: −  [Slider]  +  – ganze Zeile für die Drawer-Geste reserviert
        Row(Modifier.fillMaxWidth().excludeFromDrawerGesture(), verticalAlignment = Alignment.CenterVertically) {
            FineStepButton("−", enabled = !busy && local > min, color = color, contentDescription = "−$fineStep") {
                local = SliderValueRules.fine(local, -fineStep, min, fineMax); fineTicks++
            }
            Slider(
                value = local.coerceIn(min, max).toFloat(),
                enabled = !busy,
                onValueChange = { dragging = true; local = SliderValueRules.snap(it, min, max, step) },
                onValueChangeFinished = {
                    dragging = false
                    if (local != persisted && !onCommit(local)) local = persisted
                },
                valueRange = min.toFloat()..max.toFloat(),
                colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color),
                modifier = Modifier.weight(1f)
            )
            FineStepButton("+", enabled = !busy && local < fineMax, color = color, contentDescription = "+$fineStep") {
                local = SliderValueRules.fine(local, +fineStep, min, fineMax); fineTicks++
            }
        }
    }
}

/** Feste Breite der Wertanzeige (Platz für „10080“ / „AUS“). */
private val ValueWidth = 64.dp

/** Rahmenloser `−`/`+`-Knopf mit fester 40-dp-Touch-Fläche. */
@Composable
private fun FineStepButton(text: String, enabled: Boolean, color: Color, contentDescription: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(BauhausCornerShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 20.sp, fontFamily = FontFamily.Monospace,
            color = if (enabled) color else color.copy(alpha = 0.38f))
    }
}
