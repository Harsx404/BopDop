package com.dopdop.bopdop.ui.player

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dopdop.bopdop.data.MusicRepository
import com.dopdop.bopdop.data.model.LyricLine
import com.dopdop.bopdop.data.model.Lyrics
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.components.shimmer
import com.dopdop.bopdop.ui.glass.ArtPalette
import com.dopdop.bopdop.ui.glass.LocalReduceMotion
import com.dopdop.bopdop.ui.theme.BopType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private sealed interface LyricState {
    data object Loading : LyricState
    data object None : LyricState
    data class Ready(val l: Lyrics) : LyricState
}

private val LyricStyle = BopType.Lyric.copy(fontSize = 31.sp, lineHeight = 39.sp, fontWeight = FontWeight(760))

/** Soft fade at the top and bottom so lines dissolve instead of being cut. */
private fun Modifier.edgeFade(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 0.10f to Color.Black, 0.80f to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
fun LyricsView(t: Track, palette: ArtPalette, modifier: Modifier = Modifier) {
    val state by produceState<LyricState>(LyricState.Loading, t.id) {
        value = MusicRepository.lyrics(t)?.let { LyricState.Ready(it) } ?: LyricState.None
    }
    Box(modifier.fillMaxWidth()) {
        when (val s = state) {
            LyricState.Loading -> Column(Modifier.padding(horizontal = 26.dp, vertical = 30.dp)) {
                repeat(5) {
                    Box(Modifier.fillMaxWidth(0.45f + (it % 3) * 0.18f).height(28.dp).shimmer(10.dp))
                    Spacer(Modifier.height(20.dp))
                }
            }
            LyricState.None -> Column(Modifier.fillMaxSize().padding(30.dp), verticalArrangement = Arrangement.Center) {
                BreathingDots(active = true, modifier = Modifier.padding(bottom = 18.dp))
                Text("No lyrics for this one", style = LyricStyle.copy(fontSize = 26.sp), color = Color.White.copy(alpha = 0.9f))
                Text("Just feel it.", style = BopType.Headline, color = Color.White.copy(alpha = 0.55f))
            }
            is LyricState.Ready -> if (s.l.isSynced) SyncedLyrics(s.l.synced, t) else PlainLyrics(s.l.plain.orEmpty(), "unsynced · drifts with the song")
        }
    }
}

/** Unsynced lyrics drift down with the song's progress; a touch pauses the drift for a few seconds. */
@Composable
private fun PlainLyrics(text: String, source: String) {
    val scroll = rememberScrollState()
    var touchedAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(scroll.isScrollInProgress) { if (scroll.isScrollInProgress) touchedAt = System.currentTimeMillis() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(900)
            val d = PlayerConnection.duration
            if (d <= 0 || System.currentTimeMillis() - touchedAt < 5000 || scroll.isScrollInProgress) continue
            val f = ((PlayerConnection.position.toFloat() / d - 0.08f) / 0.84f).coerceIn(0f, 1f)
            scroll.animateScrollTo((scroll.maxValue * f).toInt(), tween(900))
        }
    }
    Column(Modifier.fillMaxSize().edgeFade().verticalScroll(scroll).padding(horizontal = 26.dp, vertical = 40.dp)) {
        Text("LYRICS · $source", style = BopType.Micro, color = Color.White.copy(alpha = 0.5f))
        Spacer(Modifier.height(16.dp))
        text.trim().split(Regex("\\n\\s*\\n")).forEach { stanza ->
            Text(stanza.trim(), style = LyricStyle.copy(fontSize = 24.sp, lineHeight = 34.sp), color = Color.White.copy(alpha = 0.9f))
            Spacer(Modifier.height(28.dp))
        }
        Spacer(Modifier.height(160.dp))
    }
}

/** One rendered row: a lyric, or an instrumental gap shown as breathing dots. */
private data class LyricRow(val start: Long, val end: Long, val text: String) {
    val isGap get() = text.isBlank() || text == "♪"
}

private fun rowsOf(lines: List<LyricLine>): List<LyricRow> {
    val out = mutableListOf<LyricRow>()
    val first = lines.firstOrNull()?.timeMs ?: 0
    if (first > 4000) out += LyricRow(0, first, "")
    lines.forEachIndexed { i, l ->
        val end = lines.getOrNull(i + 1)?.timeMs ?: (l.timeMs + 6000)
        if (l.text.isBlank() && end - l.timeMs < 2500) return@forEachIndexed // too short to show dots
        out += LyricRow(l.timeMs, end, l.text)
    }
    return out
}

/**
 * Apple-style synced lyrics:
 * - the active line fills word by word as it is sung (glow on the sung words);
 * - lines glide with springs whose stiffness falls off below the active line,
 *   so the block "trails" like a ribbon;
 * - distance blur, past lines dimmer than upcoming ones;
 * - drag to browse, it settles back after a moment; tap any line to jump.
 */
