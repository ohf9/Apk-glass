package com.fahad.liquidglass.control

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import com.fahad.liquidglass.lock.LockService

/** "Glass Control" tile in the quick settings panel: opens the glass control center. */
class GlassTileService : TileService() {
    override fun onClick() {
        val i = Intent(this, OpenControlCenterActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION") startActivityAndCollapse(i)
        }
    }
}

class OpenControlCenterActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startForegroundService(Intent(this, LockService::class.java).setAction(LockService.ACTION_OPEN_CONTROL))
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }
}
