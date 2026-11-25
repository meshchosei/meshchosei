package com.meshcore.repeater.ui.screens

import com.meshcore.repeater.ui.components.hintedVerticalScroll

import com.meshcore.repeater.i18n.tr
import com.meshcore.repeater.ui.components.BusyButton
import com.meshcore.repeater.ui.components.BauhausSwitch
import com.meshcore.repeater.ui.components.RefreshButton
import com.meshcore.repeater.viewmodel.BusyKey
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshcore.repeater.R
import com.meshcore.repeater.model.FilterMode
import com.meshcore.repeater.ui.components.SlideToConfirm
import com.meshcore.repeater.ui.theme.BauhausCornerShape
import com.meshcore.repeater.ui.theme.LocalBauhausColors
import com.meshcore.repeater.viewmodel.RepeaterViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterScreen(viewModel: RepeaterViewModel, view: FilterMode = FilterMode.BLACKLIST) {
    val bColors = LocalBauhausColors.current
    val stats by viewModel.filterStats.collectAsState()
    // Filterlisten kommen aus dem einmaligen Start-Scan; erneut nur über den Aktualisieren-Button
    val cap by viewModel.deviceCapabilities.collectAsState()
    val canWhitelist = cap.supportsWhitelist
    val isWlView = view == FilterMode.WHITELIST
    val filterSwitchPending by viewModel.filterSwitchPending.collectAsState()
    val busy by viewModel.busyActions.collectAsState()
    val initialReadDone by viewModel.hasCompletedInitialRead.collectAsState()
    // Einmalig beim Öffnen: Filter aktiv, aber Kanalliste leer (z. B. über LoRa abgeschnitten) -> automatisch nachladen
    LaunchedEffect(view, initialReadDone) { if (initialReadDone) viewModel.ensureFilterChannelsLoaded(view) }
    val scrollState = rememberScrollState()

    // Whitelist State
    val wlChannels by viewModel.whitelistChannels.collectAsState()
    val wlPaths by viewModel.whitelistPaths.collectAsState()
    var newWlChannelInput by remember { mutableStateOf("") }
    var newWlPathInput by remember { mutableStateOf("") }
    var newWlPathComment by remember { mutableStateOf("") }

    // Blacklist State
    val blChannels by viewModel.blockedChannels.collectAsState()
    val blPaths by viewModel.blockedPaths.collectAsState()
    val senderRules by viewModel.senderRules.collectAsState()
    val textRules by viewModel.textRules.collectAsState()
    val watchChannels by viewModel.watchChannels.collectAsState()

    var newBlChannelInput by remember { mutableStateOf("") }
    var newBlPathInput by remember { mutableStateOf("") }
    var newBlPathComment by remember { mutableStateOf("") }

    var ruleTypeTab by remember { mutableIntStateOf(0) } // 0 = Sender, 1 = Text
    var newRulePattern by remember { mutableStateOf("") }
    var newRuleTimeout by remember { mutableStateOf("0") }
    var newRuleProb by remember { mutableStateOf("100") }

    var newWatchInput by remember { mutableStateOf("") }

    val presetWlChannels = listOf("Public", "#mesh-de", "#notfunk", "#test")
    val presetBlChannels = listOf("#spam-bot", "#crypto-ad", "#test-flood")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .hintedVerticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // =====================================================================
        // SECTION 1: MASTER FILTER ENGINE & DRY-RUN
        // =====================================================================
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(if (isWlView) R.string.menu_whitelist else R.string.menu_blacklist).uppercase(), style = MaterialTheme.typography.titleMedium, color = if (isWlView) bColors.cobalt else bColors.signalRed, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            RefreshButton(onClick = { viewModel.refreshAllFiltersFromNode() }, busy = BusyKey.FILTER_REFRESH in busy, color = if (isWlView) bColors.cobalt else bColors.signalRed)
        }
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = BauhausCornerShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Ein/Aus NUR für die Liste dieses Screens (kein Umschalten zwischen Blacklist und Whitelist)
                // activeMode = null bei ausgeschaltetem Filter ("off"); sonst exakt BLACKLIST oder WHITELIST
                val isBlacklistActive = stats.activeMode == FilterMode.BLACKLIST
                val isWhitelistActive = stats.activeMode == FilterMode.WHITELIST
                val listActive = if (isWlView) isWhitelistActive else isBlacklistActive
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isWlView) tr("Whitelist aktiv") else tr("Blacklist aktiv"),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                listActive -> if (isWlView) tr("AN – nur freigegebene Kanäle und Pfade werden weitergeleitet.") else tr("AN – gesperrte Kanäle, Pfade und Regeln werden verworfen.")
                                else -> tr("AUS – der Repeater leitet alle Pakete ungefiltert weiter.")
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BauhausSwitch(
                        checked = listActive,
                        enabled = !filterSwitchPending,
                        onCheckedChange = { on -> viewModel.setFilterActive(if (on) view else null) },
                        activeColor = if (isWlView) bColors.cobalt else bColors.signalRed
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline
                )

                // Dry-Run Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.filter_dryrun_switch),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (stats.dryRun)
                                stringResource(R.string.filter_dryrun_on_desc)
                            else
                                stringResource(R.string.filter_dryrun_off_desc),
                            fontSize = 12.sp,
                            color = if (stats.dryRun) bColors.ochre else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Dry-Run bleibt Ocker (Warnzustand "nur protokollieren", vgl. STYLE_GUIDE §2.3)
                    BauhausSwitch(
                        checked = stats.dryRun,
                        enabled = BusyKey.FILTER_DRYRUN !in busy,   // Anzeige wechselt erst nach Bestätigung
                        onCheckedChange = { viewModel.setFilterDryRun(it) },
                        activeColor = bColors.ochre
                    )
                }

            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // =====================================================================
        // SECTION 2: DYNAMIC MODE NOTICE BANNER
        // =====================================================================
        val (bannerBg, bannerBorder, bannerIcon, bannerText) = if (isWlView) {
            listOf(
                bColors.cobalt.copy(alpha = 0.12f),
                bColors.cobalt,
                Icons.Default.Security,
                stringResource(R.string.filter_banner_whitelist)
            )
        } else {
            listOf(
                bColors.signalRed.copy(alpha = 0.12f),
                bColors.signalRed,
                Icons.Default.Block,
                stringResource(R.string.filter_banner_blacklist)
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = bannerBg as Color),
            shape = BauhausCornerShape,
            border = BorderStroke(1.dp, bannerBorder as Color)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = bannerIcon as androidx.compose.ui.graphics.vector.ImageVector,
                    contentDescription = null,
                    tint = bannerBorder,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = bannerText as String,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // =====================================================================
        // SECTION 3: DYNAMIC MODE CONTROLS
        // =====================================================================
        if (isWlView) {
            // =================================================================
            // WHITELIST-ANSICHT: Kanäle & Pfad-Präfixe (filter wl ...)
            // =================================================================
            Text(
                text = stringResource(R.string.filter_wl_channel_header),
                style = MaterialTheme.typography.titleMedium,
                color = bColors.cobalt,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = BauhausCornerShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.filter_wl_presets_title),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presetWlChannels.forEach { ch ->
                            SuggestionChip(
                                enabled = BusyKey.WL_CHANNEL !in busy,
                                onClick = { viewModel.addWhitelistChannel(ch) },
                                label = { Text(ch, fontSize = 12.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = bColors.cobalt
                                ),
                                shape = BauhausCornerShape,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Input Field
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newWlChannelInput,
                            onValueChange = { newWlChannelInput = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.filter_wl_channel_input_hint), fontSize = 13.sp) },
                            singleLine = true,
                            shape = BauhausCornerShape
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BusyButton(busy = BusyKey.WL_CHANNEL in busy, containerColor = bColors.cobalt, contentColor = Color.White, contentPadding = PaddingValues(horizontal = 12.dp),
                            onClick = {
                                if (viewModel.addWhitelistChannel(newWlChannelInput)) newWlChannelInput = ""
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add), tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.filter_wl_channels_count, wlChannels.size),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    wlChannels.forEach { ch ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(BauhausCornerShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, BauhausCornerShape)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(ch.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                if (ch.hashHex.isNotEmpty()) {
                                    Text(tr("Hash: {0}", ch.hashHex), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(enabled = BusyKey.WL_CHANNEL !in busy, onClick = { viewModel.removeWhitelistChannel(ch) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = bColors.signalRed)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Whitelist Paths
            Text(
                text = stringResource(R.string.filter_wl_path_header),
                style = MaterialTheme.typography.titleMedium,
                color = bColors.cobalt,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = BauhausCornerShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newWlPathInput,
                            onValueChange = { newWlPathInput = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.filter_wl_path_input_hint), fontSize = 13.sp) },
                            singleLine = true,
                            shape = BauhausCornerShape
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BusyButton(busy = BusyKey.WL_PATH in busy, containerColor = bColors.cobalt, contentColor = Color.White, contentPadding = PaddingValues(horizontal = 12.dp),
                            onClick = {
                                if (viewModel.addWhitelistPath(newWlPathInput, newWlPathComment)) { newWlPathInput = ""; newWlPathComment = "" }
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add), tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.filter_wl_paths_count, wlPaths.size),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    wlPaths.forEach { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(BauhausCornerShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, BauhausCornerShape)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    p.prefixHex.uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = bColors.cobalt,
                                    fontFamily = FontFamily.Monospace
                                )
                                if (p.comment.isNotEmpty()) {
                                    Text(p.comment, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(enabled = BusyKey.WL_PATH !in busy, onClick = { viewModel.removeWhitelistPath(p) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = bColors.signalRed)
                            }
                        }
                    }
                }
            }

        } else {
            // =================================================================
            // BLACKLIST MODE: Kanäle, Pfade, Inhaltsregeln, Watch, Alter & Hops
            // =================================================================

            // 1. Blocked Channels (filter channel add/remove)
            Text(
                text = stringResource(R.string.filter_bl_channel_header),
                style = MaterialTheme.typography.titleMedium,
                color = bColors.signalRed,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = BauhausCornerShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.filter_wl_presets_title),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presetBlChannels.forEach { ch ->
                            SuggestionChip(
                                enabled = BusyKey.BL_CHANNEL !in busy,
                                onClick = { viewModel.addBlockedChannel(ch) },
                                label = { Text(ch, fontSize = 12.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = bColors.signalRed
                                ),
                                shape = BauhausCornerShape,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newBlChannelInput,
                            onValueChange = { newBlChannelInput = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.filter_bl_channel_input_hint), fontSize = 13.sp) },
                            singleLine = true,
                            shape = BauhausCornerShape
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BusyButton(busy = BusyKey.BL_CHANNEL in busy, containerColor = bColors.signalRed, contentColor = Color.White, contentPadding = PaddingValues(horizontal = 12.dp),
                            onClick = {
                                if (viewModel.addBlockedChannel(newBlChannelInput)) newBlChannelInput = ""
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add), tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.filter_bl_channels_count, blChannels.size),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    blChannels.forEach { ch ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(BauhausCornerShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, BauhausCornerShape)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(ch.name, fontWeight = FontWeight.Bold, color = bColors.signalRed)
                            IconButton(enabled = BusyKey.BL_CHANNEL !in busy, onClick = { viewModel.removeBlockedChannel(ch.name) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = bColors.signalRed)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Blocked Path Prefixes (filter path add/remove)
            Text(
                text = stringResource(R.string.filter_bl_path_header),
                style = MaterialTheme.typography.titleMedium,
                color = bColors.signalRed,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = BauhausCornerShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newBlPathInput,
                            onValueChange = { newBlPathInput = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.filter_bl_path_input_hint), fontSize = 13.sp) },
                            singleLine = true,
                            shape = BauhausCornerShape
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BusyButton(busy = BusyKey.BL_PATH in busy, containerColor = bColors.signalRed, contentColor = Color.White, contentPadding = PaddingValues(horizontal = 12.dp),
                            onClick = {
                                if (viewModel.addBlockedPath(newBlPathInput, newBlPathComment)) { newBlPathInput = ""; newBlPathComment = "" }
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add), tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.filter_bl_paths_count, blPaths.size),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    blPaths.forEach { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(BauhausCornerShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, BauhausCornerShape)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    p.prefixHex.uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = bColors.signalRed,
                                    fontFamily = FontFamily.Monospace
                                )
                                if (p.comment.isNotEmpty()) {
                                    Text(p.comment, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(enabled = BusyKey.BL_PATH !in busy, onClick = { viewModel.removeBlockedPath(p.prefixHex) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = bColors.signalRed)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Content Rules: Sender & Text (filter sender/text)
            Text(
                text = stringResource(R.string.filter_rules_header),
                style = MaterialTheme.typography.titleMedium,
                color = bColors.ochre,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = BauhausCornerShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    TabRow(
                        selectedTabIndex = ruleTypeTab,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = bColors.ochre,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[ruleTypeTab]),
                                color = bColors.ochre
                            )
                        },
                        modifier = Modifier
                            .clip(BauhausCornerShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, BauhausCornerShape)
                    ) {
                        Tab(
                            selected = ruleTypeTab == 0,
                            onClick = { ruleTypeTab = 0 },
                            text = { Text(stringResource(R.string.filter_sender_sub)) }
                        )
                        Tab(
                            selected = ruleTypeTab == 1,
                            onClick = { ruleTypeTab = 1 },
                            text = { Text(stringResource(R.string.filter_text_sub)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newRulePattern,
                        onValueChange = { newRulePattern = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.filter_pattern_hint), fontSize = 13.sp) },
                        singleLine = true,
                        shape = BauhausCornerShape
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newRuleTimeout,
                            onValueChange = { newRuleTimeout = it },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.filter_secs_hint), fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = BauhausCornerShape
                        )
                        OutlinedTextField(
                            value = newRuleProb,
                            onValueChange = { newRuleProb = it },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.filter_prob_hint), fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = BauhausCornerShape
                        )
                        BusyButton(busy = BusyKey.RULE in busy, containerColor = bColors.ochre, contentColor = Color.Black, contentPadding = PaddingValues(horizontal = 12.dp),
                            onClick = {
                                val s = newRuleTimeout.toIntOrNull() ?: 0
                                val p = (newRuleProb.toIntOrNull() ?: 100).coerceIn(1, 100)
                                val sent = if (ruleTypeTab == 0) viewModel.addSenderRule(newRulePattern, s, p) else viewModel.addTextRule(newRulePattern, s, p)
                                if (sent) newRulePattern = ""
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add), tint = Color.Black)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val activeList = if (ruleTypeTab == 0) senderRules else textRules
                    activeList.forEach { r ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(BauhausCornerShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, BauhausCornerShape)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(r.pattern, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    "${r.dropProbabilityPercent}% drop • ${if (r.timeoutSecs > 0) "${r.timeoutSecs}s timeout" else "permanent"}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(enabled = BusyKey.RULE !in busy, onClick = {
                                if (ruleTypeTab == 0) viewModel.removeSenderRule(r.pattern)
                                else viewModel.removeTextRule(r.pattern)
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = bColors.signalRed)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Watch Channels (filter watch add/remove)
            Text(
                text = stringResource(R.string.filter_watch_header),
                style = MaterialTheme.typography.titleMedium,
                color = bColors.cobalt,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = BauhausCornerShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.filter_watch_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newWatchInput,
                            onValueChange = { newWatchInput = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.filter_watch_input_hint), fontSize = 13.sp) },
                            singleLine = true,
                            shape = BauhausCornerShape
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BusyButton(busy = BusyKey.WATCH in busy, containerColor = bColors.cobalt, contentColor = Color.White, contentPadding = PaddingValues(horizontal = 12.dp),
                            onClick = {
                                if (viewModel.addWatchChannel(newWatchInput)) newWatchInput = ""
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add), tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.filter_watch_count, watchChannels.size),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    watchChannels.forEach { w ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(BauhausCornerShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, BauhausCornerShape)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Visibility, contentDescription = null, tint = bColors.cobalt, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(w.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(enabled = BusyKey.WATCH !in busy, onClick = { viewModel.removeWatchChannel(w.name) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = bColors.signalRed)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5. Message Age & Max Hops (filter age / hops)
            Text(
                text = stringResource(R.string.filter_params_header),
                style = MaterialTheme.typography.titleMedium,
                color = bColors.cobalt,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = BauhausCornerShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "${stringResource(R.string.filter_age_label)} ${if (stats.ageMinutes == 0) tr("AUS") else tr("{0} Min.", stats.ageMinutes)}",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp
                    )
                    // Nach der Antwort (busy endet) springt der Slider auf den bestätigten Wert zurück
                    val ageBusy = BusyKey.FILTER_AGE in busy
                    var ageSliderVal by remember(stats.ageMinutes, ageBusy) { mutableFloatStateOf(stats.ageMinutes.toFloat()) }
                    Slider(
                        value = ageSliderVal,
                        enabled = !ageBusy,
                        onValueChange = { ageSliderVal = it },
                        onValueChangeFinished = { viewModel.setFilterAge(ageSliderVal.toInt()) },
                        valueRange = 0f..120f,
                        steps = 23,
                        colors = SliderDefaults.colors(
                            thumbColor = bColors.cobalt,
                            activeTrackColor = bColors.cobalt
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "${stringResource(R.string.filter_hops_label)} ${stats.maxHops}",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp
                    )
                    val hopsBusy = BusyKey.FILTER_HOPS in busy
                    var hopsSliderVal by remember(stats.maxHops, hopsBusy) { mutableFloatStateOf(stats.maxHops.toFloat()) }
                    Slider(
                        value = hopsSliderVal,
                        enabled = !hopsBusy,
                        onValueChange = { hopsSliderVal = it },
                        onValueChangeFinished = { viewModel.setFilterMaxHops(hopsSliderVal.toInt()) },
                        valueRange = 1f..15f,
                        steps = 13,
                        colors = SliderDefaults.colors(
                            thumbColor = bColors.cobalt,
                            activeTrackColor = bColors.cobalt
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // =====================================================================
        // SECTION 4: LIVE FILTER STATISTIKEN (BEIDE MODI)
        // =====================================================================
        Text(
            text = stringResource(R.string.filter_stats_header),
            style = MaterialTheme.typography.titleMedium,
            color = bColors.cobalt,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = BauhausCornerShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(stringResource(R.string.filter_stats_allowed), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${stats.allowedPackets}", fontWeight = FontWeight.Bold, color = bColors.successGreen, fontSize = 18.sp)
                    }
                    Column {
                        Text(stringResource(R.string.filter_stats_dropped), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${stats.droppedPackets}", fontWeight = FontWeight.Bold, color = bColors.signalRed, fontSize = 18.sp)
                    }
                    Column {
                        Text(stringResource(R.string.filter_stats_droprate), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("%.1f%%".format(stats.dropRatePercent), fontWeight = FontWeight.Bold, color = bColors.ochre, fontSize = 18.sp)
                    }
                    Column {
                        Text(stringResource(R.string.filter_stats_airtime), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("%.1f s".format(stats.savedAirtimeMs / 1000f), fontWeight = FontWeight.Bold, color = bColors.cobalt, fontSize = 18.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // =====================================================================
        // SECTION 5: GESCHÜTZTER SLIDE-TO-CONFIRM FÜR "filter RESET" (BEIDE MODI)
        // =====================================================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, bColors.signalRed, BauhausCornerShape),
            colors = CardDefaults.cardColors(
                containerColor = bColors.signalRed.copy(alpha = if (bColors.isDark) 0.12f else 0.06f)
            ),
            shape = BauhausCornerShape
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = bColors.signalRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.filter_reset_header),
                        fontWeight = FontWeight.Bold,
                        color = bColors.signalRed,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.filter_reset_desc),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                SlideToConfirm(
                    text = stringResource(R.string.filter_reset_slide_text),
                    onConfirmed = { viewModel.executeFilterReset() },
                    trackColor = if (bColors.isDark) Color(0xFF2A1C1C) else Color(0xFFFFEBEE),
                    accentColor = bColors.signalRed,
                    thumbColor = bColors.signalRed
                )
            }
        }
    }
}
