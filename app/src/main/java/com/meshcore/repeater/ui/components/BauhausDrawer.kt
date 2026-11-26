package com.meshcore.repeater.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Zustand des interaktiven Bauhaus-Drawers (v1.22). Gleiche Oberfläche wie Material `DrawerState`
 * (`isOpen`, `isClosed`, `targetValue`, `open()`, `close()`), damit der Aufrufer unverändert bleibt.
 */
@Stable
class BauhausDrawerState(initiallyOpen: Boolean = false) {
    /** 0 = geschlossen, 1 = offen. */
    internal val fraction = Animatable(if (initiallyOpen) 1f else 0f)

    var currentValue by mutableStateOf(if (initiallyOpen) DrawerValue.Open else DrawerValue.Closed)
        private set
    /** Ziel der laufenden Bewegung bzw. der aktuelle Ruhezustand. */
    var targetValue by mutableStateOf(currentValue)
        private set

    /** Offen oder auf dem Weg dorthin (Zurück-Taste schließt dann sofort). */
    val isOpen: Boolean get() = targetValue == DrawerValue.Open
    val isClosed: Boolean get() = !isOpen

    suspend fun open() = animateTo(DrawerValue.Open)
    suspend fun close() = animateTo(DrawerValue.Closed)

    internal suspend fun animateTo(value: DrawerValue) {
        targetValue = value
        fraction.animateTo(if (value == DrawerValue.Open) 1f else 0f, tween(durationMillis = 220))
        currentValue = value
    }

    /** Während des Ziehens: Position direkt setzen (stoppt eine laufende Animation). */
    internal suspend fun dragTo(f: Float) { fraction.snapTo(f) }

    companion object {
        val Saver = Saver<BauhausDrawerState, Boolean>(save = { it.currentValue == DrawerValue.Open }, restore = { BauhausDrawerState(it) })
    }
}

/**
 * Reservierte Flächen (v1.23): Slider, Slide-to-Confirm und Karte melden ihre Bounds (Root-Koordinaten). Setzt der
 * Finger bei geschlossenem Menü dort auf, übernimmt der Drawer die Geste garantiert nicht – schon ab dem Down-Event,
 * unabhängig davon, wann das Kind konsumiert.
 */
class DrawerGestureExclusions {
    private val rects = HashMap<Any, Rect>()
    internal fun put(key: Any, r: Rect) { rects[key] = r }
    internal fun remove(key: Any) { rects.remove(key) }
    fun contains(p: Offset): Boolean = rects.values.any { it.contains(p) }
}

val LocalDrawerGestureExclusions = staticCompositionLocalOf<DrawerGestureExclusions?> { null }

/** Fläche dieses Elements für die Drawer-Geste reservieren (z. B. Slider-Zeile). */
fun Modifier.excludeFromDrawerGesture(): Modifier = composed {
    val reg = LocalDrawerGestureExclusions.current ?: return@composed this
    val key = remember { Any() }
    DisposableEffect(reg) { onDispose { reg.remove(key) } }
    onGloballyPositioned { reg.put(key, it.boundsInRoot()) }
}

@Composable
fun rememberBauhausDrawerState(): BauhausDrawerState = rememberSaveable(saver = BauhausDrawerState.Saver) { BauhausDrawerState() }

/**
 * Schlanker Modal-Drawer im Aussehen von `ModalNavigationDrawer`: Menü links, abgedunkeltes Scrim daneben
 * (Antippen schließt). Gesten im Pass `Initial` über die ganze Fläche: Eine waagerechte Geste ab 12 dp
 * (|dx| > |dy| · 1,4) übernimmt der Drawer und bewegt das Menü 1:1 mit dem Finger; beim Loslassen rastet es ab
 * 35 % Weg oder bei zügigem Wisch ein. Hat ein Kind-Element die Geste vorher konsumiert (Slider,
 * Slide-to-Confirm, Karte, vertikaler Scroller), bleibt der Drawer unbeteiligt. System-Zurück: `BackHandler`
 * beim Aufrufer.
 */
@Composable
fun BauhausModalDrawer(
    state: BauhausDrawerState,
    drawerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    var widthPx by remember { mutableFloatStateOf(0f) }
    val scrimColor = DrawerDefaults.scrimColor
    val exclusions = remember { DrawerGestureExclusions() }
    var originInRoot by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { originInRoot = it.positionInRoot() }
            .pointerInput(state) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val startFraction = state.fraction.value
                    val startOpen = startFraction >= 0.5f
                    val excluded = exclusions.contains(down.position + originInRoot)
                    var childConsumed = false
                    var claimed = false
                    var observed = false   // erst nach einem beobachteten Main-Pass übernehmen (schneller Wisch auf Slider)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        if (!change.pressed) {
                            if (claimed) {
                                change.consume()
                                val open = DrawerGesture.settleOpen(startOpen, state.fraction.value, tracker.calculateVelocity().x, density)
                                scope.launch { state.animateTo(if (open) DrawerValue.Open else DrawerValue.Closed) }
                            }
                            break
                        }
                        if (!claimed && observed && DrawerGesture.shouldClaim(dx, dy, density, startFraction, childConsumed, excluded)) claimed = true
                        if (claimed) {
                            change.consume()   // Kinder (Scroller, Buttons) bekommen die Geste nicht mehr
                            val f = DrawerGesture.fractionFor(startFraction, dx, widthPx)
                            scope.launch { state.dragTo(f) }
                        } else {
                            // Nach dem Main-Pass prüfen, ob ein Kind die Bewegung beansprucht hat
                            val final = awaitPointerEvent(PointerEventPass.Final)
                            if (final.changes.any { it.id == down.id && it.isConsumed }) childConsumed = true
                            observed = true
                            if (final.changes.none { it.id == down.id && it.pressed }) break
                        }
                    }
                }
            }
    ) {
        CompositionLocalProvider(LocalDrawerGestureExclusions provides exclusions) { content() }
        val f = state.fraction.value
        if (f > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(scrimColor.copy(alpha = scrimColor.alpha * f))
                    .pointerInput(state) { detectTapGestures { scope.launch { state.close() } } }
            )
        }
        Box(
            Modifier
                .fillMaxHeight()
                .onSizeChanged { widthPx = it.width.toFloat() }
                .offset { IntOffset(((state.fraction.value - 1f) * widthPx).roundToInt(), 0) }
        ) { drawerContent() }
    }
}
