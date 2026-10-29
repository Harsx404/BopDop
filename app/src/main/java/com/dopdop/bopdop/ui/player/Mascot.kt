package com.dopdop.bopdop.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.dopdop.bopdop.R
import com.dopdop.bopdop.data.Mascot
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/** What the mascot is doing: walking with the music, curling up to sleep while paused, hopping while you scrub. */
internal enum class MascotMode { WALK, SLEEP, HOP }

/**
 * One mascot's frames and timing. [walk] is its move cycle; [sleep] starts with [settle] frames of
 * lying down (played once), then loops the rest while it dozes.
 */
internal class MascotFrames(
    val walk: List<ImageBitmap>, val sleep: List<ImageBitmap>,
    val step: Float, val settle: Int, val settleStep: Float, val doze: Float,
    /** Pixel art: drawn with nearest-pixel scaling so it stays crisp. */
    val pixel: Boolean = false,
    /** Equal square source cells; displayed at an integer pixel scale, with feet on row 54. */
    val gridCell: Int = 0,
)


private val BUNNY_WALK = listOf(R.drawable.bunny_walk_0, R.drawable.bunny_walk_1, R.drawable.bunny_walk_2, R.drawable.bunny_walk_3,
    R.drawable.bunny_walk_4, R.drawable.bunny_walk_5, R.drawable.bunny_walk_6, R.drawable.bunny_walk_7)
private val BUNNY_SLEEP = listOf(R.drawable.bunny_sleep_0, R.drawable.bunny_sleep_1, R.drawable.bunny_sleep_2, R.drawable.bunny_sleep_3,
    R.drawable.bunny_sleep_4, R.drawable.bunny_sleep_5, R.drawable.bunny_sleep_6, R.drawable.bunny_sleep_7)
private val GIRL1_WALK = listOf(R.drawable.girl1_walk_0, R.drawable.girl1_walk_1, R.drawable.girl1_walk_2, R.drawable.girl1_walk_3, R.drawable.girl1_walk_4, R.drawable.girl1_walk_5, R.drawable.girl1_walk_6, R.drawable.girl1_walk_7, R.drawable.girl1_walk_8, R.drawable.girl1_walk_9, R.drawable.girl1_walk_10, R.drawable.girl1_walk_11)
private val GIRL1_SLEEP = listOf(R.drawable.girl1_sleep_0, R.drawable.girl1_sleep_1, R.drawable.girl1_sleep_2, R.drawable.girl1_sleep_3, R.drawable.girl1_sleep_4, R.drawable.girl1_sleep_5, R.drawable.girl1_sleep_6, R.drawable.girl1_sleep_7, R.drawable.girl1_sleep_8)
private val GIRL2_WALK = listOf(R.drawable.girl2_walk_0, R.drawable.girl2_walk_1, R.drawable.girl2_walk_2, R.drawable.girl2_walk_3, R.drawable.girl2_walk_4, R.drawable.girl2_walk_5, R.drawable.girl2_walk_6, R.drawable.girl2_walk_7, R.drawable.girl2_walk_8, R.drawable.girl2_walk_9, R.drawable.girl2_walk_10, R.drawable.girl2_walk_11)
private val GIRL2_SLEEP = listOf(R.drawable.girl2_sleep_0, R.drawable.girl2_sleep_1, R.drawable.girl2_sleep_2, R.drawable.girl2_sleep_3, R.drawable.girl2_sleep_4, R.drawable.girl2_sleep_5, R.drawable.girl2_sleep_6)
private val GIRL3_WALK = listOf(R.drawable.girl3_walk_0, R.drawable.girl3_walk_1, R.drawable.girl3_walk_2, R.drawable.girl3_walk_3, R.drawable.girl3_walk_4, R.drawable.girl3_walk_5, R.drawable.girl3_walk_6, R.drawable.girl3_walk_7, R.drawable.girl3_walk_8, R.drawable.girl3_walk_9, R.drawable.girl3_walk_10, R.drawable.girl3_walk_11)
private val GIRL3_SLEEP = listOf(R.drawable.girl3_sleep_0, R.drawable.girl3_sleep_1, R.drawable.girl3_sleep_2, R.drawable.girl3_sleep_3, R.drawable.girl3_sleep_4, R.drawable.girl3_sleep_5)
private val FIGHTER_WALK = listOf(R.drawable.fighter_walk_0, R.drawable.fighter_walk_1, R.drawable.fighter_walk_2, R.drawable.fighter_walk_3, R.drawable.fighter_walk_4, R.drawable.fighter_walk_5, R.drawable.fighter_walk_6, R.drawable.fighter_walk_7)
private val FIGHTER_SLEEP = listOf(R.drawable.fighter_sleep_0, R.drawable.fighter_sleep_1, R.drawable.fighter_sleep_2, R.drawable.fighter_sleep_3, R.drawable.fighter_sleep_4, R.drawable.fighter_sleep_5)
private val SAMURAI_WALK = listOf(R.drawable.samurai_walk_0, R.drawable.samurai_walk_1, R.drawable.samurai_walk_2, R.drawable.samurai_walk_3, R.drawable.samurai_walk_4, R.drawable.samurai_walk_5, R.drawable.samurai_walk_6, R.drawable.samurai_walk_7)
private val SAMURAI_SLEEP = listOf(R.drawable.samurai_sleep_0, R.drawable.samurai_sleep_1, R.drawable.samurai_sleep_2, R.drawable.samurai_sleep_3, R.drawable.samurai_sleep_4, R.drawable.samurai_sleep_5)
private val SHINOBI_WALK = listOf(R.drawable.shinobi_walk_0, R.drawable.shinobi_walk_1, R.drawable.shinobi_walk_2, R.drawable.shinobi_walk_3, R.drawable.shinobi_walk_4, R.drawable.shinobi_walk_5, R.drawable.shinobi_walk_6, R.drawable.shinobi_walk_7)
private val SHINOBI_SLEEP = listOf(R.drawable.shinobi_sleep_0, R.drawable.shinobi_sleep_1, R.drawable.shinobi_sleep_2, R.drawable.shinobi_sleep_3, R.drawable.shinobi_sleep_4, R.drawable.shinobi_sleep_5)

