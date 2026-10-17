package com.dopdop.bopdop.ui.player

import kotlinx.coroutines.delay
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import android.view.TextureView
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.playback.VideoPlaybackState
import com.dopdop.bopdop.ui.glass.softShadow
import com.dopdop.bopdop.ui.glass.mediaGlass
import com.dopdop.bopdop.ui.theme.BopType
import dev.chrisbanes.haze.HazeState

/**
 * Song ⇄ Video, as a liquid-glass capsule like the transport droplets: the glass track
 * carries a white thumb that slides under the selected mode.
 */
@Composable
fun VideoModeSwitch(video: VideoPlaybackState, haze: HazeState, modifier: Modifier = Modifier) {
    val isVideo = video.active || video.loading
    val segment = 72.dp
    val thumbX by animateDpAsState(if (isVideo) segment else 0.dp, spring(dampingRatio = 0.72f, stiffness = 520f), label = "modeThumb")
    val shape = RoundedCornerShape(50)
    val press = remember { MutableInteractionSource() }
    Box(modifier.mediaGlass(shape, haze, emphasis = 1.1f, interactionSource = press, strong = true).padding(3.dp)) {
        Box(
            Modifier.offset(x = thumbX).width(segment).height(28.dp)
                .softShadow(shape, 10.dp, color = Color.Black, alpha = 0.14f, dy = 2.dp)
                .clip(shape).background(Color.White),
        )
        Row {
            listOf(false, true).forEach { option ->
                val selected = option == isVideo
                Row(
                    Modifier.width(segment).height(28.dp).clip(shape)
                        .selectable(selected, interactionSource = press, indication = null, role = Role.Tab) { PlayerConnection.setVideoEnabled(option) },
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                ) {
                    val ink = if (selected) Color(0xFF15161B) else Color.White
                    if (option && video.loading) CircularProgressIndicator(Modifier.size(11.dp), color = ink, strokeWidth = 1.5.dp)
                    else Icon(if (option) Icons.Rounded.Videocam else Icons.Rounded.MusicNote, null, Modifier.size(13.dp), tint = ink)
                    Text(if (option && video.loading) "Finding…" else if (option) "Video" else "Song", style = BopType.Caption.copy(fontSize = 11.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight(600)), color = ink, maxLines = 1)
                }
            }
        }
    }
}

/**
 * The music video standing in for the cover: same full-bleed frame, centre-cropped to fill it the
 * way the artwork is, so the hero's own fade carries it into the wash. Videos with black bars
 * baked into the picture (cinema 2.39:1 inside 16:9, or 4:3 pillarboxes) are measured from a few
 * sampled frames and zoomed past the bars, so the frame never shows a dark band.
 */
@Composable
fun MusicVideoHero(video: VideoPlaybackState, modifier: Modifier = Modifier) {
    val playing by PlayerConnection.isPlaying.collectAsState()
    val buffering by PlayerConnection.buffering.collectAsState()
    var view by remember { mutableStateOf<TextureView?>(null) }
    var bars by remember(video.trackId) { mutableStateOf(video.trackId?.let { measuredBars[it] } ?: Bars.NONE) }
    // Sample small frames a few times; only bars present in every sample count (a dark scene isn't a
    // bar). Once measured, a song's bars are kept so switching Song -> Video again crops at once.
    LaunchedEffect(video.trackId, view) {
        val tv = view ?: return@LaunchedEffect
        val id = video.trackId ?: return@LaunchedEffect
        if (id in measuredBars) return@LaunchedEffect
        var seen: Bars? = null
        var samples = 0
        repeat(8) {
            delay(if (seen == null) 1800 else 2600)
            if (!PlayerConnection.isPlaying.value) return@repeat
            val bmp = runCatching { tv.getBitmap(64, 36) }.getOrNull() ?: return@repeat
            val px = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
            val now = detectBars(px, bmp.width, bmp.height)
            bmp.recycle()
            if (now == null) return@repeat
            seen = seen?.intersect(now) ?: now
            if (++samples >= 2) { bars = seen!!; measuredBars[id] = seen!! }
        }
    }
    val crop by animateFloatAsState(if (bars == Bars.NONE) 0f else 1f, tween(600), label = "crop")
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds().background(Color.Black), contentAlignment = Alignment.Center) {
        val ar = video.aspectRatio.coerceIn(0.3f, 3f)
        // The part of the picture that isn't bar, eased in once measured.
        val cw = 1f - (bars.left + bars.right) * crop
        val ch = 1f - (bars.top + bars.bottom) * crop
        val contentAr = ar * cw / ch
        // Cover-fit the content: whichever side is short grows until the frame is filled.
        val contentH = maxOf(maxHeight, maxWidth / contentAr)
        val picH = contentH / ch
        val picW = contentH * contentAr / cw
        AndroidView(
            factory = { context -> TextureView(context).also { view = it; PlayerConnection.attachVideoSurface(it) } },
            modifier = Modifier.requiredSize(picW, picH).offset(
                // Uneven bars shift the content off-centre; move the picture back the other way.
                x = picW * (bars.right - bars.left) * crop / 2,
                y = picH * (bars.bottom - bars.top) * crop / 2,
            ),
            onReset = null,
            onRelease = { it.keepScreenOn = false; PlayerConnection.detachVideoSurface(it); if (view === it) view = null },
            update = { it.keepScreenOn = playing; PlayerConnection.attachVideoSurface(it) },
        )
        if (buffering) CircularProgressIndicator(Modifier.size(32.dp), color = Color.White, strokeWidth = 2.dp)
    }
}

/** Bars already measured for each song's video. */
private val measuredBars = HashMap<String, Bars>()

/** Fractions of the frame taken by black bars on each side. */
internal data class Bars(val top: Float, val bottom: Float, val left: Float, val right: Float) {
    /** Bars seen in both samples. */
    fun intersect(o: Bars) = Bars(minOf(top, o.top), minOf(bottom, o.bottom), minOf(left, o.left), minOf(right, o.right))

    companion object { val NONE = Bars(0f, 0f, 0f, 0f) }
}

/**
 * Rows/columns where nearly every pixel is near-black, counted in from each edge. Bars under 3% are
 * ignored (compression noise), and no side may take more than 30%.
 */
internal fun detectBars(argb: IntArray, w: Int, h: Int): Bars? {
    fun dark(c: Int): Boolean {
        val r = c shr 16 and 0xFF; val g = c shr 8 and 0xFF; val b = c and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000 < 22
    }
    fun rowDark(y: Int) = (0 until w).count { dark(argb[y * w + it]) } >= w * 0.96
    fun colDark(x: Int) = (0 until h).count { dark(argb[it * w + x]) } >= h * 0.96
    fun edge(n: Int, isDark: (Int) -> Boolean, fromStart: Boolean): Float {
        var k = 0
        while (k < n * 0.3 && isDark(if (fromStart) k else n - 1 - k)) k++
        return (k.toFloat() / n).takeIf { it >= 0.03f } ?: 0f
    }
    // A completely black frame (a fade, or a surface not drawn yet) says nothing about bars.
    if ((0 until h).all(::rowDark)) return null
    return Bars(edge(h, ::rowDark, true), edge(h, ::rowDark, false), edge(w, ::colDark, true), edge(w, ::colDark, false))
}
