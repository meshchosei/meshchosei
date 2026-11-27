# Datenschutz – meshchōsei

Stand: 2026-11-27 (Version 1.26)

meshchōsei betreibt **keine eigenen Server**, enthält **kein Tracking**, keine Werbung und keine Analyse-Bibliotheken. Die App erhebt keine Nutzungsdaten.

## 1. Lokale Datenhaltung

- Alle Einstellungen bleiben ausschließlich auf deinem Gerät, im privaten App-Speicher (`SharedPreferences`): Sprache, Farbschema, Disclaimer-Zustimmung und die je Repeater gespeicherten **Remote-Admin-Passwörter**.
- Android-Cloud-Backups sind deaktiviert (`allowBackup = false`). Die Daten verlassen das Gerät also auch nicht über ein System-Backup.
- **JSON-Backups** der Repeater-Konfiguration entstehen nur, wenn du sie ausdrücklich erstellst, und werden dort gespeichert oder geteilt, wo du es auswählst. Ein Backup kann den privaten Schlüssel des Repeaters enthalten; behandle es entsprechend vertraulich.
- Konfigurationsbefehle gehen ausschließlich an deinen Repeater, per USB oder über deinen Companion-Node und das LoRa-Mesh.

## 2. Bluetooth- und Standort-Berechtigung

- **Bluetooth** (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`) wird nur benötigt, um einen Companion-Node zu finden und zu verbinden. Ab Android 12 ist der Scan als `neverForLocation` deklariert.
- **Firmware-Update (seit Version 1.26):** Für „Firmware / OTA“ sucht die App per Bluetooth nach Geräten im DFU-Modus und überträgt die von dir gewählte Firmware-Datei mit einem Vordergrund-Dienst (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`). Ab Android 13 fragt sie dafür optional die Benachrichtigungs-Berechtigung (`POST_NOTIFICATIONS`) an, um den Fortschritt anzuzeigen. Die Firmware-Datei wird nur lokal im App-Cache zwischengespeichert und an niemanden außer dem gewählten Gerät gesendet.
- **Standort** (`ACCESS_FINE_LOCATION`) wird nur bis Android 11 angefordert, weil Android dort BLE-Scans an die Standortberechtigung koppelt. Die App liest den Standort deines Smartphones **nicht** aus und überträgt ihn nirgendwohin.
- „GPS“ in der App bezieht sich immer auf das GPS-Modul des **Repeaters** (z. B. `gps setloc`). Die Koordinaten des Repeaters setzt du selbst; sie werden nur an deinen Repeater gesendet.

## 3. OpenStreetMap-Kartenkacheln

