package com.dopdop.bopdop.ui.festive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.dopdop.bopdop.R
import com.dopdop.bopdop.ui.theme.Bop
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** The Diwali pack's fixed palette, so drawn touches sit in the same 15 colours as the artwork. */
internal object Diya {
    val Night = Color(0xFF17152D)
    val Plum = Color(0xFF292147)
    val Dusk = Color(0xFF48345E)
    val Rose = Color(0xFFB7678C)
    val Blush = Color(0xFFF0B4B7)
    val Magenta = Color(0xFFC64269)
    val Rust = Color(0xFF9E421E)
    val Saffron = Color(0xFFD86A24)
    val Marigold = Color(0xFFF4A23A)
    val Gold = Color(0xFFFFD166)
    val Cream = Color(0xFFFFF1C9)
}

/** Decoration atlas cells, row-major as in the pack manifest. */
enum class DiwaliDecor { UNLIT_DIYA, LIT_DIYA, DIYA_TRIO, AKASH_KANDIL, MARIGOLD_TORAN, FLOWER_GARLAND, BRASS_FLOWER_BOWL, LOTUS,
    LADOO_BOWL, BARFI_PLATE, JALEBI_PLATE, GIFT, BRASS_BELL, FLOWER_BASKET, SPARKLER, PAPER_LANTERN }

/** The four empty-state pictures, row-major. */
enum class DiwaliEmpty { LIBRARY, SEARCH, ERROR, DOWNLOADS }

/** Every 64-pixel decoration cell keeps eight transparent pixels of padding around its art. */
private const val DECOR_PAD = 8

/** Ambient sheet rows: each is its own four-frame loop. */
internal const val ROW_DIYA = 0
internal const val ROW_KANDIL = 4
internal const val ROW_FIREFLY = 12

/** Hanging diyas on the dark backdrop's garlands, in art pixels. */
private val GARLAND_LAMPS = listOf(Offset(40f, 31f), Offset(31f, 54f), Offset(23f, 73f), Offset(175f, 32f), Offset(184f, 55f), Offset(192f, 73f))
/** Lit windows and ghat lamps of the riverside town at the foot of the dark backdrop. */
private val TOWN_LIGHTS = listOf(Offset(3f, 333f), Offset(22f, 330f), Offset(55f, 333f), Offset(70f, 334f), Offset(122f, 334f),
    Offset(135f, 333f), Offset(148f, 336f), Offset(180f, 333f), Offset(201f, 318f), Offset(213f, 318f))
/** The two diyas on the light backdrop's steps. */
private val STEP_DIYAS = listOf(Offset(29f, 353f), Offset(186f, 353f))

/**
 * The whole-app Diwali night: the portrait backdrop (marigold strings and hanging diyas at the top,
 * the lamp-lit riverside town at the foot, a quiet middle for content), with a slow layer over it
 * of breathing lamp glow, twinkling stars, sky lanterns drifting up from the ghats, light dancing
 * on the river and marigold petals tumbling down. Everything moves on the backdrop's pixel grid.
 */
@Composable
fun DiwaliBackground(modifier: Modifier = Modifier, dark: Boolean = Bop.dark) {
    val backdrop = ImageBitmap.imageResource(if (dark) R.drawable.diwali_backdrop_dark else R.drawable.diwali_backdrop_light)
    val time = rememberFestiveTime(fps = 15)
    Box(modifier) {
        // Still artwork in its own layer, so the animated one above never re-records it.
        Canvas(Modifier.matchParentSize()) {
            drawRect(if (dark) Diya.Night else Diya.Cream)
            val g = grid(backdrop)
            drawImage(backdrop, dstOffset = IntOffset(g.left.roundToInt(), g.top.roundToInt()),
                dstSize = IntSize((backdrop.width * g.s).roundToInt(), (backdrop.height * g.s).roundToInt()), filterQuality = FilterQuality.None)
        }
        Canvas(Modifier.matchParentSize()) {
            val t = time.value
            val g = grid(backdrop)
            if (dark) {
                diwaliStars(t, g)
                lampGlow(t, g, GARLAND_LAMPS, 9f, 0.34f)
                lampGlow(t, g, TOWN_LIGHTS, 6f, 0.26f)
                skyLanterns(t, g)
                riverLights(t, g)
            } else {
                lampGlow(t, g, STEP_DIYAS, 12f, 0.4f)
            }
            petals(t, g, if (dark) 1f else 0.85f)
        }
    }
}

