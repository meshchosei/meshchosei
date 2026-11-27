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

**Feste Größe für Hinzufügen- und Löschen-Buttons (seit v1.23, verbindlich):**

- Ein Icon-Button neben einem Eingabefeld (`+` in Blacklist/Whitelist, Pfade, Regeln, Watch) ändert seine Größe **nie**. Umgesetzt mit `FixedBusyIconButton`, quadratisch `FixedActionButtonSize` = 56 dp, so hoch wie ein `OutlinedTextField`.
- Im Arbeitszustand ersetzen die Braille-Punkte das Icon. Ein Textwechsel zu „Speichere…“ ist hier verboten, weil er das Feld zusammendrückt und den Platzhalter umbrechen lässt.
- **Löschen in Listenzeilen:** `DeleteBusyIconButton` (48 dp). Die Braille-Punkte erscheinen nur in der Zeile, die gerade gelöscht wird (`deletingId`). Die übrigen Löschen-Icons der Liste sind gesperrt.
- **Spinner sauber getrennt (`ListBusyRules`):** Hinzufügen und Löschen teilen sich je Liste einen `BusyKey`. Der `+`-Button dreht nur beim Hinzufügen. Beim Löschen bleibt er ruhig als `+` stehen, gesperrt, aber ohne Spinner. Nach Ende des Löschens wird die Markierung gelöst.
- **Platzhalter** in Eingabezeilen sind einzeilig (`singleLine = true`, Platzhalter `maxLines = 1` mit Ellipse) und kompakt formuliert, z. B. „Kanal, z. B. regional oder Public“.

**Kanal-Eingabe im Paket-Filter (seit v1.23):**

- Die Eingabe wird vor dem Senden normalisiert (`ChannelNameRules.normalize`):
  - getrimmt;
  - `public` in beliebiger Schreibweise wird exakt zu `Public` (die Firmware prüft `strcmp(name, "Public")`);
  - sonst wird `#` vorangestellt, falls es fehlt (`test` → `#test`);
  - in der Whitelist bleibt ein zweistelliger Hex-Hash (`d9`, `0xD9`) ein Hash, wie es die Firmware erwartet.
- **Public-Schutz:** Im Blacklist-Modus erscheint vor dem Sperren von `Public`/`#Public` (beliebige Schreibweise) ein zentrierter Bauhaus-Bestätigungsdialog (`PublicBlockConfirmDialog`: „Abbrechen“ und signalrotes „Sperren“, kein zusätzliches ✕, Zurück und Tippen daneben brechen ab). Erst danach wird der Befehl gesendet. In der Whitelist wird `Public` ohne Warnung freigegeben. Geprüft in `ChannelNameRulesTest`.

**Ein Spinner pro Ladevorgang:** Während eine Liste neu geladen wird, dreht der Braille-Spinner **nur** im auslösenden ↻ (`InlineRefreshIcon`). Andere Buttons derselben Karte (Hinzufügen, Listen-Schalter, Löschen) sind gesperrt, zeigen aber keinen eigenen Spinner.

**Kompakte Listen-Schalter:** In dichten Listen (z. B. „KONFIGURIERTE REGIONEN“) stehen eckige Bauhaus-Schalter `[HOME] [DEF] [FLOOD]` statt runder Material-Switches. Sie haben `BauhausCornerShape` (2 dp), 28 dp Höhe, Monospace 10 sp fett und eine 1-dp-Outline. Aktiv sind sie vollflächig in ihrer Signalfarbe (HOME = Rot, DEF = Kobalt, FLOOD = Ocker). Während einer Übertragung sind sie gesperrt.

### 2.2.1 Segment-Buttons: gleich breit, Braille ersetzt die Beschriftung (seit v1.24, verbindlich)
**Regel: Segment-Buttons sind gleich breit. Eine laufende Braille-Animation ersetzt zentriert die Beschriftung, ohne das Layout zu verschieben.**
- Mehrere Wahl- oder Filter-Buttons in einer Reihe sind `SegmentButton` (`ui/components/BauhausControls.kt`) und **kein** `FilterChip`. `FilterChip` richtet die Beschriftung links aus und wächst mit Icon oder Text.
- Jeder Button bekommt `Modifier.weight(1f)`, Abstand 8 dp, feste Höhe `SegmentButtonHeight` = 36 dp. Der Inhalt ist zentriert (`Alignment.Center`, `TextAlign.Center`), einzeilig (`maxLines = 1`, Ellipsis), 12 sp.
- Aussehen gewählt: 1-dp-Rahmen und Text in der Akzentfarbe, Fläche in der Akzentfarbe mit 16 % Deckkraft. Nicht gewählt: Rahmen in `outline`, Text in `onSurfaceVariant`.
- **Arbeitszustand:**
  - Während `busy` steht im Button **nur** die zentrierte Braille-Animation, nicht zusätzlich die Beschriftung daneben.
  - Breite und Höhe bleiben exakt gleich, kein Nachbar verschiebt sich.
  - Die Beschriftung bleibt als `contentDescription` für Screenreader erhalten.
- **Übersetzungen bleiben unangetastet:** Bestehende Texte in `i18n.json` werden für das Layout **nie** gekürzt oder umgeschrieben. Eine zu lange Übersetzung (z. B. FR „Chemins actifs uniquement“) endet im Button zentriert mit „…“ (`maxLines = 1`, `softWrap = false`, `TextOverflow.Ellipsis`, wie beim Repeater-Namen). Sie bricht nicht um und verändert die Button-Größe nicht. Der volle Text bleibt als `contentDescription` für Screenreader erhalten.

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

### 2.3.1 Regler: lokaler Zustand + `onValueChangeFinished` + `-`/`+` (seit v1.23, verbindlich)

