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
    val costume: Festival? get() = festival.takeIf { level != FestiveLevel.OFF }
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
    Canvas(modifier) {
        when (festival) {
            Festival.NAVARATRI -> toran(t, inset)
            Festival.HALLOWEEN -> cobweb(t, inset)
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
