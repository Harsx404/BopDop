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
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
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
import com.dopdop.bopdop.data.speedAndPitch
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
    /** When headphones or the car disconnecting paused playback (0: it wasn't that). */
    @Volatile private var unpluggedAt = 0L
    private val reconnect = object : android.media.AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out android.media.AudioDeviceInfo>?) {
            val personal = added.orEmpty().any {
                it.isSink && it.type in setOf(
                    android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, android.media.AudioDeviceInfo.TYPE_BLE_HEADSET,
                    android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER, android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET, android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
                )
            }
            // Only within half an hour of that pause, so music never starts out of nowhere later on.
            val recent = unpluggedAt > 0 && System.currentTimeMillis() - unpluggedAt < 30 * 60_000
            if (personal && recent && LibraryStore.settings.resumeOnConnect && !player.playWhenReady && player.mediaItemCount > 0) {
                unpluggedAt = 0L
                Log.i("PlaybackService", "headphones back: resuming")
                // Bluetooth reports the device a moment before audio can actually route to it.
                scope.launch { delay(1200); player.play() }
            }
        }
    }
    /** Where the playing video item was a second ago, to tell "the picture ended" from "the song ended". */
    private data class VideoSpot(val index: Int, val id: String, val positionMs: Long, val songMs: Long)
    @Volatile private var videoAt: VideoSpot? = null
    private var lastTrackId: String? = null
    private var audioSessionId = C.AUDIO_SESSION_ID_UNSET
    private lateinit var sources: VideoMediaSourceFactory
    /** Writes into the play cache; [precacheNext] fills it with the start of the next song. */
    private lateinit var precacheSource: CacheDataSource.Factory
    private var precacheJob: kotlinx.coroutines.Job? = null
    private var addonJob: kotlinx.coroutines.Job? = null
    private var addonJobFor: String? = null
    private lateinit var crossfader: Crossfader
    private lateinit var island: IslandOverlay
    private lateinit var liveUpdate: LiveUpdate

    override fun onCreate() {
        super.onCreate()
        LibraryStore.init(this)
        com.dopdop.bopdop.data.lyrics.LyricOffsets.init(this)
        PlayStats.init(this)
        com.dopdop.bopdop.data.scrobble.ListenBrainz.init(this)
        com.dopdop.bopdop.data.scrobble.LastFm.init(this)

        val resolving = ResolvingDataSource.Factory(OkHttpDataSource.Factory(Http.client)) { spec -> StreamResolver.resolveSpec(spec) }
        val key = androidx.media3.datasource.cache.CacheKeyFactory { spec -> spec.key ?: spec.uri.getQueryParameter("id") ?: spec.uri.toString() }
        val streaming = CacheDataSource.Factory()
            .setCache(cache(this))
            .setUpstreamDataSourceFactory(resolving)
            .setCacheKeyFactory(key)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        // Downloads first: a downloaded song is read whole from its own cache and never needs the network.
        val dataSource = CacheDataSource.Factory()
            .setCache(Downloads.cache(this))
            .setUpstreamDataSourceFactory(streaming)
            .setCacheWriteDataSinkFactory(null)
            .setCacheKeyFactory(key)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        precacheSource = streaming
        val playCache = cache(this)
        scope.launch(kotlinx.coroutines.Dispatchers.IO) { StreamResolver.forgetLooseHiResMatches(this@PlaybackService, playCache) }

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
        // Playback speed is a setting, so it survives restarts and applies to every song.
        scope.launch {
            // The lo-fi mix brings its own speed and pitch.
            LibraryStore.state.map { it.settings.speedAndPitch }.distinctUntilChanged().collect { (speed, pitch) ->
                player.playbackParameters = androidx.media3.common.PlaybackParameters(speed, pitch)
            }
        }
        // The output format is fixed when a player is built: switching precision swaps in a new one.
        scope.launch {
            LibraryStore.state.map { it.settings.floatOutput }.distinctUntilChanged().drop(1).collect { rebuildPlayer() }
        }
        scope.launch {
            // Vocal isolation picks up its mode when playback is flushed: seek to apply a new mode, or
            // the model once its download finishes. ExoPlayer ignores a seek to exactly where it is,
            // so step back a few milliseconds to make it a real one.
            kotlinx.coroutines.flow.combine(LibraryStore.state.map { it.settings.vocalMode }, VocalModel.state.map { it is VocalModel.State.Ready }) { m, ready -> m to ready }
                .distinctUntilChanged().drop(1).collect {
                    if (player.playbackState != Player.STATE_IDLE) player.seekTo((player.currentPosition - 40).coerceAtLeast(0))
                }
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
                player.currentMediaItem?.let { item ->
                    videoAt = if (item.videoId != null) VideoSpot(player.currentMediaItemIndex, item.mediaId, player.currentPosition, (item.toTrack()?.durationSec ?: 0) * 1000L) else null
                }
                if (player.isPlaying) player.currentMediaItem?.toTrack()?.let { PlayStats.tick(it); com.dopdop.bopdop.data.scrobble.ListenBrainz.tick(it); com.dopdop.bopdop.data.scrobble.LastFm.tick(it); com.dopdop.bopdop.data.ytm.YtHistory.tick(it) }
            }
        }

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // F42: the queue as it was when the app was last closed, ready to play (not playing).
        QueueStore.init(this)
        if (player.mediaItemCount == 0) QueueStore.load()?.let { q ->
            player.setMediaItems(q.tracks.map { it.toMediaItem() }, q.index, q.positionMs)
            player.prepare()
            player.playWhenReady = false
        }
        // Saved on every change, and every few seconds while playing so the position is fresh.
        scope.launch {
            while (isActive) {
                delay(2_000)
                if (player.isPlaying) QueueStore.save(player, PlayerConnection.context.value)
            }
        }

        getSystemService(android.media.AudioManager::class.java)?.registerAudioDeviceCallback(reconnect, android.os.Handler(android.os.Looper.getMainLooper()))

        session = MediaLibrarySession.Builder(this, castable(player), LibraryCallback(this))
            .setSessionActivity(open)
            .setMediaButtonPreferences(NotificationButtons.buttons(player))
            .build()
        // The like button follows the library (a like from the app shows in the notification too).
        scope.launch { LibraryStore.state.map { it.likedIds }.distinctUntilChanged().collect { refreshButtons() } }
    }

    /**
     * Downloads the next song into the play cache while this one plays, so moving on (a skip, the
     * next track, Automix's jump mid-song) starts from disk instead of the network. Waits a little
     * first so the current song gets the bandwidth; on mobile data only when Automix is on, since
     * that's where a slow start is heard. The whole song: a partial download left the cache with a
     * wrong length and the song failed when it played past it. (ExoPlayer's own playlist preloading
     * would do this, but on Android 15+ it crashes the switch with a LoudnessCodecController error.)
     */
    private fun precacheNext() {
        precacheJob?.cancel()
        val p = player
        if (!p.hasNextMediaItem()) return
        val next = p.getMediaItemAt(p.nextMediaItemIndex)
        val uri = next.localConfiguration?.uri ?: return
        if (next.videoId != null || uri.scheme == "content" || uri.scheme == "file") return
        val metered = getSystemService(android.net.ConnectivityManager::class.java)?.isActiveNetworkMetered ?: true
        if (metered && LibraryStore.settings.crossfadeSec <= 0) return
        precacheJob = scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.delay(10_000)
            val spec = androidx.media3.datasource.DataSpec.Builder().setUri(uri).setPosition(0).build()
            val writer = androidx.media3.datasource.cache.CacheWriter(precacheSource.createDataSourceForDownloading(), spec, null, null)
            val job = coroutineContext[kotlinx.coroutines.Job]
            job?.invokeOnCompletion { if (it != null) writer.cancel() }
            runCatching { writer.cache() }
                .onSuccess { Log.i("PlaybackService", "precached ${next.mediaId}") }
                .onFailure { if (it !is java.io.InterruptedIOException && it !is kotlinx.coroutines.CancellationException) Log.w("PlaybackService", "precache ${next.mediaId}: $it") }
        }
    }

    /**
     * "Lossless from add-ons" without the wait: every song starts on its fastest source while this
     * asks the add-ons in the background, and moves onto a copy seamlessly once it's playing. The next
     * song is asked about early too, so its switch comes within seconds of it starting.
     */
    private fun lookUpAddons() {
        // Moving the song onto its add-on copy replaces its item, which lands here again: that same
        // song's job is the one doing the move, and must carry on.
        val curId = player.currentMediaItem?.mediaId
        if (addonJob?.isActive == true && addonJobFor == curId) return
        addonJob?.cancel()
        addonJobFor = curId
        if (!StreamResolver.addonsWanted()) return
        addonJob = scope.launch {
            val cur = player.currentMediaItem
            val track = cur?.toTrack()
            if (cur != null && track != null && cur.videoId == null && !cur.isAddonCopy && !Downloads.isDownloaded(track.id)) {
                // Only add-ons ranked above what it's playing from now.
                if (StreamResolver.lookUpAddon(track, StreamResolver.playingFrom(track.id)) != null) {
                    // Found while paused, or a try that didn't work out (Automix mixing, a slow start):
                    // switch once it's playing, and try again a little later.
                    for (attempt in 0 until 3) {
                        while (!player.isPlaying) {
                            if (player.currentMediaItem?.mediaId != track.id) break
                            kotlinx.coroutines.delay(400)
                        }
                        if (player.currentMediaItem?.mediaId != track.id || player.currentMediaItem?.isAddonCopy != false) break
                        if (crossfader.upgrade(track.toMediaItem(addon = true))) break
                        kotlinx.coroutines.delay(15_000)
                    }
                }
            }
            // Ask about the next song now too: it still starts on its fastest source, but with the copy
            // already known it moves onto it within seconds.
            if (!player.hasNextMediaItem()) return@launch
            val next = player.getMediaItemAt(player.nextMediaItemIndex)
            val nt = next.toTrack() ?: return@launch
            if (next.videoId == null && !Downloads.isDownloaded(nt.id)) StreamResolver.lookUpAddon(nt, StreamResolver.bestBuiltIn())
        }
    }

    /**
     * The main player owns audio focus, the notification and the queue. Tail players (Automix's
     * outgoing song) share its audio session so the equalizer shapes both, and never take focus.
     */
    private fun buildPlayer(main: Boolean): ExoPlayer {
        val float = LibraryStore.settings.floatOutput
        val leveler = Leveler()
        val splitter = VocalSplitter()
        val reverb = LofiReverb()
        if (main) AudioPipeline.leveler = leveler
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: android.content.Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    // Speed and pitch go to the AudioTrack, so a change is heard at once. Done in the
                    // processor chain instead, it waits for the chain to drain, which with vocal
                    // isolation's read-ahead can take half a minute.
                    .setEnableAudioTrackPlaybackParams(true)
                    .setAudioProcessorChain(OutputChain(splitter, leveler, reverb, float))
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
                LibraryStore.settings.speedAndPitch.let { (speed, pitch) -> it.playbackParameters = androidx.media3.common.PlaybackParameters(speed, pitch) }
                if (main) {
                    // Shuffle and repeat pick up where they were left.
                    it.shuffleModeEnabled = LibraryStore.settings.shuffle
                    it.repeatMode = LibraryStore.settings.repeat
                    it.addListener(listener); it.addAnalyticsListener(AudioPipeline.listener)
                }
            }
    }

    /**
     * Vocal isolation first (it needs the untouched mix), then the leveler and the lo-fi reverb;
     * silence skipping, speed and pitch follow as in the default chain. For 32-bit output
     * the result is converted to float last, so every stage keeps working and the AudioTrack runs in
     * float whatever the decoder emits (Android's own AAC/Opus decoders ignore float requests).
     */
    private class OutputChain(splitter: VocalSplitter, leveler: Leveler, reverb: LofiReverb, float: Boolean) : DefaultAudioSink.AudioProcessorChain {
        private val inner = DefaultAudioSink.DefaultAudioProcessorChain(splitter, leveler, reverb)
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
    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            precacheNext()
            lookUpAddons()
            val was = videoAt
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && was != null && was.songMs > 0 && was.positionMs < was.songMs - 8_000) {
                // The picture ran out (or its source ended early), not the song: carry on with the song's audio.
                val prev = player.getMediaItemAt(was.index.coerceAtMost(player.mediaItemCount - 1))
                if (prev.mediaId == was.id) prev.toTrack()?.let { t ->
                    Log.w("PlaybackService", "video item $was ended early; back to its audio")
                    videoAt = null
                    player.replaceMediaItem(was.index, t.toMediaItem())
                    player.seekTo(was.index, was.positionMs)
                    PlayerConnection.onVideoFailure(was.id)
                    return
                }
            }
            if (mediaItem?.videoId == null) videoAt = null
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                mediaItem?.mediaId == lastTrackId) return
            lastTrackId = mediaItem?.mediaId
            mediaItem?.toTrack()?.let(LibraryStore::recordPlay)
            PlayStats.newSession()
            com.dopdop.bopdop.data.scrobble.ListenBrainz.onTrack(mediaItem?.toTrack())
            com.dopdop.bopdop.data.scrobble.LastFm.onTrack(mediaItem?.toTrack())
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (LibraryStore.settings.shuffle != shuffleModeEnabled) LibraryStore.updateSettings { it.copy(shuffle = shuffleModeEnabled) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            if (LibraryStore.settings.repeat != repeatMode) LibraryStore.updateSettings { it.copy(repeat = repeatMode) }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED, Player.EVENT_REPEAT_MODE_CHANGED)) refreshButtons()
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
            if (item.isAddonCopy) {
                // The add-on copy wouldn't play: back to the song's usual stream, where it was.
                StreamResolver.dropAddon(id)
                val index = player.currentMediaItemIndex
                val position = player.currentPosition.coerceAtLeast(0)
                item.toTrack()?.let {
                    player.replaceMediaItem(index, it.toMediaItem())
                    player.seekTo(index, position)
                    player.prepare()
                    return
                }
            }
            if (item.videoId != null) {
                VideoResolver.invalidate(item.videoId!!)
                // The merged item also carried the song's own stream; a stale one would fail the fallback too.
                StreamResolver.invalidate(id)
                retried.remove(id)
                videoAt = null
                val index = player.currentMediaItemIndex
                val songMs = (item.toTrack()?.durationSec ?: 0) * 1000L
                val position = player.currentPosition.let { if (songMs > 0) it.coerceIn(0, songMs - 1000) else it.coerceAtLeast(0) }
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

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            QueueStore.save(player, PlayerConnection.context.value)
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            QueueStore.save(player, PlayerConnection.context.value)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // F51: remember a pause caused by headphones or the car going away, and nothing else.
            unpluggedAt = if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) System.currentTimeMillis() else 0L
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) QueueStore.save(player, PlayerConnection.context.value)
            if (isPlaying) player.currentMediaItem?.mediaId?.let(retried::remove)
        }
    }

    /** Autoplay: when the queue is about to run dry, append related tracks. */
    private fun maybeExtendQueue() {
        if (!LibraryStore.settings.autoplay || extending) return
        if (player.repeatMode != Player.REPEAT_MODE_OFF) return
        if (!needsMoreMusic()) return
        val seed = player.currentMediaItem?.toTrack() ?: return
        // The two songs before this one, unless they were skipped: autoplay follows the session.
        val before = (player.currentMediaItemIndex - 1 downTo maxOf(0, player.currentMediaItemIndex - 4))
            .mapNotNull { player.getMediaItemAt(it).toTrack() }.filter { !it.isPodcast && !com.dopdop.bopdop.data.PlayStats.skippedLately(it.id) }.take(2)
        extending = true
        scope.launch {
            try {
                val more = MusicRepository.related(seed, before)
                val sameQueue = player.queueTracks().any { it.id == seed.id }
                if (LibraryStore.settings.autoplay && player.repeatMode == Player.REPEAT_MODE_OFF &&
                    needsMoreMusic() && sameQueue) {
                    val firstNewIndex = player.mediaItemCount
                    val wasEnded = player.playbackState == Player.STATE_ENDED
                    val added = player.appendRadio(more, limit = 20)
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
        QueueStore.save(player, PlayerConnection.context.value)
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    private fun refreshButtons() {
        val s = session ?: return
        s.setMediaButtonPreferences(NotificationButtons.buttons(s.player))
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
        WidgetUpdater.update(this, player.currentMediaItem?.toTrack(), player.isPlaying, player.currentPosition, player.duration.coerceAtLeast(0), next, player.shuffleModeEnabled)
    }

    override fun onDestroy() {
        runCatching { QueueStore.save(player, PlayerConnection.context.value) }
        runCatching { getSystemService(android.media.AudioManager::class.java)?.unregisterAudioDeviceCallback(reconnect) }
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
        /** Shuffle on or off (from the widget). */
        fun toggleShuffle() {
            val s = running ?: return
            s.player.shuffleModeEnabled = !s.player.shuffleModeEnabled
            s.pushWidgets()
        }

        fun jumpTo(index: Int) {
            val s = running ?: return
            if (index < 0 || index >= s.player.mediaItemCount) return
            s.player.seekTo(index, 0L)
            s.player.play()
        }

        fun cache(ctx: android.content.Context): SimpleCache = simpleCache ?: synchronized(this) {
            simpleCache ?: SimpleCache(
                cacheDir(ctx),
                LibraryStore.settings.cacheMb.let { mb -> if (mb <= 0) NoOpCacheEvictor() else LeastRecentlyUsedCacheEvictor(mb * 1024L * 1024L) },
                Databases.provider(ctx),
            ).also { simpleCache = it }
        }

        /**
         * cache/ can be emptied by Android when storage runs low (and by some phones on update);
         * "Keep cached songs" moves the folder into app storage, which only the user clears.
         * The folder moves with the setting, so nothing is re-downloaded.
         */
        private fun cacheDir(ctx: android.content.Context): File {
            val kept = File(ctx.filesDir, "audio")
            val loose = File(ctx.cacheDir, "audio")
            val (want, other) = if (LibraryStore.settings.keepCache) kept to loose else loose to kept
            if (!want.exists() && other.exists()) other.renameTo(want)
            return want
        }
    }
}
