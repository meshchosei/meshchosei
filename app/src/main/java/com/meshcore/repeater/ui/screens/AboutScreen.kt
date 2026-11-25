package com.meshcore.repeater.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshcore.repeater.R
import com.meshcore.repeater.BuildConfig
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.meshcore.repeater.ui.theme.BauhausCornerShape
import com.meshcore.repeater.ui.theme.LocalBauhausColors
import com.meshcore.repeater.viewmodel.RepeaterViewModel

@Composable
fun AboutScreen(viewModel: RepeaterViewModel, onShowDisclaimer: () -> Unit) {
    val bColors = LocalBauhausColors.current
    val cap by viewModel.deviceCapabilities.collectAsState()
    var logoTaps by remember { mutableIntStateOf(0) }
    var lastLogoTapMs by remember { mutableLongStateOf(0L) }
    var showPong by remember { mutableStateOf(false) }
    if (showPong) com.meshcore.repeater.ui.components.MonoPongDialog(onDismiss = { showPong = false })
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Kopf: Logo (5x tippen = MonoPong) + Versionsnummer dezent oben rechts
        Box(Modifier.fillMaxWidth().clip(BauhausCornerShape)) {
            com.meshcore.repeater.ui.components.MeshchoseiLogo(
                modifier = Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    val now = System.currentTimeMillis()
                    logoTaps = if (now - lastLogoTapMs < 1200L) logoTaps + 1 else 1
                    lastLogoTapMs = now
                    if (logoTaps >= 5) { logoTaps = 0; showPong = true }
                },
                emblemSize = 96.dp, fontSize = 20.sp
            )
            Text(
                "v${BuildConfig.VERSION_NAME}", fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                color = com.meshcore.repeater.ui.components.MeshchoseiColors.Paper.copy(alpha = 0.55f),
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
            )
        }
        Card(Modifier.fillMaxWidth(), shape = BauhausCornerShape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.about_license_title), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface); Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.about_license_body), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.about_ai_title), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = bColors.ochre); Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.about_ai_body), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.about_credits_title), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = bColors.cobalt); Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.about_credits_body), fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Datenschutzerklärung (ersetzt die doppelte Disclaimer-Karte; der Disclaimer bleibt über den Button abrufbar)
        Card(Modifier.fillMaxWidth(), shape = BauhausCornerShape, border = BorderStroke(1.dp, bColors.cobalt), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.about_privacy_title), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = bColors.cobalt)
                Text(stringResource(R.string.about_privacy_body), fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedButton(onClick = onShowDisclaimer, shape = BauhausCornerShape, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.about_show_disclaimer), fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            com.meshcore.repeater.ui.components.MESHCHOSEI_SIGNATURE,
            modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontSize = 12.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
    }
}
