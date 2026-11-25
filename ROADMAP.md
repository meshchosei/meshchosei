# ROADMAP – meshchōsei

Stand: 2026-11-25 · aktuelle Version: 1.06

| Version | Status | Inhalt |
|---|---|---|
| **1.00** | ✅ veröffentlicht (Tag `v1.00`) | Erste stabile Version |
| **1.01** | ✅ veröffentlicht (Tag `v1.01`) | Regionen-Verwaltung (region …), Button-Arbeitszustand & Doppelklick-Sperre, zuverlässige Filter-Kanallisten über LoRa, LED-„boot“-Hinweis |
| **1.02** | ✅ veröffentlicht (Tag `v1.02`) | JSON-Backup mit `"filter"`-Block (Modus, Blacklist, Whitelist), Wiederherstellung per Abgleich |
| **1.03** | ✅ veröffentlicht (Tag `v1.03`) | Kompakte Regionen-Karte mit eckigen [HOME]/[DEF]/[FLOOD]-Schaltern, Backup-Export liest Regionen & Filter zuerst vollständig ein |
| **1.04** | ✅ veröffentlicht (Tag `v1.04`) | UI-Vereinheitlichung: eckiger `BauhausSwitch` statt Material-Switch, Status-LEDs im Hardware-Screen, einheitlicher `RefreshButton` |
| **1.05** | ✅ veröffentlicht (Tag `v1.05`) | Seitenmenü: Flagge der tatsächlich aktiven Sprache auch bei Systemsprache hervorgehoben |
| **1.06** | ✅ veröffentlicht (Tag `v1.06`) | Audit-Fixes: Filter-Dry-Run/Max-Hops/Alter/RESET erst nach Bestätigung, Overlay-Abbruch trifft laufenden Job, Regionen-Anzeige bleibt bei Backup-Fehler, Alt-Screens entfernt |
| **1.1** | 📋 geplant | CLI-Autocomplete (F1), BLE-OTA-Firmware-Update (F2), Live-Repeater-Suche (F3), Präfix-Filter Repeater-Auswahl (F4) |

Beide Features folgen den Regeln aus [`STYLE_GUIDE.md`](STYLE_GUIDE.md):
- Bauhaus-Design;
- streng sequentielle Befehle und kein Polling;
- Trennung von CLI und SYSTEM;
- alle Texte in DE, EN, NL und FR.

---

## F1 – CLI-Befehls-Autocomplete (`CliConsoleScreen`)

### Ziel
Beim Tippen im CLI-Eingabefeld erscheinen live passende MeshCore-/ZephCore-Befehle. Ein Antippen übernimmt den Befehl ins Feld.

### Anforderungen
1. **Live-Vorschlagsliste** direkt **über** dem CLI-Eingabefeld, ab dem **ersten** getippten Zeichen.
2. **Filterung** aller bekannten MeshCore-/ZephCore-CLI-Befehle nach der aktuellen Eingabe:
   - zuerst Präfix-Treffer (`get r` → `get radio`, `get repeat`, `get rxdelay`);
   - danach Treffer innerhalb des Befehls (`radio` → `get radio`, `set radio …`, `tempradio …`).
3. Jeder Vorschlag zeigt:
   - die **Parameter-Signatur** in Monospace, zum Beispiel `set radio <freq>,<bw>,<sf>,<cr>`;
   - eine **Kurzbeschreibung** in einer Zeile, 11 sp, `onSurfaceVariant`.
4. **Antippen übernimmt** den Befehl, und zwar den Text ohne Platzhalter: `set radio `, der Cursor steht am Ende.
   - **Wichtig:** Es wird **nichts automatisch gesendet**. Gesendet wird weiterhin nur über den roten Senden-Button.
5. Die Liste schließt sich:
   - bei leerem Feld;
   - nach dem Senden;
   - per Zurück-Taste;
   - wenn die Eingabe exakt einem Befehl ohne weitere Parameter entspricht.

### Umsetzungsskizze
- **Datenquelle:** Die vorhandene `ui/screens/CliHelpData.kt` mit `CliHelpCommand(syntax, insertText, description, isDangerousOta)` wird wiederverwendet und **nicht dupliziert**.
  - Die Befehle bleiben dort zentral gepflegt.
  - Jeder Befehl wird vor der Aufnahme gegen `~/meshcore-dev/MeshCore/src/helpers/CommonCLI.cpp` und `~/meshcore-dev/ZephCore/zephcore/helpers/CommonCLI.cpp` (sowie `Filter.cpp`) geprüft. Befehle, die es nicht gibt, kommen nicht hinein.
