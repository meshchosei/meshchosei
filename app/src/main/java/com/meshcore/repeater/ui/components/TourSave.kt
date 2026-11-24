package com.meshcore.repeater.ui.components

import androidx.compose.runtime.*

/**
 * Setup-Tour: Screens mit eigenem "Speichern" melden ihre Speicheraktion hier an.
 * Im Tour-Modus blenden sie ihren roten Speichern-Button aus; die Tour-Leiste bietet
 * stattdessen "Speichern & Weiter →" an (kein doppelter roter Button mehr).
 */
class TourSaveController {
    var action by mutableStateOf<(() -> Unit)?>(null)
}

val LocalTourMode = compositionLocalOf { false }
val LocalTourSave = staticCompositionLocalOf<TourSaveController?> { null }

/** Registriert [onSave] als Speicheraktion des aktuell sichtbaren Screens (nur im Tour-Modus relevant). */
@Composable
fun RegisterTourSave(onSave: () -> Unit) {
    val controller = LocalTourSave.current ?: return
    val latest by rememberUpdatedState(onSave)
    DisposableEffect(controller) {
        val a: () -> Unit = { latest() }
        controller.action = a
        onDispose { if (controller.action === a) controller.action = null }
    }
}
