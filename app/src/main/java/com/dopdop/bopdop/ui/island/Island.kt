package com.dopdop.bopdop.ui.island

import android.graphics.Rect
import android.os.Build
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import com.dopdop.bopdop.ui.festive.ThemedIcon as Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.model.formatDuration
import com.dopdop.bopdop.ui.components.Artwork
import com.dopdop.bopdop.ui.theme.BopType
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The camera hole the island wraps around, in screen pixels: a punch-hole or notch at the top
 * edge, the centred one if there are several. Null when the screen has none (or Android < 9).
 */
fun topCutout(rects: List<Rect>, screenWidth: Int): Rect? =
    rects.filter { it.top <= 8 && it.height() in 1..300 && it.width() < screenWidth * 0.6 }
        .minByOrNull { kotlin.math.abs(it.centerX() - screenWidth / 2) }

/**
 * The camera hole itself. The bounding box Android reports for a punch-hole runs from the top
 * edge of the screen down to the hole, so on Android 12+ the cutout's real outline is used to
 * centre the island on the camera.
 */
fun cameraHole(cutout: android.view.DisplayCutout, screenWidth: Int): Rect? {
    val raw = (if (Build.VERSION.SDK_INT >= 31) cutout.cutoutPath?.let { path ->
        val b = android.graphics.RectF()
        path.computeBounds(b, true)
        if (!b.isEmpty && b.top < 400f && b.width() < screenWidth * 0.6f)
            Rect(b.left.toInt(), b.top.toInt(), kotlin.math.ceil(b.right).toInt(), kotlin.math.ceil(b.bottom).toInt()) else null
    } else null) ?: topCutout(cutout.boundingRects, screenWidth) ?: return null
    return punchHole(raw)
}

/**
 * A punch-hole is round, but some phones (vivo, iQOO, OPPO) report its box from the top edge of
 * the screen down to the bottom of the hole: 90 x 120 px for a 50 px camera. A box much taller
 * than it is wide is read as the round hole at its foot, so the island hugs the camera instead of
 * swallowing the status bar.
 */
internal fun punchHole(r: Rect): Rect = holeTop(r.left, r.top, r.right, r.bottom).let { top -> if (top == r.top) r else Rect(r.left, top, r.right, r.bottom) }

/** The top edge of the hole inside a reported cutout box (see [punchHole]). */
internal fun holeTop(left: Int, top: Int, right: Int, bottom: Int): Int {
    val w = right - left
    return if (w <= 0 || bottom - top <= w * 1.15f) top else bottom - w
}

/**
 * The camera cutout of the window this view is in. Re-read whenever Compose sees the cutout
 * insets change (they aren't known until the window is laid out).
 */
@Composable
fun rememberCutout(): Rect? {
    val view = LocalView.current
    val top = androidx.compose.foundation.layout.WindowInsets.displayCutout.getTop(LocalDensity.current)
    return remember(top, view.rootView.width) { readCutout(view) }
}

private fun readCutout(view: View): Rect? {
    if (Build.VERSION.SDK_INT < 28) return null
    val cutout = view.rootWindowInsets?.displayCutout ?: return null
    return cameraHole(cutout, view.rootView.width.takeIf { it > 0 } ?: view.resources.displayMetrics.widthPixels)
}

/** Height of the invisible tab under the collapsed pill that catches taps the status bar would eat. */
val TAP_ZONE = 22.dp

/** Where the collapsed island sits on screen, tap tab included, in pixels. */
fun collapsedBounds(cut: Rect, density: Float, screenWidthPx: Int): Rect {
    val pillH = maxOf(cut.height() + 4 * density, 28 * density)
    val w = cut.width() + pillH * 1.5f
    val minLeft = (10 * density).roundToInt()
    val left = (cut.centerX() - w / 2).roundToInt().coerceIn(minLeft, maxOf(minLeft, (screenWidthPx - w - 10 * density).roundToInt()))
    val top = (cut.centerY() - pillH / 2).roundToInt().coerceAtLeast(0)
    return Rect(left, top, (left + w).roundToInt(), (top + pillH + TAP_ZONE.value * density).roundToInt())
}

