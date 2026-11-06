package com.fahad.liquidglass.widget

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.fahad.liquidglass.MainActivity
import com.fahad.liquidglass.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Note: Android launchers do not let a widget blur what is behind it, so widgets use a
 * frosted drawable (translucent fill + top sheen + bright rim) over the real wallpaper.
 */
private val white = ColorProvider(Color.White)
private val soft = ColorProvider(Color.White.copy(alpha = 0.75f))

class GlassClockWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val date = SimpleDateFormat("EEEE، d MMMM", Locale("ar")).format(Date())
        provideContent {
            Column(
                GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.glass_widget_bg))
                    .padding(18.dp).clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AndroidRemoteViews(RemoteViews(context.packageName, R.layout.widget_textclock))
                Text(date, style = TextStyle(color = soft, fontSize = 14.sp))
            }
        }
    }
}

class GlassStatsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val b = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val charging = (b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1) == BatteryManager.BATTERY_STATUS_CHARGING
        provideContent {
            Column(
                GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.glass_widget_bg)).padding(16.dp),
                verticalAlignment = Alignment.Vertical.Bottom,
            ) {
                Text(if (charging) "يشحن ⚡" else "البطارية", style = TextStyle(color = soft, fontSize = 13.sp))
                Text("$level%", style = TextStyle(color = white, fontSize = 40.sp, fontWeight = FontWeight.Medium))
                Spacer(GlanceModifier.height(8.dp))
                LinearProgressIndicator(
                    level / 100f, GlanceModifier.fillMaxWidth().height(6.dp),
                    color = white, backgroundColor = ColorProvider(Color.White.copy(alpha = 0.25f)),
                )
            }
        }
    }
}

class GlassClockReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = GlassClockWidget() }
class GlassStatsReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = GlassStatsWidget() }
