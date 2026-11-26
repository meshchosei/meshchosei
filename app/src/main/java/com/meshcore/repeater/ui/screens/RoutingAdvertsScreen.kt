package com.meshcore.repeater.ui.screens

import com.meshcore.repeater.ui.components.hintedVerticalScroll

import com.meshcore.repeater.ui.components.BauhausSwitch
import com.meshcore.repeater.ui.components.InlineRefreshIcon

import com.meshcore.repeater.i18n.tr
import com.meshcore.repeater.ui.components.LocalTourMode
import com.meshcore.repeater.ui.components.RegisterTourSave
import com.meshcore.repeater.ui.components.BusyButton
import com.meshcore.repeater.ui.components.BusyOutlinedButton
import com.meshcore.repeater.model.RegionParser
import com.meshcore.repeater.model.RegionState
import com.meshcore.repeater.viewmodel.BusyKey
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import com.meshcore.repeater.ui.components.SAVED_STATE_ALPHA
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshcore.repeater.ui.theme.BauhausCornerShape
import com.meshcore.repeater.ui.theme.LocalBauhausColors
import com.meshcore.repeater.viewmodel.RepeaterViewModel
import com.meshcore.repeater.viewmodel.SetupChange

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoutingAdvertsScreen(viewModel: RepeaterViewModel) {
    val bColors = LocalBauhausColors.current
    val setup by viewModel.basicSetup.collectAsState()
    // "Gespeichert"-Basis liegt im ViewModel und wird erst nach Bestätigung durch den Repeater aktualisiert
    val baseline by viewModel.nodeBaseline.collectAsState()
    var showRebootConfirm by remember { mutableStateOf(false) }
    val busy by viewModel.busyActions.collectAsState()

    val pendingChanges = remember(setup, baseline) {
        buildList {
            if (setup.repeatEnabled != baseline.repeatEnabled) add(SetupChange("set repeat ${if (setup.repeatEnabled) "on" else "off"}") { it.copy(repeatEnabled = setup.repeatEnabled) })
            if (setup.pathHashMode != baseline.pathHashMode) add(SetupChange("set path.hash.mode ${setup.pathHashMode}") { it.copy(pathHashMode = setup.pathHashMode) })
            if (setup.loopDetect.isNotBlank() && setup.loopDetect != baseline.loopDetect) add(SetupChange("set loop.detect ${setup.loopDetect.trim()}") { it.copy(loopDetect = setup.loopDetect) })
            if (setup.advertIntervalMin.isNotBlank() && setup.advertIntervalMin != baseline.advertIntervalMin) add(SetupChange("set advert.interval ${setup.advertIntervalMin.trim()}") { it.copy(advertIntervalMin = setup.advertIntervalMin) })
            if (setup.floodAdvertIntervalHours.isNotBlank() && setup.floodAdvertIntervalHours != baseline.floodAdvertIntervalHours) add(SetupChange("set flood.advert.interval ${setup.floodAdvertIntervalHours.trim()}") { it.copy(floodAdvertIntervalHours = setup.floodAdvertIntervalHours) })
            if (setup.txDelaySec.isNotBlank() && setup.txDelaySec != baseline.txDelaySec && setup.txDelaySec != "adaptive") add(SetupChange("set txdelay ${setup.txDelaySec.trim()}") { it.copy(txDelaySec = setup.txDelaySec) })
            if (setup.multiAcks != baseline.multiAcks) add(SetupChange("set multi.acks ${setup.multiAcks}") { it.copy(multiAcks = setup.multiAcks) })
        }
    }

    val doSave = { viewModel.applyBasicSetupChanges(pendingChanges, BusyKey.ROUTING) }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).hintedVerticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth(), shape = BauhausCornerShape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("ROUTING & ADVERTS"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = bColors.cobalt)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tr("Repeater-Weiterleitung aktiv (set repeat)"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    BauhausSwitch(checked = setup.repeatEnabled, onCheckedChange = { viewModel.updateBasicSetupDraft(setup.copy(repeatEnabled = it)) })
                }
                Text(tr("Pfad-Hash-Modus (Node-ID Breite):"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "1-Byte (0)", 1 to "2-Byte (1)", 2 to "3-Byte (2)").forEach { (mode, label) ->
                        FilterChip(selected = setup.pathHashMode == mode, onClick = { viewModel.updateBasicSetupDraft(setup.copy(pathHashMode = mode)) }, label = { Text(label, fontSize = 11.sp) }, shape = BauhausCornerShape)
                    }
                }
                Text(tr("Schleifen-Erkennung (loop.detect):"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("off", "minimal", "moderate", "strict").forEach { mode ->
                        FilterChip(selected = setup.loopDetect.equals(mode, true), onClick = { viewModel.updateBasicSetupDraft(setup.copy(loopDetect = mode)) }, label = { Text(mode, fontSize = 11.sp) }, shape = BauhausCornerShape)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = setup.advertIntervalMin, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(advertIntervalMin = it)) }, label = { Text(tr("Advert (Min)"), fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                    OutlinedTextField(value = setup.floodAdvertIntervalHours, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(floodAdvertIntervalHours = it)) }, label = { Text(tr("Flood-Adv (Std)"), fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tr("Mehrfache Empfangsbestätigungen (multi.acks = 1)"), fontSize = 12.sp)
                    BauhausSwitch(checked = setup.multiAcks == 1, onCheckedChange = { viewModel.updateBasicSetupDraft(setup.copy(multiAcks = if (it) 1 else 0)) })
                }
                // "Speichern" gehört ausschließlich zu dieser Karte (Routing/Adverts). Die Regionen-Liste darunter
                // sendet ihre CLI-Befehle direkt. Im Setup-Tour-Modus übernimmt "Speichern & Weiter →" in der Tour-Leiste.
                if (!LocalTourMode.current) {
                    BusyButton(
                        busy = BusyKey.ROUTING in busy, onClick = { doSave() }, hasUnsavedChanges = pendingChanges.isNotEmpty(),
                        containerColor = bColors.signalRed, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Speichern"), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        RegisterTourSave { if (pendingChanges.isNotEmpty()) doSave() }

        val regionState by viewModel.regionState.collectAsState()
        RegionsSection(viewModel, regionState, BusyKey.REGION in busy)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            BusyOutlinedButton(
                busy = BusyKey.ADVERT in busy, onClick = { viewModel.sendCommand("advert", busyKey = BusyKey.ADVERT) },
                color = bColors.cobalt, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), busyText = tr("Sende…")
            ) {
                Text(tr("Advert senden"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = bColors.cobalt)
            }
            OutlinedButton(modifier = Modifier.weight(1f), shape = BauhausCornerShape, border = BorderStroke(1.dp, bColors.signalRed), onClick = { showRebootConfirm = true }) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, tint = bColors.signalRed, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(tr("Neu starten"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = bColors.signalRed)
            }
        }
    }

    if (showRebootConfirm) {
        AlertDialog(
            onDismissRequest = { showRebootConfirm = false }, shape = BauhausCornerShape,
            title = { Text(tr("Repeater neu starten?"), fontWeight = FontWeight.Bold, color = bColors.signalRed) },
            text = { Text(tr("Der Repeater wird neu gestartet, um alle gespeicherten Einstellungen zu übernehmen.")) },
            confirmButton = { Button(shape = BauhausCornerShape, colors = ButtonDefaults.buttonColors(containerColor = bColors.signalRed), onClick = { showRebootConfirm = false; viewModel.sendCommand("reboot", manualCli = false) }) { Text(tr("Neu starten"), color = Color.White, fontWeight = FontWeight.Bold) } },
            dismissButton = { OutlinedButton(shape = BauhausCornerShape, onClick = { showRebootConfirm = false }) { Text(tr("Abbrechen")) } }
        )
    }
}

/**
 * KONFIGURIERTE REGIONEN (MeshCore/ZephCore "region …") – eine einzige Karte:
 * je Region eine Zeile mit eckigen Bauhaus-Schaltern [HOME] [DEF] [FLOOD] + Löschen; die letzte Zeile ([RegionAddRow],
 * signalroter Rahmen) legt eine neue Region an.
 * Jede Aktion wird sofort gesendet (put/allowf/denyf/remove/home + "region save"; "region default" speichert selbst)
 * und danach der Regionen-Stand neu eingelesen.
 */
@Composable
private fun RegionsSection(viewModel: RepeaterViewModel, state: RegionState, busy: Boolean) {
    val bColors = LocalBauhausColors.current
    var newName by remember { mutableStateOf("") }
    var newParent by remember { mutableStateOf("*") }
    var confirmRemove by remember { mutableStateOf<String?>(null) }

    Card(modifier = Modifier.fillMaxWidth(), shape = BauhausCornerShape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("KONFIGURIERTE REGIONEN"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = bColors.cobalt, modifier = Modifier.weight(1f))
                InlineRefreshIcon(onClick = { viewModel.refreshRegions() }, busy = busy, contentDescription = tr("Aktualisieren"))
            }
            when (state.supported) {
                null -> { Text(tr("Regionen noch nicht gelesen – oben auf ↻ tippen."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); return@Column }
                false -> { Text(tr("Diese Firmware unterstützt keine Regionen (Befehl \"region\" unbekannt)."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); return@Column }
                true -> Unit
            }

            // Wildcard "*": nur Flood (Home/Default/Löschen gibt es für die Wurzel nicht)
            RegionRow(name = "*", parent = null, isHome = false, isDefault = false, flood = state.wildcardFlood, busy = busy, wildcard = true,
                onHome = {}, onDefault = {}, onFlood = { viewModel.setRegionFlood("*", !state.wildcardFlood) }, onRemove = {})
            // Jede Region genau einmal (Parser dedupliziert bereits; hier zusätzlich als Absicherung der Anzeige)
            state.regions.distinctBy { it.name }.forEach { r ->
                val isHome = state.home == r.name; val isDef = state.default == r.name
                RegionRow(
                    name = r.name, parent = r.parent, isHome = isHome, isDefault = isDef, flood = r.floodAllowed, busy = busy,
                    onHome = { viewModel.setHomeRegion(if (isHome) "*" else r.name) },          // erneut tippen = Home zurück auf "*"
                    onDefault = { viewModel.setDefaultRegion(if (isDef) null else r.name) },     // erneut tippen = "<null>"
                    onFlood = { viewModel.setRegionFlood(r.name, !r.floodAllowed) },
                    onRemove = { confirmRemove = r.name }
                )
            }
            if (state.regions.isEmpty()) Text(tr("Noch keine eigenen Regionen."), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Abschlusszeile der Liste: neue Region anlegen ("region put" erlaubt Flood standardmäßig)
            RegionAddRow(
                name = newName, parent = newParent, busy = busy,
                onNameChange = { newName = it.filterNot(Char::isWhitespace).take(RegionParser.MAX_NAME_LEN) },
                onParentChange = { newParent = it.filterNot(Char::isWhitespace).take(RegionParser.MAX_NAME_LEN) },
                onAdd = { viewModel.addRegion(newName, newParent.ifBlank { "*" }, flood = true); newName = "" }
            )
        }
    }

    confirmRemove?.let { name ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null }, shape = BauhausCornerShape,
            title = { Text(tr("Region \"{0}\" löschen?", name), fontWeight = FontWeight.Bold, color = bColors.signalRed) },
            text = { Text(tr("Regionen mit Unterregionen lehnt die Firmware ab (\"Err - not empty\")."), fontSize = 13.sp) },
            confirmButton = { Button(shape = BauhausCornerShape, colors = ButtonDefaults.buttonColors(containerColor = bColors.signalRed), onClick = { confirmRemove = null; viewModel.removeRegion(name) }) { Text(tr("Löschen"), color = Color.White, fontWeight = FontWeight.Bold) } },
            dismissButton = { OutlinedButton(shape = BauhausCornerShape, onClick = { confirmRemove = null }) { Text(tr("Abbrechen")) } }
        )
    }
}