@Composable
private fun SyncedLyrics(lines: List<LyricLine>, t: Track) {
    val rows = remember(lines) { rowsOf(lines) }
    val reduce = LocalReduceMotion.current
    val pos by produceState(0L, t.id) {
        while (true) { value = PlayerConnection.position + 150; delay(50) }
    }
    val active = remember(pos, rows) { rows.indexOfLast { it.start <= pos }.coerceAtLeast(0) }
    val tops = remember(rows) { mutableStateMapOf<Int, Float>() }
    val user = remember { Animatable(0f) }
    var lastDrag by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(lastDrag, dragging) {
        if (dragging || lastDrag == 0L) return@LaunchedEffect
        delay(2800)
        user.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 120f))
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().clipToBounds().edgeFade()
            .pointerInput(rows) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false; lastDrag = System.currentTimeMillis() },
                    onDragCancel = { dragging = false; lastDrag = System.currentTimeMillis() },
                ) { change, d -> change.consume(); scope.launch { user.snapTo(user.value + d) } }
            },
    ) {
        val anchor = constraints.maxHeight * 0.26f
        val target = anchor - (tops[active] ?: 0f)
        Column(
            Modifier.fillMaxWidth().wrapContentHeight(align = Alignment.Top, unbounded = true).padding(horizontal = 24.dp),
        ) {
            rows.forEachIndexed { i, row ->
                val dist = i - active
                // Lines below the active one respond a little later — a ribbon, not a slab.
                val stiffness = if (reduce) 1000f else (230f - dist.coerceIn(0, 8) * 18f)
                val y by animateFloatAsState(target, spring(dampingRatio = 0.84f, stiffness = stiffness), label = "ly$i")
                val on = i == active
                val alpha by animateFloatAsState(
                    when { on -> 1f; dist < 0 -> 0.32f; else -> (0.55f - dist * 0.06f).coerceAtLeast(0.26f) }, tween(420), label = "la$i",
                )
                val scale by animateFloatAsState(if (on) 1f else 0.955f, spring(dampingRatio = 0.75f, stiffness = 260f), label = "ls$i")
                val blur = if (Build.VERSION.SDK_INT >= 31 && !dragging && user.value == 0f && !on) (abs(dist).coerceAtMost(4) * 0.7f).dp else 0.dp
                Box(
                    Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { tops[i] = it.positionInParent().y }
                        .graphicsLayer {
                            translationY = y + user.value
                            scaleX = scale; scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            this.alpha = alpha
                        }
                        .then(if (blur > 0.dp) Modifier.blur(blur) else Modifier)
                        .clip(RoundedCornerShape(16.dp))
                        .bounceClick(0.98f) { scope.launch { user.snapTo(0f) }; PlayerConnection.seekTo(row.start) }
                        .padding(vertical = if (row.isGap) 14.dp else 10.dp),
                ) {
                    if (row.isGap) BreathingDots(active = on, progress = if (on) ((pos - row.start).toFloat() / (row.end - row.start).coerceAtLeast(1)).coerceIn(0f, 1f) else 0f)
                    else Text(
                        if (on) karaoke(row.text, ((pos - row.start).toFloat() / (row.end - row.start).coerceIn(600, 9000)).coerceIn(0f, 1f)) else AnnotatedString(row.text),
                        style = LyricStyle.copy(shadow = if (on) Shadow(Color.White.copy(alpha = 0.45f), Offset.Zero, 22f) else null),
                        color = Color.White,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Lyrics via LRCLIB", style = BopType.Caption, color = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.graphicsLayer { translationY = target + user.value })
            Spacer(Modifier.height(420.dp))
        }
    }
}

/** Words light up in sequence; the word being sung ramps from dim to full. */
private fun karaoke(text: String, frac: Float): AnnotatedString = buildAnnotatedString {
    val total = text.length.coerceAtLeast(1)
    // Lines are usually sung a bit faster than their slot — finish the sweep at 85%.
    val sung = (frac / 0.85f).coerceAtMost(1f) * total
    var i = 0
    Regex("\\S+\\s*").findAll(text).forEach { m ->
        val start = m.range.first; val end = m.range.last + 1
        if (start > i) append(text.substring(i, start))
        val p = ((sung - start) / (end - start).coerceAtLeast(1)).coerceIn(0f, 1f)
        withStyle(SpanStyle(color = Color.White.copy(alpha = 0.42f + 0.58f * p))) { append(m.value) }
        i = end
    }
    if (i < text.length) append(text.substring(i))
}

/** Three dots that breathe; during an instrumental gap they fill one by one. */
@Composable
private fun BreathingDots(active: Boolean, modifier: Modifier = Modifier, progress: Float = 0f) {
    val t = rememberInfiniteTransition(label = "dots")
    val breath by t.animateFloat(0.88f, 1.12f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    Row(modifier.graphicsLayer { val s = if (active) breath else 1f; scaleX = s; scaleY = s; transformOrigin = TransformOrigin(0f, 0.5f) }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) { k ->
            val fill = ((progress * 3f) - k).coerceIn(0f, 1f)
            Box(Modifier.size(13.dp).clip(CircleShape).background(Color.White.copy(alpha = if (active) 0.35f + 0.65f * fill else 0.3f)))
        }
    }
}
