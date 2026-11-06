package com.fahad.liquidglass.lock

import android.content.Context

enum class UnlockStyle(val label: String, val hint: String) {
    GENIE("Genie", "تنكمش الشاشة وتنسحب لفوق مثل تصغير النوافذ في macOS"),
    RIPPLE("موجة سائلة", "موجة زجاجية تنطلق من مكان إصبعك وتكشف ما تحتها"),
    ZOOM("تكبير وذوبان", "تكبر الشاشة وتتلاشى مع ضبابية، مثل فتح الآيفون"),
    SLIDE("سحب نابضي", "تتبع إصبعك وتطير لفوق بحركة نابضية"),
    DISSOLVE("ضباب", "تذوب الشاشة في ضبابية ناعمة"),
}

class LockPrefs(context: Context) {
    private val p = context.getSharedPreferences("glass_lock", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = p.getBoolean("enabled", false)
        set(v) = p.edit().putBoolean("enabled", v).apply()

    var style: UnlockStyle
        get() = runCatching { UnlockStyle.valueOf(p.getString("style", null)!!) }.getOrDefault(UnlockStyle.GENIE)
        set(v) = p.edit().putString("style", v.name).apply()

    /** 0.5 = twice as fast, 1.5 = slower. */
    var speed: Float
        get() = p.getFloat("speed", 1f)
        set(v) = p.edit().putFloat("speed", v).apply()

    /** Motion blur strength while things move, 0 = off, 1 = strong. */
    var motionBlur: Float
        get() = p.getFloat("motionBlur", 0.5f)
        set(v) = p.edit().putFloat("motionBlur", v).apply()

    /** 0 = no bounce, 1 = very bouncy. */
    var bounce: Float
        get() = p.getFloat("bounce", 0.5f)
        set(v) = p.edit().putFloat("bounce", v).apply()
}