@Composable
private fun RegionRow(
    name: String, parent: String?, isHome: Boolean, isDefault: Boolean, flood: Boolean, busy: Boolean, wildcard: Boolean = false,
    onHome: () -> Unit, onDefault: () -> Unit, onFlood: () -> Unit, onRemove: () -> Unit
) {
    val bColors = LocalBauhausColors.current
    Row(
        modifier = Modifier.fillMaxWidth().clip(BauhausCornerShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, BauhausCornerShape)
            .padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!parent.isNullOrBlank() && parent != "*") Text("↳ $parent", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!wildcard) {
            BauhausToggle(tr("HOME"), isHome, bColors.signalRed, enabled = !busy, onClick = onHome)
            BauhausToggle(tr("DEF"), isDefault, bColors.cobalt, enabled = !busy, onClick = onDefault)
        }
        BauhausToggle(tr("FLOOD"), flood, bColors.ochre, activeText = Color.Black, enabled = !busy, onClick = onFlood)
        if (!wildcard) {
            IconButton(enabled = !busy, onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = tr("Löschen"), tint = if (busy) MaterialTheme.colorScheme.outline else bColors.signalRed, modifier = Modifier.size(20.dp))
            }
        } else Spacer(Modifier.width(36.dp))
    }
}

/**
 * Abschlusszeile der Regionen-Liste im identischen Zeilen-Layout von [RegionRow] (gleiche Paddings, 1-dp-Rahmen
 * in Signalrot): links Name (~70 %) und Parent (~30 %, vorbelegt "*"), rechts an der Position des Mülleimer-Icons
 * der eckige signalrote "+"-Button. Kein eigener Spinner – den Ladezustand zeigt nur "Aktualisieren".
 */
