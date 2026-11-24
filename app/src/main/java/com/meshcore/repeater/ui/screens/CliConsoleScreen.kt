package com.meshcore.repeater.ui.screens

import com.meshcore.repeater.i18n.tr
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
    val cliLogs by viewModel.terminalLogs.collectAsState()
    val sysLogs by viewModel.systemLogs.collectAsState()
    val clipboard = LocalClipboardManager.current
    var cmd by remember { mutableStateOf("") }
    var copiedHint by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showOtaConfirmDialog by remember { mutableStateOf(false) }
    var showSystemLogs by remember { mutableStateOf(false) }

    val activeLogs = if (showSystemLogs) sysLogs else cliLogs

    fun handleSendCommand(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return
        showSystemLogs = false
        if (trimmed.equals("start ota", ignoreCase = true)) showOtaConfirmDialog = true
        else { viewModel.sendManualCommand(trimmed); cmd = "" }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(12.dp)) {
        Card(Modifier.fillMaxSize(), shape = BauhausCornerShape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "CLI (${cliLogs.size})",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (!showSystemLogs) FontWeight.Bold else FontWeight.Normal,
                            color = if (!showSystemLogs) bColors.signalRed else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { showSystemLogs = false }.padding(vertical = 2.dp, horizontal = 2.dp)
                        )
                        Text(
                            text = "SYSTEM (${sysLogs.size})",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (showSystemLogs) FontWeight.Bold else FontWeight.Normal,
                            color = if (showSystemLogs) bColors.ochre else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { showSystemLogs = true }.padding(vertical = 2.dp, horizontal = 2.dp)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = tr("Leeren"),
                            color = bColors.cobalt,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { if (showSystemLogs) viewModel.clearSystemLogs() else viewModel.clearTerminalLogs() }.padding(vertical = 2.dp, horizontal = 2.dp)
                        )
                        Text(
                            text = if (copiedHint) tr("Kopiert!") else tr("Kopieren"),
                            color = bColors.cobalt,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                clipboard.setText(AnnotatedString(activeLogs.joinToString("\n") { (if (it.isOutgoing) "> " else "  ") + it.text }))
                                copiedHint = true
                            }.padding(vertical = 2.dp, horizontal = 2.dp)
                        )
                        Text(
                            text = tr("Hilfe"),
                            color = bColors.cobalt,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { showHelpDialog = true }.padding(vertical = 2.dp, horizontal = 2.dp)
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline)
                val state = rememberLazyListState()
                LaunchedEffect(activeLogs.size, showSystemLogs) { copiedHint = false; if (activeLogs.isNotEmpty()) state.animateScrollToItem(activeLogs.size - 1) }
                LazyColumn(state = state, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(activeLogs) { e ->
                        Text(
                            text = if (e.isOutgoing) "> ${e.text}" else "  ${e.text}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = if (e.isOutgoing) bColors.signalRed else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = cmd, onValueChange = { cmd = it }, modifier = Modifier.weight(1f), singleLine = true, shape = BauhausCornerShape, placeholder = { Text("ver, filter, get radio...", fontSize = 12.sp) })
                    Spacer(Modifier.width(8.dp))
                    Button(shape = BauhausCornerShape, onClick = { handleSendCommand(cmd) }, colors = ButtonDefaults.buttonColors(containerColor = bColors.signalRed)) { Icon(Icons.AutoMirrored.Filled.Send, null) }
                }
            }
        }
    }

    if (showHelpDialog) {
        CliHelpDialog(
            onDismiss = { showHelpDialog = false },
            onSelectCommand = { item ->
                cmd = item.insertText
                showHelpDialog = false
                if (item.isDangerousOta) showOtaConfirmDialog = true
            }
        )
    }
    if (showOtaConfirmDialog) {
        OtaConfirmDialog(
            onDismiss = { showOtaConfirmDialog = false },
            onConfirm = { showOtaConfirmDialog = false; viewModel.sendManualCommand("start ota"); cmd = "" }
        )
    }
}
