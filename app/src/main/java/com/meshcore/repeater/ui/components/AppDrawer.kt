package com.meshcore.repeater.ui.components

import com.meshcore.repeater.i18n.tr
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * Zustandsbehafteter Wrapper: sammelt die sich sekündlich ändernden Flows (Mesh-Care-Countdown,
 * Filter-Statistik) erst HIER ein, damit nicht der gesamte MainAppContent samt
 * ModalNavigationDrawer jede Sekunde neu komponiert wird (Ursache für ruckelndes/zurückschnappendes Menü).
 */
@Composable
fun AppDrawerHost(
    currentRoute: NavDrawerItem,
    connectionState: ConnectionState,
    meshCareFlow: kotlinx.coroutines.flow.StateFlow<MeshCareState>,
    filterStatsFlow: kotlinx.coroutines.flow.StateFlow<FilterStats>,
    themeMode: ThemeMode,
    appLanguage: AppLanguage,
    /** Normalisierte, tatsächlich aktive Sprache ("de|en|nl|fr") – auch bei Systemsprache. */
    effectiveLang: String,
    firmwareVersion: String,
    navigationAllowed: Boolean,
    meshCareAvailable: Boolean,
    supportsBlacklist: Boolean = false,
    supportsWhitelist: Boolean = false,
    onStartGuidedSetup: () -> Unit,
    onItemClick: (NavDrawerItem) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onShowDisclaimer: () -> Unit
) {
    val meshCareState by meshCareFlow.collectAsState()
    val filterStats by filterStatsFlow.collectAsState()
    AppDrawerSheet(
        currentRoute = currentRoute, connectionState = connectionState, meshCareState = meshCareState, filterStats = filterStats,
        themeMode = themeMode, appLanguage = appLanguage, effectiveLang = effectiveLang, firmwareVersion = firmwareVersion, navigationAllowed = navigationAllowed,
        meshCareAvailable = meshCareAvailable, supportsBlacklist = supportsBlacklist, supportsWhitelist = supportsWhitelist, onStartGuidedSetup = onStartGuidedSetup, onItemClick = onItemClick,
        onThemeChange = onThemeChange, onLanguageChange = onLanguageChange, onShowDisclaimer = onShowDisclaimer
    )
}