- **Logik:** Reine, unit-getestete Funktion `CliAutocomplete.suggest(input, commands, limit = 6)` in `service/` oder `model/`, mit dem Ranking Präfix → Wortanfang → enthalten.
- **Kontextfilter** nach `DeviceCapabilities`:
  - Filter-Befehle nur bei `supportsBlacklist`;
  - `filter wl …` und `filter mode …` nur bei `supportsWhitelist`.
- **UI:**
  - `Card` mit `BauhausCornerShape` und 1-dp-Outline, höchstens 4–5 sichtbare Einträge, scrollbar.
  - Sitzt über dem Eingabefeld und wandert mit `imePadding` über der Tastatur mit.
  - Kritische Befehle (`isDangerousOta`, `reboot`, `erase`, `set prv.key`) sind mit Signalrot und ⚠ markiert.
- **Log-Trennung bleibt:** Ein übernommener Befehl ist ein manueller CLI-Befehl und landet im **CLI**-Tab.

### Abnahme
- [ ] Vorschläge ab dem 1. Zeichen, ohne Verzögerung, auch bei laufender Abfrage.
- [ ] Ranking per Unit-Test abgesichert (Präfix vor Teiltreffer, keine Duplikate, Groß-/Kleinschreibung egal).
- [ ] Antippen übernimmt nur, gesendet wird erst per Button.
- [ ] Kurzbeschreibungen in allen 4 Sprachen. Signaturen bleiben unübersetzt.
- [ ] Bedienbar bei offener Tastatur auf 360 dp Breite.

---

## F2 – Integriertes BLE-OTA-Firmware-Update (Nordic DFU)

### Ziel
Ein geführtes Firmware-Update für nRF52-Repeater per Bluetooth, direkt aus der App, ohne separate Nordic-App.

### Firmware-Fakten (geprüft am Quellcode, Stand rc13)
- Der Befehl heißt **`start ota`** (nicht `ota start`):
  - MeshCore `CommonCLI.cpp`: `memcmp(command, "start ota", 9)`;
  - ZephCore `CommonCLI.cpp`: ebenso.
- **Nordic-DFU-fähig sind nur nRF52-Boards:**
  - MeshCore `NRF52Board::startOTAUpdate()` startet den Adafruit-BLE-DFU-Dienst (`bledfu`).
  - ZephCore startet nRF52 neu in den BLE-OTA-DFU-Modus und antwortet mit `"OK - rebooting to BLE OTA DFU"`.
- **ESP32 ist anders und mit Nordic DFU nicht kompatibel:**
  - MeshCore-ESP32 startet einen WLAN-AP mit HTTP-OTA.
  - ZephCore-ESP32 startet je nach Build WLAN-OTA (`Started: http://…/update`) oder MCUmgr-over-BLE (`BLE OTA open for … s`).
  - Die App muss diese Fälle erkennen (Board-Profil `mcu` bzw. Antworttext) und **nicht** in den Nordic-DFU-Ablauf schicken.
- **Nicht verwechseln:** `start dfu` (ZephCore) startet in den **UF2-USB-Bootloader** bzw. den ESP32-Download-Modus. Das ist kein BLE-OTA.

### Neuer Menüpunkt „Firmware-Update (OTA)“
- **Eintrag:** neues `NavDrawerItem.FIRMWARE_OTA`.
  - Ist kein Gerät verbunden, ist der Menüpunkt gesperrt, wie die anderen Menüpunkte.
  - Er bleibt **erreichbar**, wenn der Node schon im DFU-Modus läuft (siehe Schritt 1).
- **Hinweis bei ESP32:** Ist das erkannte Board ein ESP32, erklärt der Screen den WLAN-/MCUmgr-Weg und blendet den Nordic-Ablauf aus. Dafür gibt es eine differenzierte Snackbar statt einer stummen Sperre.

