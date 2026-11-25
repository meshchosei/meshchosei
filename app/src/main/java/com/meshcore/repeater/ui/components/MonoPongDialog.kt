package com.meshcore.repeater.ui.components

import com.meshcore.repeater.ui.theme.BauhausCornerShape

import com.meshcore.repeater.i18n.tr
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val PREFS = "monopong"
private const val KEY_HIGHSCORE = "highscore"

/** Spielzustand in Feld-Koordinaten (0..1 Breite, 0..aspect Höhe; Einheit = Feldbreite). */
private class PongState {
    var x = 0.5f; var y = 0.5f
    var vx = 0f; var vy = 0f
    var speed = 0.55f            // Feldbreiten pro Sekunde
    var paddleX = 0.5f           // Mitte des Schlägers
    var paddleW = 0.30f
    var score = 0
    var running = false
    var gameOver = false

    fun reset(h: Float) {
        x = 0.5f; y = h * 0.45f
        speed = 0.55f; paddleW = 0.30f; paddleX = 0.5f; score = 0
        val dirX = if (Math.random() < 0.5) -0.45f else 0.45f
        val n = hypot(dirX, 1f)
        vx = dirX / n; vy = 1f / n
        running = false; gameOver = false
    }
}

/**
 * Bauhaus-Easteregg "MonoPong": Solo-Pong gegen eine Prellwand.
 * Jeder Treffer: +1 Punkt, Ball schneller; alle 4 Punkte wird der Schläger schmaler.
 */
