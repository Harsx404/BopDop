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
import androidx.compose.ui.graphics.ColorFilter
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

/** The pack's fixed palette, so drawn touches sit in the same 15 colours as the artwork. */
private object Pal {
    val Night = Color(0xFF21162F)
    val Plum = Color(0xFF31223E)
    val Dusk = Color(0xFF49345F)
    val Violet = Color(0xFF75529B)
    val Lilac = Color(0xFFA788C8)
    val Mist = Color(0xFFD9C8E8)
    val Bone = Color(0xFFFFF5DE)
    val Ember = Color(0xFFF18A36)
    val Candle = Color(0xFFFFC45B)
}

/** Decoration atlas cells, row-major as in the expansion manifest. */
enum class HalloweenDecor { PUMPKIN, PUMPKIN_STACK, JACK_O_LANTERN, PUMPKIN_BOWL, CANDLE, CANDELABRA, HANGING_LANTERN, CAULDRON,
    POTION, SPELLBOOK, WITCH_HAT, BROOM, CANDY_CORN, WRAPPED_CANDY, MUSICAL_GRAVESTONE, BLACK_ROSE }

/** Every 64-pixel cell keeps six transparent pixels of padding around its art. */
private const val PAD = 6

/**
 * The whole-app night: the portrait backdrop (village at the foot, a quiet middle for content), the
 * graveyard strip in front of it, and a slow layer of moonglow, stars, drifting cloud, rising
 * embers, ground fog and the odd bat. The backdrop's art pixels set the grid everything snaps to.
 */
@Composable
fun HalloweenBackground(modifier: Modifier = Modifier) {
    val dark = Bop.dark
    val backdrop = ImageBitmap.imageResource(if (dark) R.drawable.halloween_backdrop_dark else R.drawable.halloween_backdrop_light)
    val grave = ImageBitmap.imageResource(R.drawable.halloween_graveyard)
    val props = ImageBitmap.imageResource(R.drawable.halloween_props)
    val bats = ImageBitmap.imageResource(R.drawable.halloween_bats)
    val fogFar = ImageBitmap.imageResource(R.drawable.halloween_fog_far)
    val fogNear = ImageBitmap.imageResource(R.drawable.halloween_fog_near)
    val time = rememberHalloweenTime(fps = 15)
    val inset = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    Box(modifier) {
        // Still artwork in its own layer, so the animated one above never re-records it.
        Canvas(Modifier.matchParentSize()) {
            drawRect(if (dark) Pal.Night else Color(0xFFF4E4C1))
            val g = grid(backdrop)
            drawImage(backdrop, dstOffset = IntOffset(g.left.roundToInt(), g.top.roundToInt()),
                dstSize = IntSize((backdrop.width * g.s).roundToInt(), (backdrop.height * g.s).roundToInt()), filterQuality = FilterQuality.None)
            val gw = grave.width * g.s
            drawImage(grave, dstOffset = IntOffset(((size.width - gw) / 2).roundToInt(), (size.height - grave.height * g.s).roundToInt()),
                dstSize = IntSize(gw.roundToInt(), (grave.height * g.s).roundToInt()),
                alpha = if (dark) 1f else 0.55f, filterQuality = FilterQuality.None)
        }
        Canvas(Modifier.matchParentSize()) {
            val t = time.value
            val g = grid(backdrop)
            // The full moon, top centre where pages keep no controls (the backdrops' own moon sat
            // under the top-left button, so it was painted out), with its glow breathing.
            val moonPx = max(1, 1.dp.toPx().roundToInt())
            val moon = Offset(snap(size.width * 0.43f, moonPx.toFloat()), snap(inset + 28.dp.toPx(), moonPx.toFloat()))
            val glowR = 64f * moonPx
            val breathe = 0.85f + 0.15f * sin(t * 0.6f)
            drawCircle(Brush.radialGradient(listOf((if (dark) Pal.Ember else Pal.Candle).copy(alpha = (if (dark) 0.26f else 0.34f) * breathe), Color.Transparent), moon, glowR), glowR, moon)
            sprite(props, 0, moon.x - 31.5f * moonPx, moon.y - 32f * moonPx, moonPx, if (dark) 0.95f else 0.85f)
            // Stars, and small cloud wisps drifting through the open strip under Home's buttons and
            // above the greeting, so they never sit behind a control. The light backdrop paints its own clouds.
            if (dark) {
                stars(t, g, moon, 30f * moonPx)
                val cloudPx = max(1, 0.7.dp.toPx().roundToInt())
                for (i in 0..1) {
                    val span = size.width + 64f * cloudPx * 2
                    val x = (t * (4f + i * 2.5f) * cloudPx + i * span * 0.5f) % span - 64f * cloudPx
                    val y = inset + (if (i == 0) 66 else 80).dp.toPx() - 18 * cloudPx
                    sprite(props, 1 + i * 2, snap(x, cloudPx.toFloat()), snap(y, cloudPx.toFloat()), cloudPx, 0.36f)
                }
            }
            embers(t, g)
            // Two dithered fog banks on the backdrop's grid, the near one drifting faster for depth.
            // Kept to the foot of the screen, where the village is, below where most text sits.
            fogBand(fogFar, t * 1.6f, size.height * 0.83f, g.s, if (dark) 0.34f else 0.32f)
            fogBand(fogNear, -t * 3.2f, size.height * 0.92f, g.s, if (dark) 0.5f else 0.4f)
            // A rim-lit bat crosses the upper sky every 26 s.
            val q = t % 26f / 26f
            if (q < 0.28f) {
                val p = q / 0.28f
                val px = max(1, 0.7.dp.toPx().roundToInt())
                sprite(bats, (t * 1000 / 140).toInt() % 4, size.width * 1.1f - (size.width * 1.3f) * p,
                    size.height * 0.24f + sin(p * 9f) * 18.dp.toPx() - p * size.height * 0.08f, px, cols = 2)
            }
        }
    }
}

