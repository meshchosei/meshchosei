package com.meshcore.repeater.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.meshcore.repeater.R

enum class NavDrawerItem(
    val route: String,
    val titleRes: Int,
    val icon: ImageVector,
    val subtitleRes: Int
) {
    CONNECTION("connection", R.string.menu_connection, Icons.Default.Usb, R.string.menu_connection_sub),
    MESH_CARE("mesh_care", R.string.menu_mesh_care, Icons.Default.ElectricBolt, R.string.menu_mesh_care_sub),
    HARDWARE_ANTENNA("hardware_antenna", R.string.menu_hardware_antenna, Icons.Default.Memory, R.string.menu_hardware_antenna_sub),
    RADIO_CONFIG("radio_config", R.string.menu_radio_config, Icons.Default.CellTower, R.string.menu_radio_config_sub),
    IDENTITY_LOCATION("identity_location", R.string.menu_identity_location, Icons.Default.PersonPinCircle, R.string.menu_identity_location_sub),
    ROUTING_ADVERTS("routing_adverts", R.string.menu_routing_adverts, Icons.Default.AltRoute, R.string.menu_routing_adverts_sub),
    BLACKLIST("blacklist", R.string.menu_blacklist, Icons.Default.Block, R.string.menu_blacklist_sub),
    WHITELIST("whitelist", R.string.menu_whitelist, Icons.Default.VerifiedUser, R.string.menu_whitelist_sub),
    CLI_CONSOLE("cli_console", R.string.menu_cli, Icons.Default.Terminal, R.string.menu_cli_sub),
    BACKUP("backup", R.string.menu_backup, Icons.Default.Backup, R.string.menu_backup_sub),
    FIRMWARE_OTA("firmware_ota", R.string.menu_ota, Icons.Default.SystemUpdate, R.string.menu_ota_sub),
    ABOUT("about", R.string.menu_about, Icons.Default.Info, R.string.menu_about_sub)
}
