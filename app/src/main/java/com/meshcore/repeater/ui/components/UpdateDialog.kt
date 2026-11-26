package com.meshcore.repeater.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.meshcore.repeater.i18n.tr
import com.meshcore.repeater.service.UpdateCheck
import com.meshcore.repeater.ui.theme.BauhausCornerShape
import com.meshcore.repeater.ui.theme.LocalBauhausColors

/**
 * Zentrierter Update-Dialog (v1.19, STYLE_GUIDE §2.11): eckige Surface mit 1-dp-Rahmen, Meldung „Neue Version vX.YZ
 * verfügbar“, darunter die anklickbare Release-URL in Kobalt (öffnet den Browser), unten mittig ein ✕ zum Schließen.
 * Zurück-Taste und Tippen neben den Dialog schließen ebenfalls (für die laufende Sitzung).
 */
@Composable
fun UpdateDialog(update: UpdateCheck.ReleaseInfo, debugTest: Boolean, onDismiss: () -> Unit) {
    val bColors = LocalBauhausColors.current
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = BauhausCornerShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = tr("Neue Version {0} verfügbar", update.displayVersion),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (debugTest) {
                    Text("[DEBUG-Test]", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = update.htmlUrl,
                    fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    color = bColors.cobalt,
                    textDecoration = TextDecoration.Underline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clip(BauhausCornerShape)
                        .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.htmlUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                        .padding(4.dp)
                )
                Spacer(Modifier.height(8.dp))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = tr("Schließen"), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(CloseIconSize))
                }
            }
        }
    }
}