/** Art-pixel grid of a backdrop covering the screen, anchored to the bottom so the village stays. */
private class Grid(val s: Float, val left: Float, val top: Float)

private fun DrawScope.grid(img: ImageBitmap): Grid {
    val s = max(size.width / img.width, size.height / img.height)
    return Grid(s, (size.width - img.width * s) / 2, size.height - img.height * s)
}

private fun snap(v: Float, step: Float) = floor(v / step) * step

/** Single art-pixel stars that twinkle out of step with each other, none in front of the moon. */
private fun DrawScope.stars(t: Float, g: Grid, moon: Offset, moonR: Float) {
    for (i in 0 until 26) {
        val ax = (i * 83 + 17) % 216
        val ay = 6 + (i * 47 + i * i * 3) % 150
        val p = Offset(g.left + ax * g.s, g.top + ay * g.s)
        if ((p - moon).getDistance() < moonR) continue
        val tw = sin(t * (0.7f + (i % 5) * 0.23f) + i * 2.1f).coerceAtLeast(0f)
        val a = 0.12f + 0.75f * tw * tw * tw
        val c = if (i % 7 == 0) Pal.Candle else Pal.Bone
        drawRect(c.copy(alpha = a), p, Size(g.s, g.s))
        // The brightest few open into a four-point glint at their peak.
        if (i % 4 == 0 && tw > 0.8f) for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1))
            drawRect(c.copy(alpha = a * 0.45f), Offset(p.x + dx * g.s, p.y + dy * g.s), Size(g.s, g.s))
    }
}

/** Embers drifting up from the village lights, moving a whole art pixel at a time. */
private fun DrawScope.embers(t: Float, g: Grid) {
    for (i in 0 until 10) {
        val period = 11f + (i % 4) * 2.5f
        val age = ((t + i * 3.7f) / period) % 1f
        val baseX = size.width * ((i * 0.137f + 0.06f) % 1f)
        val x = baseX + sin(age * 6.28f + i) * 10.dp.toPx()
        val y = size.height * (0.86f - 0.5f * age)
        val a = sin(age * 3.14159f).let { it * it } * 0.8f
        val c = if (i % 3 == 0) Pal.Candle else Pal.Ember
        val p = Offset(snap(x, g.s), snap(y, g.s))
        drawCircle(c.copy(alpha = a * 0.18f), g.s * 2.6f, Offset(p.x + g.s / 2, p.y + g.s / 2))
        drawRect(c.copy(alpha = a), p, Size(g.s, g.s))
    }
}