- Die Standortkarte lädt Kartenkacheln von `tile.openstreetmap.org` (OpenStreetMap Foundation), aber nur, wenn du die Karte öffnest.
- Dabei werden technisch bedingt deine **IP-Adresse**, ein allgemeiner App-User-Agent (ohne persönliche Angaben) und die angezeigten Kachelkoordinaten an diesen Server übertragen. Es gilt die [Datenschutzerklärung der OSMF](https://osmfoundation.org/wiki/Privacy_Policy).
- Die Kacheln werden nur im Arbeitsspeicher zwischengespeichert.

## 4. Versions-Check beim Start (seit Version 1.20)

- Beim Start fragt die App **einmal** im Hintergrund die öffentliche GitHub-API ab (`https://api.github.com/repos/meshchosei/meshchosei/releases/latest`), um zu prüfen, ob eine neuere Version veröffentlicht wurde. Ausgewertet werden nur die Versionsnummer (`tag_name`) und der Link zur Release-Seite (`html_url`).
- Der Abruf ist **anonym**: Es gibt kein Konto, keine Kennung und keine Geräte- oder Nutzungsdaten. Technisch bedingt sieht GitHub deine **IP-Adresse** und einen allgemeinen User-Agent (`meshchosei-update-check (Android)`). Es gilt die [Datenschutzerklärung von GitHub](https://docs.github.com/de/site-policy/privacy-policies/github-general-privacy-statement).
- Ohne Internet oder bei Fehlern passiert nichts: kein Hinweis, keine Meldung. Ist eine neuere Version verfügbar, zeigt der Verbindungs-Screen einen Hinweis. Die Release-Seite öffnet sich nur, wenn du ihn antippst.

Kartenkacheln und Versions-Check sind die einzigen Internetzugriffe der App (Berechtigung `INTERNET`).

## 5. Debug-Bericht für Fehlermeldungen (optional, seit Version 1.25)

- Unter **„Über & Lizenz“ → „Fehler melden“** kann ein Debug-Bericht erstellt werden. Er entsteht **nur lokal und nur, wenn du den Button „Debug-Informationen sammeln“ antippst**. Es werden **keinerlei Diagnosedaten automatisch übertragen**, auch nicht im Hintergrund.
- Inhalt: App- und Android-Version, Gerätemodell, gewählte Sprache und Palette, Verbindungsart, die Gerätedaten des **Companion-Nodes** (Name, Modell, Firmware, Build-Datum, Protokollversion, Funkparameter) und des **Ziel-Repeaters** (Board, Funkchip, Firmware, Bootloader-Version, Fähigkeiten), die letzten Status- und Fehlermeldungen sowie das CLI- und System-Protokoll der laufenden Sitzung. Die **BLE-PIN** und die **GPS-Position** des Companion-Nodes werden nie aufgenommen.
- **Passwörter und Schlüssel werden ausgeschlossen:** Privater Schlüssel (`prv.key`), Admin- und Gast-Passwörter, `wifi.pass` und `mqtt.*.password` werden vor der Anzeige durch `[REDACTED]` ersetzt. Das gilt auch für alle in der App gespeicherten Admin-Passwörter, wo immer sie im Text vorkommen.
- Der Bericht wird dir vollständig angezeigt. Du entscheidest selbst, ob du ihn **kopierst** oder **als Datei speicherst**, und du kannst vor dem Teilen (z. B. in einem GitHub-Issue) beliebige Zeilen entfernen. Er bleibt im Arbeitsspeicher und wird nicht dauerhaft abgelegt, außer du speicherst ihn selbst.

---

## Debug report (optional, since version 1.25) – English

- Under **“About & license” → “Report a bug”** you can create a debug report. It is created **locally only, and only when you tap “Collect debug information”**. **No diagnostic data is ever transmitted automatically**, not even in the background.
- Contents: app and Android version, device model, selected language and palette, connection type, the device data of the **companion node** (name, model, firmware, build date, protocol version, radio settings) and of the **target repeater** (board, radio chip, firmware, bootloader version, capabilities), the latest status and error messages, and the CLI and system log of the current session. The companion node's **BLE PIN** and **GPS position** are never included.
- **Passwords and keys are excluded:** the private key (`prv.key`), admin and guest passwords, `wifi.pass` and `mqtt.*.password` are replaced by `[REDACTED]` before the report is shown. This also applies to every admin password stored in the app, wherever it appears in the text.
- The full report is shown to you. You decide whether to **copy** it or **save it as a file**, and you can remove any line before sharing it (e.g. in a GitHub issue). It stays in memory and is not stored permanently unless you save it yourself.

## Firmware update (optional, since version 1.26) – English

- For **“Firmware / OTA”** the app scans via Bluetooth for devices in DFU mode and sends the firmware file you selected using a foreground service (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`). On Android 13+ it optionally asks for the notification permission (`POST_NOTIFICATIONS`) to show the progress. The firmware file is only cached locally in the app and is sent to nobody but the selected device.

## Kontakt & Quellcode

meshchōsei ist freie Software unter der GNU GPLv3. Der Quellcode kann jederzeit eingesehen und geprüft werden.

🄯2026 AHGH
