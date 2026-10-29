package com.dopdop.bopdop.ui.festive

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
data class FestiveNow(val festival: Festival?, val level: FestiveLevel, val today: LocalDate = LocalDate.now(), val preview: Boolean = false) {
    /** Home gets its decoration. */
    val decor: Boolean get() = festival != null && level == FestiveLevel.FULL
}

val LocalFestive = staticCompositionLocalOf { FestiveNow(null, FestiveLevel.OFF) }

/** Refresh at date/time changes and on resume, including an app left open across midnight. */
@Composable
fun rememberFestive(level: FestiveLevel, pick: FestivePick, halloweenPreview: Boolean = false, diwaliPreview: Boolean = false): FestiveNow {
    val ctx = LocalContext.current
    var country by remember { mutableStateOf(Festivals.country(ctx)) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(ctx, lifecycle) {
        fun refresh() { today = LocalDate.now(); country = Festivals.country(ctx) }
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) = refresh()
        }
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_DATE_CHANGED)
            addAction(android.content.Intent.ACTION_TIME_CHANGED)
            addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED)
        }
        androidx.core.content.ContextCompat.registerReceiver(ctx, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh() }
        lifecycle.addObserver(observer)
        onDispose { ctx.unregisterReceiver(receiver); lifecycle.removeObserver(observer) }
    }
    return remember(level, pick, country, today, halloweenPreview, diwaliPreview) {
        FestiveNow(Festivals.current(level, pick, country, today, halloweenPreview, diwaliPreview), level, today, halloweenPreview || diwaliPreview)
    }
}

/** Saffron, marigold and leaf for Navaratri; pumpkin and witch purple for Halloween; diya gold for Diwali; dawn for Chhath. */
object FestiveColors {
    val Saffron = Color(0xFFFF8F1F)
    val Marigold = Color(0xFFFFC53D)
    val Leaf = Color(0xFF3E8E41)
    val Pumpkin = Color(0xFFFF7A1A)
    val Witch = Color(0xFFA77BFF)
    val Gold = Color(0xFFFFD166)
    val Dawn = Color(0xFFFF9E5E)
    fun accent(f: Festival) = when (f) {
        Festival.NAVARATRI -> Saffron
        Festival.HALLOWEEN -> com.dopdop.bopdop.ui.theme.Bop.Accent
        Festival.DIWALI -> com.dopdop.bopdop.ui.theme.Bop.Accent
        Festival.CHHATH -> Dawn
    }
}

/**
 * A light touch of the festival across the top of Home: a marigold toran swaying gently for
 * Navaratri, a corner cobweb with a dangling spider and a bat or two for Halloween, swaying akash
 * kandils and blooming fireworks for Diwali. Drawn behind the header, small and soft, so it
 * decorates without getting in the way.
 */
@Composable
fun FestiveHomeDecor(festival: Festival, modifier: Modifier = Modifier) {
    if (festival == Festival.HALLOWEEN) { HalloweenHeaderScene(modifier); return }
    if (festival == Festival.DIWALI) { DiwaliHeaderScene(modifier); return }
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
    val dussehra = Festivals.isDussehra(LocalFestive.current.today)
    Canvas(modifier) {
        when (festival) {
            Festival.NAVARATRI -> {
                toran(t, inset)
                // Fireworks over the toran now and then; Dussehra night gets more of them.
                fireworks(t, inset, if (dussehra) 3.5f else 8f)
            }
            Festival.HALLOWEEN -> halloween(t, inset)
            Festival.DIWALI -> Unit
            Festival.CHHATH -> sunrise(t, inset)
        }
    }
}

/**
 * Small bursts of saffron, gold and pink sparks below the toran, one at a time, every [every]
 * seconds, each in a different spot. Quiet enough to sit behind the header.
 */
