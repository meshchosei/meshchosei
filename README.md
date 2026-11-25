# meshchōsei

**meshchōsei** (調整, jap. „Abstimmung, Feineinstellung“) ist eine native Android-App zur Konfiguration und Wartung von **MeshCore**- und **ZephCore**-LoRa-Repeatern, direkt per **USB-OTG** oder drahtlos über einen **Companion-Node** (BLE/USB) mit **Remote-Admin über LoRa**.

> *English summary:* meshchōsei is a native Android app (Kotlin, Jetpack Compose) for configuring and maintaining MeshCore and ZephCore LoRa repeaters via USB-OTG or remotely over LoRa through a companion node. UI in German, English, Dutch and French. Licensed under GNU GPLv3.

Oberfläche in **Deutsch, Englisch, Niederländisch und Französisch**, umschaltbar ohne Neustart.

---

## Hauptfunktionen

| Bereich | Was die App kann |
|---|---|
| **Verbindung** | USB-Serial-OTG (CH340, CP210x, FTDI, nRF52-CDC, ESP32, RP2040 …) oder Companion-Node per BLE/USB; Remote-Admin-Login auf einen Repeater im Mesh. Automatische Erkennung von Rolle (Repeater/Companion), Firmware und Funktionsumfang. |
| **Start-Scan** | Ein streng sequentieller Durchlauf liest Hardware, Funk, Identität, Routing, Filter und Regionen einmal vollständig ein; jede Antwort wird geprüft und eindeutig ihrem Befehl zugeordnet (über LoRa mit `XX\|`-Antwort-Tags). |
| **Mesh-Schonmodus** | Senkt für Wartungsarbeiten die Sendeleistung temporär (`tempradio`); die Firmware stellt nach Ablauf selbstständig wieder her, auch wenn die App die Verbindung verliert. |
| **Hardware & Antenne** | Board-Erkennung, Antennen- und Kabeldämpfungs-Presets, ERP/EIRP-Berechnung, Versorgung (Akku/USB/Solar), Status-LEDs. |
| **Funk & Region** | LoRa-Parameter (Frequenz, Bandbreite, SF, CR), TX-Leistung und Duty-Cycle mit Länder-Presets und Compliance-Grenzen. |
| **Identität & Standort** | Name, Besitzer-Info, Koordinaten auf OpenStreetMap-Karte, Admin-/Gast-Passwort, Schlüssel. |
| **Routing & Adverts** | Weiterleitung, Pfad-Hash-Modus, Schleifen-Erkennung, Advert-Intervalle, Multi-ACKs sowie die komplette **Regionen-Verwaltung** (`region home/default/put/remove/allowf/denyf`). |
| **Paket-Filter** | Blacklist (Dutch-MeshCore-Filter) und Whitelist: Kanäle, Pfad-Präfixe, Absender, Texte, Hops, Paketalter, Dry-Run. `filter RESET` ist durch Slide-to-Confirm geschützt. |
| **CLI-Konsole** | Freie Befehlseingabe mit vollständiger, übersetzter Befehlsübersicht; über LoRa ein automatischer Retry und klare Timeout-Meldung. |
| **Backup & Wiederherstellung** | JSON-Backup aller Variablen, Regionen und Filterlisten; Wiederherstellung per Abgleich (nur Abweichungen werden gesendet). |

Alle Änderungen gelten erst als gespeichert, wenn der Repeater sie **bestätigt** hat. Speichern-Buttons zeigen jederzeit, ob ungespeicherte Änderungen vorliegen.

---

## Unterstützte Firmwares

| Firmware | Unterstützung |
|---|---|
| **MeshCore** (Repeater, Companion) | vollständig: CLI-Variablen, Regionen, Remote-Admin über Companion |
| **ZephCore** (Repeater, Companion) | vollständig, zusätzlich Paket-Filter (Blacklist/Whitelist) und LED-Steuerung |
| **Dutch MeshCore** (Filter) | Blacklist-Filterbefehle (DMC) |

Das Verhalten der App wird laufend gegen die Quelltexte von MeshCore und ZephCore geprüft. Wo eine Anforderung der Firmware widerspricht, folgt die App der Firmware.

## Unterstützte Boards

Erkannt und mit passenden Hardware-Grenzen hinterlegt:

- Heltec WiFi LoRa 32 V3 / V3.2
- RAK4631 / WisBlock
- Seeed XIAO nRF52840 / ESP32-S3 + Wio-SX1262
- Seeed SenseCAP Solar Node P1 / P1-Pro
- Seeed Wio Tracker L1
- LilyGO T-Beam / T-Beam Supreme
- LilyGO LoRa32 T3-S3
- Elecrow ThinkNode M1 / M2
- B&Q Station G2 (SX1262 + PA)
- sowie jedes andere SX1262-Board (generisches Profil)

---

## Bauhaus-UI

Die Oberfläche folgt einer strengen, an das Bauhaus angelehnten Formensprache (Details in [`STYLE_GUIDE.md`](STYLE_GUIDE.md)):

- **Farben:** Signalrot (Primäraktionen), Kobaltblau (Information), Ocker (Warnung) auf Carbon/Off-White, mit Hell- und Dunkelmodus.
- **Formen:** einheitlich `BauhausCornerShape` (2 dp): Karten, Buttons, Dialoge, Badges und eckige Schalter. Einzige runde Ausnahme ist das USB/BT-Verbindungs-Badge.
- **Bedienung:** genau ein roter Speichern-Button pro Karte, kurze einzeilige Button-Beschriftungen, Braille-Spinner für laufende Vorgänge, Scroll-Hinweise bei überlaufendem Inhalt.

---

## Bauen

Voraussetzungen: JDK 17, Android SDK 34 (minSdk 26 / Android 8.0).

```bash
./gradlew testDebugUnitTest   # Unit-Tests
./gradlew assembleDebug       # Debug-APK: app/build/outputs/apk/debug/app-debug.apk
```

Weitere Dokumente: [`ROADMAP.md`](ROADMAP.md) (Versionen), [`STYLE_GUIDE.md`](STYLE_GUIDE.md) (Design- und Protokollregeln), [`PRIVACY.md`](PRIVACY.md) (Datenschutz).

---

## Hinweis

Diese Software wurde mit KI-Unterstützung entwickelt und wird **ohne jegliche Gewährleistung** bereitgestellt. Du bist selbst dafür verantwortlich, die in deinem Land geltenden Funkvorschriften einzuhalten (in der EU: ETSI EN 300 220 und nationale Frequenzpläne).

## Lizenz

meshchōsei ist freie Software unter der **GNU General Public License v3.0 (GPLv3)**. Du darfst sie nutzen, untersuchen, verändern und unter denselben Bedingungen weitergeben.

Verwendete Bibliotheken: Jetpack Compose & Material 3 (Apache 2.0), usb-serial-for-android (MIT). Kartendaten © OpenStreetMap-Mitwirkende (ODbL).

🄯2026 AHGH
