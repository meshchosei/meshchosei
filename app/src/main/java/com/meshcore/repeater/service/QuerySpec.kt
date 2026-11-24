package com.meshcore.repeater.service

import com.meshcore.repeater.i18n.tr

/**
 * Strenges Request-Response-Protokoll: Für jeden Abfragebefehl ein Format-Validator und ein Klartext
 * für das Status-Overlay. Eine Antwort wird nur dann dem aktiven Befehl zugeordnet, wenn sie dessen
 * Format erfüllt – verspätete oder fremde Antworten (z. B. "869.618,62.5,8,8" während "board" läuft)
 * werden verworfen statt in falsche Felder geschrieben.
 */
object QuerySpec {
    private val RADIO = Regex("""^\d+(\.\d+)?,\d+(\.\d+)?,\d+,\d+$""")
    private val COORD = Regex("""^-?\d{1,3}\.\d+$""")
    private val INT = Regex("""^-?\d+$""")
    private val DECIMAL = Regex("""^\d+(\.\d+)?$""")
    private val PUBLIC_KEY = Regex("""^[0-9a-fA-F]{64}$""")
    private val HEX_KEY = Regex("""^[0-9a-fA-F]{32,128}$""")
    private val VERSION_START = Regex("""^v?\d+\.\d+""")
    private val VERSION_ANY = Regex("""\d+\.\d+""")
    private val LOOP = setOf("off", "minimal", "moderate", "strict")

    private fun isOnOff(v: String) = v.equals("on", true) || v.equals("off", true)

    /** Kein anderes bekanntes Antwortformat (Funkwerte, Koordinate, Zahl, on/off, Key, Version, Telemetrie). */
    private fun isForeignFormat(v: String) =
        RADIO.matches(v) || COORD.matches(v) || INT.matches(v) || isOnOff(v) || HEX_KEY.matches(v) ||
            VERSION_START.containsMatchIn(v) || v.contains("(Build:", true) || v.startsWith("batt:", true) ||
            v.startsWith("adaptive", true) || v.endsWith("%")

    /** Antwort bedeutet "Befehl verstanden, aber kein Wert" (Fehler/unbekannt). */
    fun isErrorReply(v: String): Boolean {
        val lo = v.lowercase()
        return lo.startsWith("unknown") || lo.startsWith("err") || lo.startsWith("error") || v.startsWith("??") || lo.startsWith("not available")
    }

    /** Befehle, deren Antwort aus mehreren Zeilen bestehen kann (danach kurze Nachlaufzeit). */
    fun isMultiLine(cmd: String): Boolean {
        val c = cmd.trim().lowercase()
        return c.startsWith("filter") || c == "region" || c.startsWith("region def") || c == "help" || c == "clock" || c == "stats" || c.startsWith("neighbors")
    }

