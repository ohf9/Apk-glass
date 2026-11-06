package com.fahad.liquidglass.control

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.platform.ComposeView
import com.fahad.liquidglass.glass.GlassStyle
import com.fahad.liquidglass.Settings as GlassSettings
import kotlin.math.roundToInt

/**
 * Glass Control Center (works over any app, no root).
 * A thin invisible strip on the top-right edge catches a downward swipe; the panel then
 * follows the finger while the real screen behind it gets blurred by the system
 * (cross-window blur, Android 12+), which is what makes it look like actual glass.
 */
class GlassAccessibilityService : AccessibilityService() {
    private lateinit var wm: WindowManager
    private var trigger: View? = null
    private var panel: ComposeView? = null
    private var panelLp: WindowManager.LayoutParams? = null
    private var life: OverlayLifecycle? = null
    private val state = ControlCenterState()

    override fun onServiceConnected() {
        wm = getSystemService(WindowManager::class.java)
        addTrigger()
    }

    private fun addTrigger() {
        val dm = resources.displayMetrics
        val v = View(this)
        val lp = WindowManager.LayoutParams(
            (dm.widthPixels * 0.38f).roundToInt(), (30 * dm.density).roundToInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            y = statusBarHeight()
        }
        var startY = 0f
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startY = e.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    val dy = e.rawY - startY
                    if (dy > 8 * dm.density) { showPanel(); state.dragFromTrigger(dy, dm.heightPixels.toFloat()) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { state.releaseFromTrigger(); true }
                else -> false
            }
        }
        wm.addView(v, lp); trigger = v
    }

    private fun showPanel() {
        if (panel != null) return
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                (if (Build.VERSION.SDK_INT >= 31) WindowManager.LayoutParams.FLAG_BLUR_BEHIND else 0),
            PixelFormat.TRANSLUCENT,
        ).apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            if (Build.VERSION.SDK_INT >= 31) blurBehindRadius = 0
        }
        val l = OverlayLifecycle()
        val prefs = GlassSettings(this)
        val v = ComposeView(this).apply {
            setContent {
                ControlCenter(
                    state = state,
                    style = GlassStyle(enabled = prefs.glass),
                    motionBlur = prefs.motionBlur,
                    bounce = prefs.bounce,
                    service = this@GlassAccessibilityService,
                    onProgress = ::setBlur,
                    onClosed = ::hidePanel,
                )
            }
        }
        l.attach(v)
        wm.addView(v, lp)
        panel = v; panelLp = lp; life = l
    }

    /** Blur radius of what is behind the panel follows how far it is pulled down. */
    private fun setBlur(progress: Float) {
        val lp = panelLp ?: return
        val v = panel ?: return
        if (Build.VERSION.SDK_INT < 31) return
        val r = (progress.coerceIn(0f, 1f) * GlassSettings(this).systemBlur).roundToInt()
        if (lp.blurBehindRadius == r) return
        lp.blurBehindRadius = r
        lp.dimAmount = 0.12f * progress.coerceIn(0f, 1f)
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
        runCatching { wm.updateViewLayout(v, lp) }
    }

    private fun hidePanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        life?.destroy()
        panel = null; panelLp = null; life = null
        state.reset()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        hidePanel()
        trigger?.let { runCatching { wm.removeView(it) } }
        super.onDestroy()
    }

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }
}
