package com.meshcore.repeater.ui

import com.meshcore.repeater.ui.components.ScrollHintRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ScrollHintRuleTest {
    @Test fun visibleOnlyWhileMoreContentBelow() {
        assertTrue(ScrollHintRule.visible(canScrollForward = true, isLandscape = true, always = false))
        assertFalse("ganz unten angekommen", ScrollHintRule.visible(canScrollForward = false, isLandscape = true, always = false))
        assertFalse("Screens im Hochformat ohne Hinweis", ScrollHintRule.visible(canScrollForward = true, isLandscape = false, always = false))
        assertTrue("Dialoge immer bei Überlauf", ScrollHintRule.visible(canScrollForward = true, isLandscape = false, always = true))
        assertFalse(ScrollHintRule.visible(canScrollForward = false, isLandscape = false, always = true))
    }

    /** STYLE_GUIDE §2.9: kein nacktes verticalScroll mehr – jeder Scroll-Container trägt den Hinweis. */
    @Test fun everyScrollContainerUsesHint() {
        val src = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        val bare = src.walkTopDown().filter { it.extension == "kt" && it.name != "ScrollHint.kt" }
            .flatMap { f -> f.readLines().filter { "verticalScroll(" in it && "hintedVerticalScroll(" !in it && !it.trimStart().startsWith("import") }.map { "${f.name}: ${it.trim()}" } }
            .toList()
        assertTrue("verticalScroll ohne Scroll-Hinweis:\n" + bare.joinToString("\n"), bare.isEmpty())
    }
}
