package com.meshcore.repeater.ui.components

import com.meshcore.repeater.ui.theme.BauhausCornerShape

import com.meshcore.repeater.i18n.tr
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.tan

private const val USER_AGENT = "MeshCoreRepeaterConfigurator/0.93 (Android)"
private const val MIN_ZOOM = 4
private const val MAX_ZOOM = 18
private val GRID_STEPS_DEG = doubleArrayOf(0.0005, 0.001, 0.002, 0.005, 0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0)

/** App-weiter Speicher-Cache für OSM-Kacheln (überlebt das Wechseln zwischen Screens). */
private object OsmTileCache {
    val tiles = LruCache<String, ImageBitmap>(96)
    val failedAt = HashMap<String, Long>()
    val inFlight = HashSet<String>()
    val limiter = Semaphore(4)
}

/** Web-Mercator: Welt-Pixelkoordinaten bei [zoom] und Kachelgröße [tilePx]. */
private fun worldX(lon: Double, zoom: Int, tilePx: Double) = (lon + 180.0) / 360.0 * 2.0.pow(zoom) * tilePx
private fun worldY(lat: Double, zoom: Int, tilePx: Double): Double {
    val r = Math.toRadians(lat.coerceIn(-85.05, 85.05))
    return (1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * 2.0.pow(zoom) * tilePx
}
private fun lonFromX(x: Double, zoom: Int, tilePx: Double) = x / (2.0.pow(zoom) * tilePx) * 360.0 - 180.0
private fun latFromY(y: Double, zoom: Int, tilePx: Double): Double {
    val n = PI - 2.0 * PI * y / (2.0.pow(zoom) * tilePx)
    return Math.toDegrees(atan(0.5 * (exp(n) - exp(-n))))
}

/**
 * Native Compose-Canvas-Kachelkarte (OpenStreetMap):
 *  - Koordinaten-Gitter mit Grad-Beschriftung als sofort sichtbarer Offline-/Lade-Hintergrund
 *  - darüber die asynchron geladenen OSM-Kacheln (Speicher-Cache, gültiger User-Agent)
 *  - rotes Fadenkreuz in der Mitte = Standort, gestrichelter 200-m-Privatsphäre-Radius
 *  - Ziehen verschiebt die Karte und setzt lat/lon (Mittelpunkt), Tippen springt auf den Punkt,
 *    +/− zoomt. Die Gesten werden konsumiert -> die äußere verticalScroll-Seite scrollt nicht mit.
 */
@Composable
fun OsmTileMap(
    lat: Double,
    lon: Double,
    hasPosition: Boolean,
    onPositionChange: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
    markerColor: Color = Color(0xFFD72626),
    radiusColor: Color = Color(0xFF1E5AA8),
    privacyRadiusM: Double = 200.0
) {
    val latestOnChange by rememberUpdatedState(onPositionChange)
    val density = LocalDensity.current
    val tilePx = with(density) { 256.dp.toPx() }.toDouble()   // Kacheln in dp-Größe: lesbare Beschriftung
    var size by remember { mutableStateOf(IntSize.Zero) }
    val hasPos = hasPosition
    val startLat = lat.takeIf { it.isFinite() && it in -85.0..85.0 } ?: 51.8810
    val startLon = lon.takeIf { it.isFinite() && it in -180.0..180.0 } ?: 10.5620
    var centerLat by remember { mutableDoubleStateOf(startLat) }
    var centerLon by remember { mutableDoubleStateOf(startLon) }
    var zoom by remember { mutableIntStateOf(15) }
    var tileVersion by remember { mutableIntStateOf(0) }   // Redraw-Trigger, wenn Kacheln eintreffen
    var lastPushMs by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()

    // Externe Änderung (Textfelder, ±200m-Buttons, GPS): Karte auf die neue Position zentrieren
    LaunchedEffect(lat, lon) {
        if (!lat.isFinite() || !lon.isFinite() || lat !in -85.0..85.0 || lon !in -180.0..180.0) return@LaunchedEffect
        if (kotlin.math.abs(lat - centerLat) > 1e-7 || kotlin.math.abs(lon - centerLon) > 1e-7) {
            centerLat = lat; centerLon = lon
        }
    }

    fun pushPosition(force: Boolean) {
        val now = System.currentTimeMillis()
        if (force || now - lastPushMs > 120L) { lastPushMs = now; latestOnChange(centerLat, centerLon) }
    }

    // Sichtbare Kacheln (typisch 3x3 um die Mitte) nachladen
    val cx = worldX(centerLon, zoom, tilePx); val cy = worldY(centerLat, zoom, tilePx)
    val n = 1 shl zoom
    val minTx = floor((cx - size.width / 2.0) / tilePx).toInt(); val maxTx = floor((cx + size.width / 2.0) / tilePx).toInt()
    val minTy = floor((cy - size.height / 2.0) / tilePx).toInt().coerceAtLeast(0); val maxTy = floor((cy + size.height / 2.0) / tilePx).toInt().coerceAtMost(n - 1)
    LaunchedEffect(zoom, minTx, maxTx, minTy, maxTy, size) {
        if (size == IntSize.Zero) return@LaunchedEffect
        for (ty in minTy..maxTy) for (tx in minTx..maxTx) {
            val wx = ((tx % n) + n) % n
            val key = "$zoom/$wx/$ty"
            if (OsmTileCache.tiles.get(key) != null || key in OsmTileCache.inFlight) continue
            val failed = OsmTileCache.failedAt[key]
            if (failed != null && System.currentTimeMillis() - failed < 30_000L) continue
            OsmTileCache.inFlight += key
            scope.launch {
                try {
                    // Netzwerk + Bitmap-Dekodierung ausschließlich in Dispatchers.IO
                    val bmp = OsmTileCache.limiter.withPermit { withContext(Dispatchers.IO) { fetchTile(key) } }
                    if (bmp != null) { OsmTileCache.tiles.put(key, bmp); OsmTileCache.failedAt.remove(key) } else OsmTileCache.failedAt[key] = System.currentTimeMillis()
                    tileVersion++
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    OsmTileCache.failedAt[key] = System.currentTimeMillis()
                } finally {
                    OsmTileCache.inFlight -= key
                }
            }
        }
    }

    val bg = MaterialTheme.colorScheme.surfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = labelColor)
    val compassStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = markerColor)
    val attrStyle = TextStyle(fontSize = 9.sp, color = Color(0xFF333333))

    Box(modifier.background(bg)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .excludeFromDrawerGesture()   // v1.23: Karte verschieben zieht nie das Seitenmenü auf
                .onSizeChanged { size = it }
                .pointerInput(tilePx) {
                    detectDragGestures(
                        onDragEnd = { pushPosition(force = true) },
                        onDragCancel = { pushPosition(force = true) }
                    ) { change, drag ->
                        change.consume()
                        val x = worldX(centerLon, zoom, tilePx) - drag.x
                        val y = (worldY(centerLat, zoom, tilePx) - drag.y).coerceIn(0.0, 2.0.pow(zoom) * tilePx)
                        centerLat = latFromY(y, zoom, tilePx).coerceIn(-85.0, 85.0)
                        centerLon = lonFromX(x, zoom, tilePx).let { ((it + 180) % 360 + 360) % 360 - 180 }
                        pushPosition(force = false)
                    }
                }
                .pointerInput(tilePx) {
                    detectTapGestures { p ->
                        val x = worldX(centerLon, zoom, tilePx) + (p.x - size.width / 2.0)
                        val y = worldY(centerLat, zoom, tilePx) + (p.y - size.height / 2.0)
                        centerLat = latFromY(y, zoom, tilePx).coerceIn(-85.0, 85.0)
                        centerLon = lonFromX(x, zoom, tilePx).let { ((it + 180) % 360 + 360) % 360 - 180 }
                        pushPosition(force = true)
                    }
                }
        ) {
            if (tileVersion < 0) return@Canvas // liest tileVersion -> Redraw, sobald neue Kacheln da sind
            val w = this.size.width; val h = this.size.height
            if (w < 2f || h < 2f || !centerLat.isFinite() || !centerLon.isFinite()) return@Canvas
            try {
            /** Beschriftung nur zeichnen, wenn sie vollständig im Canvas liegt (sonst negative Constraints -> Absturz). */
            fun safeLabel(s: String, at: Offset) {
                if (!at.x.isFinite() || !at.y.isFinite() || at.x < 0f || at.y < 0f) return
                val m = textMeasurer.measure(s, labelStyle)
                if (at.x + m.size.width > w || at.y + m.size.height > h) return
                drawText(m, topLeft = at)
            }
            val ox = worldX(centerLon, zoom, tilePx) - w / 2.0
            val oy = worldY(centerLat, zoom, tilePx) - h / 2.0
            fun sx(lonV: Double) = (worldX(lonV, zoom, tilePx) - ox).toFloat()
            fun sy(latV: Double) = (worldY(latV, zoom, tilePx) - oy).toFloat()

            // 1) Koordinaten-Gitter (Offline-/Lade-Hintergrund)
            val pxPerDegLon = 2.0.pow(zoom) * tilePx / 360.0
            val stepDeg = GRID_STEPS_DEG.firstOrNull { it * pxPerDegLon >= 80.0 } ?: GRID_STEPS_DEG.last()
            val decimals = max(0, ceil(-log10(stepDeg)).toInt())
            val fmt = "%.${decimals}f"
            val west = lonFromX(ox, zoom, tilePx); val east = lonFromX(ox + w, zoom, tilePx)
            val north = latFromY(oy.coerceAtLeast(0.0), zoom, tilePx); val south = latFromY((oy + h).coerceAtMost(2.0.pow(zoom) * tilePx), zoom, tilePx)
            var g = floor(west / stepDeg) * stepDeg; var guard = 0
            while (g <= east && guard++ < 400) {
                val x = sx(g)
                drawLine(gridColor.copy(alpha = 0.45f), Offset(x, 0f), Offset(x, h), 1f)
                safeLabel(String.format(Locale.US, fmt, g), Offset(x + 3f, h - 14.dp.toPx()))
                g += stepDeg
            }
            g = floor(south / stepDeg) * stepDeg; guard = 0
            while (g <= north && guard++ < 400) {
                val y = sy(g)
                drawLine(gridColor.copy(alpha = 0.45f), Offset(0f, y), Offset(w, y), 1f)
                safeLabel(String.format(Locale.US, fmt, g), Offset(4f, y + 2f))
                g += stepDeg
            }

            // 2) OSM-Kacheln darüber
            val t = tilePx.toFloat(); val tSize = IntSize(ceil(tilePx).toInt(), ceil(tilePx).toInt())
            val nTiles = 1 shl zoom
            for (ty in floor(oy / tilePx).toInt()..floor((oy + h) / tilePx).toInt()) {
                if (ty < 0 || ty >= nTiles) continue
                for (tx in floor(ox / tilePx).toInt()..floor((ox + w) / tilePx).toInt()) {
                    val wx = ((tx % nTiles) + nTiles) % nTiles
                    val img = OsmTileCache.tiles.get("$zoom/$wx/$ty") ?: continue
                    drawImage(img, srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height),
                        dstOffset = IntOffset((tx * tilePx - ox).toInt(), (ty * tilePx - oy).toInt()), dstSize = tSize)
                }
            }

            // 3) N/O/S/W
            fun compass(s: String, at: Offset) { val m = textMeasurer.measure(s, compassStyle); drawText(m, topLeft = Offset(at.x - m.size.width / 2f, at.y - m.size.height / 2f)) }
            compass("N", Offset(w / 2f, 12.dp.toPx())); compass("S", Offset(w / 2f, h - 30.dp.toPx()))
            compass("W", Offset(12.dp.toPx(), h / 2f)); compass("O", Offset(w - 56.dp.toPx(), h / 2f))

            // 4) 200-m-Radius + Fadenkreuz in der Mitte
            run {
                val c = Offset(w / 2f, h / 2f)
                val metersPerPx = 156_543.03392 * cos(Math.toRadians(centerLat)) / 2.0.pow(zoom) * (256.0 / tilePx)
                val r = (privacyRadiusM / metersPerPx).toFloat()
                if (hasPos && r.isFinite() && r > 3f) {
                    drawCircle(radiusColor.copy(alpha = 0.14f), r, c)
                    drawCircle(radiusColor, r, c, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f))))
                }
                val arm = 18.dp.toPx(); val gap = 5.dp.toPx(); val sw = 2.5.dp.toPx()
                drawLine(markerColor, Offset(c.x - arm, c.y), Offset(c.x - gap, c.y), sw)
                drawLine(markerColor, Offset(c.x + gap, c.y), Offset(c.x + arm, c.y), sw)
                drawLine(markerColor, Offset(c.x, c.y - arm), Offset(c.x, c.y - gap), sw)
                drawLine(markerColor, Offset(c.x, c.y + gap), Offset(c.x, c.y + arm), sw)
                drawCircle(markerColor, 10.dp.toPx(), c, style = Stroke(sw))
                drawCircle(markerColor, 2.dp.toPx(), c)
            }

            // 5) Pflicht-Attribution
            val a = textMeasurer.measure(tr("© OpenStreetMap-Mitwirkende"), attrStyle)
            drawRect(Color.White.copy(alpha = 0.8f), topLeft = Offset(w - a.size.width - 8f, h - a.size.height - 4f), size = androidx.compose.ui.geometry.Size(a.size.width + 8f, a.size.height + 4f))
            drawText(a, topLeft = Offset(w - a.size.width - 4f, h - a.size.height - 2f))
            } catch (_: Throwable) { /* Zeichenfehler darf den Screen nie abstürzen lassen */ }
        }

        Column(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TileMapButton("+") { zoom = (zoom + 1).coerceAtMost(MAX_ZOOM) }
            TileMapButton("−") { zoom = (zoom - 1).coerceAtLeast(MIN_ZOOM) }
        }
        Surface(Modifier.align(Alignment.TopStart).padding(8.dp), shape = BauhausCornerShape, color = Color.Black.copy(alpha = 0.6f)) {
            Text(if (hasPos) tr("Zoom {0} · ziehen = verschieben", zoom) else tr("Ziehen oder tippen = Standort setzen"), color = Color.White, fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}

private fun fetchTile(key: String): ImageBitmap? = try {
    val conn = URL("https://tile.openstreetmap.org/$key.png").openConnection() as HttpURLConnection
    conn.setRequestProperty("User-Agent", USER_AGENT)
    conn.connectTimeout = 6000; conn.readTimeout = 8000
    conn.useCaches = true
    try {
        if (conn.responseCode == 200) conn.inputStream.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } else null
    } finally { conn.disconnect() }
} catch (_: Throwable) { null }

@Composable
private fun TileMapButton(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = BauhausCornerShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), tonalElevation = 2.dp, modifier = Modifier.size(36.dp)) {
        Box(contentAlignment = Alignment.Center) { Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    }
}
