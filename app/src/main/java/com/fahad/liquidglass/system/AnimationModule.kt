package com.fahad.liquidglass.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.fahad.liquidglass.editor.Chips
import com.fahad.liquidglass.editor.GlassSlider
import com.fahad.liquidglass.editor.Labeled
import com.fahad.liquidglass.editor.Pill
import com.fahad.liquidglass.glass.GlassPanel

@Composable
fun AnimationModule() {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(SystemAnimations.hasPermission(ctx)) }
    LifecycleResumeEffect(Unit) { granted = SystemAnimations.hasPermission(ctx); onPauseOrDispose { } }

    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("أنيميشن النظام", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "يغيّر سرعة حركات الجهاز كله: فتح وإغلاق التطبيقات، الانتقالات، والحركات داخل التطبيقات وسامسونج هوم.",
                color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp,
            )
            if (!granted) Setup(ctx) else Controls(ctx)
            Text(
                "حدود: هذا يغيّر السرعة والمدة فقط. شكل الحركة وضبابية الحركة داخل النظام تحتاج روت.",
                color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun Setup(ctx: Context) {
    Text("الإذن غير مفعّل. تفعيله مرة وحدة:", color = Color(0xFFFFB74D), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    Text(
        "١. الإعدادات > حول الهاتف > معلومات البرنامج > اضغط رقم البناء ٧ مرات\n" +
        "٢. خيارات المطور > فعّل \"التصحيح اللاسلكي\" (وفعّل أيضاً \"تعطيل مهلة ADB\")\n" +
        "٣. من كمبيوتر متصل بنفس الواي فاي: اتصل بالجهاز وشغّل هذا الأمر\n" +
        "   أو من الجوال نفسه بتطبيق مثل Shizuku أو LADB",
        color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp,
    )
    GlassPanel(Modifier.fillMaxWidth(), cornerRadius = 14.dp, contentPadding = PaddingValues(10.dp)) {
        Text(SystemAnimations.GRANT_COMMAND, color = Color(0xFF6CF0C2), fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("نسخ الأمر", strong = true, glass = false) {
            ctx.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("cmd", SystemAnimations.GRANT_COMMAND))
        }
        Pill("خيارات المطور", glass = false) {
            runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}

@Composable
private fun Controls(ctx: Context) {
    var window by remember { mutableFloatStateOf(SystemAnimations.read(ctx, SystemAnimations.WINDOW)) }
    var transition by remember { mutableFloatStateOf(SystemAnimations.read(ctx, SystemAnimations.TRANSITION)) }
    var animator by remember { mutableFloatStateOf(SystemAnimations.read(ctx, SystemAnimations.ANIMATOR)) }
    val sel = SystemAnimations.presets.firstOrNull { it.window == window && it.transition == transition && it.animator == animator }

    Text("✓ الإذن مفعّل، والتغييرات تنطبق فوراً", color = Color(0xFF6CF0C2), fontSize = 13.sp)
    Chips(SystemAnimations.presets, sel ?: SystemAnimations.presets[3], { it.label }) {
        SystemAnimations.apply(ctx, it); window = it.window; transition = it.transition; animator = it.animator
    }
    Labeled("فتح وإغلاق التطبيقات والنوافذ (${fmt(window)})") {
        GlassSlider(window, 0f..3f) { window = round(it); SystemAnimations.write(ctx, SystemAnimations.WINDOW, window) }
    }
    Labeled("الانتقالات بين الشاشات (${fmt(transition)})") {
        GlassSlider(transition, 0f..3f) { transition = round(it); SystemAnimations.write(ctx, SystemAnimations.TRANSITION, transition) }
    }
    Labeled("الحركات داخل التطبيقات (${fmt(animator)})") {
        GlassSlider(animator, 0f..3f) { animator = round(it); SystemAnimations.write(ctx, SystemAnimations.ANIMATOR, animator) }
    }
    Text("الرقم أقل = أسرع، أكثر = أبطأ. جرّب افتح تطبيق وارجع هنا.", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
}

private fun round(v: Float) = (v * 20).toInt() / 20f
private fun fmt(v: Float) = if (v == 0f) "مطفأة" else "×" + String.format(java.util.Locale.ENGLISH, "%.2f", v).trimEnd('0').trimEnd('.')
