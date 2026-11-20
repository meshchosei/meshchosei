# Repeater Configurator (Android App)

Native Android-App zur professionellen Konfiguration und Überwachung von **MeshCore-** und **ZephCore-Repeatern** (inkl. ZephCore BW2 Packet-Filter & Whitelist) über **USB-Serial OTG** oder **BLE-Companion Remote-Admin**.

Entwickelt mit **Kotlin**, **Jetpack Compose** und **Material 3**.

---

## 🚀 Kernfunktionen & Architektur

### 1. Navigation Drawer (Reinwisch-Seitenmenü)
- **Strukturierte Bereiche:** Kein endloses Scrollen – direkter Wechsel zwischen den 5 Funktionsmodulen.
- **Echtzeit-Badges:** Zeigt im Menü direkt an, ob der **Mesh-Schonmodus aktiv ist (mit Restzeit-Countdown)**, ob der **Whitelist-Modus** scharfgeschaltet ist und wie der Verbindungsstatus lautet.

---

### 2. Die 5 Hauptbereiche

#### 🔌 1. Verbindung (USB-Serial OTG & BLE-Companion)
- **USB-OTG:** Erkennt automatisch angebundene USB-UART-Chips (CH340, CP210x, FTDI, Nordic nRF52 CDC-ACM, ESP32, RP2040 usw.) mit wählbarer Baudrate (Default: 115200).
- **BLE-Companion:** Drahtlose Kopplung für Remote-Administration über Nordic UART Service (NUS) mit Live-RSSI-Feldstärkeanzeige.
- **Integrierte Konsole:** Live-Terminal für eingehende und ausgehende CLI-Nachrichten sowie Schnelleingabe für manuelle Befehle (`ver`, `filter`, `telemetry`).

#### 📡 2. Funk, Land & Antenne (ERP/EIRP-Compliance)
- **Ländererkennung & Presets:**
  - Automatische Erkennung via Geräte-Locale (DE/EU868 Standard: 868.125 MHz, BW 250 kHz, SF 11, CR 4/5, max. 14 dBm ERP).
  - Presets für EU869 High-Power (869.525 MHz bis 27 dBm / 500 mW ERP), US915, UK868 und benutzerdefinierte Frequenzen.
- **Antennen-Presets:**
  - Stock-Antenne (~2.0 dBi)
  - RAK Glasfaser-Omni (5.8 dBi)
  - Allnet High-Gain (7.3 dBi)
  - High-Gain / Yagi (12.0 dBi)
  - Unbekannt (+3.0 dBi konservativer Sicherheitszuschlag)
  - Eigene Antenne (freie dBi-Eingabe) inkl. Kabel- und Pigtaildämpfung in dB.
- **Automatische ERP/EIRP-Berechnung:**
  $$\text{EIRP} = P_{\text{TX}} + G_{\text{Antenne}} - L_{\text{Kabel}}$$
  $$\text{ERP} = \text{EIRP} - 2.15\,\text{dB}$$
  $$\text{P}_{\text{TX,max}} = \text{Limit}_{\text{ERP}} + 2.15 - G_{\text{Antenne}} + L_{\text{Kabel}}$$
- **Compliance-Sperre & Warnung:**
  - Automatische Begrenzung der TX-Sendeleistung auf das gesetzliche Limit.
  - Überschreitungen werden optisch gewarnt und sind standardmäßig gesperrt.
  - Freigabe nur über einen bewussten Sicherheits-Sperrschalter (z. B. für lizenzierte Funkamateure oder Testumgebungen).

#### 🛡️ 3. Filter & Whitelist (ZephCore BW2)
- **Filter-Steuerung:** Hauptschalter (`filter on`/`filter off`) und Dry-Run-Testmodus (`filter dryrun on`/`filter dryrun off`).
- **Modus-Umschaltung:**
  - `filter mode blacklist`: Standardmodus (alles wird weitergeleitet, außer blockierte Kanäle/Pfade).
  - `filter mode whitelist`: Strenger Modus (alles Weiterleiten blockiert, außer freigegebene Kanäle & Pfad-Präfixe).
