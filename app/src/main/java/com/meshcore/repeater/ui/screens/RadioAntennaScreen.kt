package com.meshcore.repeater.ui.screens

import com.meshcore.repeater.ui.components.hintedVerticalScroll

import com.meshcore.repeater.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshcore.repeater.model.CountryRegion
import com.meshcore.repeater.ui.components.LocalTourMode
import com.meshcore.repeater.ui.components.RegisterTourSave
import com.meshcore.repeater.ui.components.BusyButton
import com.meshcore.repeater.ui.components.BusyOutlinedButton
import com.meshcore.repeater.viewmodel.BusyKey
import com.meshcore.repeater.ui.theme.AlertRed
import com.meshcore.repeater.ui.theme.BauhausCornerShape
import com.meshcore.repeater.ui.theme.LocalBauhausColors
import com.meshcore.repeater.viewmodel.BasicSetupState
import com.meshcore.repeater.viewmodel.RepeaterViewModel
import com.meshcore.repeater.viewmodel.radioDiffersFrom

/** Regions-Presets (ohne "CUSTOM" – die Felder darunter sind immer frei editierbar). */
private val RegionPresets = listOf(
    CountryRegion.DE_STANDARD to "EU/DE Narrow",
    CountryRegion.EU868_CLASSIC to "EU/DE Wide",
    CountryRegion.DE_HIGHSPEED to "DE High-Speed",
    CountryRegion.UK868 to "UK868",
    CountryRegion.US915 to "US915"
)

