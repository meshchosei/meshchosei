package com.meshcore.repeater.ui.components

import com.meshcore.repeater.i18n.tr
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.meshcore.repeater.ui.theme.BauhausCornerShape
import com.meshcore.repeater.ui.theme.LocalBauhausColors

/**
 * Haftungsausschluss-Dialog (Bauhaus-Design, `BauhausCornerShape`). Alle Texte laufen über [tr] (assets/i18n.json),
 * damit sie immer in der aktiven App-Sprache (DE/EN/NL/FR) erscheinen – auch beim Aufruf aus „Über & Lizenz“.
 *
 * @param isInitialAcceptance true = Erststart: nicht wegklickbar, Buttons „Abbrechen“ (beendet die App) und
 *   „Verstanden & Akzeptieren“; false = nachträgliches Ansehen: schließbar, nur ein breiter „Schließen“-Button.
 * @param onAccept  Erststart: Zustimmung gespeichert
 * @param onDismiss Erststart: Abbruch; sonst: Dialog schließen
 */
@Composable
fun DisclaimerDialog(
    isInitialAcceptance: Boolean,
    onAccept: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val bColors = LocalBauhausColors.current

    AlertDialog(
        onDismissRequest = {
            if (!isInitialAcceptance) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !isInitialAcceptance,
            dismissOnClickOutside = !isInitialAcceptance
        ),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = BauhausCornerShape,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = bColors.ochre,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = tr("⚠ Wichtiger Hinweis"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.5.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // AI-generiert Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = bColors.signalRed.copy(alpha = 0.12f)
                    ),
                    shape = BauhausCornerShape,
                    border = BorderStroke(1.dp, bColors.signalRed)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {

                        Text(
                            text = tr("Dies ist KI-generierte Software — ohne jegliche Gewährleistung."),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = bColors.signalRed,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = tr("Bitte lies die folgenden Hinweise sorgfältig, bevor du diese App zur Konfiguration von LoRa-Repeater-Hardware einsetzt."),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                Spacer(modifier = Modifier.height(14.dp))

                // Drei Punkte: Haftung, Eigenverantwortung, Funkrecht
                listOf(
                    tr("Diese Software wird OHNE JEGLICHE GEWÄHRLEISTUNG bereitgestellt. Die Entwickler übernehmen keinerlei Haftung für Hardware-Schäden, Netzwerkstörungen oder Verstöße gegen Funkregelungen, die durch die Nutzung entstehen."),
                    tr("Du bist allein verantwortlich für die Einhaltung aller geltenden Funkvorschriften in deinem Land. In der EU ist das EU868-Band nach ETSI EN 300 220 und den nationalen Frequenzplänen geregelt. Das Überschreiten erlaubter ERP/EIRP-Grenzwerte ist illegal und kann Bußgelder nach sich ziehen."),
                    tr("Die Nutzung erfolgt auf eigene Gefahr. Stelle stets sicher, dass deine Repeater-Konfiguration dem lokalen Funkrecht entspricht, bevor du sendest.")
                ).forEach { point ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            fontSize = 14.sp,
                            color = bColors.ochre,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 8.dp, top = 1.dp)
                        )
                        Text(
                            text = point,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 19.sp
                        )
                    }
                }

                // Zustimmungshinweis nur beim Erststart (beim Ansehen gibt es nichts zu bestätigen)
                if (isInitialAcceptance) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = tr("Mit Tippen auf „Akzeptieren“ bestätigst du, dass du alle obigen Bedingungen gelesen, verstanden und akzeptiert hast."),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (!isInitialAcceptance) {
                // Nachträgliches Ansehen ("Über & Lizenz"): nur ein breiter "Schließen"-Button
                Button(onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = bColors.cobalt),
                    shape = BauhausCornerShape
                ) {
                    Text(
                        text = tr("Schließen"),
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
            ) {
                androidx.compose.material3.OutlinedButton(onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = BauhausCornerShape
                ) {
                    Text(
                        text = tr("Abbrechen"),
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(onClick = onAccept,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = bColors.signalRed),
                    shape = BauhausCornerShape
                ) {
                    Text(
                        text = tr("Akzeptieren"),
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    )
}
