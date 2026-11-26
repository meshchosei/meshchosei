# STYLE_GUIDE – meshchōsei

Verbindliche Design-, UX- und Architektur-Regeln, abgeleitet aus dem RepeaterConfigurator (meshchōsei).
Gedacht als Vorlage für künftige Android/Compose-Projekte im Umfeld MeshCore/ZephCore.

Stack-Referenz: Kotlin 1.9 · Jetpack Compose (BOM 2024.05) · Material3 · `AndroidViewModel` + `StateFlow` · minSdk 26 / targetSdk 34.

---

## 1. Visuelle Identität & Bauhaus-Designsystem

### 1.1 Farbpalette

Primärfarben werden **nie** als Hex im Screen-Code verwendet, sondern über `MaterialTheme.colorScheme` bzw. `LocalBauhausColors.current` (`bColors.signalRed`, `.cobalt`, `.ochre`, `.successGreen`).

| Rolle | Token (Dark) | Hex Dark | Hex Light | Einsatz |
|---|---|---|---|---|
| **Carbon** (Grund) | `BauhausDarkCarbon` | `#121316` | `#F7F6F2` (Off-White) | App-Hintergrund |
| Karte / Surface | `BauhausDarkCard` | `#1C1E22` | `#FFFFFF` | Cards, Overlays |
| Surface Variant | `BauhausDarkSurfaceVariant` | `#26292E` | `#EBE9E1` | interaktive Flächen |
| Kontur | `BauhausDarkOutline` | `#383C44` | `#D0CECA` | 1-dp-Rahmen |
| **Signalrot** | `BauhausDarkSignalRed` | `#E53935` | `#D32F2F` | `primary`, Speichern, Aktiv-Zustand, Fehler |
| **Kobaltblau** | `BauhausDarkCobalt` | `#1E88E5` | `#1565C0` | Funk, Telemetrie, Remote-Admin, Info |
| **Ocker** | `BauhausDarkOchre` | `#FFB300` | `#E65100` | Warnungen, Schonmodus/Mesh-Care |
| Grün | `BauhausDarkGreen` | `#00E676` | `#2E7D32` | nur OK-/Verbunden-Status |
| Text primär | – | `#F0F1F2` | `#111111` | |
| Text sekundär | – | `#9EABA4` | `#5A5D61` | technische Captions |

**Logo-/Markenfarben** (fest, theme-unabhängig, identisch zu `ic_launcher_foreground.xml`; Objekt `MeshchoseiColors`):
Carbon `#18181B` · **Paper `#F4F1EA`** · Kobalt `#1E88E5` · Signalrot `#E53935` · Ocker `#FFB300`.

Regeln:
- Eine Farbe = eine Bedeutung. Rot heißt „handeln/aktiv/kritisch“, Blau heißt „Funk/Info“, Ocker heißt „Achtung“. Grün steht nur für Status, nie für Buttons.
- Dark und Light sind gleichwertig. Jede Farbe braucht beide Varianten, Light mit höherem Kontrast für Sonnenlicht.
- Paletten-Varianten (`BauhausPalette`: BAUHAUS, TACTICAL, NORDIC, STEALTH) tauschen nur primary/secondary/tertiary. Die Semantik bleibt gleich.

### 1.2 Formen

- **`BauhausCornerShape = RoundedCornerShape(2.dp)`** ist seit v1.09 die **einzige** Form für alle Kästchen: Cards, Buttons (auch `TextButton`), Chips, TextFields, Badges, Kacheln, Overlays und Dialoge (`AlertDialog(shape = BauhausCornerShape, …)`, auch der „App beenden“-Dialog). Kanten sind fast eckig.
  Der Code-Kommentar in `BauhausTheme.kt` nennt seit rc12 ebenfalls 2 dp; früher stand dort irreführend „strict 4.dp radii“.
- `BauhausShapes`: seit v1.09 liegen **alle** Stufen (extraSmall bis extraLarge) auf `BauhausCornerShape`. Material-Komponenten ohne explizites `shape` (Menüs, Snackbars, Dialoge) werden dadurch nicht wieder runder. Trotzdem gilt: `shape = BauhausCornerShape` immer explizit setzen, denn Material-Buttons ignorieren das Theme und sind standardmäßig pillenförmig.
- **Verboten:** `CircleShape`, `RoundedCornerShape(n)` mit eigenem Radius und das pillenförmige Material-`Badge`. Badges wie „ON“ im Seitenmenü sind ein `Text` mit `Modifier.clip(BauhausCornerShape).background(farbe.copy(alpha = 0.2f))`.
- **Einzige runde Ausnahme:** das USB-/BT-Verbindungs-Badge (Icon-Kreis neben dem Gerätenamen in der Verbindungskarte, `ConnectionScreen`). Frühere Ausnahmen (Status-Overlay 14 dp, Speichern-Button 12 dp) gibt es nicht mehr.
- Kein UI-Kästchen und daher nicht betroffen: das Logo-Emblem (`MeshchoseiLogo`, Ecken 12 % der Kantenlänge wie das Launcher-Icon).
- `ShapeConsistencyTest` prüft die Quelltexte und schlägt bei neuen runden Formen fehl.
- Rahmen sind immer `BorderStroke(1.dp, colorScheme.outline)`. Für Hervorhebungen 1 dp in einer Akzentfarbe, zum Beispiel Kobalt für die Datenschutz-Card.
- Keine Schatten auf normalen Cards. Elevation (8 dp) bekommen nur schwebende Elemente wie das Overlay.

### 1.3 Typografie (`ui/theme/Type.kt`)

| Stil | Font | Gewicht | Größe/Zeile |
|---|---|---|---|
| headlineMedium | Default | Bold | 24/30 sp |
| titleLarge | Default | SemiBold | 20/26 sp |
| titleMedium | Default | Medium | 16/22 sp |
| bodyLarge | Default | Normal | 15/22 sp |
| bodyMedium | Default | Normal | 13/18 sp |
| labelSmall | **Monospace** | Medium | 11/14 sp |

- **Monospace** steht für alles Technische: CLI-Konsole, Versionsnummer, Spinner, Hex-/Schlüsselwerte, Credits.
- Hilfstexte haben 11–12 sp in `onSurfaceVariant`, Chip-Labels 11 sp.
- Wortmarke: `FontWeight.Light`, Sperrung `letterSpacing = 0.32.em`.

### 1.4 Emblem & Wortmarke („ō“-Krone)

- Das Emblem ist der Buchstabe **ō**:
  - ein Paper-Ring (EvenOdd, Radien 16/10);
  - ein Paper-Makronbalken darüber;
  - **drei Funk-Blitze**, die aus dem Balken nach oben zucken: links Kobalt, Mitte Signalrot, rechts Ocker.
- Es wird als Vektor in 108er-Launcher-Koordinaten gezeichnet (`drawEmblem()`). Dieselbe Geometrie wird in Compose und im Adaptive Icon verwendet.
- Wortmarke: `meshch` + **`ō`** + `sei`. Das „ō“ ist in Signalrot und `FontWeight.Black` gesetzt, der Rest in Paper.
- Optionaler Farbbalken unter dem Logo, 4 dp hoch: Kobalt 30 %, Rot 15 %, Ocker 30 % der Emblembreite.
- Varianten:
  - `MeshchoseiLogo`: groß, für Splash und About;
  - `MeshchoseiLogoIcon`: 44 dp, auf einem Carbon-Quadrat, für den Drawer-Kopf;
  - `MeshchoseiEmblem`: ohne Hintergrund.

### 1.5 Splashscreen

