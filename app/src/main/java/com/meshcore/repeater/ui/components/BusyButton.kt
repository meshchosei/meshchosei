package com.meshcore.repeater.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshcore.repeater.i18n.tr
import com.meshcore.repeater.ui.theme.BauhausCornerShape
import kotlinx.coroutines.delay

/** Einheitlicher Braille-Punkte-Spinner (STYLE_GUIDE.md §3 "Arbeitszustand"). */
val BrailleSpinnerFrames = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")

@Composable
fun BrailleSpinner(color: Color, fontSize: TextUnit = 16.sp) {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(80L); frame = (frame + 1) % BrailleSpinnerFrames.size } }
    Text(BrailleSpinnerFrames[frame], color = color, fontSize = fontSize, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
}

/** Button-Inhalt: im Arbeitszustand Spinner + "Speichere…", sonst [idle]. */
@Composable
fun RowScope.BusyContent(busy: Boolean, color: Color, busyText: String? = tr("Speichere…"), fontSize: TextUnit = 14.sp, idle: @Composable RowScope.() -> Unit) {
    if (busy) {
        BrailleSpinner(color, fontSize = (fontSize.value + 2f).sp)
        if (!busyText.isNullOrEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(busyText, color = color, fontWeight = FontWeight.Bold, fontSize = fontSize, maxLines = 1)
        }
    } else idle()
}

/**
 * Speichern-/Senden-Button mit Arbeitszustand: Während [busy] ist der Button gesperrt (kein Doppelklick),
 * bleibt aber in seiner Signalfarbe (nicht grau) und zeigt den rotierenden Braille-Spinner samt "Speichere…".
 */
@Composable
fun BusyButton(
    busy: Boolean,
    onClick: () -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = Color.White,
    shape: Shape = BauhausCornerShape,
    busyText: String? = tr("Speichere…"),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = { if (!busy) onClick() },
        enabled = enabled && !busy,
        modifier = modifier,
        shape = shape,
        contentPadding = contentPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor, contentColor = contentColor,
            // Arbeitszustand: gesperrt, aber sichtbar "aktiv"; echtes Disabled (enabled = false) bleibt gedämpft
            disabledContainerColor = if (busy) containerColor.copy(alpha = 0.85f) else containerColor.copy(alpha = 0.35f),
            disabledContentColor = if (busy) contentColor else contentColor.copy(alpha = 0.7f)
        )
    ) { BusyContent(busy, contentColor, busyText, idle = content) }
}

/** Outline-Variante für sekundäre Senden-Aktionen (z. B. "Advert senden", "Aktualisieren"). */
@Composable
fun BusyOutlinedButton(
    busy: Boolean,
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    border: BorderStroke? = null,
    busyText: String? = tr("Speichere…"),
    fontSize: TextUnit = 12.sp,
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = { if (!busy) onClick() },
        enabled = enabled && !busy,
        modifier = modifier,
        shape = BauhausCornerShape,
        border = border ?: BorderStroke(1.dp, color),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color, disabledContentColor = if (busy) color else color.copy(alpha = 0.4f))
    ) { BusyContent(busy, color, busyText, fontSize = fontSize, idle = content) }
}
