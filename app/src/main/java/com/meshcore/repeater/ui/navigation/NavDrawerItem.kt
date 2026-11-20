package com.meshcore.repeater.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Usb
import androidx.compose.ui.graphics.vector.ImageVector
import com.meshcore.repeater.R

enum class NavDrawerItem(
    val route: String,
    val titleRes: Int,
    val icon: ImageVector,
    val subtitleRes: Int
) {
    CONNECTION(
        route = "connection",
        titleRes = R.string.menu_connection,
        icon = Icons.Default.Usb,
        subtitleRes = R.string.menu_connection_sub
    ),
    RADIO_ANTENNA(
        route = "radio_antenna",
        titleRes = R.string.menu_radio,
        icon = Icons.Default.CellTower,
        subtitleRes = R.string.menu_radio_sub
    ),
    FILTER_WHITELIST(
        route = "filter_whitelist",
        titleRes = R.string.menu_filter,
        icon = Icons.Default.FilterList,
        subtitleRes = R.string.menu_filter_sub
    ),
    MESH_CARE(
        route = "mesh_care",
        titleRes = R.string.menu_mesh_care,
        icon = Icons.Default.ElectricBolt,
        subtitleRes = R.string.menu_mesh_care_sub
    ),
    HARDWARE_STATUS(
        route = "hardware_status",
        titleRes = R.string.menu_hardware,
        icon = Icons.Default.Memory,
        subtitleRes = R.string.menu_hardware_sub
    ),
    CLI_CONSOLE("cli_console", R.string.menu_cli, Icons.Default.Terminal, R.string.menu_cli_sub),
    ABOUT("about", R.string.menu_about, Icons.Default.Info, R.string.menu_about_sub)
}