### Geführter, manueller 3-Schritt-Ablauf
Jeder Schritt muss vom Nutzer ausgelöst werden; nichts läuft automatisch weiter. Der Fortschritt steht oben als `Schritt x / 3`, wie in der Setup-Tour.

**Schritt 1 – OTA-Modus starten**
- Der rote Button **„OTA-Modus starten“** sendet `start ota` über die bestehende Verbindung: USB-direkt, Companion-CLI oder Remote-Admin.
  - Der Befehl läuft streng sequentiell über `sequentialQueryMutex`, Ausgabe im SYSTEM-Log.
  - Davor muss der Nutzer per `SlideToConfirm` bestätigen.
- Ausgewertet wird die Antwort:
  - `OK - rebooting to BLE OTA DFU` oder `OK` bedeutet Erfolg.
  - `Error`, `Err…` oder ein Timeout bedeutet Fehler, mit Meldung.
- Bei Erfolg ist es normal, dass die Verbindung abbricht. Die App behandelt das als **erwarteten Neustart** und zeigt keine Meldung „Verbindung getrennt“. Dafür gibt es ein Flag im Link-Watch.
- Alternativ: **„Node läuft bereits im DFU-Modus“**. Damit geht es ohne Befehl direkt zu Schritt 2.

**Schritt 2 – Datei & Gerät wählen**
- **Firmware-Datei:**
  - Gewählt wird über den Android-Dateidialog (`ActivityResultContracts.OpenDocument`, MIME-Typ `application/zip`).
  - Die App prüft, ob ein Nordic-DFU-Paket vorliegt (`manifest.json` im ZIP).
  - Angezeigt werden Dateiname, Größe und Ziel aus dem Manifest.
- **DFU-Gerät:**
  - Ein eigener BLE-Scan, den der Nutzer startet, listet nur Geräte mit dem Nordic-DFU-Dienst. Angezeigt werden Name, RSSI und Adresse.
  - Der Nutzer wählt das Gerät **gezielt** aus; die App verbindet sich nicht automatisch.
  - Ist der Name aus `start ota` bekannt (der Node-Name, siehe `ota_name`), wird das passende Gerät hervorgehoben, aber nicht vorausgewählt.
  - Es gelten die vorhandenen BLE-Berechtigungen (`BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`, `neverForLocation`).

**Schritt 3 – Transfer**
- Der rote Button **„Update starten“** startet den Transfer, aber erst, wenn Datei **und** Gerät gewählt sind.
- Technik: Bibliothek `no.nordicsemi.android:dfu` mit `DfuServiceInitiator`, `DfuBaseService` und `DfuProgressListener`.
- Anzeige:
  - **Prozent-Fortschrittsbalken** (`LinearProgressIndicator`) mit Datenrate;
  - **Status** im Klartext: Verbinde → Starte DFU → Übertrage x % → Prüfe → Fertig, Node startet neu.
- Abbrechen ist möglich; danach erscheint ein Hinweis, dass der Node im DFU-Modus bleibt.
- **Am Ende** steht „Update abgeschlossen – Node startet neu“. Ein Button „Neu verbinden“ führt zum Verbindungs-Screen; die App verbindet sich nicht von selbst.
- Bei einem Fehler:
  - Die App zeigt den DFU-Fehlercode im Klartext.
  - Der Node bleibt im DFU-Modus, ein neuer Versuch ab Schritt 2 ist möglich.

### Technische Punkte
- **Abhängigkeit:**
  - `no.nordicsemi.android:dfu` in einer Version, die mindestens 2 Wochen alt ist; vorher prüfen, ob sie mit minSdk 26 und targetSdk 34 kompatibel ist.
  - Der DFU-Service wird im Manifest deklariert. Unter Android 14 braucht der Vordergrund-Dienst `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_CONNECTED_DEVICE` und eine Benachrichtigung.
- **Exklusive BLE-Nutzung:** Vor dem DFU wird die eigene BLE-Verbindung (`BleConnectionManager`) sauber getrennt, Scan und GATT dürfen nicht parallel laufen.
- **Link-Watch & Schonmodus:**
  - Während des OTA ist der Link-Watch pausiert.
  - Ein aktiver Schonmodus wird **vorher** beendet, weil sich die Sendeleistung sonst nicht mehr per `set tx` zurücksetzen lässt.
