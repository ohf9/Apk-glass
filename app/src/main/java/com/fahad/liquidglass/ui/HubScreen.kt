package com.fahad.liquidglass.ui

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fahad.liquidglass.editor.EditorActivity
import com.fahad.liquidglass.editor.Pill
import com.fahad.liquidglass.glass.GlassPanel
import com.fahad.liquidglass.glass.GlassRoot
import com.fahad.liquidglass.glass.LiquidBackdrop
import com.fahad.liquidglass.lock.LockActivity
import com.fahad.liquidglass.lock.LockPrefs
import com.fahad.liquidglass.lock.LockService

@SuppressLint("BatteryLife")
@Composable
fun HubScreen(onOpenDemo: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { LockPrefs(ctx) }
    var enabled by remember { mutableStateOf(prefs.enabled) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun setEnabled(on: Boolean) {
        enabled = on; prefs.enabled = on
        if (on) {
            if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            if (!Settings.canDrawOverlays(ctx)) ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            )
        }
        LockService.sync(ctx)
    }

    GlassRoot(backdrop = { LiquidBackdrop() }) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).systemBarsPadding().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Liquid Glass", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            GlassPanel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("شاشة القفل الزجاجية", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("تطلع فوق قفل سامسونج، والبصمة تبقى شغالة", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                    }
                    Switch(enabled, ::setEnabled)
                }
            }
            Pill("تعديل شاشة القفل", strong = true, modifier = Modifier.fillMaxWidth()) {
                ctx.startActivity(Intent(ctx, EditorActivity::class.java))
            }
            Pill("معاينة شاشة القفل", modifier = Modifier.fillMaxWidth()) {
                ctx.startActivity(Intent(ctx, LockActivity::class.java).putExtra(LockActivity.EXTRA_PREVIEW, true))
            }
            val pm = ctx.getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
                Pill("استثناء من توفير البطارية (ضروري في سامسونج)", modifier = Modifier.fillMaxWidth()) {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                }
            }
            Pill("تجربة الواجهة الزجاجية", modifier = Modifier.fillMaxWidth()) { onOpenDemo() }
            GlassPanel(Modifier.fillMaxWidth()) {
                Text(
                    "الويجيتز: اضغط مطولاً على الشاشة الرئيسية > ويجيتز > Liquid Glass",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp,
                )
            }
        }
    }
}
