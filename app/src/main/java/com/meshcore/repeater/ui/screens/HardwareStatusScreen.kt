package com.meshcore.repeater.ui.screens

import com.meshcore.repeater.ui.components.RefreshButton

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.meshcore.repeater.model.GpsMode
import com.meshcore.repeater.model.LedMode
import com.meshcore.repeater.ui.theme.AlertRed
import com.meshcore.repeater.ui.theme.BauhausCornerShape
import com.meshcore.repeater.ui.theme.LocalBauhausColors
import com.meshcore.repeater.viewmodel.RepeaterViewModel

@Composable
fun HardwareStatusScreen(viewModel: RepeaterViewModel) {
    val telemetry by viewModel.telemetry.collectAsState()
    val hardwareInfo by viewModel.hardwareInfo.collectAsState()
    val ledMode by viewModel.ledMode.collectAsState()
    val gpsMode by viewModel.gpsMode.collectAsState()
    val bColors = LocalBauhausColors.current
    val scrollState = rememberScrollState()

    var showRebootDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var backupText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // === Section 1: Batterie & Solar Telemetrie ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "BATTERIE & SOLAR-TELEMETRIE",
                style = MaterialTheme.typography.titleMedium,
                color = bColors.signalRed,
                fontWeight = FontWeight.Bold
            )
            RefreshButton(onClick = { viewModel.requestTelemetry() })
        }
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = BauhausCornerShape
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Battery Bar & Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = bColors.signalRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${telemetry.batteryPercent}% (${telemetry.batteryVoltage} V)",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp
                        )
                    }
                    Text(
                        text = telemetry.chargingState.label,
                        color = bColors.signalRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = telemetry.batteryPercent / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = bColors.signalRed,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Solar & Temperature Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WbSunny, contentDescription = null, tint = bColors.ochre, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Solarspannung", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("%.2f V".format(telemetry.solarVoltage), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }

                    Column {
                        Text("Ladestrom", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("%.0f mA".format(telemetry.solarCurrentMa), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }

                    Column {
                        Text("Solarleistung", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("%.2f W".format(telemetry.solarPowerWatts), fontWeight = FontWeight.Bold, color = bColors.ochre)
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Thermostat, contentDescription = null, tint = bColors.cobalt, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Temperatur", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("%.1f °C".format(telemetry.temperatureC), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // === Section 2: LED-Steuerung ===
        Text(
            text = "LED-STATUS & STEALTH-MODUS",
            style = MaterialTheme.typography.titleMedium,
            color = bColors.cobalt,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = BauhausCornerShape
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "LED-Verhalten auswählen:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                LedMode.values().forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(shape = BauhausCornerShape, selected = ledMode == mode,
                            onClick = { viewModel.setLedMode(mode) },
                            label = { Text(mode.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = bColors.cobalt,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // === Section 3: GPS-Modus ===
        Text(
            text = "GPS-MODUS & STANDORT",
            style = MaterialTheme.typography.titleMedium,
            color = bColors.cobalt,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = BauhausCornerShape
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                GpsMode.values().forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(shape = BauhausCornerShape, selected = gpsMode == mode,
                            onClick = { viewModel.setGpsMode(mode) },
                            label = { Text(mode.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = bColors.cobalt,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // === Section 4: Node Information ===
        Text(
            text = "GERÄTE- & FIRMWARE-DETAILS",
            style = MaterialTheme.typography.titleMedium,
            color = bColors.signalRed,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = BauhausCornerShape
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                DetailRow("Firmware-Version", hardwareInfo.firmwareVersion)
                DetailRow("Hardware-Board", hardwareInfo.boardModel)
                DetailRow("LoRa-Transceiver", hardwareInfo.radioChip)
                DetailRow("Freier Heap-RAM", "${hardwareInfo.freeHeapBytes} Bytes")
                DetailRow("Laufzeit (Uptime)", "${hardwareInfo.uptimeSeconds / 3600}h ${(hardwareInfo.uptimeSeconds % 3600) / 60}m")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // === Section 5: Node System Aktionen ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = {
                    val clip = android.content.ClipboardManager::class.java
                    showBackupDialog = true
                },
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = bColors.signalRed),
                shape = BauhausCornerShape
            ) {
                Icon(Icons.Default.Save, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Backup / Klonen", color = Color.White, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(onClick = { showRebootDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed),
                shape = BauhausCornerShape
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Node Neustart")
            }
        }
    }

    // Reboot Confirmation Dialog
    if (showBackupDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text("Repeater-Profil Backup & Klonen") },
            text = {
                Column {
                    Text("Exportiere die aktuelle Konfiguration oder füge ein Profil ein, um weitere Repeater identisch zu bespielen:", fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(value = backupText, onValueChange = { backupText = it }, modifier = Modifier.fillMaxWidth().height(150.dp), textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp))
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { backupText = viewModel.buildBackupScript() }) { Text("Auslesen") }
                    Button(shape = BauhausCornerShape, onClick = { viewModel.restoreBackupScript(backupText); showBackupDialog = false }) { Text("Einspielen") }
                }
            },
            dismissButton = { TextButton(onClick = { showBackupDialog = false }) { Text("Schließen") } }
        )
    }
    if (showRebootDialog) {
        AlertDialog(
            onDismissRequest = { showRebootDialog = false },
            title = { Text("Repeater neu starten?") },
            text = { Text("Möchtest du den Repeater wirklich neu starten? Die Funk- und Bluetooth-Verbindung wird dabei kurzzeitig unterbrochen.") },
            confirmButton = {
                Button(shape = BauhausCornerShape, onClick = {
                        showRebootDialog = false
                        viewModel.rebootNode()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) {
                    Text("Neu starten", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRebootDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

@Composable
private fun DetailRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}
