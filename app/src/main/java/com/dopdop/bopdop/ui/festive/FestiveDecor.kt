package com.dopdop.bopdop.ui.festive

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dopdop.bopdop.data.Festival
import com.dopdop.bopdop.data.FestiveLevel
import com.dopdop.bopdop.data.FestivePick
import com.dopdop.bopdop.data.Festivals
import com.dopdop.bopdop.ui.glass.LocalReduceMotion
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin

/** The festival on now and how much of it to show. */
data class FestiveNow(val festival: Festival?, val level: FestiveLevel) {
    /** Home gets its decoration. */
    val decor: Boolean get() = festival != null && level == FestiveLevel.FULL
    /** Pets dress up (and the festival's own pet can be picked). */
}

val LocalFestive = staticCompositionLocalOf { FestiveNow(null, FestiveLevel.OFF) }

/** Today's festival for these settings; rechecked every hour so it arrives and leaves on its own. */
@Composable
fun rememberFestive(level: FestiveLevel, pick: FestivePick): FestiveNow {
    val ctx = LocalContext.current
    val country = remember { Festivals.country(ctx) }
    val today by produceState(LocalDate.now()) { while (true) { delay(3_600_000); value = LocalDate.now() } }
    return remember(level, pick, country, today) { FestiveNow(Festivals.current(level, pick, country, today), level) }
}

/** Saffron, marigold and leaf green for Navaratri; pumpkin for Halloween. */
object FestiveColors {
    val Saffron = Color(0xFFFF8F1F)
    val Marigold = Color(0xFFFFC53D)
    val Leaf = Color(0xFF3E8E41)
    val Pumpkin = Color(0xFFFF7A1A)
    fun accent(f: Festival) = if (f == Festival.NAVARATRI) Saffron else Pumpkin
}

/**
 * A light touch of the festival across the top of Home: a marigold toran swaying gently for
 * Navaratri, a corner cobweb with a dangling spider and a bat or two for Halloween. Drawn behind
 * the header, small and soft, so it decorates without getting in the way.
 */
@Composable
fun FestiveHomeDecor(festival: Festival, modifier: Modifier = Modifier) {
    // Still while reduced motion is on, or while Home is hidden under another tab or page.
    val reduce = LocalReduceMotion.current || !com.dopdop.bopdop.ui.LocalTabActive.current
    val t by produceState(0f, reduce) {
        if (reduce) return@produceState
        val start = withInfiniteAnimationFrameMillis { it }
        var last = 0L
        // Slow movement: 30 updates a second is plenty.
        while (true) withInfiniteAnimationFrameMillis { if (it - last >= 33) { last = it; value = (it - start) / 1000f } }
    }
    // Hung just under the status bar, so the clock and icons stay clear.
    val inset = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    val dussehra = remember { Festivals.isDussehra(LocalDate.now()) }
    Canvas(modifier) {
        when (festival) {
            Festival.NAVARATRI -> {
                toran(t, inset)
                // Fireworks over the toran now and then; Dussehra night gets more of them.
                fireworks(t, inset, if (dussehra) 3.5f else 8f)
            }
            Festival.HALLOWEEN -> cobweb(t, inset)
        }
    }
}

/**
 * Small bursts of saffron, gold and pink sparks below the toran, one at a time, every [every]
 * seconds, each in a different spot. Quiet enough to sit behind the header.
 */
private fun DrawScope.fireworks(t: Float, inset: Float, every: Float) {
    val u = 1.dp.toPx()
    val n = (t / every).toInt()
    val p = (t % every) / 1.6f
    if (p >= 1f) return
    // A fixed, varied spot per burst.
    val seed = (n * 7919) % 1000 / 1000f
    val c = Offset(size.width * (0.3f + 0.45f * seed), inset + (40f + 22f * ((n * 31) % 7) / 7f) * u)
    val colors = listOf(FestiveColors.Saffron, FestiveColors.Marigold, Color(0xFFFF5FA2), Color(0xFFFFF1B8))
    val reach = 20f * u * kotlin.math.sqrt(p)
    val fade = (1f - p).coerceIn(0f, 1f)
    for (i in 0 until 12) {
        val a = i / 12f * 2f * Math.PI.toFloat() + seed
        val drop = 6f * u * p * p
        val at = Offset(c.x + cos(a) * reach, c.y + sin(a) * reach + drop)
        val tailAt = Offset(c.x + cos(a) * reach * 0.7f, c.y + sin(a) * reach * 0.7f + drop * 0.7f)
        val col = colors[(i + n) % colors.size].copy(alpha = 0.85f * fade)
        drawLine(col, tailAt, at, 1.4f * u, StrokeCap.Round)
        drawCircle(col, 1.3f * u * (0.6f + 0.4f * fade), at)
    }
}

