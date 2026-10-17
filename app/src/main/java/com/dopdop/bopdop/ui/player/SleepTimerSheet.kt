package com.dopdop.bopdop.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.ui.AppState
import com.dopdop.bopdop.ui.components.bounceClick
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
private val Dusk = listOf(Color(0xFF6C5CE7), Color(0xFFB06AB3), Color(0xFFF29E7A), Color(0xFF6C5CE7))

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
    // The ring: what's picked while setting, what's left while running.
    val ring = when {
        endOfSong -> 1f
        running -> left / 60_000f / MAX_MIN
        else -> pick / MAX_MIN.toFloat()
    }
    val shown by animateFloatAsState(ring.coerceIn(0f, 1f), spring(dampingRatio = 0.8f, stiffness = 300f), label = "ring")

    Row(Modifier.padding(horizontal = 22.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        FloatingMoon()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Sleep timer", style = BopType.Title, color = Bop.Text)
            Text("Fades out gently, then pauses.", style = BopType.Caption, color = Bop.Text2)
        }
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(230.dp).pointerInput(running) {
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
                val stroke = 16.dp.toPx()
                val r = size.minDimension / 2 - stroke
                val c = Offset(size.width / 2, size.height / 2)
                drawCircle(Bop.Ink.copy(alpha = 0.07f), r, c, style = Stroke(stroke))
                // Hour marks every 15 minutes.
                for (k in 0 until MAX_MIN / 15) {
                    val a = k / (MAX_MIN / 15f) * 2f * Math.PI.toFloat()
                    val p1 = Offset(c.x + sin(a) * (r - stroke), c.y - cos(a) * (r - stroke))
                    val p2 = Offset(c.x + sin(a) * (r - stroke - 5.dp.toPx()), c.y - cos(a) * (r - stroke - 5.dp.toPx()))
                    drawLine(Bop.Ink.copy(alpha = if (k % 4 == 0) 0.35f else 0.15f), p1, p2, 1.5.dp.toPx(), StrokeCap.Round)
                }
                if (shown > 0f) rotate(-90f, c) {
                    drawArc(Brush.sweepGradient(Dusk, c), 0f, 360f * shown, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round))
                }
                val a = shown * 2f * Math.PI.toFloat()
                val k = Offset(c.x + sin(a) * r, c.y - cos(a) * r)
                drawCircle(Color.Black.copy(alpha = 0.12f), stroke * 0.78f, k + Offset(0f, 2.dp.toPx()))
                drawCircle(Color.White, stroke * 0.72f, k)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val big = BopType.Hero.copy(fontSize = 46.sp, fontWeight = FontWeight(700))
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
                        running -> "left · stops ${clock(at!!)}"
                        else -> "minutes · stops ${clock(now + pick * 60_000L)}"
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

@Composable
private fun Preset(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) Bop.Ink else Color.White.copy(alpha = 0.6f), label = "preset")
    Box(modifier.height(42.dp).clip(RoundedCornerShape(14.dp)).background(bg).bounceClick(0.94f, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, style = BopType.BodyStrong, color = if (on) Color.White else Bop.Text, maxLines = 1)
    }
}

@Composable
private fun Pill(label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(52.dp).clip(RoundedCornerShape(50))
            .background(if (primary) Brush.linearGradient(Dusk.take(3)) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.7f))))
            .bounceClick(0.96f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = BopType.BodyStrong, color = if (primary) Color.White else Bop.Text) }
}

/** A moon that bobs gently, with a couple of stars twinkling beside it. */
@Composable
private fun FloatingMoon() {
    val t by produceState(0f) {
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) withInfiniteAnimationFrameMillis { value = (it - start) / 1000f }
    }
    Box(Modifier.size(46.dp).clip(CircleShape).background(Brush.linearGradient(Dusk.take(2))), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Bedtime, null, tint = Color.White, modifier = Modifier.size(22.dp).padding(top = (2f + 2f * sin(t * 1.6f)).dp))
        Canvas(Modifier.fillMaxSize()) {
            listOf(Offset(0.25f, 0.28f) to 0f, Offset(0.76f, 0.22f) to 1.7f, Offset(0.72f, 0.74f) to 3.1f).forEach { (p, off) ->
                val a = 0.35f + 0.65f * ((sin(t * 2.2f + off) + 1f) / 2f)
                drawCircle(Color.White.copy(alpha = a), 1.4.dp.toPx(), Offset(size.width * p.x, size.height * p.y))
            }
        }
    }
}