- **Datenschutz:** Die Firmware-Datei bleibt lokal. Die App sendet nichts an externe Dienste.
- **Tests:**
  - Unit-Tests für die ZIP-/Manifest-Prüfung, die Auswertung der `start ota`-Antworten (nRF52 gegenüber ESP32 und Fehlern) und die Zustandsmaschine Schritt 1 → 2 → 3.
  - Praxistest auf Wio Tracker L1 und RAK4631, mit MeshCore und mit ZephCore.

### Abnahme
- [ ] `start ota` wird genau einmal sequentiell gesendet; der Verbindungsabbruch danach gilt als erwartet.
- [ ] ESP32-Boards werden erkannt und nicht in den Nordic-DFU-Ablauf geschickt.
- [ ] Ungültige oder Nicht-DFU-ZIPs werden vor dem Start abgelehnt.
- [ ] Der Fortschritt läuft flüssig von 0 bis 100 %; Abbruch und Fehler werden verständlich angezeigt.
- [ ] Alle Texte in DE, EN, NL und FR; der Screen ist bei 360 dp Breite bedienbar.

---

## F3 – Live-Repeater-Suche / Zero-Hop-Discovery (`ConnectionScreen` & `RepeaterViewModel`)

### Ziel
Repeater in Funkreichweite live finden, auch wenn sie **nicht** in der gespeicherten Kontaktliste des Companions stehen, etwa frisch in Betrieb genommene Geräte vor Ort. Der Nutzer kann sie direkt für das Remote-Admin-Login auswählen. Vorbild ist die Live-Suche in Kiek.

### Anforderungen
1. **Ergänzung zur gespeicherten Kontaktliste, kein Ersatz.**
   - Die vorhandene Liste aus `CMD_GET_CONTACTS` bleibt.
   - Neu ist ein Button **„Live-Suche“** bzw. „Zero-Hop-Discovery“, den der Nutzer selbst auslöst. Es gibt kein Dauer-Scan und kein Polling.
2. **Echtzeit-Liste** gefundener Repeater, jeweils mit:
   - **Name** (falls bekannt);
   - **Public Key**: Präfix in Monospace, voller Key per Antippen bzw. Kopieren;
   - **Signalstärke** RSSI (dBm) und SNR (dB), farbcodiert wie die BLE-Liste: grün / Ocker / Signalrot;
   - **Letzter Empfang**, relativ („vor 12 s“), live aktualisiert;
   - Badge **„neu“**, wenn der Repeater nicht in der Kontaktliste steht.
3. **Direkte Auswahl:** Antippen übernimmt den Repeater als Ziel (`selectTargetRepeater`). Danach folgt der bestehende Remote-Admin-Login mit Passwortfeld, `CMD_SEND_LOGIN` und Bestätigung erst bei `PUSH_LOGIN_SUCCESS` (0x85).
4. **Begrenzte Dauer:** Die Suche läuft höchstens ~60 s. Danach endet sie mit dem Status „Suche beendet – x Repeater gefunden“, und der Nutzer kann sie erneut starten.

### Firmware-Fakten (geprüft am MeshCore-Quellcode, Stand rc14)
- **Aktive Suche:** Der Companion sendet mit `CMD_SEND_CONTROL_DATA` (55, ab v8) einen **Zero-Hop**-Control-Frame: `[55][0x80 = NODE_DISCOVER_REQ][filter][tag(4)][since(4)]`.
  - `filter = 1 << ADV_TYPE_REPEATER` sucht nur Repeater.
  - Der Companion akzeptiert nur Control-Daten mit gesetztem Bit 7 (`cmd_frame[1] & 0x80`) und antwortet mit `RESP_CODE_OK`.
  - Das Format entspricht `sendNodeDiscoverReq()` in `examples/simple_repeater/MyMesh.cpp`.
- **Antwort der Repeater:**
  - Sie antworten per `sendZeroHop` mit `NODE_DISCOVER_RESP` (0x90 | Knotentyp), `snr_in(×4)`, `tag(4)` und dem `pub_key` (32 Byte oder 8-Byte-Präfix).
  - Die Antworten kommen mit zufälliger Verzögerung (×4), weil viele Repeater gleichzeitig antworten können.
  - **Ausnahmen:** Nicht antworten Repeater mit `disable_fwd` (repeat off), versteckte Knoten (`isHiddenNode`) und Repeater, deren Rate-Limiter (`discover_limiter`) gerade greift.