/** A seamless fog strip centred on [centerY], wrapped across the screen and scrolled [shift] art pixels. */
private fun DrawScope.fogBand(img: ImageBitmap, shift: Float, centerY: Float, s: Float, alpha: Float) {
    val w = img.width * s
    val h = img.height * s
    // Whole art pixels only, so the dither pattern never smears.
    val off = (floor(shift) % img.width + img.width) % img.width * s
    var x = -off
    val top = (centerY - h / 2).roundToInt()
    while (x < size.width) {
        drawImage(img, dstOffset = IntOffset(x.roundToInt(), top), dstSize = IntSize(w.roundToInt() + 1, h.roundToInt()),
            alpha = alpha, filterQuality = FilterQuality.None)
        x += w
    }
}

/**
 * Home's header: two jack-o'-lanterns hanging on cords between the buttons, a spider letting
 * itself down beside the search button, cobwebs in the top corners and a bat flitting past.
 * The moon, sky and village are the backdrop's; this only hangs things in front of them.
 */
@Composable
fun HalloweenHeaderScene(modifier: Modifier = Modifier) {
    val props = ImageBitmap.imageResource(R.drawable.halloween_props)
    val webs = ImageBitmap.imageResource(R.drawable.halloween_webs)
    val spider = ImageBitmap.imageResource(R.drawable.halloween_spider)
    val bats = ImageBitmap.imageResource(R.drawable.halloween_bats)
    val time = rememberHalloweenTime()
    val dark = Bop.dark
    val inset = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    Canvas(modifier) {
        val t = time.value
        val px = max(1, 0.7.dp.toPx().roundToInt())
        val cord = if (dark) Pal.Lilac.copy(alpha = 0.7f) else Pal.Dusk.copy(alpha = 0.6f)
        // Rounded cobwebs that follow the screen's own rounded corners, an intact one and a torn one.
        val web = if (dark) null else Pal.Violet
        val webAlpha = if (dark) 0.7f else 0.9f
        roundedWeb(webs, right = false, torn = false, 48.dp.toPx(), webAlpha, web)
        roundedWeb(webs, right = true, torn = true, 48.dp.toPx(), webAlpha, web)
        // Lanterns either side of the open gap between the menu and search buttons, swinging a
        // pixel or two, their glow flickering; the spider hangs between them.
        listOf(0.28f to 14f, 0.58f to 22f).forEachIndexed { i, (fx, drop) ->
            val hook = Offset(snap(size.width * fx, px.toFloat()), inset)
            val sway = (sin(t * 1.1f + i * 1.7f) * 2f).roundToInt() * px
            val ring = Offset(hook.x + sway, hook.y + drop.dp.toPx())
            drawLine(cord, hook, ring, px.toFloat())
            val frame = 8 + ((t * 7f).toInt() + i * 2) % 4
            val left = ring.x - 32 * px
            val top = ring.y - PAD * px
            val flicker = 0.75f + 0.25f * sin(t * 9f + i) * sin(t * 3.3f + i * 2f)
            val c = Offset(ring.x, top + 38 * px)
            drawCircle(Brush.radialGradient(listOf(Pal.Ember.copy(alpha = 0.35f * flicker), Color.Transparent), c, 30f * px), 30f * px, c)
            sprite(props, frame, left, top, px)
        }
        // The rim-lit spider lets itself down between the lanterns, bobbing on a thread from the bar.
        val spx = max(1, 0.6.dp.toPx().roundToInt())
        val bob = (sin(t * 0.5f) * 6f).roundToInt() * spx
        danglingSpider(spider, snap(size.width * 0.43f, px.toFloat()), inset, inset + 10.dp.toPx() + bob, spx, cord, t)
        // A bat darting across the header every 15 s.
        val q = t % 15f / 15f
        if (q < 0.35f) {
            val p = q / 0.35f
            sprite(bats, (t * 1000 / 140).toInt() % 4, -64f * px + (size.width + 128f * px) * p,
                inset + 46.dp.toPx() + sin(p * 14f) * 6.dp.toPx(), px, cols = 2)
        }
    }
}

/**
 * A rounded cobweb fitted to a card's top corner: its outer arc follows the card's [corner]
 * rounding, so it reads as spun onto the card. Cards take turns between corners and between the
 * intact and torn web ([variant]), and on the card in focus a spider dangles from it.
 * Cream over artwork in both themes. Draw it over the whole card.
 */
