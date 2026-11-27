package com.meshcore.repeater

import com.meshcore.repeater.model.LiveRepeater
import com.meshcore.repeater.model.LiveScanRules
import com.meshcore.repeater.model.MeshContact
import com.meshcore.repeater.service.CompanionProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.24: Live-Umgebungssuche. Frames wörtlich nach MeshCore companion_radio/MyMesh.cpp (onControlDataRecv,
 * CMD_SEND_CONTROL_DATA) und simple_repeater/MyMesh.cpp (NODE_DISCOVER_REQ/RESP); ZephCore identisch.
 */
class LiveRepeaterScanTest {
    private val tag = 0xA1B2C3D4L

    private fun key(b: Int) = ByteArray(32) { (b + it).toByte() }

    /** PUSH_CODE_CONTROL_DATA mit Discover-Antwort: [0x8E][snr*4][rssi][path_len][0x90|type][remoteSnr*4][tag LE][pubkey 32]. */
    private fun push(snrX4: Int, rssi: Int, nodeType: Int = 2, t: Long = tag, k: ByteArray = key(1), remoteX4: Int = 20, pubLen: Int = 32): ByteArray {
        val f = ByteArray(4 + 6 + pubLen)
        f[0] = 0x8E.toByte(); f[1] = snrX4.toByte(); f[2] = rssi.toByte(); f[3] = 0
        f[4] = (0x90 or nodeType).toByte(); f[5] = remoteX4.toByte()
        f[6] = (t and 0xFF).toByte(); f[7] = ((t shr 8) and 0xFF).toByte(); f[8] = ((t shr 16) and 0xFF).toByte(); f[9] = ((t shr 24) and 0xFF).toByte()
        System.arraycopy(k, 0, f, 10, pubLen)
        return f
    }

    private fun live(hex: String, snr: Float, rssi: Int) = LiveRepeater(ByteArray(32), hex, snr, rssi, 0f)

    @Test fun discoverRequestMatchesFirmwareLayout() {
        val f = CompanionProtocol.buildDiscoverRepeatersFrame(tag)
        assertArrayEquals(
            byteArrayOf(0x37, 0x80.toByte(), 0x04, 0xD4.toByte(), 0xC3.toByte(), 0xB2.toByte(), 0xA1.toByte(), 0, 0, 0, 0), f
        )
        assertTrue("Firmware verlangt Bit 0x80 im ersten Datenbyte", (f[1].toInt() and 0x80) != 0)
    }

    @Test fun parsesRepeaterAnswerWithSignal() {
        val r = CompanionProtocol.parseDiscoverResponse(push(snrX4 = 46, rssi = -87, remoteX4 = -10), tag, nowMs = 42)
        assertNotNull(r)
        assertEquals(11.5f, r!!.snr, 0f)
        assertEquals(-87, r.rssi)
        assertEquals(-2.5f, r.remoteSnr, 0f)
        assertArrayEquals(key(1), r.pubKey)
        assertEquals("0102030405060708", r.pubKeyHex.take(16))
        assertEquals(42L, r.seenAtMs)
    }

    /** Nur Repeater: Antworten von Chat-, Room- oder Sensor-Knoten werden verworfen. */
    @Test fun filtersToRepeaterNodes() {
        assertNull("Chat", CompanionProtocol.parseDiscoverResponse(push(40, -80, nodeType = 1), tag))
        assertNull("Room-Server", CompanionProtocol.parseDiscoverResponse(push(40, -80, nodeType = 3), tag))
        assertNull("Sensor", CompanionProtocol.parseDiscoverResponse(push(40, -80, nodeType = 4), tag))
        assertNotNull(CompanionProtocol.parseDiscoverResponse(push(40, -80, nodeType = 2), tag))
    }

    @Test fun ignoresForeignOrBrokenFrames() {
        assertNull("fremde/alte Suche", CompanionProtocol.parseDiscoverResponse(push(40, -80, t = 0x11111111L), tag))
        assertNull("nur Key-Präfix (prefix_only)", CompanionProtocol.parseDiscoverResponse(push(40, -80, pubLen = 8), tag))
        val req = push(40, -80); req[4] = 0x80.toByte()
        assertNull("eine Suchanfrage ist keine Antwort", CompanionProtocol.parseDiscoverResponse(req, tag))
        val other = push(40, -80); other[0] = 0x85.toByte()
        assertNull("anderer Push", CompanionProtocol.parseDiscoverResponse(other, tag))
    }

