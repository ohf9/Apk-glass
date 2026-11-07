package com.fahad.liquidglass.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.fahad.liquidglass.control.GlassAccessibilityService
import com.fahad.liquidglass.control.GlassTileService
import com.fahad.liquidglass.editor.Chips
import com.fahad.liquidglass.launcher.IconPackInfo
import com.fahad.liquidglass.launcher.IconPacks
import com.fahad.liquidglass.lock.Depth
import com.fahad.liquidglass.system.AnimationModule
import com.fahad.liquidglass.editor.EditorActivity
import com.fahad.liquidglass.editor.GlassSlider
import com.fahad.liquidglass.editor.Labeled
import com.fahad.liquidglass.editor.Pill
import com.fahad.liquidglass.editor.Toggle
import com.fahad.liquidglass.glass.GlassPanel
import com.fahad.liquidglass.glass.GlassRoot
import com.fahad.liquidglass.glass.GlassStyle
import com.fahad.liquidglass.glass.LiquidBackdrop
import com.fahad.liquidglass.glass.LocalGlassStyle
import com.fahad.liquidglass.lock.LockActivity
import com.fahad.liquidglass.lock.LockPrefs
import com.fahad.liquidglass.lock.LockService
import com.fahad.liquidglass.Settings as GlassSettings

/** Good Lock style hub: one master glass switch, then a card per module. */
@SuppressLint("BatteryLife")
@Composable
fun HubScreen(onOpenDemo: () -> Unit) {
    val ctx = LocalContext.current
    val g = remember { GlassSettings(ctx) }
    val lockPrefs = remember { LockPrefs(ctx) }
    var glass by remember { mutableStateOf(g.glass) }
    var sysBlur by remember { mutableFloatStateOf(g.systemBlur) }
    var motion by remember { mutableFloatStateOf(g.motionBlur) }
    var bounce by remember { mutableFloatStateOf(g.bounce) }
    var lockOn by remember { mutableStateOf(lockPrefs.enabled) }
    var a11yOn by remember { mutableStateOf(false) }
    var batteryOk by remember { mutableStateOf(true) }
    var overlayOk by remember { mutableStateOf(true) }
    var filesOk by remember { mutableStateOf(true) }
    var controlOn by remember { mutableStateOf(g.controlCenter) }
    var pack by remember { mutableStateOf(g.iconPack) }
    val packs = remember { IconPacks.installed(ctx) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LifecycleResumeEffect(Unit) {
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        a11yOn = enabled.contains(ComponentName(ctx, GlassAccessibilityService::class.java).flattenToString())
        batteryOk = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
        overlayOk = Settings.canDrawOverlays(ctx)
        filesOk = Depth.canReadWallpaper(ctx)
        LockService.sync(ctx)
        onPauseOrDispose { }
    }

    fun setControl(on: Boolean) {
        controlOn = on; g.controlCenter = on
        if (on) {
            if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            if (!Settings.canDrawOverlays(ctx)) ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            )
        }
        LockService.sync(ctx)
    }

    fun setLock(on: Boolean) {
        lockOn = on; lockPrefs.enabled = on
        if (on) {
            if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            if (!Settings.canDrawOverlays(ctx)) ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            )
        }
        LockService.sync(ctx)
    }

    CompositionLocalProvider(LocalGlassStyle provides GlassStyle(enabled = glass)) {
        GlassRoot(backdrop = { LiquidBackdrop() }) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).systemBarsPadding().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Liquid Glass", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)

                // Master switch
                GlassPanel(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Toggle("التأثير الزجاجي على كل الوحدات", glass) { glass = it; g.glass = it }
                        Labeled("قوة ضبابية النظام خلف الزجاج") { GlassSlider(sysBlur, 0f..150f) { sysBlur = it; g.systemBlur = it } }
                        Labeled("ضبابية الحركة") { GlassSlider(motion, 0f..1f) { motion = it; g.motionBlur = it } }
                        Labeled("الارتداد النابضي") { GlassSlider(bounce, 0f..1f) { bounce = it; g.bounce = it } }
                    }
                }

                Module(Icons.Rounded.Lock, "شاشة القفل", "شاشة قفل زجاجية تخصصها بنفسك، مع العمق وأنيميشن الفتح", lockOn, ::setLock) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("تخصيص", strong = true, glass = false) { ctx.startActivity(Intent(ctx, EditorActivity::class.java)) }
                        Pill("معاينة", glass = false) {
                            ctx.startActivity(Intent(ctx, LockActivity::class.java).putExtra(LockActivity.EXTRA_PREVIEW, true))
                        }
                    }
                }

                Module(Icons.Rounded.Tune, "مركز التحكم", "اسحب من أعلى يمين الشاشة لتحت فوق أي تطبيق، أو من زر في الإعدادات السريعة", controlOn, ::setControl) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (Build.VERSION.SDK_INT >= 33) Pill("أضف زر للإعدادات السريعة", glass = false) {
                            val sbm = ctx.getSystemService(android.app.StatusBarManager::class.java)
                            sbm.requestAddTileService(
                                ComponentName(ctx, GlassTileService::class.java), "تحكم زجاجي",
                                android.graphics.drawable.Icon.createWithResource(ctx, android.R.drawable.ic_menu_manage),
                                ctx.mainExecutor,
                            ) { }
                        }
                    }
                }

                AnimationModule()

                // Icon pack
                GlassPanel(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("باك الأيقونات", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "الأيقونات اللي أرسمها أنا (اختصارات شاشة القفل) تاخذ من الباك. أيقونات الشاشة الرئيسية يرسمها One UI Home من إعدادات الثيمات.",
                            color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp,
                        )
                        if (packs.isEmpty()) Text("ما لقيت باك أيقونات مثبت", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                        Chips(listOf(IconPackInfo("", "الافتراضي")) + packs, (packs.firstOrNull { it.pkg == pack } ?: IconPackInfo("", "الافتراضي")),
                            { it.label }) { pack = it.pkg; g.iconPack = it.pkg }
                    }
                }

                Module(Icons.Rounded.Widgets, "الويجيتز", "اضغط مطولاً على الشاشة الرئيسية > ويجيتز > Liquid Glass", null, null) { }

                SetupGuide(overlayOk, filesOk, batteryOk, a11yOn)
                if (false) Pill("استثناء من توفير البطارية (ضروري في سامسونج)", modifier = Modifier.fillMaxWidth()) {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                }
                Pill("تجربة الواجهة الزجاجية", modifier = Modifier.fillMaxWidth()) { onOpenDemo() }
            }
        }
    }
}

