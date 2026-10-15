@file:OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class)

package com.dopdop.bopdop.ui.glass

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import dev.chrisbanes.haze.glass.hazeGlass
import androidx.compose.runtime.getValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.InteractionSource
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ClipOp
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
    GlassLevel.CLEAR -> GlassSpec(14.dp, 0.32f)
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
    interactionSource: InteractionSource? = null,
    strong: Boolean = false,
): Modifier {
    val level = LocalGlassLevel.current
    val s = spec(level)
    val alpha = (s.tint * emphasis).coerceIn(0f, 1f)
    var m = if (shadow > 0.dp) this.softShadow(shape, shadow) else this
    // True Liquid Glass: refraction, fresnel, specular that follows the phone's tilt.
    if (state != null && shape is RoundedCornerShape) {
        val light by LocalGlassLight.current
        val style = remember(level, light, shape, tint, alpha, strong) {
            liquidStyle(level, light, shape, if (level == GlassLevel.CLEAR) Color.Transparent else tint.copy(alpha = alpha), strong = strong, onMedia = false, pane = !strong)
        }
        return m.hazeGlass(
            HazeInput.Sources(state), style, expandLayerBounds = false, interactionSource = interactionSource,
            // Big frosted panes hide detail anyway; small lens-like controls keep the default fidelity.
            performanceMode = if (strong) null else dev.chrisbanes.haze.HazePerformanceMode.Fixed(0.3f),
        )
    }
    val blur = remember(level, tint, emphasis) {
        HazeBlurStyle {
            blurRadius(s.blur)
            noiseFactor(0.05f)
            backgroundColor(Bop.Pearl)
            colorEffects(listOf(HazeColorEffect.tint(tint.copy(alpha = alpha))))
            fallbackColorEffect(HazeColorEffect.tint(tint.copy(alpha = 0.86f)))
        }
    }
    m = m.clip(shape)
    m = if (state != null) m.hazeBlur(HazeInput.Sources(state), blur) else m.background(tint.copy(alpha = (alpha + 0.3f).coerceAtMost(0.92f)))
    return m.glassEdges(shape, if (rim) 1f else 0f, light = true)
}

/** Glass for controls over photos / colour washes: near-clear, strong lensing, brighter specular. */
@Composable
fun Modifier.mediaGlass(shape: Shape, state: HazeState?, emphasis: Float = 1f, interactionSource: InteractionSource? = null, strong: Boolean = false): Modifier {
    if (state != null && shape is RoundedCornerShape) {
        val level = LocalGlassLevel.current
        val light by LocalGlassLight.current
        val style = remember(level, light, shape, emphasis, strong) {
            liquidStyle(GlassLevel.CLEAR.takeIf { level != GlassLevel.FROSTED } ?: level, light, shape,
                if (level == GlassLevel.CLEAR) Color.Transparent else Color.White.copy(alpha = (0.07f * emphasis).coerceIn(0f, 0.5f)), strong = strong, onMedia = true)
        }
        return this.hazeGlass(HazeInput.Sources(state), style, expandLayerBounds = false, interactionSource = interactionSource)
    }
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
fun Modifier.glassLite(shape: Shape, tint: Color = Color.White, alpha: Float = 0.55f, shadow: Dp = 0.dp): Modifier {
    val clearWhite = LocalGlassLevel.current == GlassLevel.CLEAR && tint.luminanceIsLight() && alpha < 0.9f
    return (if (shadow > 0.dp && !clearWhite) this.softShadow(shape, shadow) else this)
        .clip(shape).background(tint.copy(alpha = if (clearWhite) 0f else alpha)).glassEdges(shape, 1f, light = tint.luminanceIsLight())
}

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
 * Soft drop shadow drawn only outside the shape. The Gaussian blur is rendered
 * ONCE into a quarter-resolution bitmap (a blur loses nothing at low res) and
 * cached until the element's size changes; each frame just blits it. Drawing a
 * BlurMaskFilter path every frame was the main cost during page transitions.
 */
fun Modifier.softShadow(shape: Shape, blur: Dp, color: Color = Color(0xFF3A4150), alpha: Float = 0.14f, dy: Dp = 3.dp): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val radius = (blur.toPx() * 0.6f).coerceAtLeast(1f)
    val offY = dy.toPx()
    val margin = radius * 2.4f + offY
    val scale = 0.25f
    val bw = ((size.width + 2 * margin) * scale).toInt().coerceAtLeast(1)
    val bh = ((size.height + 2 * margin) * scale).toInt().coerceAtLeast(1)
    val image = if (size.width < 1f || size.height < 1f) null else {
        val bmp = android.graphics.Bitmap.createBitmap(bw, bh, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.scale(scale, scale)
        c.translate(margin, margin + offY)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.copy(alpha = alpha).toArgb()
            maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawPath(path.asAndroidPath(), paint)
        bmp.asImageBitmap()
    }
    onDrawBehind {
        val img = image ?: return@onDrawBehind
        clipPath(path, ClipOp.Difference) {
            drawImage(
                img,
                srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height),
                dstOffset = IntOffset(-margin.toInt(), -margin.toInt()),
                dstSize = IntSize((size.width + 2 * margin).toInt(), (size.height + 2 * margin).toInt()),
                filterQuality = FilterQuality.Low,
            )
        }
    }
}
