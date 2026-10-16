package com.dopdop.bopdop.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    val video by PlayerConnection.video.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) PlayerConnection.setVideoEnabled(false)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); PlayerConnection.setVideoEnabled(false) }
    }
    LaunchedEffect(app.nowPlayingPage) {
        if (app.nowPlayingPage != NowPlayingPage.ART) PlayerConnection.setVideoEnabled(false)
    }
    LaunchedEffect(video.error) { video.error?.let(app::toast) }
    val dim by animateFloatAsState(
        when (app.nowPlayingPage) { NowPlayingPage.ART -> 0f; NowPlayingPage.LYRICS -> 0.42f; NowPlayingPage.QUEUE -> 0.62f }, tween(420), label = "dim",
    )
    // Lyrics/queue: the artwork melts into a soft colour field behind the text.
    val artBlur by animateFloatAsState(if (app.nowPlayingPage == NowPlayingPage.ART) 0f else 46f, tween(520), label = "artBlur")
    // Swipe: depth-parallax swap inside the backdrop — the cover drifts back and fades
    // while the neighbour glides in over it. Nothing leaves the artwork area.
    val swipe = remember { Animatable(0f) }
    var swiped by remember { mutableStateOf(false) }
    // The track a swipe started on; once the player moves on, the old offset no longer applies.
    var swipeFrom by remember { mutableStateOf<String?>(null) }
    val queue by PlayerConnection.queue.collectAsState()
    val index by PlayerConnection.index.collectAsState()
    val nextArt = queue.getOrNull(index + 1)?.artwork
    val prevArt = queue.getOrNull(index - 1)?.artwork
    LaunchedEffect(t.id) {
        swipe.snapTo(0f)
        swipeFrom = null
        delay(120) // let the art transition read `swiped` before clearing it
        swiped = false
    }

    val genie = remember { if (android.os.Build.VERSION.SDK_INT >= 33) Genie() else null }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = constraints.maxHeight.toFloat()
        val w = constraints.maxWidth.toFloat()
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Closes INTO the mini player's cover like a genie into its lamp; opening pours back out.
                    val e = (1f - p).coerceIn(0f, 1f)
                    val target = app.miniArt ?: androidx.compose.ui.geometry.Rect(
                        25.dp.toPx(), h - 150.dp.toPx(), 71.dp.toPx(), h - 104.dp.toPx(),
                    )
                    // The real cover takes over for the last stretch.
                    alpha = 1f - ((e - 0.84f) / 0.16f).coerceIn(0f, 1f)
                    if (genie != null) {
                        renderEffect = if (e > 0.002f) genie.effect(size, target, e) else null
                    } else {
                        // No runtime shaders: scale straight into the cover instead.
                        val eased = e * e * (3f - 2f * e)
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        scaleX = 1f + (target.width / w - 1f) * eased
                        scaleY = 1f + (target.height / h - 1f) * eased
                        translationX = target.left * eased
                        translationY = target.top * eased
                        shape = RoundedCornerShape((eased * 14.dp.toPx() / scaleX.coerceAtLeast(0.05f)).coerceAtMost(w / 2f))
                        clip = true
                    }
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
                Box(
                    Modifier.fillMaxWidth().aspectRatio(0.86f).clipToBounds()
                        .then(if (android.os.Build.VERSION.SDK_INT >= 31 && artBlur > 0.5f) Modifier.blur(artBlur.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Rectangle) else Modifier),
                ) {
                    val active = swipeFrom == t.id
                    val sv = if (active) swipe.value else 0f
                    val incoming = if (sv < -1f) nextArt else if (sv > 1f) prevArt else null
                    AnimatedContent(
                        targetState = t.artwork,
                        // A swipe already brought the new cover in — don't crossfade it again.
                        transitionSpec = { if (swiped) EnterTransition.None togetherWith ExitTransition.None else fadeIn(tween(700)) togetherWith fadeOut(tween(700)) },
                        label = "bgart",
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            if (swipeFrom != t.id) return@graphicsLayer
                            val x = swipe.value
                            val prog = (kotlin.math.abs(x) / w).coerceIn(0f, 1f)
                            // Melts away: drifts with the finger, swells and softens. The zoom always
                            // outgrows the drift, so the cover never shows an edge.
                            translationX = x * 0.06f
                            val sc = 1f + 0.16f * prog
                            scaleX = sc; scaleY = sc
                            alpha = 1f - prog
                            liquidBlur(prog)
                        },
                    ) { art -> KenBurnsArt(art) }
                    // The music video takes the cover's place; it fades with the swipe like the art does.
                    AnimatedVisibility(video.active, enter = fadeIn(tween(500)), exit = fadeOut(tween(300))) {
                        MusicVideoHero(video, Modifier.fillMaxSize().graphicsLayer {
                            if (swipeFrom == t.id) alpha = 1f - (kotlin.math.abs(swipe.value) / w).coerceIn(0f, 1f)
                        })
                    }
                    if (incoming != null) Artwork(
                        incoming,
                        Modifier.fillMaxSize().graphicsLayer {
                            val x = if (swipeFrom == t.id) swipe.value else 0f
                            val prog = (kotlin.math.abs(x) / w).coerceIn(0f, 1f)
                            // Condenses in from the side the finger pulls from, settling from a zoom into focus.
                            translationX = -kotlin.math.sign(x) * (1f - prog) * w * 0.06f
                            val sc = 1f + 0.16f * (1f - prog)
                            scaleX = sc; scaleY = sc
                            alpha = (prog * 1.3f).coerceIn(0f, 1f)
                            liquidBlur(1f - prog)
                        },
                        corner = 0.dp, size = 1200,
                    )
                }
                Box(
                    Modifier.fillMaxWidth().aspectRatio(0.86f).background(
                        Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.28f), 0.2f to Color.Transparent, 0.5f to Color.Transparent, 0.86f to wash.copy(alpha = 0.85f), 1f to wash),
                    ),
                )
                Box(Modifier.fillMaxSize().background(washDeep.copy(alpha = dim)))
                // Contrast floor: light covers wash out white controls, so deepen the bottom a touch.
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.62f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.30f))))
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
                            NowPlayingPage.ART -> ArtGestureArea(t, swipe, w, hasNext = nextArt != null, hasPrev = prevArt != null, onStart = { swipeFrom = t.id }) { swiped = true }
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

