package com.meshcore.repeater.model

/** Seite des Türkanten-Lichtscheins: rechts = Node verbunden, links = Repeater-Login erfolgreich. */
enum class EdgeSide { LEFT, RIGHT }

/**
 * Einmal-Ereignis für den Lichtschein (v1.24). Der ViewModel setzt es direkt an der Erfolgsstelle mit
 * `id = System.nanoTime()` (monoton seit Gerätestart); die UI spielt jede id genau einmal ab (`id > lastPlayedId`) –
 * unabhängig von Recomposition oder davon, ob der übrige UI-State gleich geblieben ist.
 */
/**
 * Beobachter der Geräterolle (v1.25): meldet je Verbindung **genau einmal** die Seite, sobald die Verbindung steht und
 * die Rolle feststeht. Greift nicht in den Verbindungsablauf ein, sondern wertet nur Zustände aus
 * (`connectionState` + `deviceCapabilities`). Trennen setzt zurück, die nächste Verbindung meldet wieder.
 */
class EdgeRoleWatch {
    private var firedThisConnection = false
    fun onState(connected: Boolean, isDirectRepeater: Boolean, isCompanion: Boolean): EdgeSide? {
        if (!connected) { firedThisConnection = false; return null }
        if (firedThisConnection) return null
        val side = EdgeLightTrigger.sideForConnectedDevice(isDirectRepeater, isCompanion) ?: return null
        firedThisConnection = true
        return side
    }
}

data class EdgeLightTrigger(val id: Long, val side: EdgeSide) {
    companion object {
        /**
         * Seite nach erkanntem Gerät (v1.25): Maßgeblich ist, **was** verbunden ist, nicht der Transport (USB/BLE).
         * Direkter Repeater (USB-CLI) -> links (Signalgrün, Ziel ist ein Repeater); Companion-Node -> rechts (Kobaltblau).
         * Noch nicht erkannt -> null (kein Schein).
         */
        fun sideForConnectedDevice(isDirectRepeater: Boolean, isCompanion: Boolean): EdgeSide? = when {
            isDirectRepeater -> EdgeSide.LEFT
            isCompanion -> EdgeSide.RIGHT
            else -> null
        }

        /** Remote-Admin-Login auf einen Repeater: Ziel ist ein Repeater -> links. */
        val SIDE_FOR_REPEATER_LOGIN = EdgeSide.LEFT

        /** Abspielen, wenn das Ereignis neuer ist als das zuletzt gezeigte. */
        fun shouldPlay(trigger: EdgeLightTrigger?, lastPlayedId: Long): Boolean = trigger != null && trigger.id > lastPlayedId
    }
}
