package com.meshcore.repeater.ui.screens

import com.meshcore.repeater.i18n.tr
import com.meshcore.repeater.ui.components.LocalTourMode
import com.meshcore.repeater.ui.components.RegisterTourSave

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

        val doSave = { viewModel.applyBasicSetupChanges(pendingChanges) }
        RegisterTourSave { if (pendingChanges.isNotEmpty()) doSave() }
        // Im Setup-Tour-Modus übernimmt "Speichern & Weiter →" in der Tour-Leiste
        if (!LocalTourMode.current) {
            Button(
                modifier = Modifier.fillMaxWidth(), shape = BauhausCornerShape, colors = ButtonDefaults.buttonColors(containerColor = bColors.signalRed),
                onClick = { doSave() }
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(tr("Speichern"), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(modifier = Modifier.weight(1f), shape = BauhausCornerShape, onClick = { viewModel.sendCommand("advert", manualCli = false) }) {
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
