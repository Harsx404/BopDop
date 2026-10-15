package com.dopdop.bopdop.ui.player

import android.media.AudioManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import com.dopdop.bopdop.ui.components.LinePlayPause
import com.dopdop.bopdop.ui.components.LineSkip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.model.formatDuration
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.playback.RepeatMode
import com.dopdop.bopdop.playback.StreamQuality
import com.dopdop.bopdop.playback.StreamResolver
import com.dopdop.bopdop.ui.AppState
import com.dopdop.bopdop.ui.NowPlayingPage
import com.dopdop.bopdop.ui.Sheet
import com.dopdop.bopdop.ui.components.Artwork
import com.dopdop.bopdop.ui.components.PlayPauseGlyph
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.glass.ArtPalette
import com.dopdop.bopdop.ui.glass.LocalReduceMotion
import com.dopdop.bopdop.ui.glass.mediaGlass
import com.dopdop.bopdop.ui.glass.rememberArtPalette
import com.dopdop.bopdop.ui.glass.softShadow
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun NowPlaying(app: AppState) {
    // The expand spring overshoots past 1 — clamp so radius/offset never go negative.
    val p = app.player.value.coerceIn(0f, 1f)
    val track by PlayerConnection.current.collectAsState()
    val t = track ?: return
    if (p <= 0.001f) return
    val palette = rememberArtPalette(t.artwork)
    val wash by animateColorAsState(palette.wash, tween(900), label = "wash")
    val washDeep by animateColorAsState(palette.washDeep, tween(900), label = "washDeep")
    val haze = rememberHazeState()
    val scope = rememberCoroutineScope()
    val dim by animateFloatAsState(
        when (app.nowPlayingPage) { NowPlayingPage.ART -> 0f; NowPlayingPage.LYRICS -> 0.42f; NowPlayingPage.QUEUE -> 0.62f }, tween(420), label = "dim",
    )
    // Lyrics/queue: the artwork melts into a soft colour field behind the text.
    val artBlur by animateFloatAsState(if (app.nowPlayingPage == NowPlayingPage.ART) 0f else 46f, tween(520), label = "artBlur")

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = constraints.maxHeight.toFloat()
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = (1f - p) * h
                    val r = lerp(40.dp.toPx(), 0f, p).coerceAtLeast(0f)
                    shape = RoundedCornerShape(r, r, 0f, 0f)
                    clip = true
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { if (app.player.value < 0.82f) app.collapsePlayer() else app.expandPlayer(app.nowPlayingPage) },
                    ) { change, d ->
                        change.consume()
                        scope.launch { app.player.snapTo((app.player.value - d / h).coerceIn(0f, 1f)) }
                    }
                },
        ) {
            // --- Backdrop: artwork full-bleed on top, melting into its own colour ---
            Box(Modifier.fillMaxSize().hazeSource(haze)) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to wash, 0.64f to wash, 1f to washDeep)))
                AnimatedContent(
                    targetState = t.artwork,
                    transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(700)) },
                    label = "bgart",
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.86f).clipToBounds()
                        .then(if (android.os.Build.VERSION.SDK_INT >= 31 && artBlur > 0.5f) Modifier.blur(artBlur.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Rectangle) else Modifier),
                ) { art -> KenBurnsArt(art) }
                Box(
                    Modifier.fillMaxWidth().aspectRatio(0.86f).background(
                        Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.28f), 0.2f to Color.Transparent, 0.5f to Color.Transparent, 0.86f to wash.copy(alpha = 0.85f), 1f to wash),
                    ),
                )
                Box(Modifier.fillMaxSize().background(washDeep.copy(alpha = dim)))
            }

            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
                TopRow(app, haze)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AnimatedContent(
                        targetState = app.nowPlayingPage,
                        transitionSpec = { (fadeIn(tween(260)) + scaleIn(tween(320), 0.97f)) togetherWith fadeOut(tween(180)) },
                        label = "np",
                    ) { page ->
                        when (page) {
                            NowPlayingPage.ART -> ArtGestureArea(t)
                            NowPlayingPage.LYRICS -> Column { SongHeader(t, app, haze); LyricsView(t, palette, Modifier.weight(1f)) }
                            NowPlayingPage.QUEUE -> Column { SongHeader(t, app, haze); QueueView(app, Modifier.weight(1f)) }
                        }
                    }
                }
                BottomPanel(app, t, palette, haze)
            }
        }
    }
}

