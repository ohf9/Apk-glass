package com.fahad.liquidglass.launcher

import android.content.Context

enum class OpenStyle(val label: String) {
    ICON_ZOOM("تكبير من الأيقونة"), SPRING("ارتداد نابضي"), SLIDE("انزلاق لفوق"), FADE("تلاشي"), REVEAL("كشف دائري"),
}

class LauncherPrefs(context: Context) {
    private val p = context.getSharedPreferences("glass_launcher", Context.MODE_PRIVATE)

    /** The one switch that turns the glass look on/off everywhere in the launcher. */
    var glass: Boolean
        get() = p.getBoolean("glass", true); set(v) = p.edit().putBoolean("glass", v).apply()
    var blur: Float
        get() = p.getFloat("blur", 22f); set(v) = p.edit().putFloat("blur", v).apply()
    var refraction: Float
        get() = p.getFloat("refraction", 14f); set(v) = p.edit().putFloat("refraction", v).apply()
    var openStyle: OpenStyle
        get() = runCatching { OpenStyle.valueOf(p.getString("open", null)!!) }.getOrDefault(OpenStyle.ICON_ZOOM)
        set(v) = p.edit().putString("open", v.name).apply()
    var motionBlur: Float
        get() = p.getFloat("motionBlur", 0.5f); set(v) = p.edit().putFloat("motionBlur", v).apply()
    var bounce: Float
        get() = p.getFloat("bounce", 0.5f); set(v) = p.edit().putFloat("bounce", v).apply()
    var dockMagnify: Float
        get() = p.getFloat("magnify", 0.7f); set(v) = p.edit().putFloat("magnify", v).apply()

    var dock: List<String>
        get() = p.getString("dock", "")!!.split('|').filter { it.isNotBlank() }
        set(v) = p.edit().putString("dock", v.joinToString("|")).apply()
    var widgets: List<Int>
        get() = p.getString("widgets", "")!!.split(',').mapNotNull { it.toIntOrNull() }
        set(v) = p.edit().putString("widgets", v.joinToString(",")).apply()
    var widgetHeights: Map<Int, Int>
        get() = p.getString("wh", "")!!.split(',').mapNotNull {
            val (a, b) = it.split(':').takeIf { s -> s.size == 2 } ?: return@mapNotNull null
            a.toIntOrNull()?.let { id -> b.toIntOrNull()?.let { h -> id to h } }
        }.toMap()
        set(v) = p.edit().putString("wh", v.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()
}
