package com.meshcore.repeater.ui.screens

import com.meshcore.repeater.i18n.tr
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshcore.repeater.service.CliAutocomplete
import com.meshcore.repeater.ui.components.hintedVerticalScroll
import com.meshcore.repeater.ui.theme.BauhausCornerShape
import com.meshcore.repeater.ui.theme.LocalBauhausColors
import com.meshcore.repeater.viewmodel.RepeaterViewModel

@Composable
fun CliConsoleScreen(viewModel: RepeaterViewModel) {
    val bColors = LocalBauhausColors.current
    val cliLogs by viewModel.terminalLogs.collectAsState()
    val sysLogs by viewModel.systemLogs.collectAsState()
    val clipboard = LocalClipboardManager.current
    val caps by viewModel.deviceCapabilities.collectAsState()
    var input by remember { mutableStateOf(TextFieldValue("")) }
    /** Setzt den Feldinhalt und stellt den Cursor ans Ende (z. B. nach Übernahme eines Vorschlags). */
    fun setCmd(v: String) { input = TextFieldValue(v, TextRange(v.length)) }
    var menuDismissed by remember { mutableStateOf(false) }
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
        else { viewModel.sendManualCommand(trimmed); setCmd(""); menuDismissed = true }
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
                // CLI-Autocomplete (v1.18): vertikales Klappmenü direkt über der Eingabezeile. Antippen übernimmt nur,
                // gesendet wird ausschließlich per rotem Button (STYLE_GUIDE §2.10).
                val available = remember(caps.supportsBlacklist, caps.supportsWhitelist, caps.isDirectRepeater) {
                    cliAutocompleteEntries.filter { CliAutocomplete.isAvailable(it, caps.supportsBlacklist, caps.supportsWhitelist, caps.isDirectRepeater) }
                }
                // öffnet nur ab dem 1. getippten Zeichen (leeres Feld → nie); Präfix-Treffer zuerst, je Gruppe alphabetisch
                val suggestions = remember(input.text, available) { CliAutocomplete.suggest(input.text, available) }
                val menuVisible = !menuDismissed && suggestions.isNotEmpty()
                BackHandler(enabled = menuVisible) { menuDismissed = true }
                if (menuVisible) {
                    val menuScroll = rememberScrollState()
                    LaunchedEffect(input.text) { menuScroll.scrollTo(0) }
                    Card(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = BauhausCornerShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                      Box(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().heightIn(max = 200.dp).hintedVerticalScroll(menuScroll, always = true, fadeColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            suggestions.forEachIndexed { i, s ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { setCmd(s.insertText) }.padding(start = 10.dp, end = 26.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (s.dangerous) "⚠ ${s.syntax}" else s.syntax,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (s.dangerous) bColors.signalRed else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.widthIn(max = 210.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = if (s.description.isNotEmpty()) tr(s.description) else "",
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        // ✕ oben rechts: Auswahl manuell schließen (erneutes Tippen öffnet sie wieder)
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(2.dp).size(22.dp).clip(BauhausCornerShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { menuDismissed = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = tr("Schließen"), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        }
                      }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { if (it.text != input.text) menuDismissed = false; input = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = BauhausCornerShape,
                        placeholder = { Text("ver, filter, get radio...", fontSize = 12.sp) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(shape = BauhausCornerShape, onClick = { handleSendCommand(input.text) }, colors = ButtonDefaults.buttonColors(containerColor = bColors.signalRed)) { Icon(Icons.AutoMirrored.Filled.Send, null) }
                }
            }
        }
    }

    if (showHelpDialog) {
        CliHelpDialog(
            onDismiss = { showHelpDialog = false },
            onSelectCommand = { item ->
                setCmd(item.insertText)
                showHelpDialog = false
                if (item.isDangerousOta) showOtaConfirmDialog = true
            }
        )
    }
    if (showOtaConfirmDialog) {
        OtaConfirmDialog(
            onDismiss = { showOtaConfirmDialog = false },
            onConfirm = { showOtaConfirmDialog = false; viewModel.sendManualCommand("start ota"); setCmd("") }
        )
    }
}
