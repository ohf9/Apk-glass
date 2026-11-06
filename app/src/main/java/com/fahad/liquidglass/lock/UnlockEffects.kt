package com.fahad.liquidglass.lock

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs

// macOS-style "Genie": the screen funnels up into the top centre (towards the camera).
private const val GENIE = """
uniform shader content;
uniform float2 size;
uniform float t;
half4 main(float2 p) {
    float W = size.x; float H = size.y; float cx = W * 0.5;
    float k = clamp(t, 0.0, 0.999);
    float sy = p.y / (1.0 - k);                 // bottom edge rises to the top
    if (sy > H) return half4(0);
    float yn = 1.0 - p.y / H;                    // 1 at top: narrowest there
    float s = clamp(k * 1.7, 0.0, 1.0) * (0.25 + 0.75 * smoothstep(0.0, 1.0, yn));
    float w = mix(1.0, 0.02, s);
    float sx = cx + (p.x - cx) / w;
    if (sx < 0.0 || sx > W) return half4(0);
    return content.eval(float2(sx, sy)) * (1.0 - smoothstep(0.85, 1.0, t));
}
"""

// Liquid ripple: a glassy wave rolls out from the finger and reveals what is underneath.
private const val RIPPLE = """
uniform shader content;
uniform float2 size;
uniform float2 center;
uniform float t;
half4 main(float2 p) {
    float2 d = p - center; float dist = length(d);
    float R = t * length(size) * 1.05;
    float band = 110.0;
    float x = (dist - R) / band;
    float w = exp(-x * x);
    float amp = 46.0 * (1.0 - t);
    float2 dir = dist > 0.001 ? d / dist : float2(0.0);
    float2 sp = p - dir * sin(x * 3.14159) * w * amp;
    half4 c = content.eval(sp);
    float gone = smoothstep(R - band * 0.2, R - band * 1.6, dist);
    return c * (1.0 - gone) + half4(w * 0.18 * (1.0 - t)) * (1.0 - gone);
}
"""

/**
 * Applies the chosen unlock animation. [exit] runs 0 -> 1 once unlocked, [drag] is how far
 * the finger has pulled up (px), [velocity] feeds the motion blur. Reads happen inside
 * graphicsLayer so animating never recomposes the lock screen.
 */
fun Modifier.unlockEffect(
    style: UnlockStyle,
    motionBlur: Float,
    exit: () -> Float,
    drag: () -> Float,
    velocity: () -> Float,
    touch: () -> Offset,
): Modifier = composed {
    val shader = remember(style) {
        if (Build.VERSION.SDK_INT < 33) null else when (style) {
            UnlockStyle.GENIE -> RuntimeShader(GENIE)
            UnlockStyle.RIPPLE -> RuntimeShader(RIPPLE)
            else -> null
        }
    }
    graphicsLayer {
        val p = exit()
        val d = drag()
        val h = size.height
        // Rubber-band follow while the finger is down.
        val pull = -d * 0.5f
        translationY = pull
        val dragScale = 1f - (d / h).coerceIn(0f, 1f) * 0.08f
        scaleX = dragScale; scaleY = dragScale
        var blur = 0f
        when (style) {
            UnlockStyle.SLIDE -> translationY = pull * (1 - p) - p * h * 1.05f
            UnlockStyle.ZOOM -> {
                val s = dragScale + 0.4f * p; scaleX = s; scaleY = s
                alpha = 1f - p; blur = 40f * p
            }
            UnlockStyle.DISSOLVE -> {
                val s = dragScale - 0.06f * p; scaleX = s; scaleY = s
                alpha = 1f - p * p; blur = 80f * p
            }
            UnlockStyle.GENIE -> if (shader == null) {
                transformOrigin = TransformOrigin(0.5f, 0f)
                scaleX = dragScale * (1 - p); scaleY = dragScale * (1 - p * 0.9f); alpha = 1 - p
            }
            UnlockStyle.RIPPLE -> if (shader == null) alpha = 1 - p
        }
        // Motion blur: stretch the blur along the direction of travel, scaled by speed.
        val mb = (abs(velocity()) / 8000f).coerceIn(0f, 1f) * motionBlur * 60f
        val effects = mutableListOf<RenderEffect>()
        if (Build.VERSION.SDK_INT >= 31) {
            if (blur > 0.5f) effects += RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.DECAL)
            if (mb > 0.5f) effects += RenderEffect.createBlurEffect(mb * 0.15f, mb, Shader.TileMode.DECAL)
        }
        if (shader != null && Build.VERSION.SDK_INT >= 33 && p > 0f) {
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("t", p)
            if (style == UnlockStyle.RIPPLE) shader.setFloatUniform("center", touch().x, touch().y - pull)
            effects += RenderEffect.createRuntimeShaderEffect(shader, "content")
        }
        renderEffect = effects.reduceOrNull { inner, outer -> RenderEffect.createChainEffect(outer, inner) }
            ?.asComposeRenderEffect()
    }
}
