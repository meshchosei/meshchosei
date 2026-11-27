package com.meshcore.repeater.model

/**
 * Reihenfolge des Companion-Handshakes (v1.25), rein und unit-getestet.
 *
 * Firmware-Befund (MeshCore `companion_radio/MyMesh.cpp`, ZephCore `app/CompanionMesh.cpp`): Von den Befehlen dieser App
 * liefert **nur `CMD_GET_CONTACTS`** `ERR_CODE_BAD_STATE` (4) – wenn der Kontakt-Iterator noch läuft. `CMD_APP_START`
 * setzt den Iterator zurück. `CMD_DEVICE_QUERY`, `CMD_ADD_UPDATE_CONTACT` und `CMD_SEND_LOGIN` erzeugen nie Code 4.
 *
 * Regeln:
 *  - Höchstens **ein** `CMD_GET_CONTACTS` gleichzeitig: weitere erst nach `END_OF_CONTACTS`, nach `APP_START` oder wenn
 *    die laufende Synchronisation [syncTimeoutMs] lang nichts mehr geliefert hat.
 *  - `CMD_DEVICE_QUERY` einmal pro Verbindung, erst wenn `SELF_INFO` **und** `END_OF_CONTACTS` empfangen wurden.
 */
class CompanionHandshake(private val syncTimeoutMs: Long = SYNC_TIMEOUT_MS) {
    var selfInfoReceived = false; private set
    var contactsSynced = false; private set
    var syncInFlight = false; private set
    private var lastSyncActivityMs = 0L
    private var deviceQuerySent = false

    /** Neue Verbindung bzw. getrennt: alles zurück. */
    fun reset() { selfInfoReceived = false; contactsSynced = false; syncInFlight = false; lastSyncActivityMs = 0L; deviceQuerySent = false }

    /** `CMD_APP_START` gesendet: Die Firmware beendet einen laufenden Kontakt-Iterator. */
    fun onAppStartSent() { syncInFlight = false }

    /** Darf jetzt `CMD_GET_CONTACTS` gesendet werden? Bei true gilt die Synchronisation ab sofort als laufend. */
    fun tryStartContactSync(nowMs: Long): Boolean {
        if (syncInFlight && nowMs - lastSyncActivityMs < syncTimeoutMs) return false
        syncInFlight = true; lastSyncActivityMs = nowMs
        return true
    }

    /** `CONTACT_START` / `CONTACT` empfangen: Synchronisation lebt noch. */
    fun onContactFrame(nowMs: Long) { if (syncInFlight) lastSyncActivityMs = nowMs }

    /** `END_OF_CONTACTS` empfangen. */
    fun onContactsEnd() { syncInFlight = false; contactsSynced = true }

    fun onSelfInfo() { selfInfoReceived = true }

    /** true genau einmal, sobald SELF_INFO und END_OF_CONTACTS da sind -> jetzt `CMD_DEVICE_QUERY` senden. */
    fun takeDeviceQuery(): Boolean {
        if (deviceQuerySent || !selfInfoReceived || !contactsSynced) return false
        deviceQuerySent = true
        return true
    }

    companion object {
        /** Ohne Lebenszeichen der Synchronisation darf nach dieser Zeit erneut angefordert werden. */
        const val SYNC_TIMEOUT_MS = 15_000L
    }
}
