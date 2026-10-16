package com.dopdop.bopdop.playback

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.dopdop.bopdop.MainActivity
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.MusicRepository
import com.dopdop.bopdop.data.PlayStats
import com.dopdop.bopdop.data.net.Http
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val scope = MainScope()
    private var extending = false
    private val retried = HashSet<String>()
    private var lastTrackId: String? = null

    override fun onCreate() {
        super.onCreate()
        LibraryStore.init(this)
        PlayStats.init(this)

        val resolving = ResolvingDataSource.Factory(OkHttpDataSource.Factory(Http.client)) { spec -> resolve(spec) }
        val dataSource = CacheDataSource.Factory()
            .setCache(cache(this))
            .setUpstreamDataSourceFactory(resolving)
            .setCacheKeyFactory { spec -> spec.key ?: spec.uri.getQueryParameter("id") ?: spec.uri.toString() }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(VideoMediaSourceFactory(DefaultMediaSourceFactory(dataSource)))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(listener)
        scope.launch {
            LibraryStore.state.map { it.settings.autoplay }.distinctUntilChanged().collect { enabled ->
                if (enabled) maybeExtendQueue()
            }
        }
        // Our own audio session so the equalizer can sit on it from the first note.
        val sessionId = androidx.media3.common.util.Util.generateAudioSessionIdV21(this)
        player.setAudioSessionId(sessionId)
        AudioFx.attach(sessionId)
        scope.launch { AudioFx.state.collect { AudioFx.apply(it) } }
        // Replay: count real listening time, one second at a time.
        scope.launch {
            while (isActive) {
                delay(1000)
                if (player.isPlaying) player.currentMediaItem?.toTrack()?.let { PlayStats.tick(it) }
            }
        }

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback)
            .setSessionActivity(open)
            .build()
    }

    /** `bopdop://play?...` → real CDN url, carrying the User-Agent the url is bound to. */
    private fun resolve(spec: DataSpec): DataSpec {
        if (spec.uri.scheme != StreamResolver.SCHEME) return spec
        val s = StreamResolver.resolve(spec.uri)
        val headers = HashMap(spec.httpRequestHeaders)
        s.userAgent?.let { headers["User-Agent"] = it }
        return spec.buildUpon().setUri(s.url).setHttpRequestHeaders(headers).build()
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                mediaItem?.mediaId == lastTrackId) return
            lastTrackId = mediaItem?.mediaId
            mediaItem?.toTrack()?.let(LibraryStore::recordPlay)
            PlayStats.newSession()
        }

        override fun onEvents(player: Player, events: Player.Events) {
            // Video is an explicit choice for the current song; queued and past items remain audio.
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                for (i in 0 until player.mediaItemCount) {
                    val item = player.getMediaItemAt(i)
                    if (i != player.currentMediaItemIndex && item.videoId != null) {
                        item.toTrack()?.let { player.replaceMediaItem(i, it.toMediaItem()) }
                    }
                }
            }
            // Removing upcoming songs changes the timeline without changing the playing song.
            // Read the timeline and current position together after all callbacks have settled.
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                )) maybeExtendQueue()
        }

        override fun onPlayerError(error: PlaybackException) {
            val item = player.currentMediaItem ?: return
            val id = item.mediaId
            Log.w("PlaybackService", "error on $id: ${error.errorCodeName}", error)
            if (item.videoId != null) {
                VideoResolver.invalidate(item.videoId!!)
                val index = player.currentMediaItemIndex
                val position = player.currentPosition
                item.toTrack()?.let {
                    player.replaceMediaItem(index, it.toMediaItem())
                    player.seekTo(index, position)
                    player.prepare()
                    PlayerConnection.onVideoFailure(id)
                    return
                }
            }
            StreamResolver.invalidate(id)
            // A partial download from a dead url may be a different format than the next one — drop it.
            runCatching { cache(this@PlaybackService).removeResource(id) }
            if (retried.add(id)) {
                // Expired / IP-bound url: resolve again (other clients get a turn) and resume in place.
                player.prepare()
                player.play()
            } else if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) player.currentMediaItem?.mediaId?.let(retried::remove)
        }
    }

    /** Autoplay: when the queue is about to run dry, append related tracks. */
    private fun maybeExtendQueue() {
        if (!LibraryStore.settings.autoplay || extending) return
        if (player.repeatMode != Player.REPEAT_MODE_OFF) return
        if (!needsMoreMusic()) return
        val seed = player.currentMediaItem?.toTrack() ?: return
        extending = true
        scope.launch {
            try {
                val more = MusicRepository.related(seed)
                val sameQueue = player.queueTracks().any { it.id == seed.id }
                if (LibraryStore.settings.autoplay && player.repeatMode == Player.REPEAT_MODE_OFF &&
                    needsMoreMusic() && sameQueue) {
                    val firstNewIndex = player.mediaItemCount
                    val wasEnded = player.playbackState == Player.STATE_ENDED
                    val added = player.appendUnique(more, limit = 20)
                    if (wasEnded && added > 0) {
                        // Appending after STATE_ENDED alone does not advance the player.
                        // Seeking retains playWhenReady, including a pause while radio was loading.
                        player.seekTo(firstNewIndex, 0L)
                        player.prepare()
                    }
                }
            } catch (e: Exception) {
                Log.w("PlaybackService", "autoplay failed: ${e.message}")
            } finally {
                extending = false
            }
        }
    }

    private fun needsMoreMusic(): Boolean {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return false
        val next = timeline.getNextWindowIndex(player.currentMediaItemIndex, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
        return next == C.INDEX_UNSET ||
            timeline.getNextWindowIndex(next, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled) == C.INDEX_UNSET
    }

    private object SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { it.withPlaybackUri() }.toMutableList())
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        AudioFx.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var simpleCache: SimpleCache? = null

        fun cache(ctx: android.content.Context): SimpleCache = simpleCache ?: synchronized(this) {
            simpleCache ?: SimpleCache(
                File(ctx.cacheDir, "audio"),
                LeastRecentlyUsedCacheEvictor(LibraryStore.settings.cacheMb * 1024L * 1024L),
                StandaloneDatabaseProvider(ctx),
            ).also { simpleCache = it }
        }
    }
}
