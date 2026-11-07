package com.fahad.liquidglass.lock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.media.AudioManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import com.fahad.liquidglass.control.ControlCenterHost
import com.fahad.liquidglass.Settings as GlassSettings

/**
 * Keeps running in the background and opens the glass lock screen the moment the
 * display turns off, so it is already waiting when the user wakes the phone.
 * Starting an activity from the background needs the "display over other apps"
 * permission; on Android 15+ the app must also own a visible overlay window, so we
 * keep a 1-pixel transparent one.
 */
class LockService : Service() {
    private var anchor: View? = null
    private var control: ControlCenterHost? = null
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (!LockPrefs(c).enabled) return
            // Pick up a changed wallpaper in the background so it is ready the next time.
            scope.launch { runCatching { Depth.syncWithSystem(applicationContext, LockStore(applicationContext)) } }
            // Don't cover the screen during a phone or VoIP call.
            val mode = getSystemService(AudioManager::class.java).mode
            if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION) return
            startActivity(Intent(c, LockActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            ))
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("lock", "شاشة القفل الزجاجية", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, "lock")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("شاشة القفل الزجاجية تعمل")
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    private fun addAnchorOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        val wm = getSystemService(WindowManager::class.java)
        val v = View(this)
        val lp = WindowManager.LayoutParams(
            1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        runCatching { wm.addView(v, lp); anchor = v }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (anchor == null) addAnchorOverlay()
        val wantControl = GlassSettings(this).controlCenter
        if (wantControl) {
            val h = control ?: ControlCenterHost(this).also { control = it }
            h.attach()
            if (intent?.action == ACTION_OPEN_CONTROL) h.openFully()
        } else {
            control?.detach(); control = null
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(screenOff)
        scope.cancel()
        control?.detach()
        anchor?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_OPEN_CONTROL = "com.fahad.liquidglass.OPEN_CONTROL"

        /** Runs while either the lock screen or the control center is switched on. */
        fun sync(c: Context) {
            val i = Intent(c, LockService::class.java)
            if (LockPrefs(c).enabled || GlassSettings(c).controlCenter) c.startForegroundService(i) else c.stopService(i)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED) LockService.sync(c)
    }
}