- Inhalt: Vollbild in Carbon, das Logo zentriert (Emblem 168 dp, Schrift 28 sp), unten die Signatur in Paper mit 45 % Alpha.
- Dauer: **genau 2,5 s** (`delay(2500L)`).
- **Nur beim Kaltstart.** Ein prozessweites Flag `splashShownInProcess` (Top-Level-`var`, nicht `remember`/`rememberSaveable`) wird nach dem ersten Anzeigen gesetzt.
- Wegen `launchMode="singleTask"` und `configChanges="keyboard|keyboardHidden|navigation"` erscheint der Splash nicht erneut beim USB-Attach, bei einem neuen Intent, bei einem Tastatur-Event oder bei der Aktivitäts-Neuerstellung.

### 1.6 Versionsanzeige & Signatur

- Die Version steht **oben rechts im About-Header** als `"v${BuildConfig.VERSION_NAME}"`, Monospace, 11 sp, gedämpft. Es gibt keine eigene Versions-Card.
- Signaturkonstante: `const val MESHCHOSEI_SIGNATURE = "🄯2026 AHGH"`. Das Copyleft-Zeichen U+1F12F steht vor Jahr und Kürzel, ohne Leerzeichen. Sie erscheint auf dem Splash und im About-Screen.
- Versionsschema: `0.<minor>` oder `0.<minor>-rc<n>`, `versionCode` steigt monoton. APK-Name: `RepeaterConfigurator-v<versionName>.apk`.

---

## 2. Layout- & Bedienungs-Standards

### 2.1 Aufbau eines Konfigurations-Screens

```
Column(fillMaxSize · verticalScroll · imePadding · padding(16.dp) · spacedBy(10–12.dp))
 ├─ Card(BauhausCornerShape, 1-dp-Outline, surface)      ← thematische Gruppe
 │   └─ Column(padding 16.dp, spacedBy 10.dp)
 │       ├─ Hilfstext (12 sp, onSurfaceVariant)
 │       ├─ FilterChip-Reihe (Presets, selected = signalRed + Weiß)
 │       ├─ HorizontalDivider(outline α 0.4)
 │       └─ Eingabefelder (Row, spacedBy 8.dp, isError bei Validierungsfehler)
 ├─ weitere Cards …
 └─ Speichern-Button (außer im Tour-Modus)
```

- Jeder Screen arbeitet auf einem **Draft** im ViewModel. Dazu gehören `edit { it.copy(...) }`, die Validierung im ViewModel und `save…Draft()`.
- Eingaben werden beim Tippen gefiltert, zum Beispiel `decimal(raw, maxLen)`. Ungültige Werte setzen `isError`, sie werden nicht stillschweigend korrigiert.
- Grenzen kommen aus dem Modell, nicht aus der UI. Beispiel: das TX-Limit über `maxTxInputDbm` aus dem aktiven `BoardProfile`.
- Es gibt keine Änderungsvorschau und keine „keine Änderungen“-Meldung. Speichern schreibt, und die Snackbar bestätigt.
- **Gespeichert erst nach Bestätigung:**
  - Die Vergleichsbasis (`nodeBaseline`) liegt im ViewModel, nicht lokal im Screen.
  - Jeder Set-Befehl ist eine `SetupChange(command, commit)`. `commit` wird nur angewendet, wenn der Repeater den Befehl ohne Fehler bestätigt hat.
  - Bei Timeout oder Fehlerantwort gilt der Wert weiter als geändert und wird beim nächsten Speichern erneut gesendet.
- Zentrale Eingabeprüfung über `InputValidator`:
  - TX von −9 dBm bis zur Grenze des Board-Profils;
  - Koordinaten im gültigen Bereich;
  - Private Key mit exakt 128 Hex-Zeichen;
  - Name und Besitzer-Info ohne Steuerzeichen.

### 2.2 Speichern-Button

```kotlin
val busy by viewModel.busyActions.collectAsState()
BusyButton(                                   // ui/components/BusyButton.kt
    busy = BusyKey.RADIO in busy,             // Arbeitszustand aus dem ViewModel
    onClick = { viewModel.saveRadioDraft() }, // VM sendet mit busyKey = BusyKey.RADIO
    modifier = Modifier.fillMaxWidth().height(52.dp),
    containerColor = bColors.signalRed
) {
    Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White)
    Spacer(Modifier.width(8.dp))
    Text(tr("Speichern"), color = Color.White, fontWeight = FontWeight.Bold)
}
```

**Sofortiges Button-Feedback & Doppelklick-Sperre (verbindlich für jeden Speichern-/Senden-Button):**

- Beim Antippen wechselt der Button **sofort** in den Arbeitszustand (`isSaving`/`busy = true`): Er zeigt den rotierenden Braille-Spinner `⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏` (80 ms/Frame, Monospace) samt „Speichere…“ (bei reinen Sende-Aktionen „Sende…“, bei sehr schmalen Buttons nur den Spinner).
- Während der Übertragung ist der Button gesperrt. Er bleibt aber in seiner Signalfarbe (85 % Deckkraft, nicht grau), damit erkennbar ist, dass gearbeitet wird.
- Der Zustand endet erst, wenn der Repeater geantwortet hat (Bestätigung, Fehlerantwort oder Timeout). Das gilt auch bei Abbruch durch Trennung (`invokeOnCompletion`).
- Die Sperre liegt **im ViewModel** (`busyActions: StateFlow<Set<String>>`, Schlüssel aus `BusyKey`). `enqueueCommands(…, busyKey = …)` verwirft einen zweiten Aufruf mit demselben Schlüssel, auch wenn die UI noch nicht neu gezeichnet hat.
- Listen-Aktionen (Hinzufügen/Löschen in Filtern, Regionen) ändern die Anzeige erst **nach** der Bestätigung durch den Repeater. Die Löschen-Icons der betroffenen Liste sind währenddessen gesperrt.
- Eingabefelder werden nur geleert, wenn der Befehl wirklich gesendet wurde (Rückgabe `true`).
- Betroffene Screens: `RadioAntennaScreen`, `IdentityLocationScreen` (inkl. GPS, Private Key), `RoutingAdvertsScreen` (inkl. Advert, Regionen), `BackupScreen` (Erstellen, Installieren) und `FilterScreen` (alle Hinzufügen-Buttons).

**Geändert vs. unverändert (verbindlich für alle Speichern-Buttons, seit v1.07):**

- **Keine ungespeicherten Änderungen** (`hasUnsavedChanges == false`): Der Button erscheint in gedecktem Rot (`bColors.signalRed.copy(alpha = 0.38f)`, Konstante `SAVED_STATE_ALPHA`) und ist gesperrt. So ist auf einen Blick erkennbar, dass alles gespeichert ist.
- **Mindestens ein Feld geändert** (`hasUnsavedChanges == true`): Der Button erscheint in vollem, leuchtendem Signalrot (`bColors.signalRed`) mit weißer Fettschrift und ist klickbar.
- „Geändert“ bedeutet: Der Entwurf weicht vom **bestätigten** Stand `nodeBaseline` ab. Nach der Bestätigung durch den Repeater wird der Button daher automatisch wieder gedeckt. Bei Timeout oder Fehlerantwort bleibt er leuchtend.
- Umsetzung: `BusyButton(…, hasUnsavedChanges = …)`. Der Vergleich liegt bei der Logik des Screens bzw. im ViewModel und nie in der Button-Komponente:

| Screen | `hasUnsavedChanges` |
|---|---|
| Funk & Region | `setup.radioDiffersFrom(baseline)` (Funkparameter, TX, Duty-Cycle) |
| Identität & Standort | Name, Besitzer, lat/lon ≠ Basis oder neues Admin-/Gast-Passwort bzw. „Gast-Passwort löschen“ |
| Routing & Adverts | `pendingChanges.isNotEmpty()` |
| Hardware & Antenne (Status-LEDs) | `setup.ledsMode != baseline.ledsMode` |

