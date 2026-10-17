package com.dopdop.bopdop.ui.glass

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush

/**
 * A living two-colour gradient for small cards: three soft light pools drift across a diagonal
 * blend, a faint silk ripple runs through it and a gloss sits in the top corner. [seed] offsets
 * each card so a row of them never moves in step.
 */
private const val SILK_SKSL = """
uniform float2 size;
uniform float time;
uniform float3 c1;
uniform float3 c2;
uniform float seed;

float hash(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }

float pool(float2 p, float2 c, float k) { float2 d = p - c; return exp(-dot(d, d) * k); }

half4 main(float2 frag) {
    float2 uv = frag / size;
    float aspect = size.x / size.y;
    float2 p = float2(uv.x * aspect, uv.y);
    float t = time + seed * 7.31;
    float3 col = mix(c2, c1, clamp(0.85 - uv.x * 0.55 - uv.y * 0.35, 0.0, 1.0));
    float3 lite = mix(c1, float3(1.0), 0.42);
    float3 deep = c2 * 0.72;
    col = mix(col, lite, 0.75 * pool(p, float2(0.25 + 0.22 * sin(t * 0.50), 0.28 + 0.18 * cos(t * 0.41)), 3.2));
    col = mix(col, deep, 0.65 * pool(p, float2(aspect * 0.78 + 0.25 * cos(t * 0.33 + 1.7), 0.80 + 0.18 * sin(t * 0.46 + 0.6)), 2.6));
    col = mix(col, mix(c1, c2, 0.5) * 1.12, 0.45 * pool(p, float2(aspect * 0.55 + 0.30 * sin(t * 0.27 + 3.1), 0.12 + 0.22 * cos(t * 0.31 + 2.2)), 4.0));
    float ripple = sin(p.x * 3.2 + 1.3 * sin(p.y * 2.6 + t * 0.6) - t * 0.45);
    col += 0.07 * smoothstep(0.55, 1.0, ripple);
    col += 0.12 * smoothstep(0.6, 0.0, length(float2(uv.x * aspect, uv.y) - float2(0.1, -0.05)));
    col += (hash(frag) - 0.5) * 0.025;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

/** Seconds for the silk cards, updated ~30 times a second (they move slowly); frozen when [animate] is off. */
@Composable
fun rememberSilkTime(animate: Boolean = true): State<Float> = produceState(0f, animate) {
    if (!animate) return@produceState
    val start = withInfiniteAnimationFrameMillis { it }
    var last = 0L
    while (true) withInfiniteAnimationFrameMillis {
        if (it - last >= 33) { last = it; value = (it - start) / 1000f }
    }
}

@Composable
fun SilkGradient(colors: List<Color>, time: State<Float>, seed: Float, modifier: Modifier = Modifier) {
    val a = colors.first()
    val b = colors.last()
    if (Build.VERSION.SDK_INT >= 33) ShaderSilk(a, b, time, seed, modifier)
    else Canvas(modifier) { drawRect(Brush.linearGradient(listOf(a, b))) }
}

@RequiresApi(33)
@Composable
private fun ShaderSilk(a: Color, b: Color, time: State<Float>, seed: Float, modifier: Modifier) {
    val shader = remember { RuntimeShader(SILK_SKSL) }
    val brush = remember(shader) { ShaderBrush(shader) }
    Canvas(modifier) {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("time", time.value)
        shader.setFloatUniform("c1", a.red, a.green, a.blue)
        shader.setFloatUniform("c2", b.red, b.green, b.blue)
        shader.setFloatUniform("seed", seed)
        drawRect(brush)
    }
}