@Composable
fun HalloweenCardWeb(variant: Int, spider: Boolean, corner: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val webs = ImageBitmap.imageResource(R.drawable.halloween_webs)
    val sheet = ImageBitmap.imageResource(R.drawable.halloween_spider)
    val time = rememberHalloweenTime(spider, fps = 12)
    val right = variant % 2 == 0
    Canvas(modifier) {
        roundedWeb(webs, right, torn = variant % 4 >= 2, corner.toPx(), 0.95f)
        if (!spider) return@Canvas
        val t = time.value
        val spx = max(1, 0.6.dp.toPx().roundToInt())
        val sx = snap(if (right) size.width - 26.dp.toPx() else 26.dp.toPx(), spx.toFloat())
        val anchor = 14.dp.toPx()
        danglingSpider(sheet, sx, anchor, anchor + 12.dp.toPx() + (sin(t * 0.6f) * 5f).roundToInt() * spx, spx, Pal.Bone.copy(alpha = 0.8f), t)
    }
}

/**
 * One rounded cobweb in a top corner. Each 96-pixel cell's outer arc has a radius of about 72
 * pixels; the scale is the whole number that brings it closest to [radius], and the cell is placed
 * so the arc's straight ends lie along the top and side edges.
 */
private fun DrawScope.roundedWeb(webs: ImageBitmap, right: Boolean, torn: Boolean, radius: Float, alpha: Float, tint: Color? = null) {
    val px = max(1, (radius / 72f).roundToInt())
    // Art bounds within the cell: intact webs start a row lower than torn ones.
    val top = if (torn) 9 else 11
    val x = if (right) size.width - 84 * px else -12f * px
    val cell = (if (torn) 4 else 0) + if (right) 1 else 0
    drawImage(webs, srcOffset = IntOffset(cell % 4 * 96, cell / 4 * 96), srcSize = IntSize(96, 96),
        dstOffset = IntOffset(x.roundToInt(), -top * px), dstSize = IntSize(96 * px, 96 * px),
        alpha = alpha, colorFilter = tint?.let { ColorFilter.tint(it) }, filterQuality = FilterQuality.None)
}

/** The rim-lit spider (8 frames, 120 ms) on a silk thread from [anchorY] down to its back. */
private fun DrawScope.danglingSpider(sheet: ImageBitmap, x: Float, anchorY: Float, top: Float, px: Int, silk: Color, t: Float) {
    // Every frame's art starts 17 pixels down, at the top of the abdomen.
    drawLine(silk, Offset(x, anchorY), Offset(x, top + 18 * px), px.toFloat())
    sprite(sheet, (t * 1000 / 120).toInt() % 8, x - 32.5f * px, top, px)
}

/**
 * Now Playing: the haunted village rises out of the bottom of the cover, so the artwork melts
 * into rooftops and lit windows instead of a flat wash, with a few windows flickering. Its ground
 * colour is the wash below it, so the two meet without a seam.
 */
@Composable
fun HalloweenPlayerHorizon(modifier: Modifier = Modifier) {
    val roofs = ImageBitmap.imageResource(R.drawable.halloween_rooftops)
    val time = rememberHalloweenTime(fps = 12)
    Canvas(modifier) {
        val s = size.width / roofs.width
        val h = roofs.height * s
        val top = size.height - h
        // Fade the art into the village's sky first, so the silhouettes have something to stand on.
        drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Pal.Plum.copy(alpha = 0.92f), startY = top - h * 0.2f, endY = size.height),
            Offset(0f, top - h * 0.2f), Size(size.width, h * 1.2f))
        drawImage(roofs, dstOffset = IntOffset(0, top.roundToInt()), dstSize = IntSize(size.width.roundToInt(), h.roundToInt()), filterQuality = FilterQuality.None)
        val t = time.value
        // Warm light spilling from a handful of windows, each candle guttering on its own beat.
        for ((i, w) in listOf(Offset(41f, 70f), Offset(117f, 80f), Offset(187f, 80f), Offset(242f, 72f), Offset(267f, 74f)).withIndex()) {
            val f = 0.6f + 0.4f * sin(t * (2.1f + i * 0.4f) + i) * sin(t * 1.3f + i * 2f)
            val c = Offset(w.x * s, top + w.y * s)
            drawCircle(Brush.radialGradient(listOf(Pal.Candle.copy(alpha = 0.28f * f), Color.Transparent), c, 9f * s), 9f * s, c)
        }
    }
}