- **Komponente:** `FineSlider` in `ui/components/FineSlider.kt`. Im Einsatz für das Max. Nachrichtenalter und die Max. Hops im Paket-Filter.
- **Lokaler Zustand:** Während des Ziehens ändert `onValueChange` **nur** den lokalen Wert. Es gibt keinen Befehl, kein Neu-Laden und kein Überschreiben durch den Wert vom Repeater.
- **Senden:** einmalig beim Loslassen (`onValueChangeFinished`). Nach `-`/`+` wird ~0,7 s nach dem letzten Tipp gesendet, mehrere Tipps ergeben einen Befehl.
- **Kein Zurückspringen:** Solange gesendet wird (`busy`), überschreibt der alte Wert den lokalen nicht. Danach gilt der bestätigte Wert, bei Fehler also wieder der alte. Wurde nichts gesendet (`onCommit` liefert `false`), springt der Regler sofort zurück.
  - **Verboten:** `remember(persisted, busy) { … }`. Das setzte den Regler beim Senden auf den alten Wert zurück (Ursache vor v1.23).
- **Raster:**
  - Der Slider ist stufenlos (`steps` nicht gesetzt). Gerundet wird selbst mit `SliderValueRules.snap`, per `roundToInt()` statt `toInt()`, damit nichts verspringt.
  - Grobraster: Alter 5 Min. (0–120), Hops 1 (1–15).
- **Layout (ruhig):**
  - Oben stehen nur der Titel links und der reine Zahlenwert rechts. Der Wert hat eine feste Breite (64 dp, rechtsbündig, Monospace), damit sich bei 1- bis 5-stelligen Zahlen nichts verschiebt.
  - Darunter steht die Zeile `−  [Slider]  +`. `−` und `+` sind rahmenlos und haben eine feste 40-dp-Touch-Fläche. Sie ändern den Wert um 1 und reichen bis zur Firmware-Grenze, also über das Slider-Ende hinaus (Alter bis 10080 Min., Hops bis 64).
  - Umrandete Kästchen neben dem Wert gibt es nicht.
- **Einheit im Titel**, nicht am Wert, z. B. „Max. Nachrichtenalter (in Minuten)“. Der Wert zeigt nur die Zahl, bei 0 „AUS“.
- **Drawer-Schutz:** Die ganze Slider-Zeile trägt `Modifier.excludeFromDrawerGesture()` (siehe §2.6).
- **Labels** nennen keinen Wertebereich, die Grenzen erzwingen Slider und Knöpfe. Geprüft in `SliderValueRulesTest`.

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
- **Alle Menüpunkte immer sichtbar (seit v1.26):** Das Menü zeigt stets alle `NavDrawerItem` in fester Reihenfolge (`DrawerItemRules.items()`). Gesperrte Einträge sind nur ausgegraut (Alpha 0,5), nie ausgeblendet. Die Sperrlogik steht zentral in `DrawerItemRules.isEnabled`; „Firmware / OTA“ folgt `isRepeaterReady || otaActiveOrFinished` (§2.16). Geprüft in `DrawerItemRulesTest`.
- **Dichte der Menüeinträge (seit v1.26):** `NavigationDrawerItem` mit `padding(horizontal = 8.dp, vertical = 0.dp)` und `heightIn(min = 56.dp)` (vorher 1 dp / 58 dp); der Kopfbereich hat oben 18 statt 20 dp Innenabstand. So stehen die Einträge je 4 dp enger, und alle Menüpunkte inklusive „Firmware / OTA“ passen ohne Scrollen ins Seitenmenü. Ein neuer Menüpunkt darf diese Dichte nicht weiter verringern; passt er nicht mehr, werden Einträge zusammengelegt statt die Abstände weiter zu kürzen.
- **Interaktiver Bauhaus-Drawer (seit v1.22, verbindlich):**
  - Ersetzt `ModalNavigationDrawer`, bei dem das Menü im geschlossenen Zustand nur an der ~20-dp-Kante reagierte (Konflikt mit der System-Zurück-Geste) und leicht zurückschnappte.
  - Technik: `BauhausModalDrawer` mit `rememberBauhausDrawerState()` in `ui/components/BauhausDrawer.kt`. Die Position ist ein `Animatable` von 0 (zu) bis 1 (offen), der Offset läuft von `-drawerWidth` bis `0`.
  - Aussehen wie bisher: `ModalDrawerSheet` mit 316 dp links, daneben das Scrim (`DrawerDefaults.scrimColor`, Deckkraft wächst mit der Position). Tippen auf das Scrim schließt.
  - Der State hat dieselbe Oberfläche wie Material `DrawerState` (`isOpen`, `isClosed`, `targetValue`, `open()`, `close()`). `isOpen` gilt schon während der Öffnen-Animation, damit Zurück sofort schließt.
  - **Live-Tracking (`PointerEventPass.Initial`, ganze Fläche):** Eine waagerechte Geste ab 12 dp mit `|dx| > |dy| · 1,4` übernimmt der Drawer, solange er sich in diese Richtung bewegen kann. Das Menü folgt dann 1:1 dem Finger, und die Ereignisse werden konsumiert, damit Scroller und Buttons nichts abbekommen.
  - **Einrasten beim Loslassen:** Ab 35 % Weg in Gestenrichtung, beim Öffnen wie beim Schließen, oder bei zügigem Wisch über 400 dp/s rastet das Menü ein. Darunter gleitet es in die Ausgangslage zurück (`DrawerGesture.settleOpen`).
  - **Kein Konflikt:** Hat ein Kind-Element die Bewegung im `Main`-Pass schon konsumiert, bleibt der Drawer für diese Geste unbeteiligt. Das betrifft Slider, Slide-to-Confirm, die Karte und einen bereits laufenden vertikalen Scroll. Übernommen wird erst nach mindestens einem beobachteten `Final`-Pass, damit auch ein schneller Wisch zuerst beim Slider ankommt.
  - **Schließen:** durch Scrim-Tippen, System-Zurück (`BackHandler`) oder interaktives Zuziehen nach links.
  - **Reservierte Flächen (seit v1.23):**
    - Elemente mit eigener waagerechter Geste melden ihre Bounds mit `Modifier.excludeFromDrawerGesture()` an (Registry `LocalDrawerGestureExclusions`, Root-Koordinaten).
    - Setzt der Finger bei geschlossenem Menü in so einer Fläche auf, übernimmt der Drawer die Geste nie. Das gilt schon ab dem Down-Event im `Initial`-Pass, unabhängig davon, wann das Kind konsumiert.
    - Pflicht für: `FineSlider`-Zeilen, `SlideToConfirm` und die Karte (`OsmTileMap`). Jeder neue Slider bzw. jede neue horizontale Drag-Fläche bekommt den Modifier.
    - Ein offenes Menü lässt sich weiterhin über allem zuziehen.
  - Geprüft in `DrawerGestureTest`.
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

