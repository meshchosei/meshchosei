package com.meshcore.repeater.service

import com.meshcore.repeater.service.CliReplyTag.Match
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Remote-CLI-Tags ("XX|", MeshCore simple_repeater / ZephCore RepeaterMesh::handleCommand). */
class CliReplyTagTest {
    @Test fun tagsAreTwoCharsAndWrapAround() {
        assertEquals("00", CliReplyTag.tagFor(0))
        assertEquals("0z", CliReplyTag.tagFor(35))
        assertEquals("10", CliReplyTag.tagFor(36))
        assertEquals("zz", CliReplyTag.tagFor(1295))
        assertEquals("00", CliReplyTag.tagFor(1296))
        assertEquals(1296, (0 until 1296).map(CliReplyTag::tagFor).toSet().size)
    }

    /** Firmware-Bedingung: command[2] == '|' und strlen(command) > 4 – auch für den kürzesten Befehl "ver". */
    @Test fun wrappedCommandMatchesFirmwarePrefixRule() {
        val w = CliReplyTag.wrap("a7", "ver")!!
        assertEquals("a7|ver", w)
        assertEquals('|', w[2]); assertTrue(w.length > 4)
        assertNull("zu lang für den 160-Byte-Textframe", CliReplyTag.wrap("a7", "set owner.info " + "x".repeat(150)))
    }

    @Test fun splitStripsOnlyALeadingTag() {
        assertEquals("a7" to "> MeinRepeater", CliReplyTag.split("a7|> MeinRepeater"))
        assertEquals(null to "> Kontakt|Zeile2", CliReplyTag.split("> Kontakt|Zeile2"))
        assertEquals("0b" to "* F\n Berlin F", CliReplyTag.split("0b|* F\n Berlin F"))
    }

    /** Kernfall: verspätete "get name"-Antwort darf nie dem laufenden "get owner.info" zugeordnet werden. */
    @Test fun lateNameReplyIsNotTakenAsOwnerInfo() {
        val nameTag = CliReplyTag.tagFor(4)        // "get name" – Timeout
        val ownerTag = CliReplyTag.tagFor(5)       // "get owner.info" läuft
        assertEquals(Match.STALE, CliReplyTag.classify("$nameTag|> MeinRepeater", ownerTag))
        assertEquals(Match.CURRENT, CliReplyTag.classify("$ownerTag|> Max Muster", ownerTag))
        // nach dem Timeout ist kein Befehl mehr aktiv: auch dann ist die Antwort veraltet
        assertEquals(Match.STALE, CliReplyTag.classify("$nameTag|> MeinRepeater", null))
        assertEquals(Match.UNTAGGED, CliReplyTag.classify("> MeinRepeater", ownerTag))
    }
}