private fun DrawScope.fireworks(
    t: Float, inset: Float, every: Float,
    colors: List<Color> = listOf(FestiveColors.Saffron, FestiveColors.Marigold, Color(0xFFFF5FA2), Color(0xFFFFF1B8)),
) {
    val u = 1.dp.toPx()
    val n = (t / every).toInt()
    val p = (t % every) / 1.6f
    if (p >= 1f) return
    // A fixed, varied spot per burst.
    val seed = (n * 7919) % 1000 / 1000f
    val c = Offset(size.width * (0.3f + 0.45f * seed), inset + (40f + 22f * ((n * 31) % 7) / 7f) * u)
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

/** Crossed dandiya sticks for Navratri, a drawn bow and arrow for Dussehra, a pumpkin for Halloween, a lit diya for Diwali. */
@Composable
fun FestiveMark(festival: Festival, modifier: Modifier = Modifier) {
    if (festival == Festival.HALLOWEEN) {
        HalloweenDecorIcon(HalloweenDecor.JACK_O_LANTERN, null, modifier)
        return
    }
    if (festival == Festival.DIWALI) {
        DiwaliDecorIcon(DiwaliDecor.LIT_DIYA, null, modifier)
        return
    }
    val dussehra = Festivals.isDussehra(LocalFestive.current.today)
    Canvas(modifier) {
        val u = size.minDimension / 16f
        when {
            festival == Festival.CHHATH -> {
                // The sun on the water, as it is offered arghya.
                drawCircle(FestiveColors.Dawn, 4.5f * u, Offset(8f * u, 8f * u))
                for (k in 0..2) drawLine(Color(0xFF6FB7FF), Offset((3f + k) * u, (11.5f + k * 1.6f) * u), Offset((13f - k) * u, (11.5f + k * 1.6f) * u), 0.9f * u, StrokeCap.Round)
            }
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

/**
 * Halloween across the top: a garland of glowing jack-o'-lanterns and little bats swaying under the
 * status bar, a spider lowering itself on a silk thread in the gap between the buttons, and a bat
 * flitting past now and then. Bright on dark so it reads in both themes.
 */
private fun DrawScope.halloween(t: Float, inset: Float) {
    val u = 1.dp.toPx()
    val top = inset + 1f * u
    val silk = Color(0xFFE9E2FF).copy(alpha = 0.7f)
    val hooks = listOf(0f, size.width / 2f, size.width)
    for (k in 0 until hooks.size - 1) {
        val a = hooks[k]; val b = hooks[k + 1]
        val sag = (14f + 2f * sin(t * 0.8f + k)) * u
        val line = Path()
        for (i in 0..24) {
            val f = i / 24f
            val x = a + (b - a) * f; val y = top + sag * 4f * f * (1f - f)
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        drawPath(line, silk.copy(alpha = 0.45f), style = Stroke(0.9f * u))
        for (i in 1..5) {
            val f = i / 6f
            val x = a + (b - a) * f; val y = top + sag * 4f * f * (1f - f)
            if ((i + k) % 2 == 0) jackOLantern(Offset(x, y + 6f * u), 5.5f * u, t + i)
            else bat(Offset(x, y + 4f * u), 5f * u, sin(t * 6f + i) * 0.5f, FestiveColors.Witch)
        }
    }
    // Spider on its thread, in the open space left of the search button.
    val sx = size.width * 0.58f
    val sy = inset + (30f + 10f * sin(t * 0.7f)) * u
    drawLine(silk, Offset(sx, top), Offset(sx, sy), 0.8f * u)
    for (side in listOf(-1f, 1f)) for (l in 0..2) {
        val ly = sy - 1.5f * u + l * 1.6f * u
        drawLine(FestiveColors.Witch, Offset(sx, ly), Offset(sx + side * 5.5f * u, ly + (l - 1) * 2.4f * u), 1f * u, StrokeCap.Round)
    }
    drawCircle(FestiveColors.Witch, 3.2f * u, Offset(sx, sy))
    drawCircle(FestiveColors.Witch, 2.1f * u, Offset(sx, sy - 3.5f * u))
    drawCircle(FestiveColors.Pumpkin, 0.7f * u, Offset(sx - 0.8f * u, sy - 3.8f * u))
    drawCircle(FestiveColors.Pumpkin, 0.7f * u, Offset(sx + 0.8f * u, sy - 3.8f * u))
    // A bigger bat crossing every ~12 s.
    val cycle = (t % 12f) / 12f
    if (cycle < 0.5f) {
        val q = cycle / 0.5f
        bat(Offset(-24f * u + (size.width + 48f * u) * q, inset + (44f + 10f * sin(q * 10f)) * u), 9f * u, sin(t * 14f), FestiveColors.Witch.copy(alpha = 0.9f))
    }
}

/** A small jack-o'-lantern whose face flickers like a candle inside. */
private fun DrawScope.jackOLantern(c: Offset, r: Float, t: Float) {
    val glow = 0.75f + 0.25f * sin(t * 7f) * sin(t * 3.1f)
    drawCircle(FestiveColors.Pumpkin.copy(alpha = 0.25f * glow), r * 1.9f, c)
    drawOval(FestiveColors.Pumpkin, Offset(c.x - r, c.y - r * 0.82f), androidx.compose.ui.geometry.Size(r * 2f, r * 1.64f))
    drawLine(FestiveColors.Leaf, Offset(c.x, c.y - r * 0.8f), Offset(c.x + r * 0.25f, c.y - r * 1.25f), r * 0.28f, StrokeCap.Round)
    val face = Color(0xFFFFE27A).copy(alpha = glow)
    drawPath(Path().apply { moveTo(c.x - r * 0.55f, c.y - r * 0.1f); lineTo(c.x - r * 0.25f, c.y - r * 0.45f); lineTo(c.x - r * 0.05f, c.y - r * 0.1f); close() }, face)
    drawPath(Path().apply { moveTo(c.x + r * 0.05f, c.y - r * 0.1f); lineTo(c.x + r * 0.25f, c.y - r * 0.45f); lineTo(c.x + r * 0.55f, c.y - r * 0.1f); close() }, face)
    drawPath(Path().apply { moveTo(c.x - r * 0.5f, c.y + r * 0.15f); quadraticTo(c.x, c.y + r * 0.6f, c.x + r * 0.5f, c.y + r * 0.15f); quadraticTo(c.x, c.y + r * 0.35f, c.x - r * 0.5f, c.y + r * 0.15f); close() }, face)
}

/**
 * Chhath Puja: dawn behind the header, the sun rising slowly over still water, with soft ripples
 * and its reflection, the moment of Usha Arghya.
 */
private fun DrawScope.sunrise(t: Float, inset: Float) {
    val u = 1.dp.toPx()
    val horizon = inset + 46f * u
    val cx = size.width * 0.5f
    drawRect(
        androidx.compose.ui.graphics.Brush.verticalGradient(
            0f to Color.Transparent, 0.45f to FestiveColors.Dawn.copy(alpha = 0.10f), 1f to FestiveColors.Dawn.copy(alpha = 0.22f),
            startY = inset, endY = horizon,
        ),
        topLeft = Offset(0f, inset), size = androidx.compose.ui.geometry.Size(size.width, horizon - inset),
    )
    val rise = 6f * u * sin(t * 0.25f)
    val sun = Offset(cx, horizon - 8f * u + rise)
    drawCircle(FestiveColors.Dawn.copy(alpha = 0.18f), 34f * u, sun)
    drawCircle(Color(0xFFFFB25E).copy(alpha = 0.35f), 22f * u, sun)
    drawCircle(Color(0xFFFF8A3D), 13f * u, sun)
    // Water: the horizon line, the sun's broken reflection and slow ripples.
    drawLine(Color(0xFF6FB7FF).copy(alpha = 0.45f), Offset(0f, horizon), Offset(size.width, horizon), 1f * u)
    for (k in 0..3) {
        val y = horizon + (5f + k * 5f) * u
        val w = (22f - k * 4f) * u * (1f + 0.08f * sin(t * 1.4f + k))
        drawLine(Color(0xFFFF9E5E).copy(alpha = 0.5f - k * 0.1f), Offset(cx - w, y), Offset(cx + w, y), 1.4f * u, StrokeCap.Round)
        val drift = ((t * 8f + k * 40f) % (size.width * 0.4f))
        drawLine(Color(0xFF9FD0FF).copy(alpha = 0.3f), Offset(size.width * 0.05f + drift, y + 2f * u), Offset(size.width * 0.05f + drift + 14f * u, y + 2f * u), 0.9f * u, StrokeCap.Round)
        drawLine(Color(0xFF9FD0FF).copy(alpha = 0.3f), Offset(size.width * 0.95f - drift, y + 1f * u), Offset(size.width * 0.95f - drift - 14f * u, y + 1f * u), 0.9f * u, StrokeCap.Round)
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