### 2.6.1 Aufzählungen mit hängendem Einzug (seit v1.26, verbindlich)
- Aufzählungen mit `•` werden nie als ein einziger `Text` mit eingebetteten Punkten gesetzt, weil Folgezeilen sonst unter den Punkt rutschen.
- Stattdessen `BulletList` (`ui/components/BulletList.kt`): je Eintrag eine `Row` mit fester Bullet-Spalte (`•` in `BulletColumnWidth` = 14 dp) und rechts `Text(modifier = Modifier.weight(1f))`. So stehen 2. und 3. Zeile bündig unter dem Textanfang.
- Ressourcentexte dürfen weiter als „• a\n• b“ gepflegt werden; `BulletListRules.items()` zerlegt sie und entfernt das führende `•`.
- Erster Einsatz: „Verwendete Open-Source-Komponenten“ in „Über & Lizenz“.

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
- **Schließen:** kleines `✕` (Box 22 dp, Icon `CloseIconSize` = 16 dp, eckig, `Icons.Filled.Close`, 2 dp Abstand, Grund `surfaceVariant`) bündig oben rechts in der Ecke des Containers. Die 26 dp rechts in jeder Zeile halten es frei. Außerdem schließt System-Zurück (`BackHandler(enabled = menuOpen)`).
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
  2. Kopfzeile „Repeater-Auswahl (n)“ und Segment-Buttons `Gespeichert` | `Live` | `Nur aktive Pfade` in einer Zeile (seit v1.24, §2.14).
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
- **Dialog statt Banner:** Der Verbindungs-Screen bleibt unverändert. Sobald eine neuere Version erkannt ist, der Hinweis in dieser Sitzung noch nicht geschlossen wurde und kein Haftungsausschluss-Dialog offen ist, zeigt `MainActivity` einen **zentrierten** `Dialog` (`ui/components/UpdateDialog.kt`): eckige `Surface` (`BauhausCornerShape`, 1-dp-Rahmen `outline`), oben mittig die Meldung „Neue Version vX.YZ verfügbar“ (16 sp fett, über `tr()`, in der Debug-APK darunter `[DEBUG-Test]`), darunter die Release-URL (`html_url`, Fallback `…/releases/latest`) in Kobalt, Monospace, unterstrichen, antippbar (`Intent.ACTION_VIEW`), unten horizontal zentriert ein `✕` (`IconButton`, `Icons.Filled.Close`, Icon `CloseIconSize`). `✕`, System-Zurück und Tippen neben den Dialog schließen ihn für die laufende Sitzung. Im „Über & Lizenz“-Screen gibt es keinen Update-Hinweis.

### 2.12 Info-Dialoge schließen: zentriertes `✕` (seit v1.21)

- Reine Info-Dialoge ohne Entscheidung schließen mit **einem** zentrierten `✕` unten in der Mitte (`IconButton` mit 48-dp-Touch-Fläche, `Icons.Filled.Close` in `CloseIconSize`, `onSurfaceVariant`, `contentDescription = tr("Schließen")`), nicht mit einem Text-Button „Schließen“. System-Zurück und Tippen neben den Dialog schließen ebenfalls.
- **Einheitliche ✕-Größe (verbindlich seit v1.21):** Jedes Schließen-`✕` (`Icons.Filled.Close`) – Klappmenüs (§2.10), Update-Dialog (§2.11), Haftungsausschluss – zeichnet das Icon in genau **`CloseIconSize` = 16 dp** (`ui/components/BauhausControls.kt`). Nur die Touch-Fläche darf je nach Ort größer sein (22-dp-Box in der Menü-Ecke, `IconButton` 48 dp in Dialogen). Keine eigenen Größen im Code; geprüft durch `CloseIconSizeTest`. Das Leeren-Icon im Suchfeld (`Icons.Default.Clear`) ist kein Schließen-✕ und fällt nicht darunter.
- Einsatzorte: Update-Dialog (§2.11) und der **Haftungsausschluss beim erneuten Ansehen** aus „Über & Lizenz“ / Seitenmenü (`DisclaimerDialog(isInitialAcceptance = false)`).
- Ausnahme: Beim **Erststart** bleibt der Haftungsausschluss ein Entscheidungsdialog mit den Text-Buttons „Abbrechen“ (beendet die App) und „Akzeptieren“; er ist dort nicht wegklickbar.

### 2.13 Türkanten-Lichtschein als Rückmeldung (seit v1.24)
`EdgeLightEffect` (`ui/components/EdgeLightEffect.kt`) zeigt einen Lichtspalt an der Bildschirmkante, wie eine Taschenlampe hinter einer geschlossenen Tür im Dunkeln:
- **Maßgeblich ist das Zielgerät, nicht der Transport (seit v1.25):**
  - **Rechts, Kobaltblau:** Die App ist mit einem **Companion-Node** verbunden (USB-OTG oder BLE), über den anschließend andere Repeater per Remote-Admin gesteuert werden.
  - **Links, Signalgrün:** Das Ziel ist ein **Repeater**. Das gilt beim erfolgreichen Remote-Admin-Login auf einen Repeater und ebenso bei einer **direkten USB-Verbindung zu einem Repeater**.
  - „USB verbunden“ allein löst nichts aus. Der Schein kommt erst, wenn die Verbindung steht **und** die Rolle feststeht (direkter Repeater bzw. Companion, `deviceCapabilities`).
  - **Kein Eingriff in den Verbindungsablauf (verbindlich):** USB-Handshake und Protokollerkennung bleiben unverändert. Ein Beobachter im ViewModel liest nur `connectionState` + `deviceCapabilities` und meldet je Verbindung genau einmal die Seite (`EdgeRoleWatch`). Eine Beruhigungszeit von 250 ms verhindert, dass die Rolle der vorigen Verbindung kurz nach „Connected“ einen falschen Schein auslöst.
  - Regel: `EdgeLightTrigger.sideForConnectedDevice(isDirectRepeater, isCompanion)`. Der Remote-Login (`onLoginSuccess`) nutzt `SIDE_FOR_REPEATER_LOGIN`, also links.
