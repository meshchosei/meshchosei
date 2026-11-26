package com.meshcore.repeater.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.23: Kanal-Eingabe im Paket-Filter (Firmware: `#name` oder exakt `Public`). */
class ChannelNameRulesTest {
    @Test fun prependsHashWhenMissing() {
        assertEquals("#test", ChannelNameRules.normalize("test"))
        assertEquals("#bot", ChannelNameRules.normalize("bot"))
        assertEquals("#mesh-de", ChannelNameRules.normalize("  mesh-de  "))
    }

    @Test fun keepsExistingHash() {
        assertEquals("#bot", ChannelNameRules.normalize("#bot"))
        assertEquals("#Spam", ChannelNameRules.normalize(" #Spam "))
    }

    /** Firmware vergleicht `strcmp(name, "Public")` – jede Schreibweise wird zu exakt „Public“, ohne #. */
    @Test fun publicIsCanonical() {
        assertEquals("Public", ChannelNameRules.normalize("public"))
        assertEquals("Public", ChannelNameRules.normalize("PUBLIC"))
        assertEquals("Public", ChannelNameRules.normalize(" Public "))
        assertEquals("Public", ChannelNameRules.normalize("public", whitelist = true))
    }

    @Test fun emptyStaysEmpty() {
        assertEquals("", ChannelNameRules.normalize(""))
        assertEquals("", ChannelNameRules.normalize("   "))
    }

    /** Whitelist nimmt einen 2-stelligen Hex-Hash an (`filter wl channel add d9`) – kein # davor. */
    @Test fun whitelistKeepsHexHash() {
        assertEquals("d9", ChannelNameRules.normalize("D9", whitelist = true))
        assertEquals("d9", ChannelNameRules.normalize("0xD9", whitelist = true))
        assertEquals("#d9", ChannelNameRules.normalize("d9", whitelist = false))
    }

    @Test fun publicBlacklistGuard() {
        for (n in listOf("Public", "public", "PUBLIC", "#Public", "#public", " Public "))
            assertTrue(n, ChannelNameRules.needsPublicBlockConfirm(n, blacklist = true))
        assertFalse("Whitelist: Public ohne Warnung", ChannelNameRules.needsPublicBlockConfirm("Public", blacklist = false))
        assertFalse(ChannelNameRules.needsPublicBlockConfirm("#public", blacklist = false))
        assertFalse(ChannelNameRules.needsPublicBlockConfirm("#spam", blacklist = true))
        assertFalse(ChannelNameRules.needsPublicBlockConfirm("#publicity", blacklist = true))
        assertTrue("normalisierte Eingabe 'public'", ChannelNameRules.needsPublicBlockConfirm(ChannelNameRules.normalize("public"), blacklist = true))
    }

    /** Hinzufügen: nur „+“ dreht. Löschen: nur der Mülleimer der betroffenen Zeile dreht, „+“ bleibt ruhig. */
    @Test fun addAndDeleteSpinnersAreSeparate() {
        val key = "bl_channel"
        // Hinzufügen läuft (keine Lösch-Markierung)
        assertTrue(ListBusyRules.addButtonBusy(keyBusy = true, deletingId = null, key = key))
        assertFalse(ListBusyRules.rowDeleteBusy(true, null, key, "#spam"))
        // Löschen von #spam läuft
        val del = ListBusyRules.tag(key, "#spam")
        assertFalse("+ bleibt beim Löschen ruhig", ListBusyRules.addButtonBusy(true, del, key))
        assertTrue(ListBusyRules.rowDeleteBusy(true, del, key, "#spam"))
        assertFalse("andere Zeile ruhig", ListBusyRules.rowDeleteBusy(true, del, key, "#bot"))
        // Lösch-Markierung einer anderen Liste stört „+“ nicht
        assertTrue(ListBusyRules.addButtonBusy(true, ListBusyRules.tag("wl_channel", "Public"), key))
        // Nichts läuft -> alles ruhig
        assertFalse(ListBusyRules.addButtonBusy(false, null, key))
        assertFalse(ListBusyRules.rowDeleteBusy(false, del, key, "#spam"))
        assertTrue(ListBusyRules.keyOf(del) == key)
    }
}
