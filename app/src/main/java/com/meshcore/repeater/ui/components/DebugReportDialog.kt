package com.meshcore.repeater.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meshcore.repeater.i18n.tr
import com.meshcore.repeater.service.DebugReportBuilder
import com.meshcore.repeater.ui.theme.BauhausCornerShape
import com.meshcore.repeater.ui.theme.LocalBauhausColors

/**
 * Debug-Bericht teilen (v1.25, STYLE_GUIDE §2.15): zentrierter Bauhaus-Dialog mit `✕` oben rechts (16 dp,
 * [CloseIconSize]), kurzem Hinweis, Vorschau des (bereits geschwärzten) Berichts und zwei gleichwertigen Aktionen
 * „Kopieren“ | „Als Datei speichern“. Nach dem Kopieren führt ein Link zu GitHub Issues.
 */
@Composable
fun DebugReportDialog(report: String, fileName: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val bColors = LocalBauhausColors.current
    var status by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { u ->
        if (u == null) return@rememberLauncherForActivityResult
        val ok = runCatching { context.contentResolver.openOutputStream(u, "wt")?.use { it.write(report.toByteArray(Charsets.UTF_8)) } != null }.getOrDefault(false)
        status = if (ok) tr("Gespeichert: {0}", u.lastPathSegment?.substringAfterLast('/') ?: fileName) else tr("Speichern fehlgeschlagen.")
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = BauhausCornerShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()
        ) {
            Box {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr("Debug-Informationen"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(end = 24.dp))
                    Text(
                        tr("Enthalten: App- und Android-Version, Gerätemodell, Sprache und Palette, Verbindung, erkannte Hardware und Firmware sowie die letzten Meldungen und das CLI-Protokoll. Passwörter und private Schlüssel sind durch [REDACTED] ersetzt. Vor dem Einfügen in ein GitHub-Issue kannst du beliebige Zeilen entfernen."),
                        fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Vorschau: genau das, was kopiert bzw. gespeichert wird
                    Box(
                        Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(BauhausCornerShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .hintedVerticalScroll(rememberScrollState(), always = true, fadeColor = MaterialTheme.colorScheme.surfaceVariant).padding(8.dp)
                    ) {
                        Text(report, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    // Zwei gleichwertige Aktionen, gleich breit
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { copyToClipboard(context, report); copied = true; status = tr("In die Zwischenablage kopiert") },
                            shape = BauhausCornerShape, colors = ButtonDefaults.buttonColors(containerColor = bColors.cobalt),
                            modifier = Modifier.weight(1f)
                        ) { Text(tr("Kopieren"), color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
                        OutlinedButton(
                            onClick = { saveLauncher.launch(fileName) },
                            shape = BauhausCornerShape, border = BorderStroke(1.dp, bColors.cobalt),
                            modifier = Modifier.weight(1f)
                        ) { Text(tr("Als Datei speichern"), color = bColors.cobalt, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
                    }
                    status?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (copied) {
                        TextButton(onClick = { runCatching { uri.openUri(DebugReportBuilder.ISSUES_URL) } }, shape = BauhausCornerShape, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text(tr("GitHub-Issue öffnen"), fontSize = 12.sp, color = bColors.cobalt)
                        }
                    }
                }
                // Einheitliches ✕ oben rechts (16 dp)
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp).clip(BauhausCornerShape).clickable(onClickLabel = tr("Schließen"), onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = tr("Schließen"), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(CloseIconSize))
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("meshchōsei debug report", text))
}