- **Auslöser (verbindlich):**
  - Das `RepeaterViewModel` setzt `edgeLightTrigger = EdgeLightTrigger(id = System.nanoTime(), side)` (`model/EdgeLightTrigger.kt`) **direkt an der Erfolgsstelle**. Es gibt genau zwei Auslöser: den Rollen-Beobachter (`EdgeRoleWatch`; Companion rechts, direkter Repeater links) und `onLoginSuccess()` (links).
  - Die UI merkt sich `lastPlayedId` per `rememberSaveable` und spielt jede id genau einmal ab (`id > lastPlayedId`).
  - Kein Flankenvergleich im Composable: Er geht bei Neuaufbau verloren oder löst bei Display-Drehung doppelt aus.
- **Animation über die echte Zeit (verbindlich):**
  - Die Animation läuft per `withFrameNanos` und `EdgeLightRules.progressAt(ms)` mit `FastOutSlowInEasing`, **nicht** über `Animatable` oder `tween`.
  - Compose-Animationen übernehmen die System-Animationsskala. Bei „Animator-Dauer aus“ (Entwickleroptionen, Energiesparmodus) springen sie sofort ans Ende, und der Effekt bliebe unsichtbar.
  - Jede Seite hat ihren eigenen Job. Wird ein Job abgebrochen, wird der Schein sauber ausgeblendet.
- **Lage:**
  - Das Overlay ist das allerletzte Kind der obersten Root-Box in `MainActivity.setContent`, über Splash, Screens, Cards und `BauhausModalDrawer`, mit `Modifier.fillMaxSize().zIndex(999f)`.
  - Es hat kein `systemBarsPadding` und kein `clipToBounds` und zeichnet über die volle Fläche (`size.width` × `size.height`).
  - Material-`Dialog`s sind eigene Fenster; dort kann kein Overlay der App darüberliegen.
- **Optik:**
  - Kernlinie: 3 dp direkt an der äußersten Kante (links ab `x = 0`, rechts bis `x = size.width`). Im Dunkelmodus ist sie `Color.White`; im Hellmodus eine aufgehellte Kantenfarbe, weil Weiß auf Weiß unsichtbar wäre. Alpha höchstens 0,95.
  - Lichtkegel: 64 dp breiter `Brush.horizontalGradient` in der Kantenfarbe, von `MAX_ALPHA` 0,92 am Rand bis 0.
  - Senkrecht blendet eine `DstIn`-Maske den Schein nach oben und unten weich aus. Die Mitte wandert dabei von 30 % auf 70 % der Bildhöhe.
  - Dauer 1,5 s, Helligkeit als Halbwelle (sanft auf, sanft ab).
- **Nicht blockierend:** kein `pointerInput`, keine Semantik, kein Layout-Platz.
- **Nur echte Ereignisse:** Der Schein erscheint ausschließlich beim echten Verbinden und beim echten Login. Es gibt keinen Test-Auslöser in der Oberfläche.
- **TopBar bleibt ruhig:** links nur `☰` und der Titel, kein Logo. Das ō-Emblem steht nur im Kopf des Seitenmenüs und im Splash.

### 2.14 Repeater-Auswahl: `Gespeichert` | `Live` | `Nur aktive Pfade` (seit v1.24)
Über dem Suchfeld der Repeater-Auswahl stehen in **einer** Zeile drei gleich breite Segment-Buttons `Gespeichert` | `Live` | `Nur aktive Pfade` (`SegmentButton`, je `weight(1f)`, §2.2.1), alle mit ihren Originaltexten. Rechts neben dem Suchfeld bleibt nur das ruhige ↻ (Kontakte neu einlesen); dort gibt es kein weiteres Mini-Icon.
- **Gespeichert:** die im Companion gespeicherten Kontakte. Gezeigt werden immer nur Repeater und Room-Server.
- **Live:**
  - Ein Tipp startet sofort eine Funksuche nach Repeatern in **direkter** Reichweite und öffnet die Trefferliste.
  - Während des 4-s-Scan-Fensters ersetzen zentrierte Braille-Punkte das Wort „Live“; der Button behält seine Größe. Ein erneuter Tipp sucht neu.
  - „Gespeichert“ und ✕ beenden die Live-Ansicht.
- **↻ kontextabhängig (seit v1.25):** Im Tab **Live** löst ↻ einen neuen Live-Scan aus und bleibt in „Live“ (Braille im Button „Live“). Im Tab **Gespeichert** lädt ↻ die gespeicherten Kontakte vom Companion neu (Braille im Button „Gespeichert“). Das ↻ selbst bleibt immer ein ruhiges Icon. Die Bedienhilfe lautet „Live-Suche wiederholen“ bzw. „Kontakte neu einlesen“.
- **Einheitliche Lade-Anzeige (seit v1.25):** Werden die gespeicherten Kontakte geladen, beim Verbinden oder über ↻ neu eingelesen, ersetzt die zentrierte Braille-Animation das Wort „Gespeichert“ **im Segment-Button**, genau wie bei „Live“. Die Button-Breite bleibt gleich. Das ↻ neben dem Suchfeld bleibt dabei ruhig ein ↻-Icon und wird selbst nicht zum Spinner. Fortschritt zeigt in dieser Leiste immer der betroffene Segment-Button (§2.2.1).
- **Nur aktive Pfade:** filtert auf Einträge mit bekanntem Routing-Pfad. Live-Treffer sind immer direkt (0 Hops).
- **Protokoll (`service/CompanionProtocol.kt`, MeshCore = ZephCore):**
  - Die App sendet `CMD_SEND_CONTROL_DATA [0x37][0x80][1<<ADV_TYPE_REPEATER][tag(4)][since(4)=0]`. Der Companion sendet das als Zero-Hop.
  - Jeder Repeater in Reichweite antwortet. Beim Companion kommt die Antwort als Push `[0x8E][SNR×4][RSSI][path_len][0x92][Remote-SNR×4][tag][pub_key(32)]` an.
  - Ausgewertet werden nur Antworten von **Repeatern** mit unserem Tag und vollem Public-Key.
  - Spätere Antworten derselben Suche werden noch übernommen. Je Repeater gibt es einen Eintrag mit der jüngsten Messung.
  - Repeater mit `disable_fwd`, versteckte Repeater und solche mit vollem Discover-Limiter antworten nicht.
