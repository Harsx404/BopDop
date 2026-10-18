package com.dopdop.bopdop.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.ui.AppState
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.glass.glassLite
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val MAX_MIN = 120

/**
 * Sleep timer as a dial: drag the knob round the ring (5-minute steps, up to two hours) or tap a
 * preset, then start. While running, the ring empties as time passes, and you can add five
 * minutes or turn it off. The music fades out at the end rather than stopping dead.
 */
@Composable
fun ColumnScope.SleepTimerSheet(app: AppState) {
    val at by PlayerConnection.sleepAt.collectAsState()
    val haptics = LocalHapticFeedback.current
    var pick by remember { mutableIntStateOf(30) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }
    val running = at != null
    val endOfSong = at == Long.MAX_VALUE
    val left = if (running && !endOfSong) (at!! - now).coerceAtLeast(0) else 0L
    // The ring: what is picked while setting, what is left while running.
    val ring = when {
        endOfSong -> 1f
        running -> left / 60_000f / MAX_MIN
        else -> pick / MAX_MIN.toFloat()
    }
    val shown by animateFloatAsState(ring.coerceIn(0f, 1f), spring(dampingRatio = 0.8f, stiffness = 300f), label = "ring")

    Row(Modifier.padding(horizontal = 22.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Bedtime, null, tint = Bop.Text)
        Spacer(Modifier.width(10.dp))
        Text("Sleep timer", style = BopType.Title, color = Bop.Text, modifier = Modifier.weight(1f))
        if (running) Text("On", style = BopType.BodyStrong, color = Bop.Text2)
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(210.dp).pointerInput(running) {
                // Drag round the dial; tap to jump. Setting while running restarts the timer.
                fun minutesAt(o: Offset): Int {
                    val a = atan2(o.x - size.width / 2f, -(o.y - size.height / 2f))
                    val f = ((a / (2 * Math.PI).toFloat()) + 1f) % 1f
                    return ((f * MAX_MIN / 5f).roundToInt() * 5).coerceIn(5, MAX_MIN)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun set(m: Int) { if (m != pick) { pick = m; haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) } }
                    set(minutesAt(down.position))
                    drag(down.id) { c -> set(minutesAt(c.position)); c.consume() }
                    if (running) PlayerConnection.sleepIn(pick)
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 8.dp.toPx()
                val knob = 12.dp.toPx()
                val r = size.minDimension / 2 - knob - 2.dp.toPx()
                val c = Offset(size.width / 2, size.height / 2)
                // A frosted disc inside the ring, like the other glass surfaces.
                drawCircle(Bop.Veil.copy(alpha = 0.45f), r - stroke, c)
                drawCircle(Bop.Veil.copy(alpha = 0.7f), r - stroke, c, style = Stroke(1.dp.toPx()))
                drawCircle(Bop.Ink.copy(alpha = 0.07f), r, c, style = Stroke(stroke))
                // Quarter-hour marks.
                for (k in 0 until MAX_MIN / 15) {
                    val a = k / (MAX_MIN / 15f) * 2f * Math.PI.toFloat()
                    val inner = r - stroke - 6.dp.toPx()
                    val len = if (k % 4 == 0) 6.dp.toPx() else 3.dp.toPx()
                    drawLine(Bop.Ink.copy(alpha = if (k % 4 == 0) 0.3f else 0.14f),
                        Offset(c.x + sin(a) * inner, c.y - cos(a) * inner), Offset(c.x + sin(a) * (inner - len), c.y - cos(a) * (inner - len)),
                        1.2.dp.toPx(), StrokeCap.Round)
                }
                if (shown > 0f) rotate(-90f, c) {
                    drawArc(Bop.Ink, 0f, 360f * shown, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round))
                }
                val a = shown * 2f * Math.PI.toFloat()
                val k = Offset(c.x + sin(a) * r, c.y - cos(a) * r)
                drawCircle(Color.Black.copy(alpha = 0.10f), knob + 1.dp.toPx(), k + Offset(0f, 2.dp.toPx()))
                drawCircle(Bop.Veil, knob, k)
                drawCircle(Bop.Ink.copy(alpha = 0.10f), knob, k, style = Stroke(1.dp.toPx()))
                drawCircle(Bop.Ink, 3.dp.toPx(), k)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val big = BopType.Hero.copy(fontSize = 42.sp)
                AnimatedContent(
                    when {
                        endOfSong -> "♪"
                        running -> "%d:%02d".format(left / 60_000, left / 1000 % 60)
                        else -> "$pick"
                    },
                    transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "dialNumber",
                ) { Text(it, style = big, color = Bop.Text) }
                Text(
                    when {
                        endOfSong -> "after this song"
                        running -> "until ${clock(at!!)}"
                        else -> "min · until ${clock(now + pick * 60_000L)}"
                    },
                    style = BopType.Caption, color = Bop.Text2,
                )
            }
        }
    }
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(15, 30, 45, 60, 90).forEach { m ->
            // While running, a preset restarts the timer at that length.
            Preset("$m", !running && pick == m, Modifier.weight(1f)) { pick = m; if (running) PlayerConnection.sleepIn(m) }
        }
        Preset("Song end", endOfSong, Modifier.weight(1.5f)) {
            PlayerConnection.sleepIn(0); app.toast("Stopping after this song"); app.sheet = null
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!running) {
            Pill("Start timer", primary = true, modifier = Modifier.weight(1f)) {
                PlayerConnection.sleepIn(pick); app.toast("Stopping in $pick min"); app.sheet = null
            }
        } else {
            if (!endOfSong) Pill("+5 min", primary = false, modifier = Modifier.weight(1f)) { PlayerConnection.extendSleep(5) }
            Pill("Turn off", primary = true, modifier = Modifier.weight(1f)) { PlayerConnection.cancelSleep(); app.toast("Sleep timer off") }
        }
    }
}

private fun clock(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

/** Same glass chips as the rest of the sheets; the picked one turns ink. */
@Composable
private fun Preset(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val ink by animateColorAsState(if (on) Bop.Ink else Color.Transparent, label = "preset")
    Box(
        modifier.height(46.dp).bounceClick(0.94f, onClick = onClick).glassLite(shape, alpha = 0.6f).clip(shape).background(ink),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = BopType.BodyStrong, color = if (on) Bop.OnInk else Bop.Text, maxLines = 1) }
}

@Composable
private fun Pill(label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier.height(52.dp).bounceClick(0.96f, onClick = onClick)
            .then(if (primary) Modifier.clip(shape).background(Bop.Ink) else Modifier.glassLite(shape, alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = BopType.BodyStrong, color = if (primary) Bop.OnInk else Bop.Text) }
}
