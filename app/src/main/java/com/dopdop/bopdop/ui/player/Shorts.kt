package com.dopdop.bopdop.ui.player

import android.view.TextureView
import android.os.SystemClock
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.core.animateFloat
import androidx.compose.material3.Text
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.dopdop.bopdop.data.ShortsFeed
import com.dopdop.bopdop.data.ShortsStore
import com.dopdop.bopdop.data.ShortsTags
import com.dopdop.bopdop.data.ShortWatchSession
import com.dopdop.bopdop.data.ShortsPreloader
import com.dopdop.bopdop.data.shortsAhead
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.net.Http
import com.dopdop.bopdop.data.ytm.ShortClip
import com.dopdop.bopdop.data.ytm.YtShorts
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.playback.VideoQualityPolicy
import com.dopdop.bopdop.playback.ShortsVideoCache
import com.dopdop.bopdop.data.ytm.ResolvedVideo
import com.dopdop.bopdop.ui.LocalApp
import com.dopdop.bopdop.ui.Sheet
import com.dopdop.bopdop.ui.components.Artwork
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.festive.ThemedIcon as Icon
import com.dopdop.bopdop.ui.glass.LocalReduceMotion
import com.dopdop.bopdop.ui.glass.mediaGlass
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

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
@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun ShortsViewer(seed: Track, first: List<ShortClip>, onClose: (Track) -> Unit) {
    val feed = remember(seed.id) { ShortsFeed(seed, first) }
    val clips = feed.items
    val history by ShortsStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current.applicationContext
    val app = LocalApp.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val pager = rememberPagerState { clips.size }
    var leaving by remember { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }
    val playlistOpen = app.sheet != null
    val currentItem = clips.getOrNull(pager.settledPage)
    val latestItem by rememberUpdatedState(currentItem)
    val latestClose by rememberUpdatedState(onClose)
    val leave: (Track) -> Unit = { song -> if (!leaving) { leaving = true; latestClose(song) } }
    // Leaving plays the full song of the Short on screen, never a clip's own audio.
    val fullSong: (com.dopdop.bopdop.data.FeedShort?) -> Track = { item -> item?.let(feed::song)?.takeUnless { it.isShortAudio } ?: seed }
    val leaveHere: () -> Unit = { leave(fullSong(latestItem)) }
    val latestLeave by rememberUpdatedState(leaveHere)
    DisposableEffect(Unit) {
        PlayerConnection.pauseForShorts()
        onDispose {
            if (!leaving && PlayerConnection.current.value?.id == seed.id)
                PlayerConnection.continueFromShorts(fullSong(latestItem))
        }
    }
    // Going to another app/screen-off also hands the current audio to the playback service.
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) latestLeave() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    BackHandler { if (showSaved) showSaved = false else leaveHere() }
    // Keep the feed going a few Shorts ahead of wherever you are.
    LaunchedEffect(pager.settledPage, clips.size, history.revision) { feed.onPage(pager.settledPage) }
    // Find the full song each Short really uses: this one first, then the next two.
    LaunchedEffect(pager.settledPage, clips.size) {
        (0..2).mapNotNull { clips.getOrNull(pager.settledPage + it) }.forEach { feed.resolve(it) }
    }
    var menuFor by remember { mutableStateOf<com.dopdop.bopdop.data.FeedShort?>(null) }
    // Hearts from a double tap, where the finger was.
    val hearts = remember { androidx.compose.runtime.mutableStateListOf<Pair<Long, androidx.compose.ui.geometry.Offset>>() }
    val likeNow: (com.dopdop.bopdop.data.FeedShort) -> Unit = { item ->
        if (item.clip.id !in ShortsStore.state.value.likedIds) ShortsStore.toggleLike(item.copy(song = fullSong(item)))
    }
    val shareShort: (com.dopdop.bopdop.data.FeedShort) -> Unit = { item ->
        val song = fullSong(item)
        val text = "${item.clip.title.ifBlank { "A Short" }}\nhttps://www.youtube.com/shorts/${item.clip.id}\n\n" +
            "\u266A ${song.title} \u00B7 ${song.artist.substringBefore(",")}\nListen on YouTube Music: " + trackLink(song)
        runCatching {
            context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                .putExtra(android.content.Intent.EXTRA_TEXT, text), "Share Short").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
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
                if (out) latestLeave()
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }
    val player = rememberShortPlayer(muted = false)
    var firstFrame by remember { mutableStateOf(false) }
    // Which Short the player holds, and which one has drawn a frame: a swipe shows the new Short's cover
    // until its own video is on screen, never the last frame of the one before.
    var loadedId by remember { mutableStateOf<String?>(null) }
    var framedId by remember { mutableStateOf<String?>(null) }
    var paused by remember { mutableStateOf(false) }
    val canPlay by rememberUpdatedState(!paused && !showSaved && !playlistOpen && menuFor == null)
    var playbackError by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }
    val session = remember(currentItem?.clip?.id) { currentItem?.let { ShortWatchSession(it) } }
    // The picture's shape (width over height), from the video itself; Shorts are usually 9:16.
    var videoAspect by remember { mutableStateOf(9f / 16f) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onRenderedFirstFrame() { framedId = loadedId; firstFrame = true }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                firstFrame = false
                playbackError = "Couldn't play this Short. Tap to retry, or swipe for another."
            }
            override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) {
                if (size.width > 0 && size.height > 0) videoAspect = size.width * size.pixelWidthHeightRatio / size.height
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    // How far into the Short, for the line along the foot; read a few times a second while it plays.
    var progress by remember { mutableStateOf(0f) }
    DisposableEffect(player, session) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) session?.loopCompleted()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            session?.snapshot()?.let(ShortsStore::record)
        }
    }
    LaunchedEffect(player, session) {
        var checkpoint = SystemClock.elapsedRealtime()
        while (true) {
            val d = player.duration
            progress = if (d > 0) (player.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
            val now = SystemClock.elapsedRealtime()
            session?.sample(now, player.isPlaying && firstFrame && canPlay && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED), d)
            if (now - checkpoint >= 5_000) { session?.snapshot()?.let(ShortsStore::record); checkpoint = now }
            kotlinx.coroutines.delay(200)
        }
    }
    val toggle: () -> Unit = { paused = !paused }
    LaunchedEffect(canPlay) { player.playWhenReady = canPlay }
    val current = currentItem?.clip
    val cap = VideoQualityPolicy.maxHeight(LibraryStore.settings.videoQuality)
    val preloader = remember(context, scope) {
        ShortsPreloader<String, ResolvedVideo>(scope,
            load = { key -> withContext(Dispatchers.IO) { YtShorts.video(key.substringBeforeLast('@'), key.substringAfterLast('@').toInt()) } },
            warm = { _, video -> ShortsVideoCache.warm(context, video, VideoQualityPolicy.metered()) })
    }
    DisposableEffect(preloader) { onDispose { preloader.close() } }
    val nextIds = shortsAhead(clips.map { it.clip.id }, pager.settledPage, VideoQualityPolicy.metered())
    LaunchedEffect(current?.id, nextIds, cap, showSaved, playlistOpen) {
        current?.id?.let { id -> preloader.updateWindow("$id@$cap", if (showSaved || playlistOpen) emptyList() else nextIds.map { "$it@$cap" }) }
    }
    LaunchedEffect(current?.id, cap, retry) {
        firstFrame = false; paused = false; progress = 0f; playbackError = null
        val id = current?.id ?: return@LaunchedEffect
        player.stop()
        val source = runCatching { ShortsVideoCache.source(context, preloader.get("$id@$cap")) }.getOrNull()
        if (source == null) { playbackError = "Couldn't load this Short. Tap to retry, or swipe for another."; return@LaunchedEffect }
        loadedId = id
        player.setMediaSource(source); player.prepare(); player.playWhenReady = canPlay
    }
    val uri = LocalUriHandler.current
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(pager, Modifier.fillMaxSize().nestedScroll(back).graphicsLayer { translationY = pull * 0.6f }, key = { clips[it].clip.id }) { page ->
            val item = clips[page]
            val c = item.clip
            Box(Modifier.fillMaxSize().pointerInput(page) {
                // Tap pauses; double tap likes, with a heart where you tapped; a long press opens the options.
                detectTapGestures(
                    onTap = { if (page == pager.settledPage) toggle() },
                    onDoubleTap = { at -> likeNow(item); session?.act(); hearts += System.nanoTime() to at },
                    onLongPress = { menuFor = item },
                )
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
                AnimatedVisibility(!(page == pager.settledPage && firstFrame && framedId == c.id), enter = fadeIn(), exit = fadeOut()) {
                    Artwork(c.thumbnail, Modifier.fillMaxSize(), corner = 0.dp, size = 720)
                }
                if (paused && page == pager.settledPage) Icon(Icons.Rounded.PlayArrow, "Play", tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.Center).size(72.dp))
                // Shade top and bottom, so the controls read on any video.
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = .35f), .14f to Color.Transparent,
                    .62f to Color.Transparent, 1f to Color.Black.copy(alpha = .75f))))
                ShortCaption(
                    item, feed.song(item), feed.suggested(item), found = c.id in feed.songs || item.source == com.dopdop.bopdop.data.ShortsFeed.SEED,
                    Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 16.dp, end = 88.dp, bottom = 22.dp),
                    // A soundtrack's movie or show, or an album, or the artist: their page, with the song playing.
                    onAlbum = { song -> session?.act(); leave(song); app.collapsePlayer(); song.albumId?.let { app.openAlbum(it, song.album, song.artwork) } },
                    onArtist = { song -> session?.act(); leave(song); app.collapsePlayer(); song.artistId?.let { app.openArtist(it, song.artist.substringBefore(",")) } },
                ) { session?.act(); leave(fullSong(item)) }
                ShortRail(
                    feed.song(item), liked = c.id in history.likedIds, spinning = page == pager.settledPage && !paused && firstFrame,
                    modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 10.dp, bottom = 22.dp),
                    onLike = { if (c.id !in history.likedIds) session?.act(); ShortsStore.toggleLike(item.copy(song = fullSong(item))) },
                    onAdd = { session?.act(); app.sheet = Sheet.AddToPlaylist(listOf(fullSong(item))) },
                    onShare = { session?.act(); shareShort(item) },
                    onMore = { menuFor = item },
                    onSong = { session?.act(); leave(fullSong(item)) },
                )
            }
        }
        // Double-tap hearts: pop, rise and fade where the finger was.
        hearts.toList().forEach { (key, at) ->
            androidx.compose.runtime.key(key) { TapHeart(at) { hearts.removeAll { it.first == key } } }
        }
        if (playbackError != null) Text(playbackError!!, style = BopType.BodyStrong, color = Color.White,
            modifier = Modifier.align(Alignment.Center).padding(32.dp).background(Color.Black.copy(alpha = .7f), RoundedCornerShape(16.dp)).bounceClick {
                current?.id?.let { id -> YtShorts.invalidateVideo(id, cap); preloader.invalidate("$id@$cap"); retry++ }
            }.padding(16.dp))
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).bounceClick(0.9f, onClick = leaveHere).mediaGlass(CircleShape, null), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(Icons.Rounded.Close, "Close Shorts", tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Text("Shorts", style = BopType.Headline, color = Color.White, modifier = Modifier.weight(1f).padding(start = 4.dp))
            Box(Modifier.size(44.dp).bounceClick { showSaved = true }.mediaGlass(CircleShape, null), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(Icons.Rounded.VideoLibrary, "Liked Shorts (${history.liked.size})", tint = Color.White, modifier = Modifier.size(21.dp))
            }
        }
        // How far into this Short.
        Box(Modifier.align(Alignment.BottomStart).navigationBarsPadding().fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.22f))) {
            Box(Modifier.fillMaxWidth(progress).height(2.dp).background(Color.White))
        }
    }
    menuFor?.let { item ->
        ModalBottomSheet(onDismissRequest = { menuFor = null }, containerColor = Color(0xFF171719)) {
            val song = fullSong(item)
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 24.dp)) {
                MenuRow(Icons.Rounded.VisibilityOff, "Not interested", "You'll see fewer Shorts like this") {
                    if (item.clip.id == current?.id) session?.dismiss()
                    else ShortsStore.record(com.dopdop.bopdop.data.ShortWatch(item, System.currentTimeMillis(), 0, 0, dismissed = true))
                    menuFor = null
                    scope.launch { if (pager.settledPage + 1 < clips.size) pager.animateScrollToPage(pager.settledPage + 1) }
                }
                MenuRow(Icons.Rounded.MusicNote, "Play the full song", song.title + " · " + song.artist.substringBefore(",")) {
                    menuFor = null; session?.act(); leave(song)
                }
                MenuRow(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add the full song to a playlist", null) {
                    menuFor = null; session?.act(); app.sheet = Sheet.AddToPlaylist(listOf(song))
                }
                MenuRow(Icons.Rounded.Share, "Share", null) { menuFor = null; session?.act(); shareShort(item) }
                MenuRow(Icons.AutoMirrored.Rounded.OpenInNew, "Open on YouTube", null) {
                    menuFor = null; runCatching { uri.openUri("https://www.youtube.com/shorts/${item.clip.id}") }
                }
            }
        }
    }
    if (showSaved) ModalBottomSheet(onDismissRequest = { showSaved = false }, containerColor = Color(0xFF171719)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("Liked Shorts", style = BopType.BodyStrong, color = Color.White)
            Text("Saved on this device", style = BopType.Caption, color = Color.White.copy(alpha = .65f))
            history.error?.let { Text(it, style = BopType.Caption, color = Color(0xFFFFB4AB), modifier = Modifier.padding(vertical = 12.dp)) }
            if (history.liked.isEmpty()) Text("Tap the heart on a Short to keep it here.", style = BopType.BodyStrong,
                color = Color.White.copy(alpha = .75f), modifier = Modifier.padding(vertical = 32.dp))
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(history.liked, key = { it.item.clip.id }) { saved ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).bounceClick {
                    val index = feed.openSaved(saved.item)
                    showSaved = false
                    scope.launch { pager.scrollToPage(index) }
                }, verticalAlignment = Alignment.CenterVertically) {
                    Artwork(saved.item.clip.thumbnail, Modifier.size(48.dp, 72.dp), corner = 8.dp, size = 200)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(saved.item.clip.title, style = BopType.BodyStrong, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(if (saved.item.song.isShortAudio) "Original audio" else saved.item.song.title + " \u00B7 " + saved.item.song.artist.substringBefore(","), style = BopType.Caption,
                            color = Color.White.copy(alpha = .65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Rounded.Favorite, "Remove liked Short", tint = Bop.Like,
                        modifier = Modifier.size(44.dp).bounceClick { ShortsStore.toggleLike(saved.item) }.padding(10.dp))
                }
            }
        }
    }
}