- Senden-Aktionen ohne Änderungsbegriff (z. B. „Advert senden“, Hinzufügen-Buttons) lassen den Parameter weg (Standard `true`).

- Jeder Screen hat genau **einen** roten Primärbutton, volle Breite, 52 dp hoch, mit weißer Fettschrift. Sekundäraktionen sind `OutlinedButton` mit `BauhausCornerShape`.
- **Zuordnung (seit v1.08):** Hat ein Screen neben dem Speichern-Block weitere Karten, die ihre Befehle direkt senden (z. B. „KONFIGURIERTE REGIONEN“ unter „ROUTING & ADVERTS“), steht der Speichern-Button **innerhalb** der Karte, deren Entwurf er speichert, als letztes Element. So ist eindeutig, wofür er gilt.
- **Tour-Modus:** Ein Screen registriert seine Aktion über `RegisterTourSave { … }` und blendet den eigenen Button aus (`if (!inTour)`). In der Tour-Leiste erscheint dann „Speichern & Weiter →“. Es gibt nie zwei rote Buttons gleichzeitig.
- Destruktive oder riskante Aktionen laufen über `SlideToConfirm` statt über einen Klick.

**Hinzufügen als Abschlusszeile (seit v1.08):** In Listen mit Zeilen-Layout (z. B. Regionen) ist die Eingabe für einen neuen Eintrag die **letzte Zeile der Liste** und kein separater Block darunter. Sie hat dasselbe Zeilen-Layout (gleiche Paddings), einen 1-dp-Rahmen in Signalrot (`BorderStroke(1.dp, bColors.signalRed)`, `BauhausCornerShape`) und kompakte, 28 dp hohe Monospace-Felder (Regionen: Name ~70 % mit Platzhalter „Neue Region“, Parent ~30 %, vorbelegt `*`). Rechts sitzt an exakt der Position des Mülleimer-Icons (36-dp-Zelle) der eckige signalrote `+`-Button. Ungültige Eingaben färben das Feld rot (2-dp-Rahmen), der `+`-Button ist dann gedeckt (`SAVED_STATE_ALPHA`).

**Ein Spinner pro Ladevorgang:** Während eine Liste neu geladen wird, dreht der Braille-Spinner **nur** im auslösenden ↻ (`InlineRefreshIcon`). Andere Buttons derselben Karte (Hinzufügen, Listen-Schalter, Löschen) sind gesperrt, zeigen aber keinen eigenen Spinner.

**Kompakte Listen-Schalter:** In dichten Listen (z. B. „KONFIGURIERTE REGIONEN“) stehen eckige Bauhaus-Schalter `[HOME] [DEF] [FLOOD]` statt runder Material-Switches. Sie haben `BauhausCornerShape` (2 dp), 28 dp Höhe, Monospace 10 sp fett und eine 1-dp-Outline. Aktiv sind sie vollflächig in ihrer Signalfarbe (HOME = Rot, DEF = Kobalt, FLOOD = Ocker). Während einer Übertragung sind sie gesperrt.

### 2.3 Schalter: `BauhausSwitch` (verbindlich)

Runde Material-`Switch`-Komponenten sind seit v1.04 **verboten**. Jeder Ein/Aus-Schalter ist ein `BauhausSwitch` (`ui/components/BauhausControls.kt`):

```kotlin
BauhausSwitch(
    checked = setup.repeatEnabled,
    onCheckedChange = { viewModel.updateBasicSetupDraft(setup.copy(repeatEnabled = it)) },
    enabled = inputEnabled,                 // optional
    activeColor = bColors.signalRed         // Standard; Kobalt für Whitelist/Info
)
```

- Form: rechteckige Spur 44 × 24 dp mit quadratischem Schieber 20 × 20 dp. Beide haben `BauhausCornerShape` (2 dp) und eine klare 1,5-dp-Rahmenlinie. Die Touch-Fläche ist 48 dp hoch.
- **Aktiv:** Die Spur ist vollflächig in der Aktiv-Farbe, der Schieber weiß und rechts.
- **Inaktiv:** Die Spur ist leer mit Outline-Rahmen, der Schieber in Outline-Farbe und links. Gesperrt bedeutet 40 % Deckkraft.
- Aktiv-Farben:
  - **Signalrot** ist der Standard, etwa für Weiterleitung, multi.acks und den Blacklist-Filter.
  - **Kobaltblau** gilt für Whitelist- und Info-Schalter.
  - **Einzige Ausnahme ist Ocker** für den Warnzustand „Dry-Run (nur protokollieren)“ im Filter-Screen.
- Barrierefreiheit: `toggleable(role = Role.Switch)`. TalkBack liest ihn wie einen normalen Schalter vor.
- Für kompakte, beschriftete Mehrfach-Schalter in Listen gelten die Listen-Schalter aus §2.2, zum Beispiel `[HOME] [DEF] [FLOOD]`.

### 2.4 „Aktualisieren“ / „Neu einlesen“: ungerahmtes ↻ (`InlineRefreshIcon`, verbindlich seit v1.21)

Jede Aktion, die Werte, Listen oder Status vom Repeater oder von Geräten neu lädt, ist **ein** ungerahmtes Aktualisieren-Icon `InlineRefreshIcon` (`ui/components/BauhausControls.kt`): `Icons.Default.Refresh` (↻), kein Rahmen, kein Text. Umrandete Buttons mit Text („Aktualisieren“, „Neu einlesen“), kleine blaue Klicktexte („Sync“) und rahmenlose `TextButton`s gibt es dafür nicht mehr.

```kotlin
InlineRefreshIcon(
    onClick = { viewModel.refreshRegions() },
    busy = BusyKey.REGION in busy,              // Braille-Spinner statt Icon, Fläche gesperrt
    contentDescription = tr("Aktualisieren"),   // Screenreader („Neu einlesen“, „Kontakte neu einlesen“ …)
    color = bColors.cobalt                      // Standard; Blacklist in Signalrot
)
```

- Touch-Fläche 28 dp, eckig (`BauhausCornerShape`), Icon 16 dp, Standardfarbe Kobalt.
- Während des Ladens ersetzt der Braille-Spinner das Icon, die Fläche ist gesperrt. Das Status-Overlay zeigt die Meldung (z. B. „Versorgung wird abgefragt…“).
- **Ort:** rechts in der Kopfzeile der betroffenen Card oder Sektion bzw. direkt hinter dem neu abgefragten Wert (Wert-Text mit `Modifier.weight(1f, fill = false)`, Abstand ca. 6 dp, damit das Icon am Wert klebt).
- Hinweistexte nennen das Symbol statt eines Button-Namens („oben auf ↻ tippen“, „danach mit ↻ neu einlesen“).
- Einsatzorte:
  - **Verbindung:** Statuszeile „✓ Alle Parameter eingelesen“ (Neu einlesen, seit v1.21), Repeater-Auswahl (direkt rechts neben dem Suchfeld, Companion-Kontakte neu einlesen, öffnet zugleich das Klappmenü, §2.10), „Erkannte OTG-Geräte“ und BLE-Scan;
  - **Hardware & Antenne:** „Versorgung: …“ (`refreshPowerStatus`, `BusyKey.POWER`);
  - **Routing & Adverts:** „Konfigurierte Regionen“ (seit v1.21);
  - **Filter:** Kopfzeile der Blacklist- **und** der Whitelist-Ansicht (gemeinsamer Aufbau, Farbe je Ansicht Signalrot bzw. Kobalt, seit v1.21).
