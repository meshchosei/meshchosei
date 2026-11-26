package com.meshcore.repeater.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v1.21: Jedes Schließen-✕ (Icons.*.Close) nutzt dieselbe Icon-Größe CloseIconSize (16 dp). */
class CloseIconSizeTest {
    private val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }

    @Test fun everyCloseIconUsesTheSharedSize() {
        val lines = root.walkTopDown().filter { it.extension == "kt" }
            .flatMap { f -> f.readLines().filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") }.filter { Regex("""Icons\.(Filled|Default)\.Close\b""").containsMatchIn(it) }.map { "${f.name}: ${it.trim()}" } }.toList()
        assertTrue("keine ✕ gefunden", lines.size >= 4)
        val wrong = lines.filterNot { it.contains("Modifier.size(CloseIconSize)") }
        assertTrue("✕ ohne CloseIconSize:\n" + wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test fun closeIconSizeIs16dp() {
        val src = File(root, "com/meshcore/repeater/ui/components/BauhausControls.kt").readText()
        assertEquals("16", Regex("""val CloseIconSize = (\d+)\.dp""").find(src)?.groupValues?.get(1))
    }
}
