package com.dopdop.bopdop.ui.player

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * macOS-style genie: the whole Now Playing screen pours down a curved funnel into
 * [target] (the mini player's album cover). Inverse-mapped per pixel, so it warps
 * whatever the layer drew — glass, lyrics, everything.
 */
private const val GENIE_SKSL = """
uniform shader content;
uniform float2 size;
uniform float4 target; // left, top, right, bottom
uniform float e;       // 0 = open, 1 = inside the cover

half4 main(float2 p) {
    float bend  = smoothstep(0.0, 0.5, e);   // sides curve in first
    float slide = smoothstep(0.2, 1.0, e);   // then the top pours down
    float sink  = smoothstep(0.0, 0.75, e);  // bottom reaches the cover early
    float top = mix(0.0, target.y, slide);
    float bot = max(mix(size.y, target.w, sink), top + 1.0);
    if (p.y < top || p.y > bot) return half4(0);

    // Funnel fixed in space: full width at the top of the screen, cover width at the cover.
    float neck = smoothstep(0.0, target.y, p.y) * bend;
    float l = mix(0.0, target.x, neck);
    float r = mix(size.x, target.z, neck);
    if (p.x < l || p.x > r) return half4(0);

    float u = (p.x - l) / max(r - l, 1.0);
    float v = (p.y - top) / (bot - top);
    // One-pixel feather on the curved sides.
    float a = clamp(min(p.x - l, r - p.x), 0.0, 1.0);
    return content.eval(float2(u * size.x, v * size.y)) * a;
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class Genie {
    private val shader = RuntimeShader(GENIE_SKSL)

    fun effect(size: Size, target: Rect, e: Float): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("target", target.left, target.top, target.right, target.bottom)
        shader.setFloatUniform("e", e)
        return RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}