@Composable
fun MonoPongDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var highscore by remember { mutableIntStateOf(prefs.getInt(KEY_HIGHSCORE, 0)) }
    val state = remember { PongState() }
    var frame by remember { mutableLongStateOf(0L) }       // Redraw-Trigger
    var fieldSize by remember { mutableStateOf(Size.Zero) }
    var score by remember { mutableIntStateOf(0) }
    var gameOver by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(0) }

    val aspect = if (fieldSize.width > 0f) fieldSize.height / fieldSize.width else 1.5f
    val wallH = 0.035f
    val paddleY = aspect - 0.07f
    val paddleH = 0.025f
    val r = 0.022f

    LaunchedEffect(round, fieldSize) {
        if (fieldSize == Size.Zero) return@LaunchedEffect
        state.reset(aspect)
        score = 0; gameOver = false; running = false
        var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0f else min((now - last) / 1_000_000_000f, 0.032f)
            last = now
            if (state.running && !state.gameOver) {
                // in kleinen Schritten bewegen, damit der Ball bei hoher Geschwindigkeit nicht durchtunnelt
                val steps = max(1, (state.speed * dt / (r * 0.5f)).toInt() + 1)
                val sdt = dt / steps
                repeat(steps) {
                    if (state.gameOver) return@repeat
                    state.x += state.vx * state.speed * sdt
                    state.y += state.vy * state.speed * sdt
                    if (state.x - r < 0f) { state.x = r; state.vx = abs(state.vx) }
                    if (state.x + r > 1f) { state.x = 1f - r; state.vx = -abs(state.vx) }
                    if (state.y - r < wallH) { state.y = wallH + r; state.vy = abs(state.vy) }
                    // Schläger
                    if (state.vy > 0 && state.y + r >= paddleY && state.y + r <= paddleY + paddleH + state.speed * sdt * 2) {
                        val half = state.paddleW / 2f
                        if (state.x >= state.paddleX - half - r && state.x <= state.paddleX + half + r) {
                            // Abprallwinkel je nach Trefferpunkt
                            val rel = ((state.x - state.paddleX) / (half + r)).coerceIn(-1f, 1f)
                            val nvx = rel * 0.85f
                            val nvy = -sqrt(max(0.15f, 1f - nvx * nvx))
                            val n = hypot(nvx, nvy)
                            state.vx = nvx / n; state.vy = nvy / n
                            state.y = paddleY - r
                            state.score++
                            state.speed *= 1.07f
                            if (state.score % 4 == 0) state.paddleW = max(0.10f, state.paddleW * 0.86f)
                            score = state.score
                        }
                    }
                    if (state.y - r > aspect) {
                        state.gameOver = true; state.running = false
                        gameOver = true; running = false
                        if (state.score > highscore) { highscore = state.score; prefs.edit().putInt(KEY_HIGHSCORE, highscore).apply() }
                    }
                }
            }
            frame = now
        }
    }

    // Echtes Vollbild: Dialogfenster über den ganzen Bildschirm (auch unter die Systemleisten), Carbon-Grund deckt alles ab
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MeshchoseiColors.Carbon)
                .systemBarsPadding()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Kopfzeile: Titel, Punktestand, Highscore
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MeshchoseiEmblem(Modifier.size(26.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        androidx.compose.ui.text.buildAnnotatedString {
                            append("MonoP")
                            pushStyle(androidx.compose.ui.text.SpanStyle(color = MeshchoseiColors.SignalRed, fontWeight = FontWeight.Black)); append("ō"); pop()
                            append("ng")
                        },
                        color = MeshchoseiColors.Paper, fontWeight = FontWeight.Bold, letterSpacing = 0.12.em, fontSize = 17.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$score", color = MeshchoseiColors.SignalRed, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Text("HI $highscore", color = MeshchoseiColors.Ochre, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(10.dp))

            Box(Modifier.fillMaxWidth().weight(1f)) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(onDragStart = { p ->
                                if (!state.gameOver) { state.running = true; running = true }
                                state.paddleX = (p.x / size.width).coerceIn(0f, 1f)
                            }) { change, _ ->
                                change.consume()
                                state.paddleX = (change.position.x / size.width).coerceIn(state.paddleW / 2f, 1f - state.paddleW / 2f)
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { p ->
                                if (!state.gameOver) { state.running = true; running = true }
                                state.paddleX = (p.x / size.width).coerceIn(state.paddleW / 2f, 1f - state.paddleW / 2f)
                            }
                        }
                ) {
                    if (frame < 0) return@Canvas
                    if (size.width != fieldSize.width || size.height != fieldSize.height) fieldSize = size
                    val u = size.width   // 1 Feldeinheit in px
                    // Spielfeld-Rahmen
                    drawRect(MeshchoseiColors.Paper.copy(alpha = 0.06f), size = size)
                    // Prellwand oben: Bauhaus-Akzente Signalrot / Kobalt / Ocker
                    val wh = wallH * u
                    drawRect(MeshchoseiColors.SignalRed, Offset(0f, 0f), Size(u * 0.45f, wh))
                    drawRect(MeshchoseiColors.Cobalt, Offset(u * 0.45f, 0f), Size(u * 0.30f, wh))
                    drawRect(MeshchoseiColors.Ochre, Offset(u * 0.75f, 0f), Size(u * 0.25f, wh))
                    // Schläger
                    val pw = state.paddleW * u
                    drawRect(MeshchoseiColors.Paper, Offset(state.paddleX * u - pw / 2f, paddleY * u), Size(pw, paddleH * u))
                    // Ball (rotes Quadrat statt Kreis: Bauhaus)
                    val br = r * u
                    drawRect(MeshchoseiColors.SignalRed, Offset(state.x * u - br, state.y * u - br), Size(br * 2, br * 2))
                }
                if (!running && !gameOver) {
                    Text(tr("Tippen oder ziehen zum Starten"), color = MeshchoseiColors.Paper.copy(alpha = 0.7f), fontSize = 13.sp, letterSpacing = 0.1.em,
                        modifier = Modifier.align(Alignment.Center))
                }
                if (gameOver) {
                    Column(
                        Modifier.align(Alignment.Center).background(MeshchoseiColors.Carbon.copy(alpha = 0.92f), BauhausCornerShape).padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("GAME OVER", color = MeshchoseiColors.SignalRed, fontWeight = FontWeight.Black, letterSpacing = 0.25.em, fontSize = 20.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(tr("Punkte: {0}", score) + if (score > 0 && score >= highscore) tr("  · neuer Highscore!") else "", color = MeshchoseiColors.Paper, fontSize = 13.sp)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { round++ }, shape = BauhausCornerShape, colors = ButtonDefaults.buttonColors(containerColor = MeshchoseiColors.SignalRed)) {
                                Text(tr("Nochmal"), color = MeshchoseiColors.Paper)
                            }
                            OutlinedButton(onClick = onDismiss, shape = BauhausCornerShape, border = BorderStroke(1.dp, MeshchoseiColors.Paper.copy(alpha = 0.6f))) {
                                Text(tr("Schließen"), color = MeshchoseiColors.Paper)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(MESHCHOSEI_SIGNATURE, color = MeshchoseiColors.Paper.copy(alpha = 0.45f), fontSize = 11.sp, letterSpacing = 0.2.em)
        }
    }
}
