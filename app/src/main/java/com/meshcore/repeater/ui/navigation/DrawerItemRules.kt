package com.meshcore.repeater.ui.navigation

/**
 * Seitenmenü-Einträge (v1.26): Es werden **immer alle** Einträge an ihrem festen Platz angezeigt – gesperrte nur
 * ausgegraut, nie ausgeblendet. „Firmware / OTA“ ist nutzbar mit bereitem Ziel-Repeater (USB direkt oder Remote-Admin
 * eingeloggt) oder während eines aktiven bzw. noch nicht abgeschlossenen OTA-Vorgangs
 * ([otaMenuEnabled] = `isRepeaterReady || otaActiveOrFinished`).
 */
object DrawerItemRules {
    /** Feste Reihenfolge, nichts wird gefiltert. */
    fun items(): List<NavDrawerItem> = NavDrawerItem.values().toList()

    fun isEnabled(
        item: NavDrawerItem,
        navigationAllowed: Boolean,
        meshCareAvailable: Boolean,
        supportsBlacklist: Boolean,
        supportsWhitelist: Boolean,
        otaMenuEnabled: Boolean
    ): Boolean = when (item) {
        NavDrawerItem.FIRMWARE_OTA -> otaMenuEnabled
        NavDrawerItem.CONNECTION, NavDrawerItem.ABOUT -> true
        NavDrawerItem.MESH_CARE -> navigationAllowed && meshCareAvailable
        NavDrawerItem.BLACKLIST -> navigationAllowed && supportsBlacklist
        NavDrawerItem.WHITELIST -> navigationAllowed && supportsWhitelist
        else -> navigationAllowed
    }
}
