package com.fahad.liquidglass.launcher

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Process
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.fahad.liquidglass.R

data class AppEntry(val key: String, val label: String, val component: ComponentName, val icon: ImageBitmap)

object Apps {
    fun load(context: Context): List<AppEntry> {
        val la = context.getSystemService(LauncherApps::class.java)
        val dpi = context.resources.displayMetrics.densityDpi
        val pack = IconPacks.load(context, com.fahad.liquidglass.Settings(context).iconPack)
        return la.getActivityList(null, Process.myUserHandle())
            .filter { it.componentName.packageName != context.packageName }
            .map {
                AppEntry(
                    key = it.componentName.flattenToString(),
                    label = it.label.toString(),
                    component = it.componentName,
                    icon = (pack?.iconFor(it.componentName) ?: it.getBadgedIcon(dpi)).toBitmap(144, 144).asImageBitmap(),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Launch with the chosen opening animation. [bounds] is the icon's on-screen rect, so the
     * app grows out of the icon the user tapped.
     */
    fun launch(context: Context, source: android.view.View, app: AppEntry, bounds: Rect, style: OpenStyle) {
        val opts = when (style) {
            OpenStyle.ICON_ZOOM -> ActivityOptions.makeScaleUpAnimation(source, bounds.left, bounds.top, bounds.width(), bounds.height())
            OpenStyle.REVEAL -> ActivityOptions.makeClipRevealAnimation(source, bounds.left, bounds.top, bounds.width(), bounds.height())
            OpenStyle.SPRING -> ActivityOptions.makeCustomAnimation(context, R.anim.open_spring, R.anim.home_exit_zoom)
            OpenStyle.SLIDE -> ActivityOptions.makeCustomAnimation(context, R.anim.open_slide_up, R.anim.home_exit_zoom)
            OpenStyle.FADE -> ActivityOptions.makeCustomAnimation(context, R.anim.open_fade, R.anim.home_exit_zoom)
        }
        context.getSystemService(LauncherApps::class.java)
            .startMainActivity(app.component, Process.myUserHandle(), bounds, opts.toBundle())
    }
}