- Der ältere `RefreshButton` (umrandet, mit Text) bleibt nur als Komponente erhalten und wird in keinem Screen mehr verwendet.
- Nicht gemeint ist „Auffrischen“ im Schonmodus. Dieser Button verlängert den Timer und lädt nichts neu, er bleibt die Primäraktion des Screens.

- **BLE-Scan (seit v1.19):** Starten per `InlineRefreshIcon` (↻, Kobalt). Während des Scans steht an derselben Stelle ein rahmenloses **Stop-Icon** (`Icons.Default.Stop`, 18 dp, Signalrot, Touch-Fläche 28 dp eckig) statt des Wortes „Stop“. Der Hinweistext lautet „Tippe oben auf ↻.“

### 2.5 Tastatur-Verhalten

- Manifest: `android:windowSoftInputMode="adjustResize"`.
- Scroll-Container: Modifier-Reihenfolge `verticalScroll(...)` → `imePadding()` → `padding(16.dp)`.
- **Ohne Stauchung:** Screens mit einem Viewport-füllenden Layout, etwa die Verbindungsseite, merken sich die größte gemessene Höhe (`StableHeight`, pro Orientierung) und behalten sie, während die Tastatur offen ist. Der Inhalt scrollt dann, statt zusammengedrückt zu werden. Untergrenze: `maxOf(viewportHeight - 32.dp, 420.dp)`.
- Eingabefelder sind `singleLine = true`. Labels haben `maxLines = 1`, `softWrap = false` und `TextOverflow.Ellipsis`.

### 2.6 Navigation & System-Zurück

- Navigation läuft über einen Drawer (`AppDrawer`, `NavDrawerItem`). Startseite ist **CONNECTION**.
- `BackHandler`, Reihenfolge verbindlich:
  ```kotlin
  BackHandler { when {
      drawerState.isOpen                    -> scope.launch { drawerState.close() }
      currentScreen != NavDrawerItem.CONNECTION -> resetToConnection()  // 1 Klick, beendet auch die Tour
      else                                  -> showExitDialog = true  // "App beenden?"
  } }
  ```
- Kein schrittweises Zurück durch den Verlauf: **ein** Druck führt immer zur Hauptseite.
- Der Navigationszustand übersteht Display-Drehungen: Verlauf, Tour-Schritt und Beenden-Dialog laufen über `rememberSaveable`.
  - Der Verlauf ist begrenzt: jeder Screen höchstens einmal, maximal 8 Einträge.
- Compose-State wird nie während der Komposition geschrieben.
  - Beispiel: `I18n.language` wird vor `setContent` gesetzt und bei Änderungen per `LaunchedEffect(langCode)`.

### 2.7 Status-Overlay mit Braille-Spinner

- Das Overlay ist eine zentrierte Card, `BoxScope.align(Center)`:
  - **feste Breite 300 dp**, `heightIn(min = 96.dp)`, `BauhausCornerShape` (2 dp, seit v1.09; vorher 14 dp), Elevation 8 dp, klare 1-dp-Outline.
  - Spinner-Frames: `⠋ ⠙ ⠹ ⠸ ⠼ ⠴ ⠦ ⠧ ⠇ ⠏`, 80 ms pro Frame, 34 sp, Monospace, Farbe `primary`.
  - Der Klartext der aktuellen Abfrage ist SemiBold und 14 sp, **immer `minLines = 2, maxLines = 2`**.
  - Darunter „Abfrage x von y“ und ein `LinearProgressIndicator`, danach „Abbrechen“.
- **Gleiche Größe in allen Phasen.** Fortschritt und Abbrechen-Button behalten ihren Platz, auch wenn sie nicht gebraucht werden. Sie werden über `alpha(0f)` ausgeblendet, nicht entfernt. So sind Verbinden, Login, Erkennung und Einlesen exakt gleich groß.
- Statustexte beschreiben, was gerade passiert („Lese Funkparameter…“). Es gibt keine doppelten oder generischen Texte.

### 2.8 Menü-Sperren mit differenzierten Hinweisen

- Ein Drawer-Eintrag ist gesperrt (ausgegraut, `isEnabled = false`), solange `navigationAllowed` falsch ist. Das gilt so lange, bis Verbindung **und** erstes Einlesen abgeschlossen sind (`initialReadDone && !setupReading`).
- **Menü-Sperre nur beim Start-Scan (seit v1.13):** `NavigationRules.navigationAllowed` hängt nur an Verbindung, Erkennung, Remote-Login und dem initialen Start-Scan, nie an manuellen CLI-Befehlen. Der Hamburger-Button ist immer bedienbar. Öffnen des Menüs oder ein Screenwechsel bricht wartende manuelle CLI-Befehle ab (`cancelManualCli`), damit sie den Mutex nicht bis zum Timeout blockieren.
- Ein Klick auf einen gesperrten Eintrag navigiert nicht, sondern zeigt eine Snackbar mit dem **konkreten Grund**. Prüfreihenfolge:
  1. nicht verbunden → `menu_blocked_not_connected`;
  2. Firmware oder Verbindung kann das nicht → `menu_blocked_unsupported`;
  3. ein konkurrierender Modus ist aktiv, etwa der andere Filter → `menu_blocked_filter_active`.
- Die Freigabelogik ist eine reine, unit-getestete Funktion (`FilterMenuRules`) und steht nicht in der Composable.
- Zustandsbadges wie „ON“ im Drawer lesen denselben Zustand wie der Screen-Schalter (`activeMode`).
- Schalter für Gerätezustände:
  - Sie sind **optimistisch**. Bis zur Bestätigung durch die Firmware sind sie gesperrt (`…Pending`) und werden nicht von eingehenden Statuszeilen überschrieben.
  - Danach wird der echte Zustand **einmal** nachgelesen.

### 2.9 Scroll-Hinweis bei überlaufendem Inhalt (seit v1.14)

- Kann ein Scroll-Bereich weiter nach unten gescrollt werden (`scrollState.canScrollForward == true`), zeigt er am unteren Rand einen **dezenten Hinweis**: Fade-Kante in der Hintergrundfarbe (28 dp), feine 1-dp-Linie in `outline` und einen kleinen Pfeil nach unten (Chevron, `onSurfaceVariant`).
- Der Hinweis blendet animiert aus, sobald das Ende erreicht ist.
- **Screens:** nur im Querformat (im Hochformat ist Scrollen der Normalfall). **Dialoge und Seitenmenü:** Dialoge (Haftungsausschluss, CLI-Hilfe) immer, sobald ihr Inhalt überläuft; das Seitenmenü wie Screens nur im Querformat.
- Umsetzung: `Modifier.hintedVerticalScroll(state, always = …, fadeColor = …)` statt `verticalScroll(state)` (`ui/components/ScrollHint.kt`). `fadeColor` ist die Containerfarbe: `background` für Screens, `surface` für Dialoge und Menü.
- **Geöffnetes Seitenmenü (seit v1.15):** Solange das Menü offen ist oder sich öffnet, zeigt nur das Menü seinen Hinweis. `MainActivity` setzt für das Hauptfenster `LocalSuppressScrollHint provides drawerOpen`, für das Menü `provides false`. Wer eigene überlagernde Ebenen baut, unterdrückt den Hinweis der verdeckten Ebene genauso.
- Nacktes `verticalScroll(…)` ist nicht erlaubt. `ScrollHintRuleTest` prüft das.

---

### 2.10 Einheitliche vertikale Klappmenüs (seit v1.18, verallgemeinert in v1.19)

Auswahl- und Vorschlagslisten sind **immer** dasselbe vertikale Bauhaus-Klappmenü. Keine horizontalen Chip-Scroller, keine Material-`DropdownMenu`-Popups, keine schwebenden Overlays: das Menü liegt im Layout und schiebt bzw. verkleinert den Inhalt daneben.