/** Slow Ken Burns drift while playing — the photo feels alive, like the reference's hero shots. */
@Composable
private fun KenBurnsArt(art: String) {
    val playing by PlayerConnection.isPlaying.collectAsState()
    val reduce = LocalReduceMotion.current
    val zoom = remember { Animatable(1.02f) }
    LaunchedEffect(playing, reduce) {
        if (!playing || reduce) return@LaunchedEffect
        while (true) {
            zoom.animateTo(1.12f, tween(16000))
            zoom.animateTo(1.02f, tween(16000))
        }
    }
    Artwork(art, Modifier.fillMaxSize().graphicsLayer { scaleX = zoom.value; scaleY = zoom.value }, corner = 0.dp, size = 1200)
}

/** Empty space over the artwork: swipe to skip, double-tap to like (with a heart burst). */
@Composable
private fun ArtGestureArea(t: Track) {
    val scope = rememberCoroutineScope()
    val heart = remember { Animatable(0f) }
    var dx by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(t.id) {
                detectHorizontalDragGestures(onDragEnd = {
                    if (dx < -140f) PlayerConnection.next() else if (dx > 140f) PlayerConnection.previous()
                    dx = 0f
                }) { change, d -> change.consume(); dx += d }
            }
            .pointerInput(t.id) {
                detectTapGestures(onDoubleTap = {
                    if (!LibraryStore.isLiked(t.id)) LibraryStore.toggleLike(t)
                    scope.launch { heart.snapTo(0.01f); heart.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f)); delay(450); heart.animateTo(0f, tween(250)) }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        if (heart.value > 0f) Icon(
            Icons.Rounded.Favorite, null, tint = Color.White,
            modifier = Modifier.size(110.dp).graphicsLayer { scaleX = heart.value; scaleY = heart.value; alpha = heart.value.coerceAtMost(1f) },
        )
    }
}

@Composable
private fun TopRow(app: AppState, haze: HazeState) {
    val ctx by PlayerConnection.context.collectAsState()
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        MediaCircle(Icons.Rounded.KeyboardArrowDown, "Close", haze, 46.dp) { app.collapsePlayer() }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PLAYING FROM", style = BopType.Micro, color = Bop.OnMedia3)
            Text(ctx.ifBlank { "Your queue" }, style = BopType.BodyStrong, color = Bop.OnMedia, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 12.dp))
        }
        MediaCircle(Icons.Rounded.MoreHoriz, "More", haze, 46.dp) { PlayerConnection.current.value?.let { app.sheet = Sheet.TrackOptions(it) } }
    }
}

