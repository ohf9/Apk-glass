package com.fahad.liquidglass.lock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

/** Emphasised "decelerate" curve, similar feel to iOS/macOS system motion. */
val GlassEase = CubicBezierEasing(0.2f, 0.9f, 0.1f, 1f)

@Composable
fun LockScreen(
    prefs: LockPrefs,
    unlocked: Boolean,
    onSwipeCommitted: () -> Unit,
    onAnimationFinished: () -> Unit,
) {
    val ctx = LocalContext.current
    val layout = remember { LockStore(ctx).load() }
    val style = prefs.style
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    var dragVelocity by remember { mutableFloatStateOf(0f) }
    var touch by remember { mutableStateOf(Offset.Zero) }
    var height by remember { mutableFloatStateOf(1f) }
    val isUnlocked by rememberUpdatedState(unlocked)
    val damping = 1f - prefs.bounce * 0.65f   // 1 = no bounce, 0.35 = very springy

    LaunchedEffect(unlocked) {
        if (!unlocked) return@LaunchedEffect
        val ms = (620 * prefs.speed).toInt()
        val spec = when (style) {
            UnlockStyle.RIPPLE -> tween<Float>((ms * 1.3f).toInt(), easing = LinearEasing)
            UnlockStyle.SLIDE -> tween(ms, easing = GlassEase)
            else -> tween(ms, easing = GlassEase)
        }
        exit.animateTo(1f, spec)
        onAnimationFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                height = size.height.toFloat()
                val vt = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = { touch = it; vt.resetTracking() },
                    onVerticalDrag = { ch, dy ->
                        vt.addPosition(ch.uptimeMillis, ch.position)
                        touch = ch.position
                        scope.launch { drag.snapTo((drag.value - dy).coerceAtLeast(0f)) }
                        dragVelocity = vt.calculateVelocity().y
                    },
                    onDragEnd = {
                        val v = vt.calculateVelocity().y
                        dragVelocity = 0f
                        val commit = drag.value > size.height * 0.16f || v < -1400f
                        if (commit) onSwipeCommitted()
                        scope.launch {
                            if (commit) delay(300)
                            if (!isUnlocked) drag.animateTo(0f, spring(damping, Spring.StiffnessMediumLow), initialVelocity = -v)
                        }
                    },
                    onDragCancel = { scope.launch { drag.animateTo(0f, spring(damping, Spring.StiffnessMediumLow)) } },
                )
            }
            .unlockEffect(
                style = style,
                motionBlur = prefs.motionBlur,
                exit = { exit.value },
                drag = { drag.value },
                velocity = { if (exit.isRunning) exit.velocity * height else dragVelocity + drag.velocity },
                touch = { touch },
            )
    ) {
        LockCanvas(layout) {
            // Swipe hint, gently bobbing.
            var tick by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(Unit) { while (true) { tick += 0.05f; delay(16) } }
            Column(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 18.dp)
                    .graphicsLayer { translationY = sin(tick) * 6f; alpha = 1f - (drag.value / 300f).coerceIn(0f, 1f) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.KeyboardArrowUp, null, tint = Color.White)
                Text("اسحب للأعلى أو استخدم البصمة", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            }
        }
    }
}
