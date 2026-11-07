package com.fahad.liquidglass

import android.content.Context

/** Global settings shared by every module (the Good Lock style "master" switches). */
class Settings(context: Context) {
    private val p = context.getSharedPreferences("glass_global", Context.MODE_PRIVATE)
    /** The one button: glass look on/off for every module. */
    var glass: Boolean
        get() = p.getBoolean("glass", true); set(v) = p.edit().putBoolean("glass", v).apply()
    /** Package name of the chosen icon pack ("" = the apps' own icons). */
    var iconPack: String
        get() = p.getString("iconPack", "")!!; set(v) = p.edit().putString("iconPack", v).apply()
    /** Edge-swipe glass control center on/off. */
    var controlCenter: Boolean
        get() = p.getBoolean("controlCenter", false); set(v) = p.edit().putBoolean("controlCenter", v).apply()
    /** Blur strength (px) applied by the system behind overlays such as the control center. */
    var systemBlur: Float
        get() = p.getFloat("systemBlur", 90f); set(v) = p.edit().putFloat("systemBlur", v).apply()
    var motionBlur: Float
        get() = p.getFloat("motionBlur", 0.5f); set(v) = p.edit().putFloat("motionBlur", v).apply()
    var bounce: Float
        get() = p.getFloat("bounce", 0.5f); set(v) = p.edit().putFloat("bounce", v).apply()
}
