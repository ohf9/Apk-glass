package com.fahad.liquidglass.lock

import android.content.Intent
import androidx.core.graphics.drawable.toBitmap
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.BatteryManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.fahad.liquidglass.glass.GlassPanel
import com.fahad.liquidglass.glass.GlassRoot
import com.fahad.liquidglass.glass.LiquidBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Draws a [LockLayout]. Used both by the real lock screen and by the editor
 * (editing = true makes every element draggable / pinchable / rotatable).
 */
@Composable
fun LockCanvas(
    layout: LockLayout,
    editing: Boolean = false,
    selectedId: String? = null,
    onSelect: (String?) -> Unit = {},
    onElementChange: (LockElement) -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val ctx = LocalContext.current
    val store = remember { LockStore(ctx) }
    val tilt = rememberTilt(enabled = !editing && layout.parallax > 0f)
    val wall by rememberBitmap(store.wallpaperFile, layout.hasWallpaper, layout.version)
    val subject by rememberBitmap(store.subjectFile, layout.hasSubject && layout.depth, layout.version)
    val parallaxPx = with(LocalDensity.current) { (layout.parallax * 26).dp.toPx() }
    val now by produceState(Date()) {
        while (true) { value = Date(); delay(1000 - System.currentTimeMillis() % 1000) }
    }
    val battery by produceState(0) {
        while (true) {
            val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            value = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
            delay(60_000)
        }
    }
    val depthOn = subject != null
    val behind = layout.elements.filter { depthOn && it.behindSubject }
    val front = layout.elements.filter { !(depthOn && it.behindSubject) }

    val glassOn = remember { com.fahad.liquidglass.Settings(ctx).glass }
    androidx.compose.runtime.CompositionLocalProvider(
        com.fahad.liquidglass.glass.LocalGlassStyle provides com.fahad.liquidglass.glass.GlassStyle(enabled = glassOn)
    ) {
    GlassRoot(backdrop = { Wallpaper(wall, layout, tilt, parallaxPx) }) {
        if (editing) Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onSelect(null) } })
        Elements(behind, now, battery, editing, selectedId, onSelect, onElementChange, tilt, parallaxPx)
        subject?.let {
            Image(it.asImageBitmap(), null, Modifier.fillMaxSize().parallax(tilt, -parallaxPx, 1.08f), contentScale = ContentScale.Crop)
        }
        Elements(front, now, battery, editing, selectedId, onSelect, onElementChange, tilt, parallaxPx)
        overlay()
    }
    }
}

@Composable
private fun Wallpaper(wall: Bitmap?, layout: LockLayout, tilt: State<Offset>, parallaxPx: Float) {
    Box(Modifier.fillMaxSize().parallax(tilt, -parallaxPx, 1.08f).drawWithContent {
        drawContent(); drawRect(Color.Black.copy(alpha = layout.dim))
    }) {
        val m = if (layout.wallBlur > 0f) Modifier.fillMaxSize().blur(layout.wallBlur.dp) else Modifier.fillMaxSize()
        if (wall != null) Image(wall.asImageBitmap(), null, m, contentScale = ContentScale.Crop)
        else LiquidBackdrop(m)
    }
}

private fun Modifier.parallax(tilt: State<Offset>, px: Float, scale: Float = 1f) = graphicsLayer {
    translationX = tilt.value.x * px
    translationY = -tilt.value.y * px
    scaleX = scale; scaleY = scale
}

