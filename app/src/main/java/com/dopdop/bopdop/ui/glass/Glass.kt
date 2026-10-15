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

private data class GlassSpec(val blur: Dp, val tint: Float)

private fun spec(level: GlassLevel) = when (level) {
    GlassLevel.CLEAR -> GlassSpec(14.dp, 0.22f)
    GlassLevel.REGULAR -> GlassSpec(24.dp, 0.42f)
    GlassLevel.FROSTED -> GlassSpec(36.dp, 0.62f)
}

/**
 * Soft frosted glass for light backdrops: real backdrop blur (Haze; scrim on
 * API < 31), milky white tint, a bright top-left rim and an outer-only shadow
 * so the surface floats like the reference's pill chips.
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
    val alpha = (s.tint * emphasis).coerceIn(0f, 1f)
    val style = remember(level, tint, emphasis) {
        HazeBlurStyle {
            blurRadius(s.blur)
            noiseFactor(0.05f)
            backgroundColor(Bop.Pearl)
            colorEffects(listOf(HazeColorEffect.tint(tint.copy(alpha = alpha))))
            fallbackColorEffect(HazeColorEffect.tint(tint.copy(alpha = 0.86f)))
        }
    }
    var m = if (shadow > 0.dp) this.softShadow(shape, shadow) else this
    m = m.clip(shape)
    m = if (state != null) m.hazeBlur(HazeInput.Sources(state), style) else m.background(tint.copy(alpha = (alpha + 0.3f).coerceAtMost(0.92f)))
    return m.glassEdges(shape, if (rim) 1f else 0f, light = true)
}

/** Glass for controls that sit on photos / colour washes: clearer, white rim, no shadow. */
@Composable
fun Modifier.mediaGlass(shape: Shape, state: HazeState?, emphasis: Float = 1f): Modifier {
    val style = remember(emphasis) {
        HazeBlurStyle {
            blurRadius(22.dp)
            noiseFactor(0.06f)
            colorEffects(listOf(HazeColorEffect.tint(Color.White.copy(alpha = 0.16f * emphasis))))
            fallbackColorEffect(HazeColorEffect.tint(Color.White.copy(alpha = 0.22f)))
        }
    }
    var m = this.clip(shape)
    m = if (state != null) m.hazeBlur(HazeInput.Sources(state), style) else m.background(Color.White.copy(alpha = 0.18f * emphasis))
    return m.glassEdges(shape, 0.75f, light = false)
}

/** Translucent glass without backdrop sampling — for chips inside scrolling content. */
@Composable
fun Modifier.glassLite(shape: Shape, tint: Color = Color.White, alpha: Float = 0.55f, shadow: Dp = 0.dp): Modifier =
    (if (shadow > 0.dp) this.softShadow(shape, shadow) else this)
        .clip(shape).background(tint.copy(alpha = alpha)).glassEdges(shape, 1f, light = tint.luminanceIsLight())

private fun Color.luminanceIsLight() = (0.299f * red + 0.587f * green + 0.114f * blue) > 0.5f

/**
 * Rim + sheen. Light glass: brilliant top-left edge fading out, a faint dark
 * hairline at the bottom for thickness. Media glass: an even white rim.
 */
fun Modifier.glassEdges(shape: Shape, strength: Float, light: Boolean = true): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val stroke = 1.2.dp.toPx()
    val rim = if (light) Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.95f * strength),
        0.45f to Color.White.copy(alpha = 0.35f * strength),
        0.8f to Color.Black.copy(alpha = 0.03f * strength),
        1f to Color.Black.copy(alpha = 0.07f * strength),
        start = Offset.Zero, end = Offset(size.width * 0.6f, size.height),
    ) else Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.55f * strength),
        0.5f to Color.White.copy(alpha = 0.18f * strength),
        1f to Color.White.copy(alpha = 0.38f * strength),
        start = Offset.Zero, end = Offset(size.width, size.height),
    )
    val sheen = Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (light) 0.35f else 0.14f),
        0.5f to Color.White.copy(alpha = 0f),
    )
    onDrawWithContent {
        drawPath(path, sheen)
        drawContent()
        if (strength > 0f) drawPath(path, rim, style = Stroke(stroke))
    }
}

/**
 * Soft drop shadow drawn only *outside* the real shape: a normal Gaussian blur of
 * the (slightly lowered) outline, with the shape itself clipped out so nothing
 * ghosts through translucent glass.
 */
fun Modifier.softShadow(shape: Shape, blur: Dp, color: Color = Color(0xFF3A4150), alpha: Float = 0.14f, dy: Dp = 3.dp): Modifier = drawBehind {
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
