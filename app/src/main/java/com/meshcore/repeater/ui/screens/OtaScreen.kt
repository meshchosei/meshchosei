package com.meshcore.repeater.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshcore.repeater.i18n.tr
import com.meshcore.repeater.model.ConnectionState
import com.meshcore.repeater.model.DfuKind
import com.meshcore.repeater.model.DfuScanRules
import com.meshcore.repeater.model.DfuTarget
import com.meshcore.repeater.model.FirmwareFileRules
import com.meshcore.repeater.model.OtaBootloaderRequest
import com.meshcore.repeater.model.OtaPhase
import com.meshcore.repeater.model.OtaTargetRules
import com.meshcore.repeater.ui.components.BauhausSwitch
import com.meshcore.repeater.ui.components.BrailleSpinner
import com.meshcore.repeater.ui.components.SlideToConfirm
import com.meshcore.repeater.ui.components.hintedVerticalScroll
import com.meshcore.repeater.ui.theme.BauhausCornerShape
import com.meshcore.repeater.ui.theme.LocalBauhausColors
import com.meshcore.repeater.viewmodel.RepeaterViewModel
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Firmware / OTA-Update (v1.26, STYLE_GUIDE §2.16): ruhiger Bauhaus-Screen in drei Schritten –
 * 1. Paket wählen, 2. Zielgerät wählen (A: `start ota` an den verbundenen Repeater, B: BLE-Scan nach DFU-Geräten),
 * 3. Schieberegler-Bestätigung und Fortschritt in vier Phasen. Keine Expertenoptionen.
 */
