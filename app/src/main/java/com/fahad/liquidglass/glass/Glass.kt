package com.fahad.liquidglass.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * How it works: the backdrop (wallpaper) is drawn once full-screen by [GlassRoot].
 * Every [GlassPanel] draws the *same* backdrop again inside its own clip, shifted so it
 * lines up with what is behind it, then blurs it (API 31+) and bends it near the edges
 * with an AGSL lens shader (API 33+). On top go a milky tint, a top sheen and a lit rim.
 */
class Backdrop(val content: @Composable () -> Unit, val size: IntSize)

val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** App-wide glass settings: the one "glass on/off" switch plus its strength. */
data class GlassStyle(val enabled: Boolean = true, val blur: Dp = 22.dp, val refraction: Dp = 14.dp)

val LocalGlassStyle = androidx.compose.runtime.compositionLocalOf { GlassStyle() }

@Composable
fun GlassRoot(
    backdrop: @Composable () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    Box(Modifier.fillMaxSize().onSizeChanged { size = it }) {
        backdrop()
        CompositionLocalProvider(LocalBackdrop provides Backdrop(backdrop, size)) {
            content()
        }
    }
}

// Edge-refraction lens: samples get pulled toward the centre near the rim,
// so the backdrop looks magnified and bent at the edges like thick glass.
private const val LENS_AGSL = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float strength;
half4 main(float2 p) {
    float2 c = size * 0.5;
    float2 q = abs(p - c) - (c - radius);
    float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
    edge = smoothstep(-strength * 2.5, 0.0, d);
    float2 dir = normalize(p - c + float2(0.0001));
    float2 sp = p - dir * edge * edge * strength;
    return content.eval(sp);
}
"""

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    blur: Dp = Dp.Unspecified,
    refraction: Dp = Dp.Unspecified,
    tint: Color = Color.White,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val style = LocalGlassStyle.current
    val blur = if (blur == Dp.Unspecified) style.blur else blur
    val refraction = if (refraction == Dp.Unspecified) style.refraction else refraction
    if (!style.enabled) {
        // Glass switched off: plain dark translucent card.
        Box(modifier.clip(shape).background(Color.Black.copy(alpha = 0.35f))) {
            Box(Modifier.padding(contentPadding), content = content)
        }
        return
    }
    val backdrop = LocalBackdrop.current
    val density = LocalDensity.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(modifier.clip(shape)) {
        if (backdrop != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .onGloballyPositioned { pos = it.positionInRoot() }
                    .graphicsLayer {
                        clip = true
                        this.shape = shape
                        if (canBlur) {
                            val r = with(density) { blur.toPx() }
                            var effect = RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && size.width > 0f) {
                                val lens = RuntimeShader(LENS_AGSL).apply {
                                    setFloatUniform("size", size.width, size.height)
                                    setFloatUniform("radius", with(density) { cornerRadius.toPx() })
                                    setFloatUniform("strength", with(density) { refraction.toPx() })
                                }
                                effect = RenderEffect.createChainEffect(
                                    RenderEffect.createRuntimeShaderEffect(lens, "content"), effect
                                )
                            }
                            renderEffect = effect.asComposeRenderEffect()
                        }
                    }
            ) {
                // Full-screen copy of the backdrop, shifted to sit exactly behind this panel.
                Box(Modifier.layout { m, c ->
                    val p = m.measure(Constraints.fixed(backdrop.size.width.coerceAtLeast(1), backdrop.size.height.coerceAtLeast(1)))
                    layout(c.maxWidth, c.maxHeight) { p.place(-pos.x.roundToInt(), -pos.y.roundToInt()) }
                }) { backdrop.content() }
            }
        }
        // Milky tint (stronger when we cannot blur, so text stays readable).
        val a = if (canBlur) 0.16f else 0.38f
        Box(Modifier.matchParentSize().background(
            Brush.verticalGradient(listOf(tint.copy(alpha = a + 0.10f), tint.copy(alpha = a)))
        ))
        // Specular sheen across the top.
        Box(Modifier.matchParentSize().background(
            Brush.verticalGradient(0f to Color.White.copy(alpha = 0.22f), 0.45f to Color.Transparent)
        ))
        // Lit rim: bright on top-left, fading, catching light again bottom-right.
        Box(Modifier.matchParentSize().border(
            1.dp,
            Brush.linearGradient(listOf(
                Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.40f)
            )),
            shape,
        ))
        Box(Modifier.padding(contentPadding), content = content)
    }
}
