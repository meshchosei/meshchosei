package com.meshcore.repeater.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Geändert vs. unverändert" des Funk-Speichern-Buttons (STYLE_GUIDE §2.2). */
class UnsavedChangesTest {
    private val base = BasicSetupState(freqMhz = "869.618", bwKhz = "62.5", sf = "8", cr = "8", txPowerDbm = "14", dutyCyclePercent = "10")

    @Test fun identicalDraftHasNoUnsavedChanges() = assertFalse(base.copy().radioDiffersFrom(base))

    @Test fun anyRadioFieldMarksDraftAsChanged() {
        assertTrue(base.copy(freqMhz = "869.525").radioDiffersFrom(base))
        assertTrue(base.copy(sf = "9").radioDiffersFrom(base))
        assertTrue(base.copy(txPowerDbm = "20").radioDiffersFrom(base))
        assertTrue(base.copy(dutyCyclePercent = "1").radioDiffersFrom(base))
    }

    @Test fun nonRadioFieldsDoNotCount() = assertFalse(base.copy(nodeName = "neu").radioDiffersFrom(base))
}
