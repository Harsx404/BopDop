package com.dopdop.bopdop.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.Mascot
import com.dopdop.bopdop.data.SeekStyle
import com.dopdop.bopdop.data.model.formatDuration
import com.dopdop.bopdop.ui.glass.LocalReduceMotion
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType
import kotlin.math.PI
import kotlin.math.sin

private fun smooth(t: Float) = t * t * (3f - 2f * t)

/**
 * The player's seek bar, drawn in the style picked in Settings (wave, line, bars or dots), with
 * the chosen pet running along on top of the playhead. Tap or drag anywhere to seek; a long press
 * calls [onLongPress] (the player opens the style picker with it).
 */
@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    seed: Int = 0,
    style: SeekStyle? = null,
    mascot: Mascot? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val settings by LibraryStore.state.collectAsState()
    val look = style ?: settings.settings.seekStyle
    val friend = mascot ?: settings.settings.mascot
    val frames = if (friend == Mascot.NONE) null else rememberMascotFrames()
    val dur = durationMs.coerceAtLeast(1L)
    var drag by remember { mutableStateOf<Float?>(null) }
    val frac = drag ?: (positionMs.toFloat() / dur).coerceIn(0f, 1f)
    val reduce = LocalReduceMotion.current
    val amp by animateFloatAsState(if (playing && drag == null) 1f else 0f, tween(420), label = "amp")
    val thumbH by animateFloatAsState(if (drag != null) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "thumb")
    val moving = (playing || drag != null) && !reduce
    // Seconds of animation; keeps running while paused only when the kitty is asleep.
    val time by produceState(0f, moving, friend, reduce) {
        if (reduce || (!moving && friend == Mascot.NONE)) return@produceState
        if (!moving) {
            // Paused: only the kitty's slow sleep frames change, so a few ticks a second, not every frame.
            while (true) { kotlinx.coroutines.delay(150); value += 0.15f }
        }
        val start = withInfiniteAnimationFrameMillis { it }
        val base = value
        while (true) withInfiniteAnimationFrameMillis { value = base + (it - start) / 1000f }
    }
    // When playback paused, so the kitty lies down once and then dozes.
    var pausedAt by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) { if (!playing) pausedAt = time }
    // Which way the mascot faces: back to the left while you scrub backwards.
    var facing by remember { mutableFloatStateOf(1f) }
    var last by remember { mutableFloatStateOf(frac) }
    if (drag != null && frac != last) { facing = if (frac < last) -1f else 1f; last = frac }
    if (drag == null) { if (facing < 0f) facing = 1f; last = frac }
    val petRoom = if (friend == Mascot.NONE) 0.dp else 30.dp

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier.fillMaxWidth().height(38.dp + petRoom)
                .scrub(onStart = {}, onMove = { drag = it }, onEnd = { onSeek((it * dur).toLong()); drag = null }, inset = 8.dp, onLongPress = onLongPress),
        ) {
            val pad = 8.dp.toPx()
            val w = size.width - 2 * pad
            val cy = petRoom.toPx() + 19.dp.toPx()
            val x = pad + w * frac
            // Read here, in drawing, so each animation frame redraws the bar without recomposing it.
            val phase = time * 2f * PI.toFloat() * 0.9f
            val top = when (look) {
                SeekStyle.WAVE -> drawWave(pad, w, cy, x, amp, phase, thumbH)
                SeekStyle.LINE -> drawPlainLine(pad, w, cy, x, thumbH)
                SeekStyle.BARS -> drawBars(pad, w, cy, x, amp, phase, thumbH, seed)
                SeekStyle.DOTS -> drawDots(pad, w, cy, x, amp, phase, thumbH)
            }
            if (frames != null) {
                val mode = when { drag != null -> MascotMode.HOP; playing -> MascotMode.WALK; else -> MascotMode.SLEEP }
                // Reduced motion: a still kitty, asleep when paused.
                drawMascot(frames, x, top - 1.dp.toPx(), if (reduce) 0f else time, if (reduce && mode == MascotMode.HOP) MascotMode.WALK else mode, facing,
                    asleepFor = if (reduce) 99f else (time - pausedAt).coerceAtLeast(0f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Text(formatDuration((frac * dur / 1000).toInt()).ifEmpty { "0:00" }, style = BopType.Caption, color = if (drag != null) Bop.OnMedia else Bop.OnMedia2)
            Spacer(Modifier.weight(1f))
            if (caption != null) Text(caption, style = BopType.Micro.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified), color = Bop.OnMedia3)
            Spacer(Modifier.weight(1f))
            Text(
                if (drag != null) "-" + formatDuration(((1f - frac) * dur / 1000).toInt()).ifEmpty { "0:00" } else formatDuration((dur / 1000).toInt()).ifEmpty { "–:––" },
                style = BopType.Caption, color = Bop.OnMedia2,
            )
        }
    }
}

private val Rest = Color.White.copy(alpha = 0.26f)

/** The upright pill that marks the playhead; returns the y of its top. */
private fun DrawScope.pillThumb(x: Float, cy: Float, thumbH: Float): Float {
    val th = (18.dp + 8.dp * thumbH).toPx()
    val tw = (5.dp + 1.dp * thumbH).toPx()
    drawRoundRect(Color.White, Offset(x - tw / 2, cy - th / 2), Size(tw, th), CornerRadius(tw / 2))
    return cy - th / 2
}

