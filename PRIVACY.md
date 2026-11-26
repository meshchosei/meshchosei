# Datenschutz – meshchōsei

Stand: 2026-11-26 (Version 1.20)

meshchōsei betreibt **keine eigenen Server**, enthält **kein Tracking**, keine Werbung und keine Analyse-Bibliotheken. Die App erhebt keine Nutzungsdaten.

## 1. Lokale Datenhaltung

- Alle Einstellungen bleiben ausschließlich auf deinem Gerät, im privaten App-Speicher (`SharedPreferences`): Sprache, Farbschema, Disclaimer-Zustimmung und die je Repeater gespeicherten **Remote-Admin-Passwörter**.
- Android-Cloud-Backups sind deaktiviert (`allowBackup = false`). Die Daten verlassen das Gerät also auch nicht über ein System-Backup.
- **JSON-Backups** der Repeater-Konfiguration entstehen nur, wenn du sie ausdrücklich erstellst, und werden dort gespeichert oder geteilt, wo du es auswählst. Ein Backup kann den privaten Schlüssel des Repeaters enthalten; behandle es entsprechend vertraulich.
- Konfigurationsbefehle gehen ausschließlich an deinen Repeater, per USB oder über deinen Companion-Node und das LoRa-Mesh.

## 2. Bluetooth- und Standort-Berechtigung

- **Bluetooth** (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`) wird nur benötigt, um einen Companion-Node zu finden und zu verbinden. Ab Android 12 ist der Scan als `neverForLocation` deklariert.
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

## Kontakt & Quellcode

meshchōsei ist freie Software unter der GNU GPLv3. Der Quellcode kann jederzeit eingesehen und geprüft werden.

🄯2026 AHGH
