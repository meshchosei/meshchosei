package com.meshcore.repeater.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshcore.repeater.R
import com.meshcore.repeater.model.AppLanguage
import com.meshcore.repeater.model.ConnectionState
import com.meshcore.repeater.model.FilterMode
import com.meshcore.repeater.model.FilterStats
import com.meshcore.repeater.model.MeshCareState
import com.meshcore.repeater.model.ThemeMode
import com.meshcore.repeater.ui.navigation.NavDrawerItem
import com.meshcore.repeater.ui.theme.BauhausCornerShape
import com.meshcore.repeater.ui.theme.LocalBauhausColors

@Composable
fun AppDrawerSheet(
    currentRoute: NavDrawerItem,
    connectionState: ConnectionState,
    meshCareState: MeshCareState,
    filterStats: FilterStats,
    themeMode: ThemeMode,
    appLanguage: AppLanguage,
    firmwareVersion: String = "",
    offlinePreviewEnabled: Boolean,
    navigationAllowed: Boolean,
    onItemClick: (NavDrawerItem) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onOfflinePreviewChange: (Boolean) -> Unit,
    onShowDisclaimer: () -> Unit
) {
    val bColors = LocalBauhausColors.current

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.width(316.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())
    ) {
        // === Drawer Header ===
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(BauhausCornerShape)
                        .background(bColors.signalRed.copy(alpha = 0.18f))
                        .border(1.dp, bColors.signalRed.copy(alpha = 0.4f), BauhausCornerShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CellTower,
                        contentDescription = null,
                        tint = bColors.signalRed,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_title_repeater),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp
                    )
                    Text(
                        text = stringResource(R.string.app_title_configurator),
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.sp,
                        color = bColors.signalRed,
                        fontSize = 11.sp
                    )
                }


            }

            Spacer(modifier = Modifier.height(14.dp))

            // Connection status pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(BauhausCornerShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, BauhausCornerShape)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            when (connectionState) {
                                is ConnectionState.Connected -> bColors.successGreen
                                is ConnectionState.Connecting -> bColors.ochre
                                is ConnectionState.Error -> bColors.signalRed
                                else -> Color.Gray
                            }
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when (connectionState) {
                        is ConnectionState.Connected -> connectionState.deviceName
                        is ConnectionState.Connecting -> stringResource(R.string.status_connecting)
                        is ConnectionState.Error -> stringResource(R.string.status_error)
                        else -> stringResource(R.string.status_no_device)
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

        Spacer(modifier = Modifier.height(8.dp))

        // === Navigation Items ===
        NavDrawerItem.values().forEach { item ->
            val isSelected = currentRoute == item
            val isEnabled = item == NavDrawerItem.CONNECTION || item == NavDrawerItem.ABOUT || navigationAllowed

            val badgeText = when (item) {
                NavDrawerItem.MESH_CARE -> if (meshCareState.isActive) meshCareState.formattedRemainingTime else null
                NavDrawerItem.FILTER_WHITELIST -> if (filterStats.mode == FilterMode.WHITELIST) "WL" else null
                NavDrawerItem.CONNECTION -> if (connectionState is ConnectionState.Connected) stringResource(R.string.status_active) else null
                else -> null
            }

            val badgeColor = when (item) {
                NavDrawerItem.MESH_CARE -> bColors.ochre
                NavDrawerItem.FILTER_WHITELIST -> bColors.cobalt
                NavDrawerItem.CONNECTION -> bColors.signalRed
                else -> bColors.successGreen
            }

            NavigationDrawerItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = stringResource(item.titleRes),
                        tint = when {
                            !isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            isSelected -> bColors.signalRed
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                },
                label = {
                    Column {
                        Text(
                            text = stringResource(item.titleRes),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                !isEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                isSelected -> bColors.signalRed
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            fontSize = 14.sp
                        )
                        Text(
                            text = stringResource(item.subtitleRes),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (isEnabled) 1f else 0.35f
                            )
                        )
                    }
                },
                badge = {
                    if (badgeText != null) {
                        Badge(
                            containerColor = badgeColor.copy(alpha = 0.2f),
                            contentColor = badgeColor
                        ) {
                            Text(
                                text = badgeText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                selected = isSelected,
                onClick = { onItemClick(item) },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = bColors.signalRed.copy(alpha = 0.10f),
                    unselectedContainerColor = Color.Transparent
                ),
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    .clip(BauhausCornerShape)
                    .alpha(if (isEnabled) 1f else 0.5f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Spacer(modifier = Modifier.weight(1f))
        // === Bauhaus Erscheinungsbild & Sprache ===
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {


            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Farbmodus-Icons: Auto (Zahnrad+A) | Hell (Sonne) | Dunkel (Mond)
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEach { mode ->
                        val isActive = themeMode == mode
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .alpha(if (isActive) 1f else 0.38f)
                                .clip(BauhausCornerShape)
                                .background(if (isActive) bColors.signalRed.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant)
                                .border(if (isActive) 2.dp else 1.dp, if (isActive) bColors.signalRed else MaterialTheme.colorScheme.outline, BauhausCornerShape)
                                .clickable { onThemeChange(mode) },
                            contentAlignment = Alignment.Center
                        ) {
                            when (mode) {
                                ThemeMode.SYSTEM -> {
                                    Icon(Icons.Default.Settings, contentDescription = "Auto", tint = bColors.signalRed, modifier = Modifier.size(20.dp))
                                    Text("A", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp).background(bColors.signalRed, BauhausCornerShape).padding(horizontal = 2.dp))
                                }
                                ThemeMode.LIGHT -> Icon(Icons.Default.LightMode, contentDescription = "Hell", tint = bColors.ochre, modifier = Modifier.size(20.dp))
                                ThemeMode.DARK -> Icon(Icons.Default.DarkMode, contentDescription = "Dunkel", tint = bColors.cobalt, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                // Sprach-Flaggen: Auto (Globus+A) | DE | EN
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    listOf(AppLanguage.DE, AppLanguage.EN, AppLanguage.NL).forEach { lang ->
                        val isActive = appLanguage == lang
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .alpha(if (isActive) 1f else 0.38f)
                                .clip(BauhausCornerShape)
                                .background(if (isActive) bColors.cobalt.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant)
                                .border(if (isActive) 2.dp else 1.dp, if (isActive) bColors.cobalt else MaterialTheme.colorScheme.outline, BauhausCornerShape)
                                .clickable { onLanguageChange(lang) },
                            contentAlignment = Alignment.Center
                        ) {
                            when (lang) {
                                AppLanguage.SYSTEM -> {
                                    Icon(Icons.Default.Language, contentDescription = "Auto", tint = bColors.cobalt, modifier = Modifier.size(20.dp))
                                    Text("A", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp).background(bColors.cobalt, BauhausCornerShape).padding(horizontal = 2.dp))
                                }
                                AppLanguage.DE -> Text("🇩🇪", fontSize = 18.sp)
                                AppLanguage.EN -> Text("🇬🇧", fontSize = 18.sp)
                                AppLanguage.NL -> Text("🇳🇱", fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