/** Android-style squiggle: the played part is a travelling wave that settles flat when paused. */
private fun DrawScope.drawWave(pad: Float, w: Float, cy: Float, x: Float, amp: Float, phase: Float, thumbH: Float): Float {
    val stroke = 4.dp.toPx()
    val wave = 3.2.dp.toPx() * amp
    val lambda = 30.dp.toPx()
    val gap = 7.dp.toPx()
    if (x + gap < pad + w) drawLine(Rest, Offset(x + gap, cy), Offset(pad + w, cy), stroke, StrokeCap.Round)
    val end = (x - gap).coerceAtLeast(pad)
    if (end > pad) {
        val p = Path()
        var px = pad
        p.moveTo(px, cy)
        val step = 1.5.dp.toPx()
        // A squiggle needs room: short played segments stay (nearly) flat instead of kinking.
        val room = ((end - pad) / (lambda * 1.6f)).coerceIn(0f, 1f)
        while (px < end) {
            px = (px + step).coerceAtMost(end)
            // Ease in from the left cap and out into the thumb, so both ends meet the line smoothly.
            val inT = ((px - pad) / (lambda * 0.5f)).coerceIn(0f, 1f)
            val outT = ((end - px) / (lambda * 0.5f)).coerceIn(0f, 1f)
            val taper = room * smooth(inT) * smooth(outT)
            p.lineTo(px, cy + wave * taper * sin((px - pad) / lambda * 2f * PI.toFloat() - phase))
        }
        drawPath(p, Color.White, style = Stroke(stroke, cap = StrokeCap.Round))
    }
    return pillThumb(x, cy, thumbH)
}

/** A plain, thick line with a round knob that swells while you drag. */
private fun DrawScope.drawPlainLine(pad: Float, w: Float, cy: Float, x: Float, thumbH: Float): Float {
    val stroke = 5.dp.toPx()
    drawLine(Rest, Offset(pad, cy), Offset(pad + w, cy), stroke, StrokeCap.Round)
    drawLine(Color.White, Offset(pad, cy), Offset(x, cy), stroke, StrokeCap.Round)
    val r = (6.dp + 3.dp * thumbH).toPx()
    drawCircle(Color.White.copy(alpha = 0.25f * thumbH), r + 6.dp.toPx() * thumbH, Offset(x, cy))
    drawCircle(Color.White, r, Offset(x, cy))
    return cy - r
}

/** Height (0..1) of bar [i] of a song's waveform: a fixed shape per song, smooth like real audio. */
private fun barShape(seed: Int, i: Int): Float {
    fun n(k: Int): Float {
        var h = seed * 31 + k * 0x9E3779B1.toInt()
        h = (h xor (h ushr 15)) * 0x2C1B3C6D
        h = (h xor (h ushr 12)) * 0x297A2D39
        h = h xor (h ushr 15)
        return (h and 0xFFFF) / 65535f
    }
    return 0.22f + 0.78f * (n(i - 1) + 2f * n(i) + n(i + 1)) / 4f
}

/** A waveform of rounded bars; the played ones light up and breathe with the music. */
private fun DrawScope.drawBars(pad: Float, w: Float, cy: Float, x: Float, amp: Float, phase: Float, thumbH: Float, seed: Int): Float {
    val bw = 3.dp.toPx()
    val gap = 2.2.dp.toPx()
    val n = (w / (bw + gap)).toInt().coerceAtLeast(1)
    val maxH = 22.dp.toPx()
    for (i in 0 until n) {
        val bx = pad + i * (bw + gap) + bw / 2
        val played = bx <= x
        val breathe = if (played) 1f - 0.22f * amp + 0.22f * amp * sin(phase * 1.4f + i * 0.55f) else 1f
        val h = (maxH * barShape(seed, i) * breathe).coerceAtLeast(bw)
        drawRoundRect(
            if (played) Color.White else Rest, Offset(bx - bw / 2, cy - h / 2), Size(bw, h), CornerRadius(bw / 2),
        )
    }
    return minOf(pillThumb(x, cy, thumbH), cy - maxH / 2)
}

/** A string of beads: the played ones bigger and bright, and a glowing bead at the playhead. */
private fun DrawScope.drawDots(pad: Float, w: Float, cy: Float, x: Float, amp: Float, phase: Float, thumbH: Float): Float {
    val step = 9.dp.toPx()
    val n = (w / step).toInt()
    for (i in 0..n) {
        val dx = pad + i * step
        if (dx <= x) drawCircle(Color.White, 2.6.dp.toPx(), Offset(dx, cy))
        else drawCircle(Rest, 1.8.dp.toPx(), Offset(dx, cy))
    }
    val pulse = 0.5f + 0.5f * sin(phase * 1.2f) * amp
    val r = (6.dp + 3.dp * thumbH).toPx()
    drawCircle(Color.White.copy(alpha = 0.18f + 0.14f * pulse), r + (4.dp + 3.dp * pulse).toPx(), Offset(x, cy))
    drawCircle(Color.White, r, Offset(x, cy))
    return cy - r
}