- **Kanal-Manager:** Schnellauswahl (`Public`, `#mesh-de`, `#notfunk`, `#test`) und Eingabefeld für `#kanalname` oder Hex-Hashes (`filter wl channel add/del`).
- **Pfad-Präfixe:** Verwaltung von 2–8 Hex-Zeichen (`filter wl path add/del`).
- **Statistiken:** Anzeige von durchgelassenen Paketen, verworfenen Paketen, Droprate in % und eingesparter Airtime.
- **Geschützter Slide-to-Confirm:** Großbuchstaben-Schutzbefehl `filter RESET` ist gegen versehentliches Auslösen durch einen Wisch-Schieberegler geschützt.

#### ⚡ 4. Mesh-Schonmodus (Auto-Revert Watchdog)
- **Zweck:** Drosselt bei Wartungsarbeiten oder Remote-Administration über das Mesh temporär die Sendeleistung des Transceivers (z. B. auf 2 oder 5 dBm), um das umgebende LoRa-Mesh nicht durch Konfigurations-Bursts zu überlasten.
- **Fail-Safe Watchdog (Firmware + App):**
  - Einstellbar auf 5 oder 10 Minuten.
  - Live-Countdown und Fortschrittsbalken in der App mit "Watchdog verlängern"-Taste (Keep-Alive).
  - Nutzt den `tempradio`-Mechanismus in ZephCore: Reißt die Funkverbindung ab oder wird die App geschlossen, stellt der autonome Firmware-Watchdog nach Ablauf der Frist automatisch die gespeicherte reguläre Sendeleistung wieder her.

#### 🔋 5. Hardware & Status
- **Batterie- & Solar-Telemetrie:**
  - Batteriespannung in V, Ladestand in %, Ladestatus-Anzeige.
  - Solarpanel-Spannung (V), Ladestrom (mA) und berechnete Solarleistung in Watt ($P = U \times I$).
  - Batterietemperatur in °C.
- **LED-Steuerung:** Normaler Betrieb (`set leds on`), Stealth-Modus für getarnte Outdoor-Standorte (`set leds off`) und Boot-Blinken (2x 150 ms bei Neustart).
- **GPS-Modus:** Aus, Intervall (15 Min / 60 Min) oder Always-On.
- **Node-Befehle:** Konfiguration im Flash sichern (`save`), Node neu starten (`reboot`) mit Bestätigungsabfrage.

---

## 🛠️ Bauen & Ausführen

### Voraussetzungen
- **Android Studio** (Hedgehog, Iguana, Jellyfish oder neuer)
- **JDK 17**
- **Android SDK API 34** (mindestens API 26 / Android 8.0)

### Debug-APK über die Konsole bauen
```bash
cd RepeaterConfigurator
./gradlew assembleDebug
```
Die erzeugte APK liegt anschließend unter:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📂 Projektstruktur
```
RepeaterConfigurator/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/meshcore/repeater/
│       │   ├── MainActivity.kt
│       │   ├── model/
│       │   │   ├── ConnectionModel.kt
│       │   │   ├── RadioAntennaModel.kt
│       │   │   ├── FilterModel.kt
│       │   │   ├── MeshCareModel.kt
│       │   │   └── HardwareModel.kt
│       │   ├── service/
│       │   │   ├── RepeaterProtocol.kt
│       │   │   ├── SerialConnectionManager.kt
│       │   │   └── BleConnectionManager.kt
│       │   ├── viewmodel/
│       │   │   └── RepeaterViewModel.kt
│       │   └── ui/
│       │       ├── components/
│       │       │   ├── AppDrawer.kt
│       │       │   ├── TopBar.kt
│       │       │   └── SlideToConfirm.kt
│       │       ├── navigation/
│       │       │   └── NavDrawerItem.kt
│       │       ├── screens/
│       │       │   ├── ConnectionScreen.kt
│       │       │   ├── RadioAntennaScreen.kt
│       │       │   ├── FilterScreen.kt
│       │       │   ├── MeshCareScreen.kt
│       │       │   └── HardwareStatusScreen.kt
│       │       └── theme/
│       │           ├── Color.kt
│       │           ├── Theme.kt
│       │           └── Type.kt
│       └── res/
│           ├── drawable/
│           ├── mipmap-anydpi-v26/
│           ├── values/
│           └── xml/
│               └── usb_device_filter.xml
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
│       └── gradle-wrapper.properties
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
└── gradlew.bat
```