    @Test fun sortsStrongestSnrFirstThenRssi() {
        val sorted = LiveScanRules.sortBySignal(listOf(
            live("aa", -3.25f, -110), live("bb", 11.5f, -95), live("cc", 6f, -70), live("dd", 11.5f, -60), live("ee", 0f, -100)
        ))
        assertEquals(listOf("dd", "bb", "cc", "ee", "aa"), sorted.map { it.pubKeyHex })
    }

    @Test fun mergeKeepsOneEntryPerRepeaterWithLatestMeasurement() {
        var l = LiveScanRules.merge(emptyList(), live("aa", 2f, -100))
        l = LiveScanRules.merge(l, live("bb", 5f, -90))
        l = LiveScanRules.merge(l, live("aa", 9f, -80))
        assertEquals(listOf("aa", "bb"), l.map { it.pubKeyHex })
        assertEquals(9f, l.first().snr, 0f)
    }

    @Test fun signalFormatting() {
        assertEquals("SNR +11,5 dB", LiveScanRules.formatSnr(11.5f))
        assertEquals("SNR +11.5 dB", LiveScanRules.formatSnr(11.5f, decimalComma = false))
        assertEquals("SNR −3,3 dB", LiveScanRules.formatSnr(-3.25f))
        assertEquals("SNR ±0,0 dB", LiveScanRules.formatSnr(0f))
        assertEquals("−87 dBm", LiveScanRules.formatRssi(-87))
    }

    @Test fun unknownRepeaterBecomesDirectContactKnownKeepsName() {
        val r = LiveRepeater(key(7), "0708090a0b0c0d0e", 4f, -90, 1f, seenAtMs = 5)
        val fresh = LiveScanRules.toContact(r, null)
        assertEquals("0708090a…", fresh.name)
        assertEquals(2, fresh.type); assertEquals(0, fresh.outPathLen); assertTrue(fresh.isRepeater)
        assertEquals(4f, fresh.snr!!, 0f)
        val known = MeshContact(pubKey = r.pubKey, pubKeyHex = r.pubKeyHex, keyPrefix = "0708090a0b0c", type = 2, outPathLen = 2, name = "Funkturm")
        val c = LiveScanRules.toContact(r, known)
        assertEquals("Funkturm", c.name); assertEquals(2, c.outPathLen); assertEquals(-90, c.rssi)
    }

    /** CMD_ADD_UPDATE_CONTACT: [0x09][key 32][type][flags][out_path_len][path 64][name 32][last_advert 4] = 136 Byte. */
    @Test fun addContactFrameMatchesFirmwareLayout() {
        val f = CompanionProtocol.buildAddRepeaterContactFrame(key(3), "03040506…")
        assertEquals(136, f.size)
        assertEquals(0x09.toByte(), f[0])
        assertArrayEquals(key(3), f.copyOfRange(1, 33))
        assertEquals(2.toByte(), f[33]); assertEquals(0.toByte(), f[34]); assertEquals("direkt", 0.toByte(), f[35])
        assertEquals("03040506…", String(f.copyOfRange(100, 132), Charsets.UTF_8).trimEnd('\u0000'))
        val long = CompanionProtocol.buildAddRepeaterContactFrame(key(3), "Ä".repeat(40))
        assertEquals("Name nullterminiert, kein halbes UTF-8-Zeichen", 0.toByte(), long[131])
        assertEquals(15, String(long.copyOfRange(100, 132), Charsets.UTF_8).trimEnd('\u0000').length)
    }

    // ---- Namen nachfüllen (Discover-Antwort hat keinen Namen) ----

    private val hex = "cf3a8206aa55bb66cc77dd88ee99ff0011223344556677889900aabbccddeeff"
    private val r = LiveRepeater(ByteArray(32), hex, 6f, -80, 2f)
    private fun contact(name: String, k: String = hex) = MeshContact(pubKey = ByteArray(32), pubKeyHex = k, keyPrefix = k.take(12), type = 2, name = name)

    @Test fun nameComesFromSavedCompanionContacts() {
        val contacts = listOf(contact("Andere", k = "00" + hex.drop(2)), contact("Funkturm Nord"))
        val known = LiveScanRules.findKnown(r, contacts)
        assertEquals("Funkturm Nord", LiveScanRules.displayName(r, known))
        assertEquals("Key-Vergleich ohne Groß-/Kleinschreibung", "Funkturm Nord",
            LiveScanRules.displayName(r, LiveScanRules.findKnown(r.copy(pubKeyHex = hex.uppercase()), contacts)))
    }

