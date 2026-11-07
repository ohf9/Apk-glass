package com.fahad.liquidglass.lock

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Shown on top of the system keyguard. The real lock (fingerprint/face/PIN) still
 * protects the phone: we only play our animation once Android says it is unlocked.
 */
class LockActivity : ComponentActivity() {
    private val unlocked = mutableStateOf(false)
    private val preview by lazy { intent.getBooleanExtra(EXTRA_PREVIEW, false) }

    // Fires when the user unlocks with fingerprint/face while our screen is showing.
    private val userPresent = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { unlocked.value = true }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        registerReceiver(userPresent, IntentFilter(Intent.ACTION_USER_PRESENT))
        val prefs = LockPrefs(this)
        setContent {
          androidx.compose.runtime.CompositionLocalProvider(LocalAppLauncher provides ::openAfterUnlock) {
            LockScreen(
                prefs = prefs,
                unlocked = unlocked.value,
                onSwipeCommitted = ::requestUnlock,
                onAnimationFinished = {
                    finish()
                    @Suppress("DEPRECATION") overridePendingTransition(0, 0)
                },
            )
          }
        }
    }

    /** Shortcut tapped: wait for the real unlock (fingerprint/PIN), then open the app. */
    private fun openAfterUnlock(target: Intent) {
        val km = getSystemService(KeyguardManager::class.java)
        val go = { runCatching { startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; unlocked.value = true }
        if (preview || !km.isKeyguardLocked) { go(); return }
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() { go() }
        })
    }

    private fun requestUnlock() {
        val km = getSystemService(KeyguardManager::class.java)
        if (preview || !km.isKeyguardLocked) { unlocked.value = true; return }
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() { unlocked.value = true }
        })
    }

    @Deprecated("Back does nothing on a lock screen")
    override fun onBackPressed() { if (preview) super.onBackPressed() }

    override fun onDestroy() {
        unregisterReceiver(userPresent)
        super.onDestroy()
    }

    companion object { const val EXTRA_PREVIEW = "preview" }
}
