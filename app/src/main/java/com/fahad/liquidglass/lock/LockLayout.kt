package com.fahad.liquidglass.lock

import android.content.Context
import androidx.compose.ui.text.font.FontFamily
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class ElementType(val label: String) { CLOCK("ساعة"), DATE("تاريخ"), BATTERY("بطارية"), TEXT("نص") }

enum class FontChoice(val label: String, val family: FontFamily) {
    DEFAULT("افتراضي", FontFamily.Default),
    SERIF("كلاسيكي", FontFamily.Serif),
    MONO("رقمي", FontFamily.Monospace),
    CURSIVE("مزخرف", FontFamily.Cursive),
}

enum class ClockFormat(val label: String, val pattern: String) {
    H24("24 ساعة", "HH:mm"), H12("12 ساعة", "h:mm"), STACKED("عمودي", "HH\nmm"), SECONDS("بالثواني", "HH:mm:ss"),
}

/** One draggable thing on the lock screen. x/y are the element's centre as a fraction of the screen. */
data class LockElement(
    val id: String = UUID.randomUUID().toString(),
    val type: ElementType,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val size: Float = 18f,          // sp
    val rotation: Float = 0f,
    val color: Long = 0xFFFFFFFF,
    val font: FontChoice = FontChoice.DEFAULT,
    val weight: Int = 400,
    val glass: Boolean = false,     // frosted panel behind the element
    val behindSubject: Boolean = false, // depth effect: drawn under the cut-out subject
    val clockFormat: ClockFormat = ClockFormat.H24,
    val text: String = "",
)

data class LockLayout(
    val elements: List<LockElement>,
    val hasWallpaper: Boolean = false,
    val hasSubject: Boolean = false,
    val depth: Boolean = true,
    val wallBlur: Float = 0f,   // dp
    val dim: Float = 0.15f,     // 0..1
    val parallax: Float = 0.6f, // 0..1
    val version: Long = 0,      // bumps when the wallpaper image changes
) {
    companion object {
        fun default() = LockLayout(listOf(
            LockElement(type = ElementType.DATE, y = 0.12f, size = 18f, weight = 500),
            LockElement(type = ElementType.CLOCK, y = 0.21f, size = 96f, weight = 300, behindSubject = true),
            LockElement(type = ElementType.BATTERY, y = 0.86f, size = 15f, glass = true),
        ))
    }
}

class LockStore(private val context: Context) {
    private val file get() = File(context.filesDir, "lock_layout.json")
    val wallpaperFile get() = File(context.filesDir, "wallpaper.jpg")
    val subjectFile get() = File(context.filesDir, "subject.png")

    fun load(): LockLayout = runCatching { fromJson(JSONObject(file.readText())) }.getOrElse { LockLayout.default() }

    fun save(l: LockLayout) {
        val tmp = File(context.filesDir, "lock_layout.tmp")
        tmp.writeText(toJson(l).toString())
        tmp.renameTo(file)
    }

    private fun toJson(l: LockLayout) = JSONObject().apply {
        put("hasWallpaper", l.hasWallpaper); put("hasSubject", l.hasSubject); put("depth", l.depth)
        put("wallBlur", l.wallBlur.toDouble()); put("dim", l.dim.toDouble()); put("parallax", l.parallax.toDouble())
        put("version", l.version)
        put("elements", JSONArray().apply {
            l.elements.forEach { e -> put(JSONObject().apply {
                put("id", e.id); put("type", e.type.name); put("x", e.x.toDouble()); put("y", e.y.toDouble())
                put("size", e.size.toDouble()); put("rotation", e.rotation.toDouble()); put("color", e.color)
                put("font", e.font.name); put("weight", e.weight); put("glass", e.glass)
                put("behindSubject", e.behindSubject); put("clockFormat", e.clockFormat.name); put("text", e.text)
            }) }
        })
    }

    private fun fromJson(o: JSONObject): LockLayout {
        val arr = o.getJSONArray("elements")
        val els = (0 until arr.length()).map { i ->
            val e = arr.getJSONObject(i)
            LockElement(
                id = e.getString("id"), type = ElementType.valueOf(e.getString("type")),
                x = e.getDouble("x").toFloat(), y = e.getDouble("y").toFloat(),
                size = e.getDouble("size").toFloat(), rotation = e.optDouble("rotation", 0.0).toFloat(),
                color = e.getLong("color"), font = enumOr(e.optString("font"), FontChoice.DEFAULT),
                weight = e.optInt("weight", 400), glass = e.optBoolean("glass"),
                behindSubject = e.optBoolean("behindSubject"),
                clockFormat = enumOr(e.optString("clockFormat"), ClockFormat.H24), text = e.optString("text"),
            )
        }
        return LockLayout(
            els, o.optBoolean("hasWallpaper"), o.optBoolean("hasSubject"), o.optBoolean("depth", true),
            o.optDouble("wallBlur", 0.0).toFloat(), o.optDouble("dim", 0.15).toFloat(),
            o.optDouble("parallax", 0.6).toFloat(), o.optLong("version"),
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String, d: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(d)
}
