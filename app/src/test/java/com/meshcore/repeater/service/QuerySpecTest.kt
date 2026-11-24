package com.meshcore.repeater.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuerySpecTest {
    private fun ok(cmd: String, v: String) = QuerySpec.validatorFor(cmd)(v)

    @Test fun radioNeverAcceptedAsBoardOrName() {
        assertFalse(ok("board", "869.618,62.5,8,8"))
        assertFalse(ok("get name", "869.618,62.5,8,8"))
        assertFalse(ok("get owner.info", "869.618,62.5,8,8"))
        assertTrue(ok("get radio", "869.618,62.5,8,8"))
        assertTrue(ok("get radio", "869.525,250,11,5"))
    }
    @Test fun board() {
        assertTrue(ok("board", "Wio Tracker L1"))
        assertTrue(ok("board", "Heltec V3"))
        assertFalse(ok("board", "v1.18.2-zephcore (Build: 2026-11-01)"))
        assertFalse(ok("board", "22"))
        assertFalse(ok("board", "on"))
        assertFalse(ok("board", "51.881000"))
    }
    @Test fun ver() {
        assertTrue(ok("ver", "v1.18.2-zephcore-BW2 (Build: 2026-11-01)"))
        assertFalse(ok("ver", "869.618,62.5,8,8"))
        assertFalse(ok("ver", "51.881000"))
    }
    @Test fun nameAndOwner() {
        assertTrue(ok("get name", "RAK-Harz Repeater"))
        assertTrue(ok("get name", "Wio Brocken"))
        assertFalse(ok("get name", "10.0%"))
        assertFalse(ok("get name", "on"))
        assertTrue(ok("get owner.info", "Max | max@example.org"))
        assertFalse(ok("get owner.info", "240"))
    }
    @Test fun numbersAndCoords() {
        assertTrue(ok("get tx", "22")); assertFalse(ok("get tx", "869.618,62.5,8,8"))
        assertTrue(ok("get dutycycle", "10.0%")); assertFalse(ok("get dutycycle", "22"))
        assertTrue(ok("get lat", "51.881000")); assertFalse(ok("get lat", "22")); assertFalse(ok("get lat", "191.0"))
        assertTrue(ok("get lon", "-10.562000"))
        assertTrue(ok("get path.hash.mode", "1")); assertFalse(ok("get path.hash.mode", "22"))
        assertTrue(ok("get loop.detect", "minimal"))
        assertTrue(ok("get advert.interval", "240"))
        assertTrue(ok("get txdelay", "adaptive (est=0.3 flood=0.50)"))
        assertTrue(ok("get leds", "on"))
        assertTrue(ok("get repeat", "off")); assertFalse(ok("get repeat", "Wio"))
    }
    @Test fun errors() {
        assertTrue(QuerySpec.isErrorReply("Unknown command"))
        assertTrue(QuerySpec.isErrorReply("ERROR: dutycycle must be 10-100"))
        assertTrue(QuerySpec.isErrorReply("??: foo"))
        assertFalse(QuerySpec.isErrorReply("Wio Tracker L1"))
    }

    @Test
    fun filterDetection_usesRealFirmwareReplies() {
        // ZephCore/MeshCore Filter.cpp (nach Entfernen von "> ")
        val blHelp = "filter [ help | on | off | reset | dryrun | types | count | stats | hops | rate | channel | hash | malformed | advert | path | sender | text | watch | age ]"
        val wlHelp = "filter wl [channel|path] [list|add|del] ..."
        assertTrue(QuerySpec.isBlacklistHelp(blHelp))
        assertTrue(QuerySpec.isWhitelistHelp(wlHelp))
        assertFalse(QuerySpec.isWhitelistHelp("Filter: command error"))
        assertFalse(QuerySpec.isBlacklistHelp("Unknown command"))
        assertFalse(QuerySpec.isBlacklistHelp(""))
        assertFalse(QuerySpec.isBlacklistHelp(null))
        assertFalse(QuerySpec.isWhitelistHelp("?? filter"))
        assertTrue(QuerySpec.validatorFor("filter wl help")("Filter: command error")) // Antwort annehmen, aber nicht als Whitelist werten
    }

    @Test
    fun powerValidator_acceptsOnlyPowerReplies() {
        val v = QuerySpec.validatorFor("power")
        assertTrue(v("batt:4073 mV usb:no solar_chg:n/a ext:no charger:no"))
        assertFalse(v("869.618,62.5,8,8"))
        assertFalse(v("OK"))
    }

    @Test
    fun publicKeyValidator_acceptsExactly64Hex() {
        val v = QuerySpec.validatorFor("get public.key")
        assertTrue(v("A1B2C3D4E5F60718293A4B5C6D7E8F90A1B2C3D4E5F60718293A4B5C6D7E8F90"))
        assertFalse(v("A1B2C3D4"))                       // zu kurz
        assertFalse(v("a1".repeat(64)))                  // 128 Zeichen = Private Key, nicht Public Key
        assertFalse(v("MeinRepeater"))
        assertFalse(QuerySpec.validatorFor("get name")("a1".repeat(32)))  // Key nie als Name übernehmen
    }
}
