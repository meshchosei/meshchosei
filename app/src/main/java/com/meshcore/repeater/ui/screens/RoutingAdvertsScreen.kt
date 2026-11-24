package com.meshcore.repeater.ui.screens

import com.meshcore.repeater.i18n.tr
import com.meshcore.repeater.ui.components.LocalTourMode
import com.meshcore.repeater.ui.components.RegisterTourSave
import com.meshcore.repeater.ui.components.BusyButton
import com.meshcore.repeater.ui.components.BusyOutlinedButton
import com.meshcore.repeater.model.RegionParser
import com.meshcore.repeater.model.RegionState
import com.meshcore.repeater.viewmodel.BusyKey
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.draw.clip

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
            if (setup.ledsMode.isNotBlank() && setup.ledsMode != baseline.ledsMode) add(SetupChange("set leds ${setup.ledsMode.trim()}") { it.copy(ledsMode = setup.ledsMode) })
            if (setup.advertIntervalMin.isNotBlank() && setup.advertIntervalMin != baseline.advertIntervalMin) add(SetupChange("set advert.interval ${setup.advertIntervalMin.trim()}") { it.copy(advertIntervalMin = setup.advertIntervalMin) })
            if (setup.floodAdvertIntervalHours.isNotBlank() && setup.floodAdvertIntervalHours != baseline.floodAdvertIntervalHours) add(SetupChange("set flood.advert.interval ${setup.floodAdvertIntervalHours.trim()}") { it.copy(floodAdvertIntervalHours = setup.floodAdvertIntervalHours) })
            if (setup.txDelaySec.isNotBlank() && setup.txDelaySec != baseline.txDelaySec && setup.txDelaySec != "adaptive") add(SetupChange("set txdelay ${setup.txDelaySec.trim()}") { it.copy(txDelaySec = setup.txDelaySec) })
            if (setup.multiAcks != baseline.multiAcks) add(SetupChange("set multi.acks ${setup.multiAcks}") { it.copy(multiAcks = setup.multiAcks) })
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth(), shape = BauhausCornerShape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("ROUTING, ADVERTS & STATUS-LEDS"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = bColors.cobalt)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tr("Repeater-Weiterleitung aktiv (set repeat)"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Switch(checked = setup.repeatEnabled, onCheckedChange = { viewModel.updateBasicSetupDraft(setup.copy(repeatEnabled = it)) }, colors = SwitchDefaults.colors(checkedTrackColor = bColors.signalRed))
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
                Text(tr("Status-LEDs (set leds):"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("on", "off", "boot").forEach { mode ->
                        FilterChip(selected = setup.ledsMode.equals(mode, true), onClick = { viewModel.updateBasicSetupDraft(setup.copy(ledsMode = mode)) }, label = { Text(mode, fontSize = 11.sp) }, shape = BauhausCornerShape)
                    }
                }
                // ZephCore kennt nur on/off/default, Standard-MeshCore hat kein "set leds" – "boot" ist eine Custom-Erweiterung
                Text(
                    tr("Hinweis: \"boot\" ist eine Custom-Firmware-Erweiterung und wird von Standard-Firmware ggf. nicht unterstützt."),
                    fontSize = 10.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = setup.advertIntervalMin, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(advertIntervalMin = it)) }, label = { Text(tr("Advert (Min)"), fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                    OutlinedTextField(value = setup.floodAdvertIntervalHours, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(floodAdvertIntervalHours = it)) }, label = { Text(tr("Flood-Adv (Std)"), fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tr("Mehrfache Empfangsbestätigungen (multi.acks = 1)"), fontSize = 12.sp)
                    Switch(checked = setup.multiAcks == 1, onCheckedChange = { viewModel.updateBasicSetupDraft(setup.copy(multiAcks = if (it) 1 else 0)) }, colors = SwitchDefaults.colors(checkedTrackColor = bColors.signalRed))
                }
            }
        }

        val doSave = { viewModel.applyBasicSetupChanges(pendingChanges, BusyKey.ROUTING) }
        val regionState by viewModel.regionState.collectAsState()
        RegionsSection(viewModel, regionState, BusyKey.REGION in busy)

        RegisterTourSave { if (pendingChanges.isNotEmpty()) doSave() }
        // Im Setup-Tour-Modus übernimmt "Speichern & Weiter →" in der Tour-Leiste
        if (!LocalTourMode.current) {
            BusyButton(
                busy = BusyKey.ROUTING in busy, onClick = { doSave() },
                containerColor = bColors.signalRed, modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(tr("Speichern"), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

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
 * REGIONEN & SCOPE-ROUTING (MeshCore/ZephCore "region …").
 * Jede Aktion wird sofort gesendet (put/allowf/denyf/remove/home + "region save"; "region default" speichert
 * selbst) und danach der Regionen-Stand neu eingelesen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegionsSection(viewModel: RepeaterViewModel, state: RegionState, busy: Boolean) {
    val bColors = LocalBauhausColors.current
    var homeInput by remember { mutableStateOf("") }
    var defaultInput by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var newParent by remember { mutableStateOf("*") }
    var newFlood by remember { mutableStateOf(true) }
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    val names = state.regions.map { it.name }

    Card(modifier = Modifier.fillMaxWidth(), shape = BauhausCornerShape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("REGIONEN & SCOPE-ROUTING"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = bColors.cobalt, modifier = Modifier.weight(1f))
                BusyOutlinedButton(busy = busy, onClick = { viewModel.refreshRegions() }, color = bColors.cobalt, busyText = null) {
                    Icon(Icons.Default.Refresh, contentDescription = tr("Aktualisieren"), tint = bColors.cobalt, modifier = Modifier.size(16.dp))
                }
            }
            when (state.supported) {
                null -> { Text(tr("Regionen noch nicht gelesen – \"Aktualisieren\" tippen."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); return@Column }
                false -> { Text(tr("Diese Firmware unterstützt keine Regionen (Befehl \"region\" unbekannt)."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); return@Column }
                true -> Unit
            }
            Text(tr("Änderungen werden sofort gesendet und mit \"region save\" dauerhaft gespeichert."), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // --- Home-Region ---
            RegionPicker(
                title = tr("Home-Region"), current = state.home ?: "*", options = listOf("*") + names,
                input = homeInput, onInput = { homeInput = it }, busy = busy,
                hint = tr("Muss bereits existieren (sonst \"Err - unknown region\")."),
                onPick = { viewModel.setHomeRegion(it) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            // --- Default-Region ---
            RegionPicker(
                title = tr("Default-Region"), current = state.default ?: "<null>", options = listOf("<null>") + names,
                input = defaultInput, onInput = { defaultInput = it }, busy = busy,
                hint = tr("Unbekannte Namen legt die Firmware automatisch an (Flood erlaubt)."),
                onPick = { viewModel.setDefaultRegion(if (it == "<null>") null else it) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // --- Liste ---
            Text(tr("Konfigurierte Regionen ({0})", state.regions.size), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            RegionRow(name = "*", parent = null, depth = 0, flood = state.wildcardFlood, isHome = state.home == null, isDefault = false, busy = busy,
                onFlood = { viewModel.setRegionFlood("*", it) }, onRemove = null)
            state.regions.forEach { r ->
                RegionRow(name = r.name, parent = r.parent, depth = r.depth, flood = r.floodAllowed, isHome = state.home == r.name, isDefault = state.default == r.name, busy = busy,
                    onFlood = { viewModel.setRegionFlood(r.name, it) }, onRemove = { confirmRemove = r.name })
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // --- Neue Region ---
            Text(tr("Neue Region hinzufügen"), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            val nameOk = newName.isBlank() || RegionParser.isValidName(newName)
            val parentOk = newParent.isBlank() || newParent.trim() == "*" || RegionParser.isValidName(newParent)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it.filterNot(Char::isWhitespace).take(RegionParser.MAX_NAME_LEN) },
                    label = { Text(tr("Name"), fontSize = 11.sp) }, placeholder = { Text("de-ni-bs", fontSize = 12.sp) },
                    isError = !nameOk, singleLine = true, shape = BauhausCornerShape, modifier = Modifier.weight(1.4f)
                )
                OutlinedTextField(
                    value = newParent, onValueChange = { newParent = it.filterNot(Char::isWhitespace).take(RegionParser.MAX_NAME_LEN) },
                    label = { Text(tr("Parent"), fontSize = 11.sp) }, placeholder = { Text("*", fontSize = 12.sp) },
                    isError = !parentOk, singleLine = true, shape = BauhausCornerShape, modifier = Modifier.weight(1f)
                )
            }
            if (names.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOf("*") + names).forEach { n ->
                    FilterChip(selected = newParent == n, onClick = { newParent = n }, label = { Text(n, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }, shape = BauhausCornerShape)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Flood erlaubt"), fontSize = 12.sp, modifier = Modifier.weight(1f))
                Switch(checked = newFlood, onCheckedChange = { newFlood = it }, colors = SwitchDefaults.colors(checkedTrackColor = bColors.signalRed))
            }
            BusyButton(
                busy = busy, enabled = newName.isNotBlank() && nameOk && parentOk, containerColor = bColors.cobalt, modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.addRegion(newName, newParent, newFlood); newName = "" }
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text(tr("Hinzufügen"), color = Color.White, fontWeight = FontWeight.Bold)
            }
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

/** Auswahl über vorhandene Regionen (Chips) oder freie Eingabe + "Setzen". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegionPicker(title: String, current: String, options: List<String>, input: String, onInput: (String) -> Unit, busy: Boolean, hint: String, onPick: (String) -> Unit) {
    val bColors = LocalBauhausColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(current, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = bColors.cobalt)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { n ->
            FilterChip(selected = n == current, enabled = !busy, onClick = { if (n != current) onPick(n) }, label = { Text(n, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }, shape = BauhausCornerShape)
        }
    }
    val ok = input.isBlank() || RegionParser.isValidName(input)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = input, onValueChange = { onInput(it.filterNot(Char::isWhitespace).take(RegionParser.MAX_NAME_LEN)) },
            placeholder = { Text(tr("Freie Eingabe"), fontSize = 12.sp) }, isError = !ok, singleLine = true, shape = BauhausCornerShape, modifier = Modifier.weight(1f)
        )
        BusyButton(busy = busy, enabled = input.isNotBlank() && ok, containerColor = bColors.cobalt, busyText = null, onClick = { onPick(input.trim()); onInput("") }) {
            Text(tr("Setzen"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
    Text(hint, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun RegionRow(name: String, parent: String?, depth: Int, flood: Boolean, isHome: Boolean, isDefault: Boolean, busy: Boolean, onFlood: (Boolean) -> Unit, onRemove: (() -> Unit)?) {
    val bColors = LocalBauhausColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = (depth.coerceAtMost(4) * 12).dp)
            .clip(BauhausCornerShape).background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, BauhausCornerShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                if (isHome && name != "*") RegionTag(tr("HOME"), bColors.signalRed)
                if (isDefault) RegionTag(tr("DEFAULT"), bColors.cobalt)
            }
            Text(
                if (parent == null) tr("Wildcard (Wurzel)") else tr("Parent: {0}", parent) + "  ·  " + (if (flood) tr("Flood AN") else tr("Flood AUS")),
                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = flood, enabled = !busy, onCheckedChange = onFlood, colors = SwitchDefaults.colors(checkedTrackColor = bColors.signalRed))
        if (onRemove != null) {
            IconButton(enabled = !busy, onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = tr("Löschen"), tint = if (busy) MaterialTheme.colorScheme.outline else bColors.signalRed) }
        } else Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun RegionTag(text: String, color: Color) {
    Text(text, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White,
        modifier = Modifier.clip(BauhausCornerShape).background(color).padding(horizontal = 4.dp, vertical = 1.dp))
}
