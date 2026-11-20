package com.meshcore.repeater

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.meshcore.repeater.model.ConnectionState
import com.meshcore.repeater.ui.components.AppDrawerSheet
import com.meshcore.repeater.ui.components.DisclaimerDialog
import com.meshcore.repeater.ui.components.RepeaterTopBar
import com.meshcore.repeater.ui.navigation.NavDrawerItem
import com.meshcore.repeater.ui.screens.AboutScreen
import com.meshcore.repeater.ui.screens.CliConsoleScreen
import com.meshcore.repeater.ui.screens.ConnectionScreen
import com.meshcore.repeater.ui.screens.FilterScreen
import com.meshcore.repeater.ui.screens.HardwareStatusScreen
import com.meshcore.repeater.ui.screens.MeshCareScreen
import com.meshcore.repeater.ui.screens.RadioAntennaScreen
import com.meshcore.repeater.ui.theme.BauhausDarkCarbon
import com.meshcore.repeater.ui.theme.LocalBauhausColors
import com.meshcore.repeater.ui.theme.RepeaterConfiguratorTheme
import com.meshcore.repeater.viewmodel.RepeaterViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: RepeaterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            RepeaterConfiguratorTheme(themeMode = themeMode) {
                MainAppContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: RepeaterViewModel) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Aktueller Screen – immer mit CONNECTION starten
    val navHistory = remember { androidx.compose.runtime.mutableStateListOf(NavDrawerItem.CONNECTION) }
    val currentScreen = navHistory.last()
    var showExitDialog by remember { mutableStateOf(false) }

    val connectionState by viewModel.connectionState.collectAsState()
    val meshCareState by viewModel.meshCareState.collectAsState()
    val filterStats by viewModel.filterStats.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val disclaimerAccepted by viewModel.disclaimerAccepted.collectAsState()
    val offlinePreview by viewModel.offlinePreviewEnabled.collectAsState()
    val capabilities by viewModel.deviceCapabilities.collectAsState()

    // Verbindungs-Gate: Navigation nur bei verbunden oder Offline-Vorschau
    val isConnected = connectionState is ConnectionState.Connected
    val navigationAllowed = isConnected || offlinePreview

    // Wenn Verbindung getrennt wird und man sich nicht auf CONNECTION befindet →
    // zurück zu CONNECTION navigieren (außer bei Offline-Vorschau)
    LaunchedEffect(isConnected, offlinePreview) {
        if (!isConnected && !offlinePreview && currentScreen != NavDrawerItem.CONNECTION) {
            if (navHistory.last() != NavDrawerItem.CONNECTION) navHistory.add(NavDrawerItem.CONNECTION)
            snackbarHostState.showSnackbar("Verbindung getrennt – zurück zum Verbindungs-Screen.")
        }
    }

    // Snackbar-Benachrichtigungen vom ViewModel
    LaunchedEffect(Unit) {
        viewModel.uiEvents.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Modaler Erststart-Disclaimer
    var showDisclaimerForced by remember { mutableStateOf(false) }
    LaunchedEffect(disclaimerAccepted) {
        showDisclaimerForced = !disclaimerAccepted
    }

    // Info-Button-triggered Disclaimer (kann jederzeit neu angezeigt werden)
    var showDisclaimerInfo by remember { mutableStateOf(false) }

    // === Disclaimer Dialogs ===
    if (showDisclaimerForced) {
        DisclaimerDialog(
            isForced = true,
            onAccept = {
                viewModel.acceptDisclaimer()
                showDisclaimerForced = false
            }
        )
    }

    if (showDisclaimerInfo) {
        DisclaimerDialog(
            isForced = false,
            onAccept = {
                showDisclaimerInfo = false
            },
            onDismiss = {
                showDisclaimerInfo = false
            }
        )
    }

    BackHandler {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            navHistory.size > 1 -> navHistory.removeAt(navHistory.lastIndex)
            else -> showExitDialog = true
        }
    }
    val act = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    if (showExitDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { androidx.compose.material3.Text("App beenden?") },
            text = { androidx.compose.material3.Text("Möchtest du den Repeater Konfigurator wirklich schließen?") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { act?.finish() }) { androidx.compose.material3.Text("Beenden") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { showExitDialog = false }) { androidx.compose.material3.Text("Abbrechen") } }
        )
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerSheet(
                currentRoute = currentScreen,
                connectionState = connectionState,
                meshCareState = meshCareState,
                filterStats = filterStats,
                firmwareVersion = capabilities.firmwareVersion,
                themeMode = themeMode,
                appLanguage = appLanguage,
                offlinePreviewEnabled = offlinePreview,
                navigationAllowed = navigationAllowed,
                onItemClick = { item ->
                    if ((item == NavDrawerItem.CONNECTION || item == NavDrawerItem.ABOUT) || navigationAllowed) {
                        if (navHistory.last() != item) navHistory.add(item)
                        scope.launch { drawerState.close() }
                    } else {
                        scope.launch {
                            drawerState.close()
                            snackbarHostState.showSnackbar("Zuerst verbinden oder Offline-Vorschau aktivieren.")
                        }
                    }
                },
                onThemeChange = { viewModel.setThemeMode(it) },
                onLanguageChange = { viewModel.setAppLanguage(it) },
                onOfflinePreviewChange = { viewModel.setOfflinePreview(it) },
                onShowDisclaimer = { showDisclaimerInfo = true }
            )
        }
    ) {
        Scaffold(
            topBar = {
                RepeaterTopBar(
                    title = stringResource(currentScreen.titleRes),
                    connectionState = connectionState,
                    meshCareState = meshCareState,
                    capabilities = capabilities,
                    onMenuClick = {
                        scope.launch {
                            if (drawerState.isClosed) drawerState.open() else drawerState.close()
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
            ) {
                when (currentScreen) {
                    NavDrawerItem.CONNECTION -> ConnectionScreen(viewModel = viewModel)
                    NavDrawerItem.RADIO_ANTENNA -> RadioAntennaScreen(viewModel = viewModel)
                    NavDrawerItem.FILTER_WHITELIST -> FilterScreen(viewModel = viewModel)
                    NavDrawerItem.MESH_CARE -> MeshCareScreen(viewModel = viewModel)
                    NavDrawerItem.HARDWARE_STATUS -> HardwareStatusScreen(viewModel = viewModel)
                    NavDrawerItem.CLI_CONSOLE -> CliConsoleScreen(viewModel = viewModel)
                    NavDrawerItem.ABOUT -> AboutScreen(viewModel = viewModel, onShowDisclaimer = { showDisclaimerInfo = true })
                }
            }
        }
    }
}

/** Helper to pick container color consistently from Bauhaus theme */
@Composable
private fun MaterialThemeColorHelper(isDark: Boolean): androidx.compose.ui.graphics.Color =
    androidx.compose.material3.MaterialTheme.colorScheme.background