- **Empfang in der App:**
  - **`PUSH_CODE_CONTROL_DATA` (0x8E):** `[0x8E][snr×4][rssi][path_len][payload…]`. Liefert SNR/RSSI am Companion plus den Payload mit `snr_in` (wie der Repeater **uns** hört) und den Public Key.
  - Die App ordnet die Antwort über den `tag` der eigenen Anfrage zu. Antworten mit fremdem oder abgelaufenem Tag ignoriert sie.
- **Passive Ergänzung durch Adverts:**
  - `PUSH_CODE_NEW_ADVERT` (0x8A) liefert einen vollständigen Kontakt-Datensatz mit Name, Typ und Pfad für noch unbekannte Knoten.
  - `PUSH_CODE_ADVERT` (0x80) liefert nur den Public Key eines bereits bekannten Kontakts. Den Namen holt die App aus der Kontaktliste.
  - Adverts enthalten **keine** RSSI/SNR. Signalwerte kommen aus `PUSH_CODE_CONTROL_DATA` oder optional aus `PUSH_CODE_LOG_RX_DATA` (0x88: `[0x88][snr×4][rssi][raw]`).
- **Name bei Discovery-Treffern:** Die Discovery-Antwort enthält **keinen Namen**.
  - Für unbekannte Repeater zeigt die App zunächst den Key-Präfix, etwa „Repeater A1B2C3…“.
  - Der Name wird ergänzt, sobald ein Advert eingeht. Ein Button „Advert anfordern“ ist nicht vorgesehen, weil die Firmware keine Advert-Anforderung kennt.
- **Login bei unbekannten Repeatern:** `CMD_SEND_LOGIN` braucht einen Kontakt im Companion. Für Treffer, die dort nicht stehen, muss der Kontakt **vor** dem Login angelegt werden (`CMD_ADD_UPDATE_CONTACT`). Format und Pfad (Zero-Hop: direkt, `out_path_len = 0`) werden vor der Umsetzung am Quellcode geprüft.
- **ZephCore:** Die Companion-Codes werden vor der Umsetzung gegen `~/meshcore-dev/ZephCore` abgeglichen.
- **Mindestversion:** Discovery braucht Companion-Firmware ab v8. Ältere Companions antworten mit `ERR_CODE_UNSUPPORTED_CMD`. Die App zeigt dann einen klaren Hinweis und bietet nur die passive Advert-Liste an.

### Umsetzungsskizze
- **Modell** (pure, unit-getestet):
  - `DiscoveredRepeater(pubKeyHex, name?, rssiDbm?, snrDb?, snrInDb?, lastHeardMs, source = DISCOVERY|ADVERT, inContacts)`;
  - Parser für 0x8E, 0x8A und 0x80 in `RepeaterProtocol`;
  - Zusammenführen pro Public Key; der neueste Empfang gewinnt, der Name wird ergänzt, nie überschrieben.
- **ViewModel:**
  - `startLiveDiscovery()` sendet die Anfrage **einmal** über den bestehenden Companion-Sendeweg, sequentiell und nicht parallel zu laufenden CLI-Abfragen.
  - Ein Timer (60 s) setzt `isDiscovering = false`.
  - Ein-/Ausgehendes erscheint im SYSTEM-Log.
- **Frames außerhalb der Suche:** Eingehende Adverts außerhalb einer aktiven Suche aktualisieren die Liste nur, wenn der Live-Bereich geöffnet ist. Ein Hintergrund-Polling gibt es nicht.
- **UI** (`ConnectionScreen`, Abschnitt „Mesh OTA“):
  - Ein Umschalter **Gespeicherte Kontakte | Live-Suche** mit Kontaktzahl bzw. Trefferzahl.
  - Die Trefferliste ist nach Signalstärke sortiert, die Filter-Chips „Nur Repeater“ und „Nur aktive Pfade“ gelten analog.
  - Kompakte Einträge mit `BauhausCornerShape`. Die Altersangabe tickt nur, solange der Abschnitt sichtbar ist.
- **Verfügbarkeit:** Nur bei verbundenem **Companion** (BLE oder USB). Bei einem direkten USB-Repeater ist der Abschnitt ausgeblendet, denn dort gibt es kein Remote-Admin.

