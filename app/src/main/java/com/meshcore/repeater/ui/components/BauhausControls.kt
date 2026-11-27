package com.meshcore.repeater.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshcore.repeater.i18n.tr
import com.meshcore.repeater.ui.theme.BauhausCornerShape
import com.meshcore.repeater.ui.theme.LocalBauhausColors

/**
 * Eckiger Bauhaus-Schalter – verbindlicher Ersatz für den runden Material-`Switch` (STYLE_GUIDE.md §2.3).
 *
 * Rechteckige Spur 44 × 24 dp und quadratischer Schieber, beide in [BauhausCornerShape] (2 dp),
 * 1.5 dp Rahmenlinie. Aktiv: Spur vollflächig in [activeColor] (Standard Signalrot, Kobaltblau für
 * Whitelist/Info), Schieber weiß rechts. Inaktiv: Spur leer mit Outline-Rahmen, Schieber in Outline-Farbe links.
 */
@Composable
fun BauhausSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = LocalBauhausColors.current.signalRed
) {
    val outline = MaterialTheme.colorScheme.outline
    val track by animateColorAsState(if (checked) activeColor else Color.Transparent, label = "bauhausSwitchTrack")
    val knob by animateColorAsState(if (checked) Color.White else outline, label = "bauhausSwitchKnob")
    val knobOffset by animateDpAsState(if (checked) 22.dp else 2.dp, label = "bauhausSwitchOffset")
    val toggle = if (onCheckedChange != null) Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange) else Modifier
    Box(
        modifier = modifier
            .clip(BauhausCornerShape)
            .then(toggle)
            .padding(vertical = 12.dp)             // Touch-Höhe ≈ 48 dp wie beim Material-Switch
            .size(width = 44.dp, height = 24.dp)
            .clip(BauhausCornerShape)
            .background(if (enabled) track else track.copy(alpha = track.alpha * 0.4f))
            .border(1.5.dp, if (checked) activeColor.copy(alpha = if (enabled) 1f else 0.4f) else outline.copy(alpha = if (enabled) 1f else 0.4f), BauhausCornerShape),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier.offset(x = knobOffset).size(20.dp).clip(BauhausCornerShape)
                .background(if (enabled) knob else knob.copy(alpha = 0.4f))
        )
    }
}

/**
 * Einheitliche "Aktualisieren"/"Neu einlesen"-Aktion (STYLE_GUIDE.md §2.4): kompakter, eckiger OutlinedButton
 * mit Refresh-Icon und kurzem Text. Während [busy] dreht der Braille-Spinner, der Button ist gesperrt.
 */
@Composable
fun RefreshButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = tr("Aktualisieren"),
    busy: Boolean = false,
    enabled: Boolean = true,
    color: Color = LocalBauhausColors.current.cobalt
) {
    OutlinedButton(
        onClick = { if (!busy) onClick() },
        enabled = enabled && !busy,
        modifier = modifier.defaultMinSize(minHeight = 32.dp),
        shape = BauhausCornerShape,
        border = BorderStroke(1.dp, if (enabled) color else color.copy(alpha = 0.4f)),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color, disabledContentColor = if (busy) color else color.copy(alpha = 0.4f))
    ) {
        if (busy) BrailleSpinner(color, fontSize = 14.sp)
        else Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/**
 * Rahmenloses Aktualisieren-Icon direkt hinter einem angezeigten Wert (v1.18, z. B. „Versorgung: …  ↻“).
 * Ausnahme zu §2.4 für einzelne Messwerte, die man an Ort und Stelle neu abfragt: kein Rahmen, kein Text.
 * Während der Abfrage ersetzt der Braille-Spinner das Icon, die Fläche ist dann gesperrt.
 * Touch-Fläche 28 dp (eckig), Icon 16 dp.
 */
@Composable
fun InlineRefreshIcon(
    onClick: () -> Unit,
    busy: Boolean = false,
    enabled: Boolean = true,
    contentDescription: String = tr("Aktualisieren"),
    color: Color = LocalBauhausColors.current.cobalt
) {
    Box(
        Modifier.size(28.dp).clip(BauhausCornerShape)
            .clickable(enabled = enabled && !busy, role = Role.Button, onClickLabel = contentDescription) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (busy) BrailleSpinner(color, fontSize = 14.sp)
        else Icon(Icons.Default.Refresh, contentDescription = contentDescription, tint = if (enabled) color else color.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
    }
}

/**
 * Einheitliche Icon-Größe für jedes Schließen-✕ (`Icons.Filled.Close`, v1.21, STYLE_GUIDE §2.12): Klappmenüs,
 * Update-Dialog und Haftungsausschluss. Die antippbare Fläche darum bleibt je nach Ort größer (22 dp Box bzw. IconButton).
 */
val CloseIconSize = 16.dp

/** Feste Höhe der Segment-Buttons (v1.24). */
val SegmentButtonHeight = 36.dp

/**
 * Gleich breiter Segment-Button (v1.24, STYLE_GUIDE §2.2.1): in einer Reihe jeweils mit `Modifier.weight(1f)`, feste
 * Höhe [SegmentButtonHeight], Inhalt zentriert, einzeilig. Während [busy] ersetzt die Braille-Animation **zentriert die
 * Beschriftung** – der Button behält seine Abmessungen exakt, Nachbarn verschieben sich nie. Die Beschriftung bleibt
 * dabei als Bedienhilfe erhalten (Screenreader).
 */
@Composable
fun SegmentButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    color: Color = LocalBauhausColors.current.cobalt
) {
    val outline = if (selected) color else MaterialTheme.colorScheme.outline
    Box(
        modifier
            .height(SegmentButtonHeight)
            .clip(BauhausCornerShape)
            .background(if (selected) color.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, outline, BauhausCornerShape)
            .clickable(role = Role.Button, onClickLabel = text, onClick = onClick)
            .semantics { contentDescription = text; this.selected = selected }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        if (busy) BrailleSpinner(color, fontSize = 14.sp)
        else Text(
            text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
