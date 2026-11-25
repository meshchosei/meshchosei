package com.meshcore.repeater.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.18: Bootloader-Version (get bootloader.ver) und OTA-Bootloop-Warnung unter 0.9.2-OTAFIX2.2-BP1.3. */
class BootloaderCheckTest {

    @Test fun normalizeKeepsOnlyVersions() {
        assertEquals("0.9.2-OTAFIX2.2-BP1.3", BootloaderCheck.normalize("> 0.9.2-OTAFIX2.2-BP1.3"))
        assertEquals("0.6.0", BootloaderCheck.normalize("0.6.0"))
        assertNull(BootloaderCheck.normalize("unknown"))
        assertNull(BootloaderCheck.normalize("> unknown"))
        assertNull(BootloaderCheck.normalize("Error: unsupported"))
        assertNull(BootloaderCheck.normalize(null))
    }

    @Test fun minimumAndNewerAreOk() {
        assertFalse(BootloaderCheck.needsOtaWarning("0.9.2-OTAFIX2.2-BP1.3"))
        assertFalse(BootloaderCheck.needsOtaWarning("0.9.2-OTAFIX2.2-BP1.4"))
        assertFalse(BootloaderCheck.needsOtaWarning("0.9.2-OTAFIX2.3-BP1.0"))
        assertFalse(BootloaderCheck.needsOtaWarning("0.9.3-OTAFIX2.2"))
        assertFalse(BootloaderCheck.needsOtaWarning("0.10.0-OTAFIX3.0-BP2.0"))
    }

    @Test fun olderBootloadersWarn() {
        assertTrue(BootloaderCheck.needsOtaWarning("0.9.2-OTAFIX2.2-BP1.2"))
        assertTrue(BootloaderCheck.needsOtaWarning("0.9.2-OTAFIX2.1-BP1.3"))
        assertTrue(BootloaderCheck.needsOtaWarning("0.9.1-OTAFIX2.2-BP1.3"))
        assertTrue(BootloaderCheck.needsOtaWarning("0.9.2"))       // ohne OTAFIX-Patch
        assertTrue(BootloaderCheck.needsOtaWarning("0.6.0"))
        assertTrue(BootloaderCheck.needsOtaWarning("0.8.3-wio"))
    }

    @Test fun noWarningWithoutVersion() {
        assertFalse(BootloaderCheck.needsOtaWarning(null))
        assertFalse(BootloaderCheck.needsOtaWarning(""))
        assertFalse(BootloaderCheck.needsOtaWarning("unknown"))
        assertFalse(BootloaderCheck.needsOtaWarning("Error: unsupported"))
    }
}
