package com.meshcore.repeater.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.meshcore.repeater.i18n.tr
import com.meshcore.repeater.ui.theme.BauhausCornerShape
import com.meshcore.repeater.ui.theme.LocalBauhausColors

/**
 * Sicherheitsabfrage vor dem Sperren von `Public` im Blacklist-Modus (v1.23, STYLE_GUIDE §2.12):
 * zentrierte eckige Surface mit 1-dp-Rahmen, „Abbrechen“ und signalrotes „Sperren“ (kein zusätzliches ✕).
 * Zurück-Taste und Tippen neben den Dialog brechen ab.
 */
@Composable
fun PublicBlockConfirmDialog(channel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val bColors = LocalBauhausColors.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = BauhausCornerShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surface
        ) {
            Box {
                Column(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        tr("{0} wirklich sperren?", channel),
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        color = bColors.signalRed
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        tr("Das Sperren von Public blockiert den gesamten offenen Hauptkanal: Dieser Repeater leitet dann keine Public-Nachrichten mehr weiter."),
                        fontSize = 13.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        OutlinedButton(onClick = onDismiss, shape = BauhausCornerShape) { Text(tr("Abbrechen")) }
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = onConfirm, shape = BauhausCornerShape,
                            colors = ButtonDefaults.buttonColors(containerColor = bColors.signalRed, contentColor = Color.White)
                        ) { Text(tr("Sperren"), fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}