**Gemeinsame Regeln (für alle Klappmenüs):**

- **Richtung je nach Lage des Auslösers:**
  - **nach oben**, wenn der Auslöser eine Eingabezeile am **unteren** Bildschirmrand ist (CLI-Konsole). Das Menü steht direkt über der Zeile und verkleinert den Inhalt darüber (Log).
  - **nach unten**, wenn der Auslöser ein **Auswahlfeld** im oberen Bereich ist (Repeater-Auswahl). Das Menü steht direkt unter dem Feld. Was beim Aufklappen fest bleiben muss (Login-Bereich), steht **über** dem Feld.
- **Form:** `Card` mit `BauhausCornerShape` (2 dp), `BorderStroke(1.dp, outline)`, Grund `surfaceVariant`, 4–8 dp Abstand zum Auslöser. Höchstens ca. 5 Zeilen sichtbar (`heightIn(max = 200.dp)`), darüber vertikal scrollbar mit `hintedVerticalScroll(always = true, fadeColor = surfaceVariant)`. Ändert sich der Filtertext, springt die Liste nach oben (`scrollTo(0)`).
- **Zeile:** mindestens 40 dp hoch, die **ganze Zeile** antippbar, `HorizontalDivider` (outline, 50 % Alpha) zwischen den Zeilen, Innenabstand `start = 10.dp, end = 26.dp`, vertikal 6 dp.
  - links der Haupttext **fett** (Befehl bzw. Name), rechts kompakt der Zusatz in 11 sp, `onSurfaceVariant`;
  - alle Texte einzeilig: `maxLines = 1, softWrap = false, overflow = Ellipsis` (`…`).
- **Schließen:** kleines `✕` (Box 22 dp, Icon 14 dp, eckig, `Icons.Filled.Close`, 2 dp Abstand, Grund `surfaceVariant`) bündig oben rechts in der Ecke des Containers. Die 26 dp rechts in jeder Zeile halten es frei. Außerdem schließt System-Zurück (`BackHandler(enabled = menuOpen)`).
- **Wiederöffnen:** Antippen bzw. Weitertippen im Auslöser öffnet das Menü jederzeit wieder.
- **Leere Liste:** Ein Menü, das gezielt aufgeklappt wird (Auswahlfeld), zeigt eine einzelne gedämpfte 40-dp-Zeile („Lade…“, „Keine Repeater in der Kontaktliste.“). Ein Vorschlagsmenü (CLI) bleibt ohne Treffer zu.
- **Antippen wählt nur aus** und schließt das Menü. Es wird nie automatisch etwas gesendet.
- Begleitende Icons am Auslöser (Leeren `✕`, Auf-/Zu-Pfeil, ↻) sind ruhig und rahmenlos (`IconButton` bzw. `InlineRefreshIcon`, §2.4).

#### 2.10.1 CLI-Autocomplete: nach oben (seit v1.18)

- **Ort:** direkt **über** der CLI-Eingabezeile (`CliConsoleScreen`), verkleinert das Log darüber.
- **Zeile:**
  - links der Befehl in **Monospace-Fett** 12 sp (Firmware-Syntax mit Platzhaltern, unübersetzt, höchstens ca. 210 dp breit);
  - rechts die Kurzbeschreibung aus der CLI-Hilfe über `tr()`, 11 sp, `onSurfaceVariant`;
  - Kritische Befehle (`reboot`, `poweroff`, `shutdown`, `clkreboot`, `erase`, `start ota`, `start dfu`, `filter RESET`, `set prv.key`; seit v1.21 aus den Forks `ota apply`, `ota rollback`, `ota update`, `filter clear`, `set cmd.pubkey`) in Signalrot mit „⚠“.
- **Öffnen:** ausschließlich, wenn mindestens **1 Zeichen** getippt ist und passende Befehle existieren. Bei leerem Feld öffnet es **nie**, auch nicht beim Fokussieren. Es gibt keinen eigenen Öffnen-Button neben dem Senden-Button.
- **Sortierung (zweistufig alphabetisch):** zuerst alle Befehle, die mit der Eingabe **beginnen**, danach alle übrigen, die sie als Teilwort enthalten. Beide Gruppen sind jeweils alphabetisch nach Befehlsname sortiert. Beispiel `ver`: `ver`, dann `advert`, `advert.zerohop`, `get advert.interval`, `get bootloader.ver` …
- **Schließen:** zusätzlich zu `✕`/Zurück auch durch Senden. Ohne weitere Treffer bleibt das Menü zu, z. B. wenn der einzige Treffer die Eingabe selbst ist (`get name`) oder sobald ein Wert eingetippt ist. Jede weitere Eingabe öffnet es wieder.
- **Datenbasis:** ausschließlich die firmware-geprüfte CLI-Hilfe (`cliHelpCategories` → `cliAutocompleteEntries`), seit v1.18 vollständig abgeglichen mit MeshCore/ZephCore `CommonCLI.cpp` (inkl. `get pwrmgt.*`, Diagnose, CAD, GPS, LEDs, Uplink). Neue Befehle kommen nur dort hinein, immer mit Übersetzung in EN/NL/FR (`CliHelpCompletenessTest`).
- **Community-Forks (seit v1.21):** eigene Kategorien am Ende der CLI-Hilfe – „11. jhuebert-Fork: …“, „12. jhuebert-Fork: MQTT-Observer & WLAN“, „13. Sweden-Mesh LOU: Auto-Reply & Remote-Command“. Jede Kurzbeschreibung endet mit dem Herkunftsvermerk `(jhuebert)`, `(jhuebert Observer)` bzw. `(Sweden-Mesh)` in allen vier Sprachen. Ein Fork-Befehl wird nur aufgenommen, wenn es ihn im Bestand (MeshCore/ZephCore) nicht gibt; gleichnamige Befehle mit abweichender Fork-Bedeutung (`filter`, `filter on/off`, `filter stats`, `powersaving`, `time`, `start ota`) stehen nur einmal in ihrer Stock-Kategorie. Nicht-idempotente oder kritische Fork-Befehle (`ota apply/abort/rollback/stage/update`, `ota fetch start`, `filter add/del/move/clear`, `fleet forget …`, `trigger …`, `set cmd.pubkey`, `cmd ping`, `mqtt recover` …) stehen in `ManualCliRetry`. Geprüft in `ForkCliCommandsTest` (keine Duplikate, Herkunftsvermerk, Retry-/USB-/⚠-Regeln, zweistufige Sortierung).
- **Logik:** `CliAutocomplete` in `service/RepeaterProtocol.kt`, rein und unit-getestet (`CliAutocompleteTest`). Treffer = Befehl enthält die Eingabe; erst Präfix-Treffer, dann Teilwort-Treffer, je Gruppe alphabetisch (`partition { startsWith(query) }` + `sortedWith(compareBy { befehl.lowercase() })`), ohne Groß-/Kleinschreibung, ohne Duplikate.
- **Capabilities:** `filter …` nur bei `supportsBlacklist`, `filter wl …`/`filter mode …` nur bei `supportsWhitelist`. Seriell-only-Befehle (`get prv.key`, `erase`, `log`, `stats-*`, `set freq`, `get acl`, seit v1.21 `set cmd.debug`) nur bei direkter USB-Verbindung (`isDirectRepeater`). Ausnahme: Die Regelbefehle der jhuebert-Fork (`CliAutocomplete.JHUEBERT_FILTER`: `filter add/list/get/enable/disable/move/del/clear`, `filter chan …`, `filter ratelimit …`, `filter stats reset`) sind immer durchsuchbar, weil ein jhuebert-Repeater auf `filter help` anders antwortet als ZephCore.
- **Antippen übernimmt nur:** Der Befehl kommt ohne Platzhalter ins Feld, der Cursor steht am Ende. Erwartet er einen Parameter (`<…>`/`[…]`), folgt ein Leerzeichen (`set dutycycle `, `region home `, `tempradio `). Gesendet wird **nie** automatisch, nur über den roten Senden-Button. `start ota` läuft weiterhin über den Bestätigungsdialog.

