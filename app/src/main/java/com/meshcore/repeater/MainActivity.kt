package com.meshcore.repeater

import com.meshcore.repeater.ui.theme.BauhausCornerShape

import com.meshcore.repeater.i18n.tr
import com.meshcore.repeater.i18n.findActivity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.meshcore.repeater.model.ConnectionState
import com.meshcore.repeater.ui.components.*
import com.meshcore.repeater.ui.navigation.NavDrawerItem
import com.meshcore.repeater.ui.screens.*
import com.meshcore.repeater.ui.theme.RepeaterConfiguratorTheme
import com.meshcore.repeater.viewmodel.RepeaterViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Prozessweit: Splashscreen wurde bereits gezeigt (überlebt neue Activity-Instanzen, z. B. durch USB_DEVICE_ATTACHED). */
private var splashShownInProcess = false

/** Effektive 2-Buchstaben-Sprache ("de|en|nl|fr"); SYSTEM = Systemsprache (auch "de-DE" -> "de"). */
private fun langCodeFor(lang: com.meshcore.repeater.model.AppLanguage): String =
    com.meshcore.repeater.i18n.I18n.effectiveLanguage(if (lang == com.meshcore.repeater.model.AppLanguage.SYSTEM) "" else lang.code)

/** Navigationsverlauf übersteht Display-Drehung / Activity-Neuaufbau (gespeichert als Enum-Namen). */
private val NavHistorySaver = androidx.compose.runtime.saveable.listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<NavDrawerItem>, String>(
    save = { list -> list.map { it.name } },
    restore = { names ->
        mutableStateListOf<NavDrawerItem>().apply {
            addAll(names.mapNotNull { n -> NavDrawerItem.values().firstOrNull { it.name == n } })
            if (isEmpty() || first() != NavDrawerItem.CONNECTION) add(0, NavDrawerItem.CONNECTION)
        }
    }
)
private const val NAV_HISTORY_MAX = 8

