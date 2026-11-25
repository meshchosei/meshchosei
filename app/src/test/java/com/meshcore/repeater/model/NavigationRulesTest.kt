package com.meshcore.repeater.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Menü-Sperre nur während des Start-Scans – manuelle CLI-Befehle sind gar kein Eingang der Regel. */
class NavigationRulesTest {
    private fun allowed(initialReadDone: Boolean = true, reading: Boolean = false, companion: Boolean = false, loggedIn: Boolean = false) =
        NavigationRules.navigationAllowed(true, true, companion, loggedIn, initialReadDone, reading)

    @Test fun lockedOnlyDuringStartScan() {
        assertTrue(allowed())
        assertFalse(allowed(reading = true))
        assertFalse(allowed(initialReadDone = false))
    }

    @Test fun companionNeedsRemoteLogin() {
        assertFalse(allowed(companion = true))
        assertTrue(allowed(companion = true, loggedIn = true))
        assertFalse(NavigationRules.navigationAllowed(false, true, false, false, true, false))
    }
}