- **Anzeige:**
  - Sortiert wird strikt nach SNR absteigend, bei Gleichstand nach RSSI (`LiveScanRules.sortBySignal`).
  - Rechts stehen in Monospace `SNR +11,5 dB` (Dezimalkomma außer im Englischen) und darunter `−87 dBm`.
- **Namen (verbindlich):**
  - Die Discover-Antwort enthält **keinen Namen**. Jeder Treffer wird deshalb sofort per Public-Key mit den gespeicherten Kontakten abgeglichen (`LiveScanRules.findKnown`), und deren Name wird übernommen.
  - Ohne bekannten Namen zeigt die Zeile das kurze Key-Präfix als Platzhalter, z. B. `cf3a8206…`, mit dem Hinweis „neu“. Auch „Node-xxxxxxxx“ gilt als Platzhalter.
  - Sobald `get name` vom gewählten Repeater gelesen wurde (Start-Scan oder ↻), zieht `LiveScanRules.applyFetchedName` den echten Namen sofort an drei Stellen nach, jeweils für den gleichen Public-Key: im gewählten Repeater des Login-Bereichs, im Live-Eintrag (`LiveRepeater.name`) und im Kontakt.
  - Ein Login ist dafür keine Voraussetzung, die Antwort kam ja vom Repeater. Der gewählte Repeater wird atomar aktualisiert (`MutableStateFlow.update`), damit ein gleichzeitiges Status-Update den Namen nicht überschreibt.
  - Der gelernte Name bleibt erhalten:
    - bei einem neuen Live-Scan (`merge` behält den Namen);
    - beim Neu-Einlesen der Kontakte, wenn im Companion noch der Platzhalter steht (`keepLearnedName`).
  - **Anzeige-Vorrang:** Statuskarte und Login-Bereich zeigen `LiveScanRules.shownName`. Ein live per `get name` gelesener, nicht leerer Name (`viewModel.readRepeaterName`) hat Vorrang vor dem gespeicherten Namen, sofern er zum gewählten Repeater gehört.
  - **Key-Vergleich:** `LiveScanRules.sameKey` vergleicht ohne Groß-/Kleinschreibung über das gemeinsame Hex-Präfix (mindestens 8 Hex-Zeichen), weil Listen teils nur 8/12 Zeichen oder den vollen Key halten.
- **StateFlow und `equals` (verbindlich):**
  - Modelle in einem `StateFlow` dürfen `equals` **nicht** auf eine ID verkürzen. `MutableStateFlow` verwirft jeden Wert, der dem aktuellen gleicht.
  - Bis v1.24 verglich `MeshContact.equals` nur `pubKeyHex`. Ein umbenannter Repeater galt deshalb als unverändert, und der Name aus `get name` kam nie im Verbindungs-Screen an.
  - Heute vergleicht `equals` alle Werte. Für „derselbe Repeater?“ wird immer `pubKeyHex` bzw. `sameKey` verglichen, nie `==`. Abgesichert durch `LiveRepeaterNameAfterLoginTest`.
- **Übernahme:**
  - Ein Tipp übernimmt den Repeater mit Public-Key und gespeichertem Passwort in den Login-Bereich.
  - Ein unbekannter Repeater wird vorher per `CMD_ADD_UPDATE_CONTACT` (0x09, Typ 2, **`out_path_len = OUT_PATH_UNKNOWN` (0xFF)**, seit v1.25) im Companion angelegt. Sonst lehnt der Companion `CMD_SEND_LOGIN` ab (`lookupContactByPubKey`). Mit „direkt“ (0) würde MeshCore den Login nur als Zero-Hop senden, ohne Flood-Versuch. Mit 0xFF darf der Companion fluten und lernt den Pfad aus der Antwort.
  - Nach `get name` wird der Kontakt **nicht** erneut per `CMD_ADD_UPDATE_CONTACT` umbenannt, weil das den gelernten Pfad wieder löschen würde. Der echte Name bleibt in der App.

### 2.15 Debug-Bericht für Fehlermeldungen (seit v1.25, verbindlich)
- **Ort:** „Über & Lizenz“, Karte **„Fehler melden“** unter der Datenschutzerklärung, mit dem Button **„Debug-Informationen sammeln“** (Kobalt, volle Breite).
- **Erzeugung:** `DebugReportBuilder` (`service/DebugReportBuilder.kt`) ist rein und unit-getestet. Der ViewModel liefert nur die Werte (`buildDebugReport(palette)`). Der Bericht ist eine Momentaufnahme beim Antippen, nie im Hintergrund, und es wird **nichts automatisch übertragen**.
- **Format:** Markdown für GitHub Issues mit deutschen Überschriften. **Companion-Node und Ziel-Repeater stehen in getrennten Abschnitten:**
  - `## App & Gerät`: Version und Code, Build-Typ, Android und SDK, Hersteller und Modell, Sprache, Palette, Farbschema, Verbindungsstatus.
  - `## Companion-Node`: Verbindungsart (USB-OTG/BLE), Node-Name, Board/Modell, Firmware-Typ und -Version, Build-Datum, Protokollversion, Public-Key-Präfix, Funkparameter, Sendeleistung, max. Kontakte und Kanäle. Ohne Companion steht dort „nicht verbunden“ bzw. „Direktverbindung“.
  - `## Ziel-Repeater`: Zugang (direkt per USB-CLI / Remote-Admin über Companion), Name, eingeloggt, Board, Funkchip, Firmware-Typ und -Version, Bootloader, Fähigkeiten. Sonst steht dort „nicht verbunden / nicht eingeloggt“. Hardwaredaten eines Remote-Repeaters erscheinen erst nach dem Login, damit keine veralteten Werte einer früheren Sitzung im Bericht landen.
  - `## Meldungen`, `## CLI-Protokoll`, `## System-Protokoll`, jeweils als Code-Block.
  - Firmware-Typ: „ZephCore“, wenn die Version „zeph“ enthält, sonst „MeshCore“.
