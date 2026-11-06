package com.fahad.liquidglass.lock

import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.BatteryManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
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

@Composable
fun ElementContent(e: LockElement, now: Date, battery: Int) {
    val style = TextStyle(
        color = Color(e.color), fontSize = e.size.sp, fontFamily = e.font.family,
        fontWeight = FontWeight(e.weight.coerceIn(100, 900)), textAlign = TextAlign.Center,
        lineHeight = if (e.type == ElementType.CLOCK) 0.95.em else 1.2.em,
        shadow = Shadow(Color.Black.copy(alpha = 0.25f), Offset(0f, 2f), 12f),
    )
    val text = when (e.type) {
        ElementType.CLOCK -> SimpleDateFormat(e.clockFormat.pattern, Locale.ENGLISH).format(now)
        ElementType.DATE -> SimpleDateFormat("EEEE، d MMMM", Locale("ar")).format(now)
        ElementType.BATTERY -> "⚡ $battery%"
        ElementType.TEXT -> e.text.ifBlank { "اكتب نصك" }
    }
    Text(text, style = style)
}

@Composable
private fun rememberBitmap(file: java.io.File, enabled: Boolean, version: Long) =
    produceState<Bitmap?>(null, file, enabled, version) {
        value = if (enabled) withContext(Dispatchers.IO) { Depth.load(file) } else null
    }