@Composable
private fun BottomPanel(app: AppState, t: Track, palette: ArtPalette, haze: HazeState) {
    val playing by PlayerConnection.isPlaying.collectAsState()
    val buffering by PlayerConnection.buffering.collectAsState()
    val shuffle by PlayerConnection.shuffle.collectAsState()
    val repeat by PlayerConnection.repeat.collectAsState()
    val sleepAt by PlayerConnection.sleepAt.collectAsState()
    val ctxLabel by PlayerConnection.context.collectAsState()
    val lib by LibraryStore.state.collectAsState()
    val quality by StreamResolver.quality.collectAsState()
    val liked = t.id in lib.likedIds
    val onArt = app.nowPlayingPage == NowPlayingPage.ART

    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 10.dp)) {
        AnimatedVisibility(onArt, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MediaChip(ctxLabel.ifBlank { "Now playing" }, haze)
                    quality[t.id]?.let { QualityChip(it, haze) }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        AnimatedContent(t.title, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }, label = "title") { title ->
                            Text(title, style = BopType.Display.copy(fontSize = BopType.Display.fontSize * 1.15f), color = Bop.OnMedia, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            t.artist, style = BopType.Headline.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(500)), color = Bop.OnMedia2,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp).bounceClick { t.artistId?.let { id -> app.collapsePlayer(); app.openArtist(id, t.artist.split(",").first()) } },
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    LikeCircle(t, liked, haze, app)
                }
                Spacer(Modifier.height(18.dp))
            }
        }
        ProgressPill(t, haze)
        AnimatedVisibility(onArt, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column { Spacer(Modifier.height(10.dp)); VolumePill(haze) }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SmallToggle(Icons.Rounded.Shuffle, "Shuffle", shuffle) { PlayerConnection.toggleShuffle() }
            Spacer(Modifier.weight(1f))
            TransportCluster(playing, buffering, haze, app)
            Spacer(Modifier.weight(1f))
            SmallToggle(if (repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat", repeat != RepeatMode.OFF) { PlayerConnection.cycleRepeat() }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            PageToggle(Icons.Rounded.Lyrics, "Lyrics", app.nowPlayingPage == NowPlayingPage.LYRICS, haze) {
                app.nowPlayingPage = if (app.nowPlayingPage == NowPlayingPage.LYRICS) NowPlayingPage.ART else NowPlayingPage.LYRICS
            }
            if (!onArt) PageToggle(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Like", liked, haze) { LibraryStore.toggleLike(t) }
            PageToggle(Icons.Rounded.Bedtime, "Sleep", sleepAt != null, haze) { app.sheet = Sheet.SleepTimer }
            PageToggle(Icons.AutoMirrored.Rounded.QueueMusic, "Queue", app.nowPlayingPage == NowPlayingPage.QUEUE, haze) {
                app.nowPlayingPage = if (app.nowPlayingPage == NowPlayingPage.QUEUE) NowPlayingPage.ART else NowPlayingPage.QUEUE
            }
        }
    }
}

// --- Liquid transport: three glass droplets fused together ------------------------------

@Composable
private fun TransportCluster(playing: Boolean, buffering: Boolean, haze: HazeState, app: AppState) {
    val haptics = LocalHapticFeedback.current
    // Three near-equal droplets fused by overlap, like the reference: glass · white · glass.
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((-12).dp)) {
        SideDrop(next = false, haze = haze,
            onLong = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); PlayerConnection.seekTo((PlayerConnection.position - 10_000).coerceAtLeast(0)); app.toast("−10 seconds") },
        ) { PlayerConnection.previous() }
        Box(
            Modifier.zIndex(1f).size(76.dp).bounceClick(0.9f) { PlayerConnection.toggle() }
                .softShadow(CircleShape, 16.dp, color = Color.Black, alpha = 0.16f, dy = 4.dp)
                .clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            LinePlayPause(playing, Modifier.size(26.dp), color = Bop.Ink, stroke = 2.4.dp)
            if (buffering) androidx.compose.material3.CircularProgressIndicator(Modifier.size(72.dp), color = Bop.Ink.copy(alpha = 0.25f), strokeWidth = 1.5.dp)
        }
        SideDrop(next = true, haze = haze,
            onLong = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); PlayerConnection.seekTo(PlayerConnection.position + 10_000); app.toast("+10 seconds") },
        ) { PlayerConnection.next() }
    }
}