#### 2.10.2 Repeater-Auswahl: nach unten (seit v1.19)

- **Ort:** Verbindungs-Screen nach dem Verbinden mit einem Companion-Node (`ConnectionScreen`). Feste Reihenfolge von oben:
  1. **Login-Bereich** (immer sichtbar, sobald der Companion verbunden ist): eckige Card mit Repeater-**Name** (fett) und **Public-Key-Präfix** + Hops (Monospace 11 sp) in einer Zeile, darunter Passwortfeld + Login-Button. Name, Key und Passwort stehen immer fest zusammen und verschieben sich weder beim Aufklappen der Liste noch bei der Auswahl um einen Pixel. Ohne gewählten Repeater: Platzhalter nur „Kein Repeater gewählt“ (gedämpft, ohne Key-Angabe), Passwortfeld und Login-Button gesperrt (`enabled = false`), Rahmen in `outline`.
  2. Kopfzeile „Repeater-Auswahl (n)“ und Filter-Chips „Nur Repeater“/„Nur aktive Pfade“.
  3. **Suchfeld** mit rahmenlosem ↻ (`InlineRefreshIcon`, Kontakte neu einlesen) direkt rechts daneben.
  4. unmittelbar darunter das **Klappmenü** mit `✕` oben rechts.
- **Suchfeld:** eckiges `OutlinedTextField`, Filter nach Name oder Key-Präfix, Platzhalter „Repeater suchen (Name oder Key)…“. **Kein** Auf-/Zu-Pfeil: Antippen (`PressInteraction.Release`) oder Tippen öffnet das Menü, geschlossen wird es nur über das `✕` oben rechts in der Liste, System-Zurück oder durch eine Auswahl (wie im CLI). Bei getipptem Text steht im Feld nur das Leeren-`✕` (leert den Filter, schließt nicht).
- **Automatisches Öffnen:** solange kein Repeater gewählt ist, beim Verbinden, beim (Neu-)Einlesen der Kontakte (auch per ↻) und beim Umschalten der Filter-Chips. Nach `✕`/Zurück bleibt es zu, bis der Nutzer das Feld antippt oder ↻ drückt.
- **Zeile:** links der Repeater-Name fett 13 sp (Room Server in Kobalt, `weight(1f, fill = false)`), rechts daneben das Public-Key-Präfix in Monospace 11 sp (`ABCDEF123456…`) und, falls ein Admin-Passwort gespeichert ist, ein dezentes Schlüssel-Icon (`Icons.Default.Key`, 12 dp, `onSurfaceVariant`). Der gewählte Repeater ist mit Signalrot 14 % hinterlegt.
- **Übernehmen (Antippen einer Zeile):**
  - Repeater (Name, Public-Key, ggf. gespeichertes Admin-Passwort) in den Login-Bereich oben übernehmen (`selectTargetRepeater`; derselbe Repeater setzt die Sitzung nicht neu auf);
  - Liste **sofort schließen**;
  - Suchfeld **leeren** (`""`), damit das nächste Antippen wieder die vollständige Liste zeigt;
  - Login-Bereich per `BringIntoViewRequester` in den Blick holen; ohne gespeichertes Passwort bekommt das Passwortfeld den Fokus. Eingeloggt wird **nie** automatisch, nur über den Login-Button.
- Der frühere Zwischenschritt „← Zurück“ zur Liste entfällt: Ein anderer Repeater wird über das Suchfeld gewählt.
- **Login-Bereich:** 1-dp-Rahmen Kobalt, eingeloggt Grün, ohne Repeater `outline`. **Kein** eigenes Status-Badge („Admin aktiv“/„Nicht eingeloggt“): Der Login-Status steht ausschließlich oben in der Verbindungs-Statuskarte.
- **Login-Button (Ausnahme zur Doppelklick-Sperre, §2.2):** Breite und Höhe bleiben konstant. Der Spinner (14 dp) ersetzt nur das Schloss-Icon in einer festen 16-dp-Box, der Text bleibt immer „Login“. Während eines Login-Versuchs ist der Button nur blasser (`alpha = 0.65f`), bleibt aber **anklickbar** (`enabled = true`): Geht über LoRa ein Paket verloren, kann der Nutzer sofort erneut senden (`startRemoteLogin` setzt den Timeout neu auf).

### 2.11 Update-Hinweis (seit v1.20)

- **Abruf:** einmal beim Start im `RepeaterViewModel` (läuft beim ersten Zugriff in `MainActivity.onCreate` an) auf `Dispatchers.IO`, blockiert den Start nie. `GET https://api.github.com/repos/meshchosei/meshchosei/releases/latest`, `Accept: application/vnd.github+json`, Connect-/Read-Timeout je 3 s. Ausgewertet werden nur `tag_name` und `html_url` (`service/UpdateCheck.kt`, unit-getestet in `UpdateCheckTest`).
- **Fehler:** kein Netz, Timeout, HTTP-Fehler, Rate-Limit, kaputtes JSON → komplett still: keine Meldung, kein Log, kein UI-Element.
- **Vergleich:** numerisch `Major.Minor` (`1.20 > 1.19 > 1.2`), ein führendes `v` wird ignoriert. Ein Hinweis kommt nur bei einer **echt neueren** Version. Die Debug-APK vergleicht gegen die simulierte Version `1.00` und zeigt den Vermerk `[DEBUG-Test]`; die Release-APK vergleicht gegen `BuildConfig.VERSION_NAME`.
- **Dialog statt Banner:** Der Verbindungs-Screen bleibt unverändert. Sobald eine neuere Version erkannt ist, der Hinweis in dieser Sitzung noch nicht geschlossen wurde und kein Haftungsausschluss-Dialog offen ist, zeigt `MainActivity` einen **zentrierten** `Dialog` (`ui/components/UpdateDialog.kt`): eckige `Surface` (`BauhausCornerShape`, 1-dp-Rahmen `outline`), oben mittig die Meldung „Neue Version vX.YZ verfügbar“ (16 sp fett, über `tr()`, in der Debug-APK darunter `[DEBUG-Test]`), darunter die Release-URL (`html_url`, Fallback `…/releases/latest`) in Kobalt, Monospace, unterstrichen, antippbar (`Intent.ACTION_VIEW`), unten horizontal zentriert ein `✕` (`IconButton`, `Icons.Filled.Close`). `✕`, System-Zurück und Tippen neben den Dialog schließen ihn für die laufende Sitzung. Im „Über & Lizenz“-Screen gibt es keinen Update-Hinweis.

### 2.12 Info-Dialoge schließen: zentriertes `✕` (seit v1.21)

- Reine Info-Dialoge ohne Entscheidung schließen mit **einem** zentrierten `✕` unten in der Mitte (`IconButton`, `Icons.Filled.Close`, 22 dp, `onSurfaceVariant`, `contentDescription = tr("Schließen")`), nicht mit einem Text-Button „Schließen“. System-Zurück und Tippen neben den Dialog schließen ebenfalls.
- Einsatzorte: Update-Dialog (§2.11) und der **Haftungsausschluss beim erneuten Ansehen** aus „Über & Lizenz“ / Seitenmenü (`DisclaimerDialog(isInitialAcceptance = false)`).
- Ausnahme: Beim **Erststart** bleibt der Haftungsausschluss ein Entscheidungsdialog mit den Text-Buttons „Abbrechen“ (beendet die App) und „Akzeptieren“; er ist dort nicht wegklickbar.

