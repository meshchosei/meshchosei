package com.meshcore.repeater.ui.components

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

                // Erkennung läuft noch → kurzer Hinweis-Chip
                if (!capabilities.detectionComplete && connectionState is ConnectionState.Connected) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(BauhausCornerShape)
                            .background(bColors.cobalt.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.caps_detecting),
                            color = bColors.cobalt,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Schonmodus warning pill
                if (meshCareState.isActive) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(BauhausCornerShape)
                            .background(bColors.ochre.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Schonmodus",
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

                // Connection status indicator pill
                val (statusColor, statusText) = when (connectionState) {
                    is ConnectionState.Connected -> Pair(bColors.successGreen, stringResource(R.string.status_connected))
                    is ConnectionState.Connecting -> Pair(bColors.ochre, stringResource(R.string.status_connecting))
                    is ConnectionState.Error -> Pair(bColors.signalRed, stringResource(R.string.status_error))
                    is ConnectionState.Disconnected -> Pair(Color.Gray, stringResource(R.string.status_disconnected))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(BauhausCornerShape)
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
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
