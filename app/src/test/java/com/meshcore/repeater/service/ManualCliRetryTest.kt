package com.meshcore.repeater.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualCliRetryTest {
    @Test fun queriesAndSetsAreRetried() {
        for (c in listOf("get name", "get owner.info", "ver", "set name Turm", "filter channel add #test", "region put Berlin", "filter", "  get   radio "))
            assertTrue(c, ManualCliRetry.isRetrySafe(c))
        assertEquals(1, ManualCliRetry.MAX_RETRIES)
    }

    /** Der Repeater würde einen wiederholten Befehl erneut ausführen (neuer Zeitstempel vom Companion). */
    @Test fun nonIdempotentCommandsAreNeverRetried() {
        for (c in listOf("reboot", "poweroff", "erase", "start ota", "start dfu", "advert", "advert.zerohop", "clear stats",
            "filter RESET", "password geheim", "region remove Berlin", "set prv.key abcd", "discover.neighbors", ""))
            assertFalse(c, ManualCliRetry.isRetrySafe(c))
    }
}
