package com.fahad.liquidglass.editor

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fahad.liquidglass.glass.GlassPanel
import com.fahad.liquidglass.lock.*
import kotlinx.coroutines.launch

private enum class Tab(val label: String) { WALL("الخلفية"), ADD("إضافة"), MOTION("الحركة") }

private val Swatches = listOf(
    0xFFFFFFFF, 0xFF0B0B0F, 0xFFFFD54F, 0xFFFF6B9A, 0xFF7FB2FF, 0xFF6CF0C2, 0xFFB794FF, 0xFFFF8A50,
)

@Composable
fun EditorScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { LockStore(ctx) }
    val prefs = remember { LockPrefs(ctx) }
    var layout by remember { mutableStateOf(store.load()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf<Tab?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // First time: build on the wallpaper the phone already has (no picking needed).
    LaunchedEffect(Unit) {
        if (layout.followSystem && !layout.hasWallpaper) {
            busy = "جاري قراءة خلفيتك الحالية…"
            layout = Depth.syncWithSystem(ctx, store); busy = null
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = "جاري قص الشخص من الصورة…"
        scope.launch {
            val found = runCatching { Depth.importWallpaper(ctx, uri, store) }.getOrDefault(false)
            layout = layout.copy(hasWallpaper = true, hasSubject = found, followSystem = false, version = System.currentTimeMillis())
            store.save(layout)
            busy = if (found) null else "ما لقيت شخص أو عنصر واضح في الصورة، تأثير العمق بيكون مطفي لها"
        }
    }

    fun update(e: LockElement) { layout = layout.copy(elements = layout.elements.map { if (it.id == e.id) e else it }) }
    val sel = layout.elements.firstOrNull { it.id == selected }

    LockCanvas(
        layout = layout, editing = true, selectedId = selected,
        onSelect = { selected = it; if (it != null) tab = null },
        onElementChange = ::update,
    ) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Pill("إلغاء") { onClose() }
            Pill("حفظ", strong = true) { store.save(layout); onClose() }
        }
        busy?.let {
            GlassPanel(Modifier.align(Alignment.Center).padding(24.dp)) {
                Text(it, color = Color.White, modifier = Modifier.clickable { busy = null })
            }
        }
        // Bottom sheet
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AnimatedContent(
                targetState = sel?.id ?: tab?.name,
                transitionSpec = {
                    (slideInVertically(spring(0.8f, Spring.StiffnessMediumLow)) { it / 3 } + fadeIn() + scaleIn(initialScale = 0.96f))
                        .togetherWith(slideOutVertically { it / 3 } + fadeOut())
                }, label = "sheet",
            ) { key ->
                when {
                    key == null -> Spacer(Modifier.height(0.dp))
                    sel != null && key == sel.id -> ElementSheet(sel, ::update,
                        onDelete = { layout = layout.copy(elements = layout.elements - sel); selected = null },
                        onDone = { selected = null })
                    key == Tab.WALL.name -> WallSheet(layout, { layout = it },
                        onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onUseSystem = {
                            scope.launch {
                                busy = "جاري قراءة خلفيتك الحالية…"
                                layout = layout.copy(followSystem = true, systemWallId = -1)
                                store.save(layout)
                                if (!Depth.canReadWallpaper(ctx)) {
                                    busy = "لازم تسمح بالوصول لكل الملفات عشان أقرأ خلفية جوالك، بيفتح لك الإعداد"
                                    ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        android.net.Uri.parse("package:${ctx.packageName}")))
                                } else {
                                    layout = Depth.syncWithSystem(ctx, store)
                                    busy = if (layout.hasWallpaper) null else "ما قدرت أقرأ الخلفية (بعض خلفيات سامسونج الديناميكية ما تنقرأ). اختر صورة من المعرض"
                                }
                            }
                        })
                    key == Tab.ADD.name -> AddSheet { type ->
                        val e = when (type) {
                            ElementType.CLOCK -> LockElement(type = type, size = 72f, weight = 300)
                            ElementType.TEXT -> LockElement(type = type, size = 22f, text = "")
                            else -> LockElement(type = type)
                        }
                        layout = layout.copy(elements = layout.elements + e); selected = e.id; tab = null
                    }
                    key == Tab.MOTION.name -> MotionSheet(prefs) {
                        store.save(layout)
                        ctx.startActivity(Intent(ctx, LockActivity::class.java).putExtra(LockActivity.EXTRA_PREVIEW, true))
                    }
                    else -> Spacer(Modifier.height(0.dp))
                }
            }
            GlassPanel(Modifier.fillMaxWidth(), cornerRadius = 30.dp, contentPadding = PaddingValues(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Tab.entries.forEach { t ->
                        Pill(t.label, strong = tab == t, glass = false) { selected = null; tab = if (tab == t) null else t }
                    }
                }
            }
        }
    }
}