@Composable
private fun SetupGuide(overlayOk: Boolean, filesOk: Boolean, batteryOk: Boolean, a11yOn: Boolean) {
    val ctx = LocalContext.current
    fun go(i: Intent) = ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    val appInfo = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("الأذونات", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            PermRow("العرض فوق التطبيقات", "ضروري لمركز التحكم وشاشة القفل", overlayOk) {
                go(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
            }
            PermRow("الوصول لكل الملفات", "عشان أقرأ خلفية جوالك لشاشة القفل", filesOk) {
                go(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")))
            }
            PermRow("استثناء من توفير البطارية", "عشان سامسونج ما توقف الخدمة", batteryOk) {
                go(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
            }
            PermRow("تسهيل الاستخدام (اختياري)", "يضيف زرّي لقطة الشاشة والقفل فقط", a11yOn) {
                go(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            Text(
                "لو الإذن رمادي أو يقول \"إعداد مقيّد\": افتح معلومات التطبيق ثم اضغط ⋮ فوق يمين ثم \"السماح بالإعدادات المقيدة\"، وارجع للإذن. هذا قفل أندرويد 13+ للتطبيقات المثبتة من خارج المتجر.",
                color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp,
            )
            Pill("فتح معلومات التطبيق", glass = false) { go(appInfo) }
        }
    }
}

@Composable
private fun PermRow(title: String, why: String, ok: Boolean, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, null,
            tint = if (ok) Color(0xFF6CF0C2) else Color(0xFFFFB74D))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp)
            Text(why, color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
        }
        if (!ok) Pill("منح", glass = false, strong = true) { onFix() }
    }
}

@Composable
private fun Module(
    icon: ImageVector, title: String, desc: String, on: Boolean?, onToggle: ((Boolean) -> Unit)?,
    extra: @Composable () -> Unit,
) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(desc, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                }
                if (on != null && onToggle != null) androidx.compose.material3.Switch(on, onToggle)
            }
            extra()
        }
    }
}
