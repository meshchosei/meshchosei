package com.meshcore.repeater.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
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

/** Deckkraft des Speichern-Buttons ohne ungespeicherte Änderungen (STYLE_GUIDE §2.2 "Geändert vs. unverändert"). */
const val SAVED_STATE_ALPHA = 0.38f

/**
 * Speichern-/Senden-Button mit Arbeitszustand: Während [busy] ist der Button gesperrt (kein Doppelklick),
 * bleibt aber in seiner Signalfarbe (nicht grau) und zeigt den rotierenden Braille-Spinner samt "Speichere…".
 *
 * [hasUnsavedChanges] = false: alles gespeichert -> gedecktes Rot ([SAVED_STATE_ALPHA]) und gesperrt;
 * true: volles Signalrot mit weißer Fettschrift. Standard true (Senden-Aktionen ohne Änderungsbegriff).
 */
@Composable
fun BusyButton(
    busy: Boolean,
    onClick: () -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hasUnsavedChanges: Boolean = true,
    contentColor: Color = Color.White,
    shape: Shape = BauhausCornerShape,
    busyText: String? = tr("Speichere…"),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = { if (!busy) onClick() },
        enabled = enabled && hasUnsavedChanges && !busy,
        modifier = modifier,
        shape = shape,
        contentPadding = contentPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor, contentColor = contentColor,
            // Arbeitszustand: gesperrt, aber sichtbar "aktiv"; echtes Disabled (enabled = false) bleibt gedämpft
            disabledContainerColor = if (busy) containerColor.copy(alpha = 0.85f) else containerColor.copy(alpha = SAVED_STATE_ALPHA),
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

/** Feste Größe des Hinzufügen-Buttons neben einem Eingabefeld (= Höhe eines OutlinedTextField), v1.23. */
val FixedActionButtonSize: Dp = 56.dp

/**
 * Quadratischer Icon-Button mit Arbeitszustand (v1.23, STYLE_GUIDE §2.2): Größe ändert sich NIE – statt eines
 * Textwechsels („Speichere…“) zeigt er im Arbeitszustand nur die Braille-Punkte. Gesperrt, solange [busy].
 */
@Composable
fun FixedBusyIconButton(
    busy: Boolean,
    onClick: () -> Unit,
    containerColor: Color,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.White,
    enabled: Boolean = true
) {
    Box(
        modifier
            .size(FixedActionButtonSize)
            .clip(BauhausCornerShape)
            .background(if (enabled || busy) containerColor else containerColor.copy(alpha = SAVED_STATE_ALPHA))
            .clickable(enabled = enabled && !busy, role = Role.Button, onClickLabel = contentDescription) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (busy) BrailleSpinner(contentColor, fontSize = 20.sp)
        else Icon(icon, contentDescription = contentDescription, tint = contentColor)
    }
}

/** Löschen-Button einer Listenzeile (48 dp, unverändert groß): während des Löschens dieser Zeile Braille-Punkte statt Icon. */
@Composable
fun DeleteBusyIconButton(busy: Boolean, enabled: Boolean, onClick: () -> Unit, icon: ImageVector, contentDescription: String, tint: Color) {
    IconButton(enabled = enabled && !busy, onClick = onClick) {
        if (busy) BrailleSpinner(tint, fontSize = 18.sp)
        else Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}
