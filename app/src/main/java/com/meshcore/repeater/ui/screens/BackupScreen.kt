package com.meshcore.repeater.ui.screens

import com.meshcore.repeater.i18n.tr
import com.meshcore.repeater.ui.components.BusyButton
import com.meshcore.repeater.ui.components.BusyOutlinedButton
import com.meshcore.repeater.viewmodel.BusyKey
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshcore.repeater.model.ConnectionState
import com.meshcore.repeater.service.MeshCoreBackup
import com.meshcore.repeater.ui.theme.BauhausCornerShape
import com.meshcore.repeater.ui.theme.LocalBauhausColors
import com.meshcore.repeater.viewmodel.RepeaterViewModel

@Composable
fun BackupScreen(viewModel: RepeaterViewModel) {
    val bColors = LocalBauhausColors.current
    val context = LocalContext.current
    val setup by viewModel.basicSetup.collectAsState()
    val connection by viewModel.connectionState.collectAsState()
    val backupJson by viewModel.backupJson.collectAsState()
    val readStatus by viewModel.activeReadStatusText.collectAsState()
    val busy = readStatus != null
    val busyActions by viewModel.busyActions.collectAsState()
    val caps by viewModel.deviceCapabilities.collectAsState()
    val remoteAdmin by viewModel.remoteAdminSession.collectAsState()
    // Gleiche Bedingung wie navigationAllowed: verbunden, erkannt und (bei Companion) eingeloggt
    val isConnected = connection is ConnectionState.Connected && caps.detectionComplete && (!caps.isCompanion || remoteAdmin.isLoggedIn)
    val enabled = isConnected && !busy

    var includePrvOnCreate by remember { mutableStateOf(false) }
    var plan by remember { mutableStateOf<MeshCoreBackup.Plan?>(null) }
    var planFileName by remember { mutableStateOf("") }
    var applyPrv by remember { mutableStateOf(false) }
    var applyGuest by remember { mutableStateOf(false) }
    var showInstallConfirm by remember { mutableStateOf(false) }
    var lastSaved by remember { mutableStateOf<String?>(null) }

    val fileName = remember(setup.nodeName) {
        val n = setup.nodeName.trim().ifEmpty { "repeater" }.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifEmpty { "repeater" }
        "$n-backup.json"
    }

    // 1) Backup speichern: sobald das JSON fertig ist, Android-Dateidialog öffnen
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = viewModel.backupJson.value
        if (uri != null && json != null) {
            val ok = runCatching { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) } != null }.getOrDefault(false)
            lastSaved = if (ok) tr("Gespeichert: {0}", uri.lastPathSegment ?: fileName) else tr("Speichern fehlgeschlagen.")
        }
        viewModel.clearBackupJson()
    }
    LaunchedEffect(backupJson) { if (backupJson != null) saveLauncher.launch(fileName) }

    // 2) Backup laden
    var loadError by remember { mutableStateOf<String?>(null) }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
        if (text == null) { loadError = tr("Datei konnte nicht gelesen werden."); return@rememberLauncherForActivityResult }
        runCatching { MeshCoreBackup.parse(text) }
            .onSuccess { plan = it; planFileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Backup"; loadError = null; applyPrv = false; applyGuest = false }
            .onFailure { plan = null; loadError = it.message }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!isConnected) {
            Text(tr("Backup erstellen und laden ist erst möglich, wenn ein Repeater verbunden ist."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // ---------- Backup erstellen ----------
        Card(Modifier.fillMaxWidth(), shape = BauhausCornerShape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Backup, null, tint = bColors.cobalt, modifier = Modifier.size(18.dp))
                    Text(tr("BACKUP ERSTELLEN"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = bColors.cobalt)
                }
                Text(
                    tr("Liest alle Einstellungen und Regionen vom verbundenen Repeater und speichert sie im MeshCore/PEmesh-JSON-Format."),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includePrvOnCreate, enabled = enabled, onCheckedChange = { includePrvOnCreate = it })
                    Column(Modifier.weight(1f)) {
                        Text(tr("Private Key (prv.key) mitsichern"), fontSize = 13.sp)
                        Text(tr("Nur über USB-Direktverbindung auslesbar. Datei danach sicher aufbewahren!"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                BusyOutlinedButton(
                    busy = BusyKey.BACKUP_CREATE in busyActions, color = bColors.cobalt, busyText = tr("Lese Backup…"),
                    modifier = Modifier.fillMaxWidth(), enabled = enabled, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    onClick = { lastSaved = null; viewModel.createBackup(includePrvOnCreate) }
                ) {
                    Icon(Icons.Default.SaveAlt, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Backup erstellen & speichern ({0})", fileName), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                lastSaved?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

        // ---------- Backup laden & installieren ----------
        Card(Modifier.fillMaxWidth(), shape = BauhausCornerShape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Restore, null, tint = bColors.signalRed, modifier = Modifier.size(18.dp))
                    Text(tr("BACKUP LADEN & INSTALLIEREN"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = bColors.signalRed)
                }
                OutlinedButton(modifier = Modifier.fillMaxWidth(), shape = BauhausCornerShape, enabled = enabled,
                    onClick = { openLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }) {
                    Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Backup-Datei (.json) öffnen"), fontSize = 12.sp)
                }
                loadError?.let { Text(it, fontSize = 12.sp, color = bColors.signalRed) }

                plan?.let { p ->
                    Text(tr("Vorschau: {0}", planFileName), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    val applyCount = p.vars.count { it.state == MeshCoreBackup.ItemState.APPLY }
                    val placeholders = p.vars.count { it.state == MeshCoreBackup.ItemState.PLACEHOLDER }
                    Text(tr("vars: {0} übertragbar", applyCount) + (if (placeholders > 0) tr(", {0} \"XXX\"-Platzhalter ignoriert", placeholders) else ""), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background, BauhausCornerShape).padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        p.vars.forEach { item ->
                            val excluded = (item.sensitivity == MeshCoreBackup.Sensitivity.PRIVATE_KEY && !applyPrv) ||
                                (item.sensitivity == MeshCoreBackup.Sensitivity.GUEST_PASSWORD && !applyGuest)
                            val (note, color) = when {
                                item.state == MeshCoreBackup.ItemState.PLACEHOLDER -> tr("Platzhalter – ignoriert") to MaterialTheme.colorScheme.onSurfaceVariant
                                item.state == MeshCoreBackup.ItemState.INVALID -> tr("ungültig – ignoriert") to bColors.signalRed
                                excluded -> tr("nicht ausgewählt") to MaterialTheme.colorScheme.onSurfaceVariant
                                else -> "" to MaterialTheme.colorScheme.onSurface
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(item.key, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(132.dp), color = color)
                                Text(if (note.isEmpty()) item.display.ifEmpty { tr("(leer)") } else "${item.display.take(24)}  · $note", fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                    color = color, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    if (p.regions.isNotEmpty() || p.regionHome != null || p.hasRegionDefault) {
                        Text(tr("Regionen: {0}  ·  Home: {1}  ·  Default: {2}", p.regions.size, p.regionHome ?: "—", p.regionDefault ?: "<null>"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background, BauhausCornerShape).padding(8.dp)) {
                            p.regions.forEach { r ->
                                Text("${r.name}${r.parent?.let { "  ← $it" } ?: ""}${if (r.flood) "  (Flood)" else tr("  (Flood gesperrt)")}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }
                        }
                    }

                    val hasPrv = p.vars.any { it.sensitivity == MeshCoreBackup.Sensitivity.PRIVATE_KEY && it.state == MeshCoreBackup.ItemState.APPLY }
                    val hasGuest = p.vars.any { it.sensitivity == MeshCoreBackup.Sensitivity.GUEST_PASSWORD && it.state == MeshCoreBackup.ItemState.APPLY }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = applyPrv && hasPrv, enabled = enabled && hasPrv, onCheckedChange = { applyPrv = it })
                        Column(Modifier.weight(1f)) {
                            Text(tr("Private Key (prv.key) übernehmen"), fontSize = 13.sp)
                            Text(if (hasPrv) tr("Ändert die Node-Identität! Nur beim Wiederherstellen DESSELBEN Repeaters.") else tr("Nicht im Backup enthalten."), fontSize = 11.sp,
                                color = if (hasPrv) bColors.signalRed else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = applyGuest && hasGuest, enabled = enabled && hasGuest, onCheckedChange = { applyGuest = it })
                        Text(if (hasGuest) tr("Gast-Passwort übernehmen") else tr("Gast-Passwort übernehmen (nicht im Backup enthalten)"), fontSize = 13.sp)
                    }

                    val cmds = p.commands(applyPrv && hasPrv, applyGuest && hasGuest)
                    BusyButton(
                        busy = BusyKey.BACKUP_INSTALL in busyActions, containerColor = bColors.signalRed, busyText = tr("Installiere…"),
                        modifier = Modifier.fillMaxWidth(), enabled = enabled && cmds.isNotEmpty(),
                        onClick = { showInstallConfirm = true }
                    ) { Text(tr("Backup installieren ({0} Befehle)", cmds.size), color = Color.White, fontWeight = FontWeight.Bold) }

                    if (showInstallConfirm) {
                        AlertDialog(
                            onDismissRequest = { showInstallConfirm = false }, shape = BauhausCornerShape,
                            title = { Text(tr("Backup installieren?"), fontWeight = FontWeight.Bold, color = bColors.signalRed) },
                            text = {
                                Text(tr("{0} Befehle werden nacheinander an den Repeater gesendet (Einstellungen, Regionen + \"region save\"; Funkparameter und Key zuletzt).", cmds.size) +
                                    if (applyPrv && hasPrv) tr("\n\nDer Private Key wird überschrieben – die Node-Identität ändert sich.") else "")
                            },
                            confirmButton = { Button(shape = BauhausCornerShape, colors = ButtonDefaults.buttonColors(containerColor = bColors.signalRed), onClick = { showInstallConfirm = false; viewModel.installBackup(cmds) }) { Text(tr("Installieren"), color = Color.White, fontWeight = FontWeight.Bold) } },
                            dismissButton = { OutlinedButton(shape = BauhausCornerShape, onClick = { showInstallConfirm = false }) { Text(tr("Abbrechen")) } }
                        )
                    }
                }
            }
        }
    }
}