    fun validatorFor(cmd: String): (String) -> Boolean = when (cmd.trim().lowercase()) {
        "board" -> { v -> v.length in 2..64 && v.any { it.isLetter() } && !isForeignFormat(v) && !v.startsWith("filter", true) }
        "ver" -> { v -> VERSION_ANY.containsMatchIn(v) && v.any { it.isLetter() } && !RADIO.matches(v) && !COORD.matches(v) }
        "get radio" -> { v -> RADIO.matches(v) }
        "get tx" -> { v -> INT.matches(v) && (v.toIntOrNull() ?: 99) in -9..37 }
        "get dutycycle" -> { v -> v.endsWith("%") && DECIMAL.matches(v.removeSuffix("%").trim()) }
        // MeshCore/ZephCore CommonCLI: "> " + toHex(pub_key, PUB_KEY_SIZE = 32) -> genau 64 Hex-Zeichen
        "get public.key" -> { v -> PUBLIC_KEY.matches(v) }
        "get name" -> { v -> v.length in 1..32 && !isForeignFormat(v) && !PUBLIC_KEY.matches(v) }
        "get owner.info" -> { v -> v.length in 1..160 && !RADIO.matches(v) && !COORD.matches(v) && !INT.matches(v) && !isOnOff(v) && !v.contains("(Build:", true) && !v.endsWith("%") }
        "get lat" -> { v -> COORD.matches(v) && (v.toDoubleOrNull() ?: 999.0) in -90.0..90.0 }
        "get lon" -> { v -> COORD.matches(v) && (v.toDoubleOrNull() ?: 999.0) in -180.0..180.0 }
        "get repeat", "get allow.read.only" -> { v -> isOnOff(v) }
        "get path.hash.mode" -> { v -> v in setOf("0", "1", "2") }
        "get loop.detect" -> { v -> v.lowercase() in LOOP }
        "get advert.interval", "get flood.advert.interval" -> { v -> Regex("""^\d{1,5}$""").matches(v) }
        "get txdelay", "get direct.txdelay", "get rxdelay" -> { v -> v.startsWith("adaptive", true) || DECIMAL.matches(v) }
        "get multi.acks" -> { v -> v == "0" || v == "1" }
        "get leds" -> { v -> v.lowercase().substringBefore(' ') in setOf("on", "off", "boot") }
        // MeshCore/ZephCore CommonCLI: "batt:4073 mV usb:no solar_chg:n/a ext:no charger:no"
        "power" -> { v -> Regex("""\bbatt:\s*\d+\s*mV\b""", RegexOption.IGNORE_CASE).containsMatchIn(v) }
        "get gps" -> { v -> v.startsWith("on", true) || v.startsWith("off", true) || v.contains("lat=", true) }
        // Backup-Variablen
        "get af" -> { v -> DECIMAL.matches(v) }
        "get int.thresh", "get agc.reset.interval", "get flood.max", "get flood.max.unscoped", "get flood.max.advert" -> { v -> INT.matches(v) }
        "get cad", "get radio.rxgain" -> { v -> isOnOff(v) }
        "get prv.key" -> { v -> HEX_KEY.matches(v) }
        "get guest.password" -> { v -> v.length <= 32 && !RADIO.matches(v) && !v.contains("(Build:", true) }
        // Filter-Erkennung: jede "filter…"-Antwort annehmen (auch "Filter: command error"), Auswertung in isBlacklistHelp/isWhitelistHelp
        "filter help", "filter wl help" -> { v -> v.startsWith("filter", true) }
        "filter mode" -> { v -> v.contains("mode", true) }
        // Listen: nur das jeweilige Listenformat bzw. "None" annehmen – keine verspäteten Status-/OK-Zeilen
        "filter channel list", "filter wl channel list", "filter path list", "filter wl path list" -> { v -> FilterListParser.isValidListReply(cmd, v) }
        // Regionen (CommonCLI::handleRegionCmd): Baum beginnt immer mit der Wildcard "*"
        "region" -> { v -> v.trimStart().startsWith("*") }
        "region home" -> { v -> v.contains("home is", true) }
        "region default" -> { v -> v.contains("default scope", true) }
        else -> { _ -> true } // set-/sonstige Befehle: erste Antwortzeile (z. B. "OK")
    }

    /** Gültige Antwort auf "filter help" mit Sperrlisten-Befehlen (kein Fehler, nicht leer). */
    fun isBlacklistHelp(v: String?): Boolean {
        if (v.isNullOrBlank() || isErrorReply(v)) return false
        val lo = v.lowercase()
        return lo.startsWith("filter") && !lo.contains("error") && (lo.contains("channel") || lo.contains("path"))
    }

    /** Gültige Antwort auf "filter wl help": "filter wl [channel|path] [list|add|del] ..." (kein Fehler, nicht leer). */
    fun isWhitelistHelp(v: String?): Boolean {
        if (v.isNullOrBlank() || isErrorReply(v)) return false
        val lo = v.lowercase()
        return lo.startsWith("filter wl") && !lo.contains("error") && lo.contains("list")
    }

    fun statusTextFor(cmd: String): String = when (cmd.trim().lowercase()) {
        "board" -> "Hardware-Board wird abgefragt…"
        "ver" -> "Firmware-Version wird abgefragt…"
        "get radio" -> "Funkparameter werden abgefragt…"
        "get tx", "get dutycycle" -> "Sendeleistung & Duty-Cycle werden abgefragt…"
        "get public.key" -> "Public Key wird abgefragt…"
        "get name" -> "Repeater-Name wird abgefragt…"
        "get owner.info" -> "Besitzer-Info wird abgefragt…"
        "get lat", "get lon" -> "Standort-Koordinaten werden abgefragt…"
        "get repeat", "get advert.interval", "get leds", "get flood.advert.interval", "get path.hash.mode",
        "get loop.detect", "get txdelay", "get direct.txdelay", "get rxdelay", "get multi.acks" -> "Routing & Adverts werden abgefragt…"
        "get gps" -> "GPS-Status wird abgefragt…"
        "filter", "filter help" -> "Prüfe Geräte- & Filter-Funktionen…"
        "power" -> "Versorgung wird abgefragt…"
        "clock" -> "Telemetrie wird abgefragt…"
        "region", "region home", "region default" -> "Regionen werden abgefragt…"
        else -> if (cmd.trim().lowercase().startsWith("filter")) "Filter-Einstellungen werden abgefragt…" else tr("\"{0}\" wird ausgeführt…", cmd.trim())
    }
}