## 3. Kommunikations- & Protokoll-Architektur

### 3.1 Streng sequentielles Request-Response

- Es gibt **einen einzigen Mutex** (`sequentialQueryMutex`) für alle CLI-Befehle: Abfragen, Set-Befehle, Konsole, Pings.
- Es läuft immer genau ein Befehl. Der nächste wird erst gesendet, wenn die Antwort validiert wurde oder der Timeout abgelaufen ist.
- Jede Abfrage ist eine `QuerySpec` mit Befehl, **Validator** und lokalisiertem Statustext.
  - Antworten, die der Validator nicht akzeptiert, sind Fremdzeilen und werden ignoriert.
  - `null` bedeutet Fehler oder „unknown command“.
- **Antwort-Tags über LoRa (seit v1.12):** Jeder Remote-Befehl wird als `XX|befehl` gesendet (`CliReplyTag`, zwei Zeichen `0-9a-z`, laufend). MeshCore (`simple_repeater/MyMesh.cpp`) und ZephCore (`RepeaterMesh::handleCommand`) spiegeln das Präfix an den Antwortanfang. Nur eine Antwort mit genau dem Tag des laufenden Befehls zählt. Antworten mit fremdem Tag (verspätet nach Timeout) oder ohne Tag werden verworfen und im System-Log vermerkt. So landet z. B. eine späte `get name`-Antwort nie in `owner.info`. Der Inhalts-Validator gilt zusätzlich. USB-Direkt und lokale Companion-CLI bleiben ohne Tag.
- **Automatische Wiederholungen mit gleichem Tag (seit v1.16):** Auch im Start-Scan, beim Backup, bei Filtern und Regionen trägt der zweite Versuch dasselbe `XX|`-Tag wie der erste. So zählt eine verspätete LoRa-Antwort auf Versuch 1 noch und wird nicht als veraltet verworfen.
- **Filterstatus (seit v1.16):** Ein/Aus kommt aus `filter` (Zusammenfassung, „(dry-run)“-Zusatz), der Modus aus `filter mode`. `filter status` gibt es in der Firmware nicht. Status-Antworten werden nur für Status- und Schaltbefehle ausgewertet, nie für Hilfe oder Listen. `filterStatusKnown` wird erst nach gültiger Antwort `true`. Solange es `false` ist, fragt die App beim Öffnen des Menüs oder eines Filter-Screens einmal still nach.
- **Manueller CLI-Retry über LoRa (seit v1.13):** Bleibt die Antwort auf einen Konsolenbefehl aus, wird er **einmal** mit demselben `XX|`-Tag wiederholt (Hinweis „↻ … erneut“ im CLI-Tab), danach folgt eine klare Timeout-Meldung. Nicht-idempotente Befehle (`reboot`, `erase`, `advert`, `password …`, `filter RESET`, `region remove …` usw., `ManualCliRetry`) werden nie automatisch wiederholt, weil der Repeater sie sonst doppelt ausführt.
- Bei Timeout hebt `runQuery` die Zuordnung (`activeQueryCommand`, Validator, Waiter, Tag, Sammelpuffer) **sofort** auf. Der Sammelpuffer wird vor jedem Befehl geleert.
- `activeQueryCommand` legt fest, welcher Parser zuständig ist. Beispiel: Filter-Status wird nur aus Antworten auf `filter…`-Befehle übernommen, Listen nur aus `… list`.
- Die Timeouts hängen vom Transport ab: lokales USB/Companion ist kurz, Remote über LoRa länger.
  - Den Login-Timeout liefert das Gerät (`est_timeout` aus `RESP_CODE_SENT`) plus 4 s Puffer, begrenzt auf 8–60 s.
- **Feste Startreihenfolge** (sie ist Teil des Vertrags):
  1. Hardware;
  2. Capabilities, also Help-Probes (`filter help`, `filter wl help`);
  3. nur unterstützte Feature-Abfragen;
  4. Basis-Setup;
  5. ausstehende Wiederherstellungen.
- **Kein Hintergrund-Dauerpolling** von Gerätezuständen. Gelesen wird nur in drei Fällen:
  1. beim Start-Scan;
  2. bei einer expliziten Nutzeraktion wie „Status laden“ oder „Neu einlesen“;
  3. einmal nach einer eigenen Änderung.
- Befehle müssen zur Firmware passen. Jeder Befehl und jedes Antwortformat wird gegen den Quellcode geprüft (`~/meshcore-dev/MeshCore`, `~/meshcore-dev/ZephCore`). Nicht existierende Befehle (`filter mode off`, `filter bl`) werden nie gesendet, auch wenn sie plausibel klingen.
- Parser:
  - Sie arbeiten mit exakten Regexen, mit Wortgrenzen und am Zeilenanfang.
  - Sie entfernen das Prompt-Präfix `>` und verwenden `lowercase(Locale.ROOT)`.
  - Hilfetexte wie `[blacklist|whitelist]` dürfen nie als Zustand erkannt werden.
  - Jede Verwechslungsgefahr bekommt einen Unit-Test.

### 3.2 Trennung CLI ↔ SYSTEM

- Es gibt zwei Logs: `terminalLogs` (Tab **CLI**) und `systemLogs` (Tab **SYSTEM**), jeweils mit `takeLast(400)`.
- In den **CLI**-Tab kommen **nur** manuell im Eingabefeld getippte Befehle und deren Antworten, innerhalb des Antwortfensters `manualReplyUntilMs`.
- Alles andere geht in **SYSTEM**:
  - Start-Scan und Button-Aktionen;
  - Keep-Alive;
  - Antworten auf Auto-Befehle (`lastCommandWasAuto`, `activeAutoUntilMs`, `systemOnly = true`).
- Befehle von UI-Buttons gelten als System-Befehle. Nur `manualCli = true` landet in CLI.

### 3.3 Verbindungsabbruch-Erkennung

- Der Link-Watch ist der **einzige** erlaubte Hintergrund-Loop. Er läuft im 5-s-Takt, solange `Connected`.
- **Companion** (USB/BLE):
  - nach 20 s Funkstille ein binärer Keep-Alive (`DeviceQuery`), Retry alle 8 s;
  - nach 40 s ohne Frame gilt die Verbindung als getrennt.
- **Remote-Admin** (LoRa):
  - Antwort-Timeout 15 s;
  - nach mehreren Fehlschlägen gilt der Link als verloren;
  - nach 60 s Leerlauf ein `ver`-Ping.
- Ein Abbruch läuft über `handleLinkLost(reason)` und dann `disconnect()`. Dabei werden alle Jobs abgebrochen (Read, Login, Probe, Link-Watch), die Session zurückgesetzt und der Nutzer mit dem Grund informiert. Mesh-Care-Zustände werden nicht blind wiederhergestellt, sondern über einen persistierten Restore-Marker.
- Transportschicht:
  - **BLE:** Der GATT-Aufbau ist streng seriell: MTU → Services → Descriptor → genau ein `onConnectionChanged(true)`. Es gibt eine Write-Queue, die auf `onCharacteristicWrite` wartet. Frames größer als MTU−3 werden verworfen, nicht gesplittet.
  - **USB:** Es gibt eine Write-Queue mit Generationszähler, damit nach einem Reconnect keine alten Writes mehr ankommen.
  - **Aufräumen:** `onCleared()` ruft `destroy()` auf allen Managern auf.
