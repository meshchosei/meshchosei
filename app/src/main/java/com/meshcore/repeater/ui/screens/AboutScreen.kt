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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshcore.repeater.R
import com.meshcore.repeater.ui.theme.BauhausCornerShape
import com.meshcore.repeater.ui.theme.LocalBauhausColors
import com.meshcore.repeater.viewmodel.RepeaterViewModel

@Composable
fun AboutScreen(viewModel: RepeaterViewModel, onShowDisclaimer: () -> Unit) {
    val bColors = LocalBauhausColors.current
    val cap by viewModel.deviceCapabilities.collectAsState()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth(), shape = BauhausCornerShape, border = BorderStroke(1.dp, bColors.signalRed), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.about_title), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = bColors.signalRed)
                Text(if (cap.firmwareVersion.isNotBlank()) "v0.21 • FW: ${cap.firmwareVersion}" else "v0.21", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
        Card(Modifier.fillMaxWidth(), shape = BauhausCornerShape, border = BorderStroke(1.dp, bColors.ochre), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.disclaimer_title), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = bColors.ochre)
                Text(stringResource(R.string.disclaimer_ai_notice), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = bColors.signalRed)
                Text(stringResource(R.string.disclaimer_body), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Text("• " + stringResource(R.string.disclaimer_point_liability), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("• " + stringResource(R.string.disclaimer_point_regulations), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("• " + stringResource(R.string.disclaimer_point_responsibility), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
