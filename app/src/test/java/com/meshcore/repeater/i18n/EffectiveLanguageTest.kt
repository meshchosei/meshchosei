package com.meshcore.repeater.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

class EffectiveLanguageTest {
    @Test fun systemLanguageUsedWhenNothingSelected() {
        assertEquals("de", LanguageCode.effective("", "de"))
        assertEquals("de", LanguageCode.effective(null, "de"))
        assertEquals("nl", LanguageCode.effective("system", "nl"))
    }

    @Test fun fullLocalesAreNormalized() {
        assertEquals("de", LanguageCode.effective("", "de-DE"))
        assertEquals("fr", LanguageCode.effective("", "fr_FR"))
        assertEquals("en", LanguageCode.effective("EN-GB", "de"))
    }

    @Test fun explicitSelectionWins() {
        assertEquals("fr", LanguageCode.effective("fr", "de"))
    }

    @Test fun unsupportedFallsBackToEnglish() {
        assertEquals("en", LanguageCode.effective("", "es"))
        assertEquals("en", LanguageCode.effective("", ""))
    }
}