/** Crossed dandiya sticks for Navratri, a drawn bow and arrow for Dussehra, a pumpkin for Halloween. */
@Composable
fun FestiveMark(festival: Festival, modifier: Modifier = Modifier) {
    val dussehra = remember { Festivals.isDussehra(LocalDate.now()) }
    Canvas(modifier) {
        val u = size.minDimension / 16f
        when {
            festival == Festival.HALLOWEEN -> {
                val c = Offset(size.width / 2, size.height * 0.56f)
                drawOval(FestiveColors.Pumpkin, Offset(c.x - 7f * u, c.y - 5.5f * u), androidx.compose.ui.geometry.Size(14f * u, 11f * u))
                drawLine(FestiveColors.Leaf, Offset(c.x, c.y - 5f * u), Offset(c.x + 1.5f * u, c.y - 8f * u), 1.6f * u, StrokeCap.Round)
                drawLine(Color(0xFFB8560F), Offset(c.x, c.y - 4.5f * u), Offset(c.x, c.y + 5f * u), 0.8f * u)
            }
            dussehra -> {
                // Bow drawn, arrow nocked: the victory of Vijayadashami.
                val bow = Path().apply { moveTo(4f * u, 1f * u); quadraticTo(15f * u, 8f * u, 4f * u, 15f * u) }
                drawPath(bow, Color(0xFF8A4B1F), style = Stroke(1.6f * u, cap = StrokeCap.Round))
                drawLine(Color(0xFFB98D5A), Offset(4f * u, 1f * u), Offset(4f * u, 15f * u), 0.7f * u)
                drawLine(FestiveColors.Saffron, Offset(2f * u, 8f * u), Offset(15f * u, 8f * u), 1.1f * u, StrokeCap.Round)
                drawPath(Path().apply { moveTo(15.5f * u, 8f * u); lineTo(12.5f * u, 6.3f * u); lineTo(12.5f * u, 9.7f * u); close() }, FestiveColors.Saffron)
            }
            else -> {
                // Two dandiya sticks crossed, with ribbon bands.
                listOf(Offset(3f * u, 14f * u) to Offset(13f * u, 2f * u), Offset(3f * u, 2f * u) to Offset(13f * u, 14f * u)).forEachIndexed { i, (a, b) ->
                    drawLine(if (i == 0) FestiveColors.Saffron else Color(0xFFE0457B), a, b, 1.8f * u, StrokeCap.Round)
                    val m = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
                    drawCircle(FestiveColors.Marigold, 1.4f * u, Offset((a.x + m.x) / 2, (a.y + m.y) / 2))
                }
            }
        }
    }
}

/** Marigold garlands looping between three hooks, with mango leaves hanging from each hook. */
private fun DrawScope.toran(t: Float, inset: Float) {
    val u = 1.dp.toPx()
    val top = inset + 1f * u
    val hooks = listOf(0f, size.width / 2f, size.width)
    for (k in 0 until hooks.size - 1) {
        val a = hooks[k]; val b = hooks[k + 1]
        val sag = (16f + 2f * sin(t * 0.9f + k)) * u
        val n = 17
        for (i in 0..n) {
            val f = i / n.toFloat()
            val x = a + (b - a) * f
            val y = top + sag * 4f * f * (1f - f)
            val c = if (i % 2 == 0) FestiveColors.Saffron else FestiveColors.Marigold
            marigold(Offset(x, y), 3.4f * u, c)
        }
    }
    // Mango leaves: a little fan at each hook.
    hooks.forEach { hx ->
        for (j in -1..1) {
            val sway = 4f * sin(t * 1.3f + hx / 97f + j)
            rotate(j * 22f + sway, Offset(hx, top)) {
                val leaf = Path().apply {
                    moveTo(hx, top)
                    quadraticTo(hx + 4f * u, top + 7f * u, hx, top + 14f * u)
                    quadraticTo(hx - 4f * u, top + 7f * u, hx, top)
                    close()
                }
                drawPath(leaf, FestiveColors.Leaf.copy(alpha = 0.9f))
            }
        }
        marigold(Offset(hx, top + 1f * u), 4f * u, FestiveColors.Saffron)
    }
}

