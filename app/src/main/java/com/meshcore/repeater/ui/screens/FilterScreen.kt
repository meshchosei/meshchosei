package com.meshcore.repeater.ui.screens

import com.meshcore.repeater.ui.components.hintedVerticalScroll

import com.meshcore.repeater.i18n.tr
import com.meshcore.repeater.ui.components.DeleteBusyIconButton
import com.meshcore.repeater.ui.components.FixedBusyIconButton
import com.meshcore.repeater.ui.components.FineSlider
import com.meshcore.repeater.ui.components.PublicBlockConfirmDialog
import com.meshcore.repeater.ui.components.BauhausSwitch
import com.meshcore.repeater.ui.components.InlineRefreshIcon
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
import androidx.compose.ui.text.style.TextOverflow
import com.meshcore.repeater.model.ChannelNameRules
import com.meshcore.repeater.model.ListBusyRules
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
import com.meshcore.repeater.model.FilterCounters
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
    // Statistik-Karte (v1.22): beim Öffnen still "filter" + "filter stats air" lesen (enthält auch den Ein/Aus-Status)
    LaunchedEffect(view, initialReadDone) { if (initialReadDone) { viewModel.refreshFilterStats(); viewModel.ensureFilterChannelsLoaded(view) } }
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
    // v1.23: welche Listenzeile gerade gelöscht wird (Braille-Punkte nur dort) und Public-Sperre zur Bestätigung
    var deletingId by remember { mutableStateOf<String?>(null) }
    // Nach Ende des Löschens (oder wenn nichts gesendet wurde) Markierung lösen, damit „+“ beim nächsten Hinzufügen wieder dreht
    LaunchedEffect(busy, deletingId) {
        val id = deletingId ?: return@LaunchedEffect
        if (ListBusyRules.keyOf(id) !in viewModel.busyActions.value) deletingId = null
    }
    var confirmPublicBlock by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    /** Blacklist: Name normalisieren (#-Präfix, „Public“); „Public“ erst nach Bestätigung sperren. */
    fun requestBlacklistChannel(raw: String, onSent: () -> Unit) {
        val n = ChannelNameRules.normalize(raw, whitelist = false)
        if (n.isEmpty()) return
        if (ChannelNameRules.needsPublicBlockConfirm(n, blacklist = true)) confirmPublicBlock = n to onSent
        else if (viewModel.addBlockedChannel(n)) onSent()
    }
    confirmPublicBlock?.let { (name, onSent) ->
        PublicBlockConfirmDialog(
            channel = name,
            onConfirm = { confirmPublicBlock = null; if (viewModel.addBlockedChannel(name)) onSent() },
            onDismiss = { confirmPublicBlock = null }
        )
    }

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
            InlineRefreshIcon(onClick = { viewModel.refreshAllFiltersFromNode() }, busy = BusyKey.FILTER_REFRESH in busy, contentDescription = tr("Aktualisieren"), color = if (isWlView) bColors.cobalt else bColors.signalRed)
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
                            placeholder = { Text(stringResource(R.string.filter_wl_channel_input_hint), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            singleLine = true,
                            shape = BauhausCornerShape
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FixedBusyIconButton(busy = ListBusyRules.addButtonBusy(BusyKey.WL_CHANNEL in busy, deletingId, BusyKey.WL_CHANNEL), containerColor = bColors.cobalt, contentColor = Color.White, icon = Icons.Default.Add, contentDescription = stringResource(R.string.action_add),
                            onClick = {
                                if (viewModel.addWhitelistChannel(newWlChannelInput)) newWlChannelInput = ""
                            }
                        )
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
                            DeleteBusyIconButton(busy = ListBusyRules.rowDeleteBusy(BusyKey.WL_CHANNEL in busy, deletingId, BusyKey.WL_CHANNEL, ch.name), enabled = BusyKey.WL_CHANNEL !in busy,
                                onClick = { deletingId = ListBusyRules.tag(BusyKey.WL_CHANNEL, ch.name); viewModel.removeWhitelistChannel(ch) },
                                icon = Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = bColors.signalRed)
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
                            placeholder = { Text(stringResource(R.string.filter_wl_path_input_hint), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            singleLine = true,
                            shape = BauhausCornerShape
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FixedBusyIconButton(busy = ListBusyRules.addButtonBusy(BusyKey.WL_PATH in busy, deletingId, BusyKey.WL_PATH), containerColor = bColors.cobalt, contentColor = Color.White, icon = Icons.Default.Add, contentDescription = stringResource(R.string.action_add),
                            onClick = {
                                if (viewModel.addWhitelistPath(newWlPathInput, newWlPathComment)) { newWlPathInput = ""; newWlPathComment = "" }
                            }
                        )
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
                            DeleteBusyIconButton(busy = ListBusyRules.rowDeleteBusy(BusyKey.WL_PATH in busy, deletingId, BusyKey.WL_PATH, p.prefixHex), enabled = BusyKey.WL_PATH !in busy,
                                onClick = { deletingId = ListBusyRules.tag(BusyKey.WL_PATH, p.prefixHex); viewModel.removeWhitelistPath(p) },
                                icon = Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = bColors.signalRed)
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
                                onClick = { requestBlacklistChannel(ch) {} },
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
                            placeholder = { Text(stringResource(R.string.filter_bl_channel_input_hint), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            singleLine = true,
                            shape = BauhausCornerShape
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FixedBusyIconButton(busy = ListBusyRules.addButtonBusy(BusyKey.BL_CHANNEL in busy, deletingId, BusyKey.BL_CHANNEL), containerColor = bColors.signalRed, contentColor = Color.White, icon = Icons.Default.Add, contentDescription = stringResource(R.string.action_add),
                            onClick = {
                                requestBlacklistChannel(newBlChannelInput) { newBlChannelInput = "" }
                            }
                        )
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
                            DeleteBusyIconButton(busy = ListBusyRules.rowDeleteBusy(BusyKey.BL_CHANNEL in busy, deletingId, BusyKey.BL_CHANNEL, ch.name), enabled = BusyKey.BL_CHANNEL !in busy,
                                onClick = { deletingId = ListBusyRules.tag(BusyKey.BL_CHANNEL, ch.name); viewModel.removeBlockedChannel(ch.name) },
                                icon = Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = bColors.signalRed)
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
                            placeholder = { Text(stringResource(R.string.filter_bl_path_input_hint), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            singleLine = true,
                            shape = BauhausCornerShape
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FixedBusyIconButton(busy = ListBusyRules.addButtonBusy(BusyKey.BL_PATH in busy, deletingId, BusyKey.BL_PATH), containerColor = bColors.signalRed, contentColor = Color.White, icon = Icons.Default.Add, contentDescription = stringResource(R.string.action_add),
                            onClick = {
                                if (viewModel.addBlockedPath(newBlPathInput, newBlPathComment)) { newBlPathInput = ""; newBlPathComment = "" }
                            }
                        )
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
                            DeleteBusyIconButton(busy = ListBusyRules.rowDeleteBusy(BusyKey.BL_PATH in busy, deletingId, BusyKey.BL_PATH, p.prefixHex), enabled = BusyKey.BL_PATH !in busy,
                                onClick = { deletingId = ListBusyRules.tag(BusyKey.BL_PATH, p.prefixHex); viewModel.removeBlockedPath(p.prefixHex) },
                                icon = Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = bColors.signalRed)
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
                        placeholder = { Text(stringResource(R.string.filter_pattern_hint), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                        FixedBusyIconButton(busy = ListBusyRules.addButtonBusy(BusyKey.RULE in busy, deletingId, BusyKey.RULE), containerColor = bColors.ochre, contentColor = Color.Black, icon = Icons.Default.Add, contentDescription = stringResource(R.string.action_add),
                            onClick = {
                                val s = newRuleTimeout.toIntOrNull() ?: 0
                                val p = (newRuleProb.toIntOrNull() ?: 100).coerceIn(1, 100)
                                val sent = if (ruleTypeTab == 0) viewModel.addSenderRule(newRulePattern, s, p) else viewModel.addTextRule(newRulePattern, s, p)
                                if (sent) newRulePattern = ""
                            }
                        )
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
                            DeleteBusyIconButton(busy = ListBusyRules.rowDeleteBusy(BusyKey.RULE in busy, deletingId, BusyKey.RULE, r.pattern), enabled = BusyKey.RULE !in busy,
                                onClick = { deletingId = ListBusyRules.tag(BusyKey.RULE, r.pattern); if (ruleTypeTab == 0) viewModel.removeSenderRule(r.pattern)
                                else viewModel.removeTextRule(r.pattern) },
                                icon = Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = bColors.signalRed)
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
                            placeholder = { Text(stringResource(R.string.filter_watch_input_hint), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            singleLine = true,
                            shape = BauhausCornerShape
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FixedBusyIconButton(busy = ListBusyRules.addButtonBusy(BusyKey.WATCH in busy, deletingId, BusyKey.WATCH), containerColor = bColors.cobalt, contentColor = Color.White, icon = Icons.Default.Add, contentDescription = stringResource(R.string.action_add),
                            onClick = {
                                if (viewModel.addWatchChannel(newWatchInput)) newWatchInput = ""
                            }
                        )
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
                            DeleteBusyIconButton(busy = ListBusyRules.rowDeleteBusy(BusyKey.WATCH in busy, deletingId, BusyKey.WATCH, w.name), enabled = BusyKey.WATCH !in busy,
                                onClick = { deletingId = ListBusyRules.tag(BusyKey.WATCH, w.name); viewModel.removeWatchChannel(w.name) },
                                icon = Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = bColors.signalRed)
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
                    // v1.23: lokaler Slider-Zustand, Senden erst beim Loslassen bzw. nach -/+ (FineSlider)
                    FineSlider(
                        label = stringResource(R.string.filter_age_label),
                        persisted = stats.ageMinutes,
                        min = 0, max = 120, step = 5, fineMax = 10080,   // Firmware: 1–10080 Min. oder off
                        busy = BusyKey.FILTER_AGE in busy,
                        color = bColors.cobalt,
                        onCommit = { viewModel.setFilterAge(it) },
                        valueText = { if (it == 0) tr("AUS") else it.toString() }   // Einheit steht im Titel
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    FineSlider(
                        label = stringResource(R.string.filter_hops_label),
                        persisted = stats.maxHops,
                        min = 1, max = 15, step = 1, fineMax = 64,   // Firmware: 0–64
                        busy = BusyKey.FILTER_HOPS in busy,
                        color = bColors.cobalt,
                        onCommit = { viewModel.setFilterMaxHops(it) },
                        valueText = { it.toString() }
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
            // v1.22: nur Werte, die die Firmware wirklich ausgibt ("filter" + "filter stats air"); "—" = noch nicht gelesen
            val counters = stats.counters
            fun count(v: Long?) = v?.toString() ?: "—"
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(stringResource(R.string.filter_stats_dropped), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(count(counters?.total), fontWeight = FontWeight.Bold, color = bColors.signalRed, fontSize = 18.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.filter_stats_airtime), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(FilterCounters.formatAirtime(stats.savedAirtimeMs), fontWeight = FontWeight.Bold, color = bColors.cobalt, fontSize = 18.sp)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    // Feldnamen exakt wie in der Firmware-Zeile "Blocked [ Hops | Rate | Channel | Hash | Malformed ]" (nicht übersetzt)
                    listOf(
                        "Hops" to counters?.hops, "Rate" to counters?.rate, "Channel" to counters?.channel,
                        "Hash" to counters?.hash, "Malformed" to counters?.malformed
                    ).forEach { (label, v) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            Text(count(v), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                if (isWlView) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        tr("Whitelist-Ablehnungen zählt die Firmware in keiner Kategorie – sie zeigen sich nur in der gesparten Airtime."),
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