@Composable
internal fun rememberMascotFrames(mascot: Mascot = Mascot.BUNNY): MascotFrames {
    val res = LocalContext.current.resources
    val chosen = mascot.forFestival(com.dopdop.bopdop.ui.festive.LocalFestive.current.festival)
    return remember(res, chosen) {
        fun load(ids: List<Int>) = ids.map { ImageBitmap.imageResource(res, it) }
        fun sheet(id: Int): MascotFrames {
            val bitmap = android.graphics.BitmapFactory.decodeResource(res, id, android.graphics.BitmapFactory.Options().apply { inScaled = false })
            require(bitmap.width == 256 && bitmap.height == 256) { "Festival sprites require a 4x4 grid of 64px cells" }
            val cells = (0..15).map { android.graphics.Bitmap.createBitmap(bitmap, it % 4 * 64, it / 4 * 64, 64, 64).asImageBitmap() }
            return MascotFrames(cells.take(8), cells.drop(8), 0.1f, 4, 0.16f, 0.4f, pixel = true, gridCell = 64)
        }
        when (chosen) {
            Mascot.WISP -> sheet(R.drawable.halloween_wisp)
            Mascot.EMBER -> sheet(R.drawable.halloween_ember)
            Mascot.ECHO -> sheet(R.drawable.halloween_echo)
            // Diwali's companions: Jyoti the diya, Tara the kandil, Gajju the baby elephant.
            Mascot.JYOTI -> sheet(R.drawable.diwali_jyoti)
            Mascot.TARA -> sheet(R.drawable.diwali_tara)
            Mascot.GAJJU -> sheet(R.drawable.diwali_gajju)
            // Pixel-art packs: no sleep animation, so paused they stand and idle.
            Mascot.HANA -> MascotFrames(load(GIRL1_WALK), load(GIRL1_SLEEP), step = 0.08f, settle = 0, settleStep = 0f, doze = 0.16f, pixel = true)
            Mascot.MEI -> MascotFrames(load(GIRL2_WALK), load(GIRL2_SLEEP), step = 0.08f, settle = 0, settleStep = 0f, doze = 0.18f, pixel = true)
            Mascot.YUI -> MascotFrames(load(GIRL3_WALK), load(GIRL3_SLEEP), step = 0.08f, settle = 0, settleStep = 0f, doze = 0.2f, pixel = true)
            Mascot.FIGHTER -> MascotFrames(load(FIGHTER_WALK), load(FIGHTER_SLEEP), step = 0.1f, settle = 0, settleStep = 0f, doze = 0.18f, pixel = true)
            Mascot.SAMURAI -> MascotFrames(load(SAMURAI_WALK), load(SAMURAI_SLEEP), step = 0.1f, settle = 0, settleStep = 0f, doze = 0.18f, pixel = true)
            Mascot.SHINOBI -> MascotFrames(load(SHINOBI_WALK), load(SHINOBI_SLEEP), step = 0.1f, settle = 0, settleStep = 0f, doze = 0.18f, pixel = true)
            // The bunny (and Seasonal outside Halloween) is asleep from the first frame and breathes
            // through all 8, and hops a touch quicker than a walk.
            else -> MascotFrames(load(BUNNY_WALK), load(BUNNY_SLEEP), step = 0.085f, settle = 0, settleStep = 0f, doze = 0.32f)
        }
    }
}