/** What the island shows, and what its buttons do; the app and the overlay each supply their own. */
class IslandModel(
    val track: Track?,
    val playing: Boolean,
    val positionMs: () -> Long,
    val durationMs: () -> Long,
    val toggle: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val open: () -> Unit,
)

/**
 * A Dynamic-Island-style pill around the camera [cut]: the cover on the left of the camera and
 * a little equaliser on the right. Tap it to grow into a card with the song, a progress line and
 * the controls; tap again (or outside, see [onExpandedChange]) to shrink back. Black, so the
 * camera disappears into it.
 *
 * The status bar sits above apps and takes every tap in its strip, so a collapsed pill there
 * can't be tapped; a thin invisible tab hangs just below it ([TAP_ZONE]) to catch the tap.
 * [windowLeftPx] is where the hosting window starts on screen (the overlay's window is only as
 * wide as the pill).
 */
@Composable
fun IslandPill(model: IslandModel, cut: Rect, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, screenWidthPx: Int, windowLeftPx: Int = 0) {
    val t = model.track ?: return
    val d = LocalDensity.current
    val cutW = with(d) { cut.width().toDp() }
    val cutH = with(d) { cut.height().toDp() }
    val pillH = maxOf(cutH + 4.dp, 28.dp)
    val screenW = with(d) { screenWidthPx.toDp() }
    val w by animateDpAsState(if (expanded) screenW - 24.dp else cutW + pillH * 1.5f, spring(dampingRatio = 0.78f, stiffness = 420f), label = "islandW")
    val h by animateDpAsState(if (expanded) 178.dp else pillH, spring(dampingRatio = 0.78f, stiffness = 420f), label = "islandH")
    val corner by animateDpAsState(if (expanded) 38.dp else pillH / 2, spring(dampingRatio = 0.85f, stiffness = 420f), label = "islandR")
    val topPx = (cut.centerY() - with(d) { pillH.toPx() } / 2).roundToInt().coerceAtLeast(0)
    // The spring overshoots while opening, so the right-hand limit can briefly pass the left one.
    val minLeft = with(d) { 10.dp.roundToPx() }
    val maxLeft = maxOf(minLeft, screenWidthPx - with(d) { (w + 10.dp).roundToPx() })
    val leftPx = (cut.centerX() - with(d) { w.toPx() } / 2).roundToInt().coerceIn(minLeft, maxLeft) - windowLeftPx
    val shape = RoundedCornerShape(corner)
    if (!expanded) Box(
        Modifier.offset { IntOffset(leftPx, topPx + with(d) { pillH.roundToPx() }) }.width(w).height(TAP_ZONE)
            .pointerInput(Unit) { detectTapGestures(onTap = { onExpandedChange(true) }, onLongPress = { model.open() }) },
    )
    Box(
        Modifier.offset { IntOffset(leftPx, topPx) }.width(w).height(h).clip(shape)
            // Black where it meets the camera; opened, it becomes charcoal glass with a hairline edge.
            .background(if (expanded) Brush.verticalGradient(listOf(Color(0xFF000000), Color(0xFF1A191D))) else Brush.verticalGradient(listOf(Color.Black, Color.Black)))
            .then(if (expanded) Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), shape) else Modifier)
            .pointerInput(expanded) { detectTapGestures(onTap = { onExpandedChange(!expanded) }, onLongPress = { model.open() }) },
    ) {
        // Collapsed: cover (ringed by the song's progress) · camera · bars.
        AnimatedVisibility(!expanded, enter = fadeIn(), exit = fadeOut()) {
            val pos by produceState(model.positionMs(), model.playing, t.id) { while (true) { value = model.positionMs(); kotlinx.coroutines.delay(1000) } }
            val frac = (pos.toFloat() / model.durationMs().coerceAtLeast(1)).coerceIn(0f, 1f)
            Row(Modifier.fillMaxWidth().height(pillH).padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(pillH - 6.dp), contentAlignment = Alignment.Center) {
                    Artwork(t.artwork, Modifier.size(pillH - 11.dp), circle = true, size = 120)
                    Canvas(Modifier.fillMaxSize()) {
                        val sw = 2.2.dp.toPx()
                        val inset = androidx.compose.ui.geometry.Offset(sw / 2, sw / 2)
                        val arc = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw)
                        drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, inset, arc, style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
                        drawArc(ISLAND_ACCENT, -90f, 360f * frac, false, inset, arc, style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = StrokeCap.Round))
                    }
                }
                Spacer(Modifier.weight(1f))
                Bars(model.playing, Modifier.padding(end = 5.dp).size(width = pillH * 0.36f, height = pillH * 0.34f))
            }
        }
        AnimatedVisibility(expanded, enter = fadeIn(), exit = fadeOut()) {
            Expanded(model, t, pillH)
        }
    }
}

