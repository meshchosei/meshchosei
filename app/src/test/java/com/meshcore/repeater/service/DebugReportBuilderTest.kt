package com.meshcore.repeater.service

import com.meshcore.repeater.service.DebugReportBuilder.LogLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.25: Debug-Bericht – Vollständigkeit der Abschnitte und zuverlässige Schwärzung von Geheimnissen. */
class DebugReportBuilderTest {
    private val prv = "a1".repeat(64)                    // 64 Byte privater Schlüssel = 128 Hex
    private val pub = "cf3a8206" + "0b".repeat(28)       // 32 Byte Public-Key = 64 Hex (nicht geheim)

    private fun input(
        cli: List<LogLine> = emptyList(), sys: List<LogLine> = emptyList(), msgs: List<String> = emptyList(), secrets: Set<String> = emptySet()
    ) = DebugReportBuilder.Input(
        generatedAt = "2026-11-27 09:12:00 +0100", appVersionName = "1.25", appVersionCode = 125, buildType = "release",
        androidRelease = "14", androidSdk = 34, manufacturer = "Google", model = "Pixel 7",
        appLanguage = "SYSTEM", effectiveLanguage = "de", palette = "BAUHAUS", themeMode = "DARK",
        connectionSummary = "USB-OTG verbunden (USB-Node, 115200 Baud)",
        companion = DebugReportBuilder.CompanionNode(
            connection = "USB-OTG (USB-Node)", name = "Andreas Handy-Node", model = "Heltec V3",
            firmwareVersion = "v1.11.0-6d32193", buildDate = "13 Nov 2026", protocolVersion = 14,
            publicKeyPrefix = "77aa11bb22cc…", radio = "869.618 MHz / 62.5 kHz / SF8 / CR8", txPower = "22 dBm (max. 22 dBm)",
            maxContacts = 350, maxChannels = 40
        ),
        repeater = DebugReportBuilder.TargetRepeater(
            access = "Remote-Admin über Companion-Node", name = "Le Chemin de Tennis (cf3a8206aa55…)", loggedIn = true,
            board = "Wio Tracker L1", radioChip = "SX1262", firmwareVersion = "1.18.2-zephcore-BW1",
            bootloader = "0.9.2-OTAFIX2.2-BP1.3", capabilities = listOf("blacklist" to "true", "whitelist" to "false")
        ),
        messages = msgs, cliLog = cli, systemLog = sys, knownSecrets = secrets
    )

    @Test fun containsAllSections() {
        val r = DebugReportBuilder.build(input(cli = listOf(LogLine("get radio", true), LogLine("> 869.618,62.5,8,8", false)),
            sys = listOf(LogLine("Prüfe Gerät…", false)), msgs = listOf("09:10:01  Backup installiert")))
        for (h in listOf("# meshchōsei Debug-Bericht", "## App & Gerät", "## Companion-Node", "## Ziel-Repeater", "## Meldungen (1)", "## CLI-Protokoll (2 Zeilen)", "## System-Protokoll (1 Zeilen)"))
            assertTrue("fehlt: $h", r.contains(h))
        for (x in listOf("1.25 (125, release)", "Android: 14 (SDK 34)", "Google Pixel 7", "SYSTEM (aktiv: de)", "BAUHAUS / DARK",
                "USB-OTG verbunden (USB-Node, 115200 Baud)", "Le Chemin de Tennis", "Eingeloggt: ja",
                "Wio Tracker L1", "1.18.2-zephcore-BW1", "0.9.2-OTAFIX2.2-BP1.3", "SX1262", "blacklist=true, whitelist=false",
                "get radio", "869.618,62.5,8,8", "Backup installiert"))
            assertTrue("fehlt: $x", r.contains(x))
        assertTrue("Protokolle in Code-Blöcken (Markdown)", r.contains("```\nget radio\n> 869.618,62.5,8,8\n```"))
    }

    @Test fun emptyLogsAndValuesAreMarked() {
        val i = input()
        val r = DebugReportBuilder.build(i.copy(repeater = i.repeater!!.copy(board = "", bootloader = "")))
        assertTrue(r.contains("- Board: —"))
        assertTrue(r.contains("- Bootloader: —"))
        assertTrue(r.contains("```\n(leer)\n```"))
    }

    @Test fun privateKeyIsRedactedEverywhere() {
        val cli = listOf(
            LogLine("get prv.key", true), LogLine("  -> > $prv", false),
            LogLine("set prv.key $prv", true), LogLine("> OK", false)
        )
        val r = DebugReportBuilder.build(input(cli = cli, msgs = listOf("Schlüssel: $prv")))
        assertFalse("privater Schlüssel darf nie erscheinen", r.contains(prv))
        assertFalse(r.contains(prv.take(96)))
        assertTrue(r.contains("set prv.key [REDACTED]"))
        assertTrue("Antwort auf get prv.key geschwärzt, Präfix bleibt", r.contains("  -> > [REDACTED]"))
        assertTrue("normale Antwort bleibt", r.contains("> OK"))
    }

    @Test fun publicKeyStaysVisible() {
        val r = DebugReportBuilder.build(input(cli = listOf(LogLine("get public.key", true), LogLine("> $pub", false))))
        assertTrue("Public-Key (64 Hex) ist nicht geheim", r.contains(pub))
    }

