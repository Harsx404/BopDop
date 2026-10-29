package com.dopdop.bopdop.ui.player

import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.dopdop.bopdop.data.ShortsFeed
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.net.Http
import com.dopdop.bopdop.data.ytm.ShortClip
import com.dopdop.bopdop.data.ytm.YtShorts
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.playback.VideoQualityPolicy
import com.dopdop.bopdop.ui.components.Artwork
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.festive.ThemedIcon as Icon
import com.dopdop.bopdop.ui.glass.LocalReduceMotion
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Shorts with this song, like swiping into Reels: slide the player up and the song's most-viewed
 * Shorts play full screen with sound, one after another as you keep swiping up; pull down on the
 * first one to slide back to the player. Everything plays in BopDop's own player.
 */

/** Just an up arrow under the player controls: slide up (or tap it) for the song's Shorts. */
@Composable
internal fun ShortsHint(count: Int, onOpen: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(bottom = 4.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(40.dp, 28.dp).clip(RoundedCornerShape(50)).bounceClick(0.9f, onClick = onOpen), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.KeyboardArrowUp, "$count Shorts with this song", tint = Bop.OnMedia2, modifier = Modifier.size(26.dp))
        }
    }
}

/**
 * Full screen, with sound: an endless feed of Shorts (see [ShortsFeed]), swiping up and down. Each
 * one is labelled with its song. The song pauses while the feed is open; leaving it ([onClose]) hands
 * back the song of the Short you were on, so the player can carry on with that one, and otherwise
 * the paused song carries on (if it was playing). A play/pause button (or a tap anywhere) pauses the
 * Short, and a line along the foot shows how far into it you are.
 */
