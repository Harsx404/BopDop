package com.dopdop.bopdop.ui.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dopdop.bopdop.data.model.formatDuration
import com.dopdop.bopdop.ui.glass.LocalReduceMotion
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Press, then drag: reports the absolute fraction under the finger (0..1) — no drift, taps seek too.
 * With [onLongPress], a press held still for the long-press time calls it instead of seeking.
 */
@Composable
internal fun Modifier.scrub(onStart: () -> Unit, onMove: (Float) -> Unit, onEnd: (Float) -> Unit, inset: Dp = 0.dp, onLongPress: (() -> Unit)? = null): Modifier {
    val start by rememberUpdatedState(onStart)
    val move by rememberUpdatedState(onMove)
    val end by rememberUpdatedState(onEnd)
    val long by rememberUpdatedState(onLongPress)
    return pointerInput(inset, onLongPress != null) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val i = inset.toPx()
            fun frac(x: Float) = ((x - i) / (size.width - 2 * i)).coerceIn(0f, 1f)
            var v = frac(down.position.x)
            down.consume()
            var released = false
            if (long != null) {
                // Wait to see whether this is a tap, a drag, or a long press held still.
                val decided = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: continue
                        if (!c.pressed) { released = true; v = frac(c.position.x); break }
                        if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) { v = frac(c.position.x); break }
                    }
                    true
                }
                if (decided == null) {
                    long?.invoke()
                    do { val e = awaitPointerEvent(); e.changes.forEach { it.consume() } } while (e.changes.any { it.pressed })
                    return@awaitEachGesture
                }
            }
            start(); move(v)
            if (!released) horizontalDrag(down.id) { c -> v = frac(c.position.x); move(v); if (c.positionChange() != Offset.Zero) c.consume() }
            end(v)
        }
    }
}

/**
 * System media volume, step-exact. The slider moves with the finger, but the
 * level always lands on a real device step (with a haptic tick per step), and
 * it follows the hardware buttons via the system's volume broadcast.
 */
@Composable
fun VolumeSlider(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(AudioManager::class.java) }
    val max = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var step by remember { mutableIntStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var raw by remember { mutableFloatStateOf(step / max.toFloat()) }
    var touching by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (touching) return
                step = am.getStreamVolume(AudioManager.STREAM_MUSIC); raw = step / max.toFloat()
            }
        }
        ContextCompat.registerReceiver(ctx, r, IntentFilter("android.media.VOLUME_CHANGED_ACTION"), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { ctx.unregisterReceiver(r) } }
    }
    // Belt and braces for OEMs that don't send the broadcast.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1200)
            if (!touching) { val s = am.getStreamVolume(AudioManager.STREAM_MUSIC); if (s != step) { step = s; raw = s / max.toFloat() } }
        }
    }

    val shown by animateFloatAsState(if (touching) raw else step / max.toFloat(), if (touching) spring(stiffness = 3000f) else tween(160, easing = LinearEasing), label = "vol")
    val grow by animateFloatAsState(if (touching) 1f else 0f, spring(dampingRatio = 0.65f, stiffness = 600f), label = "grow")

    fun apply(v: Float) {
        raw = v
        val s = (v * max).roundToInt().coerceIn(0, max)
        if (s != step) {
            step = s
            am.setStreamVolume(AudioManager.STREAM_MUSIC, s, 0)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (step == 0) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeDown, "Quieter",
            tint = Bop.OnMedia2, modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(40.dp), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.fillMaxWidth().height(40.dp)
                    .scrub(onStart = { touching = true }, onMove = ::apply, onEnd = { apply(it); touching = false; raw = step / max.toFloat() }),
            ) {
                val h = (7.dp + 7.dp * grow).toPx()
                val top = (size.height - h) / 2
                val r = CornerRadius(h / 2)
                drawRoundRect(Color.White.copy(alpha = 0.22f + 0.06f * grow), Offset(0f, top), Size(size.width, h), r)
                // A hairline rim keeps the empty track reading as glass.
                drawRoundRect(Color.White.copy(alpha = 0.18f), Offset(0f, top), Size(size.width, h), r, style = Stroke(0.8.dp.toPx()))
                val fw = (size.width * shown).coerceAtLeast(if (shown > 0f) h else 0f)
                if (fw > 0f) drawRoundRect(Color.White, Offset(0f, top), Size(fw, h), r)
            }
            if (touching) Text(
                "${(step * 100f / max).roundToInt()}%", style = BopType.Micro, color = Bop.Graphite,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp).graphicsLayer { alpha = if (shown > 0.14f) grow else 0f },
            )
        }
        Spacer(Modifier.width(10.dp))
        Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Louder", tint = Bop.OnMedia2, modifier = Modifier.size(22.dp))
    }
}