@Composable
private fun SideDrop(next: Boolean, haze: HazeState, onLong: () -> Unit, onClick: () -> Unit) {
    Box(
        Modifier.size(70.dp).bounceClick(0.9f, onLongClick = onLong, onClick = onClick)
            .mediaGlass(CircleShape, haze, emphasis = 1.1f)
            .drawBehind {
                // Inner hairline ring — the reference's double-rim droplet.
                drawCircle(Color.White.copy(alpha = 0.28f), radius = size.minDimension / 2f - 7.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) { LineSkip(next, Modifier.size(22.dp), color = Color.White, stroke = 1.8.dp) }
}

// --- Glass pill sliders (reference "Volume · 60% · 100%") --------------------------------

/**
 * A frosted track with a brighter frosted fill riding inside it. Drag or tap to set.
 * Labels: [start] inside the fill, [fillLabel] at the fill's end, [end] at the track end.
 */
@Composable
private fun GlassPillSlider(
    value: Float,
    haze: HazeState,
    start: String,
    fillLabel: String?,
    end: String,
    minFill: Dp = 104.dp,
    onDrag: (Float) -> Unit,
    onRelease: (Float) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(56.dp)
            .mediaGlass(shape, haze, emphasis = 0.75f)
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val v = ((o.x - minFill.toPx()) / (size.width - minFill.toPx())).coerceIn(0f, 1f)
                    onDrag(v); onRelease(v)
                }
            }
            .pointerInput(Unit) {
                var v = 0f
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragging = true; v = ((o.x - minFill.toPx()) / (size.width - minFill.toPx())).coerceIn(0f, 1f); onDrag(v) },
                    onDragEnd = { dragging = false; onRelease(v) },
                    onDragCancel = { dragging = false },
                ) { change, d ->
                    change.consume()
                    v = (v + d / (size.width - minFill.toPx())).coerceIn(0f, 1f)
                    onDrag(v)
                }
            },
    ) {
        val fillW = minFill + (maxWidth - minFill) * value.coerceIn(0f, 1f)
        // Dots in the empty part of the track
        Row(Modifier.fillMaxHeight().padding(start = fillW + 10.dp, end = 18.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Dots(Modifier.weight(1f))
            Text(end, style = BopType.BodyStrong, color = Bop.OnMedia2)
        }
        Box(
            Modifier.width(fillW).fillMaxHeight()
                .graphicsLayer { val s = if (dragging) 1.02f else 1f; scaleY = s }
                .mediaGlass(shape, haze, emphasis = 2.2f),
        ) {
            Row(Modifier.fillMaxSize().padding(start = 20.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(start, style = BopType.BodyStrong, color = Bop.OnMedia, maxLines = 1)
                if (fillLabel != null) {
                    Dots(Modifier.weight(1f).padding(horizontal = 8.dp))
                    Text(fillLabel, style = BopType.BodyStrong, color = Bop.OnMedia, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Dots(modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val n = ((maxWidth.value / 28f).toInt()).coerceIn(0, 4)
        if (n > 0) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            repeat(n) { Box(Modifier.size(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.55f))) }
        }
    }
}

@Composable
private fun ProgressPill(t: Track, haze: HazeState) {
    var drag by remember(t.id) { mutableStateOf<Float?>(null) }
    val pos by produceState(0L, t.id) { while (true) { value = PlayerConnection.position; delay(250) } }
    val dur = PlayerConnection.duration.coerceAtLeast(1L)
    val frac = drag ?: (pos.toFloat() / dur).coerceIn(0f, 1f)
    GlassPillSlider(
        value = frac, haze = haze,
        start = formatDuration((frac * dur / 1000).toInt()).ifEmpty { "0:00" },
        fillLabel = null,
        end = formatDuration((dur / 1000).toInt()).ifEmpty { "–:––" },
        minFill = 84.dp,
        onDrag = { drag = it },
        onRelease = { PlayerConnection.seekTo((it * dur).toLong()); drag = null },
    )
}

@Composable
private fun VolumePill(haze: HazeState) {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(AudioManager::class.java) }
    val max = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()) }
    var touching by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            if (!touching) vol = am.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
            delay(400)
        }
    }
    GlassPillSlider(
        value = vol, haze = haze, start = "Volume", fillLabel = "${(vol * 100).roundToInt()}%", end = "100%",
        minFill = 150.dp,
        onDrag = { touching = true; vol = it; am.setStreamVolume(AudioManager.STREAM_MUSIC, (it * max).roundToInt(), 0) },
        onRelease = { touching = false },
    )
}

