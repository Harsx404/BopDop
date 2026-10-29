package com.dopdop.bopdop.ui.festive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.dopdop.bopdop.R
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.ui.LocalApp
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.glass.LocalReduceMotion
import com.dopdop.bopdop.ui.theme.BopType
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * "A Little Light": Diwali's six-chapter story. Jyoti the diya wakes on the porch, Gajju helps the
 * rangoli bloom, the three carry the light down the lane, Tara sends a lantern skyward, the sky
 * fills with fireworks, and they rest together at home. Each chapter plays on its own, and a tap,
 * swipe or button lets you take part; finishing one earns its reward. Coordinates are the pack's
 * 216 x 384 scene pixels (a layer's cell top-left), timings its milliseconds.
 */

private const val SCENE_W = 216f
private const val SCENE_H = 384f

/** A character's three loops on its 4 x 4 sheet. */
private enum class Seq(val first: Int, val count: Int, val ms: Int) { MOVE(0, 8, 100), SETTLE(8, 4, 160), REST(12, 4, 400) }

/** Every sheet the scenes draw from: cell size, columns, and the frames and timing of its loop. */
private enum class Art(val res: Int, val cell: Int = 64, val cols: Int = 4, val first: Int = 0, val count: Int = 0, val ms: Int = 0) {
    JYOTI(R.drawable.diwali_jyoti), TARA(R.drawable.diwali_tara), GAJJU(R.drawable.diwali_gajju),
    RANGOLI(R.drawable.diwali_rangoli, 96, 3, 0, 6, 160),
    FIREWORKS(R.drawable.diwali_fireworks, 96, 4, 0, 8, 100),
    LANTERN(R.drawable.diwali_lantern, 64, 3, 0, 6, 160),
    DIYA(R.drawable.diwali_ambient, 64, 4, ROW_DIYA, 4, 160),
    FIREFLY(R.drawable.diwali_ambient, 64, 4, ROW_FIREFLY, 4, 160),
    ANAR(R.drawable.diwali_anar, 96, 3, 0, 6, 130),
    CHAKRI(R.drawable.diwali_chakri, 96, 3, 0, 6, 100);

    val character get() = this == JYOTI || this == TARA || this == GAJJU
}

/**
 * One sprite moving from [from] to [to] between [start] and [end]. Afterwards it holds at [to]
 * (a walker stops and rests); [once] layers vanish instead, and [every] repeats them. A [gated]
 * layer waits for the chapter's action: until then only gated layers starting at 0 show, at rest.
 */
private class Layer(
    val art: Art, val from: Offset, val to: Offset = from, val start: Int = 0, val end: Int = 6000,
    val seq: Seq = Seq.MOVE, val loop: Boolean = true, val once: Boolean = false, val every: Int = 0, val gated: Boolean = false,
)

private enum class Action { TAP_DIYA, RANGOLI, LAMPS, LANTERN, SKY, BOOKMARK }

private class Chapter(
    val title: String, val caption: String, val hint: String, val action: Action, val button: String,
    val reward: Int, val rewardName: String, val bg: Int, val star: Art, val layers: List<Layer>,
)

/** Story-rewards atlas cells. */
private const val LOCKED = 8
private const val ACTIVE = 9
private const val COMPLETE = 10

/** The three lamps of chapter 3, in scene pixels. */
private val LAMPS = listOf(Offset(16f, 312f), Offset(78f, 312f), Offset(140f, 312f))

private val CHAPTERS = listOf(
    Chapter("The First Spark", "One small spark wakes Jyoti.", "Tap the diya to wake the little light.", Action.TAP_DIYA, "Wake Jyoti",
        0, "Spark seed", R.drawable.diwali_story_1, Art.JYOTI, listOf(
            Layer(Art.JYOTI, Offset(80f, 286f), start = 0, end = 640, seq = Seq.SETTLE, loop = false, once = true, gated = true),
            Layer(Art.JYOTI, Offset(80f, 286f), Offset(130f, 286f), start = 640, end = 5200, gated = true),
            Layer(Art.FIREFLY, Offset(132f, 144f), Offset(76f, 206f), start = 300, end = 6000),
        )),
    Chapter("Colour Awakens", "Gajju makes room for a little more colour.", "Tap the rangoli to reveal its six stages.", Action.RANGOLI, "Reveal rangoli",
        4, "Rangoli medal", R.drawable.diwali_story_2, Art.GAJJU, listOf(
            Layer(Art.GAJJU, Offset(-40f, 292f), Offset(4f, 292f), end = 4000),
            Layer(Art.JYOTI, Offset(140f, 292f), seq = Seq.REST),
        )),
    Chapter("Lights Along the Way", "Together, they carry the light down the lane.", "Light the three lamps to continue the journey.", Action.LAMPS, "Light them",
        3, "Shared light", R.drawable.diwali_story_3, Art.JYOTI, listOf(
            Layer(Art.GAJJU, Offset(-32f, 270f), Offset(78f, 270f)),
            Layer(Art.JYOTI, Offset(25f, 292f), Offset(135f, 292f)),
            Layer(Art.TARA, Offset(86f, 190f), Offset(138f, 180f)),
        )),
    Chapter("Raise the Lantern", "Tara lifts their wish into the night.", "Swipe upward to send the lantern skyward.", Action.LANTERN, "Release lantern",
        5, "Lantern medal", R.drawable.diwali_story_4, Art.TARA, listOf(
            Layer(Art.JYOTI, Offset(12f, 290f), seq = Seq.REST),
            Layer(Art.GAJJU, Offset(142f, 290f), seq = Seq.REST),
            Layer(Art.TARA, Offset(96f, 246f), Offset(96f, 120f), start = 0, end = 5200, gated = true),
            Layer(Art.LANTERN, Offset(38f, 242f), Offset(38f, 70f), start = 0, end = 4000, loop = false, gated = true),
        )),
    Chapter("The Night Blossoms", "Every little light joins the celebration.", "Tap the sky for a fireworks bloom.", Action.SKY, "Light up the sky",
        7, "Celebration medal", R.drawable.diwali_story_5, Art.TARA, listOf(
            Layer(Art.FIREWORKS, Offset(8f, 66f), start = 1000, end = 1800, loop = false, once = true, every = 6000),
            Layer(Art.FIREWORKS, Offset(112f, 120f), start = 2500, end = 3300, loop = false, once = true, every = 6000),
            Layer(Art.FIREWORKS, Offset(60f, 38f), start = 4100, end = 4900, loop = false, once = true, every = 6000),
            Layer(Art.ANAR, Offset(60f, 263f), start = 400),
            Layer(Art.JYOTI, Offset(20f, 290f), seq = Seq.REST),
            Layer(Art.GAJJU, Offset(136f, 290f), seq = Seq.REST),
            Layer(Art.TARA, Offset(78f, 234f), Offset(82f, 224f)),
        )),
    Chapter("Carry the Light Home", "The brightest part is bringing it home.", "Rest together, then collect the diya bookmark.", Action.BOOKMARK, "Collect bookmark",
        13, "Diya bookmark", R.drawable.diwali_story_6, Art.JYOTI, listOf(
            Layer(Art.JYOTI, Offset(12f, 292f), seq = Seq.REST),
            Layer(Art.TARA, Offset(78f, 272f), seq = Seq.REST),
            Layer(Art.GAJJU, Offset(142f, 292f), seq = Seq.REST),
            Layer(Art.FIREFLY, Offset(100f, 190f), Offset(110f, 184f)),
        )),
)

private fun done(progress: Int, i: Int) = progress and (1 shl i) != 0

private fun finish(i: Int) = LibraryStore.updateSettings { it.copy(diwaliStory = it.diwaliStory or (1 shl i)) }

/** A scene filling the screen: the art covers it, anchored at the bottom where the characters stand. */
private fun sceneGrid(w: Float, h: Float): Grid {
    val s = max(w / SCENE_W, h / SCENE_H)
    return Grid(s, (w - SCENE_W * s) / 2, h - SCENE_H * s)
}

/** One atlas cell at scene position ([x], [y]) on grid [g], mirrored when [flip]. */
private fun DrawScope.cell(atlas: ImageBitmap, art: Art, frame: Int, g: Grid, x: Float, y: Float, alpha: Float = 1f, flip: Boolean = false) {
    val left = (g.left + x * g.s).roundToInt()
    val top = (g.top + y * g.s).roundToInt()
    val side = (art.cell * g.s).roundToInt()
    fun draw() = drawImage(atlas, srcOffset = IntOffset(frame % art.cols * art.cell, frame / art.cols * art.cell), srcSize = IntSize(art.cell, art.cell),
        dstOffset = IntOffset(left, top), dstSize = IntSize(side, side), alpha = alpha, filterQuality = FilterQuality.None)
    if (flip) withTransform({ scale(-1f, 1f, Offset(left + side / 2f, top + side / 2f)) }) { draw() } else draw()
}

/** A trimmed 24-pixel reward icon (complete, active or locked chapter, or a reward) at whole-pixel scale. */
@Composable
fun DiwaliReward(cellIndex: Int, description: String?, modifier: Modifier = Modifier) {
    val atlas = ImageBitmap.imageResource(R.drawable.diwali_rewards)
    Canvas(modifier.then(if (description == null) Modifier else Modifier.semantics { contentDescription = description })) {
        val px = floor(size.minDimension / 24f).coerceAtLeast(1f).toInt()
        drawImage(atlas, srcOffset = IntOffset(cellIndex % 4 * 32 + 4, cellIndex / 4 * 32 + 4), srcSize = IntSize(24, 24),
            dstOffset = IntOffset(((size.width - 24 * px) / 2).roundToInt(), ((size.height - 24 * px) / 2).roundToInt()),
            dstSize = IntSize(24 * px, 24 * px), filterQuality = FilterQuality.None)
    }
}

/** The six chapters as reward chips: earned rewards, the chapter up next, and locked ones. */
@Composable
private fun ChapterChips(progress: Int, current: Int?, size: androidx.compose.ui.unit.Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        CHAPTERS.forEachIndexed { i, ch ->
            val cell = when { done(progress, i) -> ch.reward; i == current -> ACTIVE; else -> LOCKED }
            DiwaliReward(cell, null, Modifier.size(size))
        }
    }
}

