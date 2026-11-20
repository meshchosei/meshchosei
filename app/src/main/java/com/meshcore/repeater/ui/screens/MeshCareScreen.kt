package com.meshcore.repeater.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
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
import com.meshcore.repeater.model.WatchdogDuration
import com.meshcore.repeater.ui.theme.AlertRed
import com.meshcore.repeater.ui.theme.LocalBauhausColors
import com.meshcore.repeater.viewmodel.RepeaterViewModel

@Composable
fun MeshCareScreen(viewModel: RepeaterViewModel) {
    val meshCare by viewModel.meshCareState.collectAsState()
    val radioConfig by viewModel.radioConfig.collectAsState()
    val bColors = LocalBauhausColors.current
    val scrollState = rememberScrollState()

    var selectedDuration by remember { mutableStateOf(WatchdogDuration.FIVE_MINUTES) }
    var selectedReducedDbm by remember { mutableDoubleStateOf(5.0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // === Purpose & Concept Card ===
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(bColors.ochre.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = bColors.ochre)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Was bewirkt der Mesh-Schonmodus?",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Bei Wartungsarbeiten und Remote-Konfiguration über das Mesh drosselt dieser Modus die Sendeleistung des Repeaters temporär, um umliegende Nodes nicht durch Testsendungen zu überlasten.\n\nEin automatischer Watchdog-Timer (5 oder 10 Min.) garantiert: Bricht der Kontakt ab, kehrt der Repeater selbstständig auf die reguläre Leistung zurück!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // === Active Dashboard vs Configuration ===
        if (meshCare.isActive) {
            // Active Countdown Dashboard
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, bColors.ochre, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C12)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SCHONMODUS AKTIV",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = bColors.ochre
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Big Countdown Clock
                    Text(
                        text = meshCare.formattedRemainingTime,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = meshCare.progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = bColors.ochre,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Gedrosselte Leistung", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("%.1f dBm".format(meshCare.reducedTxPowerDbm), fontWeight = FontWeight.Bold, color = bColors.ochre)
                        }
                        Column {
                            Text("Gespeicherte Basisleistung", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("%.1f dBm".format(meshCare.savedOriginalTxPowerDbm), fontWeight = FontWeight.Bold, color = bColors.signalRed)
                        }
                        Column {
                            Text("Watchdog-Dauer", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(meshCare.duration.label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.extendMeshCare() },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = bColors.cobalt)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Watchdog auffrischen", color = Color.Black, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        OutlinedButton(
                            onClick = { viewModel.stopMeshCare() },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sofort beenden")
                        }
                    }
                }
            }
        } else {
            // Configuration when inactive
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "1. Watchdog-Zeitfenster wählen:",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WatchdogDuration.values().forEach { dur ->
                            FilterChip(
                                selected = selectedDuration == dur,
                                onClick = { selectedDuration = dur },
                                label = { Text(dur.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = bColors.signalRed,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "2. Drosselungs-Sendeleistung wählen:",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val powerOptions = listOf(2.0, 5.0, 8.0, 10.0)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        powerOptions.forEach { p ->
                            FilterChip(
                                selected = selectedReducedDbm == p,
                                onClick = { selectedReducedDbm = p },
                                label = { Text("%.0f dBm".format(p)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = bColors.ochre,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Summary & Start Button
                    Button(
                        onClick = { viewModel.startMeshCare(selectedDuration, selectedReducedDbm) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = bColors.ochre),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Mesh-Schonmodus starten (${selectedDuration.label})",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // === Firmware Watchdog Mechanism Explanation ===
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = bColors.cobalt)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ZephCore tempradio Mechanismus",
                        fontWeight = FontWeight.Bold,
                        color = bColors.cobalt
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Die Firmware speichert den ursprünglichen Funkzustand im RAM. Der Befehl 'tempradio' setzt einen Hardware-Timer. Solange die App Befehle sendet, wird dieser Timer zurückgesetzt. Falls das Smartphone ausgeschaltet wird, die Funkverbindung abreißt oder die App beendet wird, greift der autonome Zephyr-Watchdog im Repeater und reaktiviert die reguläre Sendeleistung ohne manuelles Zutun.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
