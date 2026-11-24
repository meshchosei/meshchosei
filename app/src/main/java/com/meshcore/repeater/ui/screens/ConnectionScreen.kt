package com.meshcore.repeater.ui.screens
import com.meshcore.repeater.R
import androidx.compose.ui.res.stringResource
import com.meshcore.repeater.i18n.tr
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshcore.repeater.model.ConnectionState
import com.meshcore.repeater.model.ConnectionType
import com.meshcore.repeater.model.MeshContact
import com.meshcore.repeater.ui.theme.AlertRed
import com.meshcore.repeater.ui.theme.BauhausCornerShape
import com.meshcore.repeater.ui.theme.LocalBauhausColors
import com.meshcore.repeater.viewmodel.RepeaterViewModel

@Composable
private fun AsciiSpinner(label: String, color: Color = Color(0xFF2196F3)) {
    val frames = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")
    var idx by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(100L)
            idx = (idx + 1) % frames.size
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = frames[idx], fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = color)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
fun ConnectionScreen(viewModel: RepeaterViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val selectedType by viewModel.selectedConnectionType.collectAsState()
    val usbDevices by viewModel.usbDevices.collectAsState()
    val usbDebugInfo by viewModel.usbDebugInfo.collectAsState()
    val bleDevices by viewModel.bleDevices.collectAsState()
    val isScanningBle by viewModel.isScanningBle.collectAsState()
    val caps by viewModel.deviceCapabilities.collectAsState()
    val meshContacts by viewModel.meshContacts.collectAsState()
    val isRefreshingContacts by viewModel.isRefreshingContacts.collectAsState()
    val remoteAdmin by viewModel.remoteAdminSession.collectAsState()
    val bColors = LocalBauhausColors.current
    val blePerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    val permContext = androidx.compose.ui.platform.LocalContext.current
    fun hasBlePerms() = blePerms.all { permContext.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED }
    // Nach dem Erteilen der Berechtigungen startet der Scan sofort – ohne zweiten Klick
    val blePermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.isNotEmpty() && granted.values.all { it }) viewModel.startBleScan() else viewModel.notifyBlePermissionDenied()
    }
    /** Scan starten – fehlende Bluetooth-Berechtigungen vorher anfordern. */
    fun scanWithPermissions() { if (hasBlePerms()) viewModel.startBleScan() else blePermLauncher.launch(blePerms) }
    // BLE-Tab bereits aktiv (z. B. nach App-Neustart): Berechtigungen sofort prüfen bzw. anfordern
    LaunchedEffect(selectedType) {
        if (selectedType == ConnectionType.BLE_COMPANION && connectionState !is ConnectionState.Connected && !hasBlePerms()) blePermLauncher.launch(blePerms)
    }

    var manualCommand by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var filterOnlyRepeaters by remember { mutableStateOf(true) }
    var filterOnlyKnownPaths by remember { mutableStateOf(false) }
    var contactSearchQuery by remember { mutableStateOf("") }

    // Oberer Bereich (Header, Tabs, Geräteliste/Login) füllt genau den sichtbaren Bildschirm mit fester Höhe;
    // die Status-Karte "Alle Parameter eingelesen" liegt als letztes Element DARUNTER im scrollbaren Bereich
    // und kann so nie das Login-Feld überlagern oder dessen Platz stehlen.
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    // Größte gemessene Höhe je Ausrichtung merken: Beim Einblenden der Tastatur (adjustResize) schrumpft
    // maxHeight – der obere Bereich behält trotzdem seine volle Höhe, die Seite scrollt stattdessen hoch
    // (das fokussierte Passwortfeld wird automatisch in den sichtbaren Bereich gescrollt).
    val orientation = androidx.compose.ui.platform.LocalConfiguration.current.orientation
    val stableHeight = remember(orientation) { StableHeight() }
    if (maxHeight > stableHeight.value) stableHeight.value = maxHeight
    val viewportHeight = stableHeight.value
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp)
    ) {
      Column(modifier = Modifier.fillMaxWidth().height(maxOf(viewportHeight - 32.dp, 420.dp))) {
        // Active Connection Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = BauhausCornerShape
        ) {
            val connected = connectionState is ConnectionState.Connected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = if (connected) 8.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = if (selectedType == ConnectionType.USB_SERIAL) Icons.Default.Usb else Icons.Default.Bluetooth
                Box(
                    modifier = Modifier
                        .size(if (connected) 36.dp else 46.dp)
                        .clip(CircleShape)
                        .background(
                            if (connectionState is ConnectionState.Connected) Color(0xFF4CAF50).copy(alpha = 0.2f)
                            else Color.Gray.copy(alpha = 0.2f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (connectionState is ConnectionState.Connected) Color(0xFF4CAF50) else Color.Gray,
                        modifier = Modifier.size(if (connected) 20.dp else 26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (val state = connectionState) {
                            is ConnectionState.Connected -> state.deviceName
                            is ConnectionState.Connecting -> tr("Verbinde...")
                            is ConnectionState.Error -> tr("Fehler: {0}", state.message)
                            is ConnectionState.Disconnected -> tr("Keine aktive Verbindung")
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // Fortschritt (Verbinden, Erkennung, Auslesen) zeigt ausschließlich das zentrale Overlay
                    val linkState = connectionState
                    if (linkState is ConnectionState.Connected && remoteAdmin.targetRepeater == null) {
                        // Verbunden ohne Ziel-Repeater: nur ein kurzes, einzeiliges Badge statt langer Beschreibung
                        Text(
                            text = when {
                                linkState.connectionType == ConnectionType.BLE_COMPANION -> tr("BLE · Mesh OTA")
                                caps.isDirectRepeater || (!caps.isCompanion && caps.firmwareVersion.isNotBlank()) -> tr("USB · Repeater")
                                caps.isCompanion -> tr("USB · Companion")
                                else -> tr("USB · Seriell")
                            },
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                            color = Color(0xFF4CAF50),
                            modifier = Modifier.padding(top = 2.dp).clip(BauhausCornerShape).background(Color(0xFF4CAF50).copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    } else Text(
                        text = when (val state = connectionState) {
                            is ConnectionState.Connected -> {
                                "${remoteAdmin.targetRepeater?.name} • ${if (remoteAdmin.isLoggedIn) tr("Eingeloggt") else tr(remoteAdmin.sessionStatus)}"
                            }
                            is ConnectionState.Connecting -> tr(state.message)
                            else -> tr("Wähle USB-OTG oder BLE-Companion (Mesh OTA)")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (remoteAdmin.isLoggedIn) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (connectionState is ConnectionState.Connected) {
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(shape = BauhausCornerShape, onClick = { viewModel.disconnect() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed)
                    ) {
                        Text(tr("Trennen"))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Connection Type Tabs
        TabRow(
            selectedTabIndex = if (selectedType == ConnectionType.USB_SERIAL) 0 else 1,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = bColors.signalRed,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[if (selectedType == ConnectionType.USB_SERIAL) 0 else 1]),
                    color = bColors.signalRed
                )
            }
        ) {
            val isBusy = connectionState is ConnectionState.Connected || connectionState is ConnectionState.Connecting
            val usbEnabled = !isBusy || selectedType == ConnectionType.USB_SERIAL
            val bleEnabled = !isBusy || selectedType == ConnectionType.BLE_COMPANION
            Tab(
                selected = selectedType == ConnectionType.USB_SERIAL,
                enabled = usbEnabled,
                onClick = { if (usbEnabled) viewModel.selectConnectionType(ConnectionType.USB_SERIAL) },
                modifier = Modifier.alpha(if (usbEnabled) 1f else 0.35f),
                text = { Text(tr("USB-Serial (OTG)"), fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.Default.Usb, contentDescription = null) }
            )
            Tab(
                selected = selectedType == ConnectionType.BLE_COMPANION,
                enabled = bleEnabled,
                onClick = { if (bleEnabled) { viewModel.selectConnectionType(ConnectionType.BLE_COMPANION, startScan = false); scanWithPermissions() } },
                modifier = Modifier.alpha(if (bleEnabled) 1f else 0.35f),
                text = { Text(tr("BLE Companion (Mesh OTA)"), fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.Default.Bluetooth, contentDescription = null) }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tab Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(BauhausCornerShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(12.dp)
        ) {
            if (selectedType == ConnectionType.USB_SERIAL && !(connectionState is ConnectionState.Connected && caps.isCompanion)) {
                // USB Serial Device List
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr("Erkannte OTG-Geräte ({0})", usbDevices.size),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (usbDebugInfo.isNotEmpty()) {
                                Text(
                                    text = tr(usbDebugInfo),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = bColors.ochre,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Manuelles Umschalten auf Companion nur, solange KEIN direkter Repeater erkannt wurde
                            if (connectionState is ConnectionState.Connected && selectedType == ConnectionType.USB_SERIAL && !caps.isDirectRepeater && !caps.isCompanion) {
                                Text(
                                    text = "Companion →",
                                    color = bColors.signalRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { viewModel.enableCompanionMode() }
                                        .padding(vertical = 4.dp, horizontal = 4.dp)
                                )
                            }
                            Text(
                                text = "Sync",
                                color = bColors.cobalt,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { viewModel.refreshUsbDevices() }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            )
                        }
                    }

                    if (usbDevices.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tr("Bitte OTG-Kabel anschließen"),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(usbDevices) { dev ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = dev.displayName,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "VID: ${dev.vendorId} • PID: ${dev.productId} • Port: ${dev.portNumber}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Button(shape = BauhausCornerShape, onClick = { viewModel.connectUsb(dev.deviceId, 115200) },
                                            colors = ButtonDefaults.buttonColors(containerColor = bColors.signalRed),
                                            enabled = connectionState !is ConnectionState.Connected
                                        ) {
                                            Text(tr("Verbinden"), color = Color.Black)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // BLE Companion / Mesh OTA Workflow
                if (connectionState !is ConnectionState.Connected) {
                    // Not connected yet: BLE Scanner list
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = tr("Gefundene BLE-Nodes ({0})", bleDevices.size),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (isScanningBle) {
                                Text(
                                    text = "Stop",
                                    color = bColors.signalRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { viewModel.stopBleScan() }
                                        .padding(vertical = 4.dp, horizontal = 4.dp)
                                )
                            } else {
                                Text(
                                    text = "Scan",
                                    color = bColors.cobalt,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { scanWithPermissions() }
                                        .padding(vertical = 4.dp, horizontal = 4.dp)
                                )
                            }
                        }

                        if (bleDevices.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isScanningBle) tr("Suche nach Bluetooth-Nodes...") else tr("Verbinde dich per Bluetooth mit deinem Companion-Node (um empfangene Repeater über das Mesh zu steuern) oder direkt mit einem BLE-fähigen Repeater.\n\nTippe oben auf 'Scan'."),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(bleDevices) { ble ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SignalCellularAlt,
                                                contentDescription = null,
                                                tint = when { ble.rssi >= -72 -> Color(0xFF4CAF50); ble.rssi >= -86 -> bColors.ochre; else -> bColors.signalRed }
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = ble.displayName,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = tr("{0} • RSSI: {1} dBm", ble.address, ble.rssi),
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Button(shape = BauhausCornerShape, onClick = { viewModel.connectBle(ble.address) },
                                                colors = ButtonDefaults.buttonColors(containerColor = bColors.cobalt)
                                            ) {
                                                Text(tr("Koppeln"), color = Color.Black)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (connectionState is ConnectionState.Connected) {
                    // Connected to Companion (BLE oder USB): Entweder Liste ODER Login-Karte (kein Quetschen!)
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (remoteAdmin.targetRepeater == null) {
                        // Section Header: Companion Contact List
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tr("Repeater-Auswahl ({0})", meshContacts.size), style = MaterialTheme.typography.titleMedium, color = bColors.signalRed, fontWeight = FontWeight.Bold)
                                Text(tr("Companion Mesh-Kontakte"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (isRefreshingContacts) {
                                AsciiSpinner("", bColors.cobalt)
                            } else {
                                Text(
                                    text = "Sync",
                                    color = bColors.cobalt,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { viewModel.refreshMeshContacts() }
                                        .padding(vertical = 4.dp, horizontal = 4.dp)
                                )
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(shape = BauhausCornerShape, selected = filterOnlyRepeaters, onClick = { filterOnlyRepeaters = !filterOnlyRepeaters }, label = { Text(tr("Nur Repeater"), fontSize = 11.sp) })
                            FilterChip(shape = BauhausCornerShape, selected = filterOnlyKnownPaths, onClick = { filterOnlyKnownPaths = !filterOnlyKnownPaths }, label = { Text(tr("Nur aktive Pfade"), fontSize = 11.sp) })
                        }
                        OutlinedTextField(
                            value = contactSearchQuery,
                            onValueChange = { contactSearchQuery = it },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            placeholder = { Text(tr("Repeater suchen (Name oder Key)..."), fontSize = 12.sp) },
                            singleLine = true,
                            trailingIcon = {
                                if (contactSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { contactSearchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = tr("Suche löschen"), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        } else {
                            val target = remoteAdmin.targetRepeater!!
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(tr("Repeater-Login"), style = MaterialTheme.typography.titleMedium, color = bColors.signalRed, fontWeight = FontWeight.Bold)
                                Text(
                                    text = tr("← Zurück"),
                                    color = bColors.cobalt,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { viewModel.selectTargetRepeater(null) }
                                        .padding(vertical = 4.dp, horizontal = 4.dp)
                                )
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, if (remoteAdmin.isLoggedIn) Color(0xFF4CAF50) else bColors.cobalt, BauhausCornerShape),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Router,
                                                contentDescription = null,
                                                tint = bColors.signalRed,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = target.name,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Key: ${target.keyPrefix}... • ${when (target.outPathLen) { 0 -> tr("Direkt (0 Hops)"); in 1..63 -> tr("{0} Hops", target.outPathLen); else -> tr("Flood (kein direkter Pfad)") }}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (remoteAdmin.isLoggedIn) bColors.signalRed.copy(alpha = 0.2f) else bColors.ochre.copy(alpha = 0.2f))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = if (remoteAdmin.isLoggedIn) tr("Admin aktiv") else tr("Nicht eingeloggt"),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (remoteAdmin.isLoggedIn) bColors.signalRed else bColors.ochre
                                            )
                                        }
                                    }

                                    // Password Input & Login Action
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = remoteAdmin.savedPassword,
                                            onValueChange = { viewModel.setRemoteAdminPassword(it) },
                                            modifier = Modifier.weight(1f),
                                            label = { Text(stringResource(R.string.conn_admin_password_label), fontSize = 11.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                            singleLine = true,
                                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, autoCorrect = false),
                                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                            trailingIcon = {
                                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                                    Icon(
                                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Button(shape = BauhausCornerShape, onClick = { viewModel.startRemoteLogin() },
                                            colors = ButtonDefaults.buttonColors(containerColor = bColors.cobalt),
                                            enabled = !remoteAdmin.isLoggingIn
                                        ) {
                                            if (remoteAdmin.isLoggingIn) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                                            } else {
                                                Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Login", color = Color.Black, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (remoteAdmin.targetRepeater == null) {
                        // Contact List
                        val filteredContacts = meshContacts.filter { ct ->
                            val typeOk = !filterOnlyRepeaters || ct.isRepeater || ct.isRoomServer
                            val pathOk = !filterOnlyKnownPaths || (ct.outPathLen in 0..63)
                            val q = contactSearchQuery.trim()
                            val searchOk = q.isEmpty() || ct.name.contains(q, ignoreCase = true) || ct.keyPrefix.contains(q, ignoreCase = true)
                            typeOk && pathOk && searchOk
                        }

                        if (filteredContacts.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isRefreshingContacts) tr("Lade...")
                                    else if (filterOnlyRepeaters) tr("Keine Repeater in der Kontaktliste.")
                                    else tr("Keine Kontakte empfangen."),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(filteredContacts) { contact ->
                                    val isSelected = remoteAdmin.targetRepeater?.pubKeyHex == contact.pubKeyHex
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .clickable { viewModel.selectTargetRepeater(contact) },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) bColors.signalRed.copy(alpha=0.14f) else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Router,
                                                contentDescription = null,
                                                tint = if (contact.isRepeater) bColors.signalRed else bColors.cobalt
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = contact.name,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(if (contact.isRepeater) bColors.signalRed.copy(alpha = 0.2f) else bColors.cobalt.copy(alpha = 0.2f))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = contact.typeLabel,
                                                            fontSize = 10.sp,
                                                            color = if (contact.isRepeater) bColors.signalRed else bColors.cobalt
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "Key: ${contact.keyPrefix}... • ${when (contact.outPathLen) { 0 -> tr("Direkt (0 Hops)"); in 1..63 -> tr("{0} Hops", contact.outPathLen); else -> tr("Flood (unbekannt)") }}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = tr("Ausgewählt"),
                                                    tint = bColors.signalRed,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            } else {
                                                OutlinedButton(shape = BauhausCornerShape, onClick = { viewModel.selectTargetRepeater(contact) }
                                                ) {
                                                    Text(tr("Wählen"), fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

      } // Ende oberer Bereich

        // Start-Scan läuft einmalig automatisch; erneut nur auf ausdrücklichen Wunsch
        val initialReadDone by viewModel.hasCompletedInitialRead.collectAsState()
        val readActive by viewModel.activeReadStatusText.collectAsState()
        // (ganz unten platziert, damit das Layout oben beim Einblenden nicht springt)
        val loginDone = !caps.isCompanion || remoteAdmin.isLoggedIn
        val scanFailed = !initialReadDone && caps.detectionComplete && readActive == null   // abgebrochen/fehlgeschlagen -> Neuversuch anbieten
        if (connectionState is ConnectionState.Connected && loginDone && readActive == null && (initialReadDone || scanFailed)) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clip(BauhausCornerShape).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline, BauhausCornerShape).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (initialReadDone) tr("✓ Alle Parameter eingelesen") else tr("Parameter unvollständig eingelesen"),
                    fontSize = 12.sp,
                    color = if (initialReadDone) MaterialTheme.colorScheme.onSurfaceVariant else bColors.ochre
                )
                OutlinedButton(shape = BauhausCornerShape, onClick = { viewModel.readBasicSetupFromNode() }) { Text(tr("Neu einlesen"), fontSize = 12.sp) }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
    }
}

}

/** Merkt sich die größte bisher gemessene Höhe (kein Compose-State: soll keine Neukomposition auslösen). */
private class StableHeight { var value: androidx.compose.ui.unit.Dp = 0.dp }