/** A looping player of its own, released with the composable; it never touches the song's audio focus. */
@OptIn(UnstableApi::class)
@Composable
private fun rememberShortPlayer(muted: Boolean): ExoPlayer {
    val ctx = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(ctx).setLoadControl(DefaultLoadControl.Builder()
            .setBufferDurationsMs(2_500, 12_000, 250, 500).build()).build().apply {
            volume = if (muted) 0f else 1f
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}


/**
 * Bottom left, as on Reels: the song the Short uses (a suggestion when it uses its own sound),
 * then its caption, tags and views. Tap the song to play the full track.
 */
@Composable
private fun ShortCaption(
    item: com.dopdop.bopdop.data.FeedShort, song: Track, suggested: Boolean, found: Boolean, modifier: Modifier,
    onAlbum: (Track) -> Unit, onArtist: (Track) -> Unit, onSong: () -> Unit,
) {
    val c = item.clip
    var open by remember(c.id) { mutableStateOf(false) }
    Column(modifier) {
        Row(
            Modifier.padding(bottom = 10.dp).bounceClick(onClick = onSong).mediaGlass(RoundedCornerShape(50), null, emphasis = .8f).padding(start = 5.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(song.artwork, Modifier.size(24.dp), corner = 6.dp, size = 120)
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            val label = when {
                !found -> "Finding the song…"
                suggested -> "Suggested · " + song.title + " · " + song.artist.substringBefore(",")
                else -> song.title + " · " + song.artist.substringBefore(",")
            }
            com.dopdop.bopdop.ui.components.MarqueeText(label, BopType.Caption, Color.White, Modifier.widthIn(max = 230.dp))
        }
        // Where the song comes from: the movie or show it was made for (or its album), and its artist.
        if (found) {
            val from = soundtrackOf(song)
            val album = song.album.takeIf { it.isNotBlank() && !it.equals(song.title, ignoreCase = true) }
            Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (song.albumId != null && (from != null || album != null)) SourceChip(if (from != null) "🎬 $from" else "💿 $album") { onAlbum(song) }
                if (song.artistId != null) SourceChip("👤 " + song.artist.substringBefore(",")) { onArtist(song) }
            }
        }
        if (c.title.isNotBlank()) Text(
            c.title, style = BopType.Body, color = Color.White, maxLines = if (open) 8 else 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.bounceClick(0.99f) { open = !open },
        )
        val tags = com.dopdop.bopdop.data.ShortsTaste.usable(ShortsTags.extract(c.title, c.tags)).take(3)
        if (tags.isNotEmpty()) Text(tags.joinToString("  ") { "#$it" }, style = BopType.Caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(600)),
            color = Color.White.copy(alpha = .9f), maxLines = 1, modifier = Modifier.padding(top = 4.dp))
        if (c.views.isNotBlank()) Text(c.views, style = BopType.Caption, color = Color.White.copy(alpha = .7f), modifier = Modifier.padding(top = 2.dp))
    }
}

