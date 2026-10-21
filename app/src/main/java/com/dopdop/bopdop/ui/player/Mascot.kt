package com.dopdop.bopdop.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
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
)


private val KITTY_WALK = listOf(R.drawable.kitty_walk_0, R.drawable.kitty_walk_1, R.drawable.kitty_walk_2, R.drawable.kitty_walk_3,
    R.drawable.kitty_walk_4, R.drawable.kitty_walk_5, R.drawable.kitty_walk_6, R.drawable.kitty_walk_7)
private val KITTY_SLEEP = listOf(R.drawable.kitty_sleep_0, R.drawable.kitty_sleep_1, R.drawable.kitty_sleep_2, R.drawable.kitty_sleep_3,
    R.drawable.kitty_sleep_4, R.drawable.kitty_sleep_5, R.drawable.kitty_sleep_6, R.drawable.kitty_sleep_7)
private val BUNNY_WALK = listOf(R.drawable.bunny_walk_0, R.drawable.bunny_walk_1, R.drawable.bunny_walk_2, R.drawable.bunny_walk_3,
    R.drawable.bunny_walk_4, R.drawable.bunny_walk_5, R.drawable.bunny_walk_6, R.drawable.bunny_walk_7)
private val BUNNY_SLEEP = listOf(R.drawable.bunny_sleep_0, R.drawable.bunny_sleep_1, R.drawable.bunny_sleep_2, R.drawable.bunny_sleep_3,
    R.drawable.bunny_sleep_4, R.drawable.bunny_sleep_5, R.drawable.bunny_sleep_6, R.drawable.bunny_sleep_7)
private val BIKE_WALK = listOf(R.drawable.bike_walk_0, R.drawable.bike_walk_1, R.drawable.bike_walk_2, R.drawable.bike_walk_3,
    R.drawable.bike_walk_4, R.drawable.bike_walk_5, R.drawable.bike_walk_6, R.drawable.bike_walk_7, R.drawable.bike_walk_8)
private val BIKE_SLEEP = listOf(R.drawable.bike_sleep_0, R.drawable.bike_sleep_1, R.drawable.bike_sleep_2, R.drawable.bike_sleep_3,
    R.drawable.bike_sleep_4, R.drawable.bike_sleep_5, R.drawable.bike_sleep_6, R.drawable.bike_sleep_7)

