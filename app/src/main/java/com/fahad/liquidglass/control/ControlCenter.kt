package com.fahad.liquidglass.control

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fahad.liquidglass.glass.GlassPanel
import com.fahad.liquidglass.glass.GlassStyle
import com.fahad.liquidglass.glass.LocalGlassStyle
import com.fahad.liquidglass.lock.GlassEase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Touch state handed over from the edge trigger (a plain View) to Compose. */
class ControlCenterState {
    var drag by mutableFloatStateOf(0f)
    var dragging by mutableStateOf(false)
    var releases by mutableIntStateOf(0)
    var opens by mutableIntStateOf(0)   // opened fully from the quick settings tile
    var height = 1f
    fun dragFromTrigger(dy: Float, h: Float) { dragging = true; drag = dy; height = h }
    fun releaseFromTrigger() { if (dragging) { dragging = false; releases++ } }
    fun reset() { drag = 0f; dragging = false }
}

@Composable
fun ControlCenter(
    state: ControlCenterState,
    style: GlassStyle,
    motionBlur: Float,
    bounce: Float,
    globalAction: ((Int) -> Boolean)?,
    onProgress: (Float) -> Unit,
    onClosed: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val openDist = state.height * 0.4f
    val damping = 1f - bounce * 0.6f
    val density = LocalDensity.current

    LaunchedEffect(state.drag, state.dragging) {
        if (state.dragging) progress.snapTo((state.drag / openDist).coerceIn(0f, 1.12f))
    }
    suspend fun settle(velocity: Float = 0f) {
        if (progress.value > 0.35f || velocity > 1200f) progress.animateTo(1f, spring(damping, Spring.StiffnessLow))
        else { progress.animateTo(0f, tween(220, easing = GlassEase)); onClosed() }
    }
    LaunchedEffect(state.releases) { if (state.releases > 0) settle() }
    LaunchedEffect(state.opens) { if (state.opens > 0) progress.animateTo(1f, spring(damping, Spring.StiffnessLow)) }
    LaunchedEffect(Unit) { snapshotFlow { progress.value }.collect { onProgress(it) } }
    fun close(then: () -> Unit = {}) = scope.launch {
        progress.animateTo(0f, tween(240, easing = GlassEase)); onClosed(); then()
    }
    val ctx = LocalContext.current
    fun open(i: Intent) = close { runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }

    CompositionLocalProvider(LocalGlassStyle provides style) {
        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { close() } }
                .pointerInput(Unit) {
                    var last = 0f
                    detectVerticalDragGestures(
                        onVerticalDrag = { ch, dy -> last = dy; scope.launch { progress.snapTo((progress.value + dy / openDist).coerceIn(0f, 1.12f)) }; ch.consume() },
                        onDragEnd = { scope.launch { settle(last * 60f) } },
                    )
                }
        ) {
            Column(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp).widthIn(max = 460.dp)
                    .graphicsLayer {
                        val p = progress.value
                        translationY = (p.coerceAtMost(1f) - 1f) * 160.dp.toPx() + (p - 1f).coerceAtLeast(0f) * 220.dp.toPx()
                        alpha = p.coerceIn(0f, 1f)
                        val s = 0.88f + 0.12f * p.coerceAtMost(1f)
                        scaleX = s; scaleY = s
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.85f, 0f)
                        // Motion blur: vertical smear proportional to how fast the panel moves.
                        val mb = (abs(progress.velocity) * 0.08f * motionBlur * 40f).coerceAtMost(40f)
                        renderEffect = if (mb > 0.5f && Build.VERSION.SDK_INT >= 31)
                            BlurEffect(mb * 0.1f, mb, TileMode.Decal) else null
                    }
                    .pointerInput(Unit) { detectTapGestures { } }, // taps on the panel don't close it
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val stagger = { i: Int -> { ((progress.value - i * 0.06f) / 0.7f).coerceIn(0f, 1f) } }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Connectivity block (2x2)
                    GlassPanel(Modifier.weight(1f).aspectRatio(1f).rise(stagger(0)), cornerRadius = 30.dp, contentPadding = PaddingValues(12.dp)) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                RoundToggle(Icons.Rounded.Wifi, Color(0xFF3D8BFF)) {
                                    open(Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS))
                                }
                                RoundToggle(Icons.Rounded.Bluetooth, Color(0xFF3D8BFF)) { open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                RoundToggle(Icons.Rounded.SignalCellularAlt, Color(0xFF34C77B)) {
                                    open(Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS))
                                }
                                RoundToggle(Icons.Rounded.Nfc, Color(0xFF8E7CFF)) { open(Intent(Settings.ACTION_NFC_SETTINGS)) }
                            }
                        }
                    }
                    // Brightness + volume (tall sliders)
                    Row(Modifier.weight(1f).aspectRatio(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BrightnessSlider(Modifier.weight(1f).fillMaxHeight().rise(stagger(1)), onNeedPermission = {
                            open(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                        })
                        VolumeSlider(Modifier.weight(1f).fillMaxHeight().rise(stagger(2)))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TorchTile(Modifier.weight(1f).rise(stagger(3)))
                    // These two need the optional accessibility permission; hidden without it.
                    if (globalAction != null) {
                        Tile(Icons.Rounded.Screenshot, "لقطة", Modifier.weight(1f).rise(stagger(4))) {
                            close { scope.launch { delay(350); globalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) } }
                        }
                        Tile(Icons.Rounded.Lock, "قفل", Modifier.weight(1f).rise(stagger(5))) {
                            close { globalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) }
                        }
                    }
                    Tile(Icons.Rounded.DoNotDisturbOn, "عدم الإزعاج", Modifier.weight(1f).rise(stagger(6))) {
                        open(Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile(Icons.Rounded.Settings, "الإعدادات", Modifier.weight(1f).rise(stagger(7))) { open(Intent(Settings.ACTION_SETTINGS)) }
                    Tile(Icons.Rounded.BatteryChargingFull, "البطارية", Modifier.weight(1f).rise(stagger(8))) {
                        open(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
                    }
                }
            }
        }
    }
}

