package com.dopdop.bopdop.ui.player

import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.model.Source
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.spotify.SpotifyAccount
import com.dopdop.bopdop.data.spotify.SpotifyApi
import com.dopdop.bopdop.data.spotify.SpotifyMatch
import com.dopdop.bopdop.playback.PlayerConnection

/**
 * Spotify Canvas for the song playing: the short looping video Spotify shows behind a track.
 * Found from the song's Spotify match (or a Spotify search), only when signed in to Spotify, with
 * the setting on, and on Wi-Fi unless "on mobile data too" is allowed.
 */
@Composable
fun rememberCanvasUrl(t: Track): String? {
    val ctx = LocalContext.current
    val s = LibraryStore.state.collectAsState().value.settings
    val signedIn = SpotifyAccount.profile.collectAsState().value != null
    val url by produceState<String?>(null, t.id, signedIn, s.canvas, s.canvasOnData) {
        value = null
        if (!signedIn || !s.canvas || t.source == Source.LOCAL) return@produceState
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
        if (!s.canvasOnData && cm?.isActiveNetworkMetered != false) return@produceState
        val uri = SpotifyMatch.spotifyUri(t.id) ?: SpotifyApi.findTrack(t.title, t.artist) ?: return@produceState
        value = SpotifyApi.canvas(uri)
    }
    return url
}

/** The Canvas, muted and looping, cover-cropped; pauses with the music and fades in once it has a frame. */
@OptIn(UnstableApi::class)
@Composable
fun CanvasVideo(url: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val playing by PlayerConnection.isPlaying.collectAsState()
    var ready by remember(url) { mutableStateOf(false) }
    var aspect by remember(url) { mutableFloatStateOf(9f / 16f) }
    val player = remember(url) {
        ExoPlayer.Builder(ctx).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            // Canvases are silent loops: skip the audio decoder entirely.
            trackSelectionParameters = trackSelectionParameters.buildUpon().setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO, true).build()
            setMediaItem(MediaItem.fromUri(url))
            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() { ready = true }
                override fun onVideoSizeChanged(size: VideoSize) { if (size.width > 0 && size.height > 0) aspect = size.width.toFloat() / size.height }
            })
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(playing, player) { player.playWhenReady = playing }
    val alpha by animateFloatAsState(if (ready) 1f else 0f, tween(700), label = "canvas")
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds().graphicsLayer { this.alpha = alpha }, contentAlignment = androidx.compose.ui.Alignment.Center) {
        AndroidView(
            factory = { c -> TextureView(c).also { player.setVideoTextureView(it) } },
            modifier = coverPastBars(aspect, Bars.NONE),
            onRelease = { player.clearVideoTextureView(it) },
        )
    }
}