private val WAGON_WALK = listOf(R.drawable.wagon_walk_0, R.drawable.wagon_walk_1, R.drawable.wagon_walk_2, R.drawable.wagon_walk_3, R.drawable.wagon_walk_4, R.drawable.wagon_walk_5, R.drawable.wagon_walk_6, R.drawable.wagon_walk_7)
private val WAGON_SLEEP = listOf(R.drawable.wagon_sleep_0, R.drawable.wagon_sleep_1, R.drawable.wagon_sleep_2, R.drawable.wagon_sleep_3, R.drawable.wagon_sleep_4, R.drawable.wagon_sleep_5)
private val DINO_WALK = listOf(R.drawable.dino_walk_0, R.drawable.dino_walk_1, R.drawable.dino_walk_2, R.drawable.dino_walk_3, R.drawable.dino_walk_4, R.drawable.dino_walk_5, R.drawable.dino_walk_6, R.drawable.dino_walk_7)
private val DINO_SLEEP = listOf(R.drawable.dino_sleep_0, R.drawable.dino_sleep_1, R.drawable.dino_sleep_2, R.drawable.dino_sleep_3, R.drawable.dino_sleep_4, R.drawable.dino_sleep_5)
private val CRAFTER_WALK = listOf(R.drawable.crafter_walk_0, R.drawable.crafter_walk_1, R.drawable.crafter_walk_2, R.drawable.crafter_walk_3, R.drawable.crafter_walk_4, R.drawable.crafter_walk_5, R.drawable.crafter_walk_6, R.drawable.crafter_walk_7)
private val CRAFTER_SLEEP = listOf(R.drawable.crafter_sleep_0, R.drawable.crafter_sleep_1, R.drawable.crafter_sleep_2, R.drawable.crafter_sleep_3)
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
internal fun rememberMascotFrames(mascot: Mascot = Mascot.KITTY): MascotFrames {
    val res = LocalContext.current.resources
    return remember(res, mascot) {
        fun load(ids: List<Int>) = ids.map { ImageBitmap.imageResource(res, it) }
        // The kitty lies down over 5 frames, then dozes on the last 3 ("z z"); the bunny is asleep
        // from the first frame and breathes through all 8, and hops a touch quicker than a walk;
        // the bike rides fast, rolls to a stop over 4 frames, then idles with its "z z".
        when (mascot) {
            Mascot.BUNNY -> MascotFrames(load(BUNNY_WALK), load(BUNNY_SLEEP), step = 0.085f, settle = 0, settleStep = 0f, doze = 0.32f)
            Mascot.BIKE -> MascotFrames(load(BIKE_WALK), load(BIKE_SLEEP), step = 0.07f, settle = 4, settleStep = 0.2f, doze = 0.4f)
            // The wagon rests in place, looping all 6 idle frames; the dino stands, yawns and lies
            // down, then sleeps on its last frame; the crafter lies down on a pillow and sleeps.
            Mascot.WAGON -> MascotFrames(load(WAGON_WALK), load(WAGON_SLEEP), step = 0.11f, settle = 0, settleStep = 0f, doze = 0.45f)
            Mascot.DINO -> MascotFrames(load(DINO_WALK), load(DINO_SLEEP), step = 0.1f, settle = 5, settleStep = 0.35f, doze = 1f)
            Mascot.CRAFTER -> MascotFrames(load(CRAFTER_WALK), load(CRAFTER_SLEEP), step = 0.1f, settle = 0, settleStep = 0f, doze = 0.6f)
            // Pixel-art packs: no sleep animation, so paused they stand and idle.
            Mascot.HANA -> MascotFrames(load(GIRL1_WALK), load(GIRL1_SLEEP), step = 0.08f, settle = 0, settleStep = 0f, doze = 0.16f, pixel = true)
            Mascot.MEI -> MascotFrames(load(GIRL2_WALK), load(GIRL2_SLEEP), step = 0.08f, settle = 0, settleStep = 0f, doze = 0.18f, pixel = true)
            Mascot.YUI -> MascotFrames(load(GIRL3_WALK), load(GIRL3_SLEEP), step = 0.08f, settle = 0, settleStep = 0f, doze = 0.2f, pixel = true)
            Mascot.FIGHTER -> MascotFrames(load(FIGHTER_WALK), load(FIGHTER_SLEEP), step = 0.1f, settle = 0, settleStep = 0f, doze = 0.18f, pixel = true)
            Mascot.SAMURAI -> MascotFrames(load(SAMURAI_WALK), load(SAMURAI_SLEEP), step = 0.1f, settle = 0, settleStep = 0f, doze = 0.18f, pixel = true)
            Mascot.SHINOBI -> MascotFrames(load(SHINOBI_WALK), load(SHINOBI_SLEEP), step = 0.1f, settle = 0, settleStep = 0f, doze = 0.18f, pixel = true)
            else -> MascotFrames(load(KITTY_WALK), load(KITTY_SLEEP), step = 0.1f, settle = 5, settleStep = 0.16f, doze = 0.55f)
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
    val h = heightDp.dp.toPx()
    val w = h * img.width / img.height
    val lift = if (mode == MascotMode.HOP) 6.dp.toPx() * abs(sin(t * 9f)) else 0f
    // A soft shadow keeps it standing on the bar instead of floating over it.
    drawOval(Color.Black.copy(alpha = 0.2f * (1f - lift / 8.dp.toPx())), Offset(x - w * 0.3f, feetY - 1.5.dp.toPx()), Size(w * 0.6f, 3.dp.toPx()))
    withTransform({ scale(facing, 1f, Offset(x, feetY)) }) {
        drawImage(
            img,
            dstOffset = IntOffset((x - w / 2).roundToInt(), (feetY - h - lift).roundToInt()),
            dstSize = IntSize(w.roundToInt(), h.roundToInt()),
            filterQuality = if (frames.pixel) FilterQuality.None else FilterQuality.Medium,
        )
    }
}
