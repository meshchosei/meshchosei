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

**Ein Spinner pro Ladevorgang:** Während eine Liste neu geladen wird, dreht der Braille-Spinner **nur** im auslösenden `RefreshButton`. Andere Buttons derselben Karte (Hinzufügen, Listen-Schalter, Löschen) sind gesperrt, zeigen aber keinen eigenen Spinner.

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

### 2.4 „Aktualisieren“ / „Neu einlesen“: `RefreshButton`

Jede Aktion, die Werte vom Repeater oder von Geräten neu lädt, ist **ein** einheitlicher `RefreshButton` (`ui/components/BauhausControls.kt`). Kleine blaue Klicktexte („Sync“), rahmenlose `TextButton`s oder reine Icon-Buttons gibt es dafür nicht mehr.

```kotlin
RefreshButton(
    onClick = { viewModel.refreshRegions() },
    busy = BusyKey.REGION in busy,          // optional: Braille-Spinner statt Icon, gesperrt
    label = tr("Aktualisieren")             // Standard; kurz halten ("Neu einlesen", "Abfragen")
)
```

- Kompakter `OutlinedButton` mit `shape = BauhausCornerShape`, 1-dp-Rahmen in der Akzentfarbe (Standard Kobalt) und mindestens 32 dp Höhe.
- Inhalt: das Icon `Icons.Default.Refresh` (16 dp) und ein kurzer Text (12 sp, fett, einzeilig).
- Während des Ladens ersetzt der Braille-Spinner das Icon. Der Text bleibt, der Button ist gesperrt.
- Er steht rechts in der Kopfzeile der betroffenen Card oder Sektion.
- Einsatzorte:
  - **Verbindung:** USB-Geräte, Companion-Kontakte und „Neu einlesen“;
  - **Routing & Adverts:** Regionen;
  - **Filter:** Status und Listen;
  - **Hardware & Antenne:** Versorgung („Abfragen“).
- Nicht gemeint ist „Auffrischen“ im Schonmodus. Dieser Button verlängert den Timer und lädt nichts neu, er bleibt die Primäraktion des Screens.

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

## 3. Kommunikations- & Protokoll-Architektur

### 3.1 Streng sequentielles Request-Response

- Es gibt **einen einzigen Mutex** (`sequentialQueryMutex`) für alle CLI-Befehle: Abfragen, Set-Befehle, Konsole, Pings.
- Es läuft immer genau ein Befehl. Der nächste wird erst gesendet, wenn die Antwort validiert wurde oder der Timeout abgelaufen ist.
- Jede Abfrage ist eine `QuerySpec` mit Befehl, **Validator** und lokalisiertem Statustext.
  - Antworten, die der Validator nicht akzeptiert, sind Fremdzeilen und werden ignoriert.
  - `null` bedeutet Fehler oder „unknown command“.
- **Antwort-Tags über LoRa (seit v1.12):** Jeder Remote-Befehl wird als `XX|befehl` gesendet (`CliReplyTag`, zwei Zeichen `0-9a-z`, laufend). MeshCore (`simple_repeater/MyMesh.cpp`) und ZephCore (`RepeaterMesh::handleCommand`) spiegeln das Präfix an den Antwortanfang. Nur eine Antwort mit genau dem Tag des laufenden Befehls zählt. Antworten mit fremdem Tag (verspätet nach Timeout) oder ohne Tag werden verworfen und im System-Log vermerkt. So landet z. B. eine späte `get name`-Antwort nie in `owner.info`. Der Inhalts-Validator gilt zusätzlich. USB-Direkt und lokale Companion-CLI bleiben ohne Tag.
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
- Chips und Badges: ein bis zwei Wörter, 11 sp („Nur Repeater“, „ON“, „BLE · Mesh OTA“).
- **Button-Beschriftungen (verbindlich seit v1.11):** kurz und prägnant, **höchstens 1–2 Wörter und immer einzeilig** – in allen vier Sprachen („Akzeptieren“ / „Accept“ / „Accepteren“ / „Accepter“, „Abbrechen“, „Schließen“, „Speichern“). Im Code `maxLines = 1, softWrap = false`. Was erklärt werden muss, steht im Hinweistext über den Buttons, nicht auf dem Button. So passen zwei Dialog-Buttons auch in FR/NL auf 360 dp kompakt nebeneinander.
- Snackbars: ein Satz, der Grund und nächsten Schritt nennt („Bitte zuerst den aktiven Paketfilter ausschalten.“).
- Prüfmaßstab ist immer die **längste** Sprache, meist FR oder NL, auf 360 dp Breite.

---

## 5. Arbeits- & Release-Konventionen

- Jede Lieferung bekommt `versionCode`/`versionName` neu, dann `./gradlew assembleDebug` (plus `testDebugUnitTest`). Das APK wird nach `/mnt/bunker/RepeaterConfigurator-v<ver>.apk` kopiert.
- Reine Logik (Parser, Validatoren, Menüregeln, Board-Matching) liegt außerhalb der Composables in `model/`/`service/` und hat Unit-Tests.
- Wenn eine Anforderung der Firmware widerspricht, wird die firmware-korrekte Lösung umgesetzt. Die Abweichung wird mit Quellenangabe begründet.
- Committet wird nur auf ausdrückliche Anweisung.