private fun DrawScope.at(g: Grid, p: Offset) = Offset(g.left + p.x * g.s, g.top + p.y * g.s)

/** Warm light breathing around each lamp, every flame guttering on its own beat. */
private fun DrawScope.lampGlow(t: Float, g: Grid, lamps: List<Offset>, radius: Float, strength: Float) {
    lamps.forEachIndexed { i, l ->
        val f = 0.7f + 0.3f * sin(t * (2.3f + i * 0.37f) + i * 1.9f) * sin(t * 1.1f + i)
        val c = at(g, l)
        val r = radius * g.s
        drawCircle(Brush.radialGradient(listOf(Diya.Gold.copy(alpha = strength * f), Diya.Gold.copy(alpha = 0f)), c, r), r, c)
    }
}

/** Single art-pixel stars in the open sky between the garlands, twinkling out of step. */
private fun DrawScope.diwaliStars(t: Float, g: Grid) {
    for (i in 0 until 24) {
        val ax = 44 + (i * 83 + 17) % 128
        val ay = 18 + (i * 47 + i * i * 3) % 250
        val p = at(g, Offset(ax.toFloat(), ay.toFloat()))
        val tw = sin(t * (0.6f + (i % 5) * 0.21f) + i * 2.3f).coerceAtLeast(0f)
        val a = 0.1f + 0.7f * tw * tw * tw
        val c = if (i % 6 == 0) Diya.Gold else Diya.Cream
        drawRect(c.copy(alpha = a), p, Size(g.s, g.s))
        if (i % 5 == 0 && tw > 0.85f) for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1))
            drawRect(c.copy(alpha = a * 0.4f), Offset(p.x + dx * g.s, p.y + dy * g.s), Size(g.s, g.s))
    }
}

/**
 * Paper sky lanterns rising from the ghats, a few at a time: each a little lit box two art pixels
 * wide, swaying as it climbs, fading in over the town and out high in the sky.
 */
private fun DrawScope.skyLanterns(t: Float, g: Grid) {
    for (i in 0 until 4) {
        val period = 46f + i * 9f
        val age = ((t + i * 13f) / period) % 1f
        val ax = 40f + ((i * 53) % 130) + sin(age * 9f + i) * 6f
        val ay = 320f - 270f * age
        val fade = (sin(age * 3.14159f) * 1.4f).coerceIn(0f, 1f)
        val p = Offset(snap(g.left + ax * g.s, g.s), snap(g.top + ay * g.s, g.s))
        val c = Offset(p.x + g.s, p.y + 1.5f * g.s)
        drawCircle(Brush.radialGradient(listOf(Diya.Marigold.copy(alpha = 0.35f * fade), Diya.Marigold.copy(alpha = 0f)), c, 5f * g.s), 5f * g.s, c)
        drawRect(Diya.Gold.copy(alpha = fade), p, Size(2 * g.s, g.s))
        drawRect(Diya.Marigold.copy(alpha = fade), Offset(p.x, p.y + g.s), Size(2 * g.s, g.s))
        drawRect(Diya.Saffron.copy(alpha = fade), Offset(p.x, p.y + 2 * g.s), Size(2 * g.s, g.s))
    }
}

