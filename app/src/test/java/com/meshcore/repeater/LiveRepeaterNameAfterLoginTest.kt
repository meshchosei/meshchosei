package com.meshcore.repeater

import com.meshcore.repeater.model.LiveRepeater
import com.meshcore.repeater.model.LiveScanRules
import com.meshcore.repeater.model.LiveScanState
import com.meshcore.repeater.model.MeshContact
import com.meshcore.repeater.model.RemoteAdminSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.24, Praxisfehler: Live-Repeater ohne Namen -> Login -> `get name` = "Le Chemin de Tennis". Der Identitäts-Screen
 * zeigte den Namen, der Verbindungs-Screen weiter das Hex-Präfix. Ursache: `MeshContact.equals` verglich nur den Key,
 * daher galt der umbenannte Kontakt als gleich und `MutableStateFlow` verwarf das Update.
 */
class LiveRepeaterNameAfterLoginTest {
    private val hex = "cf3a8206aa55bb66cc77dd88ee99ff0011223344556677889900aabbccddeeff"
    private val name = "Le Chemin de Tennis"

    @Test fun renamedContactIsNotEqualSoStateFlowEmits() {
        val placeholder = LiveScanRules.toContact(LiveRepeater(ByteArray(32), hex, 7f, -88, 3f), null)
        val renamed = placeholder.copy(name = name)
        assertNotEquals("umbenannt != alt (sonst verwirft StateFlow das Update)", placeholder, renamed)
        assertEquals("gleiche Werte bleiben gleich", placeholder, placeholder.copy())
        assertEquals("hashCode bleibt am Key", placeholder.hashCode(), renamed.hashCode())

        val session = MutableStateFlow(RemoteAdminSession(targetRepeater = placeholder, isLoggedIn = true))
        session.update { it.copy(targetRepeater = it.targetRepeater!!.copy(name = name)) }
        assertEquals(name, session.value.targetRepeater!!.name)
        val contacts = MutableStateFlow(listOf(placeholder))
        contacts.value = listOf(renamed)
        assertEquals(name, contacts.value.single().name)
    }

    /** Ganzer Ablauf: Live-Scan -> Auswahl -> Login -> get name -> Login-Bereich, Statuskarte und Live-Liste. */
    @Test fun liveRepeaterWithoutNameShowsRealNameAfterGetName() {
        val found = LiveRepeater(ByteArray(32), hex, 7f, -88, 3f)                      // Discover-Antwort: kein Name
        val live = MutableStateFlow(LiveScanState(tag = 1L, results = listOf(found)))
        val selected = LiveScanRules.toContact(found, null)
        assertEquals("cf3a8206…", selected.name)
        val session = MutableStateFlow(RemoteAdminSession(targetRepeater = selected, isLoggedIn = true))
        val contacts = MutableStateFlow(listOf(selected))

        // Start-Scan nach dem Login liest "get name" (so macht es RepeaterViewModel.onRepeaterNameRead)
        val t = session.value.targetRepeater!!
        val u = LiveScanRules.applyFetchedName(t, live.value.results, contacts.value, name)!!
        session.update { it.copy(targetRepeater = it.targetRepeater!!.copy(name = u.target.name)) }
        live.update { it.copy(results = u.live) }
        contacts.value = u.contacts

        // Verbindungs-Screen: Login-Bereich / Statuskarte
        assertEquals(name, LiveScanRules.shownName(session.value.targetRepeater!!, null, null))
        assertEquals(name, LiveScanRules.shownName(session.value.targetRepeater!!, name, hex))
        // Live-Liste
        val row = live.value.results.single()
        assertEquals(name, LiveScanRules.displayName(row, LiveScanRules.findKnown(row, contacts.value)))
        assertEquals(name, contacts.value.single().name)
    }

    /** Vorrang des live gelesenen Namens – auch wenn eine Liste nur das Key-Präfix hält oder Großbuchstaben nutzt. */
    @Test fun readNameHasPriorityAndKeysMatchByPrefix() {
        val target = MeshContact(pubKey = ByteArray(32), pubKeyHex = hex, keyPrefix = hex.take(12), type = 2, name = "cf3a8206…")
        assertEquals(name, LiveScanRules.shownName(target, name, hex.uppercase()))
        assertEquals("nur 12-Hex-Präfix bekannt", name, LiveScanRules.shownName(target, name, hex.take(12)))
        assertEquals("Name eines anderen Repeaters zählt nicht", "cf3a8206…", LiveScanRules.shownName(target, name, "00112233" + hex.drop(8)))
        assertEquals("leer -> gespeicherter Name", "cf3a8206…", LiveScanRules.shownName(target, "  ", hex))
        assertTrue(LiveScanRules.sameKey(hex, hex.take(8).uppercase()))
        assertFalse("zu kurz für einen sicheren Vergleich", LiveScanRules.sameKey(hex, hex.take(6)))
        // Live-Eintrag hält nur ein 16-Hex-Präfix -> wird trotzdem umbenannt
        val shortLive = LiveRepeater(ByteArray(32), hex.take(16).uppercase(), 5f, -90, 1f)
        val u = LiveScanRules.applyFetchedName(target, listOf(shortLive), emptyList(), name)!!
        assertEquals(name, u.live.single().name)
    }
}
