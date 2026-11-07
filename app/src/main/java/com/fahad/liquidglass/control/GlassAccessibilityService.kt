package com.fahad.liquidglass.control

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Optional extra. The control center works without it; enabling it only adds the
 * "screenshot" and "lock" tiles (Android only allows those through accessibility).
 */
class GlassAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }
    companion object { @Volatile var instance: GlassAccessibilityService? = null }
}