@Composable
private fun RegionAddRow(
    name: String, parent: String, busy: Boolean,
    onNameChange: (String) -> Unit, onParentChange: (String) -> Unit, onAdd: () -> Unit
) {
    val bColors = LocalBauhausColors.current
    val nameOk = name.isBlank() || RegionParser.isValidName(name)
    val parentOk = parent.isBlank() || parent.trim() == "*" || RegionParser.isValidName(parent)
    val canAdd = !busy && name.isNotBlank() && nameOk && parentOk
    Row(
        modifier = Modifier.fillMaxWidth().clip(BauhausCornerShape)
            .border(BorderStroke(1.dp, bColors.signalRed), BauhausCornerShape)
            .padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RegionInputField(name, onNameChange, tr("Neue Region"), isError = !nameOk, enabled = !busy, modifier = Modifier.weight(0.7f))
        RegionInputField(parent, onParentChange, tr("Parent"), isError = !parentOk, enabled = !busy, modifier = Modifier.weight(0.3f))
        // Gleiche 36-dp-Zelle wie das Mülleimer-Icon der Regionen-Zeilen
        Box(
            modifier = Modifier.size(36.dp).clickable(enabled = canAdd, role = Role.Button, onClick = onAdd)
                .semantics { contentDescription = tr("Hinzufügen") },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size(28.dp).clip(BauhausCornerShape)
                    .background(if (canAdd) bColors.signalRed else bColors.signalRed.copy(alpha = SAVED_STATE_ALPHA)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
    }
}

/** Kompaktes, eckiges Eingabefeld (28 dp hoch, Monospace) für [RegionAddRow]. */
@Composable
private fun RegionInputField(value: String, onValueChange: (String) -> Unit, placeholder: String, isError: Boolean, enabled: Boolean, modifier: Modifier) {
    val bColors = LocalBauhausColors.current
    val textColor = if (isError) bColors.signalRed else MaterialTheme.colorScheme.onSurface
    BasicTextField(
        value = value, onValueChange = onValueChange, enabled = enabled, singleLine = true,
        textStyle = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = textColor),
        cursorBrush = SolidColor(bColors.signalRed),
        keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Next),
        modifier = modifier.height(28.dp),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxSize().clip(BauhausCornerShape)
                    .border(if (isError) 2.dp else 1.dp, if (isError) bColors.signalRed else MaterialTheme.colorScheme.outline, BauhausCornerShape)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) Text(placeholder, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                inner()
            }
        }
    )
}

/** Eckiger Bauhaus-Schalter (2 dp, BauhausCornerShape) statt rundem Material-Switch: aktiv = vollflächig in Signalfarbe. */
@Composable
private fun BauhausToggle(label: String, active: Boolean, color: Color, activeText: Color = Color.White, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier.height(28.dp).widthIn(min = 44.dp).clip(BauhausCornerShape)
            .background(if (active) color else Color.Transparent)
            .border(1.dp, if (active) color else MaterialTheme.colorScheme.outline, BauhausCornerShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
            color = when { !enabled -> MaterialTheme.colorScheme.outline; active -> activeText; else -> MaterialTheme.colorScheme.onSurfaceVariant })
    }
}