private fun DrawScope.marigold(c: Offset, r: Float, color: Color) {
    for (p in 0 until 8) {
        val a = p / 8f * 2f * Math.PI.toFloat()
        drawCircle(color, r * 0.45f, Offset(c.x + cos(a) * r * 0.55f, c.y + sin(a) * r * 0.55f))
    }
    drawCircle(color.copy(red = color.red * 0.85f, green = color.green * 0.7f), r * 0.42f, c)
}

/** A cobweb tucked into the top-right corner, a spider on a thread, and a bat that flits across now and then. */
private fun DrawScope.cobweb(t: Float, inset: Float) {
    val u = 1.dp.toPx()
    val web = Color.White.copy(alpha = 0.42f)
    val corner = Offset(size.width, inset)
    val reach = 92f * u
    val spokes = 6
    for (i in 0..spokes) {
        val a = Math.PI.toFloat() / 2f + i / spokes.toFloat() * Math.PI.toFloat() / 2f
        drawLine(web, corner, Offset(corner.x + cos(a) * reach, corner.y + sin(a) * reach), 0.8f * u)
    }
    for (ring in 1..4) {
        val r = reach * ring / 4.4f
        val p = Path()
        for (i in 0..spokes) {
            val a = Math.PI.toFloat() / 2f + i / spokes.toFloat() * Math.PI.toFloat() / 2f
            // Rings sag a little between spokes, like real silk.
            val pt = Offset(corner.x + cos(a) * r, corner.y + sin(a) * r)
            if (i == 0) p.moveTo(pt.x, pt.y) else {
                val mid = a - Math.PI.toFloat() / 4f / spokes
                p.quadraticTo(corner.x + cos(mid) * r * 0.9f, corner.y + sin(mid) * r * 0.9f, pt.x, pt.y)
            }
        }
        drawPath(p, web, style = Stroke(0.8f * u))
    }
    // Spider, bobbing on its thread.
    val sx = size.width - 104f * u
    val sy = inset + (40f + 8f * sin(t * 0.8f)) * u
    drawLine(web, Offset(sx, inset), Offset(sx, sy), 0.8f * u)
    val ink = Color(0xFF1E1A22)
    for (side in listOf(-1f, 1f)) for (l in 0..2) {
        val ly = sy - 1.5f * u + l * 1.6f * u
        drawLine(ink, Offset(sx, ly), Offset(sx + side * 5.5f * u, ly + (l - 1) * 2.4f * u), 0.9f * u, StrokeCap.Round)
    }
    drawCircle(ink, 3f * u, Offset(sx, sy))
    drawCircle(ink, 2f * u, Offset(sx, sy - 3.4f * u))
    // A bat crossing every ~14 s, high up and small.
    val cycle = (t % 14f) / 14f
    if (cycle < 0.55f) {
        val p = cycle / 0.55f
        val bx = -20f * u + (size.width * 0.75f) * p
        val by = inset + (14f + 8f * sin(p * 9f)) * u
        val flap = sin(t * 14f)
        bat(Offset(bx, by), 7f * u, flap, ink.copy(alpha = 0.8f))
    }
}

private fun DrawScope.bat(c: Offset, s: Float, flap: Float, color: Color) {
    val lift = flap * s * 0.6f
    val wing = Path().apply {
        moveTo(c.x, c.y)
        quadraticTo(c.x - s * 0.6f, c.y - s * 0.4f - lift, c.x - s * 1.4f, c.y - lift)
        quadraticTo(c.x - s * 0.9f, c.y + s * 0.1f, c.x - s * 0.7f, c.y + s * 0.35f)
        quadraticTo(c.x - s * 0.35f, c.y + s * 0.1f, c.x, c.y + s * 0.3f)
        quadraticTo(c.x + s * 0.35f, c.y + s * 0.1f, c.x + s * 0.7f, c.y + s * 0.35f)
        quadraticTo(c.x + s * 0.9f, c.y + s * 0.1f, c.x + s * 1.4f, c.y - lift)
        quadraticTo(c.x + s * 0.6f, c.y - s * 0.4f - lift, c.x, c.y)
        close()
    }
    drawPath(wing, color)
    drawCircle(color, s * 0.28f, Offset(c.x, c.y - s * 0.05f))
}
