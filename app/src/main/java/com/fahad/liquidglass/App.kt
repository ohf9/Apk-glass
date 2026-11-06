package com.fahad.liquidglass

import android.app.Application
import java.io.File

/** Saves any crash to a file so the app can show it on next launch (no computer needed). */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                File(filesDir, "last_crash.txt").writeText(
                    "Android ${android.os.Build.VERSION.SDK_INT} / ${android.os.Build.MODEL}\n" + e.stackTraceToString()
                )
            }
            prev?.uncaughtException(t, e)
        }
    }
}