@Composable
private fun ElementSheet(e: LockElement, onChange: (LockElement) -> Unit, onDelete: () -> Unit, onDone: () -> Unit) {
    Sheet {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(e.type.label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("حذف", glass = false, tint = Color(0x55FF5A5A)) { onDelete() }
                Pill("تم", strong = true, glass = false) { onDone() }
            }
        }
        if (e.type == ElementType.TEXT) {
            OutlinedTextField(
                e.text, { onChange(e.copy(text = it)) }, Modifier.fillMaxWidth(), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White, unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                ),
            )
        }
        if (e.type == ElementType.CLOCK) {
            Text("شكل الساعة", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            Chips(ClockStyle.entries, e.clockStyle, { it.label }) { onChange(e.copy(clockStyle = it)) }
            if (e.clockStyle != ClockStyle.ANALOG && e.clockStyle != ClockStyle.DOTS)
                Chips(ClockFormat.entries, e.clockFormat, { it.label }) { onChange(e.copy(clockFormat = it)) }
        }
        Chips(FontChoice.entries, e.font, { it.label }) { onChange(e.copy(font = it)) }
        Labeled("الحجم") { GlassSlider(e.size, 8f..220f) { onChange(e.copy(size = it)) } }
        Labeled("السماكة") { GlassSlider(e.weight.toFloat(), 100f..900f, steps = 7) { onChange(e.copy(weight = it.toInt())) } }
        Labeled("الدوران") { GlassSlider(e.rotation, -45f..45f) { onChange(e.copy(rotation = it)) } }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Swatches.forEach { c ->
                Box(Modifier.size(30.dp).clip(CircleShape).background(Color(c))
                    .border(if (e.color == c) 3.dp else 1.dp, Color.White.copy(alpha = if (e.color == c) 1f else 0.4f), CircleShape)
                    .clickable { onChange(e.copy(color = c)) })
            }
        }
        Toggle("خلف الشخص (تأثير العمق)", e.behindSubject) { onChange(e.copy(behindSubject = it)) }
        Toggle("خلفية زجاجية", e.glass) { onChange(e.copy(glass = it)) }
    }
}

@Composable
private fun WallSheet(l: LockLayout, onChange: (LockLayout) -> Unit, onPick: () -> Unit, onUseSystem: () -> Unit) {
    Sheet {
        Pill(if (l.followSystem) "✓ تستخدم خلفية جوالك الحالية (تحديث)" else "استخدام خلفية جوالك الحالية",
            strong = l.followSystem, glass = false, modifier = Modifier.fillMaxWidth()) { onUseSystem() }
        Pill("اختيار صورة ثانية من المعرض", glass = false, modifier = Modifier.fillMaxWidth()) { onPick() }
        Toggle(
            if (l.hasSubject) "تأثير العمق" else "تأثير العمق (اختر صورة فيها شخص أو عنصر واضح)",
            l.depth && l.hasSubject,
        ) { onChange(l.copy(depth = it)) }
        Labeled("ضبابية الخلفية") { GlassSlider(l.wallBlur, 0f..40f) { onChange(l.copy(wallBlur = it)) } }
        Labeled("التعتيم") { GlassSlider(l.dim, 0f..0.7f) { onChange(l.copy(dim = it)) } }
        Labeled("حركة الإمالة (بارالاكس)") { GlassSlider(l.parallax, 0f..1f) { onChange(l.copy(parallax = it)) } }
    }
}

@Composable
private fun AddSheet(onAdd: (ElementType) -> Unit) {
    Sheet {
        Text("أضف عنصر لشاشة القفل", color = Color.White, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ElementType.entries.forEach { t -> Pill(t.label, glass = false) { onAdd(t) } }
        }
    }
}

@Composable
private fun MotionSheet(prefs: LockPrefs, onPreview: () -> Unit) {
    var style by remember { mutableStateOf(prefs.style) }
    var speed by remember { mutableFloatStateOf(prefs.speed) }
    var bounce by remember { mutableFloatStateOf(prefs.bounce) }
    var blur by remember { mutableFloatStateOf(prefs.motionBlur) }
    Sheet {
        Text("أنيميشن فتح القفل", color = Color.White, fontWeight = FontWeight.SemiBold)
        Chips(UnlockStyle.entries, style, { it.label }) { style = it; prefs.style = it }
        Text(style.hint, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
        Labeled("المدة (يسار أسرع)") { GlassSlider(speed, 0.4f..1.8f) { speed = it; prefs.speed = it } }
        Labeled("الارتداد النابضي") { GlassSlider(bounce, 0f..1f) { bounce = it; prefs.bounce = it } }
        Labeled("ضبابية الحركة") { GlassSlider(blur, 0f..1f) { blur = it; prefs.motionBlur = it } }
        Pill("معاينة", strong = true, glass = false, modifier = Modifier.fillMaxWidth()) { onPreview() }
    }
}

// ---------- small glass building blocks ----------

@Composable
private fun Sheet(content: @Composable ColumnScope.() -> Unit) {
    GlassPanel(Modifier.fillMaxWidth().heightIn(max = 420.dp), cornerRadius = 30.dp, contentPadding = PaddingValues(18.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** Squishy button: shrinks with a spring while pressed, like iOS. */
@Composable
fun Pill(
    text: String, strong: Boolean = false, glass: Boolean = true, tint: Color? = null,
    modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.9f else 1f, spring(0.45f, Spring.StiffnessMedium), label = "press")
    val bg = tint ?: if (strong) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.10f)
    val m = modifier.graphicsLayer { scaleX = s; scaleY = s }.clickable(src, null, onClick = onClick)
    if (glass) GlassPanel(m, cornerRadius = 22.dp, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp), tint = if (strong) Color(0xFFBFD8FF) else Color.White) {
        Text(text, color = Color.White, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal)
    } else Box(m.clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 16.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
fun <T> Chips(items: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { Pill(label(it), strong = it == selected, glass = false) { onPick(it) } }
    }
}

@Composable
fun Labeled(label: String, content: @Composable () -> Unit) {
    Column { Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp); content() }
}

@Composable
fun GlassSlider(v: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, onChange: (Float) -> Unit) {
    Slider(v.coerceIn(range), onChange, valueRange = range, steps = steps, colors = SliderDefaults.colors(
        thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.25f),
        activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
    ))
}

@Composable
fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        Switch(checked, onChange, colors = SwitchDefaults.colors(
            checkedTrackColor = Color(0xFF6CF0C2).copy(alpha = 0.7f), uncheckedTrackColor = Color.White.copy(alpha = 0.15f),
        ))
    }
}
