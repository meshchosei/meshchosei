package com.meshcore.repeater.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.material3.Text
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Bauhaus-Farben des Emblems (identisch zu res/drawable/ic_launcher_foreground.xml). */
object MeshchoseiColors {
    val Carbon = Color(0xFF18181B)
    val Paper = Color(0xFFF4F1EA)
    val Cobalt = Color(0xFF1E88E5)
    val SignalRed = Color(0xFFE53935)
    val Ochre = Color(0xFFFFB300)
}

/**
 * Zeichnet das "ō"-Emblem (Ring + Makron-Balken + 3 Funk-Blitze) in den 108er-Koordinaten des
 * Launcher-Icons; [DrawScope] wird so skaliert, dass der Ausschnitt 24..84 × 20.75..80.75 die Fläche füllt.
 */
private fun DrawScope.drawEmblem() {
    val view = 60f
    val s = minOf(size.width, size.height) / view
    translate((size.width - view * s) / 2f, (size.height - view * s) / 2f) {
        scale(s, s, pivot = Offset.Zero) {
            translate(-24f, -20.75f) {
                // 3 Funk-Blitze, die aus dem Makron-Balken nach oben zucken (links Kobalt, Mitte Signalrot, rechts Ocker)
                drawPath(Path().apply { moveTo(34.12f, 23.33f); lineTo(43.35f, 31.14f); lineTo(40.16f, 32.31f); lineTo(44.38f, 39.82f); lineTo(34.98f, 31.53f); lineTo(38.17f, 30.37f); close() }, MeshchoseiColors.Cobalt)
                drawPath(Path().apply { moveTo(56.0f, 21.5f); lineTo(50.0f, 32.0f); lineTo(53.4f, 32.0f); lineTo(52.0f, 40.5f); lineTo(58.0f, 29.5f); lineTo(54.6f, 29.5f); close() }, MeshchoseiColors.SignalRed)
                drawPath(Path().apply { moveTo(73.88f, 23.33f); lineTo(64.65f, 31.14f); lineTo(67.84f, 32.31f); lineTo(63.62f, 39.82f); lineTo(73.02f, 31.53f); lineTo(69.83f, 30.37f); close() }, MeshchoseiColors.Ochre)
                // Makron-Balken "¯"
                drawRect(MeshchoseiColors.Paper, topLeft = Offset(39f, 40.5f), size = Size(30f, 5f))
                // Ring "o"
                val ring = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addOval(Rect(Offset(54f, 64f), 16f))
                    addOval(Rect(Offset(54f, 64f), 10f))
                }
                drawPath(ring, MeshchoseiColors.Paper)
            }
        }
    }
}

/** Nur das Emblem (ohne Hintergrund), z. B. für kleine Kopfzeilen. */
@Composable
fun MeshchoseiEmblem(modifier: Modifier = Modifier) {
    Canvas(modifier) { drawEmblem() }
}

/**
 * Bauhaus-Logo: Emblem auf Carbon-Grund, darunter der weit gesperrte Schriftzug "meshchōsei"
 * (hervorgehobenes "ō") und optional ein Bauhaus-Farbbalken.
 */
@Composable
fun MeshchoseiLogo(
    modifier: Modifier = Modifier,
    emblemSize: Dp = 120.dp,
    fontSize: TextUnit = 22.sp,
    showWordmark: Boolean = true,
    showColorBar: Boolean = true,
    withBackground: Boolean = true
) {
    val word = remember {
        buildAnnotatedString {
            append("meshch")
            withStyle(SpanStyle(color = MeshchoseiColors.SignalRed, fontWeight = FontWeight.Black)) { append("ō") }
            append("sei")
        }
    }
    Column(
        modifier.then(if (withBackground) Modifier.background(MeshchoseiColors.Carbon) else Modifier).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MeshchoseiEmblem(Modifier.size(emblemSize))
        if (showWordmark) {
            Spacer(Modifier.height(emblemSize * 0.14f))
            Text(word, color = MeshchoseiColors.Paper, fontSize = fontSize, fontWeight = FontWeight.Light, letterSpacing = 0.32.em)
        }
        if (showColorBar) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.height(4.dp)) {
                Box(Modifier.width(emblemSize * 0.30f).fillMaxHeight().background(MeshchoseiColors.Cobalt))
                Box(Modifier.width(emblemSize * 0.15f).fillMaxHeight().background(MeshchoseiColors.SignalRed))
                Box(Modifier.width(emblemSize * 0.30f).fillMaxHeight().background(MeshchoseiColors.Ochre))
            }
        }
    }
}

/** Kompakte Icon-Variante (z. B. Seitenmenü-Kopf): Emblem auf Carbon-Quadrat, ohne Schriftzug. */
@Composable
fun MeshchoseiLogoIcon(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(modifier.size(size).background(MeshchoseiColors.Carbon, androidx.compose.foundation.shape.RoundedCornerShape(size * 0.12f)), contentAlignment = Alignment.Center) {
        MeshchoseiEmblem(Modifier.size(size * 0.82f))
    }
}

/** Ruhiger Vollbild-Splashscreen (Carbon-Grund, großes Logo, Signatur unten). */
@Composable
fun MeshchoseiSplash() {
    Box(Modifier.fillMaxSize().background(MeshchoseiColors.Carbon)) {
        MeshchoseiLogo(Modifier.align(Alignment.Center), emblemSize = 168.dp, fontSize = 28.sp)
        Text(
            MESHCHOSEI_SIGNATURE, color = MeshchoseiColors.Paper.copy(alpha = 0.45f), fontSize = 12.sp, letterSpacing = 0.2.em,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp)
        )
    }
}

const val MESHCHOSEI_SIGNATURE = "🄯2026 AHGH"