/** Each tile rises into place a little after the previous one. */
private fun Modifier.rise(p: () -> Float) = graphicsLayer {
    val v = p()
    translationY = (1f - v) * -40.dp.toPx()
    alpha = v
    val s = 0.9f + 0.1f * v; scaleX = s; scaleY = s
}

/** iOS-like squish on press. */
fun Modifier.squish(onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.88f else 1f, spring(0.4f, Spring.StiffnessMedium), label = "squish")
    graphicsLayer { scaleX = s; scaleY = s }.clickable(src, null, onClick = onClick)
}

@Composable
private fun RoundToggle(icon: ImageVector, color: Color, onClick: () -> Unit) {
    Box(Modifier.size(54.dp).squish(onClick).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White)
    }
}

@Composable
private fun Tile(icon: ImageVector, label: String, modifier: Modifier, active: Boolean = false, onClick: () -> Unit) {
    GlassPanel(modifier.aspectRatio(1f).squish(onClick), cornerRadius = 24.dp, contentPadding = PaddingValues(8.dp),
        tint = if (active) Color(0xFFFFD54F) else Color.White) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, color = Color.White, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun TorchTile(modifier: Modifier) {
    val ctx = LocalContext.current
    val cm = remember { ctx.getSystemService(CameraManager::class.java) }
    val camId = remember {
        cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    }
    var on by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val cb = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(id: String, enabled: Boolean) { if (id == camId) on = enabled }
        }
        cm.registerTorchCallback(cb, null)
        onDispose { cm.unregisterTorchCallback(cb) }
    }
    Tile(Icons.Rounded.FlashlightOn, "الكشاف", modifier, active = on) {
        camId?.let { runCatching { cm.setTorchMode(it, !on) } }
    }
}

/** Tall glass slider you drag up/down, like iOS. */
@Composable
private fun TallSlider(value: Float, icon: ImageVector, modifier: Modifier, onChange: (Float) -> Unit) {
    var h by remember { mutableFloatStateOf(1f) }
    val shown by animateFloatAsState(value, spring(0.9f, Spring.StiffnessHigh), label = "slider")
    val cur by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    GlassPanel(modifier, cornerRadius = 26.dp, contentPadding = PaddingValues(0.dp)) {
        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    h = size.height.toFloat()
                    detectVerticalDragGestures { ch, dy -> ch.consume(); change((cur - dy / h).coerceIn(0f, 1f)) }
                }
                .pointerInput(Unit) { detectTapGestures { o -> change(1f - o.y / size.height) } }
        ) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(shown).background(Color.White.copy(alpha = 0.85f)))
            Icon(icon, null, tint = if (shown > 0.15f) Color(0xFF4A4A55) else Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp))
        }
    }
}

@Composable
private fun BrightnessSlider(modifier: Modifier, onNeedPermission: () -> Unit) {
    val ctx = LocalContext.current
    var v by remember {
        mutableFloatStateOf(runCatching { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(0.5f))
    }
    val latest by rememberUpdatedState(v)
    TallSlider(latest, Icons.Rounded.LightMode, modifier) { nv ->
        if (!Settings.System.canWrite(ctx)) { onNeedPermission(); return@TallSlider }
        v = nv
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (nv * 254 + 1).toInt())
    }
}

@Composable
private fun VolumeSlider(modifier: Modifier) {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }
    var v by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC) / max) }
    TallSlider(v, Icons.Rounded.VolumeUp, modifier) { nv ->
        v = nv
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (nv * max).toInt(), 0)
    }
}