@Composable
private fun Expanded(model: IslandModel, t: Track, pillH: Dp) {
    val pos by produceState(model.positionMs(), model.playing) { while (true) { value = model.positionMs(); kotlinx.coroutines.delay(500) } }
    val dur = model.durationMs().coerceAtLeast(1)
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = pillH + 2.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clickable(remember { MutableInteractionSource() }, null) { model.open() }) {
                Artwork(t.artwork, Modifier.size(60.dp), corner = 16.dp, size = 200, elevation = 10.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, style = BopType.Headline, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.artist, style = BopType.Body, color = Color.White.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Bars(model.playing, Modifier.size(22.dp))
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatDuration((pos / 1000).toInt()).ifEmpty { "0:00" }, style = BopType.Micro, color = Color.White.copy(alpha = 0.6f))
            val f = (pos.toFloat() / dur).coerceIn(0f, 1f)
            Canvas(Modifier.weight(1f).padding(horizontal = 10.dp).height(14.dp)) {
                val y = size.height / 2; val sw = 5.dp.toPx()
                drawLine(Color.White.copy(alpha = 0.18f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), sw, StrokeCap.Round)
                drawLine(ISLAND_ACCENT, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width * f, y), sw, StrokeCap.Round)
                drawCircle(Color.White, 6.dp.toPx(), androidx.compose.ui.geometry.Offset(size.width * f, y))
            }
            Text(formatDuration((dur / 1000).toInt()).ifEmpty { "–:––" }, style = BopType.Micro, color = Color.White.copy(alpha = 0.6f))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Control(Icons.Rounded.SkipPrevious, "Previous", 30.dp) { model.previous() }
            Box(
                Modifier.size(50.dp).clip(CircleShape).background(Color.White).clickable { model.toggle() },
                contentAlignment = Alignment.Center,
            ) { Icon(if (model.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (model.playing) "Pause" else "Play", tint = Color.Black, modifier = Modifier.size(30.dp)) }
            Control(Icons.Rounded.SkipNext, "Next", 30.dp) { model.next() }
        }
    }
}

@Composable
private fun Control(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size + 12.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(size)) }
}

/** BopDop orange, for the island's progress and bars. */
private val ISLAND_ACCENT = Color(0xFFFF5A36)

/** Four little equaliser bars that dance while music plays and rest low when paused. */
@Composable
private fun Bars(playing: Boolean, modifier: Modifier) {
    val t by produceState(0f, playing) {
        if (!playing) return@produceState
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) withInfiniteAnimationFrameMillis { value = (it - start) / 1000f }
    }
    Canvas(modifier) {
        val n = 4
        val gap = size.width / (n * 2f)
        val bw = gap
        for (i in 0 until n) {
            val f = if (playing) 0.3f + 0.7f * ((sin(t * (6f + i * 1.7f) + i) + 1f) / 2f) else 0.25f
            val h = size.height * f
            val x = gap / 2 + i * (bw + gap) + bw / 2
            drawLine(ISLAND_ACCENT, androidx.compose.ui.geometry.Offset(x, size.height / 2 - h / 2),
                androidx.compose.ui.geometry.Offset(x, size.height / 2 + h / 2), bw, StrokeCap.Round)
        }
    }
}