/**
 * Home's way into the story: the next chapter's scene with its companion pacing the lane, and the
 * rewards earned so far. Once every chapter is lit it offers a replay.
 */
@Composable
fun DiwaliStoryCard(progress: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val next = CHAPTERS.indices.firstOrNull { !done(progress, it) }
    val ch = CHAPTERS[next ?: CHAPTERS.lastIndex]
    val bg = ImageBitmap.imageResource(ch.bg)
    val sheet = ImageBitmap.imageResource(ch.star.res)
    val time = rememberFestiveTime(fps = 12)
    val shape = RoundedCornerShape(28.dp)
    val label = if (next == null) "A Little Light, all six chapters done. Replay the story" else "A Little Light, chapter ${next + 1} of 6: ${ch.title}"
    Box(
        modifier.fillMaxWidth().height(156.dp).clip(shape).background(Diya.Night)
            .bounceClick(0.98f, onClick = onOpen).semantics { contentDescription = label; role = Role.Button },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // The lane where the characters stand: scene rows up to 352 at the card's foot.
            val s = size.width / SCENE_W
            val g = Grid(s, 0f, size.height - 352f * s)
            drawImage(bg, dstOffset = IntOffset(0, g.top.roundToInt()), dstSize = IntSize(size.width.roundToInt(), (SCENE_H * s).roundToInt()),
                filterQuality = FilterQuality.None)
            drawRect(Brush.horizontalGradient(0f to Diya.Night.copy(alpha = 0.92f), 0.58f to Diya.Night.copy(alpha = 0.35f), 1f to Color.Transparent))
            // The companion walks a few steps one way, rests, and walks back.
            val p = time.value % 10f / 10f
            val (x, walking, flip) = when {
                p < 0.35f -> Triple(150f - 36f * (p / 0.35f), true, true)
                p < 0.5f -> Triple(114f, false, true)
                p < 0.85f -> Triple(114f + 36f * ((p - 0.5f) / 0.35f), true, false)
                else -> Triple(150f, false, false)
            }
            val seq = if (walking) Seq.MOVE else Seq.REST
            val frame = seq.first + (time.value * 1000 / seq.ms).toInt() % seq.count
            val y = if (ch.star == Art.TARA) 272f + (sin(time.value * 1.6f) * 2f).roundToInt() else 288f
            cell(sheet, ch.star, frame, g, x, y, flip = flip)
        }
        Column(Modifier.align(Alignment.CenterStart).padding(start = 20.dp, end = 120.dp)) {
            Text("A LITTLE LIGHT", style = BopType.Micro, color = Diya.Gold)
            Spacer(Modifier.height(4.dp))
            Text(if (next == null) "Replay the story" else ch.title, style = BopType.Title, color = Diya.Cream, maxLines = 1)
            Text(if (next == null) "All six chapters lit · Shubh Deepavali" else "Chapter ${next + 1} of 6 · " + if (next == 0) "Begin" else "Continue",
                style = BopType.Caption, color = Diya.Cream.copy(alpha = 0.8f), maxLines = 1)
            Spacer(Modifier.height(10.dp))
            ChapterChips(progress, next, 22.dp)
        }
    }
}