    @Test fun passwordsAreRedacted() {
        val cli = listOf(
            LogLine("password geheim123", true),
            LogLine("set wifi.pass MeinWlan!", true),
            LogLine("set mqtt.broker1.password mq-secret", true),
            LogLine("set guest.password gast77", true),
            LogLine("get wifi.pass", true), LogLine("> MeinWlan!", false),
            LogLine("wifi.pass=MeinWlan!", false)
        )
        val r = DebugReportBuilder.build(input(cli = cli))
        for (secret in listOf("geheim123", "MeinWlan!", "mq-secret", "gast77")) assertFalse("sichtbar: $secret", r.contains(secret))
        assertTrue(r.contains("password [REDACTED]"))
        assertTrue(r.contains("set wifi.pass [REDACTED]"))
        assertTrue(r.contains("set mqtt.broker1.password [REDACTED]"))
        assertTrue(r.contains("wifi.pass=[REDACTED]"))
    }

    /** Gespeicherte Admin-Passwörter werden überall wörtlich geschwärzt – auch in freiem Text. */
    @Test fun knownAdminPasswordsAreRedactedAnywhere() {
        val r = DebugReportBuilder.build(input(
            sys = listOf(LogLine("Login mit TopSecret99 gesendet", false)),
            msgs = listOf("Fehler: TopSecret99 abgelehnt"), secrets = setOf("TopSecret99", "ab")
        ).let { it.copy(repeater = it.repeater!!.copy(name = "Turm TopSecret99")) })
        assertFalse(r.contains("TopSecret99"))
        assertTrue(r.contains("Login mit [REDACTED] gesendet"))
        assertTrue("zu kurze Werte werden nicht blind ersetzt", r.contains("Tennis") || r.contains("Turm"))
    }

    @Test fun sanitizeKeepsHarmlessLines() {
        assertEquals("get radio", DebugReportBuilder.sanitize("get radio"))
        assertEquals("Login abgelehnt (Passwort?)", DebugReportBuilder.sanitize("Login abgelehnt (Passwort?)"))
        assertEquals("get prv.key", DebugReportBuilder.sanitize("get prv.key"))
    }

    @Test fun fileNameIsSafe() {
        assertEquals("meshchosei-debug-20261127-0912.txt", DebugReportBuilder.fileName("20261127-0912"))
        assertEquals("meshchosei-debug-20261127-0912.txt", DebugReportBuilder.fileName("2026/11/27-09:12"))
    }

    /** v1.25: Companion-Node und Ziel-Repeater stehen getrennt und vollständig im Bericht. */
    @Test fun companionAndRepeaterAreSeparateAndComplete() {
        val r = DebugReportBuilder.build(input())
        val comp = r.substringAfter("## Companion-Node").substringBefore("## Ziel-Repeater")
        val rep = r.substringAfter("## Ziel-Repeater").substringBefore("## Meldungen")
        for (x in listOf("Verbindungsart: USB-OTG (USB-Node)", "Node-Name: Andreas Handy-Node", "Board / Modell: Heltec V3",
                "Firmware: MeshCore v1.11.0-6d32193", "Build-Datum: 13 Nov 2026", "Protokollversion: 14", "Public-Key: 77aa11bb22cc…",
                "Funk: 869.618 MHz / 62.5 kHz / SF8 / CR8", "Sendeleistung: 22 dBm (max. 22 dBm)", "Max. Kontakte / Kanäle: 350 / 40"))
            assertTrue("Companion fehlt: $x", comp.contains(x))
        for (x in listOf("Zugang: Remote-Admin über Companion-Node", "Repeater: Le Chemin de Tennis", "Eingeloggt: ja", "Board: Wio Tracker L1",
                "Funkchip: SX1262", "Firmware: ZephCore 1.18.2-zephcore-BW1", "Bootloader: 0.9.2-OTAFIX2.2-BP1.3", "Fähigkeiten: blacklist=true"))
            assertTrue("Repeater fehlt: $x", rep.contains(x))
        assertFalse("Repeater-Board nicht im Companion-Abschnitt", comp.contains("Wio Tracker L1"))
        assertFalse("Companion-Modell nicht im Repeater-Abschnitt", rep.contains("Heltec V3"))
    }

    @Test fun absentCompanionAndRepeaterAreExplained() {
        val direct = DebugReportBuilder.build(input().copy(companion = null, companionAbsent = "Direktverbindung (Repeater direkt am USB, kein Companion-Node)"))
        assertTrue(direct.substringAfter("## Companion-Node").substringBefore("## Ziel-Repeater").contains("Direktverbindung"))
        val none = DebugReportBuilder.build(input().copy(companion = null, repeater = null))
        assertTrue(none.substringAfter("## Companion-Node").substringBefore("## Ziel-Repeater").contains("nicht verbunden"))
        assertTrue(none.substringAfter("## Ziel-Repeater").substringBefore("## Meldungen").contains("nicht verbunden / nicht eingeloggt"))
    }

    @Test fun firmwareTypeFromVersion() {
        assertEquals("ZephCore", DebugReportBuilder.firmwareType("1.18.2-zephcore-BW1"))
        assertEquals("MeshCore", DebugReportBuilder.firmwareType("v1.11.0-6d32193"))
        assertEquals("—", DebugReportBuilder.firmwareType(""))
    }
}