/** Lamplight dancing on the river: short gold dashes under the town, each blinking on its own. */
private fun DrawScope.riverLights(t: Float, g: Grid) {
    TOWN_LIGHTS.forEachIndexed { i, l ->
        for (k in 0..2) {
            val on = sin(t * (1.7f + k * 0.6f) + i * 2.1f + k * 1.3f)
            if (on < 0.2f) continue
            val y = 343f + k * 7f + (i % 3)
            val w = (3 - k).coerceAtLeast(1)
            val drift = (sin(t * 0.8f + i + k) * 1.5f).roundToInt()
            drawRect((if (k == 0) Diya.Gold else Diya.Marigold).copy(alpha = 0.55f * on),
                at(g, Offset(l.x - w / 2 + drift, y)), Size(w * g.s, g.s))
        }
    }
}

/**
 * Marigold and rose petals tumbling down from the garlands, two art pixels each and turning as
 * they fall (flat, then edge on), swaying side to side.
 */
private fun DrawScope.petals(t: Float, g: Grid, alpha: Float, count: Int = 11, top: Float = 30f, bottom: Float = 384f) {
    for (i in 0 until count) {
        val period = 15f + (i % 5) * 3.3f
        val age = ((t + i * 4.1f) / period) % 1f
        // Most petals come off the garlands at the sides; a few drift across the middle.
        val startX = when (i % 3) { 0 -> 14f + (i * 7) % 40; 1 -> 162f + (i * 11) % 40; else -> 50f + (i * 37) % 116 }
        val x = startX + sin(age * 11f + i) * 9f + age * (if (i % 2 == 0) 14f else -14f)
        val y = top + (bottom - top) * age
        val fade = (1f - age).coerceIn(0f, 1f).let { if (age < 0.05f) age / 0.05f else it } * alpha
        val c = when (i % 4) { 0, 2 -> Diya.Marigold; 1 -> Diya.Saffron; else -> Diya.Rose }
        val p = Offset(snap(g.left + x * g.s, g.s), snap(g.top + y * g.s, g.s))
        val flat = sin(t * 3f + i * 1.7f) > 0f
        drawRect(c.copy(alpha = fade), p, if (flat) Size(2 * g.s, g.s) else Size(g.s, 2 * g.s))
    }
}

/**
 * Home's header: two akash kandils hanging on cords in the gap between the buttons, swaying and
 * glowing, with fireworks blooming one after another in the sky behind them. The garlands,
 * hanging diyas and town are the backdrop's; this only hangs things in front of them.
 */
@Composable
fun DiwaliHeaderScene(modifier: Modifier = Modifier) {
    val ambient = ImageBitmap.imageResource(R.drawable.diwali_ambient)
    val bloom = ImageBitmap.imageResource(R.drawable.diwali_fireworks)
    val time = rememberFestiveTime()
    val dark = Bop.dark
    val inset = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    Canvas(modifier) {
        val t = time.value
        val px = max(1, 0.9.dp.toPx().roundToInt())
        // Two bursts staggered half a cycle apart, each in its own spot, behind the kandils.
        val fpx = max(1, 0.55.dp.toPx().roundToInt())
        val every = 6.5f
        for (k in 0..1) {
            val tt = t + k * every / 2f
            val n = (tt / every).toInt()
            val age = tt % every
            if (age >= 0.8f) continue
            val seed = ((n * 2 + k) * 7919 % 1000) / 1000f
            val cx = size.width * (0.16f + 0.68f * seed)
            val cy = inset + (40f + 30f * ((n * 31 + k * 17) % 7) / 7f).dp.toPx()
            sprite(bloom, (age / 0.1f).toInt().coerceAtMost(7), snap(cx - 48f * fpx, fpx.toFloat()), snap(cy - 48f * fpx, fpx.toFloat()),
                fpx, if (dark) 0.95f else 0.8f, cell = 96)
        }
        val cord = if (dark) Diya.Gold.copy(alpha = 0.55f) else Diya.Rust.copy(alpha = 0.55f)
        // Hung from the very top edge of the phone, cords running down past the status bar (the clock
        // and icons sit clear of them at the sides), so they swing just over the buttons.
        listOf(0.29f to 2f, 0.59f to 10f).forEachIndexed { i, (fx, drop) ->
            val hook = Offset(snap(size.width * fx, px.toFloat()), 0f)
            val sway = (sin(t * 1.0f + i * 1.9f) * 2f).roundToInt() * px
            val ring = Offset(hook.x + sway, inset * 0.45f + drop.dp.toPx())
            drawLine(cord, hook, ring, px.toFloat())
            // The kandil's art starts eight pixels down its cell, centred.
            val left = ring.x - 32 * px
            val top = ring.y - 8 * px
            val flicker = 0.75f + 0.25f * sin(t * 8f + i) * sin(t * 2.9f + i * 2f)
            val c = Offset(ring.x, top + 26 * px)
            drawCircle(Brush.radialGradient(listOf(Diya.Marigold.copy(alpha = 0.38f * flicker), Diya.Marigold.copy(alpha = 0f)), c, 30f * px), 30f * px, c)
            sprite(ambient, ROW_KANDIL + ((t * 1000 / 180).toInt() + i * 2) % 4, left, top, px)
        }
    }
}