### Abnahme
- [ ] Die Suche löst genau einen Zero-Hop-Discovery-Frame aus. Kein Dauer-Scan, Ende nach ≤ 60 s.
- [ ] Treffer erscheinen live mit Public Key, RSSI/SNR und „vor x s“. Der Name wird ergänzt, sobald ein Advert eingeht.
- [ ] Antworten mit falschem Tag oder von anderen Knotentypen werden ignoriert (Unit-Test).
- [ ] Antippen übernimmt den Treffer als Ziel. Login bei einem bisher **nicht gespeicherten** Repeater funktioniert, inklusive Kontaktanlage.
- [ ] Companion < v8: verständlicher Hinweis, keine Fehlermeldung ohne Erklärung.
- [ ] Alle Texte in DE, EN, NL und FR; bedienbar bei 360 dp Breite.

---

## F4 – Präfix-Filter für die Repeater-Auswahl (`ConnectionScreen`)

### Ziel
In langen Kontaktlisten soll der Nutzer den gewünschten Ziel-Repeater mit wenigen Tastendrücken finden.

### Anforderungen
1. **Such-/Filterfeld über der Repeater-Liste**, im Abschnitt „Mesh OTA“: einzeilig, `BauhausCornerShape`, Such-Icon, Platzhalter „Repeater suchen…“.
2. **Live-Filter ab dem ersten Zeichen** auf den **Namensanfang**: `name.startsWith(query.trim(), ignoreCase = true)`.
   - Beispiel: „L“ zeigt nur Repeater, deren Name mit „L“ beginnt.
   - Es gibt keine Teiltreffer mitten im Namen. Das ist bewusst so, damit die Ergebnisse vorhersehbar bleiben.
3. **Löschen-Button `×`** am Feldende (`trailingIcon`): Er erscheint nur bei nicht leerem Feld und setzt den Filter mit einem Tippen zurück, die Tastatur bleibt offen.
4. **Kombinierbar** mit den bestehenden Filter-Chips „Nur Repeater“ und „Nur aktive Pfade“, nach der Regel UND. Später gilt der Filter auch für die Live-Suche aus F3.
5. **Leerer Zustand:** Bei 0 Treffern erscheint „Kein Repeater beginnt mit „{0}““. Die Trefferzahl steht in der Abschnittsüberschrift, zum Beispiel „Kontakte (3 / 42)“.

### Umsetzungsskizze
- **Logik:** Reine, unit-getestete Funktion `ContactFilter.byNamePrefix(contacts, query)`.
  - Ein leeres oder nur aus Leerzeichen bestehendes `query` lässt die Liste unverändert.
  - Groß- und Kleinschreibung spielt keine Rolle, auch bei Umlauten (`Locale.ROOT`-sicher, `ignoreCase = true`).
  - Die Reihenfolge der Eingangsliste bleibt erhalten.
- **State:** Das Suchfeld wird per `rememberSaveable` gesichert. So überlebt es eine Display-Drehung, aber keinen Verbindungswechsel: Beim Trennen wird es geleert. Gefiltert wird per `remember(contacts, query)` und ohne zusätzliche Abfragen an das Gerät.
- **Tastatur:** `imeAction = Search`. Das Feld wandert mit `imePadding` mit, und die Liste wird nicht gestaucht (siehe STYLE_GUIDE 2.3).

### Abnahme
- [ ] Die Eingabe „L“ zeigt nur Namen, die mit L oder l beginnen. „Lu“ grenzt weiter ein; Groß-/Kleinschreibung egal (Unit-Test).
- [ ] `×` leert das Feld und zeigt sofort wieder die vollständige Liste.
- [ ] Der Filter wirkt zusammen mit den Chips; Leer-Zustand und Trefferzahl stimmen.
- [ ] Alle Texte in DE, EN, NL und FR; bedienbar bei 360 dp Breite mit offener Tastatur.

---

## Bewusst nicht in 1.1
- Automatische Firmware-Downloads aus dem Internet, etwa GitHub-Releases. Die Datei wählt immer der Nutzer selbst.
- WLAN-OTA und MCUmgr für ESP32. Das ist ein möglicher Kandidat für 1.2.
- USB-UF2-Flashen (`start dfu`).