@OptIn(ExperimentalFoundationApi::class, UnstableApi::class)
@Composable
internal fun ShortsViewer(seed: Track, first: List<ShortClip>, onClose: (Track) -> Unit) {
    val feed = remember { ShortsFeed(seed, first) }
    val clips = feed.items
    val songWasPlaying = remember { PlayerConnection.isPlaying.value }
    // Leaving on another song's Short: the player moves on to that song, so the old one stays paused.
    var leavingTo by remember { mutableStateOf<Track?>(null) }
    DisposableEffect(Unit) {
        PlayerConnection.pauseForShorts()
        onDispose { if (leavingTo == null && songWasPlaying) PlayerConnection.resumeAfterShorts() }
    }
    val pager = rememberPagerState { clips.size }
    val leave: (Track) -> Unit = { song -> leavingTo = song.takeIf { it.id != seed.id }; onClose(song) }
    val leaveHere: () -> Unit = { leave(clips.getOrNull(pager.settledPage)?.song ?: seed) }
    BackHandler(onBack = leaveHere)
    // Keep the feed going a few Shorts ahead of wherever you are.
    LaunchedEffect(pager.settledPage, clips.size) { feed.onPage(pager.settledPage) }
    // Pulling down past the first Short follows the finger a little, then lets go back to the player.
    val density = androidx.compose.ui.platform.LocalDensity.current
    var pull by remember { mutableStateOf(0f) }
    val back = remember(pager) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset,
                                      source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (pager.currentPage == 0 && available.y > 0f) { pull += available.y; return available }
                if (pull > 0f && available.y < 0f) { val take = maxOf(available.y, -pull); pull += take; return androidx.compose.ui.geometry.Offset(0f, take) }
                return androidx.compose.ui.geometry.Offset.Zero
            }
            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                val out = pull > with(density) { 96.dp.toPx() } || (pull > 0f && available.y > 1800f)
                pull = 0f
                if (out) leave(seed)
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }
    val player = rememberShortPlayer(muted = false)
    var firstFrame by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    // The picture's shape (width over height), from the video itself; Shorts are usually 9:16.
    var videoAspect by remember { mutableStateOf(9f / 16f) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onRenderedFirstFrame() { firstFrame = true }
            override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) {
                if (size.width > 0 && size.height > 0) videoAspect = size.width * size.pixelWidthHeightRatio / size.height
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    // How far into the Short, for the line along the foot; read a few times a second while it plays.
    var progress by remember { mutableStateOf(0f) }
    LaunchedEffect(player) {
        while (true) {
            val d = player.duration
            progress = if (d > 0) (player.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
            kotlinx.coroutines.delay(200)
        }
    }
    val toggle: () -> Unit = { paused = !paused; player.playWhenReady = !paused }
    val current = clips.getOrNull(pager.settledPage)?.clip
    LaunchedEffect(current?.id) {
        firstFrame = false; paused = false
        val id = current?.id ?: return@LaunchedEffect
        player.stop()
        val source = runCatching { withContext(Dispatchers.IO) { shortSource(id, withSound = true) } }.getOrNull() ?: return@LaunchedEffect
        player.setMediaSource(source); player.prepare(); player.playWhenReady = true
    }
    val uri = LocalUriHandler.current
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(pager, Modifier.fillMaxSize().nestedScroll(back).graphicsLayer { translationY = pull * 0.6f }, key = { clips[it].clip.id }) { page ->
            val item = clips[page]
            val c = item.clip
            Box(Modifier.fillMaxSize().pointerInput(page) {
                detectTapGestures { if (page == pager.settledPage) toggle() }
            }) {
                if (page == pager.settledPage) androidx.compose.foundation.layout.BoxWithConstraints(
                    Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center,
                ) {
                    // Never stretched: a tall Short fills the screen like Reels, trimming a little off the
                    // sides on screens taller than 9:16; a wider video shows whole, with bars.
                    val screen = maxWidth / maxHeight
                    val fill = videoAspect < 0.75f
                    val widerThanScreen = videoAspect > screen
                    val (vw, vh) = if (fill == widerThanScreen) maxHeight * videoAspect to maxHeight else maxWidth to maxWidth / videoAspect
                    AndroidView(
                        factory = { ctx -> TextureView(ctx).also(player::setVideoTextureView) },
                        modifier = Modifier.requiredSize(vw, vh),
                        onRelease = { player.clearVideoTextureView(it) },
                    )
                }
                AnimatedVisibility(!(page == pager.settledPage && firstFrame), enter = fadeIn(), exit = fadeOut()) {
                    Artwork(c.thumbnail, Modifier.fillMaxSize(), corner = 0.dp, size = 720)
                }
                if (paused && page == pager.settledPage) Icon(Icons.Rounded.PlayArrow, "Play", tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.Center).size(72.dp))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.65f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))))
                Column(Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 20.dp, end = 84.dp, bottom = 24.dp)) {
                    // The song this Short uses, like Reels' audio tag: tap to go and play it.
                    Row(
                        Modifier.padding(bottom = 10.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.35f))
                            .bounceClick { leave(item.song) }.padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(item.song.artwork, Modifier.size(22.dp), corner = 6.dp, size = 120)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(item.song.title + " · " + item.song.artist.substringBefore(","), style = BopType.Caption, color = Color.White,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(c.title, style = BopType.BodyStrong, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(c.views + " · YouTube Shorts", style = BopType.Caption, color = Color.White.copy(alpha = 0.75f))
                    Spacer(Modifier.height(10.dp))
                    Text("Open on YouTube", style = BopType.BodyStrong, color = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f))
                            .bounceClick { runCatching { uri.openUri("https://www.youtube.com/shorts/${c.id}") } }
                            .padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        }
        Box(Modifier.statusBarsPadding().padding(12.dp).size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
            .bounceClick(0.9f, onClick = leaveHere), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Close, "Close Shorts", tint = Color.White, modifier = Modifier.size(22.dp))
        }
        // Play / pause, bottom right, over every Short.
        Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 28.dp).size(52.dp).clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f)).bounceClick(0.9f, onClick = toggle), contentAlignment = Alignment.Center) {
            Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (paused) "Play Short" else "Pause Short", tint = Color.White, modifier = Modifier.size(28.dp))
        }
        // How far into this Short.
        Box(Modifier.align(Alignment.BottomStart).navigationBarsPadding().fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.25f))) {
            Box(Modifier.fillMaxWidth(progress).height(3.dp).background(Color.White))
        }
    }
}

/** A looping player of its own, released with the composable; it never touches the song's audio focus. */
@OptIn(UnstableApi::class)
@Composable
private fun rememberShortPlayer(muted: Boolean): ExoPlayer {
    val ctx = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            volume = if (muted) 0f else 1f
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

/** Any height: the tallest picture the Short has that this phone can decode smoothly. */
private const val HIGHEST = 4320

/** The Short's picture at the highest quality the phone plays, plus its soundtrack when [withSound]. */
@OptIn(UnstableApi::class)
private fun shortSource(id: String, withSound: Boolean): MediaSource {
    val r = YtShorts.video(id, cap = HIGHEST)
    fun progressive(url: String, ua: String?) = ProgressiveMediaSource.Factory(OkHttpDataSource.Factory(Http.client).setUserAgent(ua))
        .createMediaSource(MediaItem.fromUri(url))
    val video = progressive(r.video.url, r.video.userAgent)
    val audio = r.audio
    return if (withSound && audio != null) MergingMediaSource(video, progressive(audio.url, audio.userAgent)) else video
}
