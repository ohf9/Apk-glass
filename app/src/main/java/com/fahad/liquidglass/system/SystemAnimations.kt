package com.fahad.liquidglass.system

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Real system-wide animation control. Android keeps three global speed multipliers that every
 * app and the launcher obey. Writing them needs WRITE_SECURE_SETTINGS, which a normal app
 * cannot get from the permission dialog: it is granted once with `pm grant` (ADB / Shizuku).
 */
object SystemAnimations {
    const val PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"

    /** Window open/close, app opening and closing, dialogs. */
    const val WINDOW = Settings.Global.WINDOW_ANIMATION_SCALE
    /** Transitions between screens/activities. */
    const val TRANSITION = Settings.Global.TRANSITION_ANIMATION_SCALE
    /** Everything animated inside apps (ValueAnimator): scrolling effects, ripples, springs. */
    const val ANIMATOR = Settings.Global.ANIMATOR_DURATION_SCALE

    fun hasPermission(c: Context) = c.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Scale as a duration multiplier: 1.0 = normal, 0.5 = twice as fast, 2.0 = twice as slow, 0 = off. */
    fun read(c: Context, key: String): Float = Settings.Global.getFloat(c.contentResolver, key, 1f)

    fun write(c: Context, key: String, value: Float): Boolean = runCatching {
        Settings.Global.putFloat(c.contentResolver, key, value.coerceIn(0f, 10f))
    }.getOrDefault(false)

    data class Preset(val label: String, val window: Float, val transition: Float, val animator: Float)

    val presets = listOf(
        Preset("بدون", 0f, 0f, 0f),
        Preset("سريع جداً", 0.4f, 0.4f, 0.4f),
        Preset("سريع", 0.7f, 0.7f, 0.7f),
        Preset("عادي", 1f, 1f, 1f),
        Preset("ناعم", 1.4f, 1.4f, 1.2f),
        Preset("سينمائي", 2f, 2f, 1.5f),
    )

    fun apply(c: Context, p: Preset) {
        write(c, WINDOW, p.window); write(c, TRANSITION, p.transition); write(c, ANIMATOR, p.animator)
    }

    const val GRANT_COMMAND = "adb shell pm grant com.fahad.liquidglass android.permission.WRITE_SECURE_SETTINGS"
}
