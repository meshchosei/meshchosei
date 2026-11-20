package com.meshcore.repeater.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshcore.repeater.model.AntennaPreset
import com.meshcore.repeater.model.CountryRegion
import com.meshcore.repeater.ui.theme.AlertRed
import com.meshcore.repeater.ui.theme.LocalBauhausColors
import com.meshcore.repeater.viewmodel.RepeaterViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RadioAntennaScreen(viewModel: RepeaterViewModel) {
    val config by viewModel.radioConfig.collectAsState()
    val bColors = LocalBauhausColors.current
    val scrollState = rememberScrollState()

    var customGainInput by remember(config.customAntennaGainDbi) {
        mutableStateOf(config.customAntennaGainDbi.toString())
    }
    var cableLossInput by remember(config.cableLossDb) {
        mutableStateOf(config.cableLossDb.toString())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // === Section 1: Region & Länder-Erkennung ===
        Text(
            text = "REGION & FUNK-VORGABEN (MESHCORE / ZEPHCORE)",
            style = MaterialTheme.typography.titleMedium,
            color = bColors.signalRed,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = config.region.regionName,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = config.region.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.setRegion(CountryRegion.detectDefault()) }
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Auto-Erkennung", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Region Presets Chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CountryRegion.values().forEach { region ->
                        FilterChip(
                            selected = config.region == region,
                            onClick = { viewModel.setRegion(region) },
                            label = { Text(region.countryCode) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = bColors.signalRed,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }

                if (config.region == CountryRegion.CUSTOM) {
                    var customFreq by remember(config.region) { mutableStateOf("%.3f".format(java.util.Locale.US, config.frequencyMhz)) }
                    var customBw by remember(config.region) { mutableStateOf("%.1f".format(java.util.Locale.US, config.bandwidthKhz)) }
                    var customSf by remember(config.region) { mutableStateOf(config.spreadingFactor.toString()) }
                    var customCr by remember(config.region) { mutableStateOf(config.codingRate) }
                    val freqVal = customFreq.replace(',', '.').toDoubleOrNull()
                    val bwVal = customBw.replace(',', '.').toDoubleOrNull()
                    val sfVal = customSf.toIntOrNull()
                    val freqOk = freqVal != null && freqVal in 137.0..1020.0
                    val bwOk = bwVal != null && bwVal in 7.8..500.0
                    val sfOk = sfVal != null && sfVal in 5..12
                    val crOk = customCr in listOf("4/5", "4/6", "4/7", "4/8")
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = customFreq, isError = !freqOk, onValueChange = { raw -> val s = raw.replace(',', '.'); if (s.count { it == '.' } <= 1 && s.length <= 8) { customFreq = s; s.toDoubleOrNull()?.takeIf { it in 137.0..1020.0 }?.let { v -> viewModel.setCustomRadioParams(v, config.bandwidthKhz, config.spreadingFactor, config.codingRate) } } }, label = { Text("Freq (137..1020 MHz)", fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = customBw, isError = !bwOk, onValueChange = { raw -> val s = raw.replace(',', '.'); if (s.count { it == '.' } <= 1 && s.length <= 6) { customBw = s; s.toDoubleOrNull()?.takeIf { it in 7.8..500.0 }?.let { v -> viewModel.setCustomRadioParams(config.frequencyMhz, v, config.spreadingFactor, config.codingRate) } } }, label = { Text("BW (7.8..500 kHz)", fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = customSf, isError = !sfOk, onValueChange = { raw -> if (raw.all { it.isDigit() } && raw.length <= 2) { customSf = raw; raw.toIntOrNull()?.takeIf { it in 5..12 }?.let { v -> viewModel.setCustomRadioParams(config.frequencyMhz, config.bandwidthKhz, v, config.codingRate) } } }, label = { Text("SF (5..12)", fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = customCr, isError = !crOk, onValueChange = { raw -> if (raw.length <= 4) { customCr = raw; if (raw in listOf("4/5", "4/6", "4/7", "4/8")) viewModel.setCustomRadioParams(config.frequencyMhz, config.bandwidthKhz, config.spreadingFactor, raw) } }, label = { Text("CR (4/5..4/8)", fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Radio parameters row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ParamPill("Frequenz", "%.3f MHz".format(config.frequencyMhz))
                    ParamPill("Bandbreite", "%.1f kHz".format(config.bandwidthKhz))
                    ParamPill("SF / CR", "SF%d / %s".format(config.spreadingFactor, config.codingRate))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // RepeaterMesh.cpp Standardwerte
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ParamPill("Flood-Max Hops", "${config.floodMax} (Adv: ${config.floodMaxAdvert})")
                    ParamPill("Advert-Intervall", "Flood: ${config.floodAdvertIntervalHours}h")
                    ParamPill("TX-Bereich", "${config.region.minTxPowerDbm} .. ${config.region.maxTxPowerDbm} dBm")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // === Section 2: Antennen-Presets & Gewinn ===
        Text(
            text = "ANTENNEN-SETUP & KABELDÄMPFUNG",
            style = MaterialTheme.typography.titleMedium,
            color = bColors.cobalt,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Antennen-Preset wählen:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AntennaPreset.values().forEach { preset ->
                        FilterChip(
                            selected = config.antennaPreset == preset,
                            onClick = { viewModel.setAntennaPreset(preset) },
                            label = { Text(preset.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = bColors.cobalt,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }

                if (config.antennaPreset.isCustom) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customGainInput,
                        onValueChange = {
                            customGainInput = it
                            it.toDoubleOrNull()?.let { dBi -> viewModel.setCustomAntennaGain(dBi) }
                        },
                        label = { Text("Eigener Antennengewinn (dBi)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = cableLossInput,
                    onValueChange = {
                        cableLossInput = it
                        it.toDoubleOrNull()?.let { loss -> viewModel.setCableLoss(loss) }
                    },
                    label = { Text("Kabel- & Pigtail-Dämpfung (dB)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // === Section 3: Automatische ERP / EIRP Berechnung & Compliance ===
        Text(
            text = "AUTOMATISCHE ERP/EIRP-COMPLIANCE",
            style = MaterialTheme.typography.titleMedium,
            color = if (config.isCompliant) bColors.signalRed else AlertRed,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (config.isCompliant) bColors.signalRed.copy(alpha = 0.5f) else AlertRed,
                    shape = RoundedCornerShape(12.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Formula display
                Text(
                    text = "EIRP = P_TX + Gain - Loss  |  ERP = EIRP - 2.15 dB",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Value Calculation Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Transceiver TX", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("%.1f dBm".format(config.txPowerDbm), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Column {
                        Text("Eff. Gain (G-L)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("+%.1f dBi".format(config.effectiveGainDbi - config.cableLossDb), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Column {
                        Text("Berechnetes ERP", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "%.1f dBm".format(config.calculatedErpDbm),
                            fontWeight = FontWeight.Bold,
                            color = if (config.isCompliant) bColors.signalRed else AlertRed
                        )
                        Text("%.1f mW".format(config.calculatedErpMw), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column {
                        Text("Gesetzl. Limit", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("%.1f dBm ERP".format(config.region.maxLegalErpDbm), fontWeight = FontWeight.Bold, color = bColors.signalRed)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sendeleistungs-Schieberegler
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Transceiver TX-Power: ${config.txPowerDbm.roundToInt()} dBm",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Max. legal: ${config.maxAllowedTxPowerDbm.roundToInt()} dBm",
                        fontSize = 12.sp,
                        color = if (config.isCompliant) bColors.signalRed else AlertRed
                    )
                }

                Slider(
                    value = config.txPowerDbm.toFloat(),
                    onValueChange = { viewModel.setTxPower(it.toDouble()) },
                    valueRange = config.region.minTxPowerDbm.toFloat()..config.region.maxTxPowerDbm.toFloat(),
                    steps = (config.region.maxTxPowerDbm - config.region.minTxPowerDbm - 1).coerceAtLeast(0),
                    colors = SliderDefaults.colors(
                        thumbColor = if (config.isCompliant) bColors.signalRed else AlertRed,
                        activeTrackColor = if (config.isCompliant) bColors.signalRed else AlertRed
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Compliance Lock Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (config.complianceUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (config.complianceUnlocked) AlertRed else bColors.signalRed
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sicherheits-Sperre aufheben",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (config.complianceUnlocked) "Freigegeben für Test- & Lizenzbetrieb" else "Begrenzt TX auf max. legalen Wert",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.complianceUnlocked,
                        onCheckedChange = { viewModel.setComplianceUnlocked(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AlertRed,
                            checkedTrackColor = AlertRed.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Apply Configuration Button
        Button(
            onClick = { viewModel.applyRadioSettings() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = bColors.signalRed),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.Black)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Funkkonfiguration an Repeater übertragen",
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ParamPill(title: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
    }
}
