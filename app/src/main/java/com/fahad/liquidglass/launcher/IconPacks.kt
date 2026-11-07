package com.fahad.liquidglass.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class IconPackInfo(val pkg: String, val label: String)

/**
 * Reads installed icon packs (the ADW / Nova / Go launcher standard that almost every pack
 * follows) so any app icon we draw can come from the pack the user picked.
 */
object IconPacks {
    private val ACTIONS = listOf(
        "org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.anddoes.launcher.THEME",
        "com.gau.go.launcherex.theme", "fr.neamar.kiss.THEME", "ch.deletescape.lawnchair.ICONPACK",
    )

    fun installed(ctx: Context): List<IconPackInfo> {
        val pm = ctx.packageManager
        val seen = LinkedHashMap<String, IconPackInfo>()
        for (a in ACTIONS) {
            pm.queryIntentActivities(Intent(a), PackageManager.GET_META_DATA).forEach {
                val pkg = it.activityInfo.packageName
                seen.getOrPut(pkg) { IconPackInfo(pkg, it.loadLabel(pm).toString()) }
            }
        }
        return seen.values.sortedBy { it.label.lowercase() }
    }

    class Pack(private val ctx: Context, private val pkg: String) {
        private val res = ctx.packageManager.getResourcesForApplication(pkg)
        private val map = HashMap<String, String>()   // "pkg/activity" -> drawable name
        private val cache = HashMap<String, Drawable?>()

        init { runCatching { parse() } }

        private fun parse() {
            val parser: XmlPullParser = run {
                val id = res.getIdentifier("appfilter", "xml", pkg)
                if (id != 0) res.getXml(id)
                else XmlPullParserFactory.newInstance().newPullParser().apply {
                    setInput(res.assets.open("appfilter.xml"), null)
                }
            }
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val dr = parser.getAttributeValue(null, "drawable")
                    if (comp != null && dr != null) {
                        // ComponentInfo{com.pkg/com.pkg.Activity}
                        val inner = comp.substringAfter("{", "").substringBefore("}")
                        if (inner.contains("/")) map.putIfAbsent(inner, dr)
                    }
                }
                ev = parser.next()
            }
        }

        /** The pack's icon for this app, or null if the pack does not theme it. */
        fun iconFor(c: ComponentName): Drawable? {
            val key = c.packageName + "/" + c.className
            val name = map[key] ?: map[c.packageName + "/" + c.packageName.let { c.className }] ?: return null
            return cache.getOrPut(name) {
                val id = res.getIdentifier(name, "drawable", pkg).takeIf { it != 0 }
                    ?: res.getIdentifier(name, "mipmap", pkg)
                if (id == 0) null else runCatching { res.getDrawable(id, null) }.getOrNull()
            }
        }
    }

    fun load(ctx: Context, pkg: String?): Pack? =
        pkg?.takeIf { it.isNotBlank() }?.let { runCatching { Pack(ctx, it) }.getOrNull() }
}