@Composable
fun OtaScreen(viewModel: RepeaterViewModel) {
    val bColors = LocalBauhausColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ota by viewModel.otaState.collectAsState()
    val devices by viewModel.dfuDevices.collectAsState()
    val scanning by viewModel.isScanningDfu.collectAsState()
    val request by viewModel.otaBootloaderRequest.collectAsState()
    val connection by viewModel.connectionState.collectAsState()
    val caps by viewModel.deviceCapabilities.collectAsState()
    val remote by viewModel.remoteAdminSession.collectAsState()
    val hw by viewModel.hardwareInfo.collectAsState()
    val radio by viewModel.radioConfig.collectAsState()
    val autoReconnect by viewModel.otaAutoReconnect.collectAsState()
    val running = ota.phase.isRunning
    var fileError by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }

    // Bluetooth-Berechtigungen (Scan + Verbinden); danach sofort weiter
    val blePerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    fun hasBlePerms() = blePerms.all { context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED }
    var afterPerms by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) afterPerms?.invoke() else fileError = tr("Ohne Bluetooth-Berechtigung ist kein Firmware-Update möglich.")
        afterPerms = null
    }
    fun withBle(action: () -> Unit) { if (hasBlePerms()) action() else { afterPerms = action; permLauncher.launch(blePerms) } }
    // Android 13+: Benachrichtigung des DFU-Dienstes (optional – das Update läuft auch ohne)
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.startFirmwareUpdate() }
    fun startUpdate() {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else viewModel.startFirmwareUpdate()
    }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true; fileError = null
        scope.launch {
            val r = viewModel.importFirmware(uri)
            importing = false
            fileError = when (r) {
                FirmwareFileRules.Check.Ok -> null
                FirmwareFileRules.Check.NotZip -> tr("Bitte ein Nordic-DFU-Paket (.zip) wählen.")
                FirmwareFileRules.Check.Empty -> tr("Die Datei ist leer.")
                FirmwareFileRules.Check.TooLarge -> tr("Die Datei ist zu groß für ein Firmware-Paket.")
                FirmwareFileRules.Check.NotDfuPackage -> tr("Kein Nordic-DFU-Paket: manifest.json bzw. .bin + .dat fehlen.")
                FirmwareFileRules.Check.Corrupt -> tr("Datei konnte nicht gelesen werden.")
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).hintedVerticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---------- 1. Firmware-Paket ----------
        StepCard(1, tr("FIRMWARE-PAKET WÄHLEN"), bColors.cobalt, done = ota.firmware != null) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(), shape = BauhausCornerShape, enabled = !running && !importing,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                onClick = { openLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }
            ) {
                if (importing) BrailleSpinner(bColors.cobalt, fontSize = 14.sp) else Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr("Nordic-DFU-Paket (.zip) öffnen"), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ota.firmware?.let { fw ->
                InfoRow(tr("Datei"), fw.displayName)
                InfoRow(tr("Größe"), FirmwareFileRules.formatSize(fw.sizeBytes))
            }
            fileError?.let { Text(it, fontSize = 12.sp, color = bColors.signalRed) }
            Notice(
                tr("Nur Firmware flashen, die genau für dieses Board gebaut ist. Eine falsche Firmware kann das Gerät unbrauchbar machen."),
                bColors.ochre
            )
            if (hw.boardModel.isNotBlank()) Text(tr("Verbundenes Board: {0}", hw.boardModel), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // ---------- 2. Zielgerät ----------
        StepCard(2, tr("ZIELGERÄT WÄHLEN"), bColors.signalRed, done = ota.target != null) {
            ota.target?.let { t ->
                TargetRow(t, selected = true, onClick = null)
                if (!running) TextButton(onClick = { viewModel.clearDfuTarget() }, shape = BauhausCornerShape) { Text(tr("Anderes Gerät wählen"), fontSize = 12.sp) }
            }
            if (ota.target == null) {
                // Option A: verbundenen Repeater in den Bootloader versetzen
                Text(tr("A · Verbundenen Repeater in den DFU-Bootloader versetzen"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                val nordic = OtaTargetRules.nordicCapable(radio.board.mcu)
                val canA = connection is ConnectionState.Connected && (caps.isDirectRepeater || remote.isLoggedIn) && nordic != false
                Text(
                    when {
                        nordic == false && connection is ConnectionState.Connected -> tr("Dieses Board ({0}) nutzt kein Nordic BLE DFU. ESP32-Geräte werden per WLAN- bzw. MCUmgr-OTA aktualisiert.", radio.board.mcu)
                        canA -> tr("Sendet \"start ota\" und sucht danach automatisch das Gerät im DFU-Modus. Es muss in Bluetooth-Reichweite dieses Smartphones sein.")
                        else -> tr("Verfügbar, wenn ein Repeater direkt per USB verbunden oder per Remote-Admin eingeloggt ist.")
                    },
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(), shape = BauhausCornerShape, enabled = canA && !request.isBusy && !running,
                    border = BorderStroke(1.dp, if (canA) bColors.signalRed else MaterialTheme.colorScheme.outline),
                    onClick = { withBle { viewModel.requestBootloader() } }
                ) {
                    if (request.isBusy) BrailleSpinner(bColors.signalRed, fontSize = 14.sp)
                    else Text(tr("Bootloader aktivieren (start ota)"), fontSize = 12.sp, color = if (canA) bColors.signalRed else Color.Unspecified, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                when (val r = request) {
                    OtaBootloaderRequest.Sending -> StatusLine(tr("Sende \"start ota\"…"))
                    is OtaBootloaderRequest.Searching -> StatusLine(if (r.mac != null) tr("Suche DFU-Gerät {0}…", r.mac) else tr("Suche DFU-Gerät…"))
                    OtaBootloaderRequest.NotFound -> StatusLine(tr("Kein eindeutiges DFU-Gerät gefunden – bitte unten aus der Liste wählen."), bColors.ochre)
                    is OtaBootloaderRequest.Failed -> StatusLine(
                        if (DfuScanRules.parseOtaReply(r.reply).nonNordic) tr("Kein Nordic-DFU-Gerät (ESP32-OTA): {0}", r.reply) else tr("Der Repeater hat \"start ota\" abgelehnt: {0}", r.reply),
                        bColors.signalRed
                    )
                    else -> {}
                }
                Spacer(Modifier.height(4.dp))
                // Option B: Geräte im DFU-Modus suchen
                Text(tr("B · Gerät im DFU-Modus suchen"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(tr("Findet Bootloader (DfuTarg, AdaDFU) und Nodes nach \"start ota\" (…_OTA)."), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(), shape = BauhausCornerShape, enabled = !running,
                    border = BorderStroke(1.dp, bColors.cobalt),
                    onClick = { if (scanning) viewModel.stopDfuScan() else withBle { viewModel.startDfuScan() } }
                ) {
                    if (scanning) { BrailleSpinner(bColors.cobalt, fontSize = 14.sp); Spacer(Modifier.width(6.dp)); Text(tr("Suche stoppen"), fontSize = 12.sp, color = bColors.cobalt) }
                    else { Icon(Icons.Default.Bluetooth, null, tint = bColors.cobalt, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(tr("DFU-Geräte suchen"), fontSize = 12.sp, color = bColors.cobalt) }
                }
                if (devices.isEmpty() && scanning) StatusLine(tr("Suche läuft…"))
                devices.forEach { d -> TargetRow(d, selected = false, onClick = { viewModel.chooseDfuTarget(d) }) }
            }
        }

        // ---------- 3. Bestätigen & Fortschritt ----------
        StepCard(3, tr("BESTÄTIGEN & FLASHEN"), bColors.successGreen, done = ota.phase == OtaPhase.COMPLETED) {
            // v1.26: nach erfolgreichem Update wieder mit dem vorherigen Node verbinden (und Repeater-Login erneuern)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Nach Update automatisch neu verbinden"), fontSize = 12.sp, modifier = Modifier.weight(1f))
                BauhausSwitch(checked = autoReconnect, onCheckedChange = { viewModel.setOtaAutoReconnect(it) }, activeColor = bColors.successGreen)
            }
            if (ota.phase == OtaPhase.IDLE) {
                Text(
                    if (ota.canStart) tr("Bestehende Verbindungen werden vor dem Update getrennt. Das Smartphone bleibt währenddessen wach – bitte die App geöffnet lassen.")
                    else tr("Erst Firmware-Paket und Zielgerät wählen."),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (ota.canStart) SlideToConfirm(text = tr("Zum Flashen schieben"), onConfirmed = { startUpdate() })
            } else {
                PhaseList(ota.phase, ota.stoppedAtStep)
                if (ota.phase == OtaPhase.UPLOADING || ota.phase == OtaPhase.COMPLETED) {
                    SquareProgressBar(ota.percent / 100f, bColors.successGreen)
                    Row(Modifier.fillMaxWidth()) {
                        Text("${ota.percent} %", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        if (ota.phase == OtaPhase.UPLOADING) Text(String.format(Locale.ROOT, "%.1f kB/s", ota.speedKbs), fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (ota.partsTotal > 1) Text(tr("Teil {0} von {1}", ota.currentPart, ota.partsTotal), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (ota.phase) {
                    OtaPhase.COMPLETED -> {
                        Notice(tr("Update abgeschlossen. Das Gerät startet mit der neuen Firmware neu."), bColors.successGreen)
                        if (autoReconnect) StatusLine(tr("Die App verbindet sich nach dem Neustart automatisch wieder."))
                    }
                    OtaPhase.ABORTED -> Notice(tr("Update abgebrochen. Das Gerät bleibt im Bootloader und kann erneut geflasht werden."), bColors.ochre)
                    OtaPhase.ERROR -> Notice(tr("Update fehlgeschlagen: {0}", ota.message ?: "—"), bColors.signalRed)
                    else -> {}
                }
                if (running) OutlinedButton(onClick = { viewModel.abortOta() }, shape = BauhausCornerShape, border = BorderStroke(1.dp, bColors.signalRed), modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Abbrechen"), fontSize = 12.sp, color = bColors.signalRed)
                } else OutlinedButton(onClick = { viewModel.resetOta() }, shape = BauhausCornerShape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Zurücksetzen"), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun StepCard(number: Int, title: String, color: Color, done: Boolean, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = BauhausCornerShape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(22.dp).clip(BauhausCornerShape).background(color), contentAlignment = Alignment.Center) {
                    Text("$number", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color, modifier = Modifier.weight(1f))
                if (done) Icon(Icons.Default.CheckCircle, null, tint = LocalBauhausColors.current.successGreen, modifier = Modifier.size(18.dp))
            }
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
        Text(value, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Notice(text: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().clip(BauhausCornerShape).background(color.copy(alpha = 0.12f)).border(1.dp, color.copy(alpha = 0.6f), BauhausCornerShape).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Default.Warning, null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun StatusLine(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) { Text(text, fontSize = 11.sp, color = color) }

@Composable
private fun TargetRow(t: DfuTarget, selected: Boolean, onClick: (() -> Unit)?) {
    val bColors = LocalBauhausColors.current
    val border = if (selected) bColors.successGreen else MaterialTheme.colorScheme.outline
    Row(
        Modifier.fillMaxWidth().clip(BauhausCornerShape).border(if (selected) 2.dp else 1.dp, border, BauhausCornerShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SignalBars(DfuScanRules.signalBars(t.rssi), bColors.cobalt)
        Column(Modifier.weight(1f)) {
            Text(t.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${t.address} · " + when (t.kind) { DfuKind.SECURE -> "Secure DFU"; DfuKind.LEGACY_BOOTLOADER -> "Legacy DFU"; DfuKind.APP_BUTTONLESS -> "Buttonless" },
                fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text("${t.rssi} dBm", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Eckige Signalbalken (0–4), Bauhaus-Stil. */
@Composable
private fun SignalBars(bars: Int, color: Color) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(14.dp)) {
        for (i in 1..4) Box(Modifier.width(3.dp).height((3 + i * 3).dp).background(if (i <= bars) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)))
    }
}

/** Die vier Phasen untereinander: erledigt ✓, aktiv ●, offen ○. */
@Composable
private fun PhaseList(phase: OtaPhase, stoppedAtStep: Int) {
    val bColors = LocalBauhausColors.current
    val labels = listOf(tr("Bootloader aktivieren"), tr("DFU-Initialisierung"), tr("Firmware-Upload"), tr("Fertig / Neustart"))
    val stopped = phase == OtaPhase.ABORTED || phase == OtaPhase.ERROR
    val current = if (stopped) stoppedAtStep else phase.step
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { i, label ->
            val n = i + 1
            val doneStep = n < current || phase == OtaPhase.COMPLETED
            val active = n == current && phase.isRunning
            val failedHere = n == current && stopped
            val color = when { doneStep -> bColors.successGreen; active -> bColors.cobalt; failedHere -> bColors.signalRed; else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                    if (active) BrailleSpinner(color, fontSize = 13.sp)
                    else Text(when { doneStep -> "✓"; failedHere -> "✕"; else -> "○" }, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text("$n. $label", fontSize = 12.sp, color = color, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

/** Eckiger Fortschrittsbalken (kein abgerundeter Material-Indikator). */
@Composable
private fun SquareProgressBar(fraction: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(10.dp).clip(BauhausCornerShape).background(MaterialTheme.colorScheme.surfaceVariant).border(1.dp, MaterialTheme.colorScheme.outline, BauhausCornerShape)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(color))
    }
}