@Composable
private fun Elements(
    els: List<LockElement>, now: Date, battery: Int, editing: Boolean, selectedId: String?,
    onSelect: (String?) -> Unit, onChange: (LockElement) -> Unit, tilt: State<Offset>, parallaxPx: Float,
) {
    var snapX by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        if (editing && snapX) Box(Modifier.align(Alignment.Center).width(1.dp).fillMaxHeight().background(Color(0xAAFFD54F)))
        Layout(
            content = {
                els.forEach { e ->
                    key(e.id) {
                        ElementView(e, now, battery, editing, e.id == selectedId, onSelect, onChange, { snapX = it }, tilt, parallaxPx)
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { ms, c ->
            val ps = ms.map { it.measure(c.copy(minWidth = 0, minHeight = 0)) }
            layout(c.maxWidth, c.maxHeight) {
                ps.forEachIndexed { i, p ->
                    val e = els[i]
                    p.place((e.x * c.maxWidth - p.width / 2f).roundToInt(), (e.y * c.maxHeight - p.height / 2f).roundToInt())
                }
            }
        }
    }
}

@Composable
private fun ElementView(
    e: LockElement, now: Date, battery: Int, editing: Boolean, selected: Boolean,
    onSelect: (String?) -> Unit, onChange: (LockElement) -> Unit, onSnap: (Boolean) -> Unit,
    tilt: State<Offset>, parallaxPx: Float,
) {
    val cur by rememberUpdatedState(e)
    val change by rememberUpdatedState(onChange)
    val haptic = LocalHapticFeedback.current
    var m: Modifier = Modifier
    if (editing) {
        m = m.pointerInput(e.id) {
            detectTapGestures { onSelect(cur.id) }
        }.pointerInput(e.id) {
            detectTransformGestures { _, pan, zoom, rot ->
                onSelect(cur.id)
                // Positions are fractions of the whole screen.
                var nx = cur.x + pan.x / screenW
                val ny = cur.y + pan.y / screenH
                val near = abs(nx - 0.5f) < 0.012f
                if (near) { if (cur.x != 0.5f) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); nx = 0.5f }
                onSnap(near)
                change(cur.copy(
                    x = nx.coerceIn(0f, 1f), y = ny.coerceIn(0f, 1f),
                    size = (cur.size * zoom).coerceIn(8f, 220f),
                    rotation = cur.rotation + rot,
                ))
            }
        }
        if (selected) m = m.border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
    } else {
        m = m.graphicsLayer {
            translationX = tilt.value.x * parallaxPx * 0.35f
            translationY = -tilt.value.y * parallaxPx * 0.35f
        }
    }
    m = m.graphicsLayer { rotationZ = e.rotation }

    Box(m) {
        if (e.glass) GlassPanel(cornerRadius = 20.dp, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
            ElementContent(e, now, battery)
        } else Box(Modifier.padding(6.dp)) { ElementContent(e, now, battery) }
    }
}

private val screenW: Float get() = android.content.res.Resources.getSystem().displayMetrics.widthPixels.toFloat()
private val screenH: Float get() = android.content.res.Resources.getSystem().displayMetrics.heightPixels.toFloat()

/** Set by the lock activity: unlock first, then open this intent. */
val LocalAppLauncher = androidx.compose.runtime.staticCompositionLocalOf<(Intent) -> Unit> { {} }

@Composable
fun ElementContent(e: LockElement, now: Date, battery: Int) {
    if (e.type == ElementType.SHORTCUTS) { Shortcuts(e); return }
    if (e.type == ElementType.CLOCK && e.clockStyle == ClockStyle.ANALOG) { AnalogClock(e, now); return }
    if (e.type == ElementType.CLOCK && e.clockStyle == ClockStyle.BIG) { BigClock(e, now); return }
    if (e.type == ElementType.CLOCK && e.clockStyle == ClockStyle.DOTS) { DotsClock(e, now); return }
    val col = Color(e.color)
    var style = TextStyle(
        color = col, fontSize = e.size.sp, fontFamily = e.font.family,
        fontWeight = FontWeight(e.weight.coerceIn(100, 900)), textAlign = TextAlign.Center,
        lineHeight = if (e.type == ElementType.CLOCK) 0.95.em else 1.2.em,
        shadow = Shadow(Color.Black.copy(alpha = 0.25f), Offset(0f, 2f), 12f),
    )
    if (e.type == ElementType.CLOCK) style = when (e.clockStyle) {
        ClockStyle.GLASS -> style.copy(
            brush = Brush.verticalGradient(listOf(col.copy(alpha = 0.95f), col.copy(alpha = 0.35f))),
            shadow = Shadow(Color.White.copy(alpha = 0.45f), Offset(0f, 0f), 24f),
        )
        ClockStyle.OUTLINE -> style.copy(drawStyle = Stroke(width = (e.size / 28f).coerceAtLeast(1.5f)), shadow = null)
        ClockStyle.GRADIENT -> style.copy(
            brush = Brush.linearGradient(listOf(col, Color(0xFFFF6B9A), Color(0xFF7FB2FF))), shadow = null,
        )
        else -> style
    }
    val text = when (e.type) {
        ElementType.CLOCK -> SimpleDateFormat(e.clockFormat.pattern, Locale.ENGLISH).format(now)
        ElementType.DATE -> SimpleDateFormat("EEEE، d MMMM", Locale("ar")).format(now)
        ElementType.BATTERY -> "⚡ $battery%"
        ElementType.TEXT -> e.text.ifBlank { "اكتب نصك" }
        ElementType.SHORTCUTS -> ""
    }
    Text(text, style = style)
}

@Composable
private fun AnalogClock(e: LockElement, now: Date) {
    val col = Color(e.color)
    val cal = remember(now) { java.util.Calendar.getInstance().apply { time = now } }
    val sec = cal.get(java.util.Calendar.SECOND)
    val min = cal.get(java.util.Calendar.MINUTE) + sec / 60f
    val hr = (cal.get(java.util.Calendar.HOUR) % 12) + min / 60f
    androidx.compose.foundation.Canvas(Modifier.size((e.size * 2.2f).dp)) {
        val c = center; val r = size.minDimension / 2f
        drawCircle(col.copy(alpha = 0.10f), r)
        drawCircle(col.copy(alpha = 0.55f), r, style = Stroke(r * 0.02f))
        for (i in 0 until 60) {
            val a = Math.toRadians(i * 6.0 - 90.0)
            val big = i % 5 == 0
            val r1 = r * (if (big) 0.84f else 0.91f)
            drawLine(col.copy(alpha = if (big) 0.95f else 0.4f),
                c + Offset((cos(a) * r1).toFloat(), (sin(a) * r1).toFloat()),
                c + Offset((cos(a) * r * 0.96f).toFloat(), (sin(a) * r * 0.96f).toFloat()),
                strokeWidth = r * (if (big) 0.03f else 0.012f), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
        fun hand(angleDeg: Float, len: Float, w: Float, color: Color) {
            val a = Math.toRadians(angleDeg - 90.0)
            drawLine(color, c, c + Offset((cos(a) * r * len).toFloat(), (sin(a) * r * len).toFloat()),
                strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
        hand(hr * 30f, 0.5f, r * 0.07f, col)
        hand(min * 6f, 0.75f, r * 0.045f, col)
        hand(sec * 6f, 0.82f, r * 0.018f, Color(0xFFFF6B4A))
        drawCircle(Color(0xFFFF6B4A), r * 0.045f, c)
    }
}

/** Each digit is a 3x5 grid of dots. */
@Composable
private fun DotsClock(e: LockElement, now: Date) {
    val col = Color(e.color)
    val t = SimpleDateFormat("HHmm", Locale.ENGLISH).format(now)
    val glyphs = mapOf(
        '0' to "111101101101111", '1' to "010110010010111", '2' to "111001111100111", '3' to "111001111001111",
        '4' to "101101111001001", '5' to "111100111001111", '6' to "111100111101111", '7' to "111001001001001",
        '8' to "111101111101111", '9' to "111101111001111",
    )
    val d = e.size.dp * 0.28f
    Row(horizontalArrangement = Arrangement.spacedBy(d * 1.2f), verticalAlignment = Alignment.CenterVertically) {
        t.forEachIndexed { i, ch ->
            if (i == 2) Column(verticalArrangement = Arrangement.spacedBy(d * 1.2f)) { repeat(2) { Box(Modifier.size(d).background(col, CircleShape)) } }
            Column(verticalArrangement = Arrangement.spacedBy(d * 0.35f)) {
                val g = glyphs[ch]!!
                for (row in 0 until 5) Row(horizontalArrangement = Arrangement.spacedBy(d * 0.35f)) {
                    for (cc in 0 until 3) Box(Modifier.size(d).background(
                        if (g[row * 3 + cc] == '1') col else col.copy(alpha = 0.12f), CircleShape))
                }
            }
        }
    }
}

@Composable
private fun rememberBitmap(file: java.io.File, enabled: Boolean, version: Long) =
    produceState<Bitmap?>(null, file, enabled, version) {
        value = if (enabled) withContext(Dispatchers.IO) { Depth.load(file) } else null
    }

/** Phone / camera / messages shortcuts. Icons come from the icon pack chosen in settings. */
@Composable
private fun Shortcuts(e: LockElement) {
    val ctx = LocalContext.current
    val launch = LocalAppLauncher.current
    val items = remember {
        val pm = ctx.packageManager
        val pack = com.fahad.liquidglass.launcher.IconPacks.load(ctx, com.fahad.liquidglass.Settings(ctx).iconPack)
        listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:")),
        ).mapNotNull { i ->
            val cn = pm.resolveActivity(i, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
                ?.let { android.content.ComponentName(it.packageName, it.name) } ?: return@mapNotNull null
            val d = pack?.iconFor(cn) ?: runCatching { pm.getActivityIcon(cn) }.getOrNull() ?: return@mapNotNull null
            Intent(i).setComponent(cn) to d.toBitmap(128, 128).asImageBitmap()
        }
    }
    val box = (e.size * 3f).dp
    Row(horizontalArrangement = Arrangement.spacedBy(box / 3)) {
        items.forEach { (intent, icon) ->
            GlassPanel(Modifier.size(box).clickable { launch(intent) }, cornerRadius = box / 2, contentPadding = PaddingValues(box * 0.18f)) {
                Image(icon, null, Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * Big tall clock drawn as stroked paths, so the thickness is continuous: from a hairline
 * to a very heavy stroke (no dependency on which font weights the phone has).
 */
@Composable
private fun BigClock(e: LockElement, now: Date) {
    val col = Color(e.color)
    val stacked = e.clockFormat != ClockFormat.H24 && e.clockFormat != ClockFormat.H12 && e.clockFormat != ClockFormat.SECONDS
    val time = SimpleDateFormat(if (e.clockFormat == ClockFormat.H12) "hh:mm" else "HH:mm", Locale.ENGLISH).format(now)
    val rows = if (stacked) listOf(time.substring(0, 2), time.substring(3, 5)) else listOf(time)
    val h = e.size.dp * 1.5f
    val w = h * e.digitWidth.coerceIn(0.3f, 0.9f)
    val stroke = (h * e.thickness.coerceIn(0.01f, 0.3f))
    val gap = h * 0.06f + stroke * 0.15f
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap * 0.7f)) {
        rows.forEach { row ->
            androidx.compose.foundation.Canvas(Modifier.size(
                width = (w + gap) * row.length + stroke, height = h + stroke)) {
                val sw = stroke.toPx(); val dw = w.toPx(); val dh = h.toPx(); val g = gap.toPx()
                val st = Stroke(width = sw, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                val brush = Brush.verticalGradient(listOf(col, col.copy(alpha = 0.78f)))
                row.forEachIndexed { i, ch ->
                    val ox = sw / 2 + i * (dw + g)
                    if (ch == ':') {
                        drawCircle(col, sw * 0.7f, Offset(ox + dw / 2, dh * 0.33f + sw / 2))
                        drawCircle(col, sw * 0.7f, Offset(ox + dw / 2, dh * 0.67f + sw / 2))
                    } else drawGlyph(ch, ox, sw / 2, dw, dh, brush, st)
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlyph(
    ch: Char, ox: Float, oy: Float, w: Float, h: Float, brush: Brush, st: Stroke,
) {
    val p = androidx.compose.ui.graphics.Path()
    fun x(v: Float) = ox + v * w
    fun y(v: Float) = oy + v * h
    fun M(a: Float, b: Float) = p.moveTo(x(a), y(b))
    fun L(a: Float, b: Float) = p.lineTo(x(a), y(b))
    fun C(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) = p.cubicTo(x(a), y(b), x(c), y(d), x(e), y(f))
    fun oval(t: Float, b: Float, inset: Float = 0f) = p.addOval(androidx.compose.ui.geometry.Rect(x(inset), y(t), x(1f - inset), y(b)))
    when (ch) {
        '0' -> p.addRoundRect(androidx.compose.ui.geometry.RoundRect(x(0f), y(0f), x(1f), y(1f),
            androidx.compose.ui.geometry.CornerRadius(w / 2, w / 2)))
        '1' -> { M(0.15f, 0.2f); L(0.55f, 0f); L(0.55f, 1f) }
        '2' -> { M(0f, 0.25f); C(0f, -0.08f, 1f, -0.08f, 1f, 0.28f); C(1f, 0.55f, 0f, 0.72f, 0f, 1f); L(1f, 1f) }
        '3' -> { M(0f, 0.1f); C(0.1f, -0.05f, 1f, -0.05f, 1f, 0.25f); C(1f, 0.45f, 0.55f, 0.5f, 0.35f, 0.5f)
            C(0.55f, 0.5f, 1f, 0.55f, 1f, 0.75f); C(1f, 1.05f, 0.1f, 1.05f, 0f, 0.9f) }
        '4' -> { M(0.75f, 1f); L(0.75f, 0f); L(0f, 0.68f); L(1f, 0.68f) }
        '5' -> { M(1f, 0f); L(0.08f, 0f); L(0.02f, 0.45f); C(0.3f, 0.35f, 1f, 0.4f, 1f, 0.72f); C(1f, 1.05f, 0.1f, 1.05f, 0f, 0.88f) }
        '6' -> { M(0.95f, 0.1f); C(0.7f, -0.05f, 0f, -0.02f, 0f, 0.6f); oval(0.42f, 1f) }
        '7' -> { M(0f, 0f); L(1f, 0f); L(0.35f, 1f) }
        '8' -> { oval(0f, 0.52f, 0.06f); oval(0.48f, 1f) }
        '9' -> { M(0.05f, 0.9f); C(0.3f, 1.05f, 1f, 1.02f, 1f, 0.4f); oval(0f, 0.58f) }
    }
    drawPath(p, brush, style = st)
}
