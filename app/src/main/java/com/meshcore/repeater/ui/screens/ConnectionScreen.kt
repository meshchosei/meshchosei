package com.meshcore.repeater.ui.screens
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
    val bleDevices by viewModel.bleDevices.collectAsState()
    val isScanningBle by viewModel.isScanningBle.collectAsState()
    val caps by viewModel.deviceCapabilities.collectAsState()
    val meshContacts by viewModel.meshContacts.collectAsState()
    val isRefreshingContacts by viewModel.isRefreshingContacts.collectAsState()
    val remoteAdmin by viewModel.remoteAdminSession.collectAsState()
    val bColors = LocalBauhausColors.current
    val blePerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    val blePermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) viewModel.startBleScan()
    }

    var manualCommand by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var filterOnlyRepeaters by remember { mutableStateOf(true) }
    var filterOnlyKnownPaths by remember { mutableStateOf(false) }
    var contactSearchQuery by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp)
    ) {
        // Active Connection Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = if (selectedType == ConnectionType.USB_SERIAL) Icons.Default.Usb else Icons.Default.Bluetooth
                Box(
                    modifier = Modifier
                        .size(46.dp)
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
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (val state = connectionState) {
                            is ConnectionState.Connected -> state.deviceName
                            is ConnectionState.Connecting -> "Verbinde..."
                            is ConnectionState.Error -> "Fehler: ${state.message}"
                            is ConnectionState.Disconnected -> "Keine aktive Verbindung"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (connectionState is ConnectionState.Connected && !caps.detectionComplete) {
                        AsciiSpinner("Erkenne Gerät & Rolle...", bColors.cobalt)
                    } else if (connectionState is ConnectionState.Connecting) {
                        AsciiSpinner(connectionState.let { (it as ConnectionState.Connecting).message }, bColors.ochre)
                    } else if (isScanningBle && connectionState is ConnectionState.Disconnected) {
                        AsciiSpinner("Scanne nach BLE-Nodes...", bColors.cobalt)
                    } else Text(
                        text = when (val state = connectionState) {
                            is ConnectionState.Connected -> {
                                if (remoteAdmin.targetRepeater != null) {
                                    "${remoteAdmin.targetRepeater?.name} • ${if (remoteAdmin.isLoggedIn) "Eingeloggt" else remoteAdmin.sessionStatus}"
                                } else {
                                    "${state.connectionType.displayName} • ${state.details}"
                                }
                            }
                            is ConnectionState.Connecting -> state.message
                            else -> "Wähle USB-OTG oder BLE-Companion (Mesh OTA)"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (remoteAdmin.isLoggedIn) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (connectionState is ConnectionState.Connected) {
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.disconnect() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed)
                    ) {
                        Text("Trennen")
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
                text = { Text("USB-Serial (OTG)", fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.Default.Usb, contentDescription = null) }
            )
            Tab(
                selected = selectedType == ConnectionType.BLE_COMPANION,
                enabled = bleEnabled,
                onClick = { if (bleEnabled) viewModel.selectConnectionType(ConnectionType.BLE_COMPANION) },
                modifier = Modifier.alpha(if (bleEnabled) 1f else 0.35f),
                text = { Text("BLE Companion (Mesh OTA)", fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.Default.Bluetooth, contentDescription = null) }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tab Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(12.dp)
        ) {
            if (selectedType == ConnectionType.USB_SERIAL) {
                // USB Serial Device List
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Erkannte OTG-Geräte (${usbDevices.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = { viewModel.refreshUsbDevices() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren", tint = bColors.signalRed)
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
                                text = "Bitte OTG-Kabel anschließen",
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
                                        Button(
                                            onClick = { viewModel.connectUsb(dev.deviceId, 115200) },
                                            colors = ButtonDefaults.buttonColors(containerColor = bColors.signalRed),
                                            enabled = connectionState !is ConnectionState.Connected
                                        ) {
                                            Text("Verbinden", color = Color.Black)
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
                                text = "Gefundene BLE-Nodes (${bleDevices.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (isScanningBle) {
                                Row(verticalAlignment = Alignment.CenterVertically) {

                                    OutlinedButton(onClick = { viewModel.stopBleScan() }) {
                                        Text("Stop", fontSize = 12.sp)
                                    }
                                }
                            } else {
                                Button(
                                    onClick = { blePermLauncher.launch(blePerms) },
                                    colors = ButtonDefaults.buttonColors(containerColor = bColors.cobalt)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null, tint = Color.Black)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Scannen", color = Color.Black)
                                }
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
                                    text = if (isScanningBle) "Suche nach Bluetooth-Nodes..." else "Verbinde dich per Bluetooth mit deinem Companion-Node (um empfangene Repeater über das Mesh zu steuern) oder direkt mit einem BLE-fähigen Repeater.\n\nTippe oben auf 'Scannen'.",
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
                                                    text = "${ble.address} • RSSI: ${ble.rssi} dBm",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Button(
                                                onClick = { viewModel.connectBle(ble.address) },
                                                colors = ButtonDefaults.buttonColors(containerColor = bColors.cobalt)
                                            ) {
                                                Text("Koppeln", color = Color.Black)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Connected to BLE Companion: Show Mesh Contact List and Remote-Admin Panel!
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Section Header: Companion Contact List
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Repeater-Auswahl (${meshContacts.size})", style = MaterialTheme.typography.titleMedium, color = bColors.signalRed, fontWeight = FontWeight.Bold)
                                Text("Companion Mesh-Kontakte", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { viewModel.refreshMeshContacts() }, enabled = !isRefreshingContacts) {
                                if (isRefreshingContacts) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = bColors.cobalt, strokeWidth = 2.dp)
                                else Icon(Icons.Default.Refresh, contentDescription = "Neu laden", tint = bColors.cobalt)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = filterOnlyRepeaters, onClick = { filterOnlyRepeaters = !filterOnlyRepeaters }, label = { Text("Nur Repeater", fontSize = 11.sp) })
                            FilterChip(selected = filterOnlyKnownPaths, onClick = { filterOnlyKnownPaths = !filterOnlyKnownPaths }, label = { Text("Nur aktive Pfade", fontSize = 11.sp) })
                        }
                        OutlinedTextField(
                            value = contactSearchQuery,
                            onValueChange = { contactSearchQuery = it },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            placeholder = { Text("Repeater suchen (Name oder Key)...", fontSize = 12.sp) },
                            singleLine = true,
                            trailingIcon = {
                                if (contactSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { contactSearchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Suche löschen", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Target Repeater Selection Card & Password Input
                        if (remoteAdmin.targetRepeater != null) {
                            val target = remoteAdmin.targetRepeater!!
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, if (remoteAdmin.isLoggedIn) bColors.signalRed else bColors.cobalt, RoundedCornerShape(10.dp)),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF141C1A))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
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
                                                    text = "Key: ${target.keyPrefix}... • ${when (target.outPathLen) { 0 -> "Direkt (0 Hops)"; in 1..64 -> "${target.outPathLen} Hops"; else -> "Flood (kein direkter Pfad)" }}",
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
                                                text = if (remoteAdmin.isLoggedIn) "Admin aktiv" else "Nicht eingeloggt",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (remoteAdmin.isLoggedIn) bColors.signalRed else bColors.ochre
                                            )
                                        }
                                    }

                                    if (remoteAdmin.isLoggingIn) {
                                        val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "sync_pulse")
                                        val pulseAlpha by infiniteTransition.animateFloat(
                                            initialValue = 1f,
                                            targetValue = 0.2f,
                                            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                                animation = androidx.compose.animation.core.tween(450),
                                                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                                            ),
                                            label = "pulse_alpha"
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).alpha(pulseAlpha),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(modifier = Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(bColors.cobalt))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            AsciiSpinner("Lade Daten vom Repeater...", bColors.cobalt)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Password Input & Login Action
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = remoteAdmin.savedPassword,
                                            onValueChange = { viewModel.setRemoteAdminPassword(it) },
                                            modifier = Modifier.weight(1f),
                                            label = { Text("Admin-Passwort (max 15 Zeichen)", fontSize = 11.sp) },
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

                                        Button(
                                            onClick = { viewModel.startRemoteLogin() },
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

                        // Contact List
                        val filteredContacts = meshContacts.filter { ct ->
                            val typeOk = !filterOnlyRepeaters || ct.isRepeater || ct.isRoomServer
                            val pathOk = !filterOnlyKnownPaths || (ct.outPathLen in 0..64)
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
                                    text = if (isRefreshingContacts) "Lade..."
                                    else if (filterOnlyRepeaters) "Keine Repeater in der Kontaktliste.\nKlicke oben auf 'Neu laden'."
                                    else "Keine Kontakte empfangen.\nKlicke oben auf 'Neu laden'.",
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
                                                    text = "Key: ${contact.keyPrefix}... • ${when (contact.outPathLen) { 0 -> "0 Hops (Direkt)"; in 1..64 -> "${contact.outPathLen} Hops"; else -> "Flood (unbekannt)" }}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Ausgewählt",
                                                    tint = bColors.signalRed,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            } else {
                                                OutlinedButton(
                                                    onClick = { viewModel.selectTargetRepeater(contact) }
                                                ) {
                                                    Text("Wählen", fontSize = 11.sp)
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

        Spacer(modifier = Modifier.height(10.dp))
    }
}
