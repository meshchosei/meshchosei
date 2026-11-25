package com.meshcore.repeater.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Haftungsausschluss (v1.10): jeder tr()-Text des Dialogs steht in assets/i18n.json mit EN/NL/FR,
 * und keine Sprache nennt mehr Deutschland/BNetzA (nur noch EU).
 */
class DisclaimerTranslationTest {
    private fun file(rel: String) = listOf(File(rel), File("app/$rel")).first { it.exists() }
    private val json = file("src/main/assets/i18n.json").readText()
    private val dialog = file("src/main/java/com/meshcore/repeater/ui/components/DisclaimerDialog.kt").readText()

    private fun unescape(s: String) = s.replace("\\\"", "\"").replace("\\\\", "\\")
    private val keys = Regex("""tr\("((?:[^"\\]|\\.)*)"""").findAll(dialog).map { unescape(it.groupValues[1]) }.toList()

    /** Liefert die drei Übersetzungen eines Schlüssels aus der JSON-Datei (einfacher Parser, keine Android-Abhängigkeit). */
    private fun translations(key: String): List<String>? {
        val k = "\"" + key.replace("\\", "\\\\").replace("\"", "\\\"") + "\": ["
        val i = json.indexOf(k).takeIf { it >= 0 } ?: return null
        val block = json.substring(i + k.length, json.indexOf(']', i + k.length))
        return Regex(""""((?:[^"\\]|\\.)*)"""").findAll(block).map { unescape(it.groupValues[1]) }.toList()
    }

    @Test fun everyDisclaimerTextIsTranslated() {
        assertTrue("Dialog hat zu wenige tr()-Texte: $keys", keys.size >= 10)
        for (k in keys) {
            val t = translations(k)
            assertTrue("Fehlt in i18n.json: $k", t != null)
            assertEquals("EN/NL/FR für: $k", 3, t!!.count { it.isNotBlank() })
        }
    }

    @Test fun onlyEuIsNamed() {
        for (k in keys) for (text in listOf(k) + translations(k).orEmpty()) {
            assertFalse(text, Regex("Deutschland|Germany|Duitsland|Allemagne|BNetzA").containsMatchIn(text))
        }
        assertTrue(keys.any { "In der EU" in it })
    }

    @Test fun noResourceStringsLeftInDialog() {
        assertFalse(dialog.contains("stringResource("))
    }

    /** STYLE_GUIDE §4.3: Button-Beschriftungen höchstens 1–2 Wörter, in allen Sprachen. */
    @Test fun dialogButtonLabelsAreShort() {
        for (k in listOf("Abbrechen", "Akzeptieren", "Schließen")) {
            assertTrue("Schlüssel fehlt im Dialog: $k", k in keys)
            for (text in listOf(k) + translations(k).orEmpty()) assertTrue("Zu lang: $text", text.trim().split(Regex("\\s+")).size <= 2)
        }
        assertEquals(listOf("Accept", "Accepteren", "Accepter"), translations("Akzeptieren"))
        assertFalse(json.contains("Verstanden & Akzeptieren"))
    }

    /** v1.14: das Ocker-Warnsymbol steht schon vor dem Titel – kein zusätzliches ⚠/! im Titeltext. */
    @Test fun titleHasNoWarningGlyph() {
        assertTrue(keys.contains("Wichtiger Hinweis"))
        for (text in listOf("Wichtiger Hinweis") + translations("Wichtiger Hinweis").orEmpty())
            assertFalse(text, text.startsWith("⚠") || text.startsWith("!"))
    }
}
