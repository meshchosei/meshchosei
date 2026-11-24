package com.meshcore.repeater.ui.screens

internal data class CliHelpCommand(
    val syntax: String,
    val insertText: String,
    val description: String,
    val isDangerousOta: Boolean = false
)

internal data class CliHelpCategory(
    val title: String,
    val accentIsRed: Boolean = false,
    val commands: List<CliHelpCommand>
)

internal val cliHelpCategories = listOf(
    CliHelpCategory(
        title = "1. System, Status & Diagnose",
        commands = listOf(
            CliHelpCommand("ver", "ver", "Zeigt Firmware-Version und Build-Datum des Repeaters an."),
            CliHelpCommand("board", "board", "Gibt die erkannte Hardware-Plattform (z. B. Wio Tracker L1) aus."),
            CliHelpCommand("power", "power", "Zeigt Batteriespannung (mV), USB- und Solar-Ladestatus an."),
            CliHelpCommand("clock", "clock", "Liest die aktuelle interne UTC-Systemzeit des Repeaters aus."),
            CliHelpCommand("clock sync", "clock sync", "Synchronisiert die Repeater-Uhrzeit mit dem Companion."),
            CliHelpCommand("clear stats", "clear stats", "Setzt alle gesammelten Funk- und Paket-Zähler auf null zurück."),
            CliHelpCommand("set adc.multiplier <faktor>", "set adc.multiplier ", "Kalibriert den Spannungsteiler-Faktor für die Akku-Messung."),
            CliHelpCommand("reboot", "reboot", "Startet den Repeater sofort neu (speichert alle Einstellungen)."),
            CliHelpCommand("poweroff", "poweroff", "Schaltet den Repeater aus (nur per Taster/Reset wieder weckbar)."),
            CliHelpCommand("start dfu", "start dfu", "Startet den USB-Bootloader/DFU-Modus für Firmware-Updates."),
            CliHelpCommand("erase", "erase", "Setzt Flash-Konfiguration zurück (Vorsicht: Werkseinstellungen!).")
        )
    ),
    CliHelpCategory(
        title = "2. Grundeinstellungen & Funk",
        commands = listOf(
            CliHelpCommand("get radio", "get radio", "Zeigt Frequenz (MHz), Bandbreite (kHz), SF und Coding-Rate an."),
            CliHelpCommand("set radio <freq>,<bw>,<sf>,<cr>", "set radio 869.618,62.5,8,8", "Setzt alle LoRa-Funkparameter (EU Narrow: 869.618,62.5,8,8)."),
            CliHelpCommand("tempradio <freq>,<bw>,<sf>,<cr>,<min>", "tempradio 869.618,62.5,8,8,5", "Testet Funkparameter temporär mit automatischem Fallback."),
            CliHelpCommand("get tx", "get tx", "Zeigt die aktuell eingestellte Sendeleistung in dBm an."),
            CliHelpCommand("set tx <dbm>", "set tx 22", "Setzt die LoRa-Sendeleistung in dBm (EU-Limit beachten)."),
            CliHelpCommand("get af", "get af", "Liest den Airtime-Faktor für die Sendezeit-Begrenzung aus."),
            CliHelpCommand("set af <faktor>", "set af ", "Setzt den Airtime-Faktor für die Sendezeit-Begrenzung."),
            CliHelpCommand("set dutycycle <prozent>", "set dutycycle 10", "Begrenzt den Sende-Duty-Cycle in Prozent (EU 869.4 MHz: 10%)."),
            CliHelpCommand("get name", "get name", "Liest den aktuellen Mesh-Namen des Repeaters aus."),
            CliHelpCommand("set name <name>", "set name ", "Ändert den sichtbaren Mesh-Namen des Repeaters."),
            CliHelpCommand("get lat", "get lat", "Liest den gespeicherten Breitengrad (Latitude) aus."),
            CliHelpCommand("set lat <breite>", "set lat ", "Setzt den Breitengrad (Latitude) für die Mesh-Karte."),
            CliHelpCommand("get lon", "get lon", "Liest den gespeicherten Längengrad (Longitude) aus."),
            CliHelpCommand("set lon <länge>", "set lon ", "Setzt den Längengrad (Longitude) für die Mesh-Karte."),
            CliHelpCommand("password <neues_pw>", "password ", "Ändert das Admin-Passwort (max. 15 Zeichen)."),
            CliHelpCommand("get guest.password", "get guest.password", "Liest das gesetzte Lese-/Gast-Passwort aus."),
            CliHelpCommand("set guest.password <pw>", "set guest.password ", "Setzt das Lese-/Gast-Passwort für Statusabfragen."),
            CliHelpCommand("get allow.read.only", "get allow.read.only", "Zeigt an, ob lesender Gast-Zugriff ohne Passwort aktiv ist."),
            CliHelpCommand("set allow.read.only <on|off>", "set allow.read.only on", "Erlaubt oder sperrt lesenden Gast-Zugriff ohne Admin-Passwort."),
            CliHelpCommand("get repeat", "get repeat", "Zeigt an, ob die Paket-Weiterleitung (Repeater) aktiv ist."),
            CliHelpCommand("set repeat <on|off>", "set repeat on", "Aktiviert oder pausiert die Paket-Weiterleitung."),
            CliHelpCommand("get txdelay", "get txdelay", "Liest die Sendeverzögerung bei Flood-Paketen aus."),
            CliHelpCommand("set txdelay <sek>", "set txdelay ", "Setzt die Sendeverzögerung bei Flood-Paketen."),
            CliHelpCommand("get direct.txdelay", "get direct.txdelay", "Liest die Sendeverzögerung bei direkten Paketen aus."),
            CliHelpCommand("set direct.txdelay <sek>", "set direct.txdelay ", "Setzt die Sendeverzögerung bei direkten Paketen."),
            CliHelpCommand("get rxdelay", "get rxdelay", "Liest die Empfangs-Verzögerung (RX-Timing) aus."),
            CliHelpCommand("set rxdelay <sek>", "set rxdelay ", "Setzt die Empfangs-Verzögerung (RX-Timing)."),
            CliHelpCommand("get multi.acks", "get multi.acks", "Zeigt den Status der mehrfachen Empfangsbestätigungen an."),
            CliHelpCommand("set multi.acks <0|1>", "set multi.acks 1", "Aktiviert (1) oder deaktiviert (0) mehrfache ACKs."),
            CliHelpCommand("powersaving on", "powersaving on", "Aktiviert den Tiefschlafmodus zwischen Funkaktivitäten."),
            CliHelpCommand("powersaving off", "powersaving off", "Deaktiviert den Tiefschlafmodus (Dauerbetrieb).")
        )
    ),
    CliHelpCategory(
        title = "3. Netzwerk, Adverts & Hardware",
        commands = listOf(
            CliHelpCommand("advert", "advert", "Sendet sofort ein Mesh-Advert-Paket zur Bekanntmachung aus."),
            CliHelpCommand("advert.zerohop", "advert.zerohop", "Sendet ein lokales Zero-Hop-Advert ohne Weiterleitung."),
            CliHelpCommand("get advert.interval", "get advert.interval", "Liest das lokale Advert-Intervall in Minuten aus."),
            CliHelpCommand("set advert.interval <min>", "set advert.interval 120", "Setzt das lokale Advert-Intervall in Minuten (60–240)."),
            CliHelpCommand("get flood.advert.interval", "get flood.advert.interval", "Liest das Intervall für netzweite Flood-Adverts (Std) aus."),
            CliHelpCommand("set flood.advert.interval <std>", "set flood.advert.interval 24", "Setzt das Flood-Advert-Intervall in Stunden."),
            CliHelpCommand("get flood.max", "get flood.max", "Liest das maximale Hop-Limit für Flood-Pakete aus."),
            CliHelpCommand("set flood.max <hops>", "set flood.max 64", "Setzt das maximale Hop-Limit für Flood-Pakete."),
            CliHelpCommand("get path.hash.mode", "get path.hash.mode", "Liest die eingestellte Pfad-Hash-Breite (0=1B, 1=2B, 2=3B) aus."),
            CliHelpCommand("set path.hash.mode <0..2>", "set path.hash.mode 1", "Setzt die Pfad-Hash-Breite (1-, 2- oder 3-Byte Hashes)."),
            CliHelpCommand("get loop.detect", "get loop.detect", "Liest die Stufe der Schleifen-Erkennung aus."),
            CliHelpCommand("set loop.detect <off|minimal|moderate|strict>", "set loop.detect moderate", "Steuert die Schleifen-Erkennung gegen Paket-Duplikate."),
            CliHelpCommand("region", "region", "Zeigt die aktuell aktive Flood-Region (Scope) an."),
            CliHelpCommand("region list", "region list", "Listet alle konfigurierten Flood-Regionen auf."),
            CliHelpCommand("region set <name>", "region set ", "Wechselt die aktive Flood-Region für das Routing."),
            CliHelpCommand("neighbors", "neighbors", "Listet direkt empfangene Nachbar-Repeater mit SNR/RSSI auf."),
            CliHelpCommand("discover.neighbors", "discover.neighbors", "Startet eine aktive Suche nach umliegenden Repeatern."),
            CliHelpCommand("get leds", "get leds", "Liest den aktuellen Betriebsmodus der Status-LEDs aus."),
            CliHelpCommand("set leds <on|off|boot>", "set leds on", "Steuert das Verhalten der Status-LEDs."),
            CliHelpCommand("get gps", "get gps", "Zeigt den aktuellen GPS-Fix-Status und Koordinaten an."),
            CliHelpCommand("gps on", "gps on", "Schaltet das interne GPS-Modul ein."),
            CliHelpCommand("gps off", "gps off", "Schaltet das interne GPS-Modul aus (spart Strom)."),
            CliHelpCommand("gps setloc", "gps setloc", "Übernimmt die aktuelle GPS-Position als festen Standort."),
            CliHelpCommand("gps advert share", "gps advert share", "Teilt die Live-GPS-Position direkt in Adverts."),
            CliHelpCommand("gps advert none", "gps advert none", "Deaktiviert das Mitsenden der Live-GPS-Position in Adverts."),
            CliHelpCommand("get bridge.type", "get bridge.type", "Liest den eingestellten Bridge-Typ (ESP-NOW/Serial) aus."),
            CliHelpCommand("set bridge.type <typ>", "set bridge.type ", "Setzt den Bridge-Typ für die Kopplung."),
            CliHelpCommand("get bridge.enabled", "get bridge.enabled", "Zeigt an, ob die Bridge-Kopplung aktiv ist."),
            CliHelpCommand("set bridge.enabled <on|off>", "set bridge.enabled on", "Aktiviert oder deaktiviert die Bridge-Kopplung."),
            CliHelpCommand("get bridge.baud", "get bridge.baud", "Liest die Baudrate für die serielle Bridge aus."),
            CliHelpCommand("set bridge.baud <rate>", "set bridge.baud 115200", "Setzt die Baudrate für die serielle Bridge."),
            CliHelpCommand("get bridge.channel", "get bridge.channel", "Liest den Funkkanal für eine ESP-NOW-Bridge aus."),
            CliHelpCommand("set bridge.channel <ch>", "set bridge.channel ", "Setzt den Funkkanal für eine ESP-NOW-Bridge.")
        )
    ),
    CliHelpCategory(
        title = "4. Filter – Blacklist (DMC)",
        commands = listOf(
            CliHelpCommand("filter", "filter", "Zeigt Filter-Status (on/off), Modus und Block-Zähler an."),
            CliHelpCommand("filter stats", "filter stats", "Zeigt detaillierte Statistiken aller blockierten Kategorien."),
            CliHelpCommand("filter count", "filter count", "Gibt die Gesamtzahl aktiver Filterregeln und Treffer aus."),
            CliHelpCommand("filter on", "filter on", "Schaltet die Paket-Filterung aktiv ein."),
            CliHelpCommand("filter off", "filter off", "Schaltet die Paket-Filterung aus."),
            CliHelpCommand("filter mode blacklist", "filter mode blacklist", "Aktiviert den klassischen DMC-Blacklist-Modus."),
            CliHelpCommand("filter dryrun on", "filter dryrun on", "Simuliert Filterregeln im Log, ohne Pakete zu verwerfen."),
            CliHelpCommand("filter dryrun off", "filter dryrun off", "Beendet den Simulationsmodus (Filter verwirft wieder aktiv)."),
            CliHelpCommand("filter types <mask>", "filter types ", "Filtert gezielt nach MeshCore-Pakettypen."),
            CliHelpCommand("filter channel list", "filter channel list", "Listet alle geblockten Hashtag-Kanäle auf."),
            CliHelpCommand("filter channel add <#kanal>", "filter channel add #", "Fügt einen Kanal zur Blacklist hinzu."),
            CliHelpCommand("filter channel remove <#kanal>", "filter channel remove #", "Entfernt einen Kanal wieder von der Blacklist."),
            CliHelpCommand("filter path list", "filter path list", "Listet geblockte Node-ID-/Pfad-Präfixe auf."),
            CliHelpCommand("filter path add <hex>", "filter path add ", "Blockiert einen Hex-Pfadpräfix auf der Blacklist."),
            CliHelpCommand("filter path remove <hex>", "filter path remove ", "Gibt einen geblockten Hex-Pfadpräfix wieder frei."),
            CliHelpCommand("filter hash <on|off>", "filter hash ", "Prüft oder blockiert Pakete mit kollidierenden Hashes."),
            CliHelpCommand("filter malformed <on|off>", "filter malformed ", "Verwirft fehlerhaft aufgebaute oder beschädigte Pakete."),
            CliHelpCommand("filter advert <regeln>", "filter advert ", "Steuert die Filterung und Ratenbegrenzung von Adverts."),
            CliHelpCommand("filter rate <limit>", "filter rate ", "Begrenzt die maximale Paketrate (Flood-Protection)."),
            CliHelpCommand("filter sender list", "filter sender list", "Listet alle blockierten Absendermuster auf."),
            CliHelpCommand("filter sender add <muster>", "filter sender add ", "Blockiert Nachrichten bestimmter Absendernamen."),
            CliHelpCommand("filter sender remove <muster>", "filter sender remove ", "Entfernt ein Absendermuster von der Blacklist."),
            CliHelpCommand("filter text list", "filter text list", "Listet alle blockierten Textmuster auf."),
            CliHelpCommand("filter text add <wort>", "filter text add ", "Blockiert Nachrichten mit bestimmten Textinhalten."),
            CliHelpCommand("filter text remove <wort>", "filter text remove ", "Entfernt ein Textmuster von der Blacklist."),
            CliHelpCommand("filter watch list", "filter watch list", "Listet alle aktiv beobachteten Kanäle/Absender auf."),
            CliHelpCommand("filter watch add <name>", "filter watch add ", "Beobachtet gezielt Pakete eines Absenders oder Kanals."),
            CliHelpCommand("filter watch remove <name>", "filter watch remove ", "Beendet die Beobachtung eines Absenders/Kanals."),
            CliHelpCommand("filter hops <typ> <max>", "filter hops ", "Begrenzt maximale Hops pro Pakettyp im Filter."),
            CliHelpCommand("filter age <minuten>", "filter age 60", "Verwirft Pakete, die älter als X Minuten sind."),
            CliHelpCommand("filter RESET", "filter RESET", "Löscht alle Filter- und Whitelist-Regeln unwiderruflich.")
        )
    ),
    CliHelpCategory(
        title = "5. Filter – Whitelist (Custom)",
        commands = listOf(
            CliHelpCommand("filter mode whitelist", "filter mode whitelist", "Aktiviert den strengen Whitelist-Modus."),
            CliHelpCommand("filter wl channel list", "filter wl channel list", "Listet alle erlaubten Kanäle in der Whitelist auf."),
            CliHelpCommand("filter wl channel add <#kanal>", "filter wl channel add #", "Erlaubt einen Kanal im Whitelist-Modus."),
            CliHelpCommand("filter wl channel del <#kanal>", "filter wl channel del #", "Entfernt einen Kanal aus der Whitelist."),
            CliHelpCommand("filter wl path list", "filter wl path list", "Listet alle erlaubten Pfad-Präfixe in der Whitelist auf."),
            CliHelpCommand("filter wl path add <hex>", "filter wl path add ", "Erlaubt einen Hex-Pfadpräfix in der Whitelist."),
            CliHelpCommand("filter wl path del <hex>", "filter wl path del ", "Entfernt einen Hex-Pfadpräfix aus der Whitelist.")
        )
    ),
    CliHelpCategory(
        title = "6. Wartung & OTA-Update",
        accentIsRed = true,
        commands = listOf(
            CliHelpCommand("start ota", "start ota", "Startet den Over-The-Air Firmware-Update-Modus (WiFi/BLE OTA).", isDangerousOta = true),
            CliHelpCommand("stop ota", "stop ota", "Beendet einen laufenden OTA-Update-Modus vorzeitig.")
        )
    )
)
