package com.meshcore.repeater.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * STYLE_GUIDE.md §1.2 (seit v1.09): alle Kästchen, Dialoge und Badges nutzen BauhausCornerShape (2 dp).
 * Einzige runde Ausnahme ist das USB-/BT-Verbindungs-Badge neben dem Gerätenamen (ConnectionScreen);
 * die Definition selbst (BauhausTheme) und das Logo-Emblem sind keine UI-Kästchen.
 */
class ShapeConsistencyTest {
    private val sources: List<File> =
        listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
            .walkTopDown().filter { it.extension == "kt" }.toList()

    private fun codeLines(f: File) = f.readLines().filterNot { it.trimStart().startsWith("import ") || it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }

    @Test fun noRoundedCornerShapeOutsideTheme() {
        val allowed = setOf("BauhausTheme.kt", "MeshchoseiLogo.kt")
        val hits = sources.filter { it.name !in allowed }
            .flatMap { f -> codeLines(f).filter { "RoundedCornerShape(" in it }.map { "${f.name}: ${it.trim()}" } }
        assertTrue("Runde Formen statt BauhausCornerShape:\n" + hits.joinToString("\n"), hits.isEmpty())
    }

    @Test fun circleShapeOnlyForConnectionBadge() {
        val hits = sources.flatMap { f -> codeLines(f).filter { Regex("""\bCircleShape\b""").containsMatchIn(it) }.map { f.name } }
        assertEquals(listOf("ConnectionScreen.kt"), hits)
    }

    @Test fun noPillShapedMaterialBadge() {
        val hits = sources.flatMap { f -> codeLines(f).filter { Regex("""(?<![A-Za-z])Badge\(""").containsMatchIn(it) }.map { "${f.name}: ${it.trim()}" } }
        assertTrue("Material-Badge ist pillenförmig:\n" + hits.joinToString("\n"), hits.isEmpty())
    }
}
