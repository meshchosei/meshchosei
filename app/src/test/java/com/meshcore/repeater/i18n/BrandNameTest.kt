package com.meshcore.repeater.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Der Markenname "meshchōsei" wird in keiner Sprache übersetzt; der alte Arbeitstitel
 * "Repeater Configurator" darf in keinem sichtbaren UI-Text mehr auftauchen.
 */
class BrandNameTest {
    private val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
    private val assets = listOf(File("src/main/assets"), File("app/src/main/assets")).first { it.isDirectory }
    private val brandKeys = listOf("app_name", "app_title_repeater", "about_title")

    private fun stringsFiles() = res.listFiles { f -> f.isDirectory && f.name.startsWith("values") }!!
        .map { File(it, "strings.xml") }.filter { it.isFile }

    @Test
    fun brandStringsAreUntranslatableAndCorrect() {
        val default = File(res, "values/strings.xml").readText()
        for (key in brandKeys) {
            val m = Regex("""<string name="$key"([^>]*)>([^<]*)</string>""").find(default)
            assertTrue("$key fehlt", m != null)
            assertEquals("meshchōsei", m!!.groupValues[2])
            assertTrue("$key muss translatable=\"false\" sein", m.groupValues[1].contains("translatable=\"false\""))
        }
        for (f in stringsFiles().filter { it.parentFile.name != "values" }) {
            val text = f.readText()
            for (key in brandKeys) assertFalse("$key in ${f.parentFile.name} übersetzt", text.contains("name=\"$key\""))
        }
    }

    @Test
    fun oldWorkingTitleNotInUiTexts() {
        val old = Regex("""Repeater\s*Configurator|>\s*REPEATER\s*<|>\s*CONFIGURATOR\s*<""", RegexOption.IGNORE_CASE)
        for (f in stringsFiles() + File(assets, "i18n.json")) {
            assertFalse("Alter Arbeitstitel in ${f.path}", old.containsMatchIn(f.readText()))
        }
    }

    @Test
    fun noPersonalBuildSuffixInUiTexts() {
        for (f in stringsFiles() + File(assets, "i18n.json")) {
            assertFalse("Build-Zusatz BW2 in ${f.path}", Regex("""\bBW2\b""", RegexOption.IGNORE_CASE).containsMatchIn(f.readText()))
        }
    }
}
