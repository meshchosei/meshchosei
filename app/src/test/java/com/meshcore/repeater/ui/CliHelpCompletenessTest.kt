package com.meshcore.repeater.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** CLI-Hilfe (v1.12): alle in der App genutzten Variablen enthalten, alle Texte in EN/NL/FR, nur firmware-gültige Regionen-Befehle. */
class CliHelpCompletenessTest {
    private fun file(rel: String) = listOf(File(rel), File("app/$rel")).first { it.exists() }
    private val data = file("src/main/java/com/meshcore/repeater/ui/screens/CliHelpData.kt").readText()
    private val json = file("src/main/assets/i18n.json").readText()
    private val syntaxes = Regex("""CliHelpCommand\("([^"]*)"""").findAll(data).map { it.groupValues[1] }.toList()

    @Test fun usedVariablesAreDocumented() {
        val getSet = listOf("owner.info", "path.hash.mode", "loop.detect", "multi.acks", "agc.reset.interval",
            "int.thresh", "rxdelay", "txdelay", "direct.txdelay", "flood.max", "flood.advert.interval", "advert.interval",
            "leds", "dutycycle", "guest.password", "allow.read.only", "radio.rxgain", "cad", "name", "lat", "lon", "repeat", "tx", "radio", "af")
        for (v in getSet) {
            assertTrue("get $v fehlt", syntaxes.any { it == "get $v" })
            assertTrue("set $v fehlt", syntaxes.any { it.startsWith("set $v ") })
        }
        // nur lesbar (MeshCore/ZephCore CommonCLI: kein "set public.key"; prv.key per set nur zum Import)
        for (v in listOf("public.key", "prv.key", "flood.max.unscoped", "flood.max.advert")) assertTrue("get $v fehlt", syntaxes.contains("get $v"))
        for (c in listOf("region", "filter", "tempradio ", "region home", "region default", "region put", "region remove", "region allowf", "region denyf", "region save"))
            assertTrue("$c fehlt", syntaxes.any { it == c.trim() || it.startsWith(c) })
        assertTrue(syntaxes.contains("filter RESET"))
    }

    /** v1.18: Befehle aus MeshCore/ZephCore CommonCLI.cpp, die in v1.17 fehlten (Power-Management, Diagnose, GPS, LEDs, Uplink …). */
    @Test fun firmwareCommandsFromCommonCliDocumented() {
        val getOnly = listOf("pwrmgt.support", "pwrmgt.source", "pwrmgt.bootreason", "pwrmgt.bootmv", "bootloader.ver", "role",
            "freq", "freqerr", "pm", "pwr", "mem", "loop.wakes", "dc.restarts", "cad.stats", "dc.gate.status", "acl", "uplink.status")
        for (v in getOnly) assertTrue("get $v fehlt", syntaxes.contains("get $v"))
        val getSet = listOf("adc.multiplier", "rxduty", "dc.gate", "dc.gate.thresh", "dc.gate.hyst", "radio.fem.rxgain", "cad.auto", "cad.offset",
            "cad.busycap", "probe.interval", "backoff.multiplier", "extra.sf", "meshtimesync", "advert.loc", "leds.radio", "leds.hb",
            "leds.brightness", "display.rotate", "input.rotate", "tz.offset", "bridge.delay", "bridge.source", "bridge.secret",
            "gps diag", "gps standby", "gps duty", "uplink.enable", "uplink.wifi.ssid", "uplink.mqtt.host", "uplink.mqtt.port",
            "uplink.mqtt.tls", "uplink.mqtt.user", "uplink.mqtt.iata")
        for (v in getSet) {
            assertTrue("get $v fehlt", syntaxes.contains("get $v"))
            assertTrue("set $v fehlt", syntaxes.any { it == "set $v" || it.startsWith("set $v ") })
        }
        for (c in listOf("shutdown", "clkreboot", "time ", "powersaving", "log", "log start", "log stop", "log erase", "stats-core",
                "stats-radio", "stats-packets", "sensor list", "sensor get ", "sensor set ", "neighbor.remove ", "setperm ", "region def ",
                "region load", "gps", "gps sync", "gps diag", "gps advert", "set radio default", "set freq ", "set cad.reset",
                "set adc.multiplier target ", "set adc.multiplier full", "set uplink.wifi.psk ", "set uplink.mqtt.password "))
            assertTrue("$c fehlt", syntaxes.any { it == c.trim() || it.startsWith(c) })
    }

    @Test fun onlyFirmwareRegionCommands() {
        // CommonCLI::handleRegionCmd kennt kein "region set"; "region list" braucht allowed|denied
        assertFalse(syntaxes.any { it.startsWith("region set") })
        assertFalse(syntaxes.contains("region list"))
    }

    @Test fun everyHelpTextIsTranslated() {
        val texts = Regex("""CliHelpCommand\("[^"]*", "[^"]*", "([^"]*)"""").findAll(data).map { it.groupValues[1] }.toList() +
            Regex("""title = "([^"]*)"""").findAll(data).map { it.groupValues[1] }.toList()
        assertTrue(texts.size > 100)
        val missing = texts.filterNot { json.contains("\"" + it.replace("\"", "\\\"") + "\": [") }
        assertTrue("Ohne Übersetzung: $missing", missing.isEmpty())
    }
}
