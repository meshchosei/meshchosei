package com.meshcore.repeater.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshcore.repeater.ui.theme.BauhausCornerShape
import com.meshcore.repeater.ui.theme.LocalBauhausColors
import com.meshcore.repeater.viewmodel.RepeaterViewModel

@Composable
fun CliConsoleScreen(viewModel: RepeaterViewModel) {
    val bColors = LocalBauhausColors.current
    val logs by viewModel.terminalLogs.collectAsState()
    var cmd by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(12.dp)) {
        Card(Modifier.fillMaxSize(), shape = BauhausCornerShape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Text("CLI TERMINAL (${logs.size})", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = bColors.signalRed, fontSize = 12.sp)
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline)
                val state = rememberLazyListState()
                LaunchedEffect(logs.size) { if (logs.isNotEmpty()) state.animateScrollToItem(logs.size - 1) }
                LazyColumn(state = state, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(logs) { e -> Text(if (e.isOutgoing) "> ${e.text}" else "  ${e.text}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = if (e.isOutgoing) bColors.signalRed else MaterialTheme.colorScheme.onSurface) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = cmd, onValueChange = { cmd = it }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape, placeholder = { Text("ver, filter, get radio...", fontSize = 12.sp) })
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { if (cmd.isNotBlank()) { viewModel.sendCommand(cmd); cmd = "" } }, shape = BauhausCornerShape, colors = ButtonDefaults.buttonColors(containerColor = bColors.signalRed)) { Icon(Icons.AutoMirrored.Filled.Send, null) }
                }
            }
        }
    }
}
