package com.meshcore.repeater.model

/** Seite des Türkanten-Lichtscheins: rechts = Node verbunden, links = Repeater-Login erfolgreich. */
enum class EdgeSide { LEFT, RIGHT }

/**
 * Einmal-Ereignis für den Lichtschein (v1.24). Der ViewModel setzt es direkt an der Erfolgsstelle mit
 * `id = System.nanoTime()` (monoton seit Gerätestart); die UI spielt jede id genau einmal ab (`id > lastPlayedId`) –
 * unabhängig von Recomposition oder davon, ob der übrige UI-State gleich geblieben ist.
 */
data class EdgeLightTrigger(val id: Long, val side: EdgeSide) {
    companion object {
        /** Abspielen, wenn das Ereignis neuer ist als das zuletzt gezeigte. */
        fun shouldPlay(trigger: EdgeLightTrigger?, lastPlayedId: Long): Boolean = trigger != null && trigger.id > lastPlayedId
    }
}