class MainActivity : ComponentActivity() {
    private val viewModel: RepeaterViewModel by viewModels()
    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); try { viewModel.refreshUsbDevices() } catch (_: Exception) {} }
    override fun onResume() { super.onResume(); try { viewModel.refreshUsbDevices() } catch (_: Exception) {} }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Sprache VOR der ersten Komposition setzen (außerhalb von Compose) – kein Flackern im ersten Frame
        com.meshcore.repeater.i18n.I18n.init(applicationContext)
        com.meshcore.repeater.i18n.I18n.language = langCodeFor(viewModel.appLanguage.value)
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val appLanguage by viewModel.appLanguage.collectAsState()
            // Sprachwahl aus dem Seitenmenü sofort anwenden: tr()-Texte (I18n) + strings.xml (lokalisierter Context)
            val baseContext = androidx.compose.ui.platform.LocalContext.current
            val baseConfig = androidx.compose.ui.platform.LocalConfiguration.current
            val langCode = langCodeFor(appLanguage)
            // Kein Compose-State-Schreiben während der Komposition: Sprachwechsel als Effekt anwenden
            LaunchedEffect(langCode) { com.meshcore.repeater.i18n.I18n.language = langCode }
            val localizedConfig = remember(langCode, baseConfig) {
                android.content.res.Configuration(baseConfig).apply { setLocale(java.util.Locale(langCode)) }
            }
            val localizedContext = remember(localizedConfig) {
                com.meshcore.repeater.i18n.LocalizedContextWrapper(baseContext, baseContext.createConfigurationContext(localizedConfig).resources)
            }
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalContext provides localizedContext,
                androidx.compose.ui.platform.LocalConfiguration provides localizedConfig
            ) {
            RepeaterConfiguratorTheme(themeMode = themeMode) {
                // 2,5-s-Splashscreen beim Start, danach sanfter Übergang auf Hauptbildschirm bzw. Disclaimer
                // Nur beim echten App-Start (einmal pro Prozess) – nicht bei USB-Intents oder Activity-Neuaufbau
                var showSplash by remember { mutableStateOf(!splashShownInProcess) }
                LaunchedEffect(Unit) { if (showSplash) { kotlinx.coroutines.delay(2500L); splashShownInProcess = true; showSplash = false } }
                androidx.compose.animation.Crossfade(targetState = showSplash, animationSpec = androidx.compose.animation.core.tween(700), label = "splash") { splash ->
                    if (splash) com.meshcore.repeater.ui.components.MeshchoseiSplash() else MainAppContent(viewModel = viewModel)
                }
            }
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: RepeaterViewModel) {
    val drawerState = rememberBauhausDrawerState()   // interaktiver Bauhaus-Drawer (v1.22)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    // rememberSaveable: Display-Drehung wirft nicht auf den Startscreen zurück und bricht keine Setup-Tour ab
    val navHistory = androidx.compose.runtime.saveable.rememberSaveable(saver = NavHistorySaver) { mutableStateListOf(NavDrawerItem.CONNECTION) }
    val currentScreen = navHistory.last()
    var showExitDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var setupTourStep by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Int?>(null) }
    val tourSave = remember { TourSaveController() }

    val connectionState by viewModel.connectionState.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val disclaimerAccepted by viewModel.disclaimerAccepted.collectAsState()
    val capabilities by viewModel.deviceCapabilities.collectAsState()
    val remoteAdminSession by viewModel.remoteAdminSession.collectAsState()
    val isConnected = connectionState is ConnectionState.Connected
    val initialReadDone by viewModel.hasCompletedInitialRead.collectAsState()
    val setupReading by remember { viewModel.basicSetup.map { it.isReading }.distinctUntilChanged() }.collectAsState(initial = false)
    // Menü & "Geführtes Setup starten" erst nach vollständig abgeschlossenem Start-Einlesen freigeben
    // Exklusive Filter-Menüs: nur der aktive Modus (bzw. bei ausgeschaltetem Filter alle unterstützten)
    val activeFilterMode by remember { viewModel.filterStats.map { it.activeMode }.distinctUntilChanged() }.collectAsState(initial = null)
    val blacklistMenuEnabled = com.meshcore.repeater.model.FilterMenuRules.blacklistEnabled(capabilities.supportsBlacklist, activeFilterMode)
    val whitelistMenuEnabled = com.meshcore.repeater.model.FilterMenuRules.whitelistEnabled(capabilities.supportsWhitelist, activeFilterMode)
    val msgNotConnected = androidx.compose.ui.res.stringResource(com.meshcore.repeater.R.string.menu_blocked_not_connected)
    val msgUnsupported = androidx.compose.ui.res.stringResource(com.meshcore.repeater.R.string.menu_blocked_unsupported)
    val msgFilterActive = androidx.compose.ui.res.stringResource(com.meshcore.repeater.R.string.menu_blocked_filter_active)
    // Menü-Sperre NUR während des initialen Start-Scans (isReading setzt ausschließlich startInitialScan) – nie wegen manueller CLI-Befehle
    val navigationAllowed = com.meshcore.repeater.model.NavigationRules.navigationAllowed(
        connected = isConnected, detectionComplete = capabilities.detectionComplete, isCompanion = capabilities.isCompanion,
        remoteLoggedIn = remoteAdminSession.isLoggedIn, initialReadDone = initialReadDone, startScanReading = setupReading
    )

    // Mesh-Schonmodus: per Remote-Admin (LoRa) ODER bei direkt per USB verbundenem Repeater; gesperrt nur bei Companion ohne Remote-Login
    val meshCareAvailable = !capabilities.isCompanion || remoteAdminSession.targetRepeater != null
    // Zurück zum Verbindungs-Screen: Tour beenden und Navigationsverlauf zurücksetzen
    fun resetToConnection() { setupTourStep = null; if (navHistory.last() != NavDrawerItem.CONNECTION || navHistory.size > 1) { navHistory.clear(); navHistory.add(NavDrawerItem.CONNECTION) } }
    LaunchedEffect(isConnected) { if (!isConnected) resetToConnection() }
    // Aktive Verbindungsüberwachung (USB-Detach, BLE-Abriss, Remote-Repeater stumm): Meldung kommt als Snackbar über uiEvents
    LaunchedEffect(Unit) { viewModel.linkLost.collect { resetToConnection(); scope.launch { drawerState.close() } } }
    LaunchedEffect(Unit) { viewModel.uiEvents.collectLatest { snackbarHostState.showSnackbar(it) } }
    var showDisclaimerForced by remember { mutableStateOf(!disclaimerAccepted) }
    LaunchedEffect(disclaimerAccepted) { showDisclaimerForced = !disclaimerAccepted }
    var showDisclaimerInfo by remember { mutableStateOf(false) }
    val act = androidx.compose.ui.platform.LocalContext.current.findActivity()
    if (showDisclaimerForced) { DisclaimerDialog(isInitialAcceptance = true, onAccept = { viewModel.acceptDisclaimer(); showDisclaimerForced = false }, onDismiss = { act?.finishAffinity() }) }
    if (showDisclaimerInfo) { DisclaimerDialog(isInitialAcceptance = false, onDismiss = { showDisclaimerInfo = false }) }
    // Update-Hinweis (v1.19): zentrierter Dialog, erst wenn kein Haftungsausschluss mehr offen ist
    val availableUpdate by viewModel.availableUpdate.collectAsState()
    availableUpdate?.let { upd ->
        if (!showDisclaimerForced && !showDisclaimerInfo) UpdateDialog(upd, debugTest = viewModel.updateCheckIsDebugTest, onDismiss = { viewModel.dismissUpdateHint() })
    }

    BackHandler {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            // Ein einziger Druck auf Zurück führt von jeder Unterseite (auch aus der Setup-Tour) direkt zum Hauptscreen
            currentScreen != NavDrawerItem.CONNECTION -> resetToConnection()
            else -> showExitDialog = true
        }
    }
    if (showExitDialog) { AlertDialog(onDismissRequest = { showExitDialog = false }, shape = BauhausCornerShape, title = { Text(tr("App beenden?")) }, text = { Text(tr("Möchtest du den meshchōsei wirklich schließen?")) }, confirmButton = { TextButton(shape = BauhausCornerShape, onClick = { showExitDialog = false; try { viewModel.disconnect() } catch (_: Exception) {}; act?.finishAffinity(); return@TextButton }) { Text(tr("Beenden")) } }, dismissButton = { TextButton(shape = BauhausCornerShape, onClick = { showExitDialog = false }) { Text(tr("Abbrechen")) } }) }

    // Seitenmenü öffnen oder Screen wechseln: wartende/laufende manuelle CLI-Befehle sauber abbrechen,
    // damit sie den Mutex nicht bis zum (LoRa-)Timeout blockieren und der neue Screen sofort laden kann.
    LaunchedEffect(drawerState.targetValue) {
        if (drawerState.targetValue == DrawerValue.Open) {
            viewModel.cancelManualCli()
            viewModel.ensureFilterStatusKnown()   // Filterstatus unbekannt (z. B. LoRa-Antwort verloren): einmal nachfragen
        }
    }
    LaunchedEffect(currentScreen) { if (currentScreen != NavDrawerItem.CLI_CONSOLE) viewModel.cancelManualCli() }

    // Scroll-Hinweis: bei offenem (oder sich öffnendem) Seitenmenü nur im Menü, nicht im Hauptfenster dahinter
    val drawerOpen = drawerState.isOpen || drawerState.targetValue == DrawerValue.Open
    // Interaktiver Bauhaus-Drawer (v1.22): Aufziehen von überall 1:1 am Finger, Einrasten ab 35 % oder per Wisch
    BauhausModalDrawer(state = drawerState, drawerContent = {
      CompositionLocalProvider(com.meshcore.repeater.ui.components.LocalSuppressScrollHint provides false) {
        AppDrawerHost(
            currentRoute = currentScreen, connectionState = connectionState, meshCareFlow = viewModel.meshCareState, filterStatsFlow = viewModel.filterStats,
            firmwareVersion = capabilities.firmwareVersion, themeMode = themeMode, appLanguage = appLanguage, effectiveLang = langCodeFor(appLanguage), navigationAllowed = navigationAllowed,
            meshCareAvailable = meshCareAvailable,
            supportsBlacklist = blacklistMenuEnabled, supportsWhitelist = whitelistMenuEnabled,
            // Geführtes Setup: KEINE erneute Abfrage – die Werte stammen aus dem einmaligen Start-Scan
            onStartGuidedSetup = { val s = if (remoteAdminSession.targetRepeater != null) 1 else 2; setupTourStep = s; viewModel.ensureInitialRead(); navHistory[navHistory.lastIndex] = (if (s == 1) NavDrawerItem.MESH_CARE else NavDrawerItem.HARDWARE_ANTENNA); scope.launch { drawerState.close() } },
            onItemClick = { item ->
                setupTourStep = null
                // Differenzierter Hinweis, warum ein Menüpunkt gesperrt ist
                val blockedMsg: String? = when {
                    item == NavDrawerItem.CONNECTION || item == NavDrawerItem.ABOUT -> null
                    !navigationAllowed -> msgNotConnected
                    item == NavDrawerItem.BLACKLIST && !capabilities.supportsBlacklist -> msgUnsupported
                    item == NavDrawerItem.WHITELIST && !capabilities.supportsWhitelist -> msgUnsupported
                    item == NavDrawerItem.MESH_CARE && !meshCareAvailable -> msgUnsupported
                    item == NavDrawerItem.BLACKLIST && !blacklistMenuEnabled -> msgFilterActive   // Whitelist läuft gerade
                    item == NavDrawerItem.WHITELIST && !whitelistMenuEnabled -> msgFilterActive   // Blacklist läuft gerade
                    else -> null
                }
                if (blockedMsg == null) {
                    // Verlauf begrenzen: jeder Screen höchstens einmal, maximal NAV_HISTORY_MAX Einträge
                    if (navHistory.last() != item) { if (item != NavDrawerItem.CONNECTION) navHistory.remove(item); navHistory.add(item); while (navHistory.size > NAV_HISTORY_MAX) navHistory.removeAt(1) }
                    scope.launch { drawerState.close() }
                }
                else scope.launch { drawerState.close(); snackbarHostState.showSnackbar(blockedMsg) }
            },
            onThemeChange = { viewModel.setThemeMode(it) }, onLanguageChange = { viewModel.setAppLanguage(it) },
            onShowDisclaimer = { showDisclaimerInfo = true; scope.launch { drawerState.close() } }
        )
      }
    }) {
      CompositionLocalProvider(com.meshcore.repeater.ui.components.LocalSuppressScrollHint provides drawerOpen) {
        Scaffold(topBar = { TopBarHost(title = stringResource(currentScreen.titleRes), connectionState = connectionState, viewModel = viewModel, capabilities = capabilities, onMenuClick = { scope.launch { if (drawerState.isClosed) drawerState.open() else drawerState.close() } }) }, snackbarHost = { SnackbarHost(snackbarHostState) }, containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
            Column(Modifier.fillMaxSize().padding(paddingValues).background(MaterialTheme.colorScheme.background)) {
                Box(Modifier.weight(1f)) {
                    CompositionLocalProvider(LocalTourMode provides (setupTourStep != null), LocalTourSave provides tourSave) {
                    when (currentScreen) {
                        NavDrawerItem.CONNECTION -> ConnectionScreen(viewModel = viewModel)
                        NavDrawerItem.MESH_CARE -> MeshCareScreen(viewModel = viewModel)
                        NavDrawerItem.HARDWARE_ANTENNA -> HardwareAntennaScreen(viewModel = viewModel)
                        NavDrawerItem.RADIO_CONFIG -> RadioAntennaScreen(viewModel = viewModel)
                        NavDrawerItem.IDENTITY_LOCATION -> IdentityLocationScreen(viewModel = viewModel)
                        NavDrawerItem.ROUTING_ADVERTS -> RoutingAdvertsScreen(viewModel = viewModel)
                        NavDrawerItem.BLACKLIST -> FilterScreen(viewModel = viewModel, view = com.meshcore.repeater.model.FilterMode.BLACKLIST)
                        NavDrawerItem.WHITELIST -> FilterScreen(viewModel = viewModel, view = com.meshcore.repeater.model.FilterMode.WHITELIST)
                        NavDrawerItem.CLI_CONSOLE -> CliConsoleScreen(viewModel = viewModel)
                        NavDrawerItem.BACKUP -> BackupScreen(viewModel = viewModel)
                        NavDrawerItem.ABOUT -> AboutScreen(viewModel = viewModel, onShowDisclaimer = { showDisclaimerInfo = true })
                    }
                    }
                    // Zentriertes Status-Overlay: einzige Statusanzeige für Verbinden, Login, Erkennung & Auslesen
                    val readStatus by viewModel.activeReadStatusText.collectAsState()
                    val readProgress by viewModel.readProgress.collectAsState()
                    val overlayText = readStatus
                        ?: (connectionState as? ConnectionState.Connecting)?.message?.let { if (it.endsWith("...")) it.dropLast(3) + "…" else it }
                        ?: if (remoteAdminSession.isLoggingIn) tr("Anmeldung am Repeater \"{0}\"…", remoteAdminSession.targetRepeater?.name ?: "") else null
                    overlayText?.let { tr(it) }?.let { text ->
                        ReadStatusOverlay(
                            text = text,
                            progress = if (readStatus != null) readProgress else null,
                            onCancel = when {
                                readStatus != null -> ({ viewModel.cancelActiveRead() })
                                connectionState is ConnectionState.Connecting -> ({ viewModel.disconnect() })
                                else -> null
                            }
                        )
                    }
                }
                setupTourStep?.let { step ->
                    val tourScreens = listOf(NavDrawerItem.MESH_CARE, NavDrawerItem.HARDWARE_ANTENNA, NavDrawerItem.RADIO_CONFIG, NavDrawerItem.IDENTITY_LOCATION, NavDrawerItem.ROUTING_ADVERTS)
                    Surface(tonalElevation = 3.dp) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            if (step > 1) {
                                TextButton(shape = BauhausCornerShape, modifier = Modifier.width(96.dp), onClick = { val prev = (step - 1).coerceAtLeast(1); setupTourStep = prev; navHistory[navHistory.lastIndex] = tourScreens[prev - 1] }) { Text(tr("← Zurück")) }
                            } else {
                                Spacer(Modifier.width(96.dp))
                            }
                            Text(tr("Setup {0} / 5", step), fontWeight = FontWeight.Bold)
                            val saveAction = tourSave.action
                            Button(shape = BauhausCornerShape, onClick = {
                                saveAction?.invoke()
                                if (step < 5) { val next = step + 1; setupTourStep = next; navHistory[navHistory.lastIndex] = tourScreens[next - 1] } else { setupTourStep = null; navHistory[navHistory.lastIndex] = NavDrawerItem.CONNECTION }
                            }) {
                                Text(
                                    when {
                                        saveAction != null && step < 5 -> tr("Speichern & Weiter →")
                                        saveAction != null -> tr("Speichern & Fertig")
                                        step < 5 -> tr("Weiter →")
                                        else -> tr("Fertig")
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
      }
    }
}

private val BrailleSpinnerFrames = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")

/**
 * Mittige Overlay-Karte mit rotierendem Braille-Punkte-Spinner und Klartext der aktuellen Abfrage.
 * Feste Breite + reservierte Plätze für Fortschritt & Abbrechen: alle Runden (Verbinden, Login,
 * Erkennung, Einlesen) erscheinen in exakt derselben Fenstergröße.
 */
@Composable
private fun BoxScope.ReadStatusOverlay(text: String, progress: Pair<Int, Int>?, onCancel: (() -> Unit)?) {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(80L); frame = (frame + 1) % BrailleSpinnerFrames.size } }
    Card(
        modifier = Modifier.align(Alignment.Center).width(300.dp).heightIn(min = 96.dp),
        shape = BauhausCornerShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(BrailleSpinnerFrames[frame], fontSize = 34.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
            // Zwei Zeilen fest reserviert, damit lange und kurze Texte gleich hoch sind
            Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface,
                minLines = 2, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
            val (cur, total) = progress ?: (0 to 1)
            val showProgress = progress != null
            Text(if (showProgress) tr("Abfrage {0} von {1}", cur, total) else " ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(
                progress = { if (showProgress) cur.toFloat() / total.coerceAtLeast(1) else 0f },
                modifier = Modifier.fillMaxWidth().alpha(if (showProgress) 1f else 0f)
            )
            TextButton(shape = BauhausCornerShape, onClick = { onCancel?.invoke() }, enabled = onCancel != null, modifier = Modifier.alpha(if (onCancel != null) 1f else 0f)) { Text(tr("Abbrechen")) }
        }
    }
}

/** Top-Bar mit eigenem Mesh-Care-Countdown-Collector (der Sekundentakt rekomponiert nur die Leiste). */
@Composable
private fun TopBarHost(title: String, connectionState: ConnectionState, viewModel: RepeaterViewModel, capabilities: com.meshcore.repeater.model.DeviceCapabilities, onMenuClick: () -> Unit) {
    val meshCareState by viewModel.meshCareState.collectAsState()
    RepeaterTopBar(title = title, connectionState = connectionState, meshCareState = meshCareState, capabilities = capabilities, onMenuClick = onMenuClick)
}
