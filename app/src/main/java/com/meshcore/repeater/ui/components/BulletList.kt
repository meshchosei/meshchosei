package com.meshcore.repeater.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Breite der Bullet-Spalte (STYLE_GUIDE §2.6.1). */
val BulletColumnWidth = 14.dp

/** Zerlegt einen Ressourcentext „• a\n• b“ in Einträge ohne führendes `•` (leere Zeilen entfallen). */
object BulletListRules {
    fun items(text: String): List<String> =
        text.split('\n').map { it.trim().removePrefix("•").trim() }.filter { it.isNotEmpty() }
}

/**
 * Aufzählung mit hängendem Einzug (v1.26): je Eintrag eine `Row` mit fester Bullet-Spalte ([BulletColumnWidth]) und
 * rechts `Text(Modifier.weight(1f))` – Folgezeilen stehen bündig unter dem Textanfang, nicht unter dem Punkt.
 */
@Composable
fun BulletList(items: List<String>, color: Color, fontSize: TextUnit = 12.sp, fontFamily: FontFamily? = null, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEach { item ->
            Row(Modifier.fillMaxWidth()) {
                Text("•", fontSize = fontSize, fontFamily = fontFamily, color = color, modifier = Modifier.width(BulletColumnWidth))
                Text(item, fontSize = fontSize, fontFamily = fontFamily, color = color, modifier = Modifier.weight(1f))
            }
        }
    }
}