/**
 * The story itself, full screen. Chapters open where you left off; each plays its scene, and its
 * action (a tap, swipe or the button for the same thing) completes it and earns its reward. Close
 * any time; finishing the last chapter opens the Deepavali blessing with Lakshmi and Ganesha.
 */
@Composable
fun DiwaliStoryScreen() {
    val app = LocalApp.current
    val library by LibraryStore.state.collectAsState()
    val progress = library.settings.diwaliStory
    val reduce = LocalReduceMotion.current
    var chapter by remember { mutableIntStateOf(CHAPTERS.indices.firstOrNull { !done(LibraryStore.state.value.settings.diwaliStory, it) } ?: 0) }
    var finale by remember { mutableStateOf(false) }
    val ch = CHAPTERS[chapter]

    val time = rememberFestiveTime(fps = 24)
    var start by remember { mutableFloatStateOf(0f) }
    // The chapter's own state: when its action happened, rangoli stages, lamps lit, blooms tapped.
    var armedAt by remember { mutableStateOf<Float?>(null) }
    var stage by remember { mutableIntStateOf(0) }
    var lit by remember { mutableIntStateOf(0) }
    var taps by remember { mutableIntStateOf(0) }
    val blooms = remember { mutableStateListOf<Pair<Offset, Float>>() }
    var earned by remember { mutableStateOf(false) }
    // Done on this visit: every chapter plays afresh, a replay included; its reward is kept for good.
    var acted by remember { mutableStateOf(false) }
    LaunchedEffect(chapter) {
        start = time.value; armedAt = null; stage = 0; lit = 0; taps = 0; blooms.clear(); earned = false; acted = false
    }
    val complete = acted
    fun complete() { acted = true; if (!done(LibraryStore.state.value.settings.diwaliStory, chapter)) { finish(chapter); earned = true } }

    // Act out the chapter's action (also what its button does).
    fun act() {
        val now = time.value
        when (ch.action) {
            Action.TAP_DIYA, Action.LANTERN -> { if (armedAt == null) armedAt = now; complete() }
            Action.RANGOLI -> { stage = 6; complete() }
            Action.LAMPS -> { lit = 0b111; complete() }
            Action.SKY -> {
                if (!reduce) listOf(Offset(20f, 150f), Offset(110f, 118f), Offset(64f, 186f)).forEachIndexed { i, p -> blooms += p to now + i * 0.45f }
                taps = 3; complete()
            }
            Action.BOOKMARK -> { complete(); finale = true }
        }
    }

    var canvas by remember { mutableStateOf(IntSize.Zero) }
    val sheets = Art.entries.associateWith { ImageBitmap.imageResource(it.res) }
    val bg = ImageBitmap.imageResource(ch.bg)
    val decor = ImageBitmap.imageResource(R.drawable.diwali_decor)

    Box(Modifier.fillMaxSize().background(Diya.Night)) {
        Canvas(
            Modifier.fillMaxSize().onSizeChanged { canvas = it }
                .pointerInput(chapter) {
                    detectTapGestures { tap ->
                        val g = sceneGrid(canvas.width.toFloat(), canvas.height.toFloat())
                        val at = Offset((tap.x - g.left) / g.s, (tap.y - g.top) / g.s)
                        val now = time.value
                        when (ch.action) {
                            Action.TAP_DIYA -> act()
                            Action.RANGOLI -> if (stage < 6) { stage++; if (stage == 6) complete() }
                            Action.LAMPS -> {
                                // The lamp tapped, or else the next one still dark.
                                val hit = LAMPS.indexOfFirst { (Offset(it.x + 32f, it.y + 40f) - at).getDistance() < 24f && lit and (1 shl LAMPS.indexOf(it)) == 0 }
                                val i = if (hit >= 0) hit else (0..2).firstOrNull { lit and (1 shl it) == 0 }
                                if (i != null) { lit = lit or (1 shl i); if (lit == 0b111) complete() }
                            }
                            Action.SKY -> if (at.y < 250f) {
                                // Spaced out: at most three at once, never two in the same instant.
                                if (!reduce && blooms.count { now - it.second < 0.8f } < 3 && blooms.none { now - it.second in 0f..0.3f })
                                    blooms += Offset(at.x - 48f, at.y - 48f) to now
                                taps++; if (taps >= 3) complete()
                            }
                            Action.LANTERN, Action.BOOKMARK -> Unit
                        }
                    }
                }
                .pointerInput(chapter) {
                    if (ch.action != Action.LANTERN) return@pointerInput
                    var pulled = 0f
                    detectVerticalDragGestures(onDragStart = { pulled = 0f }, onVerticalDrag = { _, dy -> pulled += dy },
                        onDragEnd = { if (pulled < -48.dp.toPx()) act() })
                },
        ) {
            val g = sceneGrid(size.width, size.height)
            val now = time.value
            // Reduced motion shows each scene's final composition, the action already taken.
            val ms = if (reduce) 5990f else (now - start) * 1000f
            val armed = reduce || armedAt != null
            val armedMs = if (reduce || armedAt == null) 5990f else (now - armedAt!!) * 1000f
            // The porch waits in grey until Jyoti wakes; then colour washes out from the little light.
            val colour = if (ch.action != Action.TAP_DIYA) 1f else if (!armed) 0f else (armedMs / 1600f).coerceIn(0f, 1f)
            val filter = if (colour >= 1f) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.08f + 0.92f * colour * colour) })
            drawImage(bg, dstOffset = IntOffset(g.left.roundToInt(), g.top.roundToInt()),
                dstSize = IntSize((SCENE_W * g.s).roundToInt(), (SCENE_H * g.s).roundToInt()), colorFilter = filter, filterQuality = FilterQuality.None)
            if (ch.action == Action.TAP_DIYA && armed && colour < 1f) {
                // A ring of warm light spreading from the wick as the colour arrives.
                val c = Offset(g.left + 112f * g.s, g.top + 312f * g.s)
                val r = (24f + 320f * colour) * g.s
                drawCircle(Brush.radialGradient(0f to Diya.Gold.copy(alpha = 0f), 0.7f to Diya.Gold.copy(alpha = 0.4f * (1f - colour)), 1f to Diya.Gold.copy(alpha = 0f), center = c, radius = r), r, c)
            }

            if (ch.action == Action.RANGOLI && stage > 0)
                cell(sheets.getValue(Art.RANGOLI), Art.RANGOLI, stage - 1, g, 60f, 280f)
            if (ch.action == Action.RANGOLI && (complete || reduce) && stage == 0)
                cell(sheets.getValue(Art.RANGOLI), Art.RANGOLI, 5, g, 60f, 280f)
            if (ch.action == Action.LAMPS) LAMPS.forEachIndexed { i, p ->
                val on = complete || reduce || lit and (1 shl i) != 0
                if (on) {
                    val c = Offset(g.left + (p.x + 32f) * g.s, g.top + (p.y + 30f) * g.s)
                    drawCircle(Brush.radialGradient(listOf(Diya.Gold.copy(alpha = 0.35f), Diya.Gold.copy(alpha = 0f)), c, 26f * g.s), 26f * g.s, c)
                    cell(sheets.getValue(Art.DIYA), Art.DIYA, ROW_DIYA + (now * 1000 / 160).toInt().plus(i) % 4, g, p.x, p.y)
                } else {
                    // The unlit diya's bowl sits seven pixels higher in its cell than the lit one's.
                    drawImage(decor, srcOffset = IntOffset(0, 0), srcSize = IntSize(64, 64),
                        dstOffset = IntOffset((g.left + p.x * g.s).roundToInt(), (g.top + (p.y + 7f) * g.s).roundToInt()),
                        dstSize = IntSize((64 * g.s).roundToInt(), (64 * g.s).roundToInt()), filterQuality = FilterQuality.None)
                }
            }

            for (l in ch.layers) {
                val sheet = sheets.getValue(l.art)
                if (l.gated && !armed) {
                    if (l.start != 0) continue
                    val f = if (l.art.character) Seq.REST.first + (now * 1000 / Seq.REST.ms).toInt() % 4 else l.art.first
                    if (l.art == Art.JYOTI && ch.action == Action.TAP_DIYA) {
                        // Asleep and unlit, but a faint ember breathes in the wick.
                        val c = Offset(g.left + (l.from.x + 32f) * g.s, g.top + (l.from.y + 26f) * g.s)
                        drawCircle(Brush.radialGradient(listOf(Diya.Marigold.copy(alpha = 0.16f + 0.1f * sin(now * 2f)), Diya.Marigold.copy(alpha = 0f)), c, 18f * g.s), 18f * g.s, c)
                    }
                    cell(sheet, l.art, f, g, l.from.x, l.from.y)
                    continue
                }
                var local = if (l.gated) armedMs else ms
                if (l.every > 0 && local >= 0) local %= l.every
                if (local < l.start) continue
                if (l.once && local >= l.end) continue
                if (reduce && (l.art == Art.FIREWORKS)) continue
                val q = ((local - l.start) / (l.end - l.start)).coerceIn(0f, 1f)
                val x = (l.from.x + (l.to.x - l.from.x) * q).roundToInt().toFloat()
                val y = (l.from.y + (l.to.y - l.from.y) * q).roundToInt().toFloat()
                // A walker that has arrived stops and rests.
                val arrived = l.art.character && l.seq == Seq.MOVE && l.from != l.to && local >= l.end
                val frame = if (l.art.character) {
                    val seq = if (arrived) Seq.REST else l.seq
                    val k = ((if (arrived) local - l.end else local - l.start) / seq.ms).toInt()
                    seq.first + if (l.loop || arrived) k % seq.count else k.coerceAtMost(seq.count - 1)
                } else {
                    val k = ((local - l.start) / l.art.ms).toInt()
                    l.art.first + if (l.loop) k % l.art.count else k.coerceAtMost(l.art.count - 1)
                }
                if (l.art == Art.FIREFLY || l.art == Art.ANAR) {
                    val c = Offset(g.left + (x + l.art.cell / 2f) * g.s, g.top + (y + l.art.cell * 0.6f) * g.s)
                    drawCircle(Brush.radialGradient(listOf(Diya.Gold.copy(alpha = 0.22f), Diya.Gold.copy(alpha = 0f)), c, 30f * g.s), 30f * g.s, c)
                }
                cell(sheet, l.art, frame, g, x, y)
            }

            // Fireworks you set off, each blooming once.
            for ((p, at) in blooms) {
                val k = ((now - at) * 1000 / Art.FIREWORKS.ms).toInt()
                if (k in 0..7) cell(sheets.getValue(Art.FIREWORKS), Art.FIREWORKS, k, g, p.x, p.y)
            }
        }

        // Captions stay in UI text, outside the art, readable and translatable.
        if (!finale) Box(Modifier.fillMaxWidth().height(260.dp).background(Brush.verticalGradient(listOf(Diya.Night.copy(alpha = 0.92f), Color.Transparent))))
        if (!finale) Box(Modifier.fillMaxWidth().height(300.dp).align(Alignment.BottomCenter)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Diya.Night.copy(alpha = 0.95f)))))
        if (!finale) Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StoryCircle(Icons.Rounded.Close, "Close story") { app.nav.popBackStack() }
                Spacer(Modifier.weight(1f))
                ChapterChips(progress, chapter, 24.dp)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(44.dp))
            }
            Spacer(Modifier.height(22.dp))
            Text("CHAPTER ${chapter + 1} OF 6", style = BopType.Micro, color = Diya.Gold)
            Spacer(Modifier.height(6.dp))
            Text(ch.title, style = BopType.Hero, color = Diya.Cream)
            Spacer(Modifier.height(6.dp))
            Text(ch.caption, style = BopType.Body, color = Diya.Cream.copy(alpha = 0.85f))
        }
        if (!finale) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(complete, enter = fadeIn(), exit = fadeOut()) {
                Row(Modifier.padding(bottom = 12.dp).clip(RoundedCornerShape(50)).background(Diya.Plum.copy(alpha = 0.9f)).padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    DiwaliReward(ch.reward, null, Modifier.size(26.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(ch.rewardName + if (earned) " · collected!" else " · collected", style = BopType.BodyStrong, color = Diya.Gold)
                }
            }
            if (!complete) Text(ch.hint, style = BopType.Caption, color = Diya.Cream.copy(alpha = 0.8f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (chapter > 0) StoryCircle(Icons.AutoMirrored.Rounded.ArrowBack, "Previous chapter") { chapter-- } else Spacer(Modifier.width(44.dp))
                Spacer(Modifier.weight(1f))
                val last = chapter == CHAPTERS.lastIndex
                StoryPill(when { !complete -> ch.button; last -> "Deepavali blessings"; else -> "Next chapter" }) {
                    when { !complete -> act(); last -> finale = true; else -> chapter++ }
                }
                Spacer(Modifier.weight(1f))
                // Every chapter can be skipped.
                if (!complete && chapter < CHAPTERS.lastIndex) Text("Skip", style = BopType.BodyStrong, color = Diya.Cream.copy(alpha = 0.7f),
                    modifier = Modifier.bounceClick { chapter++ }.padding(10.dp))
                else Spacer(Modifier.width(44.dp))
            }
        }
        AnimatedVisibility(finale, enter = fadeIn(), exit = fadeOut()) {
            DiwaliBlessing(progress, onReplay = { finale = false; chapter = 0 }, onDone = { app.nav.popBackStack() })
        }
    }
}