/** Soft focus for the cover swap (API 31+); clamped edges so the blur never fades to transparent. */
private fun androidx.compose.ui.graphics.GraphicsLayerScope.liquidBlur(amount: Float) {
    if (android.os.Build.VERSION.SDK_INT < 31) return
    val r = amount * 26.dp.toPx()
    renderEffect = if (r < 0.5f) null else androidx.compose.ui.graphics.BlurEffect(r, r, androidx.compose.ui.graphics.TileMode.Clamp)
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

/**
 * Empty space over the artwork. Swipe to change song: the cover tracks the finger
 * (rubber-banding where there is no neighbour), a fling or a past-the-third
 * release completes the change. Double-tap likes, with a heart burst.
 */
@Composable
private fun ArtGestureArea(t: Track, swipe: Animatable<Float, AnimationVector1D>, width: Float, hasNext: Boolean, hasPrev: Boolean, onStart: () -> Unit, onSwiped: () -> Unit) {
    val scope = rememberCoroutineScope()
    val heart = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier.fillMaxSize()
            .pointerInput(t.id, hasNext, hasPrev) {
                val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                var raw = 0f
                detectHorizontalDragGestures(
                    onDragStart = { tracker.resetTracking(); raw = 0f; onStart() },
                    onDragCancel = { scope.launch { swipe.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 400f)) } },
                    onDragEnd = {
                        val v = tracker.calculateVelocity().x
                        val goNext = hasNext && (swipe.value < -width / 3 || (v < -1400f && swipe.value < 0))
                        val goPrev = hasPrev && (swipe.value > width / 3 || (v > 1400f && swipe.value > 0))
                        scope.launch {
                            when {
                                goNext -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                    swipe.animateTo(-width, tween(300, easing = FastOutSlowInEasing))
                                    onSwiped(); PlayerConnection.next()
                                }
                                goPrev -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                    swipe.animateTo(width, tween(300, easing = FastOutSlowInEasing))
                                    onSwiped(); PlayerConnection.skipTo(PlayerConnection.index.value - 1)
                                }
                                else -> swipe.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 380f)) // settles back
                            }
                        }
                    },
                ) { change, d ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    raw += d
                    // Resist where there is nothing to swipe to.
                    val resisted = if ((raw < 0 && !hasNext) || (raw > 0 && !hasPrev)) raw * 0.22f else raw
                    scope.launch { swipe.snapTo(resisted) }
                }
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
    val video by PlayerConnection.video.collectAsState()
    val liked = t.id in lib.likedIds
    val onArt = app.nowPlayingPage == NowPlayingPage.ART

    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 14.dp)) {
        AnimatedVisibility(onArt, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            VideoModeSwitch(video, haze, Modifier.padding(bottom = 18.dp))
        }
        AnimatedVisibility(onArt, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Row(Modifier.padding(bottom = 18.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    AnimatedContent(t.title, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }, label = "title") { title ->
                        Text(title, style = BopType.Display.copy(fontSize = BopType.Display.fontSize * 1.1f), color = Bop.OnMedia, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
        }
        ProgressPill(t, quality[t.id])
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TransportCluster(playing, buffering, haze, app) }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            PageToggle(Icons.Rounded.Lyrics, "Lyrics", app.nowPlayingPage == NowPlayingPage.LYRICS, haze) {
                app.nowPlayingPage = if (app.nowPlayingPage == NowPlayingPage.LYRICS) NowPlayingPage.ART else NowPlayingPage.LYRICS
            }
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
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier.size(70.dp).bounceClick(0.9f, onLongClick = onLong, interactionSource = press, onClick = onClick)
            .mediaGlass(CircleShape, haze, emphasis = 1.1f, interactionSource = press, strong = true)
            .drawBehind {
                // Inner hairline ring — the reference's double-rim droplet.
                drawCircle(Color.White.copy(alpha = 0.28f), radius = size.minDimension / 2f - 7.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) { LineSkip(next, Modifier.size(24.dp), color = Color.White, stroke = 2.3.dp) }
}

// --- Seek & volume (see Sliders.kt) ---------------------------------------------------

@Composable
private fun ProgressPill(t: Track, q: StreamQuality?) {
    val pos by produceState(0L, t.id) { while (true) { value = PlayerConnection.position; delay(200) } }
    val playing by PlayerConnection.isPlaying.collectAsState()
    val video by PlayerConnection.video.collectAsState()
    WavySeekBar(
        positionMs = pos, durationMs = PlayerConnection.duration, playing = playing,
        onSeek = { PlayerConnection.seekTo(it) },
        caption = if (video.active) {
            // The song's own sound, then the picture.
            val res = when { video.height >= 2160 -> "4K"; video.height >= 1440 -> "1440p"; video.height > 0 -> "${video.height}p"; else -> "Video" }
            listOfNotNull(q?.let { if (it.hiRes) "Hi-Res · ${it.label}" else it.label }, res).joinToString(" · ")
        } else q?.let { if (it.hiRes) "Hi-Res · ${it.label}" else it.label },
    )
}

// --- Small pieces ---------------------------------------------------------------------

@Composable
private fun MediaCircle(icon: ImageVector, desc: String, haze: HazeState, size: Dp, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Box(Modifier.size(size).bounceClick(0.88f, interactionSource = press, onClick = onClick).mediaGlass(CircleShape, haze, interactionSource = press, strong = true), contentAlignment = Alignment.Center) {
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
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier.size(46.dp).bounceClick(0.85f, interactionSource = press) {
            val now = LibraryStore.toggleLike(t)
            if (now) pop = true
            app.toast(if (now) "Added to Liked Songs" else "Removed from Liked Songs")
        }.mediaGlass(CircleShape, haze, interactionSource = press, strong = true),
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
    ) { Icon(icon, desc, tint = if (on) Color.White else Color.White.copy(alpha = 0.92f), modifier = Modifier.size(24.dp)) }
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
