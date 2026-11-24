package com.meshcore.repeater.ui.screens

import com.meshcore.repeater.i18n.tr
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshcore.repeater.ui.theme.BauhausCornerShape
import com.meshcore.repeater.ui.theme.LocalBauhausColors
import com.meshcore.repeater.viewmodel.RepeaterViewModel
import com.meshcore.repeater.viewmodel.SetupChange
import com.meshcore.repeater.service.InputValidator
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import com.meshcore.repeater.ui.components.OsmTileMap
import com.meshcore.repeater.ui.components.LocalTourMode
import com.meshcore.repeater.ui.components.RegisterTourSave

private fun fmtCoord(v: Double): String = String.format(Locale.US, "%.6f", v)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IdentityLocationScreen(viewModel: RepeaterViewModel) {
    val bColors = LocalBauhausColors.current
    val setup by viewModel.basicSetup.collectAsState()
    // "Gespeichert"-Basis liegt im ViewModel und wird erst nach Bestätigung durch den Repeater aktualisiert
    val baseline by viewModel.nodeBaseline.collectAsState()
    var newAdminPassword by remember { mutableStateOf("") }
    var newGuestPassword by remember { mutableStateOf("") }
    var clearGuestPassword by remember { mutableStateOf(false) }
    var showNewKeyDialog by remember { mutableStateOf(false) }
    var customPrivKeyHex by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth(), shape = BauhausCornerShape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("IDENTITÄT & NAME"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = bColors.cobalt)
                Text(tr("Node Public Key:"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (setup.publicKeyHex.isNotBlank()) setup.publicKeyHex else tr("(Wird beim Verbinden ermittelt)"),
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(8.dp)
                )
                OutlinedButton(shape = BauhausCornerShape, border = BorderStroke(1.dp, bColors.ochre), onClick = { showNewKeyDialog = true }) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = bColors.ochre, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Node-Schlüssel neu setzen (nur bei Neuaufbau)"), fontSize = 11.sp, color = bColors.ochre)
                }
                OutlinedTextField(value = setup.nodeName, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(nodeName = it)) }, label = { Text(tr("Repeater-Name (set name)"), fontSize = 12.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = BauhausCornerShape)
                OutlinedTextField(value = setup.ownerInfo, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(ownerInfo = it)) }, label = { Text(tr("Besitzer / Kontakt-Info (set owner.info)"), fontSize = 12.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = BauhausCornerShape)
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = BauhausCornerShape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("STANDORT & PRIVATSPHÄRE-VERSATZ"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = bColors.cobalt)
                val curLat = setup.latitude.replace(',', '.').toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
                val curLon = setup.longitude.replace(',', '.').toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
                val hasPos = curLat != null && curLon != null && !(curLat == 0.0 && curLon == 0.0)
                // Abgesicherte Kartenmitte: nie NaN/0.0/leer -> Fallback Harz
                val safeLat = (setup.latitude.replace(',', '.').toDoubleOrNull()?.takeIf { it in -85.0..85.0 && it != 0.0 }) ?: 51.8810
                val safeLon = (setup.longitude.replace(',', '.').toDoubleOrNull()?.takeIf { it in -180.0..180.0 && it != 0.0 }) ?: 10.5620
                // Native Canvas-Kachelkarte (OSM + Gitter-Fallback): Fadenkreuz = Standort, Ziehen setzt lat/lon
                OsmTileMap(
                    lat = safeLat,
                    lon = safeLon,
                    hasPosition = hasPos,
                    markerColor = bColors.signalRed,
                    radiusColor = bColors.cobalt,
                    onPositionChange = { pLat, pLon ->
                        val s = viewModel.basicSetup.value
                        viewModel.updateBasicSetupDraft(s.copy(latitude = fmtCoord(pLat), longitude = fmtCoord(pLon)))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(BauhausCornerShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, BauhausCornerShape)
                )
                Text(
                    text = if (hasPos) "Lat ${fmtCoord(curLat!!)}  |  Lon ${fmtCoord(curLon!!)}" else tr("Kein Standort gesetzt – auf die Karte tippen oder Koordinaten eingeben."),
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = if (hasPos) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = setup.latitude, isError = setup.latitude.isNotBlank() && curLat == null, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(latitude = it.replace(',', '.'))) }, label = { Text(tr("Breitengrad (lat)"), fontSize = 12.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                    OutlinedTextField(value = setup.longitude, isError = setup.longitude.isNotBlank() && curLon == null, onValueChange = { viewModel.updateBasicSetupDraft(setup.copy(longitude = it.replace(',', '.'))) }, label = { Text(tr("Längengrad (lon)"), fontSize = 12.sp) }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape)
                }
                Text(tr("±200 m Privatsphäre-Versatz (verschiebt den Marker):"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val lat = curLat ?: 51.881000
                    val lon = curLon ?: 10.562000
                    val dLat = 200.0 / 111_320.0
                    val dLon = 200.0 / (111_320.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.01))
                    fun shift(aLat: Double, aLon: Double) = viewModel.updateBasicSetupDraft(viewModel.basicSetup.value.copy(latitude = fmtCoord((lat + aLat).coerceIn(-90.0, 90.0)), longitude = fmtCoord(lon + aLon)))
                    OutlinedButton(shape = BauhausCornerShape, onClick = { shift(dLat, 0.0) }) { Text("+200m N", fontSize = 10.sp) }
                    OutlinedButton(shape = BauhausCornerShape, onClick = { shift(-dLat, 0.0) }) { Text("+200m S", fontSize = 10.sp) }
                    OutlinedButton(shape = BauhausCornerShape, onClick = { shift(0.0, dLon) }) { Text("+200m O", fontSize = 10.sp) }
                    OutlinedButton(shape = BauhausCornerShape, onClick = { shift(0.0, -dLon) }) { Text("+200m W", fontSize = 10.sp) }
                    OutlinedButton(shape = BauhausCornerShape, onClick = { val a = Math.random() * 2 * Math.PI; shift(dLat * cos(a), dLon * sin(a)) }) { Text(tr("🎲 200m Zufall"), fontSize = 10.sp) }
                    OutlinedButton(shape = BauhausCornerShape, onClick = { viewModel.sendCommand("gps setloc", manualCli = false) }) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("GPS", fontSize = 10.sp)
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = BauhausCornerShape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("ZUGRIFFSKONTROLLE"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = bColors.cobalt)
                OutlinedTextField(value = newAdminPassword, onValueChange = { newAdminPassword = it }, label = { Text(tr("Neues Admin-Passwort (leer = unverändert)"), fontSize = 12.sp) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = BauhausCornerShape)
                OutlinedTextField(value = newGuestPassword, onValueChange = { newGuestPassword = it; if (it.isNotEmpty()) clearGuestPassword = false }, label = { Text(tr("Gast-Passwort (leer = unverändert)"), fontSize = 12.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = BauhausCornerShape)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tr("Gast-Passwort löschen (offener Zugang)"), fontSize = 12.sp)
                    Checkbox(checked = clearGuestPassword, onCheckedChange = { clearGuestPassword = it; if (it) newGuestPassword = "" })
                }
            }
        }

        val inTour = LocalTourMode.current
        val doSave: () -> Unit = save@{
                val latChanged = setup.latitude.isNotBlank() && setup.latitude != baseline.latitude
                val lonChanged = setup.longitude.isNotBlank() && setup.longitude != baseline.longitude
                if ((latChanged && !InputValidator.isValidLatitude(setup.latitude)) || (lonChanged && !InputValidator.isValidLongitude(setup.longitude))) {
                    viewModel.showUserHint("Bitte gültige Koordinaten eingeben (Breite -90…90, Länge -180…180).")
                    return@save
                }
                val name = InputValidator.sanitizeCliArg(setup.nodeName, 32)
                val owner = InputValidator.sanitizeCliArg(setup.ownerInfo, 160)
                val changes = mutableListOf<SetupChange>()
                if (name.isNotBlank() && setup.nodeName != baseline.nodeName) changes.add(SetupChange("set name $name") { it.copy(nodeName = setup.nodeName) })
                if (owner.isNotBlank() && setup.ownerInfo != baseline.ownerInfo) changes.add(SetupChange("set owner.info $owner") { it.copy(ownerInfo = setup.ownerInfo) })
                if (latChanged) changes.add(SetupChange("set lat ${setup.latitude.trim().replace(',', '.')}") { it.copy(latitude = setup.latitude) })
                if (lonChanged) changes.add(SetupChange("set lon ${setup.longitude.trim().replace(',', '.')}") { it.copy(longitude = setup.longitude) })
                // Passwörter haben keine Vergleichs-Basis (werden nie ausgelesen)
                if (newAdminPassword.isNotBlank()) { changes.add(SetupChange("password ${newAdminPassword.trim()}")); newAdminPassword = "" }
                if (clearGuestPassword) { changes.add(SetupChange("set guest.password ")); clearGuestPassword = false }
                else if (newGuestPassword.isNotBlank()) { changes.add(SetupChange("set guest.password ${newGuestPassword.trim()}")); newGuestPassword = "" }
                if (changes.isEmpty() && inTour) return@save
                viewModel.applyBasicSetupChanges(changes)
        }
        RegisterTourSave { doSave() }
        // Im Setup-Tour-Modus übernimmt "Speichern & Weiter →" in der Tour-Leiste
        if (!inTour) {
            Button(
                modifier = Modifier.fillMaxWidth(), shape = BauhausCornerShape, colors = ButtonDefaults.buttonColors(containerColor = bColors.signalRed),
                onClick = { doSave() }
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(tr("Speichern"), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showNewKeyDialog) {
        AlertDialog(

onDismissRequest = { showNewKeyDialog = false }, shape = BauhausCornerShape,
title = { Text(tr("Node-Identität neu setzen?"), fontWeight = FontWeight.Bold, color = bColors.signalRed) },
text = {
    val keyLen = customPrivKeyHex.trim().length
    OutlinedTextField(
        value = customPrivKeyHex,
        onValueChange = { raw -> customPrivKeyHex = raw.filter { it.isLetterOrDigit() }.take(InputValidator.PRIVATE_KEY_HEX_LEN) },
        label = { Text(tr("Neuer Private Key (64-Byte Hex)"), fontSize = 11.sp) },
        isError = keyLen > 0 && !InputValidator.isValidPrivateKeyHex(customPrivKeyHex),
        supportingText = { Text("$keyLen / ${InputValidator.PRIVATE_KEY_HEX_LEN}", fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
        singleLine = true, shape = BauhausCornerShape, modifier = Modifier.fillMaxWidth()
    )
},
confirmButton = { Button(shape = BauhausCornerShape, colors = ButtonDefaults.buttonColors(containerColor = bColors.signalRed), enabled = InputValidator.isValidPrivateKeyHex(customPrivKeyHex), onClick = { val k = customPrivKeyHex.trim(); customPrivKeyHex = ""; showNewKeyDialog = false; viewModel.sendCommand("set prv.key $k", manualCli = false) }) { Text(tr("Setzen"), color = Color.White, fontWeight = FontWeight.Bold) } },
dismissButton = { OutlinedButton(shape = BauhausCornerShape, onClick = { showNewKeyDialog = false }) { Text(tr("Abbrechen")) } }
)
}
}
