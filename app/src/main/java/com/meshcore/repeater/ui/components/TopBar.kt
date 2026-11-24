package com.meshcore.repeater.ui.components

import com.meshcore.repeater.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshcore.repeater.R
import com.meshcore.repeater.model.ConnectionState
import com.meshcore.repeater.model.DeviceCapabilities
import com.meshcore.repeater.model.MeshCareState
import com.meshcore.repeater.ui.theme.BauhausCornerShape
import com.meshcore.repeater.ui.theme.LocalBauhausColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepeaterTopBar(
    title: String,
    connectionState: ConnectionState,
    meshCareState: MeshCareState,
    capabilities: DeviceCapabilities = DeviceCapabilities(),
    onMenuClick: () -> Unit
) {
    val bColors = LocalBauhausColors.current

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.weight(1f))

                // Rolle des direkt verbundenen Geräts (oben rechts): direkter Repeater (USB-CLI) oder Companion
                if (connectionState is ConnectionState.Connected && (capabilities.isDirectRepeater || capabilities.isCompanion || capabilities.firmwareVersion.isNotBlank())) {
                    val companion = capabilities.isCompanion && !capabilities.isDirectRepeater
                    val roleColor = if (companion) bColors.cobalt else bColors.successGreen
                    Text(
                        text = if (companion) tr("Companion") else tr("Repeater"),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, color = roleColor,
                        modifier = Modifier.clip(BauhausCornerShape).background(roleColor.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    if (meshCareState.isActive) Spacer(modifier = Modifier.width(6.dp))
                }
                // Schonmodus warning pill
                if (meshCareState.isActive) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(BauhausCornerShape)
                            .background(bColors.ochre.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = tr("Schonmodus"),
                            tint = bColors.ochre,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = meshCareState.formattedRemainingTime,
                            color = bColors.ochre,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(R.string.topbar_menu_desc),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}
