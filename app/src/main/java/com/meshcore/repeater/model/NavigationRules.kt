package com.meshcore.repeater.model

/**
 * Freigabe von Seitenmenü und Navigation. Gekoppelt AUSSCHLIESSLICH an Verbindung, Geräteerkennung,
 * Remote-Login und den initialen Start-Scan – bewusst NICHT an laufende manuelle CLI-Befehle oder
 * andere Einzelabfragen (seit v1.13). Der Hamburger-Button ist immer bedienbar.
 */
object NavigationRules {
    fun navigationAllowed(
        connected: Boolean,
        detectionComplete: Boolean,
        isCompanion: Boolean,
        remoteLoggedIn: Boolean,
        initialReadDone: Boolean,
        startScanReading: Boolean
    ): Boolean = connected && detectionComplete && (!isCompanion || remoteLoggedIn) && initialReadDone && !startScanReading
}
