package com.dopdop.bopdop.playback

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.RemoteCastPlayer
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
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ToFloatPcmAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.dopdop.bopdop.MainActivity
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.MusicRepository
import com.dopdop.bopdop.data.PlayStats
import com.dopdop.bopdop.data.net.Http
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private val scope = MainScope()
    private var extending = false
    private val retried = HashSet<String>()
    private var lastTrackId: String? = null
    private var audioSessionId = C.AUDIO_SESSION_ID_UNSET
    private lateinit var sources: VideoMediaSourceFactory
    private lateinit var crossfader: Crossfader
    private lateinit var island: IslandOverlay
    private lateinit var liveUpdate: LiveUpdate

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

        sources = VideoMediaSourceFactory(DefaultMediaSourceFactory(dataSource), DefaultMediaSourceFactory(DefaultDataSource.Factory(this)))
        // Our own audio session so the equalizer can sit on it from the first note.
        audioSessionId = androidx.media3.common.util.Util.generateAudioSessionIdV21(this)
        player = buildPlayer(main = true)
        crossfader = Crossfader(scope) { buildPlayer(main = false) }
        crossfader.attach(player)
        LockScreenLyrics(scope) { player }
        scope.launch {
            LibraryStore.state.map { it.settings.skipSilence }.distinctUntilChanged().collect { player.skipSilenceEnabled = it }
        }
        // The output format is fixed when a player is built: switching precision swaps in a new one.
        scope.launch {
            LibraryStore.state.map { it.settings.floatOutput }.distinctUntilChanged().drop(1).collect { rebuildPlayer() }
        }
        scope.launch {
            LibraryStore.state.map { it.settings.autoplay }.distinctUntilChanged().collect { enabled ->
                if (enabled) maybeExtendQueue()
            }
        }
        AudioFx.attach(audioSessionId)
        scope.launch { AudioFx.state.collect { AudioFx.apply(it) } }
        // With a music video, the song still decides when to move on. The item's length is the
        // song's own audio (the picture is trimmed to the listed length), so if the real audio ends
        // a little before the listed length, go to the next song now rather than wait for the picture.
        scope.launch {
            var movedFrom: String? = null
            while (isActive) {
                delay(200)
                val item = player.currentMediaItem ?: continue
                if (item.videoId == null || !player.isPlaying) continue
                val dur = player.duration
                if (dur > 0 && player.currentPosition < dur - 1_000) movedFrom = null
                if (dur > 0 && player.currentPosition >= dur - 150 && movedFrom != item.mediaId && player.hasNextMediaItem() &&
                    player.repeatMode != Player.REPEAT_MODE_ONE) {
                    movedFrom = item.mediaId
                    player.seekToNextMediaItem()
                }
            }
        }
        // The island over other apps follows the setting and whether BopDop itself is on screen.
        island = IslandOverlay(this) { player }
        running = this
        // Widgets show progress; refresh them now and then while music plays.
        scope.launch {
            while (isActive) {
                delay(15_000)
                if (player.isPlaying) pushWidgets()
            }
        }
        liveUpdate = LiveUpdate(this)
        // The Live Update's progress bar moves on its own clock; nudge it every few seconds.
        scope.launch {
            while (isActive) {
                delay(5_000)
                if (player.isPlaying) liveUpdate.update(player.currentMediaItem?.toTrack(), true, player.currentPosition, player.duration)
            }
        }
        scope.launch { LibraryStore.state.map { it.settings.liveUpdate }.distinctUntilChanged().collect {
            liveUpdate.update(player.currentMediaItem?.toTrack(), player.isPlaying, player.currentPosition, player.duration)
        } }
        scope.launch { AppVisibility.foreground.collect { island.refresh() } }
        scope.launch { LibraryStore.state.map { it.settings.islandOverlay }.distinctUntilChanged().collect { island.refresh() } }
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
        session = MediaLibrarySession.Builder(this, castable(player), LibraryCallback(this))
            .setSessionActivity(open)
            .build()
    }

    /**
     * The main player owns audio focus, the notification and the queue. Tail players (Automix's
     * outgoing song) share its audio session so the equalizer shapes both, and never take focus.
     */
    private fun buildPlayer(main: Boolean): ExoPlayer {
        val float = LibraryStore.settings.floatOutput
        val leveler = Leveler()
        if (main) AudioPipeline.leveler = leveler
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: android.content.Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessorChain(OutputChain(leveler, float))
                    .build()
        }
        return ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(sources)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), main)
            .setHandleAudioBecomingNoisy(main)
            .setWakeMode(if (main) C.WAKE_MODE_NETWORK else C.WAKE_MODE_NONE)
            .build()
            .also {
                it.setAudioSessionId(audioSessionId)
                it.skipSilenceEnabled = LibraryStore.settings.skipSilence
                if (main) { it.addListener(listener); it.addAnalyticsListener(AudioPipeline.listener) }
            }
    }

    /**
     * Leveler first; silence skipping and speed follow as in the default chain. For 32-bit output
     * the result is converted to float last, so every stage keeps working and the AudioTrack runs in
     * float whatever the decoder emits (Android's own AAC/Opus decoders ignore float requests).
     */
    private class OutputChain(leveler: Leveler, float: Boolean) : DefaultAudioSink.AudioProcessorChain {
        private val inner = DefaultAudioSink.DefaultAudioProcessorChain(leveler)
        private val processors = inner.audioProcessors + listOfNotNull(if (float) ToFloatPcmAudioProcessor() else null)
        override fun getAudioProcessors(): Array<androidx.media3.common.audio.AudioProcessor> = processors
        override fun applyPlaybackParameters(playbackParameters: androidx.media3.common.PlaybackParameters) = inner.applyPlaybackParameters(playbackParameters)
        override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean) = inner.applySkipSilenceEnabled(skipSilenceEnabled)
        override fun getMediaDuration(playoutDuration: Long) = inner.getMediaDuration(playoutDuration)
        override fun getSkippedOutputFrameCount() = inner.getSkippedOutputFrameCount()
    }

    /**
     * The session's player: the local ExoPlayer wrapped so a Cast device can take over and hand
     * back. Without Google Play services the local player is used as is.
     */
    private fun castable(local: ExoPlayer): Player = runCatching {
        CastPlayer.Builder(this).setLocalPlayer(local)
            .setRemotePlayer(RemoteCastPlayer.Builder(this).setMediaItemConverter(CastConverter).build())
            .build()
    }.onFailure { Log.w("PlaybackService", "Cast unavailable: ${it.message}") }.getOrDefault(local)

    /** New output precision: carry the queue, position and state over to a freshly built player. */
    private fun rebuildPlayer() {
        val old = player
        crossfader.finish()
        val items = (0 until old.mediaItemCount).map { old.getMediaItemAt(it) }
        val next = buildPlayer(main = true)
        Log.i("PlaybackService", "output precision changed: rebuilt player (float=${LibraryStore.settings.floatOutput})")
        next.repeatMode = old.repeatMode
        next.shuffleModeEnabled = old.shuffleModeEnabled
        if (items.isNotEmpty()) {
            next.setMediaItems(items, old.currentMediaItemIndex, old.currentPosition)
            next.prepare()
        }
        next.playWhenReady = old.playWhenReady
        old.removeListener(listener)
        old.removeAnalyticsListener(AudioPipeline.listener)
        session?.player = castable(next)
        player = next
        crossfader.attach(next)
        old.release()
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
            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_TIMELINE_CHANGED)) {
                pushWidgets()
                island.update(player.currentMediaItem?.toTrack(), player.isPlaying)
                liveUpdate.update(player.currentMediaItem?.toTrack(), player.isPlaying, player.currentPosition, player.duration)
            }
            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_TIMELINE_CHANGED)) {
                val from = player.currentMediaItemIndex
                CastPrefetch.onQueue((from until minOf(player.mediaItemCount, from + 4)).mapNotNull { player.getMediaItemAt(it).toTrack() })
            }
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    /** Hand the widgets the song, its progress and the next few songs. */
    private fun pushWidgets() {
        val tl = player.currentTimeline
        val w = androidx.media3.common.Timeline.Window()
        val next = ArrayList<Pair<Int, com.dopdop.bopdop.data.model.Track>>()
        var i = player.currentMediaItemIndex
        while (next.size < 4 && !tl.isEmpty) {
            i = tl.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
            if (i == C.INDEX_UNSET) break
            tl.getWindow(i, w).mediaItem.toTrack()?.let { next += i to it }
        }
        WidgetUpdater.update(this, player.currentMediaItem?.toTrack(), player.isPlaying, player.currentPosition, player.duration.coerceAtLeast(0), next)
    }

    override fun onDestroy() {
        running = null
        island.release()
        liveUpdate.cancel()
        crossfader.release()
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
        @Volatile private var running: PlaybackService? = null

        /** Jump to a song in the queue (from the Up Next widget). */
        fun jumpTo(index: Int) {
            val s = running ?: return
            if (index < 0 || index >= s.player.mediaItemCount) return
            s.player.seekTo(index, 0L)
            s.player.play()
        }

        fun cache(ctx: android.content.Context): SimpleCache = simpleCache ?: synchronized(this) {
            simpleCache ?: SimpleCache(
                File(ctx.cacheDir, "audio"),
                LeastRecentlyUsedCacheEvictor(LibraryStore.settings.cacheMb * 1024L * 1024L),
                StandaloneDatabaseProvider(ctx),
            ).also { simpleCache = it }
        }
    }
}
