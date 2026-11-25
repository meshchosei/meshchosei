package com.meshcore.repeater.ui.screens

import com.meshcore.repeater.ui.components.hintedVerticalScroll

import com.meshcore.repeater.i18n.tr
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meshcore.repeater.ui.theme.BauhausCornerShape
import com.meshcore.repeater.ui.theme.LocalBauhausColors

@Composable
internal fun CliHelpDialog(onDismiss: () -> Unit, onSelectCommand: (CliHelpCommand) -> Unit) {
    val bColors = LocalBauhausColors.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f),
            shape = BauhausCornerShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tr("CLI BEFEHLSÜBERSICHT"), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = bColors.signalRed)
                        Text(tr("Befehl antippen zum Übernehmen"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = tr("Schließen"),
                        color = bColors.cobalt,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onDismiss() }.padding(4.dp)
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline)
                Column(
                    modifier = Modifier.weight(1f).hintedVerticalScroll(rememberScrollState(), always = true, fadeColor = MaterialTheme.colorScheme.surface),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    cliHelpCategories.forEach { cat ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = tr(cat.title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (cat.accentIsRed) bColors.signalRed else bColors.ochre
                            )
                            cat.commands.forEach { item ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(BauhausCornerShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(1.dp, if (item.isDangerousOta) bColors.signalRed else MaterialTheme.colorScheme.outline, BauhausCornerShape)
                                        .clickable { onSelectCommand(item) }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = item.syntax,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (item.isDangerousOta) bColors.signalRed else bColors.cobalt
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(tr(item.description), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun OtaConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val bColors = LocalBauhausColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = BauhausCornerShape,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(tr("OTA-Modus wirklich starten?"), fontWeight = FontWeight.Bold, color = bColors.signalRed, fontSize = 16.sp) },
        text = { Text(tr("Der Repeater wechselt in den Over-The-Air-Update-Modus (WiFi/BLE). Die Mesh-Weiterleitung wird unterbrochen und bei Abbruch ist der Repeater aus der Ferne nicht mehr erreichbar."), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface) },
        confirmButton = {
            Button(shape = BauhausCornerShape, colors = ButtonDefaults.buttonColors(containerColor = bColors.signalRed), onClick = onConfirm) {
                Text(tr("OTA jetzt starten"), color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { OutlinedButton(shape = BauhausCornerShape, onClick = onDismiss) { Text(tr("Abbrechen")) } }
    )
}
