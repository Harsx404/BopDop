package com.dopdop.bopdop.ui.glass

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import com.dopdop.bopdop.ui.theme.Bop
import kotlin.math.cos
import kotlin.math.sin

/**
 * Liquid metal: a slowly flowing silk height-field, shaded like brushed chrome
 * under a soft studio light (reflection → environment gradient + rim band +
 * small specular), then pearl-tinted with the current artwork's pastels.
 * Kept in a light luminance band (≈0.7–1.0) so graphite text always reads.
 */
private const val METAL_SKSL = """
uniform float2 size;
uniform float time;
uniform float3 tintA;
uniform float3 tintB;
uniform float strength;

float hash(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }

float field(float2 p, float t) {
    float2 q = p;
    q.x += 0.38 * sin(p.y * 1.25 + t * 0.35);
    q.y += 0.26 * sin(p.x * 1.05 - t * 0.28);
    float v = sin(q.x * 2.0 + q.y * 1.15 + t * 0.40);
    v += 0.55 * sin(q.x * -1.25 + q.y * 2.55 - t * 0.33);
    v += 0.20 * sin(q.x * 3.6 + q.y * 2.8 + t * 0.55);
    return v;
}

half4 main(float2 frag) {
    float2 uv = frag / size;
    float2 p = frag / size.y * 2.3;
    float t = time;
    float e = 0.012;
    float h0 = field(p, t);
    float hx = field(p + float2(e, 0.0), t);
    float hy = field(p + float2(0.0, e), t);
    float3 n = normalize(float3((h0 - hx) / e * 0.62, (h0 - hy) / e * 0.62, 1.0));
    float3 r = reflect(float3(0.0, 0.0, -1.0), n);
    float env = smoothstep(-0.6, 0.7, -r.y);
    float band = exp(-pow((r.y + 0.18) * 5.0, 2.0));
    float side = smoothstep(-0.7, 0.9, r.x);
    float band2 = exp(-pow((r.x - 0.25) * 4.0, 2.0));
    float l = 0.60 + 0.28 * env + 0.16 * band + 0.08 * band2 + 0.05 * side;
    // Two-tone coloured chrome: troughs take the deep tint, crests the light tint, rims stay white.
    float3 grey = float3(l);
    float3 deep = tintB * (0.74 + 0.16 * env);
    float3 lite = mix(tintA, float3(1.0), 0.35 + 0.45 * band);
    float k = smoothstep(0.58, 1.02, l);
    float3 chrome = mix(deep, lite, k);
    float3 col = mix(grey, chrome, strength);
    float spec = pow(clamp(dot(n, normalize(float3(-0.35, -0.55, 0.76))), 0.0, 1.0), 42.0);
    col += spec * 0.22;
    col += (hash(frag) - 0.5) * 0.016;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

@Composable
/**
 * The backdrop drifts slowly, so 30 updates a second look identical to 60 — and
 * every update also forces each glass blur above it to recompute. Halving the
 * rate halves that work for the whole UI.
 */
private fun rememberTime(animate: Boolean): State<Float> = produceState(4f, animate) {
    if (!animate) return@produceState
    val start = withInfiniteAnimationFrameMillis { it }
    var last = 0L
    while (true) withInfiniteAnimationFrameMillis {
        if (it - last >= 33) { last = it; value = 4f + (it - start) / 1000f * 0.32f }
    }
}

@Composable
fun LiquidMetalBackground(
    palette: ArtPalette,
    modifier: Modifier = Modifier,
    strength: Float = 0.78f,
    animate: Boolean = !LocalReduceMotion.current,
) {
    val a by animateColorAsState(palette.pastel, tween(1600), label = "ta")
    val b by animateColorAsState(palette.pastel2, tween(1600), label = "tb")
    val time = rememberTime(animate)
    if (Build.VERSION.SDK_INT >= 33) ShaderMetal(modifier, time, a, b, strength)
    else Canvas(modifier) {
        val t = time.value
        drawRect(Brush.verticalGradient(listOf(lerpTint(Color(0xFFEDEFF2), a, strength * 0.6f), lerpTint(Bop.Pearl, b, strength * 0.6f))))
        // Drifting satin bands
        for (i in 0 until 4) {
            val y = size.height * (0.15f + i * 0.25f) + sin(t * 0.4f + i) * 60f
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.06f), Color.Transparent),
                    start = Offset(0f, y - 220f + cos(t * 0.3f + i) * 80f), end = Offset(size.width, y + 160f),
                ),
            )
        }
    }
}

private fun lerpTint(base: Color, tint: Color, s: Float) = Color(
    base.red * (1 - s + s * tint.red), base.green * (1 - s + s * tint.green), base.blue * (1 - s + s * tint.blue),
)

@RequiresApi(33)
@Composable
private fun ShaderMetal(modifier: Modifier, time: State<Float>, a: Color, b: Color, strength: Float) {
    val shader = remember { RuntimeShader(METAL_SKSL) }
    val brush = remember(shader) { ShaderBrush(shader) }
    // Rendered at half resolution into a cached offscreen layer, then scaled up: the
    // shader runs on a quarter of the pixels and only when the time actually changes.
    androidx.compose.foundation.layout.Box(modifier) {
        Canvas(
            Modifier.fillMaxWidth(0.5f).fillMaxHeight(0.5f).graphicsLayer {
                scaleX = 2f; scaleY = 2f
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
            },
        ) {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("time", time.value)
        shader.setFloatUniform("tintA", a.red, a.green, a.blue)
        shader.setFloatUniform("tintB", b.red, b.green, b.blue)
            shader.setFloatUniform("strength", strength)
            drawRect(brush)
        }
    }
}
