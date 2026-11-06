package com.fahad.liquidglass.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

/** Slowly drifting colour blobs: a stand-in wallpaper that shows off the glass. */
@Composable
fun LiquidBackdrop(modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "bg").animateFloat(
        0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart), label = "t"
    )
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.linearGradient(listOf(Color(0xFF0B1026), Color(0xFF1B0F3B), Color(0xFF06283D))))
        val blobs = listOf(
            Triple(Color(0xFFFF4D8D), 0.30f, 0.0f),
            Triple(Color(0xFF3D7BFF), 0.38f, 2.1f),
            Triple(Color(0xFF00E0B8), 0.28f, 4.2f),
            Triple(Color(0xFFFFB13D), 0.22f, 1.0f),
        )
        blobs.forEachIndexed { i, (c, r, phase) ->
            val cx = size.width * (0.5f + 0.35f * cos(t + phase + i))
            val cy = size.height * (0.45f + 0.30f * sin(t * (1 + i * 0.25f) + phase))
            val rad = size.minDimension * r * 1.6f
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.85f), Color.Transparent), Offset(cx, cy), rad), rad, Offset(cx, cy))
        }
    }
}