- **Companion-Daten (`CompanionNodeInfo`):**
  - Quelle sind `RESP_SELF_INFO` (Antwort auf APP_START) und `RESP_DEVICE_INFO` (Antwort auf `CMD_DEVICE_QUERY`). Die Geräteabfrage geht einmal pro Verbindung erst raus, wenn `SELF_INFO` **und** `END_OF_CONTACTS` empfangen wurden (§3.6).
  - Die Daten gelten nur für die laufende Node-Verbindung und werden beim Trennen geleert.
  - **Nie übernommen** werden die BLE-PIN (DEVICE_INFO) und die GPS-Position des Companions (SELF_INFO).
- **Schwärzung (`sanitize`, Pflicht vor jeder Anzeige):**
  - Ersetzt durch `[REDACTED]` werden die Werte von `prv.key`, `wifi.pass`, `mqtt.*.password`, `admin.`/`guest.password`, `password …` sowie Hex-Ketten ab 96 Zeichen (privater Schlüssel).
  - Die Antwortzeile direkt nach `get prv.key` bzw. `get …pass…` wird komplett geschwärzt.
  - Alle in der App gespeicherten Admin-Passwörter werden überall wörtlich ersetzt.
  - Public-Keys (64 Hex) bleiben sichtbar, weil sie für die Analyse nötig und nicht geheim sind.
- **Dialog:**
  - zentriert, Surface mit `BauhausCornerShape` und 1-dp-Rahmen, `✕` oben rechts (`CloseIconSize` = 16 dp);
  - kurzer Hinweis, was enthalten ist und dass man Zeilen entfernen kann;
  - scrollbare Vorschau in Monospace (`hintedVerticalScroll`), genau mit dem Text, der geteilt wird.
- **Aktionen:**
  - Zwei gleichwertige, gleich breite Aktionen: **„Kopieren“** (Zwischenablage; danach erscheint der Link „GitHub-Issue öffnen“ → `…/issues/new`) und **„Als Datei speichern“** (`ActivityResultContracts.CreateDocument("text/plain")`, Name `meshchosei-debug-JJJJMMTT-HHMM.txt`).
  - Eine Rückmeldung erscheint als kleine Statuszeile im Dialog.
- **Datenschutz:** Abschnitt 5 in `PRIVACY.md` (DE und EN) und `about_privacy_debug_body` in der Datenschutz-Karte (4 Sprachen). Meldungen werden nur im Arbeitsspeicher gesammelt (letzte 60).

### 2.16 Firmware / OTA-Update per Nordic BLE DFU (seit v1.26, verbindlich)
- **Ort:** eigener Menüpunkt **„Firmware / OTA“** (`NavDrawerItem.FIRMWARE_OTA`, `OtaScreen.kt`) zwischen „Backup“ und „Über & Lizenz“. Er ist ausgegraut, bis ein **Ziel-Repeater bereit** ist (direkt per USB verbunden oder per Remote-Admin eingeloggt; ein bloß verbundener Companion-Node reicht nicht, weil dann offen ist, wohin `start ota` geht), **außer** ein OTA-Vorgang ist aktiv oder noch nicht abgeschlossen (`OtaFlowRules.menuEnabled(isRepeaterReady, otaActiveOrFinished)`). So kommt man während eines Updates jederzeit zurück.
- **Technik:** offizielle Nordic Android DFU Library `no.nordicsemi.android:dfu` **2.8.0**. Das ist die neueste Version ohne Kotlin-2.x-Abhängigkeit (ab 2.9.0 kotlin-stdlib 2.x, Projekt: Kotlin 1.9).
  - `service/dfu/DfuService` erweitert `DfuBaseService` (Vordergrund-Dienst, `foregroundServiceType="connectedDevice"`). Die Benachrichtigung führt zurück zur `MainActivity`.
  - Manifest: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `POST_NOTIFICATIONS` (Android 13+, wird erst beim Start optional angefragt; das Update läuft auch ohne).
  - Benachrichtigungskanal einmalig per `DfuServiceInitiator.createDfuNotificationChannel`.
- **Feste DFU-Einstellungen, keine Expertenoptionen in der UI** (`FirmwareUpdateController.start`):
  - Packet Receipt Notifications an, alle **12** Pakete;
  - `setKeepBond(true)`, `setForceDfu(false)`;
  - Resume bleibt aktiv (Bibliotheksstandard; 2.8.0 hat kein `setDisableResume(false)`, nur das Abschalten);
  - `setMtu(247)`, `setForeground(true)`.
- **Firmware-Fakten (am Quellcode geprüft):**
  - MeshCore nRF52 `start ota` bleibt in der Anwendung, startet den Adafruit-Legacy-DFU-Dienst (`00001530-…`) unter dem Namen `<BOARD>_OTA` und antwortet `OK - mac: AA:BB:…`. Den Sprung in den Bootloader macht die Nordic-Bibliothek selbst (Buttonless).
  - ZephCore nRF52 `start ota` setzt GPREGRET 0xA8 und startet direkt im BLE-OTA-Bootloader neu. Die Antwort kommt deshalb oft nicht mehr an.
  - Bootloader melden sich als `DfuTarg` (Secure DFU, `FE59`) oder `AdaDFU` (Legacy). **Nicht** jedes Gerät nennt sich `DfuTarg`; der Scan erkennt alle drei Fälle (`DfuScanRules.classify`).
  - ESP32 (WLAN-/HTTP-OTA, MCUmgr) ist mit Nordic DFU nicht kompatibel: Option A ist für ESP32-Boards gesperrt (`OtaTargetRules.nordicCapable`), ESP32-Antworten gelten als Fehler mit Erklärung.