private fun presetMatches(r: CountryRegion, s: BasicSetupState): Boolean =
    s.freqMhz.toDoubleOrNull()?.let { kotlin.math.abs(it - r.frequencyMhz) < 0.0005 } == true &&
        s.bwKhz.toDoubleOrNull()?.let { kotlin.math.abs(it - r.bandwidthKhz) < 0.05 } == true &&
        s.sf.toIntOrNull() == r.spreadingFactor && s.cr.toIntOrNull() == r.codingRateNum

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RadioAntennaScreen(viewModel: RepeaterViewModel) {
    val config by viewModel.radioConfig.collectAsState()
    val setup by viewModel.basicSetup.collectAsState()
    val baseline by viewModel.nodeBaseline.collectAsState()
    val bColors = LocalBauhausColors.current
    val inTour = LocalTourMode.current
    val busy by viewModel.busyActions.collectAsState()

    val freqOk = setup.freqMhz.toDoubleOrNull()?.let { it in 137.0..1020.0 } == true
    val bwOk = setup.bwKhz.toDoubleOrNull()?.let { it in 7.8..500.0 } == true
    val sfOk = setup.sf.toIntOrNull()?.let { it in 5..12 } == true
    val crOk = setup.cr.toIntOrNull()?.let { it in 5..8 } == true
    val txOk = setup.txPowerDbm.toIntOrNull()?.let { it in -9..config.maxTxInputDbm } == true   // Grenze folgt dem aktiven Board-Profil
    val dutyOk = setup.dutyCyclePercent.toDoubleOrNull()?.let { it in 1.0..100.0 } == true

    fun edit(transform: (BasicSetupState) -> BasicSetupState) = viewModel.updateRadioDraft(transform(viewModel.basicSetup.value))
    fun decimal(raw: String, maxLen: Int): String? {
        val v = raw.replace(',', '.')
        return if (v.length <= maxLen && v.count { it == '.' } <= 1 && v.all { it.isDigit() || it == '.' }) v else null
    }
    fun integer(raw: String, maxLen: Int, allowMinus: Boolean = false): String? =
        if (raw.length <= maxLen && raw.withIndex().all { (i, c) -> c.isDigit() || (allowMinus && i == 0 && c == '-') }) raw else null

    RegisterTourSave { viewModel.saveRadioDraft() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .hintedVerticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = tr("FUNK & REGION"),
            style = MaterialTheme.typography.titleMedium,
            color = bColors.signalRed,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = BauhausCornerShape
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Regions-Preset übernehmen (Werte danach frei anpassbar):"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RegionPresets.forEach { (region, label) ->
                        FilterChip(
                            shape = BauhausCornerShape,
                            selected = presetMatches(region, setup),
                            onClick = { viewModel.applyRegionPreset(region) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = bColors.signalRed, selectedLabelColor = Color.White)
                        )
                    }
                }
                Text(tr(config.region.description), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        shape = BauhausCornerShape,
                        value = setup.freqMhz, isError = !freqOk,
                        onValueChange = { raw -> decimal(raw, 8)?.let { v -> edit { it.copy(freqMhz = v) } } },
                        label = { Text(tr("Frequenz (MHz)"), fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                    OutlinedTextField(
                        shape = BauhausCornerShape,
                        value = setup.bwKhz, isError = !bwOk,
                        onValueChange = { raw -> decimal(raw, 6)?.let { v -> edit { it.copy(bwKhz = v) } } },
                        label = { Text(tr("Bandbreite (kHz)"), fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        shape = BauhausCornerShape,
                        value = setup.sf, isError = !sfOk,
                        onValueChange = { raw -> integer(raw, 2)?.let { v -> edit { it.copy(sf = v) } } },
                        label = { Text("SF (5..12)", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                    OutlinedTextField(
                        shape = BauhausCornerShape,
                        value = setup.cr, isError = !crOk,
                        onValueChange = { raw -> integer(raw.removePrefix("4/"), 1)?.let { v -> edit { it.copy(cr = v) } } },
                        label = { Text("CR 4/x (5..8)", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        shape = BauhausCornerShape,
                        value = setup.txPowerDbm, isError = !txOk,
                        onValueChange = { raw -> integer(raw, 3, allowMinus = true)?.let { v -> edit { it.copy(txPowerDbm = v) } } },
                        label = { Text("TX (dBm)", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                    OutlinedTextField(
                        shape = BauhausCornerShape,
                        value = setup.dutyCyclePercent, isError = !dutyOk,
                        onValueChange = { raw -> decimal(raw.removeSuffix("%"), 5)?.let { v -> edit { it.copy(dutyCyclePercent = v) } } },
                        label = { Text(tr("Duty-Cycle (%)"), fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                }
                // RX-Gain (v1.21): nur wenn die Firmware radio.rxgain bzw. radio.fem.rxgain im Start-Scan geliefert hat
                if (setup.rxGain != null || setup.femRxGain != null || setup.rxDuty != null) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                setup.rxGain?.let { on ->
                    RxGainRow(tr("RX-Gain (LNA-Verstärkung)"), "radio.rxgain", on) { v -> edit { it.copy(rxGain = v) } }
                }
                setup.femRxGain?.let { on ->
                    RxGainRow(tr("FEM-RX-Gain (externer LNA)"), "radio.fem.rxgain", on) { v -> edit { it.copy(femRxGain = v) } }
                }
                // v1.27: RX-Duty-Cycle (nur ZephCore) – spart Empfangsstrom, wirkt erst nach Neustart
                setup.rxDuty?.let { on ->
                    RxGainRow(tr("RX-Duty-Cycle (stromsparender Empfang)"), "rxduty", on) { v -> edit { it.copy(rxDuty = v) } }
                    Text(tr("Wirkt erst nach einem Neustart. Nur einschalten, wenn die Nodes in der Umgebung aktuelle Firmware (Präambel 32) nutzen."), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = if (config.isCompliant) tr("Empfohlener TX mit aktueller Antenne: max. {0} dBm (Antenne unter Hardware & Antenne).", viewModel.recommendedTxDbm(config))
                           else tr("Hinweis: {0} dBm überschreitet mit der eingestellten Antenne das ERP-Limit – empfohlen max. {1} dBm.", setup.txPowerDbm, viewModel.recommendedTxDbm(config)),
                    fontSize = 11.sp,
                    color = if (config.isCompliant) MaterialTheme.colorScheme.onSurfaceVariant else AlertRed
                )
            }
        }

        // Im Setup-Tour-Modus übernimmt "Speichern & Weiter →" in der Tour-Leiste
        if (!inTour) {
            Spacer(Modifier.height(8.dp))
            BusyButton(
                busy = BusyKey.RADIO in busy,
                hasUnsavedChanges = setup.radioDiffersFrom(baseline),
                onClick = { viewModel.saveRadioDraft() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                containerColor = bColors.signalRed
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = tr("Speichern"), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Eckiger Bauhaus-Schalter für eine RX-Gain-Variable (Titel + Firmware-Name in Monospace). */
@Composable
private fun RxGainRow(title: String, variable: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(variable, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        com.meshcore.repeater.ui.components.BauhausSwitch(checked = checked, onCheckedChange = onChange)
    }
}