/**
 * Now Playing: the lamp-lit rooftops rise out of the bottom of the cover, so the artwork melts into
 * the festival town instead of a flat wash, with its string lights twinkling. Its ground colour is
 * the wash below it, so the two meet without a seam.
 */
@Composable
fun DiwaliPlayerHorizon(modifier: Modifier = Modifier) {
    val roofs = ImageBitmap.imageResource(R.drawable.diwali_skyline)
    val time = rememberFestiveTime(fps = 12)
    Canvas(modifier) {
        val s = size.width / roofs.width
        val h = roofs.height * s
        val top = size.height - h
        drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Diya.Plum.copy(alpha = 0.92f), startY = top - h * 0.2f, endY = size.height),
            Offset(0f, top - h * 0.2f), Size(size.width, h * 1.2f))
        drawImage(roofs, dstOffset = IntOffset(0, top.roundToInt()), dstSize = IntSize(size.width.roundToInt(), h.roundToInt()), filterQuality = FilterQuality.None)
        val t = time.value
        for ((i, w) in listOf(Offset(20f, 42f), Offset(62f, 74f), Offset(96f, 79f), Offset(124f, 77f), Offset(185f, 66f),
            Offset(196f, 73f), Offset(228f, 73f), Offset(244f, 67f)).withIndex()) {
            val f = 0.55f + 0.45f * sin(t * (1.9f + i * 0.41f) + i) * sin(t * 1.2f + i * 2f)
            val c = Offset(w.x * s, top + w.y * s)
            drawCircle(Brush.radialGradient(listOf(Diya.Gold.copy(alpha = 0.3f * f), Diya.Gold.copy(alpha = 0f)), c, 8f * s), 8f * s, c)
        }
    }
}

/** Phuljhari sparks crackling around the play button while music plays. */
@Composable
fun DiwaliPlayMotes(playing: Boolean, modifier: Modifier = Modifier) {
    if (!playing) return
    val time = rememberFestiveTime(fps = 15)
    Canvas(modifier) {
        val u = 1.dp.toPx().roundToInt().coerceAtLeast(1).toFloat()
        for (i in 0..7) {
            val age = (time.value * 0.35f + i / 8f) % 1f
            val a = i * 0.785f + time.value * 0.2f
            val r = size.minDimension * (0.36f + 0.12f * age)
            val at = Offset(floor((center.x + cos(a) * r) / u) * u, floor((center.y + sin(a) * r) / u) * u)
            val c = (if (i % 3 == 0) Diya.Cream else if (i % 3 == 1) Diya.Gold else Diya.Marigold).copy(alpha = (1f - age) * 0.8f)
            // A tiny four-point spark, its arms shrinking as it fades.
            drawRect(c, at, Size(u, u))
            if (age < 0.5f) for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) drawRect(c.copy(alpha = c.alpha * 0.5f), Offset(at.x + dx * u, at.y + dy * u), Size(u, u))
        }
    }
}