- **Drei Schritte als nummerierte Karten** (Nummernkästchen in Kobalt / Signalrot / Grün, ✓ wenn erledigt):
  1. **Firmware-Paket wählen:** `OpenDocument` für `application/zip`. Das Paket wird in den App-Cache kopiert (`cacheDir/dfu/firmware.zip`), damit der Dienst unabhängig von der Dokument-Freigabe lesen kann. Geprüft werden Endung `.zip`, Größe (0 < x ≤ 4 MB) und Inhalt (`manifest.json` oder `.bin`/`.hex` + `.dat`). Angezeigt werden Dateiname und Größe sowie immer der Ocker-Hinweis, nur passende Board-Firmware zu flashen (mit dem verbundenen Board, wenn bekannt).
  2. **Zielgerät wählen:**
     - **A** – verbundenen Repeater (USB direkt oder Remote-Admin eingeloggt) per `start ota` in den Bootloader versetzen. Der Befehl läuft genau einmal unter `sequentialQueryMutex`; ein aktiver Schonmodus wird vorher beendet. Danach sucht die App 30 s nach dem DFU-Gerät und wählt **nur bei eindeutigem Treffer** automatisch (MAC aus der Antwort, MAC+1, gemeldeter Name, sonst genau ein neues DFU-Gerät). Mehrdeutig heißt: Der Nutzer wählt aus der Liste.
     - **B** – eigener BLE-Scan (20 s, Low Latency) nach DFU-Geräten. Die Liste ist nach RSSI sortiert und zeigt eckige Signalbalken, Name, Adresse, DFU-Art und dBm.
     - Der erwartete Verbindungsabbruch nach `start ota` erzeugt keine Warnung „Verbindung getrennt“, nur einen Eintrag im System-Protokoll.
  3. **Bestätigen & Flashen:** Start nur per `SlideToConfirm`. Vorher werden BLE-Scans gestoppt und die bestehende Verbindung (GATT/USB) sauber getrennt (§3.7), dann 800 ms gewartet, damit der DFU-Dienst den BLE-Chip exklusiv hat.
- **Fortschritt in 4 Phasen** (`PhaseList`): erledigt `✓` grün, aktiv Braille-Spinner in Kobalt, abgebrochen/fehlgeschlagen `✕` rot, offen `○`.
  1. Bootloader aktivieren (`onDeviceConnecting`, `onEnablingDfuMode`);
  2. DFU-Initialisierung (`onDfuProcessStarting/Started`, `onFirmwareValidating`);
  3. Firmware-Upload mit **eckigem** Fortschrittsbalken (`SquareProgressBar`, kein runder Material-Indikator), `0–100 %` in Monospace und Übertragungsrate in kB/s; bei mehrteiligen Paketen „Teil x von y“;
  4. Fertig / Neustart (`onDfuCompleted`).
  - Während des Laufs gibt es nur „Abbrechen“ (rot umrandet), danach „Zurücksetzen“ (leert alles für einen neuen Durchlauf).
- **Zustandsmaschine `OtaStateMachine` (rein, unit-getestet):**
  - Start nur mit Paket und Ziel; kein Doppelstart; Auswahl während des Laufs gesperrt.
  - Phasen laufen vorwärts. Einzige Ausnahme: DFU-Init → Bootloader aktivieren, solange 0 % übertragen sind (Buttonless-Sprung der Bibliothek).
  - Prozent steigen nur und werden auf 0–100 begrenzt; je Teil eines mehrteiligen Pakets beginnt die Zählung neu.
  - Nach Fertig, Abbruch oder Fehler werden späte Bibliotheksereignisse ignoriert; der Schritt des Abbruchs bleibt sichtbar (`stoppedAtStep`).
  - `Reset` liefert einen komplett leeren Zustand (`OtaState()`).
- **Kein Zurückspringen bei gewolltem Abbruch:** Nach `start ota` bzw. dem Trennen vor dem DFU fällt die Verbindung absichtlich weg. Wer im OTA-Screen ist, bleibt dort und sieht den Fortschritt bis „Fertig“ (`OtaFlowRules.resetToConnectionOnDisconnect`). Ohne OTA-Vorgang und auf anderen Screens gilt weiter: zurück zum Verbindungs-Screen.
- **„Nach Update automatisch neu verbinden“** (`BauhausSwitch` in Karte 3, Standard an, gespeichert):
  - Beim ersten OTA-Schritt merkt sich die App die Verbindung (`OtaReconnectPlan`: BLE-Adresse bzw. USB-Gerät mit Vendor/Product-ID, Baudrate, eingeloggter Remote-Repeater).
  - Nach erfolgreichem Update (oder beim Verlassen bzw. „Zurücksetzen“ danach) verbindet sie sich nach **2,5 s** Neustart-Pause genau einmal neu. Ein USB-Gerät mit neuer ID wird über Vendor/Product-ID wiedergefunden.
  - War vorher ein Repeater eingeloggt, wird er nach der Companion-Erkennung wieder ausgewählt und mit dem gespeicherten Passwort angemeldet.
  - Nach Abbruch oder Fehler wird nicht neu verbunden.
- **Aufräumen:** Nach Abschluss (Fertig, Abbruch, Fehler) leert das Verlassen des OTA-Screens oder „Zurücksetzen“ **alle** DFU-Felder: Paket samt Cache-Kopie, Ziel, Trefferliste, Fortschritt, Status, `start ota`-Status und die OTA-Sitzung. Während eines Laufs bleibt alles erhalten.
- **Wachhalten:** Der Bildschirm bleibt während Scan, Bootloader-Suche und Upload an (`ScreenWakeRules.keepScreenOn(…, firmwareUpdateActive)`), auch wenn die App-Verbindung schon getrennt ist.

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

### 3.5 Statistik-Anzeigen nur aus echten Firmware-Werten (seit v1.22)