- Login gilt **nur** nach dem Push `0x85` (LOGIN_SUCCESS) für den richtigen Pubkey-Präfix. `0x86` oder ein Timeout bedeuten Fehlschlag.

---

### 3.4 Optionale Firmware-Variablen & Backup-Import (seit v1.21)

- **Start-Scan:** liest zusätzlich `flood.max`, `flood.max.unscoped`, `flood.max.advert`, `radio.rxgain` und `radio.fem.rxgain`. Vor dem Lesen werden diese Werte geleert. Antwortet die Firmware mit Fehler (`??:`/`unknown`/`Err`), bleibt das Feld bzw. der Schalter **ausgeblendet** – eine Fehlerantwort zählt nicht als LoRa-Timeout.
- **Routing & Adverts:** Hop-Limits als Zahlenfelder 0–64 (`RoutingLimits.hopsOk`), Timing-Delays `txdelay`/`direct.txdelay` (0–2) und `rxdelay` (0–20) als Dezimalfelder (`RoutingLimits.delayOk`). ZephCore meldet die Delays als `adaptive`: Das Feld ist dann gesperrt und wird nie gesendet. Alles läuft über den gemeinsamen „Speichern“-Button der Karte (nur geänderte, gültige Werte).
- **Funk & Region:** eckiger `BauhausSwitch` „RX-Gain (LNA-Verstärkung)“ (`radio.rxgain`) bzw. „FEM-RX-Gain (externer LNA)“ (`radio.fem.rxgain`), Teil des Funk-Speicherns (`radioDiffersFrom`).
- **Backup-/Template-Import (`MeshCoreBackup.parse`):** tolerant für Community-Templates (z. B. BSmesh):
  - `regions` und `filter` sind optional, `regions` auch direkt als Liste; ohne `vars`-Hülle zählen bekannte Variablen auf oberster Ebene;
  - `radio` als Objekt `{freq,bw,sf,cr}` (Strings oder Zahlen) oder als String `"869.618,62.5,8,8"`;
  - Booleans als `true/false`, `"on"/"off"`, `1/0`; Zahlen-Variablen werden geprüft;
  - `null`/leere Werte und ungültige Schlüssel (Metadaten wie `comment`) werden nie gesendet; was im Template fehlt (`name`, `lat`, `lon`, `owner.info` …), wird nie überschrieben.
- **Diff-Abgleich (`MeshCoreBackup.withoutUnchanged`):** Nach einem vollständigen Start-Scan werden `set`-Befehle übersprungen, deren Wert der Repeater schon hat (numerisch verglichen, `radio` je Teilwert). Regionen- und Filterbefehle bleiben unberührt. Geprüft in `RoutingRxGainTemplateTest`.

## 4. Lokalisierung & Text-Disziplin

### 4.1 Mehrsprachigkeit (DE · EN · NL · FR)

- **Keine hartcodierten UI-Strings.** Zwei erlaubte Wege:
  1. `res/values*/strings.xml` für Menü, Drawer, Sperrhinweise, Labels und About. `values` ist Englisch (Fallback), dazu `values-de`, `values-nl`, `values-fr`. Zugriff über `stringResource(R.string.…)`.
  2. `tr("Deutscher Text {0}", arg)` für Texte in Code und ViewModel. Der Schlüssel ist der deutsche Originaltext. Die Übersetzungen stehen in `assets/i18n.json` als `"de": [en, nl, fr]`.
- `I18n.language` ist Compose-State. Ein Sprachwechsel im Drawer wirkt sofort, ohne Neustart. `MainActivity` liefert einen `LocalizedContextWrapper`, damit auch `stringResource` umschaltet. Activity-Zugriff erfolgt nur über `findActivity()`, nie per Cast.
- Für Meldungen aus dem ViewModel übersetzt `notifyUser(msg)` zentral über `tr`. Statustexte in `QuerySpec` laufen ebenfalls über `tr`.
- **Abnahmekriterium:** Ein neuer Text gilt erst als fertig, wenn er in allen vier Sprachen vorhanden ist. Ein fehlender Schlüssel fällt sichtbar auf Deutsch zurück und zählt als Bug.
- Nicht übersetzt werden Firmware-Befehle, CLI-Ausgaben, Board-Namen und Einheiten.

### 4.2 Schreibweise

- Groß- und Kleinschreibung folgt der jeweiligen Sprache, nicht dem Englischen:
  - **DE:** Substantive groß („Admin-Passwort“, „Pakete filtern“), Bindestrich-Komposita.
  - **EN:** Sentence case („Admin password“), kein Title Case in Labels.
  - **FR:** Satzanfang groß, geschütztes Leerzeichen vor `? ! :` („Quitter l'application ?“).
  - **NL:** Satzanfang groß, Zusammenschreibung („Admin-wachtwoord“).
- Die Ansprache ist **du** (DE, Hinweise bevorzugt unpersönlich: „Bitte zuerst …“), *you* (EN), **je** (NL) und **vous** (FR).
- Der Markenname bleibt immer klein: **meshchōsei**, mit ō.

### 4.3 Kompakte Label-Längen

- Drawer:
  - Titel `maxLines = 1`, Untertitel `maxLines = 2`, Eintrag `heightIn(min = 58.dp)`;
  - Untertitel höchstens ~30 Zeichen, zum Beispiel „Kanalen & paden blokkeren“ oder „Bloquer canaux & chemins“.
- Eingabefeld-Labels:
  - höchstens ~25 Zeichen, einzeilig, Grenzen in Klammern: „Admin password (max. 15)“ / „Mot de passe admin (15 max)“;
  - `softWrap = false` und `TextOverflow.Ellipsis` als Sicherheitsnetz, nicht als Normalfall.
- Chips und Badges: ein bis zwei Wörter, 11 sp („Nur Repeater“, „ON“, „BLE-OTA“).
- Verbindungs-Tabs: einzeilig (`maxLines = 1, softWrap = false`). Seit v1.19 heißen die beiden Wege in allen Sprachen einheitlich **„USB-OTG“** und **„BLE-OTA“** (Tabs, Statuskarten-Badge, Hinweis „Wähle USB-OTG oder BLE-OTA“) statt „USB-Serial (OTG)“ / „BLE Companion (Mesh OTA)“.
- **Button-Beschriftungen (verbindlich seit v1.11):** kurz und prägnant, **höchstens 1–2 Wörter und immer einzeilig** – in allen vier Sprachen („Akzeptieren“ / „Accept“ / „Accepteren“ / „Accepter“, „Abbrechen“, „Schließen“, „Speichern“). Im Code `maxLines = 1, softWrap = false`. Was erklärt werden muss, steht im Hinweistext über den Buttons, nicht auf dem Button. So passen zwei Dialog-Buttons auch in FR/NL auf 360 dp kompakt nebeneinander.
- Snackbars: ein Satz, der Grund und nächsten Schritt nennt („Bitte zuerst den aktiven Paketfilter ausschalten.“).
- Prüfmaßstab ist immer die **längste** Sprache, meist FR oder NL, auf 360 dp Breite.

---

## 5. Arbeits- & Release-Konventionen

- Jede Lieferung bekommt `versionCode`/`versionName` neu, dann `./gradlew assembleDebug` (plus `testDebugUnitTest`). Das APK wird nach `/mnt/bunker/RepeaterConfigurator-v<ver>.apk` kopiert.
- Reine Logik (Parser, Validatoren, Menüregeln, Board-Matching) liegt außerhalb der Composables in `model/`/`service/` und hat Unit-Tests.
- Wenn eine Anforderung der Firmware widerspricht, wird die firmware-korrekte Lösung umgesetzt. Die Abweichung wird mit Quellenangabe begründet.
- Committet wird nur auf ausdrückliche Anweisung.
