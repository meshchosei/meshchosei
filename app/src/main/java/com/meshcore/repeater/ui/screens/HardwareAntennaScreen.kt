package com.meshcore.repeater.ui.screens

import com.meshcore.repeater.model.BoardDatabase
import com.meshcore.repeater.i18n.tr
import com.meshcore.repeater.ui.components.BusyButton
import com.meshcore.repeater.ui.components.LocalTourMode
import com.meshcore.repeater.ui.components.RefreshButton
import com.meshcore.repeater.ui.components.RegisterTourSave
import com.meshcore.repeater.viewmodel.BusyKey
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshcore.repeater.model.AntennaPreset
import com.meshcore.repeater.ui.theme.AlertRed
import com.meshcore.repeater.ui.theme.BauhausCornerShape
import com.meshcore.repeater.ui.theme.LocalBauhausColors
import com.meshcore.repeater.viewmodel.RepeaterViewModel
import com.meshcore.repeater.model.BatterySolarTelemetry
import com.meshcore.repeater.model.PowerSource
import kotlin.math.floor
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HardwareAntennaScreen(viewModel: RepeaterViewModel) {
    val bColors = LocalBauhausColors.current
    val hardwareInfo by viewModel.hardwareInfo.collectAsState()
    val capabilities by viewModel.deviceCapabilities.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val cfg by viewModel.radioConfig.collectAsState()
    val busy by viewModel.busyActions.collectAsState()

    var cableLossInput by remember(cfg.cableLossDb) { mutableStateOf(cfg.cableLossDb.toString()) }
    var customGainInput by remember(cfg.customAntennaGainDbi) { mutableStateOf(cfg.customAntennaGainDbi.toString()) }
    val statusColor = if (cfg.isCompliant) bColors.cobalt else AlertRed
    val maxTx = floor(cfg.maxAllowedTxPowerDbm).toInt()
    val recommendedTx = viewModel.recommendedTxDbm(cfg)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Erkannte Hardware
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = BauhausCornerShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(Icons.Default.Memory, tr("ERKANNTE HARDWARE"), bColors.cobalt)
                val boardLabel = hardwareInfo.boardModel.ifBlank { tr("Wird beim Auslesen ermittelt...") }
                val fwLabel = hardwareInfo.firmwareVersion.ifBlank { capabilities.firmwareVersion.ifBlank { "—" } }
                Text(tr("Board: {0}", boardLabel), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(tr("Firmware: {0}", fwLabel), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Versorgung aus der "power"-Antwort (Start-Scan bzw. Button) – nie mehr pauschal "Netz / USB"
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("Versorgung: {0}", powerLabel(telemetry)), fontSize = 12.sp,
                        color = if (telemetry.powerSource == PowerSource.BATTERY && telemetry.batteryPercent < 20) bColors.ochre else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    RefreshButton(onClick = { viewModel.refreshPowerStatus() }, label = tr("Abfragen"), busy = BusyKey.POWER in busy)
                }

                // Board-Profil aus der BoardDatabase
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                val board = cfg.board
                Text(
                    tr("Profil: {0}", tr(board.displayName)) + "  " + (if (cfg.boardAutoDetected) tr("(automatisch erkannt)") else tr("(nicht automatisch erkannt)")),
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = if (cfg.boardAutoDetected) bColors.cobalt else bColors.ochre
                )
                // IntrinsicSize.Max + fillMaxHeight: alle drei Kacheln exakt gleich hoch
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ValueTile(tr("Transceiver"), board.transceiver, board.mcu, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f).fillMaxHeight())
                    ValueTile(tr("Hardware-Max-TX"), tr("{0} dBm", "%.0f".format(board.maxTxDbm)), null, bColors.signalRed, Modifier.weight(1f).fillMaxHeight())
                    ValueTile(tr("Antennenanschluss"), board.connector, tr("Pigtail {0} dB", "%.1f".format(board.defaultPigtailLossDb)), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f).fillMaxHeight())
                }
                if (board.hasExternalPa) {
                    Text(
                        tr("⚠ Board mit externem Leistungsverstärker (PA, bis {0} dBm): Die Ausgangsleistung kann deutlich über üblichen SX1262-Werten liegen. Prüfe, wie die Firmware \"tx\" auf die PA-Ausgangsleistung abbildet, und halte das ERP-Limit unbedingt ein!", "%.0f".format(board.maxTxDbm)),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AlertRed,
                        modifier = Modifier.fillMaxWidth().background(AlertRed.copy(alpha = 0.10f), BauhausCornerShape).padding(8.dp)
                    )
                }
                // Manuelle Auswahl – immer möglich, standardmäßig aufgeklappt, wenn nicht erkannt
                var showBoardPicker by remember(cfg.boardAutoDetected) { mutableStateOf(!cfg.boardAutoDetected) }
                TextButton(onClick = { showBoardPicker = !showBoardPicker }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (showBoardPicker) tr("Board-Auswahl ausblenden") else tr("Board manuell wählen…"), fontSize = 12.sp)
                }
                if (showBoardPicker) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BoardDatabase.profiles.forEach { p ->
                            FilterChip(
                                selected = board.id == p.id,
                                onClick = { viewModel.selectBoardProfile(p) },
                                label = { Text(tr(p.displayName), fontSize = 11.sp) },
                                shape = BauhausCornerShape,
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = bColors.cobalt, selectedLabelColor = Color.White)
                            )
                        }
                    }
                }
            }
        }

        // 2. Status-LEDs (aus "Routing & Adverts" hierher verschoben – Hardware-Thema)
        StatusLedSection(viewModel, BusyKey.LEDS in busy)

        // 3. Antennen-Setup & Kabeldämpfung
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = BauhausCornerShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(Icons.Default.SettingsInputAntenna, tr("ANTENNEN-SETUP & KABELDÄMPFUNG"), bColors.signalRed)
                Text(tr("Montierte Antenne wählen:"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AntennaPreset.values().forEach { preset ->
                        FilterChip(
                            selected = cfg.antennaPreset == preset,
                            onClick = { viewModel.setAntennaPreset(preset) },
                            label = { Text(tr(preset.label), fontSize = 11.sp) },
                            shape = BauhausCornerShape,
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = bColors.cobalt, selectedLabelColor = Color.White)
                        )
                    }
                }
                if (cfg.antennaPreset.note.isNotBlank()) {
                    Text(cfg.antennaPreset.note, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (cfg.antennaPreset.isCustom) {
                        OutlinedTextField(
                            value = customGainInput,
                            onValueChange = { customGainInput = it; it.replace(',', '.').toDoubleOrNull()?.takeIf { g -> g in -10.0..30.0 }?.let { g -> viewModel.setCustomAntennaGain(g) } },
                            label = { Text(tr("Eigener Gewinn (dBi)"), fontSize = 11.sp) },
                            isError = customGainInput.replace(',', '.').toDoubleOrNull()?.let { it in -10.0..30.0 } != true,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = BauhausCornerShape
                        )
                    }
                    OutlinedTextField(
                        value = cableLossInput,
                        onValueChange = { cableLossInput = it; it.replace(',', '.').toDoubleOrNull()?.takeIf { l -> l in 0.0..20.0 }?.let { l -> viewModel.setCableLoss(l) } },
                        label = { Text(tr("Kabel- & Pigtail-Dämpfung (dB)"), fontSize = 11.sp) },
                        isError = cableLossInput.replace(',', '.').toDoubleOrNull()?.let { it in 0.0..20.0 } != true,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = BauhausCornerShape
                    )
                }
            }
        }

        // 4. Live-ERP/EIRP-Berechnung, Grenzwerte & TX-Abgleich
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = BauhausCornerShape,
            border = BorderStroke(1.dp, if (cfg.isCompliant) MaterialTheme.colorScheme.outline else AlertRed),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(Icons.Default.Tune, tr("ERP/EIRP-GRENZWERTE & TX-EMPFEHLUNG"), statusColor)
                Text(
                    "EIRP = P_TX + G − L   |   ERP = EIRP − 2.15 dB",
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ValueTile("Transceiver TX", "${"%.1f".format(cfg.txPowerDbm)} dBm", null, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                    ValueTile(tr("Eff. Gewinn (G−L)"), "${"%+.1f".format(cfg.effectiveGainDbi - cfg.cableLossDb)} dB", null, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ValueTile("EIRP", "${"%.1f".format(cfg.calculatedEirpDbm)} dBm", "${cfg.calculatedEirpMw.roundToInt()} mW", statusColor, Modifier.weight(1f))
                    ValueTile("ERP", "${"%.1f".format(cfg.calculatedErpDbm)} dBm", "${cfg.calculatedErpMw.roundToInt()} mW", statusColor, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ValueTile(tr("Gesetzl. Limit ({0})", cfg.region.countryCode), "${"%.1f".format(cfg.region.maxLegalErpDbm)} dBm ERP", null, bColors.signalRed, Modifier.weight(1f))
                    ValueTile(tr("Max. Transceiver-TX"), tr("{0} dBm", maxTx), tr("Hardware: {0}..{1} dBm", cfg.region.minTxPowerDbm, "%.0f".format(cfg.hardwareMaxTxDbm)), bColors.signalRed, Modifier.weight(1f))
                }
                Text(
                    text = if (cfg.isCompliant) tr("✓ Setup innerhalb des ERP-Limits (Reserve {0} dB).", "%.1f".format(cfg.region.maxLegalErpDbm - cfg.calculatedErpDbm))
                           else tr("⚠ ERP-Limit um {0} dB überschritten – TX auf max. {1} dBm reduzieren.", "%.1f".format(cfg.excessDbm), maxTx),
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = statusColor
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                // Ruhiger Hinweis zu den Grenzwerten (keine Sperre – der Wert bleibt unter "Funk & Region" frei editierbar)
                Text(
                    tr("Gesetzlich zählt die abgestrahlte Leistung (ERP/EIRP), nicht die Transceiver-Leistung: ") +
                        tr("In {0} sind max. {1} dBm ERP ({2} mW) zulässig. ", tr(cfg.region.regionName), "%.0f".format(cfg.region.maxLegalErpDbm), "%.0f".format(Math.pow(10.0, cfg.region.maxLegalErpDbm / 10.0))) +
                        tr("Mit Antennengewinn und Kabelverlust ergibt sich daraus ein empfohlener TX-Wert von {0} dBm.", recommendedTx) +
                        (if (cfg.maxAllowedTxPowerDbm > cfg.hardwareMaxTxDbm) " " + tr("Begrenzt durch das Hardware-Maximum des Boards ({0} dBm).", "%.0f".format(cfg.hardwareMaxTxDbm)) else ""),
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(), shape = BauhausCornerShape,
                    onClick = { viewModel.adoptRecommendedTx() }
                ) { Text(tr("Empfohlenen TX-Wert übernehmen ({0} dBm)", recommendedTx), fontSize = 12.sp) }
                Text(
                    tr("Der Wert wird nur vorgemerkt – unter \"Funk & Region\" kannst du ihn frei anpassen und speichern."),
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Status-LEDs ("set leds"). Firmware: ZephCore kennt on/off/default, Standard-MeshCore hat kein "set leds";
 * "boot" ist eine Custom-Erweiterung. Gesendet wird nur bei Abweichung vom bestätigten Stand (nodeBaseline).
 * Im Setup-Tour-Modus übernimmt "Speichern & Weiter →" das Speichern.
 */
@Composable
private fun StatusLedSection(viewModel: RepeaterViewModel, busy: Boolean) {
    val bColors = LocalBauhausColors.current
    val setup by viewModel.basicSetup.collectAsState()
    val baseline by viewModel.nodeBaseline.collectAsState()
    val changed = setup.ledsMode.isNotBlank() && setup.ledsMode != baseline.ledsMode
    RegisterTourSave { viewModel.applyLedMode() }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = BauhausCornerShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(Icons.Default.Lightbulb, tr("STATUS-LEDS"), bColors.ochre)
            Text(tr("Status-LEDs (set leds):"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("on", "off", "boot").forEach { mode ->
                    FilterChip(
                        selected = setup.ledsMode.equals(mode, true),
                        onClick = { viewModel.updateBasicSetupDraft(setup.copy(ledsMode = mode)) },
                        label = { Text(mode, fontSize = 11.sp) },
                        shape = BauhausCornerShape,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = bColors.cobalt, selectedLabelColor = Color.White)
                    )
                }
            }
            Text(
                tr("Hinweis: \"boot\" ist eine Custom-Firmware-Erweiterung und wird von Standard-Firmware ggf. nicht unterstützt."),
                fontSize = 10.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!LocalTourMode.current) {
                BusyButton(
                    busy = busy, hasUnsavedChanges = changed, onClick = { viewModel.applyLedMode() },
                    containerColor = bColors.signalRed, modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Speichern"), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color)
    }
}

@Composable
private fun ValueTile(title: String, value: String, sub: String?, valueColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = valueColor, fontFamily = FontFamily.Monospace)
        if (sub != null) Text(sub, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


/** Lesbare Versorgungsangabe aus der "power"-Telemetrie (Akku / Solar / USB-Netz / extern). */
@Composable
private fun powerLabel(t: BatterySolarTelemetry): String {
    val batt = if (t.hasBattery) tr("Akku {0} % ({1} V)", t.batteryPercent, "%.2f".format(t.batteryVoltage)) else null
    val charging = if (t.charging) " · " + tr("lädt") else ""
    return when (t.powerSource) {
        PowerSource.UNKNOWN -> if (t.queried) tr("nicht ermittelbar") else tr("unbekannt (noch nicht abgefragt)")
        PowerSource.SOLAR -> tr("Solar (lädt)") + (batt?.let { " · $it" } ?: "")
        PowerSource.USB -> tr("USB-Netz") + (batt?.let { " · $it" } ?: "") + charging
        PowerSource.EXTERNAL -> tr("Externe Versorgung") + (batt?.let { " · $it" } ?: "") + charging
        PowerSource.BATTERY -> batt!! + charging
    }
}
