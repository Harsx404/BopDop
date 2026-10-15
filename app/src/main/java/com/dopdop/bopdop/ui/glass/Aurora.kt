package com.dopdop.bopdop.ui.glass

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
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
import kotlin.math.max
import kotlin.math.sin

/**
 * Liquid aurora: four soft colour glows drifting over a deep indigo base,
 * blended like a mesh gradient and folded by a slow domain warp into the wide
 * silky sweeps of the Liquid Glass wallpapers. Luminance is capped so white
 * type on clear glass always reads.
 */
private const val AURORA_SKSL = """
uniform float2 size;
uniform float time;
uniform float3 c0;
uniform float3 c1;
uniform float3 c2;
uniform float3 c3;
uniform float3 base;

float hash(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }

float glow(float2 p, float2 c, float r) {
    float2 d = p - c;
    return exp(-dot(d, d) / (r * r));
}

half4 main(float2 frag) {
    float a = size.x / size.y;
    float2 uv = frag / size;
    float2 p = float2(uv.x * a, uv.y);
    float t = time;

    // Domain warp: the glows smear into broad diagonal folds instead of round blobs.
    float2 w = p;
    w.x += 0.16 * sin(p.y * 2.4 + t * 0.50) + 0.05 * sin(p.y * 5.3 - t * 0.35);
    w.y += 0.12 * sin(p.x * 3.1 - t * 0.40) + 0.04 * sin((p.x + p.y) * 6.0 + t * 0.3);

    float g0 = glow(w, float2(a * (0.10 + 0.12 * sin(t * 0.31)), 0.06 + 0.07 * cos(t * 0.27)), 0.52);
    float g1 = glow(w, float2(a * (0.28 + 0.16 * cos(t * 0.23)), 0.52 + 0.10 * sin(t * 0.29)), 0.42);
    float g2 = glow(w, float2(a * (0.92 + 0.12 * sin(t * 0.19 + 1.0)), 0.30 + 0.12 * cos(t * 0.21)), 0.50);
    float g3 = glow(w, float2(a * (0.72 + 0.16 * cos(t * 0.17 + 2.0)), 0.96 + 0.07 * sin(t * 0.25)), 0.55);

    float k = 0.18;
    float3 col = (base * k + c0 * g0 + c1 * g1 + c2 * g2 + c3 * g3) / (k + g0 + g1 + g2 + g3);

    // Silk folds: a slow travelling sheen and a deeper trough that sweeps across.
    float f = sin(w.x * 3.4 + w.y * 2.2 + t * 0.22);
    col *= 0.86 + 0.14 * smoothstep(-0.9, 0.9, f);
    col += 0.07 * exp(-pow((f - 0.75) * 2.6, 2.0)) * float3(0.85, 0.9, 1.0);

    // Keep it a backdrop: gentle top/bottom falloff and a luminance ceiling.
    col *= 1.0 - 0.18 * pow(abs(uv.y - 0.45) * 1.6, 2.0);
    float l = dot(col, float3(0.299, 0.587, 0.114));
    col *= min(1.0, 0.60 / max(l, 0.001));
    col += (hash(frag) - 0.5) * 0.012;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

@Composable
private fun rememberTime(animate: Boolean): State<Float> = produceState(4f, animate) {
    if (!animate) return@produceState
    val start = withInfiniteAnimationFrameMillis { it }
    while (true) withInfiniteAnimationFrameMillis { value = 4f + (it - start) / 1000f * 0.32f }
}

@Composable
fun LiquidAuroraBackground(
    palette: ArtPalette,
    modifier: Modifier = Modifier,
    animate: Boolean = !LocalReduceMotion.current,
) {
    val c0 by animateColorAsState(palette.aurora[0], tween(1600), label = "a0")
    val c1 by animateColorAsState(palette.aurora[1], tween(1600), label = "a1")
    val c2 by animateColorAsState(palette.aurora[2], tween(1600), label = "a2")
    val c3 by animateColorAsState(palette.aurora[3], tween(1600), label = "a3")
    val colors = listOf(c0, c1, c2, c3)
    val time = rememberTime(animate)
    if (Build.VERSION.SDK_INT >= 33) ShaderAurora(modifier, time, colors)
    else Canvas(modifier) {
        // API < 33: the same four glows as drifting radial gradients.
        val t = time.value
        val r = max(size.width, size.height) * 0.62f
        drawRect(Bop.Base)
        val centers = listOf(
            Offset(size.width * (0.10f + 0.12f * sin(t * 0.31f)), size.height * (0.06f + 0.07f * cos(t * 0.27f))),
            Offset(size.width * (0.28f + 0.16f * cos(t * 0.23f)), size.height * (0.52f + 0.10f * sin(t * 0.29f))),
            Offset(size.width * (0.92f + 0.12f * sin(t * 0.19f + 1f)), size.height * (0.30f + 0.12f * cos(t * 0.21f))),
            Offset(size.width * (0.72f + 0.16f * cos(t * 0.17f + 2f)), size.height * (0.96f + 0.07f * sin(t * 0.25f))),
        )
        centers.forEachIndexed { i, c ->
            val col = colors[i].copy(alpha = 0.9f)
            drawRect(Brush.radialGradient(listOf(col, col.copy(alpha = 0.45f), Color.Transparent), center = c, radius = r))
        }
    }
}

@RequiresApi(33)
@Composable
private fun ShaderAurora(modifier: Modifier, time: State<Float>, colors: List<Color>) {
    val shader = remember { RuntimeShader(AURORA_SKSL) }
    val brush = remember(shader) { ShaderBrush(shader) }
    Canvas(modifier) {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("time", time.value)
        colors.forEachIndexed { i, c -> shader.setFloatUniform("c$i", c.red, c.green, c.blue) }
        shader.setFloatUniform("base", Bop.Base.red, Bop.Base.green, Bop.Base.blue)
        drawRect(brush)
    }
}
