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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/** What the mascot is doing: walking with the music, curling up to sleep while paused, hopping while you scrub. */
internal enum class MascotMode { WALK, SLEEP, HOP }

/** The kitty's frames: an 8-step walk, and 8 steps from walking to lying down asleep. */
internal class MascotFrames(val walk: List<ImageBitmap>, val sleep: List<ImageBitmap>)

private val WALK = listOf(R.drawable.kitty_walk_0, R.drawable.kitty_walk_1, R.drawable.kitty_walk_2, R.drawable.kitty_walk_3,
    R.drawable.kitty_walk_4, R.drawable.kitty_walk_5, R.drawable.kitty_walk_6, R.drawable.kitty_walk_7)
private val SLEEP = listOf(R.drawable.kitty_sleep_0, R.drawable.kitty_sleep_1, R.drawable.kitty_sleep_2, R.drawable.kitty_sleep_3,
    R.drawable.kitty_sleep_4, R.drawable.kitty_sleep_5, R.drawable.kitty_sleep_6, R.drawable.kitty_sleep_7)

@Composable
internal fun rememberMascotFrames(): MascotFrames {
    val res = LocalContext.current.resources
    return remember(res) {
        MascotFrames(WALK.map { ImageBitmap.imageResource(res, it) }, SLEEP.map { ImageBitmap.imageResource(res, it) })
    }
}

/** Seconds per walk step, and how the walk-to-sleep turns into a slow breathing loop. */
private const val STEP = 0.1f
private const val SETTLE = 0.16f
private const val DOZE = 0.55f

/**
 * Draws the mascot standing on ([x], [feetY]) at [heightDp] tall, facing right (left when
 * [facing] < 0). [t] is seconds of animation; [asleepFor] is seconds since playback paused, which
 * plays the lying-down frames once and then loops the last three (the "z z" ones).
 */
internal fun DrawScope.drawMascot(frames: MascotFrames, x: Float, feetY: Float, t: Float, mode: MascotMode, facing: Float, asleepFor: Float = 0f, heightDp: Float = 30f) {
    val img = when (mode) {
        MascotMode.SLEEP -> {
            val settled = (asleepFor / SETTLE).toInt()
            if (settled < 5) frames.sleep[settled.coerceIn(0, 4)]
            else frames.sleep[5 + ((asleepFor - 5 * SETTLE) / DOZE).toInt() % 3]
        }
        else -> frames.walk[(t / STEP).toInt().mod(frames.walk.size)]
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
            filterQuality = FilterQuality.Medium,
        )
    }
}