/**
 * Draws the mascot standing on ([x], [feetY]) at [heightDp] tall, facing right (left when
 * [facing] < 0). [t] is seconds of animation; [asleepFor] is seconds since playback paused, which
 * plays the lying-down frames once and then loops the dozing ones.
 */
internal fun DrawScope.drawMascot(frames: MascotFrames, x: Float, feetY: Float, t: Float, mode: MascotMode, facing: Float, asleepFor: Float = 0f, heightDp: Float = 30f) {
    val img = when (mode) {
        MascotMode.SLEEP -> {
            val settled = if (frames.settle == 0) frames.settle else (asleepFor / frames.settleStep).toInt()
            val loop = frames.sleep.size - frames.settle
            if (settled < frames.settle) frames.sleep[settled.coerceAtLeast(0)]
            else frames.sleep[frames.settle + ((asleepFor - frames.settle * frames.settleStep) / frames.doze).toInt().mod(loop)]
        }
        else -> frames.walk[(t / frames.step).toInt().mod(frames.walk.size)]
    }
    val h = if (frames.gridCell > 0) ((heightDp.dp.toPx() / frames.gridCell).roundToInt().coerceAtLeast(1) * frames.gridCell).toFloat() else heightDp.dp.toPx()
    val w = h * img.width / img.height
    val rawLift = if (mode == MascotMode.HOP) 6.dp.toPx() * abs(sin(t * 9f)) else 0f
    val lift = if (frames.gridCell > 0) (rawLift / (h / frames.gridCell)).roundToInt() * (h / frames.gridCell) else rawLift
    val safeX = x.coerceIn((w / 2).coerceAtMost(size.width / 2), (size.width - w / 2).coerceAtLeast(size.width / 2))
    val baseline = if (frames.gridCell > 0) h * 54f / frames.gridCell else h
    // A soft shadow keeps it standing on the bar instead of floating over it.
    if (frames.gridCell > 0) drawRect(Color.Black.copy(alpha = 0.14f), Offset((safeX - w * 0.22f).roundToInt().toFloat(), feetY.roundToInt().toFloat()), Size((w * 0.44f).roundToInt().toFloat(), (h / frames.gridCell).coerceAtLeast(1f)))
    else drawOval(Color.Black.copy(alpha = 0.2f * (1f - lift / 8.dp.toPx())), Offset(safeX - w * 0.3f, feetY - 1.5.dp.toPx()), Size(w * 0.6f, 3.dp.toPx()))
    withTransform({ scale(facing, 1f, Offset(safeX.roundToInt().toFloat(), feetY.roundToInt().toFloat())) }) {
        drawImage(
            img,
            dstOffset = IntOffset((safeX - w / 2).roundToInt(), (feetY - baseline - lift).roundToInt()),
            dstSize = IntSize(w.roundToInt(), h.roundToInt()),
            filterQuality = if (frames.pixel) FilterQuality.None else FilterQuality.Medium,
        )
    }
}