@Composable
private fun StoryCircle(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(Diya.Plum.copy(alpha = 0.75f)).bounceClick(0.9f, onClick = onClick), contentAlignment = Alignment.Center) {
        ThemedIcon(icon, description, Modifier.size(22.dp), tint = Diya.Cream)
    }
}

@Composable
private fun StoryPill(text: String, filled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.height(48.dp).clip(RoundedCornerShape(50)).background(if (filled) Diya.Gold else Diya.Plum.copy(alpha = 0.8f))
            .bounceClick(onClick = onClick).padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = BopType.BodyStrong, color = if (filled) Diya.Night else Diya.Cream) }
}

/**
 * The story's close, a small home shrine: the lamplit night with its garlands and drifting petals,
 * a marigold toran hung over Lord Ganesha and Goddess Lakshmi, each in their own soft halo with
 * sparks of gold rising around them, then the Deepavali greeting and the lights you gathered.
 */
@Composable
private fun DiwaliBlessing(progress: Int, onReplay: () -> Unit, onDone: () -> Unit) {
    val ganesha = ImageBitmap.imageResource(R.drawable.diwali_ganesha)
    val lakshmi = ImageBitmap.imageResource(R.drawable.diwali_lakshmi)
    val toran = ImageBitmap.imageResource(R.drawable.diwali_toran)
    val time = rememberFestiveTime(fps = 15)
    Box(Modifier.fillMaxSize().background(Diya.Night).pointerInput(Unit) { detectTapGestures { } }) {
        DiwaliBackground(Modifier.fillMaxSize(), dark = true)
        // A calm band through the middle, so the greeting reads over the sky.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.2f to Diya.Night.copy(alpha = 0f), 0.42f to Diya.Night.copy(alpha = 0.55f),
            0.78f to Diya.Night.copy(alpha = 0.7f), 1f to Diya.Night.copy(alpha = 0.2f))))
        Column(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.fillMaxWidth().height(268.dp).semantics { contentDescription = "Lord Ganesha and Goddess Lakshmi beneath a marigold toran" }) {
                val t = time.value
                // Trimmed to the portraits' art (12..116 x 13..147), side by side at whole-pixel scale.
                val w = 104; val h = 134
                val px = floor(minOf(size.width / (2 * w + 16), (size.height - 40.dp.toPx()) / h)).coerceAtLeast(1f).toInt()
                val gap = 16 * px
                val left = ((size.width - (2 * w * px + gap)) / 2).roundToInt()
                val top = (size.height - h * px).roundToInt()
                // The toran across the top of the shrine, at the portraits' own pixel size.
                val tw = toran.width * px
                drawImage(toran, srcOffset = IntOffset(0, 8), srcSize = IntSize(toran.width, 32),
                    dstOffset = IntOffset(((size.width - tw) / 2).roundToInt(), 0), dstSize = IntSize(tw, 32 * px), filterQuality = FilterQuality.None)
                val breathe = 0.82f + 0.18f * sin(t * 0.8f)
                listOf(left + w * px / 2, left + w * px + gap + w * px / 2).forEachIndexed { i, cx ->
                    val c = Offset(cx.toFloat(), top + h * px * 0.42f)
                    val r = w * px * 0.62f
                    drawCircle(Brush.radialGradient(0f to Diya.Gold.copy(alpha = 0.34f * breathe), 0.55f to Diya.Marigold.copy(alpha = 0.12f * breathe),
                        1f to Diya.Marigold.copy(alpha = 0f), center = c, radius = r), r, c)
                    // Sparks of gold drifting up around each, one art pixel at a time.
                    for (k in 0 until 6) {
                        val age = ((t * 0.18f) + k / 6f + i * 0.37f) % 1f
                        val sx = cx + kotlin.math.sin(k * 2.4f + i + age * 5f) * w * px * 0.5f
                        val sy = top + h * px * (0.95f - 0.9f * age)
                        drawRect(Diya.Gold.copy(alpha = kotlin.math.sin(age * 3.14159f) * 0.85f),
                            Offset(snap(sx, px.toFloat()), snap(sy, px.toFloat())), androidx.compose.ui.geometry.Size(px.toFloat(), px.toFloat()))
                    }
                }
                drawImage(ganesha, srcOffset = IntOffset(12, 13), srcSize = IntSize(w, h), dstOffset = IntOffset(left, top),
                    dstSize = IntSize(w * px, h * px), filterQuality = FilterQuality.None)
                drawImage(lakshmi, srcOffset = IntOffset(12, 13), srcSize = IntSize(w, h), dstOffset = IntOffset(left + w * px + gap, top),
                    dstSize = IntSize(w * px, h * px), filterQuality = FilterQuality.None)
            }
            Spacer(Modifier.height(20.dp))
            Text("Shubh Deepavali", style = BopType.Hero, color = Diya.Gold)
            Spacer(Modifier.height(6.dp))
            Text("May Lakshmi and Ganesha bless your home with light, music and joy.", style = BopType.Body,
                color = Diya.Cream.copy(alpha = 0.9f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Column(Modifier.clip(RoundedCornerShape(22.dp)).background(Diya.Plum.copy(alpha = 0.78f)).padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LIGHTS YOU GATHERED", style = BopType.Micro, color = Diya.Gold.copy(alpha = 0.85f))
                Spacer(Modifier.height(6.dp))
                ChapterChips(progress, null, 28.dp)
            }
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StoryPill("Replay story", filled = false, onClick = onReplay)
                StoryPill("Done", onClick = onDone)
            }
        }
    }
}