/**
 * Marigold petals and embers bursting out of the like button: six 90 ms frames, played once each
 * time [bursts] goes up.
 */
@Composable
fun DiwaliLikeBurst(bursts: Int, modifier: Modifier = Modifier) {
    val sheet = ImageBitmap.imageResource(R.drawable.diwali_like_burst)
    val progress = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(bursts) {
        if (bursts == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, androidx.compose.animation.core.tween(6 * 90, easing = androidx.compose.animation.core.LinearEasing))
    }
    Canvas(modifier) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        val px = floor(size.minDimension / 64f).coerceAtLeast(1f).toInt()
        sprite(sheet, (p * 6).toInt().coerceAtMost(5), (size.width - 64 * px) / 2, (size.height - 64 * px) / 2, px, cols = 3)
    }
}

/** A diya being lit and catching flame (six 140 ms frames) for loading, at the largest whole-pixel size that fits. */
@Composable
fun DiwaliLoader(modifier: Modifier = Modifier) {
    val sheet = ImageBitmap.imageResource(R.drawable.diwali_loader)
    val time = rememberFestiveTime(fps = 12)
    Canvas(modifier.then(Modifier.semantics { contentDescription = "Loading" })) {
        val px = floor(size.minDimension / 64f).coerceAtLeast(1f).toInt()
        val frame = (time.value * 1000 / 140).toInt() % 6
        val c = Offset(size.width / 2, size.height - 34f * px)
        drawCircle(Brush.radialGradient(listOf(Diya.Gold.copy(alpha = 0.25f), Diya.Gold.copy(alpha = 0f)), c, 30f * px), 30f * px, c)
        // Bottom-anchored frames: keep the bowl on the same line.
        sprite(sheet, frame, (size.width - 64 * px) / 2, size.height - 60f * px, px, cols = 3)
    }
}

/** Jyoti, Gajju or Tara with a little scene for empty, no-results, error and ready states. */
@Composable
fun DiwaliEmptyArt(kind: DiwaliEmpty = DiwaliEmpty.LIBRARY, modifier: Modifier = Modifier) {
    val art = ImageBitmap.imageResource(R.drawable.diwali_empty)
    Canvas(modifier) {
        // Trimmed to the art's bounds within each 128 x 96 cell (8..120 x 8..88).
        val w = 112; val h = 80
        val px = floor(minOf(size.width / w, size.height / h)).coerceAtLeast(1f).toInt()
        val cell = kind.ordinal
        drawImage(art, srcOffset = IntOffset(cell % 2 * 128 + 8, cell / 2 * 96 + 8), srcSize = IntSize(w, h),
            dstOffset = IntOffset(((size.width - w * px) / 2).roundToInt(), ((size.height - h * px) / 2).roundToInt()),
            dstSize = IntSize(w * px, h * px), filterQuality = FilterQuality.None)
    }
}

/** A diya, kandil, sweets or other piece from the decorations sheet, at whole-pixel scale. */
@Composable
fun DiwaliDecorIcon(decor: DiwaliDecor, description: String?, modifier: Modifier = Modifier) {
    val atlas = ImageBitmap.imageResource(R.drawable.diwali_decor)
    Canvas(modifier.then(if (description == null) Modifier else Modifier.semantics { contentDescription = description })) {
        val art = 64 - 2 * DECOR_PAD
        val px = floor(size.minDimension / art).coerceAtLeast(1f).toInt()
        val left = ((size.width - art * px) / 2).roundToInt()
        val top = ((size.height - art * px) / 2).roundToInt()
        val cell = decor.ordinal
        drawImage(atlas, srcOffset = IntOffset(cell % 4 * 64 + DECOR_PAD, cell / 4 * 64 + DECOR_PAD), srcSize = IntSize(art, art),
            dstOffset = IntOffset(left, top), dstSize = IntSize(art * px, art * px), filterQuality = FilterQuality.None)
    }
}