// --- Small pieces ---------------------------------------------------------------------

@Composable
private fun MediaCircle(icon: ImageVector, desc: String, haze: HazeState, size: Dp, onClick: () -> Unit) {
    Box(Modifier.size(size).bounceClick(0.88f, onClick = onClick).mediaGlass(CircleShape, haze), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
private fun MediaChip(text: String, haze: HazeState) {
    Text(
        text, style = BopType.BodyStrong, color = Bop.OnMedia, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.mediaGlass(RoundedCornerShape(50), haze, emphasis = 1.2f).padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

@Composable
private fun QualityChip(q: StreamQuality, haze: HazeState) {
    val shape = RoundedCornerShape(50)
    Text(
        if (q.hiRes) "Hi-Res · ${q.label}" else q.label, style = BopType.Caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(650)),
        color = if (q.hiRes) Color(0xFF2A1F00) else Bop.OnMedia2, maxLines = 1,
        modifier = (if (q.hiRes) Modifier.clip(shape).background(Brush.linearGradient(listOf(Color(0xFFFFE08A), Color(0xFFFFB86B))))
        else Modifier.mediaGlass(shape, haze, emphasis = 0.8f)).padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun LikeCircle(t: Track, liked: Boolean, haze: HazeState, app: AppState) {
    var pop by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (pop) 1.35f else 1f, spring(dampingRatio = 0.35f, stiffness = 700f), finishedListener = { pop = false }, label = "pop")
    Box(
        Modifier.size(46.dp).bounceClick(0.85f) {
            val now = LibraryStore.toggleLike(t)
            if (now) pop = true
            app.toast(if (now) "Added to Liked Songs" else "Removed from Liked Songs")
        }.mediaGlass(CircleShape, haze),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Like",
            tint = if (liked) Bop.Like else Color.White,
            modifier = Modifier.size(22.dp).graphicsLayer { scaleX = s; scaleY = s },
        )
    }
}

@Composable
private fun SmallToggle(icon: ImageVector, desc: String, on: Boolean, onClick: () -> Unit) {
    Column(Modifier.size(44.dp).clip(CircleShape).bounceClick(0.85f, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, desc, tint = if (on) Color.White else Color.White.copy(alpha = 0.5f), modifier = Modifier.size(21.dp))
        Box(Modifier.padding(top = 3.dp).size(4.dp).clip(CircleShape).background(if (on) Color.White else Color.Transparent))
    }
}

@Composable
private fun PageToggle(icon: ImageVector, desc: String, on: Boolean, haze: HazeState, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).bounceClick(0.85f, onClick = onClick)
            .then(if (on) Modifier.mediaGlass(CircleShape, haze, emphasis = 1.6f) else Modifier),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = if (on) Color.White else Color.White.copy(alpha = 0.62f), modifier = Modifier.size(22.dp)) }
}

/** Compact "now playing" line shown above lyrics and the queue. */
@Composable
private fun SongHeader(t: Track, app: AppState, haze: HazeState) {
    val lib by LibraryStore.state.collectAsState()
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(t.artwork, Modifier.size(54.dp).bounceClick { app.nowPlayingPage = NowPlayingPage.ART }, corner = 14.dp, size = 300, elevation = 10.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, style = BopType.Headline, color = Bop.OnMedia, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.artist, style = BopType.Body, color = Bop.OnMedia2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LikeCircle(t, t.id in lib.likedIds, haze, app)
    }
}