/** The right-hand column: like, add the song, share, more, and the song's record turning at the foot. */
@Composable
private fun ShortRail(
    song: Track, liked: Boolean, spinning: Boolean, modifier: Modifier,
    onLike: () -> Unit, onAdd: () -> Unit, onShare: () -> Unit, onMore: () -> Unit, onSong: () -> Unit,
) {
    val pop by androidx.compose.animation.core.animateFloatAsState(if (liked) 1f else 0f,
        androidx.compose.animation.core.spring(dampingRatio = .4f, stiffness = 600f), label = "like")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        RailButton(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) "Liked" else "Like",
            tint = if (liked) Bop.Like else Color.White, scale = 1f + .25f * pop * (1f - pop) * 4f, themed = true, onClick = onLike)
        RailButton(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add song", onClick = onAdd)
        RailButton(Icons.Rounded.Share, "Share", onClick = onShare)
        RailButton(Icons.Rounded.MoreHoriz, "More", onClick = onMore)
        // The song as a turning record: tap to play it in full.
        val reduce = LocalReduceMotion.current
        val turn = androidx.compose.animation.core.rememberInfiniteTransition(label = "disc")
            .animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(6000, easing = androidx.compose.animation.core.LinearEasing)), label = "turn")
        Box(
            Modifier.size(44.dp).graphicsLayer { if (spinning && !reduce) rotationZ = turn.value }.clip(CircleShape)
                .background(Color(0xFF222226)).bounceClick(0.9f, onClick = onSong).padding(7.dp),
            contentAlignment = Alignment.Center,
        ) { Artwork(song.artwork, Modifier.fillMaxSize(), circle = true, size = 160) }
    }
}

