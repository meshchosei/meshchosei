package com.meshcore.repeater.ui.screens

import com.meshcore.repeater.ui.components.BauhausSwitch
import com.meshcore.repeater.ui.components.RefreshButton

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meshcore.repeater.model.AntennaPreset
import com.meshcore.repeater.model.CountryRegion
import com.meshcore.repeater.model.WatchdogDuration
import com.meshcore.repeater.ui.theme.AlertRed
import com.meshcore.repeater.ui.theme.BauhausCornerShape
import com.meshcore.repeater.ui.theme.LocalBauhausColors
import com.meshcore.repeater.viewmodel.RepeaterViewModel
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt

private data class SetupPresetOption(
    val id: String,
    val title: String,
    val freq: String,
    val bw: String,
    val sf: String,
    val cr: String,
    val tx: String,
    val duty: String,
    val advertMin: String,
    val floodHours: String
)

private val SETUP_PRESETS = listOf(
    SetupPresetOption("eu_de", "EU / DE Standard Narrow (869.618 MHz, 500 mW ERP, 10% Duty)", "869.618", "62.5", "8", "8", "22", "10", "230", "50"),
    SetupPresetOption("eu_wide", "EU / DE Wide (869.525 MHz, BW 250 kHz, 10% Duty)", "869.525", "250", "11", "5", "22", "10", "230", "50"),
    SetupPresetOption("alpen", "CH / AT / Berg-Relais (Langstrecke, 10% Duty)", "869.618", "62.5", "9", "8", "22", "10", "240", "48"),
    SetupPresetOption("solar", "Solar-Sparmodus (Advert-Puffer, 10% Duty)", "869.618", "62.5", "8", "8", "17", "10", "360", "72"),
    SetupPresetOption("uk868", "Großbritannien UK868 (869.525 MHz, 10% Duty)", "869.525", "250", "11", "5", "22", "10", "230", "50"),
    SetupPresetOption("us915", "USA / FCC US915 (915.000 MHz, 100% Duty)", "915.000", "500", "9", "5", "22", "100", "230", "50")
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BasicSetupScreen(viewModel: RepeaterViewModel) {
    val bColors = LocalBauhausColors.current
    val setup by viewModel.basicSetup.collectAsState()
    val radioCfg by viewModel.radioConfig.collectAsState()
    val remoteAdmin by viewModel.remoteAdminSession.collectAsState()
    val meshCare by viewModel.meshCareState.collectAsState()
    val capabilities by viewModel.deviceCapabilities.collectAsState()
    val hardwareInfo by viewModel.hardwareInfo.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val terminalLines by viewModel.terminalLogs.collectAsState()
    val systemLines by viewModel.systemLogs.collectAsState()

    val isWirelessLink = remoteAdmin.targetRepeater != null
    var isWizardMode by rememberSaveable { mutableStateOf(true) }
    var wizardStep by rememberSaveable { mutableStateOf(1) }

    var baseline by remember { mutableStateOf(setup) }
    var newAdminPassword by remember { mutableStateOf("") }
    var newGuestPassword by remember { mutableStateOf("") }
    var clearGuestPassword by remember { mutableStateOf(false) }
    var showRebootConfirm by remember { mutableStateOf(false) }
    var showNewKeyDialog by remember { mutableStateOf(false) }
    var customPrivKeyHex by remember { mutableStateOf("") }
    var showMapPickerDialog by remember { mutableStateOf(false) }
    var presetExpanded by remember { mutableStateOf(false) }
    var showDebugLog by remember { mutableStateOf(false) }
    var cableLossInput by remember(radioCfg.cableLossDb) { mutableStateOf(radioCfg.cableLossDb.toString()) }
    var customGainInput by remember(radioCfg.customAntennaGainDbi) { mutableStateOf(radioCfg.customAntennaGainDbi.toString()) }

    // Initial-Read Sperre & Status
    var hasInitialReadDone by remember(remoteAdmin.targetRepeater?.pubKeyHex) { mutableStateOf(false) }
    var wasReading by remember { mutableStateOf(false) }
    LaunchedEffect(setup.isReading) {
        if (wasReading && !setup.isReading) {
            hasInitialReadDone = true
        }
        wasReading = setup.isReading
    }
    val lockInputsDuringInitialRead = setup.isReading && !hasInitialReadDone
    val inputEnabled = !lockInputsDuringInitialRead

    val defaultPreset = remember(radioCfg.region) {
        when (radioCfg.region) {
            CountryRegion.UK868 -> SETUP_PRESETS.first { it.id == "uk868" }
            CountryRegion.US915 -> SETUP_PRESETS.first { it.id == "us915" }
            else -> SETUP_PRESETS.first { it.id == "eu_de" }
        }
    }
    var selectedPreset by remember(defaultPreset) { mutableStateOf(defaultPreset) }

    LaunchedEffect(remoteAdmin.targetRepeater?.pubKeyHex) { viewModel.readBasicSetupFromNode() }
    LaunchedEffect(setup) { if (setup.isReading) baseline = setup }

    // Einheitlicher, ruhiger ASCII-Spinner ("|", "/", "-", "\")
    val spinnerChars = listOf("|", "/", "-", "\\")
    var spinnerFrame by remember { mutableStateOf(0) }
    LaunchedEffect(setup.isReading) {
        if (setup.isReading) {
            while (true) {
                spinnerFrame++
                delay(220L)
            }
        }
    }
    val currentSpinner = spinnerChars[spinnerFrame % spinnerChars.size]

    val txDbm = setup.txPowerDbm.toDoubleOrNull() ?: radioCfg.txPowerDbm
    val liveRadioCfg = radioCfg.copy(txPowerDbm = txDbm)

    // Funktion zur Berechnung der geänderten CLI-Befehle (Änderungs-Vorschau)
    fun buildPendingCommands(): List<String> {
        val cmds = mutableListOf<String>()
        if (setup.freqMhz != baseline.freqMhz || setup.bwKhz != baseline.bwKhz || setup.sf != baseline.sf || setup.cr != baseline.cr) {
            cmds.add("set radio ${setup.freqMhz.trim()},${setup.bwKhz.trim()},${setup.sf.trim()},${setup.cr.trim()}")
        }
        if (setup.txPowerDbm.isNotBlank() && setup.txPowerDbm != baseline.txPowerDbm) {
            cmds.add("set tx ${setup.txPowerDbm.trim()}")
        }
        if (setup.dutyCyclePercent.isNotBlank() && setup.dutyCyclePercent != baseline.dutyCyclePercent) {
            cmds.add("set dutycycle ${setup.dutyCyclePercent.trim()}")
        }
        if (setup.nodeName.isNotBlank() && setup.nodeName != baseline.nodeName) {
            cmds.add("set name ${setup.nodeName.trim()}")
        }
        if (setup.ownerInfo.isNotBlank() && setup.ownerInfo != baseline.ownerInfo) {
            cmds.add("set owner.info ${setup.ownerInfo.trim()}")
        }
        if (setup.latitude.isNotBlank() && setup.latitude != baseline.latitude) {
            cmds.add("set lat ${setup.latitude.trim()}")
        }
        if (setup.longitude.isNotBlank() && setup.longitude != baseline.longitude) {
            cmds.add("set lon ${setup.longitude.trim()}")
        }
        if (newAdminPassword.isNotBlank()) {
            cmds.add("password ${newAdminPassword.trim()}")
        }
        if (clearGuestPassword) {
            cmds.add("set guest.password ")
        } else if (newGuestPassword.isNotBlank()) {
            cmds.add("set guest.password ${newGuestPassword.trim()}")
        }
        if (setup.repeatEnabled != baseline.repeatEnabled) {
            cmds.add("set repeat ${if (setup.repeatEnabled) "on" else "off"}")
        }
        if (setup.pathHashMode != baseline.pathHashMode) {
            cmds.add("set path.hash.mode ${setup.pathHashMode}")
        }
        if (setup.loopDetect.isNotBlank() && setup.loopDetect != baseline.loopDetect) {
            cmds.add("set loop.detect ${setup.loopDetect.trim()}")
        }
        if (setup.ledsMode.isNotBlank() && setup.ledsMode != baseline.ledsMode) {
            cmds.add("set leds ${setup.ledsMode.trim()}")
        }
        if (setup.advertIntervalMin.isNotBlank() && setup.advertIntervalMin != baseline.advertIntervalMin) {
            cmds.add("set advert.interval ${setup.advertIntervalMin.trim()}")
        }
        if (setup.floodAdvertIntervalHours.isNotBlank() && setup.floodAdvertIntervalHours != baseline.floodAdvertIntervalHours) {
            cmds.add("set flood.advert.interval ${setup.floodAdvertIntervalHours.trim()}")
        }
        if (setup.txDelaySec.isNotBlank() && setup.txDelaySec != baseline.txDelaySec) {
            cmds.add("set txdelay ${setup.txDelaySec.trim()}")
        }
        if (setup.directTxDelaySec.isNotBlank() && setup.directTxDelaySec != baseline.directTxDelaySec) {
            cmds.add("set direct.txdelay ${setup.directTxDelaySec.trim()}")
        }
        if (setup.rxDelaySec.isNotBlank() && setup.rxDelaySec != baseline.rxDelaySec) {
            cmds.add("set rxdelay ${setup.rxDelaySec.trim()}")
        }
        if (setup.multiAcks != baseline.multiAcks) {
            cmds.add("set multi.acks ${setup.multiAcks}")
        }
        return cmds
    }

    fun executeApplyChanges() {
        val cmds = buildPendingCommands()
        if (newAdminPassword.isNotBlank()) newAdminPassword = ""
        if (newGuestPassword.isNotBlank()) newGuestPassword = ""
        clearGuestPassword = false
        baseline = setup
        viewModel.applyBasicSetupChanges(cmds.map { com.meshcore.repeater.viewmodel.SetupChange(it) })  // ungenutzter Alt-Screen
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // =========================================================================
        // 1. OBERE LEISTE: MODUS-UMSCHALTER + RAHMENLOSER TEXTBUTTON [ NEU EINLESEN ]
        // =========================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Modus-Umschalter
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(BauhausCornerShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = isWizardMode,
                    onClick = { isWizardMode = true },
                    label = { Text("Assistent", fontSize = 11.sp, fontWeight = if (isWizardMode) FontWeight.Bold else FontWeight.Normal) },
                    modifier = Modifier.weight(1f),
                    shape = BauhausCornerShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = bColors.cobalt,
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = !isWizardMode,
                    onClick = { isWizardMode = false },
                    label = { Text("Profi (Alles)", fontSize = 11.sp, fontWeight = if (!isWizardMode) FontWeight.Bold else FontWeight.Normal) },
                    modifier = Modifier.weight(1f),
                    shape = BauhausCornerShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = bColors.cobalt,
                        selectedLabelColor = Color.White
                    )
                )
            }

            RefreshButton(onClick = { viewModel.readBasicSetupFromNode() }, label = "Neu einlesen", busy = setup.isReading)
        }

        // Kompakter 1-zeiliger Hinweis während des ersten Einlesens
        if (lockInputsDuringInitialRead) {
            Surface(
                shape = BauhausCornerShape,
                color = bColors.cobalt.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, bColors.cobalt.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(currentSpinner, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = bColors.cobalt, fontSize = 13.sp)
                    Text(
                        "Lese Repeater-Werte ein – Eingaben werden gleich freigeschaltet...",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // =========================================================================
        // GEFÜHRTER ASSISTENTEN-MODUS (WIZARD)
        // =========================================================================
        if (isWizardMode) {
            // Scrollbarer Inhalt des aktuellen Schritts
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (wizardStep) {
                    // SCHRITT 1: Verbindung & Schonmodus
                    1 -> {
                        if (isWirelessLink) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = BauhausCornerShape,
                                border = BorderStroke(1.dp, bColors.ochre),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.ElectricBolt, contentDescription = null, tint = bColors.ochre)
                                        Text("Funkstrecke aktiv – Schonmodus empfohlen", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = bColors.ochre)
                                    }
                                    Text(
                                        "Beim Konfigurieren über Funk werden temporär Sendeleistung und Parameter gedrosselt, um Nachbarknoten nicht zu stören und eine sichere Verbindung zu gewährleisten.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (meshCare.isActive) {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            color = bColors.ochre.copy(alpha = 0.15f)
                                        ) {
                                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Icon(Icons.Default.Timer, contentDescription = null, tint = bColors.ochre)
                                                Text("Schonmodus läuft bereits (${meshCare.formattedRemainingTime} verbleibend)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = bColors.ochre)
                                            }
                                        }
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = BauhausCornerShape,
                                                colors = ButtonDefaults.buttonColors(containerColor = bColors.ochre),
                                                onClick = {
                                                    viewModel.startMeshCare(WatchdogDuration.FIVE_MINUTES)
                                                    viewModel.sendCommand("tempradio 869.618,62.5,8,8,5", manualCli = false)
                                                    wizardStep = 2
                                                }
                                            ) {
                                                Icon(Icons.Default.Shield, contentDescription = null, tint = Color.Black)
                                                Spacer(Modifier.width(8.dp))
                                                Text("5 Min. Schonmodus aktivieren", color = Color.Black, fontWeight = FontWeight.Bold)
                                            }

                                            Button(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = BauhausCornerShape,
                                                colors = ButtonDefaults.buttonColors(containerColor = bColors.ochre.copy(alpha = 0.85f)),
                                                onClick = {
                                                    viewModel.startMeshCare(WatchdogDuration.TEN_MINUTES)
                                                    viewModel.sendCommand("tempradio 869.618,62.5,8,8,10", manualCli = false)
                                                    wizardStep = 2
                                                }
                                            ) {
                                                Icon(Icons.Default.Shield, contentDescription = null, tint = Color.Black)
                                                Spacer(Modifier.width(8.dp))
                                                Text("10 Min. Schonmodus aktivieren", color = Color.Black, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Direkte USB-Verbindung mit 2,2s Auto-Timer
                            var usbAnimFrame by remember { mutableStateOf(0) }
                            LaunchedEffect(Unit) {
                                for (i in 0..5) {
                                    usbAnimFrame = i
                                    delay(370L)
                                }
                                if (wizardStep == 1) wizardStep = 2
                            }
                            val asciiBar = when (usbAnimFrame % 6) {
                                0 -> "[>        ]"
                                1 -> "[==>      ]"
                                2 -> "[====>    ]"
                                3 -> "[======>  ]"
                                4 -> "[========>]"
                                else -> "[==========]"
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = BauhausCornerShape,
                                border = BorderStroke(1.dp, bColors.cobalt),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.Usb, contentDescription = null, tint = bColors.cobalt)
                                        Text("Direkte USB-Verbindung aktiv", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = bColors.cobalt)
                                    }
                                    Text(
                                        "Es ist keine Funkdrosselung (Schonmodus) nötig, da die Konfiguration direkt über den USB-Port erfolgt.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(asciiBar, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = bColors.cobalt)
                                            Text("Direkte Verbindung erkannt – weiter geht's...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // SCHRITT 2: Erkannte Hardware & vollständiges Antennen-Setup (KEIN Public Key!)
                    2 -> {
                        // 1. Erkannte Hardware untereinander in voller Breite
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = BauhausCornerShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Memory, contentDescription = null, tint = bColors.cobalt, modifier = Modifier.size(18.dp))
                                    Text("Erkannte Hardware", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = bColors.cobalt)
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    // Zeile 1: Board-Modell
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Board-Modell:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(hardwareInfo.boardModel.ifBlank { "Unbekannt / ESP32" }, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                    // Zeile 2: Firmware-Version
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Firmware-Version:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(capabilities.firmwareVersion.ifBlank { hardwareInfo.firmwareVersion.ifBlank { "Unbekannt" } }, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                    // Zeile 3: Akku / Stromversorgung
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Akku / Stromversorgung:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        val battText = if (telemetry.batteryVoltage > 0f) {
                                            "%.2f V (%d%%)%s".format(telemetry.batteryVoltage, telemetry.batteryPercent, if (telemetry.solarVoltage > 0f) " • Solar: %.2f V".format(telemetry.solarVoltage) else "")
                                        } else {
                                            "USB / Konstant-Versorgung"
                                        }
                                        Text(battText, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }

                        // 2. Vollständiges Antennen-Setup
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = BauhausCornerShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.SettingsInputAntenna, contentDescription = null, tint = bColors.cobalt, modifier = Modifier.size(18.dp))
                                    Text("Antennen-Setup & Kabeldämpfung", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = bColors.cobalt)
                                }
                                Text("Wähle das montierte Antennenmodell für präzise ERP/EIRP-Grenzwerte:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    AntennaPreset.values().forEach { preset ->
                                        FilterChip(
                                            shape = BauhausCornerShape,
                                            enabled = inputEnabled,
                                            selected = radioCfg.antennaPreset == preset,
                                            onClick = { viewModel.setAntennaPreset(preset) },
                                            label = { Text(preset.label, fontSize = 11.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = bColors.cobalt,
                                                selectedLabelColor = Color.White
                                            )
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (radioCfg.antennaPreset.isCustom) {
                                        OutlinedTextField(
                                            value = customGainInput,
                                            enabled = inputEnabled,
                                            onValueChange = {
                                                customGainInput = it
                                                it.replace(',', '.').toDoubleOrNull()?.let { dBi -> viewModel.setCustomAntennaGain(dBi) }
                                            },
                                            label = { Text("Eigener Gewinn (dBi)", fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            shape = BauhausCornerShape
                                        )
                                    }
                                    OutlinedTextField(
                                        value = cableLossInput,
                                        enabled = inputEnabled,
                                        onValueChange = {
                                            cableLossInput = it
                                            it.replace(',', '.').toDoubleOrNull()?.let { l -> viewModel.setCableLoss(l) }
                                        },
                                        label = { Text("Kabelverlust (dB)", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = BauhausCornerShape
                                    )
                                }
                            }
                        }

                        // 3. Komplette Live-EIRP/ERP- und Max-TX-Konformitätskarte direkt unter dem Antennen-Setup
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = BauhausCornerShape,
                            border = BorderStroke(1.dp, if (liveRadioCfg.isCompliant) MaterialTheme.colorScheme.outline else bColors.signalRed),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Live-Berechnung Antennensystem & Compliance",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (liveRadioCfg.isCompliant) bColors.cobalt else bColors.signalRed
                                )
                                Text("EIRP = P_TX + Gain - Loss  |  ERP = EIRP - 2.15 dB", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    ParamPill("Transceiver TX", "${liveRadioCfg.txPowerDbm.roundToInt()} dBm")
                                    ParamPill("Eff. Gewinn", "+%.1f dBi".format(liveRadioCfg.effectiveGainDbi - liveRadioCfg.cableLossDb))
                                    ParamPill("Berechnetes ERP", "%.1f dBm (%.0f mW)".format(liveRadioCfg.calculatedErpDbm, liveRadioCfg.calculatedErpMw))
                                    ParamPill("Max. legal (${radioCfg.region.countryCode})", "${liveRadioCfg.maxAllowedTxPowerDbm.roundToInt()} dBm")
                                }

                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Transceiver TX: ${liveRadioCfg.txPowerDbm.roundToInt()} dBm", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    Text("Max. legal: ${liveRadioCfg.maxAllowedTxPowerDbm.roundToInt()} dBm", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (liveRadioCfg.isCompliant) bColors.cobalt else bColors.signalRed)
                                }

                                Slider(
                                    value = liveRadioCfg.txPowerDbm.toFloat().coerceIn(radioCfg.region.minTxPowerDbm.toFloat(), radioCfg.region.maxTxPowerDbm.toFloat()),
                                    enabled = inputEnabled,
                                    onValueChange = {
                                        val rounded = it.roundToInt()
                                        viewModel.setTxPower(rounded.toDouble())
                                        viewModel.updateBasicSetupDraft(setup.copy(txPowerDbm = rounded.toString()))
                                    },
                                    valueRange = radioCfg.region.minTxPowerDbm.toFloat()..radioCfg.region.maxTxPowerDbm.toFloat(),
                                    steps = (radioCfg.region.maxTxPowerDbm - radioCfg.region.minTxPowerDbm - 1).coerceAtLeast(0),
                                    colors = SliderDefaults.colors(
                                        thumbColor = if (liveRadioCfg.isCompliant) bColors.cobalt else bColors.signalRed,
                                        activeTrackColor = if (liveRadioCfg.isCompliant) bColors.cobalt else bColors.signalRed
                                    )
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(if (radioCfg.complianceUnlocked) Icons.Default.LockOpen else Icons.Default.Lock, contentDescription = null, tint = if (radioCfg.complianceUnlocked) bColors.signalRed else bColors.cobalt)
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Sicherheits-Sperre aufheben", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        Text(if (radioCfg.complianceUnlocked) "Freigegeben für Test- & Lizenzbetrieb" else "Begrenzt TX auf max. legalen Wert", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    BauhausSwitch(checked = radioCfg.complianceUnlocked, enabled = inputEnabled, onCheckedChange = { viewModel.setComplianceUnlocked(it) })
                                }

                                if (!liveRadioCfg.isCompliant && !radioCfg.complianceUnlocked) {
                                    OutlinedButton(
                                        shape = BauhausCornerShape,
                                        enabled = inputEnabled,
                                        border = BorderStroke(1.dp, bColors.signalRed),
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            val legalTx = liveRadioCfg.maxAllowedTxPowerDbm.toInt().coerceIn(radioCfg.region.minTxPowerDbm, radioCfg.region.maxTxPowerDbm)
                                            viewModel.setTxPower(legalTx.toDouble())
                                            viewModel.updateBasicSetupDraft(setup.copy(txPowerDbm = legalTx.toString()))
                                        }
                                    ) {
                                        Text("TX automatisch auf ${liveRadioCfg.maxAllowedTxPowerDbm.toInt()} dBm begrenzen", fontSize = 11.sp, color = bColors.signalRed, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // SCHRITT 3: Funk & Region (Sendeleistung & Konformität als ERSTER Punkt ganz oben)
                    3 -> {
                        // 1. Sendeleistung & Konformität als ERSTER Punkt ganz oben
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = BauhausCornerShape,
                            border = BorderStroke(1.dp, if (liveRadioCfg.isCompliant) MaterialTheme.colorScheme.outline else bColors.signalRed),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Sendeleistung & Konformität (Antenne aus Schritt 2)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (liveRadioCfg.isCompliant) bColors.cobalt else bColors.signalRed)

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (liveRadioCfg.isCompliant) bColors.cobalt.copy(alpha = 0.12f) else bColors.signalRed.copy(alpha = 0.15f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(
                                            imageVector = if (liveRadioCfg.isCompliant) Icons.Default.CheckCircle else Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = if (liveRadioCfg.isCompliant) bColors.cobalt else bColors.signalRed
                                        )
                                        Text(
                                            text = if (liveRadioCfg.isCompliant) {
                                                "Berechnetes ERP: %.1f dBm (%.0f mW) — Konform mit Antennen-Setup aus Schritt 2 (Limit: %.1f dBm ERP).".format(liveRadioCfg.calculatedErpDbm, liveRadioCfg.calculatedErpMw, radioCfg.region.maxLegalErpDbm)
                                            } else {
                                                "Berechnetes ERP: %.1f dBm überschreitet das Limit von %.1f dBm ERP!".format(liveRadioCfg.calculatedErpDbm, radioCfg.region.maxLegalErpDbm)
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (liveRadioCfg.isCompliant) MaterialTheme.colorScheme.onSurface else bColors.signalRed
                                        )
                                    }
                                }

                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Transceiver TX: ${liveRadioCfg.txPowerDbm.roundToInt()} dBm", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    Text("Max. legal: ${liveRadioCfg.maxAllowedTxPowerDbm.roundToInt()} dBm", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (liveRadioCfg.isCompliant) bColors.cobalt else bColors.signalRed)
                                }

                                Slider(
                                    value = liveRadioCfg.txPowerDbm.toFloat().coerceIn(radioCfg.region.minTxPowerDbm.toFloat(), radioCfg.region.maxTxPowerDbm.toFloat()),
                                    enabled = inputEnabled,
                                    onValueChange = {
                                        val rounded = it.roundToInt()
                                        viewModel.setTxPower(rounded.toDouble())
                                        viewModel.updateBasicSetupDraft(setup.copy(txPowerDbm = rounded.toString()))
                                    },
                                    valueRange = radioCfg.region.minTxPowerDbm.toFloat()..radioCfg.region.maxTxPowerDbm.toFloat(),
                                    steps = (radioCfg.region.maxTxPowerDbm - radioCfg.region.minTxPowerDbm - 1).coerceAtLeast(0),
                                    colors = SliderDefaults.colors(
                                        thumbColor = if (liveRadioCfg.isCompliant) bColors.cobalt else bColors.signalRed,
                                        activeTrackColor = if (liveRadioCfg.isCompliant) bColors.cobalt else bColors.signalRed
                                    )
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(if (radioCfg.complianceUnlocked) Icons.Default.LockOpen else Icons.Default.Lock, contentDescription = null, tint = if (radioCfg.complianceUnlocked) bColors.signalRed else bColors.cobalt)
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Sicherheits-Sperre aufheben", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        Text(if (radioCfg.complianceUnlocked) "Freigegeben für Test- & Lizenzbetrieb" else "Begrenzt TX auf max. legalen Wert", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    BauhausSwitch(checked = radioCfg.complianceUnlocked, enabled = inputEnabled, onCheckedChange = { viewModel.setComplianceUnlocked(it) })
                                }

                                if (!liveRadioCfg.isCompliant && !radioCfg.complianceUnlocked) {
                                    OutlinedButton(
                                        shape = BauhausCornerShape,
                                        enabled = inputEnabled,
                                        border = BorderStroke(1.dp, bColors.signalRed),
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            val legalTx = liveRadioCfg.maxAllowedTxPowerDbm.toInt().coerceIn(radioCfg.region.minTxPowerDbm, radioCfg.region.maxTxPowerDbm)
                                            viewModel.setTxPower(legalTx.toDouble())
                                            viewModel.updateBasicSetupDraft(setup.copy(txPowerDbm = legalTx.toString()))
                                        }
                                    ) {
                                        Text("TX automatisch auf ${liveRadioCfg.maxAllowedTxPowerDbm.toInt()} dBm begrenzen", fontSize = 11.sp, color = bColors.signalRed, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // 2. Region & Preset Auswahl
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = BauhausCornerShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Region & Optimal-Presets", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = bColors.cobalt)
                                    OutlinedButton(
                                        shape = BauhausCornerShape,
                                        enabled = inputEnabled,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        onClick = {
                                            val detected = CountryRegion.detectDefault()
                                            viewModel.setRegion(detected)
                                            viewModel.setTxPower(detected.defaultTxPowerDbm.toDouble())
                                            viewModel.updateBasicSetupDraft(
                                                setup.copy(
                                                    freqMhz = "%.3f".format(Locale.US, detected.frequencyMhz),
                                                    bwKhz = "%.1f".format(Locale.US, detected.bandwidthKhz),
                                                    sf = detected.spreadingFactor.toString(),
                                                    cr = detected.codingRateNum.toString(),
                                                    txPowerDbm = detected.defaultTxPowerDbm.toString()
                                                )
                                            )
                                        }
                                    ) {
                                        Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Auto-Land", fontSize = 11.sp)
                                    }
                                }

                                ExposedDropdownMenuBox(expanded = presetExpanded && inputEnabled, onExpandedChange = { if (inputEnabled) presetExpanded = !presetExpanded }, modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = selectedPreset.title,
                                        enabled = inputEnabled,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Optimal-Vorschlag vorfüllen", fontSize = 11.sp) },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetExpanded) },
                                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                                        shape = BauhausCornerShape
                                    )
                                    ExposedDropdownMenu(expanded = presetExpanded && inputEnabled, onDismissRequest = { presetExpanded = false }, modifier = Modifier.exposedDropdownSize(true)) {
                                        SETUP_PRESETS.forEach { opt ->
                                            DropdownMenuItem(
                                                text = { Text(opt.title, fontSize = 12.sp) },
                                                onClick = {
                                                    selectedPreset = opt
                                                    presetExpanded = false
                                                    viewModel.updateBasicSetupDraft(
                                                        setup.copy(
                                                            freqMhz = opt.freq, bwKhz = opt.bw, sf = opt.sf, cr = opt.cr,
                                                            txPowerDbm = opt.tx, dutyCyclePercent = opt.duty,
                                                            advertIntervalMin = opt.advertMin, floodAdvertIntervalHours = opt.floodHours
                                                        )
                                                    )
                                                    opt.tx.toDoubleOrNull()?.let { viewModel.setTxPower(it) }
                                                }
                                            )
                                        }
                                    }
                                }

                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    CountryRegion.values().forEach { region ->
                                        FilterChip(
                                            shape = BauhausCornerShape,
                                            enabled = inputEnabled,
                                            selected = radioCfg.region == region,
                                            onClick = {
                                                viewModel.setRegion(region)
                                                if (region != CountryRegion.CUSTOM) {
                                                    viewModel.updateBasicSetupDraft(
                                                        setup.copy(
                                                            freqMhz = "%.3f".format(Locale.US, region.frequencyMhz),
                                                            bwKhz = "%.1f".format(Locale.US, region.bandwidthKhz),
                                                            sf = region.spreadingFactor.toString(),
                                                            cr = region.codingRateNum.toString()
                                                        )
                                                    )
                                                }
                                            },
                                            label = { Text(region.countryCode, fontSize = 11.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = bColors.signalRed,
                                                selectedLabelColor = Color.Black
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Frequenz / Bandbreite / SF / CR / Duty
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = BauhausCornerShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Funkparameter (Ist- & Soll-Werte)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = bColors.cobalt)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(value = setup.freqMhz, enabled = inputEnabled, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(freqMhz = it)) }, label = { Text("Frequenz (MHz)", fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                                    OutlinedTextField(value = setup.bwKhz, enabled = inputEnabled, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(bwKhz = it)) }, label = { Text("Bandbreite (kHz)", fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(value = setup.sf, enabled = inputEnabled, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(sf = it)) }, label = { Text("SF (5..12)", fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                                    OutlinedTextField(value = setup.cr, enabled = inputEnabled, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(cr = it)) }, label = { Text("CR (5..8)", fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                                    OutlinedTextField(value = setup.dutyCyclePercent, enabled = inputEnabled, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(dutyCyclePercent = it)) }, label = { Text("Duty %", fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                                }
                            }
                        }
                    }

                    // SCHRITT 4: Identität & Standort
                    4 -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = BauhausCornerShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Node-Identität & Schlüssel", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = bColors.cobalt)
                                Text("Public Key (Adresse im Mesh):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    setup.publicKeyHex.ifBlank { "(Wird ermittelt...)" },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(8.dp)
                                )
                                OutlinedButton(
                                    shape = BauhausCornerShape,
                                    enabled = inputEnabled,
                                    border = BorderStroke(1.dp, bColors.ochre),
                                    onClick = { showNewKeyDialog = true }
                                ) {
                                    Icon(Icons.Default.Key, contentDescription = null, tint = bColors.ochre, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Node-Schlüssel neu setzen (nur bei Neuaufbau)", fontSize = 11.sp, color = bColors.ochre)
                                }

                                OutlinedTextField(value = setup.nodeName, enabled = inputEnabled, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(nodeName = it)) }, label = { Text("Repeater-Name (set name)", fontSize = 12.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = BauhausCornerShape)
                                OutlinedTextField(value = setup.ownerInfo, enabled = inputEnabled, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(ownerInfo = it)) }, label = { Text("Besitzer / Kontakt-Info (set owner.info)", fontSize = 12.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = BauhausCornerShape)
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = BauhausCornerShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Standort-Koordinaten (GPS / Map)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = bColors.cobalt)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(value = setup.latitude, enabled = inputEnabled, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(latitude = it)) }, label = { Text("Breitengrad (lat)", fontSize = 12.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                                    OutlinedTextField(value = setup.longitude, enabled = inputEnabled, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(longitude = it)) }, label = { Text("Längengrad (lon)", fontSize = 12.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(modifier = Modifier.weight(1f), enabled = inputEnabled, shape = BauhausCornerShape, onClick = { showMapPickerDialog = true }) {
                                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Auf Karte wählen", fontSize = 11.sp, color = bColors.cobalt)
                                    }
                                    OutlinedButton(modifier = Modifier.weight(1f), enabled = inputEnabled, shape = BauhausCornerShape, onClick = { viewModel.sendCommand("gps setloc", manualCli = false) }) {
                                        Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("GPS übernehmen", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    // SCHRITT 5: Routing, Sicherheit & Änderungs-Vorschau
                    5 -> {
                        // Passwörter
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = BauhausCornerShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Zugangskontrolle & Passwörter", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = bColors.cobalt)
                                OutlinedTextField(value = newAdminPassword, enabled = inputEnabled, onValueChange = { newAdminPassword = it }, label = { Text("Neues Admin-Passwort (leer = unverändert)", fontSize = 12.sp) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = BauhausCornerShape)
                                OutlinedTextField(value = newGuestPassword, enabled = inputEnabled, onValueChange = { newGuestPassword = it; if (it.isNotEmpty()) clearGuestPassword = false }, label = { Text("Gast-Passwort (leer = unverändert)", fontSize = 12.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = BauhausCornerShape)
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Gast-Passwort löschen (offener Gast-Zugang)", fontSize = 11.sp)
                                    Checkbox(checked = clearGuestPassword, enabled = inputEnabled, onCheckedChange = { clearGuestPassword = it; if (it) newGuestPassword = "" })
                                }
                            }
                        }

                        // Routing & System-Parameter
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = BauhausCornerShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Routing & System-Parameter", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = bColors.cobalt)

                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Repeater-Weiterleitung (set repeat)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    BauhausSwitch(checked = setup.repeatEnabled, enabled = inputEnabled, onCheckedChange = { viewModel.updateBasicSetupDraft(setup.copy(repeatEnabled = it)) })
                                }

                                Text("Pfad-Hash-Modus:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(0 to "1-Byte", 1 to "2-Byte", 2 to "3-Byte").forEach { (mode, label) ->
                                        FilterChip(selected = setup.pathHashMode == mode, enabled = inputEnabled, onClick = { viewModel.updateBasicSetupDraft(setup.copy(pathHashMode = mode)) }, label = { Text(label, fontSize = 11.sp) }, shape = BauhausCornerShape)
                                    }
                                }

                                Text("Schleifen-Erkennung (loop.detect):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("off", "minimal", "moderate", "strict").forEach { mode ->
                                        FilterChip(selected = setup.loopDetect.equals(mode, true), enabled = inputEnabled, onClick = { viewModel.updateBasicSetupDraft(setup.copy(loopDetect = mode)) }, label = { Text(mode, fontSize = 11.sp) }, shape = BauhausCornerShape)
                                    }
                                }

                                Text("Status-LEDs:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("on", "off", "boot").forEach { mode ->
                                        FilterChip(selected = setup.ledsMode.equals(mode, true), enabled = inputEnabled, onClick = { viewModel.updateBasicSetupDraft(setup.copy(ledsMode = mode)) }, label = { Text(mode, fontSize = 11.sp) }, shape = BauhausCornerShape)
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(value = setup.advertIntervalMin, enabled = inputEnabled, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(advertIntervalMin = it)) }, label = { Text("Advert (Min)", fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                                    OutlinedTextField(value = setup.floodAdvertIntervalHours, enabled = inputEnabled, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(floodAdvertIntervalHours = it)) }, label = { Text("Flood-Adv (Std)", fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                                }

                                val isAdaptiveDelays = setup.txDelaySec == "adaptive" || setup.directTxDelaySec == "adaptive"
                                Text("Paket-Verzögerung (Delays):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = isAdaptiveDelays,
                                        enabled = inputEnabled,
                                        onClick = {
                                            viewModel.updateBasicSetupDraft(
                                                setup.copy(
                                                    txDelaySec = "adaptive",
                                                    directTxDelaySec = "adaptive",
                                                    rxDelaySec = if (setup.rxDelaySec == "adaptive") "adaptive" else "0"
                                                )
                                            )
                                        },
                                        label = { Text("Adaptiv (Standard)", fontSize = 11.sp) },
                                        shape = BauhausCornerShape,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = bColors.cobalt,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                    FilterChip(
                                        selected = !isAdaptiveDelays,
                                        enabled = inputEnabled,
                                        onClick = {
                                            viewModel.updateBasicSetupDraft(
                                                setup.copy(
                                                    txDelaySec = if (setup.txDelaySec == "adaptive") "0.5" else setup.txDelaySec,
                                                    directTxDelaySec = if (setup.directTxDelaySec == "adaptive") "0.3" else setup.directTxDelaySec,
                                                    rxDelaySec = if (setup.rxDelaySec == "adaptive") "0" else setup.rxDelaySec
                                                )
                                            )
                                        },
                                        label = { Text("Manuell", fontSize = 11.sp) },
                                        shape = BauhausCornerShape,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = bColors.cobalt,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                                if (!isAdaptiveDelays) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(value = setup.txDelaySec, enabled = inputEnabled, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(txDelaySec = it)) }, label = { Text("TX (s)", fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                                        OutlinedTextField(value = setup.directTxDelaySec, enabled = inputEnabled, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(directTxDelaySec = it)) }, label = { Text("Direct (s)", fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                                        OutlinedTextField(value = setup.rxDelaySec, enabled = inputEnabled, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(rxDelaySec = it)) }, label = { Text("RX (s)", fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                                    }
                                }

                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Mehrfache Empfangsbestätigungen (multi.acks = 1)", fontSize = 11.sp)
                                    BauhausSwitch(checked = setup.multiAcks == 1, enabled = inputEnabled, onCheckedChange = { viewModel.updateBasicSetupDraft(setup.copy(multiAcks = if (it) 1 else 0)) })
                                }
                            }
                        }

                        // Änderungs-Vorschau (Diff Preview)
                        val pending = buildPendingCommands()
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = BauhausCornerShape,
                            border = BorderStroke(1.dp, if (pending.isNotEmpty()) bColors.cobalt else MaterialTheme.colorScheme.outline),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        if (pending.isNotEmpty()) Icons.AutoMirrored.Filled.FactCheck else Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (pending.isNotEmpty()) bColors.cobalt else bColors.signalRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text("Änderungs-Vorschau (${pending.size} Befehle)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = bColors.cobalt)
                                }

                                if (pending.isEmpty()) {
                                    Text(
                                        "Keine Änderungen vorgenommen (aktuelle Repeater-Werte bleiben unverändert).",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text("Folgende konkrete Befehle werden an den Repeater übertragen:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    pending.forEach { cmd ->
                                        Text(
                                            text = "• $cmd",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = bColors.cobalt
                                        )
                                    }
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(modifier = Modifier.weight(1f), shape = BauhausCornerShape, onClick = { viewModel.sendCommand("advert", manualCli = false) }) {
                                Text("Advert senden", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = bColors.cobalt)
                            }
                            OutlinedButton(modifier = Modifier.weight(1f), shape = BauhausCornerShape, border = BorderStroke(1.dp, bColors.signalRed), onClick = { showRebootConfirm = true }) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, tint = bColors.signalRed, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Neu starten", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = bColors.signalRed)
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // FESTE LEISTE UNTEN: NUR HIER STEHT "Schritt X / 5" + BALKEN + [Zurück] / [Weiter]
            // =========================================================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = BauhausCornerShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { wizardStep / 5f },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = bColors.cobalt,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (wizardStep > 1) {
                            OutlinedButton(
                                shape = BauhausCornerShape,
                                onClick = { wizardStep-- }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Zurück", fontSize = 12.sp)
                            }
                        } else {
                            Spacer(Modifier.width(10.dp))
                        }

                        Text("Schritt $wizardStep / 5", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                        if (wizardStep < 5) {
                            Button(
                                shape = BauhausCornerShape,
                                colors = ButtonDefaults.buttonColors(containerColor = bColors.cobalt),
                                onClick = { wizardStep++ }
                            ) {
                                Text("Weiter", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                            }
                        } else {
                            val pending = buildPendingCommands()
                            Button(
                                shape = BauhausCornerShape,
                                colors = ButtonDefaults.buttonColors(containerColor = bColors.signalRed),
                                enabled = !setup.isReading,
                                onClick = { executeApplyChanges() }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (pending.isNotEmpty()) "Senden (${pending.size})" else "Senden", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

        } else {
            // =========================================================================
            // PROFI-MODUS (Alle Sektionen untereinander)
            // =========================================================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Schonmodus-Banner bei aktiver Funkstrecke
                if (isWirelessLink && !meshCare.isActive) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = BauhausCornerShape,
                        border = BorderStroke(1.dp, bColors.ochre),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.ElectricBolt, contentDescription = null, tint = bColors.ochre, modifier = Modifier.size(18.dp))
                                Text("Funkstrecke aktiv – Schonmodus empfohlen", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = bColors.ochre)
                            }
                            Text("Senkt die Sendeleistung/Parameter temporär für 5 Min., um umliegende Nodes bei Tests nicht zu belasten.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(
                                shape = BauhausCornerShape,
                                border = BorderStroke(1.dp, bColors.ochre),
                                onClick = {
                                    viewModel.startMeshCare(WatchdogDuration.FIVE_MINUTES)
                                    viewModel.sendCommand("tempradio 869.618,62.5,8,8,5", manualCli = false)
                                }
                            ) { Text("Schonmodus (5 Min.) aktivieren", color = bColors.ochre, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                        }
                    }
                }

                // GRUNDSETUP & LÄNDER-PRESET
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = BauhausCornerShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("GRUNDSETUP & LÄNDER-PRESET", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = bColors.signalRed)
                        Text(if (setup.isReading) "Lese aktuelle Repeater-Werte ein..." else "Nur geänderte Felder werden übertragen.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        ExposedDropdownMenuBox(expanded = presetExpanded && inputEnabled, onExpandedChange = { if (inputEnabled) presetExpanded = !presetExpanded }, modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedPreset.title,
                                enabled = inputEnabled,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Optimal-Vorschlag nach Region / Profil", fontSize = 11.sp) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                shape = BauhausCornerShape
                            )
                            ExposedDropdownMenu(expanded = presetExpanded && inputEnabled, onDismissRequest = { presetExpanded = false }, modifier = Modifier.exposedDropdownSize(true)) {
                                SETUP_PRESETS.forEach { opt ->
                                    DropdownMenuItem(text = { Text(opt.title, fontSize = 12.sp) }, onClick = { selectedPreset = opt; presetExpanded = false })
                                }
                            }
                        }
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = inputEnabled,
                            shape = BauhausCornerShape,
                            onClick = {
                                viewModel.updateBasicSetupDraft(
                                    setup.copy(
                                        freqMhz = selectedPreset.freq, bwKhz = selectedPreset.bw, sf = selectedPreset.sf, cr = selectedPreset.cr,
                                        txPowerDbm = selectedPreset.tx, dutyCyclePercent = selectedPreset.duty,
                                        advertIntervalMin = selectedPreset.advertMin, floodAdvertIntervalHours = selectedPreset.floodHours,
                                        pathHashMode = 1, loopDetect = "minimal", multiAcks = 1
                                    )
                                )
                                selectedPreset.tx.toDoubleOrNull()?.let { viewModel.setTxPower(it) }
                            }
                        ) { Text("Gewähltes Preset in Felder übernehmen", fontSize = 11.sp, color = bColors.cobalt, fontWeight = FontWeight.Bold) }
                    }
                }

                // 1. FUNK, LAND & ANTENNE
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = BauhausCornerShape,
                    border = BorderStroke(1.dp, if (liveRadioCfg.isCompliant) MaterialTheme.colorScheme.outline else bColors.signalRed),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("1. FUNK, LAND & ANTENNE", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = bColors.cobalt)
                                Text(radioCfg.region.regionName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text(radioCfg.region.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(
                                shape = BauhausCornerShape,
                                enabled = inputEnabled,
                                onClick = {
                                    val detected = CountryRegion.detectDefault()
                                    viewModel.setRegion(detected)
                                    viewModel.setTxPower(detected.defaultTxPowerDbm.toDouble())
                                    viewModel.updateBasicSetupDraft(
                                        setup.copy(
                                            freqMhz = "%.3f".format(Locale.US, detected.frequencyMhz),
                                            bwKhz = "%.1f".format(Locale.US, detected.bandwidthKhz),
                                            sf = detected.spreadingFactor.toString(),
                                            cr = detected.codingRateNum.toString(),
                                            txPowerDbm = detected.defaultTxPowerDbm.toString()
                                        )
                                    )
                                }
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Auto", fontSize = 11.sp)
                            }
                        }

                        Text("Region & Frequenzband wählen:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            CountryRegion.values().forEach { region ->
                                FilterChip(
                                    shape = BauhausCornerShape,
                                    enabled = inputEnabled,
                                    selected = radioCfg.region == region,
                                    onClick = {
                                        viewModel.setRegion(region)
                                        if (region != CountryRegion.CUSTOM) {
                                            viewModel.updateBasicSetupDraft(
                                                setup.copy(
                                                    freqMhz = "%.3f".format(Locale.US, region.frequencyMhz),
                                                    bwKhz = "%.1f".format(Locale.US, region.bandwidthKhz),
                                                    sf = region.spreadingFactor.toString(),
                                                    cr = region.codingRateNum.toString()
                                                )
                                            )
                                        }
                                    },
                                    label = { Text(region.countryCode, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = bColors.signalRed,
                                        selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }

                        // Radio Status-Pills
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            ParamPill("Frequenz", "${setup.freqMhz.ifBlank { "%.3f".format(Locale.US, radioCfg.frequencyMhz) }} MHz")
                            ParamPill("Bandbreite", "${setup.bwKhz.ifBlank { "%.1f".format(Locale.US, radioCfg.bandwidthKhz) }} kHz")
                            ParamPill("SF / CR", "SF${setup.sf} / 4/${setup.cr}")
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            ParamPill("Duty-Cycle", "${setup.dutyCyclePercent}%")
                            ParamPill("TX-Bereich", "${radioCfg.region.minTxPowerDbm} .. ${radioCfg.region.maxTxPowerDbm} dBm")
                            ParamPill("Gesetzl. ERP", "%.1f dBm".format(radioCfg.region.maxLegalErpDbm))
                        }

                        // Editierbare Felder
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = setup.freqMhz, enabled = inputEnabled, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(freqMhz = it)) }, label = { Text("Frequenz (MHz)", fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                            OutlinedTextField(value = setup.bwKhz, enabled = inputEnabled, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(bwKhz = it)) }, label = { Text("Bandbreite (kHz)", fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = setup.sf, enabled = inputEnabled, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(sf = it)) }, label = { Text("SF (5..12)", fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                            OutlinedTextField(value = setup.cr, enabled = inputEnabled, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(cr = it)) }, label = { Text("CR (5..8)", fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                            OutlinedTextField(value = setup.dutyCyclePercent, enabled = inputEnabled, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(dutyCyclePercent = it)) }, label = { Text("Duty %", fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Antenne & Kabel
                        Text("Antennen-Preset & Kabeldämpfung:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = bColors.cobalt)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AntennaPreset.values().forEach { preset ->
                                FilterChip(
                                    shape = BauhausCornerShape,
                                    enabled = inputEnabled,
                                    selected = radioCfg.antennaPreset == preset,
                                    onClick = { viewModel.setAntennaPreset(preset) },
                                    label = { Text(preset.label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = bColors.cobalt,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (radioCfg.antennaPreset.isCustom) {
                                OutlinedTextField(
                                    value = customGainInput,
                                    enabled = inputEnabled,
                                    onValueChange = { customGainInput = it; it.replace(',', '.').toDoubleOrNull()?.let { dBi -> viewModel.setCustomAntennaGain(dBi) } },
                                    label = { Text("Eigener Gewinn (dBi)", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = BauhausCornerShape
                                )
                            }
                            OutlinedTextField(
                                value = cableLossInput,
                                enabled = inputEnabled,
                                onValueChange = { cableLossInput = it; it.replace(',', '.').toDoubleOrNull()?.let { l -> viewModel.setCableLoss(l) } },
                                label = { Text("Kabelverlust (dB)", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = BauhausCornerShape
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Compliance & Slider
                        Text(
                            "AUTOMATISCHE ERP/EIRP-COMPLIANCE",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (liveRadioCfg.isCompliant) bColors.cobalt else bColors.signalRed,
                            fontWeight = FontWeight.Bold
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Transceiver TX", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${liveRadioCfg.txPowerDbm.roundToInt()} dBm", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Column {
                                Text("Eff. Gain (G-L)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("+%.1f dBi".format(liveRadioCfg.effectiveGainDbi - liveRadioCfg.cableLossDb), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Column {
                                Text("Berechnetes ERP", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("%.1f dBm (%.0f mW)".format(liveRadioCfg.calculatedErpDbm, liveRadioCfg.calculatedErpMw), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (liveRadioCfg.isCompliant) bColors.cobalt else bColors.signalRed)
                            }
                            Column {
                                Text("Gesetzl. Limit", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("%.1f dBm ERP".format(radioCfg.region.maxLegalErpDbm), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = bColors.signalRed)
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Transceiver TX-Power: ${liveRadioCfg.txPowerDbm.roundToInt()} dBm", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Max. legal: ${liveRadioCfg.maxAllowedTxPowerDbm.roundToInt()} dBm", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (liveRadioCfg.isCompliant) bColors.cobalt else bColors.signalRed)
                        }

                        Slider(
                            value = liveRadioCfg.txPowerDbm.toFloat().coerceIn(radioCfg.region.minTxPowerDbm.toFloat(), radioCfg.region.maxTxPowerDbm.toFloat()),
                            enabled = inputEnabled,
                            onValueChange = {
                                val rounded = it.roundToInt()
                                viewModel.setTxPower(rounded.toDouble())
                                viewModel.updateBasicSetupDraft(setup.copy(txPowerDbm = rounded.toString()))
                            },
                            valueRange = radioCfg.region.minTxPowerDbm.toFloat()..radioCfg.region.maxTxPowerDbm.toFloat(),
                            steps = (radioCfg.region.maxTxPowerDbm - radioCfg.region.minTxPowerDbm - 1).coerceAtLeast(0),
                            colors = SliderDefaults.colors(
                                thumbColor = if (liveRadioCfg.isCompliant) bColors.cobalt else bColors.signalRed,
                                activeTrackColor = if (liveRadioCfg.isCompliant) bColors.cobalt else bColors.signalRed
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(if (radioCfg.complianceUnlocked) Icons.Default.LockOpen else Icons.Default.Lock, contentDescription = null, tint = if (radioCfg.complianceUnlocked) bColors.signalRed else bColors.cobalt)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Sicherheits-Sperre aufheben", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text(if (radioCfg.complianceUnlocked) "Freigegeben für Test- & Lizenzbetrieb" else "Begrenzt TX auf max. legalen Wert", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            BauhausSwitch(checked = radioCfg.complianceUnlocked, enabled = inputEnabled, onCheckedChange = { viewModel.setComplianceUnlocked(it) })
                        }

                        if (!liveRadioCfg.isCompliant && !radioCfg.complianceUnlocked) {
                            OutlinedButton(
                                shape = BauhausCornerShape,
                                enabled = inputEnabled,
                                border = BorderStroke(1.dp, bColors.signalRed),
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    val legalTx = liveRadioCfg.maxAllowedTxPowerDbm.toInt().coerceIn(radioCfg.region.minTxPowerDbm, radioCfg.region.maxTxPowerDbm)
                                    viewModel.setTxPower(legalTx.toDouble())
                                    viewModel.updateBasicSetupDraft(setup.copy(txPowerDbm = legalTx.toString()))
                                }
                            ) { Text("TX automatisch auf ${liveRadioCfg.maxAllowedTxPowerDbm.toInt()} dBm (EU-Limit) begrenzen", fontSize = 11.sp, color = bColors.signalRed, fontWeight = FontWeight.Bold) }
                        }
                    }
                }

                // 2. IDENTITÄT & STANDORT
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = BauhausCornerShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("2. IDENTITÄT & STANDORT", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = bColors.cobalt)
                        Text("Node Public Key (Identität bleibt geschützt):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (setup.publicKeyHex.isNotBlank()) setup.publicKeyHex else "(Wird beim Verbinden ermittelt)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(8.dp)
                        )
                        OutlinedButton(
                            shape = BauhausCornerShape,
                            enabled = inputEnabled,
                            border = BorderStroke(1.dp, bColors.ochre),
                            onClick = { showNewKeyDialog = true }
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = bColors.ochre, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Node-Schlüssel neu setzen (nur bei Neuaufbau)", fontSize = 11.sp, color = bColors.ochre)
                        }
                        OutlinedTextField(value = setup.nodeName, enabled = inputEnabled, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(nodeName = it)) }, label = { Text("Repeater-Name (set name)", fontSize = 12.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = BauhausCornerShape)
                        OutlinedTextField(value = setup.ownerInfo, enabled = inputEnabled, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(ownerInfo = it)) }, label = { Text("Besitzer / Kontakt-Info (set owner.info)", fontSize = 12.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = BauhausCornerShape)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = setup.latitude, enabled = inputEnabled, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(latitude = it)) }, label = { Text("Breitengrad (lat)", fontSize = 12.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                            OutlinedTextField(value = setup.longitude, enabled = inputEnabled, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(longitude = it)) }, label = { Text("Längengrad (lon)", fontSize = 12.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(modifier = Modifier.weight(1f), enabled = inputEnabled, shape = BauhausCornerShape, onClick = { showMapPickerDialog = true }) {
                                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Auf Karte wählen", fontSize = 11.sp, color = bColors.cobalt)
                            }
                            OutlinedButton(modifier = Modifier.weight(1f), enabled = inputEnabled, shape = BauhausCornerShape, onClick = { viewModel.sendCommand("gps setloc", manualCli = false) }) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("GPS übernehmen", fontSize = 11.sp)
                            }
                        }
                    }
                }

                // 3. ZUGRIFFSKONTROLLE
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = BauhausCornerShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("3. ZUGRIFFSKONTROLLE", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = bColors.cobalt)
                        OutlinedTextField(value = newAdminPassword, enabled = inputEnabled, onValueChange = { newAdminPassword = it }, label = { Text("Neues Admin-Passwort (leer = unverändert)", fontSize = 12.sp) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = BauhausCornerShape)
                        OutlinedTextField(value = newGuestPassword, enabled = inputEnabled, onValueChange = { newGuestPassword = it; if (it.isNotEmpty()) clearGuestPassword = false }, label = { Text("Gast-Passwort (leer = unverändert)", fontSize = 12.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = BauhausCornerShape)
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Gast-Passwort löschen (offener Zugang)", fontSize = 12.sp)
                            Checkbox(checked = clearGuestPassword, enabled = inputEnabled, onCheckedChange = { clearGuestPassword = it; if (it) newGuestPassword = "" })
                        }
                    }
                }

                // 4. Mesh-Routing, Adverts, Timing & LEDs
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = BauhausCornerShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("4. ROUTING, ADVERTS & HARDWARE", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = bColors.cobalt)
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Repeater-Weiterleitung aktiv (set repeat)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            BauhausSwitch(checked = setup.repeatEnabled, enabled = inputEnabled, onCheckedChange = { viewModel.updateBasicSetupDraft(setup.copy(repeatEnabled = it)) })
                        }
                        Text("Pfad-Hash-Modus (Node-ID Breite):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "1-Byte (0)", 1 to "2-Byte (1)", 2 to "3-Byte (2)").forEach { (mode, label) ->
                                FilterChip(selected = setup.pathHashMode == mode, enabled = inputEnabled, onClick = { viewModel.updateBasicSetupDraft(setup.copy(pathHashMode = mode)) }, label = { Text(label, fontSize = 11.sp) }, shape = BauhausCornerShape)
                            }
                        }
                        Text("Schleifen-Erkennung (loop.detect):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("off", "minimal", "moderate", "strict").forEach { mode ->
                                FilterChip(selected = setup.loopDetect.equals(mode, true), enabled = inputEnabled, onClick = { viewModel.updateBasicSetupDraft(setup.copy(loopDetect = mode)) }, label = { Text(mode, fontSize = 11.sp) }, shape = BauhausCornerShape)
                            }
                        }
                        Text("Status-LEDs (set leds):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("on", "off", "boot").forEach { mode ->
                                FilterChip(selected = setup.ledsMode.equals(mode, true), enabled = inputEnabled, onClick = { viewModel.updateBasicSetupDraft(setup.copy(ledsMode = mode)) }, label = { Text(mode, fontSize = 11.sp) }, shape = BauhausCornerShape)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = setup.advertIntervalMin, enabled = inputEnabled, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(advertIntervalMin = it)) }, label = { Text("Advert (Min)", fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                            OutlinedTextField(value = setup.floodAdvertIntervalHours, enabled = inputEnabled, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(floodAdvertIntervalHours = it)) }, label = { Text("Flood-Adv (Std)", fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                        }

                        // Timing & Paketverzögerung
                        val isAdaptiveDelays = setup.txDelaySec == "adaptive" || setup.directTxDelaySec == "adaptive"
                        Text("Paket-Verzögerung (Delays):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = isAdaptiveDelays,
                                enabled = inputEnabled,
                                onClick = {
                                    viewModel.updateBasicSetupDraft(
                                        setup.copy(
                                            txDelaySec = "adaptive",
                                            directTxDelaySec = "adaptive",
                                            rxDelaySec = if (setup.rxDelaySec == "adaptive") "adaptive" else "0"
                                        )
                                    )
                                },
                                label = { Text("Adaptiv (Standard)", fontSize = 11.sp) },
                                shape = BauhausCornerShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = bColors.cobalt,
                                    selectedLabelColor = Color.White
                                )
                            )
                            FilterChip(
                                selected = !isAdaptiveDelays,
                                enabled = inputEnabled,
                                onClick = {
                                    viewModel.updateBasicSetupDraft(
                                        setup.copy(
                                            txDelaySec = if (setup.txDelaySec == "adaptive") "0.5" else setup.txDelaySec,
                                            directTxDelaySec = if (setup.directTxDelaySec == "adaptive") "0.3" else setup.directTxDelaySec,
                                            rxDelaySec = if (setup.rxDelaySec == "adaptive") "0" else setup.rxDelaySec
                                        )
                                    )
                                },
                                label = { Text("Manuell", fontSize = 11.sp) },
                                shape = BauhausCornerShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = bColors.cobalt,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                        if (isAdaptiveDelays) {
                            Text(
                                "MeshCore / ZephCore v1.14+ nutzt standardmäßig adaptive Delays nach Spreading-Faktor & Airtime.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = setup.txDelaySec, enabled = inputEnabled, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(txDelaySec = it)) }, label = { Text("TX-Delay (s)", fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                                OutlinedTextField(value = setup.directTxDelaySec, enabled = inputEnabled, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(directTxDelaySec = it)) }, label = { Text("Direct-TX (s)", fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                                OutlinedTextField(value = setup.rxDelaySec, enabled = inputEnabled, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(rxDelaySec = it)) }, label = { Text("RX-Delay (s)", fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Mehrfache Empfangsbestätigungen (multi.acks = 1)", fontSize = 12.sp)
                            BauhausSwitch(checked = setup.multiAcks == 1, enabled = inputEnabled, onCheckedChange = { viewModel.updateBasicSetupDraft(setup.copy(multiAcks = if (it) 1 else 0)) })
                        }
                    }
                }

                // 5. Speichern & Aktionen
                val pending = buildPendingCommands()
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    shape = BauhausCornerShape,
                    colors = ButtonDefaults.buttonColors(containerColor = bColors.signalRed),
                    enabled = !setup.isReading,
                    onClick = { executeApplyChanges() }
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(if (pending.isNotEmpty()) "Geänderte Einstellungen senden (${pending.size})" else "Geänderte Einstellungen senden", color = Color.White, fontWeight = FontWeight.Bold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(modifier = Modifier.weight(1f), shape = BauhausCornerShape, onClick = { viewModel.sendCommand("advert", manualCli = false) }) {
                        Text("Advert senden", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = bColors.cobalt)
                    }
                    OutlinedButton(modifier = Modifier.weight(1f), shape = BauhausCornerShape, border = BorderStroke(1.dp, bColors.signalRed), onClick = { showRebootConfirm = true }) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, tint = bColors.signalRed, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Neu starten", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = bColors.signalRed)
                    }
                }

                val totalDebugCount = terminalLines.size + systemLines.size
                val allDebugLines = remember(terminalLines, systemLines) {
                    (terminalLines + systemLines).takeLast(25)
                }

                OutlinedButton(modifier = Modifier.fillMaxWidth(), shape = BauhausCornerShape, onClick = { showDebugLog = !showDebugLog }) {
                    Text(if (showDebugLog) "Debug- & Fehlermeldungen ausblenden" else "Debug- & Fehlermeldungen anzeigen ($totalDebugCount)", fontSize = 11.sp)
                }
                if (showDebugLog) {
                    Card(modifier = Modifier.fillMaxWidth(), shape = BauhausCornerShape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (allDebugLines.isEmpty()) {
                                Text(
                                    text = "Noch keine Log- oder Fehlermeldungen vorhanden",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            } else {
                                allDebugLines.forEach { entry ->
                                    Text(
                                        entry.text,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = if (entry.text.contains("Fehler", ignoreCase = true) || entry.text.contains("??")) bColors.signalRed else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Standort-Wahl auf Karte: Großer Dialog (92% Breite, 80% Höhe)
    if (showMapPickerDialog) {
        var pickLat by remember { mutableStateOf(setup.latitude.toDoubleOrNull() ?: 51.881000) }
        var pickLon by remember { mutableStateOf(setup.longitude.toDoubleOrNull() ?: 10.562000) }
        var webViewRef by remember { mutableStateOf<WebView?>(null) }

        Dialog(
            onDismissRequest = { showMapPickerDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.80f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, bColors.cobalt)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Map, contentDescription = null, tint = bColors.cobalt)
                            Text("Standort auf Karte wählen", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = bColors.cobalt)
                        }
                        IconButton(onClick = { showMapPickerDialog = false }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Schließen", modifier = Modifier.size(20.dp))
                        }
                    }

                    Text(
                        "Karte verschieben & zoomen (Pin bleibt im Zentrum) oder 200m-Privatsphäre-Versatz anwenden:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    webViewRef = this
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.userAgentString = "MeshCoreRepeaterConfigurator/0.85 (Android)"
                                    webViewClient = WebViewClient()
                                    addJavascriptInterface(object {
                                        @JavascriptInterface
                                        fun onPick(lat: Double, lon: Double) {
                                            pickLat = lat
                                            pickLon = lon
                                        }
                                    }, "AndroidMap")
                                    val html = """
                                        <!DOCTYPE html>
                                        <html>
                                        <head>
                                        <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no" />
                                        <style>
                                          * { box-sizing: border-box; margin: 0; padding: 0; user-select: none; -webkit-user-select: none; }
                                          html, body { width: 100%; height: 100%; overflow: hidden; background: #1a1a1a; font-family: sans-serif; }
                                          #map { width: 100%; height: 100%; position: relative; overflow: hidden; cursor: grab; }
                                          #tiles { position: absolute; top: 0; left: 0; width: 100%; height: 100%; }
                                          .tile { position: absolute; width: 256px; height: 256px; pointer-events: none; }
                                          #pin { position: absolute; top: 50%; left: 50%; transform: translate(-50%, -100%); pointer-events: none; z-index: 100; font-size: 34px; filter: drop-shadow(0 2px 5px rgba(0,0,0,0.7)); }
                                          #controls { position: absolute; right: 12px; bottom: 16px; z-index: 200; display: flex; flex-direction: column; gap: 8px; }
                                          .btn { width: 42px; height: 42px; border-radius: 8px; background: rgba(30,30,30,0.9); color: #fff; font-size: 22px; font-weight: bold; border: 1px solid #666; display: flex; align-items: center; justify-content: center; }
                                        </style>
                                        </head>
                                        <body>
                                        <div id="map">
                                          <div id="tiles"></div>
                                          <div id="pin">📍</div>
                                          <div id="controls">
                                            <button class="btn" onclick="zoomIn()">+</button>
                                            <button class="btn" onclick="zoomOut()">−</button>
                                          </div>
                                        </div>
                                        <script>
                                        var lat = $pickLat;
                                        var lon = $pickLon;
                                        var zoom = 14;

                                        function latLonToWorld(lat, lon, z) {
                                          var n = Math.pow(2, z);
                                          var x = (lon + 180) / 360 * n * 256;
                                          var latRad = lat * Math.PI / 180;
                                          var y = (1 - Math.log(Math.tan(latRad) + 1 / Math.cos(latRad)) / Math.PI) / 2 * n * 256;
                                          return {x: x, y: y};
                                        }

                                        function worldToLatLon(x, y, z) {
                                          var n = Math.pow(2, z);
                                          var lon = (x / (256 * n)) * 360 - 180;
                                          var m = Math.PI - 2 * Math.PI * (y / (256 * n));
                                          var lat = 180 / Math.PI * Math.atan(0.5 * (Math.exp(m) - Math.exp(-m)));
                                          return {lat: lat, lon: lon};
                                        }

                                        var tilesDiv = document.getElementById('tiles');
                                        var mapDiv = document.getElementById('map');

                                        function render() {
                                          var w = mapDiv.clientWidth || window.innerWidth;
                                          var h = mapDiv.clientHeight || window.innerHeight;
                                          if (w <= 0 || h <= 0) return;
                                          var centerWorld = latLonToWorld(lat, lon, zoom);
                                          var originX = centerWorld.x - w / 2;
                                          var originY = centerWorld.y - h / 2;

                                          var minTileX = Math.floor(originX / 256);
                                          var maxTileX = Math.floor((originX + w) / 256);
                                          var minTileY = Math.floor(originY / 256);
                                          var maxTileY = Math.floor((originY + h) / 256);
                                          var maxCoord = Math.pow(2, zoom);

                                          var newTiles = {};
                                          for (var tx = minTileX; tx <= maxTileX; tx++) {
                                            for (var ty = minTileY; ty <= maxTileY; ty++) {
                                              if (ty < 0 || ty >= maxCoord) continue;
                                              var wrapTx = ((tx % maxCoord) + maxCoord) % maxCoord;
                                              var key = zoom + '_' + wrapTx + '_' + ty;
                                              newTiles[key] = true;
                                              var img = document.getElementById(key);
                                              if (!img) {
                                                img = document.createElement('img');
                                                img.id = key;
                                                img.className = 'tile';
                                                img.src = 'https://tile.openstreetmap.org/' + zoom + '/' + wrapTx + '/' + ty + '.png';
                                                tilesDiv.appendChild(img);
                                              }
                                              img.style.left = Math.round(tx * 256 - originX) + 'px';
                                              img.style.top = Math.round(ty * 256 - originY) + 'px';
                                            }
                                          }
                                          var existing = tilesDiv.querySelectorAll('.tile');
                                          for (var i = 0; i < existing.length; i++) {
                                            if (!newTiles[existing[i].id]) {
                                              tilesDiv.removeChild(existing[i]);
                                            }
                                          }
                                          if (window.AndroidMap) {
                                            window.AndroidMap.onPick(lat, lon);
                                          }
                                        }

                                        function setCenter(newLat, newLon) {
                                          lat = Math.max(-85, Math.min(85, newLat));
                                          lon = ((newLon + 180) % 360 + 360) % 360 - 180;
                                          render();
                                        }

                                        function zoomIn() { if (zoom < 18) { zoom++; render(); } }
                                        function zoomOut() { if (zoom > 2) { zoom--; render(); } }

                                        var isDragging = false;
                                        var startX = 0, startY = 0;
                                        var startWorldX = 0, startWorldY = 0;

                                        function startDrag(clientX, clientY) {
                                          isDragging = true;
                                          startX = clientX;
                                          startY = clientY;
                                          var w = latLonToWorld(lat, lon, zoom);
                                          startWorldX = w.x;
                                          startWorldY = w.y;
                                        }

                                        function moveDrag(clientX, clientY) {
                                          if (!isDragging) return;
                                          var dx = clientX - startX;
                                          var dy = clientY - startY;
                                          var newWorldX = startWorldX - dx;
                                          var newWorldY = startWorldY - dy;
                                          var ll = worldToLatLon(newWorldX, newWorldY, zoom);
                                          lat = Math.max(-85, Math.min(85, ll.lat));
                                          lon = ((ll.lon + 180) % 360 + 360) % 360 - 180;
                                          render();
                                        }

                                        function endDrag() { isDragging = false; }

                                        mapDiv.addEventListener('mousedown', function(e) { startDrag(e.clientX, e.clientY); });
                                        window.addEventListener('mousemove', function(e) { moveDrag(e.clientX, e.clientY); });
                                        window.addEventListener('mouseup', function(e) { endDrag(); });

                                        mapDiv.addEventListener('touchstart', function(e) {
                                          if (e.touches.length === 1) {
                                            startDrag(e.touches[0].clientX, e.touches[0].clientY);
                                          }
                                        }, {passive: true});

                                        window.addEventListener('touchmove', function(e) {
                                          if (e.touches.length === 1) {
                                            moveDrag(e.touches[0].clientX, e.touches[0].clientY);
                                          }
                                        }, {passive: true});

                                        window.addEventListener('touchend', function() { endDrag(); });
                                        window.addEventListener('resize', function() { render(); });

                                        setTimeout(render, 100);
                                        </script>
                                        </body>
                                        </html>
                                    """.trimIndent()
                                    loadDataWithBaseURL("https://tile.openstreetmap.org", html, "text/html", "UTF-8", null)
                                }
                            }
                        )
                    }

                    Text(
                        text = "Lat: ${String.format(Locale.US, "%.6f", pickLat)}  |  Lon: ${String.format(Locale.US, "%.6f", pickLon)}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text("200m-Privatsphäre-Versatz (Schutz des genauen Standorts):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val deltaLat = 200.0 / 111139.0
                        val deltaLon = 200.0 / (111139.0 * cos(Math.toRadians(pickLat)).coerceAtLeast(0.01))

                        fun applyOffset(dLat: Double, dLon: Double) {
                            pickLat += dLat
                            pickLon += dLon
                            webViewRef?.evaluateJavascript("setCenter($pickLat, $pickLon)", null)
                        }

                        OutlinedButton(shape = BauhausCornerShape, onClick = { applyOffset(deltaLat, 0.0) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("+200m N", fontSize = 10.sp) }
                        OutlinedButton(shape = BauhausCornerShape, onClick = { applyOffset(-deltaLat, 0.0) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("+200m S", fontSize = 10.sp) }
                        OutlinedButton(shape = BauhausCornerShape, onClick = { applyOffset(0.0, deltaLon) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("+200m O", fontSize = 10.sp) }
                        OutlinedButton(shape = BauhausCornerShape, onClick = { applyOffset(0.0, -deltaLon) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("+200m W", fontSize = 10.sp) }
                        OutlinedButton(
                            shape = BauhausCornerShape,
                            onClick = {
                                val angle = Math.random() * 2 * Math.PI
                                applyOffset(deltaLat * kotlin.math.cos(angle), deltaLon * kotlin.math.sin(angle))
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("🎲 200m Zufalls-Versatz", fontSize = 10.sp, color = bColors.cobalt, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            shape = BauhausCornerShape,
                            modifier = Modifier.weight(1f),
                            onClick = { showMapPickerDialog = false }
                        ) {
                            Text("Abbrechen")
                        }
                        Button(
                            shape = BauhausCornerShape,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = bColors.cobalt),
                            onClick = {
                                viewModel.updateBasicSetupDraft(
                                    setup.copy(
                                        latitude = String.format(Locale.US, "%.6f", pickLat),
                                        longitude = String.format(Locale.US, "%.6f", pickLon)
                                    )
                                )
                                showMapPickerDialog = false
                            }
                        ) {
                            Text("Standort übernehmen", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showNewKeyDialog) {
        AlertDialog(
            onDismissRequest = { showNewKeyDialog = false },
            shape = BauhausCornerShape,
            title = { Text("Node-Identität neu setzen?", fontWeight = FontWeight.Bold, color = bColors.signalRed) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Achtung: Eine Änderung des Private Keys ändert die Mesh-Adresse des Repeaters!", fontSize = 12.sp)
                    OutlinedTextField(value = customPrivKeyHex, onValueChange = { customPrivKeyHex = it }, label = { Text("Neuer Private Key (64-Byte Hex)", fontSize = 11.sp) }, singleLine = true, shape = BauhausCornerShape, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(shape = BauhausCornerShape, colors = ButtonDefaults.buttonColors(containerColor = bColors.signalRed), enabled = customPrivKeyHex.trim().length >= 64, onClick = {
                    val k = customPrivKeyHex.trim(); customPrivKeyHex = ""; showNewKeyDialog = false; viewModel.sendCommand("set prv.key $k", manualCli = false)
                }) { Text("Schlüssel setzen", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { OutlinedButton(shape = BauhausCornerShape, onClick = { showNewKeyDialog = false }) { Text("Abbrechen") } }
        )
    }

    if (showRebootConfirm) {
        AlertDialog(
            onDismissRequest = { showRebootConfirm = false },
            shape = BauhausCornerShape,
            title = { Text("Repeater neu starten?", fontWeight = FontWeight.Bold, color = bColors.signalRed) },
            text = { Text("Der Repeater wird neu gestartet, um alle gespeicherten Einstellungen zu übernehmen.") },
            confirmButton = {
                Button(shape = BauhausCornerShape, colors = ButtonDefaults.buttonColors(containerColor = bColors.signalRed), onClick = { showRebootConfirm = false; viewModel.sendCommand("reboot", manualCli = false) }) {
                    Text("Neu starten", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { OutlinedButton(shape = BauhausCornerShape, onClick = { showRebootConfirm = false }) { Text("Abbrechen") } }
        )
    }
}

@Composable
private fun ParamPill(title: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(title, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
    }
}
