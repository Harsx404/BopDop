package com.dopdop.bopdop.ui.glass

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dopdop.bopdop.data.GlassLevel
import com.dopdop.bopdop.ui.theme.Bop
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/** The haze source glass surfaces sample from (root chrome, or a screen's own content). */
val LocalHaze = staticCompositionLocalOf<HazeState?> { null }
val LocalGlassLevel = compositionLocalOf { GlassLevel.REGULAR }
val LocalReduceMotion = compositionLocalOf { false }

private data class GlassSpec(val blur: Dp, val tint: Float, val vibrancy: Float)

private fun spec(level: GlassLevel) = when (level) {
    GlassLevel.CLEAR -> GlassSpec(14.dp, 0.07f, 1.5f)
    GlassLevel.REGULAR -> GlassSpec(26.dp, 0.13f, 1.7f)
    GlassLevel.FROSTED -> GlassSpec(40.dp, 0.22f, 1.8f)
}

/** Opaque stand-in for glass when there is nothing to blur (or blur is unavailable). */
private val GlassFallback = Color(0xFF302B66)

/**
 * Vibrancy: what shows through Liquid Glass comes out more saturated and a
 * touch brighter than the backdrop itself, so colour glows through the pane.
 */
private fun vibrancy(saturation: Float, lift: Float): ColorFilter {
    val m = ColorMatrix().apply { setToSaturation(saturation) }
    val v = m.values
    v[4] += lift; v[9] += lift; v[14] += lift
    return ColorFilter.colorMatrix(m)
}

/**
 * Liquid Glass: real backdrop blur (Haze; opaque scrim on API < 31) with a
 * vibrancy boost, a whisper of white, and the refractive edge — a light band
 * just inside the rim plus a specular hairline brightest at the top-left and
 * bottom-right corners, as if lit through a thick rounded slab.
 */
@Composable
fun Modifier.glass(
    shape: Shape,
    state: HazeState? = LocalHaze.current,
    tint: Color = Color.White,
    emphasis: Float = 1f,
    shadow: Dp = 14.dp,
    rim: Boolean = true,
): Modifier {
    val level = LocalGlassLevel.current
    val s = spec(level)
    val alpha = (s.tint * emphasis).coerceIn(0f, 0.9f)
    val style = remember(level, tint, emphasis) {
        HazeBlurStyle {
            blurRadius(s.blur)
            noiseFactor(0.04f)
            backgroundColor(Bop.Base)
            colorEffects(listOf(
                HazeColorEffect.colorFilter(vibrancy(s.vibrancy, 10f)),
                HazeColorEffect.tint(tint.copy(alpha = alpha)),
            ))
            fallbackColorEffect(HazeColorEffect.tint(GlassFallback.copy(alpha = 0.9f)))
        }
    }
    var m = if (shadow > 0.dp) this.softShadow(shape, shadow) else this
    m = m.clip(shape)
    m = if (state != null) m.hazeBlur(HazeInput.Sources(state), style)
    else m.background(GlassFallback.copy(alpha = 0.9f)).background(tint.copy(alpha = alpha))
    return m.glassEdges(shape, if (rim) 1f else 0.35f)
}

/** Glass for controls that sit on photos / colour washes: clearer, no shadow. */
@Composable
fun Modifier.mediaGlass(shape: Shape, state: HazeState?, emphasis: Float = 1f): Modifier {
    val style = remember(emphasis) {
        HazeBlurStyle {
            blurRadius(22.dp)
            noiseFactor(0.05f)
            colorEffects(listOf(
                HazeColorEffect.colorFilter(vibrancy(1.4f, 8f)),
                HazeColorEffect.tint(Color.White.copy(alpha = 0.14f * emphasis)),
            ))
            fallbackColorEffect(HazeColorEffect.tint(Color.White.copy(alpha = 0.22f)))
        }
    }
    var m = this.clip(shape)
    m = if (state != null) m.hazeBlur(HazeInput.Sources(state), style) else m.background(Color.White.copy(alpha = 0.18f * emphasis))
    return m.glassEdges(shape, 0.85f)
}

/**
 * Translucent glass without backdrop sampling — for chips and cards inside
 * scrolling content. [alpha] is the fill strength of [tint] (white ≈ 0.1–0.2
 * reads as clear glass on the aurora).
 */
@Composable
fun Modifier.glassLite(shape: Shape, tint: Color = Color.White, alpha: Float = 0.14f, shadow: Dp = 0.dp): Modifier =
    (if (shadow > 0.dp) this.softShadow(shape, shadow) else this)
        .clip(shape).background(tint.copy(alpha = alpha)).glassEdges(shape, 1f)

/**
 * The liquid edge. Inside the shape a soft white band hugs the rim (the
 * backdrop "bending" through the curved edge), strongest top-left; on the rim
 * a specular hairline catches light at the top-left and, dimmer, the
 * bottom-right, and nearly vanishes along the sides between.
 */
fun Modifier.glassEdges(shape: Shape, strength: Float = 1f): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val native = path.asAndroidPath()
    val minDim = size.minDimension
    val bevel = (minDim * 0.16f).coerceIn(3.dp.toPx(), 12.dp.toPx())
    val band = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = bevel * 2f
        maskFilter = BlurMaskFilter(bevel * 0.9f, BlurMaskFilter.Blur.NORMAL)
        shader = android.graphics.LinearGradient(
            0f, 0f, size.width, size.height,
            intArrayOf(
                Color.White.copy(alpha = 0.30f * strength).toArgb(),
                Color.White.copy(alpha = 0.06f * strength).toArgb(),
                Color.White.copy(alpha = 0.04f * strength).toArgb(),
                Color.White.copy(alpha = 0.20f * strength).toArgb(),
            ),
            floatArrayOf(0f, 0.4f, 0.65f, 1f), android.graphics.Shader.TileMode.CLAMP,
        )
    }
    // Diagonal light: start/end pushed past the corners so the hot spots sit on them.
    val diag = Offset(size.width, size.height)
    val rim = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.95f * strength),
        0.22f to Color.White.copy(alpha = 0.45f * strength),
        0.45f to Color.White.copy(alpha = 0.10f * strength),
        0.62f to Color.White.copy(alpha = 0.10f * strength),
        0.82f to Color.White.copy(alpha = 0.40f * strength),
        1f to Color.White.copy(alpha = 0.75f * strength),
        start = Offset.Zero - diag * 0.04f, end = diag * 1.04f,
    )
    val stroke = (1.1.dp.toPx() * strength).coerceAtLeast(0.6.dp.toPx())
    onDrawWithContent {
        if (strength > 0f) drawIntoCanvas { c ->
            val n = c.nativeCanvas
            n.save()
            n.clipPath(native)
            n.drawPath(native, band)
            n.restore()
        }
        drawContent()
        if (strength > 0f) drawPath(path, rim, style = Stroke(stroke))
    }
}

/**
 * Soft drop shadow drawn only *outside* the real shape: a normal Gaussian blur of
 * the (slightly lowered) outline, with the shape itself clipped out so nothing
 * ghosts through translucent glass.
 */
fun Modifier.softShadow(shape: Shape, blur: Dp, color: Color = Color(0xFF0A0820), alpha: Float = 0.22f, dy: Dp = 4.dp): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }.asAndroidPath()
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.copy(alpha = alpha).toArgb()
        maskFilter = BlurMaskFilter((blur.toPx() * 0.6f).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
    }
    drawIntoCanvas { c ->
        val n = c.nativeCanvas
        n.save()
        n.clipOutPath(path)
        n.translate(0f, dy.toPx())
        n.drawPath(path, paint)
        n.restore()
    }
}