- **Nur anzeigen, was die Firmware ausgibt.** Zähler ohne Quelle in der Firmware (z. B. „durchgelassen“, „Drop-Rate“) werden nicht geschätzt, sondern weggelassen. Werte, die noch nicht gelesen wurden, zeigen „—“, nie `0`.
- **Filter-Statistik:** Die Quelle ist ZephCore/MeshCore `FilterStats.h`.
  - `filter` liefert `> Filter on|off: Blocked [ Hops: n | Rate: n | Channel: n | Hash: n | Malformed: n ]`, bei aktivem Dry-Run gefolgt von ` (dry-run)`.
  - `filter stats air` liefert `> Filter: saved airtime <ms> ms (…)` oder `> Filter: no airtime saved yet`.
  - `filter stats` ohne Thema ist nur Hilfetext.
  - Ablehnungen im Whitelist-Modus zählt die Firmware in keiner Kategorie; sie zeigen sich nur in der gesparten Airtime. Die Whitelist-Ansicht sagt das in einem kurzen Hinweis.
- **Aktualisierung:** Die Statistik wird beim Öffnen des Screens still gelesen (`refreshFilterStats`, sequentiell, mit Tag und Retry) und zusätzlich mit dem ↻ der Kopfzeile. Bei Gerätewechsel oder neuem Start-Scan werden die Zähler geleert, nach `filter RESET` auf 0 gesetzt.
- **Parser:** Er ist tolerant gegen gekürzte Antworten (`..`): angekommene Felder zählen, fehlende Felder zählen 0. Andere Statistik-Themen (`Blocked n [1B:…`, `dropped n`) dürfen die Zusammenfassung nie überschreiben. Geprüft in `FilterStatisticsTest`.

### 3.6 Companion-Handshake & Fehlercodes (seit v1.25, verbindlich)
- **Firmware-Befund** (MeshCore `companion_radio/MyMesh.cpp`, ZephCore `app/CompanionMesh.cpp`): Von den Befehlen dieser App liefert **nur `CMD_GET_CONTACTS`** `ERR_CODE_BAD_STATE` (4), und zwar während der Kontakt-Iterator noch läuft. `CMD_APP_START` beendet den Iterator. `CMD_DEVICE_QUERY`, `CMD_ADD_UPDATE_CONTACT` und `CMD_SEND_LOGIN` liefern nie Code 4.
- **Ursache beim ThinkNode M1:** Beim BLE-Connect ging `CMD_GET_CONTACTS` doppelt raus: einmal über den ersten empfangenen Frame (Umschalten auf Companion) und einmal fest nach 700 ms. Bei vielen Kontakten antwortete der Node auf die zweite Anfrage mit `BAD_STATE`.
- **Regeln (`model/CompanionHandshake.kt`, unit-getestet):**
  - Höchstens **ein** `CMD_GET_CONTACTS` gleichzeitig. Weitere sind erst erlaubt nach `END_OF_CONTACTS`, nach `APP_START` oder nach 15 s ohne Kontakt-Frame.
  - `CMD_DEVICE_QUERY` geht einmal pro Verbindung raus, erst nach `SELF_INFO` **und** `END_OF_CONTACTS`. Es gibt keinen festen Timer.
  - Beim Trennen wird alles zurückgesetzt.
- **Fehlerzuordnung:** Ein `RESP_ERR` (0x01) bricht einen laufenden Login **nur** ab, wenn der zuletzt an den Companion gesendete Befehl `CMD_SEND_LOGIN` war (`errorBelongsToLogin`). Vorher beendete jeder Fehler den Login, auch ein `BAD_STATE` auf `GET_CONTACTS`.
- **Klartext im System-Protokoll:** `< [Fehler 0x01] Code=4 (BAD_STATE) auf GET_CONTACTS`. Die Namen lauten 1 `UNSUPPORTED_CMD`, 2 `NOT_FOUND`, 3 `TABLE_FULL`, 4 `BAD_STATE`, 5 `FILE_IO_ERROR`, 6 `ILLEGAL_ARG`. Der Login-Status zeigt `Fehler (2 NOT_FOUND)`.

### 3.7 BLE-Stabilität: Bildschirm wach halten & sauberes GATT-Trennen (seit v1.25, verbindlich)
- **`FLAG_KEEP_SCREEN_ON`:**
  - `MainActivity` setzt das Flag dynamisch, solange eine Verbindung aufgebaut wird oder steht (USB oder BLE) oder ein Einlesen bzw. BLE-Scan läuft (`ScreenWakeRules.keepScreenOn`).
  - Das Flag wird per `DisposableEffect` sofort wieder freigegeben (`clearFlags`), sobald nichts mehr läuft.
  - Grund: Sperrt sich das Smartphone mitten in einer BLE-Sitzung, kappt Android die GATT-Verbindung, und auf dem nRF52-Node bleibt oft ein belegter BLE-Slot hängen.
  - Seit v1.26 zusätzlich während eines Firmware-Updates (DFU-Scan, Bootloader-Suche, Upload), unabhängig vom Verbindungsstatus (§2.16).
- **GATT freigeben (`BleConnectionManager.releaseGatt`):**
  - Immer `gatt.disconnect()` und danach **immer** `gatt.close()`, jeweils einzeln abgesichert, damit eine Exception in `disconnect()` das `close()` nicht überspringt.
  - Danach `activeGatt = null`, und die Instanz wird als `closedGatt` gemerkt. Späte Callbacks dieser Instanz werden ignoriert.
- **Gilt für alle Wege:**
  - **Trennen** (`disconnect()`): genau ein „Getrennt“-Callback, kein zweiter, wenn nichts mehr offen war.
  - **Abbruch** (`STATE_DISCONNECTED`): freigegeben wird genau die Instanz des Callbacks, auch wenn `activeGatt` schon `null` ist, und ggf. eine abweichende aktive Instanz.
  - **Fehler bei der Einrichtung** (`failSetup`): Bleibt der Trenn-Callback 2 s aus, gibt die App die Instanz selbst frei.
  - **App schließen** (`destroy()` aus `ViewModel.onCleared`): Scan stoppen, laufenden Verbindungsaufbau abbrechen, GATT freigeben.
  - **Neuverbinden:** Eine alte Instanz wird vorher still freigegeben (`closeGattQuietly`), und die App wartet 400–600 ms, bevor sie neu verbindet.

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