@Composable
private fun RailButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = Color.White, scale: Float = 1f,
                       themed: Boolean = false, onClick: () -> Unit) {
    // The app's media glass, with plain icons: crisp over any video, in every festive theme.
    Column(Modifier.bounceClick(0.86f, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(50.dp).mediaGlass(CircleShape, null, emphasis = .9f), contentAlignment = Alignment.Center) {
            // The heart follows the festival look (its pixel heart and colour); the rest stay plain.
            val m = Modifier.size(26.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            if (themed) Icon(icon, label, tint = tint, modifier = m) else androidx.compose.material3.Icon(icon, label, tint = tint, modifier = m)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = BopType.Micro.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified), color = Color.White, maxLines = 1)
    }
}

/** A heart where a double tap landed: pops, tilts, rises and fades. */
@Composable
private fun TapHeart(at: androidx.compose.ui.geometry.Offset, onDone: () -> Unit) {
    val p = remember { androidx.compose.animation.core.Animatable(0f) }
    val tilt = remember { (-18..18).random().toFloat() }
    LaunchedEffect(Unit) { p.animateTo(1f, androidx.compose.animation.core.tween(800)); onDone() }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val size = 96.dp
    val half = with(density) { size.toPx() / 2 }
    Icon(Icons.Rounded.Favorite, null, tint = Bop.Like, modifier = Modifier.size(size).graphicsLayer {
        val v = p.value
        translationX = at.x - half; translationY = at.y - half - v * half
        val s = if (v < .2f) .4f + v / .2f * .8f else 1.2f - (v - .2f) * .25f
        scaleX = s; scaleY = s; rotationZ = tilt
        alpha = 1f - ((v - .6f) / .4f).coerceIn(0f, 1f)
    })
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).bounceClick(0.98f, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = BopType.BodyStrong, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = BopType.Caption, color = Color.White.copy(alpha = .65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The movie or show a song was made for, from its title or album: "Yeshanagula (From "The
 * Paradise")" → The Paradise; "Barbaad (Movie: Saiyaara)" → Saiyaara.
 */
internal fun soundtrackOf(t: Track): String? {
    val from = Regex("""\((?:From|Movie:|Film:|Series:)\s*["“]?(.+?)["”]?\)""", RegexOption.IGNORE_CASE)
    return from.find(t.title)?.groupValues?.get(1) ?: from.find(t.album)?.groupValues?.get(1)
}

/** A small tag under the song: tap to open where it comes from. */
@Composable
private fun SourceChip(label: String, onClick: () -> Unit) {
    Text(label, style = BopType.Caption, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 170.dp).bounceClick(onClick = onClick).mediaGlass(RoundedCornerShape(50), null, emphasis = .7f)
            .padding(horizontal = 10.dp, vertical = 5.dp))
}
