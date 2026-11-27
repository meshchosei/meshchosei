package com.meshcore.repeater.ui

import com.meshcore.repeater.ui.components.BulletListRules
import com.meshcore.repeater.ui.navigation.DrawerItemRules
import com.meshcore.repeater.ui.navigation.NavDrawerItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerItemRulesTest {
    private fun enabled(item: NavDrawerItem, nav: Boolean = false, ota: Boolean = false) =
        DrawerItemRules.isEnabled(item, navigationAllowed = nav, meshCareAvailable = true, supportsBlacklist = true, supportsWhitelist = true, otaMenuEnabled = ota)

    @Test fun firmwareOtaIsAlwaysListedAtItsFixedPlace() {
        val items = DrawerItemRules.items()
        assertEquals(NavDrawerItem.values().toList(), items)   // nichts ausgeblendet
        val i = items.indexOf(NavDrawerItem.FIRMWARE_OTA)
        assertEquals(NavDrawerItem.BACKUP, items[i - 1]); assertEquals(NavDrawerItem.ABOUT, items[i + 1])
    }

    @Test fun firmwareOtaGreyedOutAtAppStartUntilConnectedOrOtaActive() {
        assertFalse("App-Start ohne Verbindung: ausgegraut", enabled(NavDrawerItem.FIRMWARE_OTA))
        assertTrue("verbunden", enabled(NavDrawerItem.FIRMWARE_OTA, ota = true))
        assertFalse("folgt nur otaMenuEnabled, nicht der Start-Scan-Sperre", enabled(NavDrawerItem.FIRMWARE_OTA, nav = true, ota = false))
    }

    @Test fun otherItemsKeepTheirRules() {
        assertTrue(enabled(NavDrawerItem.CONNECTION)); assertTrue(enabled(NavDrawerItem.ABOUT))
        assertFalse(enabled(NavDrawerItem.BACKUP)); assertTrue(enabled(NavDrawerItem.BACKUP, nav = true))
        assertFalse(DrawerItemRules.isEnabled(NavDrawerItem.BLACKLIST, true, true, supportsBlacklist = false, supportsWhitelist = true, otaMenuEnabled = false))
        assertFalse(DrawerItemRules.isEnabled(NavDrawerItem.MESH_CARE, true, meshCareAvailable = false, supportsBlacklist = true, supportsWhitelist = true, otaMenuEnabled = false))
    }

    @Test fun bulletListSplitsResourceText() {
        val items = BulletListRules.items("• Jetpack Compose & Material 3 (Apache 2.0)\n• Nordic Android DFU Library (BSD-3-Clause License, Nordic Semiconductor ASA)\n\n")
        assertEquals(listOf("Jetpack Compose & Material 3 (Apache 2.0)", "Nordic Android DFU Library (BSD-3-Clause License, Nordic Semiconductor ASA)"), items)
    }
}