@Composable
fun AppDrawerSheet(
    currentRoute: NavDrawerItem,
    connectionState: ConnectionState,
    meshCareState: MeshCareState,
    filterStats: FilterStats,
    themeMode: ThemeMode,
    appLanguage: AppLanguage,
    effectiveLang: String = com.meshcore.repeater.i18n.I18n.effectiveLanguage(if (appLanguage == AppLanguage.SYSTEM) "" else appLanguage.code),
    firmwareVersion: String = "",
    navigationAllowed: Boolean,
    meshCareAvailable: Boolean = true,
    supportsBlacklist: Boolean = false,
    supportsWhitelist: Boolean = false,
    onStartGuidedSetup: () -> Unit = {},
    onItemClick: (NavDrawerItem) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onShowDisclaimer: () -> Unit
) {
    val bColors = LocalBauhausColors.current

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        // Kein verticalScroll direkt auf dem Sheet (unbegrenzte Höhe störte das Drawer-Layout/-Swipen);
        // gescrollt wird nur der innere Inhalt.
        modifier = Modifier.width(316.dp)
    ) {
        Column(Modifier.fillMaxHeight().verticalScroll(androidx.compose.foundation.rememberScrollState())) {
        // === Drawer Header ===
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Markenzeichen: "ō"-Emblem mit den 3 Funk-Blitzen
                MeshchoseiLogoIcon(size = 44.dp)

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_title_repeater),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.app_title_configurator),
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.5.sp,
                            color = bColors.signalRed,
                            fontSize = 9.sp,
                            lineHeight = 11.sp
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Box(Modifier.width(2.5.dp).height(4.dp).background(bColors.signalRed))
                            Box(Modifier.width(2.5.dp).height(7.dp).background(bColors.cobalt))
                            Box(Modifier.width(2.5.dp).height(10.dp).background(bColors.ochre))
                            Box(Modifier.width(14.dp).height(2.dp).background(bColors.signalRed))
                        }
                    }
                }


            }

        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

        Spacer(modifier = Modifier.height(8.dp))
        if (navigationAllowed) {
            androidx.compose.material3.Button(
                onClick = onStartGuidedSetup,
                shape = BauhausCornerShape,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = bColors.cobalt),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(tr("▶ Geführtes Setup starten"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        // === Navigation Items ===
        NavDrawerItem.values().forEach { item ->
            val isSelected = currentRoute == item
            val isEnabled = (item == NavDrawerItem.CONNECTION || item == NavDrawerItem.ABOUT || navigationAllowed) &&
                (item != NavDrawerItem.MESH_CARE || meshCareAvailable) &&
                (item != NavDrawerItem.BLACKLIST || supportsBlacklist) &&      // nur wenn die Firmware Sperrlisten kann
                (item != NavDrawerItem.WHITELIST || supportsWhitelist)         // nur wenn die Firmware "filter wl" kann

            val badgeText = when (item) {
                NavDrawerItem.MESH_CARE -> null
                NavDrawerItem.BLACKLIST -> if (filterStats.activeMode == FilterMode.BLACKLIST) "ON" else null
                NavDrawerItem.WHITELIST -> if (filterStats.activeMode == FilterMode.WHITELIST) "ON" else null
                else -> null
            }

            val badgeColor = when (item) {
                NavDrawerItem.MESH_CARE -> bColors.ochre
                NavDrawerItem.BLACKLIST -> bColors.signalRed
                NavDrawerItem.WHITELIST -> bColors.cobalt
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
                            fontSize = 14.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(item.subtitleRes),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (isEnabled) 1f else 0.35f
                            ),
                            lineHeight = 13.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                badge = {
                    if (badgeText != null) {
                        // Eckiges Badge (BauhausCornerShape) statt des pillenförmigen Material-Badge
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier.clip(BauhausCornerShape).background(badgeColor.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                },
                selected = isSelected,
                onClick = { onItemClick(item) },
                shape = BauhausCornerShape,
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = bColors.signalRed.copy(alpha = 0.10f),
                    unselectedContainerColor = Color.Transparent
                ),
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 1.dp)
                    .heightIn(min = 58.dp)
                    .clip(BauhausCornerShape)
                    .alpha(if (isEnabled) 1f else 0.5f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEach { mode ->
                    val isActive = themeMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .alpha(if (isActive) 1f else 0.4f)
                            .clip(BauhausCornerShape)
                            .background(if (isActive) bColors.signalRed.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                            .border(if (isActive) 2.dp else 1.dp, if (isActive) bColors.signalRed else MaterialTheme.colorScheme.outline, BauhausCornerShape)
                            .clickable { onThemeChange(mode) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (mode == ThemeMode.LIGHT) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = null,
                            tint = if (mode == ThemeMode.LIGHT) bColors.ochre else bColors.cobalt,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                listOf(AppLanguage.DE to "🇩🇪", AppLanguage.EN to "🇬🇧", AppLanguage.NL to "🇳🇱", AppLanguage.FR to "🇫🇷").forEach { (lang, flag) ->
                    // Auch bei Systemsprache (AppLanguage.SYSTEM) ist die tatsächlich aktive Flagge hervorgehoben
                    val isActive = effectiveLang == lang.code
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .alpha(if (isActive) 1f else 0.4f)
                            .clip(BauhausCornerShape)
                            .background(if (isActive) bColors.cobalt.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                            .border(if (isActive) 2.dp else 1.dp, if (isActive) bColors.cobalt else MaterialTheme.colorScheme.outline, BauhausCornerShape)
                            .clickable { onLanguageChange(lang) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = flag, fontSize = 16.sp)
                    }
                }
            }

            val paletteState = com.meshcore.repeater.ui.theme.LocalBauhausPalette.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
            ) {
                com.meshcore.repeater.ui.theme.BauhausPalette.values().forEach { pal ->
                    val selected = paletteState.value == pal
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .alpha(if (selected) 1f else 0.45f)
                            .clip(BauhausCornerShape)
                            .clickable { paletteState.value = pal }
                    ) {
                        Box(Modifier.weight(1f).fillMaxHeight().background(pal.primary))
                        Box(Modifier.weight(1f).fillMaxHeight().background(pal.secondary))
                        Box(Modifier.weight(1f).fillMaxHeight().background(pal.tertiary))
                    }
                }
            }
        }
        }
    }
}
