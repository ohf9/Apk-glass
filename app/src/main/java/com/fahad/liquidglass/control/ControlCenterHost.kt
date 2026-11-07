package com.fahad.liquidglass.control

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import com.fahad.liquidglass.glass.GlassStyle
import com.fahad.liquidglass.Settings as GlassSettings
import kotlin.math.roundToInt

/**
 * Glass Control Center windows. Uses "display over other apps" (TYPE_APPLICATION_OVERLAY),
 * which sideloaded apps can always grant, unlike accessibility which Android marks as a
 * restricted setting. The screen behind gets blurred by the system (cross-window blur).
 */
class ControlCenterHost(private val ctx: Context) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private var trigger: View? = null
    private var panel: ComposeView? = null
    private var panelLp: WindowManager.LayoutParams? = null
    private var life: OverlayLifecycle? = null
    private val state = ControlCenterState()
    private val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    val attached get() = trigger != null

    fun attach() {
        if (trigger != null || !android.provider.Settings.canDrawOverlays(ctx)) return
        val dm = ctx.resources.displayMetrics
        val v = View(ctx)
        val lp = WindowManager.LayoutParams(
            (dm.widthPixels * 0.38f).roundToInt(), (30 * dm.density).roundToInt(), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.END; y = statusBarHeight() }
        var startY = 0f
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startY = e.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    val dy = e.rawY - startY
                    if (dy > 8 * dm.density) { show(); state.dragFromTrigger(dy, dm.heightPixels.toFloat()) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { state.releaseFromTrigger(); true }
                else -> false
            }
        }
        runCatching { wm.addView(v, lp); trigger = v }
    }

    /** Open all the way (from the quick settings tile). */
    fun openFully() {
        if (!android.provider.Settings.canDrawOverlays(ctx)) return
        state.height = ctx.resources.displayMetrics.heightPixels.toFloat()
        show(); state.opens++
    }

    private fun show() {
        if (panel != null) return
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                (if (Build.VERSION.SDK_INT >= 31) WindowManager.LayoutParams.FLAG_BLUR_BEHIND else 0),
            PixelFormat.TRANSLUCENT,
        ).apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            dimAmount = 0f
            if (Build.VERSION.SDK_INT >= 31) blurBehindRadius = 0
        }
        val l = OverlayLifecycle()
        val prefs = GlassSettings(ctx)
        val v = ComposeView(ctx).apply {
            setContent {
                ControlCenter(
                    state = state,
                    style = GlassStyle(enabled = prefs.glass),
                    motionBlur = prefs.motionBlur,
                    bounce = prefs.bounce,
                    globalAction = GlassAccessibilityService.instance?.let { s -> { a: Int -> s.performGlobalAction(a) } },
                    onProgress = ::setBlur,
                    onClosed = ::hide,
                )
            }
        }
        l.attach(v)
        runCatching { wm.addView(v, lp); panel = v; panelLp = lp; life = l }
    }

    private fun setBlur(progress: Float) {
        val lp = panelLp ?: return
        val v = panel ?: return
        val p = progress.coerceIn(0f, 1f)
        val r = (p * GlassSettings(ctx).systemBlur).roundToInt()
        if (Build.VERSION.SDK_INT >= 31 && lp.blurBehindRadius == r) return
        if (Build.VERSION.SDK_INT >= 31) lp.blurBehindRadius = r
        lp.dimAmount = 0.15f * p
        runCatching { wm.updateViewLayout(v, lp) }
    }

    fun hide() {
        panel?.let { runCatching { wm.removeView(it) } }
        life?.destroy()
        panel = null; panelLp = null; life = null
        state.reset()
    }

    fun detach() {
        hide()
        trigger?.let { runCatching { wm.removeView(it) } }
        trigger = null
    }

    private fun statusBarHeight(): Int {
        val id = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) ctx.resources.getDimensionPixelSize(id) else 0
    }
}