    @Test fun unknownNameShowsShortKeyPrefix() {
        assertEquals("cf3a8206…", LiveScanRules.displayName(r, null))
        assertEquals("cf3a8206…", LiveScanRules.displayName(r, LiveScanRules.findKnown(r, emptyList())))
        assertEquals("Ersatzname 'Node-…' zählt nicht als Name", "cf3a8206…", LiveScanRules.displayName(r, contact("Node-cf3a8206")))
        assertTrue(LiveScanRules.isPlaceholderName("", hex))
        assertTrue(LiveScanRules.isPlaceholderName("cf3a8206…", hex))
        assertTrue(!LiveScanRules.isPlaceholderName("Funkturm", hex))
    }

    @Test fun getNameReplacesPlaceholderInSelection() {
        val placeholder = LiveScanRules.toContact(r, null)
        assertEquals("cf3a8206…", placeholder.name)
        val renamed = LiveScanRules.nameAfterGetName(placeholder, "  Funkturm Nord ")
        assertEquals("Funkturm Nord", renamed!!.name)
        assertEquals("Key, Pfad und Signal bleiben", placeholder.copy(name = "Funkturm Nord"), renamed)
    }

    @Test fun getNameIgnoresEmptyInvalidOrUnchanged() {
        val c = contact("Funkturm Nord")
        assertNull(LiveScanRules.nameAfterGetName(c, ""))
        assertNull(LiveScanRules.nameAfterGetName(c, null))
        assertNull("unverändert", LiveScanRules.nameAfterGetName(c, "Funkturm Nord"))
        assertNull("zu lang (> 32)", LiveScanRules.nameAfterGetName(c, "x".repeat(33)))
        assertNull("Platzhalter ist kein Name", LiveScanRules.nameAfterGetName(c, "cf3a8206…"))
        assertEquals("umbenannter Repeater", "Turm Süd", LiveScanRules.nameAfterGetName(c, "Turm Süd")!!.name)
    }

    /** Live-Repeater ohne Namen: nach `get name` heißt er im Login-Bereich, in der Live-Liste und in den Kontakten richtig. */
    @Test fun getNameFillsSelectedRepeaterAndLiveList() {
        val other = LiveRepeater(ByteArray(32), "00" + hex.drop(2), 9f, -70, 1f)
        val live = listOf(other, r)
        val selected = LiveScanRules.toContact(r, null)                 // unbekannt -> Platzhalter
        val contacts = listOf(selected)                                  // so im Companion angelegt
        assertEquals("cf3a8206…", selected.name)
        assertEquals("cf3a8206…", LiveScanRules.displayName(r, LiveScanRules.findKnown(r, contacts)))

        val u = LiveScanRules.applyFetchedName(selected, live, contacts, "Funkturm Nord")!!
        assertEquals("Login-Bereich", "Funkturm Nord", u.target.name)
        assertEquals("Key bleibt", hex, u.target.pubKeyHex)
        val liveEntry = u.live.first { it.pubKeyHex == hex }
        assertEquals("Live-Liste", "Funkturm Nord", LiveScanRules.displayName(liveEntry, null))
        assertNull("anderer Live-Repeater unverändert", u.live.first { it.pubKeyHex != hex }.name)
        assertEquals("Kontaktliste", "Funkturm Nord", u.contacts.single().name)
        assertNull("zweites get name mit gleichem Namen ändert nichts", LiveScanRules.applyFetchedName(u.target, u.live, u.contacts, "Funkturm Nord"))
        assertNull("leere Antwort ändert nichts", LiveScanRules.applyFetchedName(selected, live, contacts, " "))
    }

    /** Name bleibt nach neuem Scan und nach Neu-Einlesen der Kontakte (Companion hat noch den Platzhalter). */
    @Test fun learnedNameSurvivesRescanAndContactRefresh() {
        val named = r.copy(name = "Funkturm Nord")
        val rescanned = LiveScanRules.merge(listOf(named), r.copy(snr = 3f))
        assertEquals("Funkturm Nord", rescanned.single().name)
        assertEquals(3f, rescanned.single().snr, 0f)
        val fromCompanion = contact("cf3a8206…")
        assertEquals("Funkturm Nord", LiveScanRules.keepLearnedName(fromCompanion, "Funkturm Nord").name)
        assertEquals("echter Name im Companion gewinnt", "Turm", LiveScanRules.keepLearnedName(contact("Turm"), "Funkturm Nord").name)
        assertEquals("nichts gelernt", "cf3a8206…", LiveScanRules.keepLearnedName(fromCompanion, null).name)
    }
}