/** A pumpkin, candle, cauldron or other prop from the decorations sheet, at whole-pixel scale. */
@Composable
fun HalloweenDecorIcon(decor: HalloweenDecor, description: String?, modifier: Modifier = Modifier) {
    val atlas = ImageBitmap.imageResource(R.drawable.halloween_decor)
    Canvas(modifier.then(if (description == null) Modifier else Modifier.semantics { contentDescription = description })) {
        val art = 64 - 2 * PAD
        val px = floor(size.minDimension / art).coerceAtLeast(1f).toInt()
        val left = ((size.width - art * px) / 2).roundToInt()
        val top = ((size.height - art * px) / 2).roundToInt()
        val cell = decor.ordinal
        drawImage(atlas, srcOffset = IntOffset(cell % 4 * 64 + PAD, cell / 4 * 64 + PAD), srcSize = IntSize(art, art),
            dstOffset = IntOffset(left, top), dstSize = IntSize(art * px, art * px), filterQuality = FilterQuality.None)
    }
}

/** Ember motes circling the play button while music plays. */
@Composable
fun HalloweenPlayMotes(playing: Boolean, modifier: Modifier = Modifier) {
    if (!playing) return
    val time = rememberHalloweenTime(fps = 15)
    Canvas(modifier) {
        val u = 1.dp.toPx().roundToInt().coerceAtLeast(1).toFloat()
        for (i in 0..5) {
            val age = (time.value * 0.28f + i / 6f) % 1f
            val a = i * 1.0472f + time.value * 0.15f
            val r = size.minDimension * (0.38f + 0.08f * age)
            val at = Offset(floor((center.x + cos(a) * r) / u) * u, floor((center.y + sin(a) * r) / u) * u)
            drawRect(Pal.Candle.copy(alpha = (1f - age) * 0.7f), at, Size(u, 2 * u))
        }
    }
}

/**
 * Candy, candy corn and embers bursting out of the like button: six 90 ms frames, played once
 * each time [bursts] goes up.
 */
@Composable
fun HalloweenLikeBurst(bursts: Int, modifier: Modifier = Modifier) {
    val sheet = ImageBitmap.imageResource(R.drawable.halloween_like_burst)
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

/** A bubbling cauldron (six 140 ms frames) for loading, at the largest whole-pixel size that fits. */
@Composable
fun HalloweenCauldron(modifier: Modifier = Modifier) {
    val sheet = ImageBitmap.imageResource(R.drawable.halloween_cauldron)
    val time = rememberHalloweenTime(fps = 12)
    Canvas(modifier.then(Modifier.semantics { contentDescription = "Loading" })) {
        val px = floor(size.minDimension / 64f).coerceAtLeast(1f).toInt()
        val frame = (time.value * 1000 / 140).toInt() % 6
        // Bottom-anchored frames: keep the feet on the same line.
        sprite(sheet, frame, (size.width - 64 * px) / 2, size.height - 60f * px, px, cols = 3)
    }
}

/** Wisp the ghost by a musical gravestone, for empty and error states. */
@Composable
fun HalloweenEmptyArt(modifier: Modifier = Modifier) {
    val art = ImageBitmap.imageResource(R.drawable.halloween_ghost)
    Canvas(modifier) {
        // Trimmed to the art's bounds (8..120 x 12..84) so it centres on what is actually drawn.
        val w = 112; val h = 72
        val px = floor(minOf(size.width / w, size.height / h)).coerceAtLeast(1f).toInt()
        drawImage(art, srcOffset = IntOffset(8, 12), srcSize = IntSize(w, h),
            dstOffset = IntOffset(((size.width - w * px) / 2).roundToInt(), ((size.height - h * px) / 2).roundToInt()),
            dstSize = IntSize(w * px, h * px), filterQuality = FilterQuality.None)
    }
}

/** One square atlas cell at a whole-pixel [scale], optionally recoloured to a flat [tint]. */
private fun DrawScope.sprite(atlas: ImageBitmap, frame: Int, x: Float, y: Float, scale: Int,
                             alpha: Float = 1f, tint: Color? = null, cell: Int = 64, cols: Int = 4) {
    drawImage(atlas, srcOffset = IntOffset(frame % cols * cell, frame / cols * cell),
        srcSize = IntSize(cell, cell), dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
        dstSize = IntSize(cell * scale, cell * scale), alpha = alpha.coerceIn(0f, 1f),
        colorFilter = tint?.let { ColorFilter.tint(it) }, filterQuality = FilterQuality.None)
}
