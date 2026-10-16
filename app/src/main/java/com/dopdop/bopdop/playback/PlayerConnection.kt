package com.dopdop.bopdop.playback

import android.content.ComponentName
import android.content.Context
import android.view.TextureView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.MusicRepository
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.model.MusicVideoMatch
import com.dopdop.bopdop.data.model.VideoSegment
import com.dopdop.bopdop.data.ytm.ResolvedVideo
import com.dopdop.bopdop.data.model.songToVideo
import com.dopdop.bopdop.data.model.videoToSong
import com.dopdop.bopdop.data.ytm.YtMusic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RepeatMode { OFF, ALL, ONE }

/** UI-side handle on the playback service: commands in, observable state out. */
object PlayerConnection {
    private val scope = MainScope()
    private var controller: MediaController? = null
    private val pending = mutableListOf<(MediaController) -> Unit>()
    private var playGeneration = 0L
    private var videoJob: Job? = null
    private var videoRequest = 0L
    /** Song ⇄ video timing for the item currently playing as video. */
    private var videoSegments: List<VideoSegment> = emptyList()
    private val _video = MutableStateFlow(VideoPlaybackState())
    val video: StateFlow<VideoPlaybackState> = _video.asStateFlow()

    private val _current = MutableStateFlow<Track?>(null)
    val current: StateFlow<Track?> = _current.asStateFlow()
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val _buffering = MutableStateFlow(false)
    val buffering: StateFlow<Boolean> = _buffering.asStateFlow()
    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()
    private val _index = MutableStateFlow(0)
    val index: StateFlow<Int> = _index.asStateFlow()
    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()
    private val _repeat = MutableStateFlow(RepeatMode.OFF)
    val repeat: StateFlow<RepeatMode> = _repeat.asStateFlow()
    private val _context = MutableStateFlow("")
    /** "Playing from …" label. */
    val context: StateFlow<String> = _context.asStateFlow()
    private val _sleepAt = MutableStateFlow<Long?>(null)
    /** Wall-clock ms the sleep timer fires at (Long.MAX_VALUE = end of track). */
    val sleepAt: StateFlow<Long?> = _sleepAt.asStateFlow()
    private var sleepJob: Job? = null

    val position: Long get() = controller?.currentPosition ?: 0L
    val bufferedPosition: Long get() = controller?.bufferedPosition ?: 0L
    val duration: Long get() = controller?.duration?.takeIf { it > 0 } ?: ((_current.value?.durationSec ?: 0) * 1000L)

    fun connect(context: Context) {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            sync(c)
            pending.forEach { it(c) }
            pending.clear()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun withController(block: (MediaController) -> Unit) {
        controller?.let(block) ?: pending.add(block)
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { sync(player) }
    }

    private fun sync(p: Player) {
        val item = p.currentMediaItem
        val video = _video.value
        if (video.trackId != item?.mediaId) {
            videoRequest++
            videoJob?.cancel()
            _video.value = VideoPlaybackState(trackId = item?.mediaId, active = item?.videoId != null)
        } else {
            _video.value = video.copy(active = item?.videoId != null)
        }
        _current.value = p.currentMediaItem?.toTrack()
        _isPlaying.value = p.isPlaying
        _buffering.value = p.playbackState == Player.STATE_BUFFERING
        _index.value = p.currentMediaItemIndex
        _shuffle.value = p.shuffleModeEnabled
        _repeat.value = when (p.repeatMode) { Player.REPEAT_MODE_ONE -> RepeatMode.ONE; Player.REPEAT_MODE_ALL -> RepeatMode.ALL; else -> RepeatMode.OFF }
        val tl = p.currentTimeline
        val w = Timeline.Window()
        _queue.value = (0 until tl.windowCount).mapNotNull { tl.getWindow(it, w).mediaItem.toTrack() }
        if (_sleepAt.value == Long.MAX_VALUE && p.playbackState == Player.STATE_ENDED) cancelSleep()
    }

    // --- commands -------------------------------------------------------------------

    fun play(tracks: List<Track>, start: Int = 0, context: String = "", shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        cancelVideoRequest()
        val selected = tracks[start.coerceIn(tracks.indices)]
        val unique = uniqueQueueTracks(tracks)
        playGeneration++
        _context.value = context
        withController { c ->
            c.shuffleModeEnabled = shuffle
            val first = if (shuffle) unique.indices.random() else unique.indexOfFirst { sameQueueSong(it, selected) }
            c.setMediaItems(unique.map { it.toMediaItem() }, first, 0L)
            c.prepare()
            c.play()
        }
    }

    fun playSingle(t: Track, context: String = "") = startRadio(t, context)

    /** Resolve while audio keeps playing, then replace only the current item at its live position. */
    fun setVideoEnabled(enabled: Boolean) = withController { c ->
        if (enabled && (_video.value.loading || c.currentMediaItem?.videoId != null)) return@withController
        cancelVideoRequest()
        val item = c.currentMediaItem ?: return@withController
        val track = item.toTrack() ?: return@withController
        if (!enabled) {
            _video.value = VideoPlaybackState(trackId = track.id)
            if (item.videoId != null) {
                val index = c.currentMediaItemIndex
                val position = videoSegments.videoToSong(c.currentPosition)
                videoSegments = emptyList()
                c.replaceMediaItem(index, track.toMediaItem())
                c.seekTo(index, position)
                c.prepare()
            }
            return@withController
        }
        if (item.videoId != null) return@withController
        val request = videoRequest
        val generation = playGeneration
        _video.value = VideoPlaybackState(trackId = track.id, loading = true)
        videoJob = scope.launch {
            try {
                val cap = VideoQualityPolicy.maxHeight(LibraryStore.settings.videoQuality)
                val (match, streams, songAt) = withContext(Dispatchers.IO) {
                    val match = YtMusic.musicVideoFor(track) ?: throw IllegalStateException("No music video found for this song")
                    val streams = VideoResolver.prepare(match.videoId, cap)
                    Triple(match, streams, songStartInVideo(track, match, streams))
                }
                if (request != videoRequest || generation != playGeneration || c.currentMediaItem?.mediaId != track.id) return@launch
                val index = c.currentMediaItemIndex
                // With the song's own audio the timeline stays the song's; otherwise map into the video's.
                val segments = if (songAt != null) emptyList() else match.segments
                val position = segments.songToVideo(c.currentPosition)
                videoSegments = segments
                _video.value = VideoPlaybackState(track.id, active = true, songAudio = songAt != null,
                    aspectRatio = if (streams.width > 0 && streams.height > 0) streams.width.toFloat() / streams.height else 16f / 9f,
                    height = streams.height)
                c.replaceMediaItem(index, track.toMediaItem().forVideo(match.videoId, streams.audio != null, cap, songAt))
                c.seekTo(index, position)
                c.prepare()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (request == videoRequest && c.currentMediaItem?.mediaId == track.id) {
                    _video.value = VideoPlaybackState(trackId = track.id,
                        error = "Music video unavailable. Keep listening or try again.")
                    android.util.Log.w("VideoPlayback", "Video unavailable for ${track.id}: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Where the song begins in the video, so its own JioSaavn / YouTube Music stream can play under
     * the picture. Null → the video's soundtrack is used instead (can't line them up with confidence).
     */
    private fun songStartInVideo(track: Track, match: MusicVideoMatch, streams: ResolvedVideo): Long? {
        val sound = streams.audio ?: return null
        val key = "${track.id}>${match.videoId}"
        VideoQualityPolicy.songStart(key)?.let { return it }
        val at = when {
            match.videoId == track.rawId -> 0L
            // YouTube Music's own map; a single stretch means the video only adds an intro/outro.
            match.segments.size == 1 -> match.segments[0].let { it.videoStartMs - it.songStartMs }
            match.segments.size > 1 -> null
            else -> runCatching { AudioAlign.songStartInVideo(StreamResolver.resolve(StreamResolver.playUri(track)), sound) }.getOrNull()
        }?.takeIf { it >= 0 }
        at?.let { VideoQualityPolicy.rememberSongStart(key, it) }
        return at
    }

    private fun cancelVideoRequest() {
        videoRequest++
        videoJob?.cancel()
        videoJob = null
        if (_video.value.loading) _video.value = _video.value.copy(loading = false)
    }

    internal fun onVideoFailure(id: String) {
        if (_video.value.trackId == id) _video.value = VideoPlaybackState(trackId = id,
            error = "Video stopped loading. Switched back to audio.")
    }

    fun attachVideoSurface(view: TextureView) = withController { c ->
        if (c.isCommandAvailable(Player.COMMAND_SET_VIDEO_SURFACE)) c.setVideoTextureView(view)
    }

    fun detachVideoSurface(view: TextureView) = withController { c ->
        if (c.isCommandAvailable(Player.COMMAND_SET_VIDEO_SURFACE)) c.clearVideoTextureView(view)
    }

    /** Plays the track now and fills the queue with its radio. */
    fun startRadio(t: Track, context: String = "${t.title} Radio") {
        play(listOf(t), 0, context)
        val generation = playGeneration
        scope.launch {
            val more = runCatching { MusicRepository.related(t) }.getOrDefault(emptyList())
            withController { c ->
                if (generation == playGeneration && c.currentMediaItem?.mediaId == t.id) {
                    c.appendUnique(more.take(25))
                }
            }
        }
    }

    fun playNext(t: Track) = withController { c ->
        if (c.mediaItemCount == 0) { play(listOf(t)); return@withController }
        val next = c.currentMediaItemIndex + 1
        if (c.currentMediaItem?.toTrack()?.let { sameQueueSong(it, t) } == true) return@withController
        val existing = (next until c.mediaItemCount).firstOrNull {
            c.getMediaItemAt(it).toTrack()?.let { queued -> sameQueueSong(queued, t) } == true
        }
        if (existing != null) c.moveMediaItem(existing, next)
        else c.addMediaItem(next, t.toMediaItem())
    }

    fun addToQueue(tracks: List<Track>) = withController { c ->
        if (tracks.isEmpty()) return@withController
        if (c.mediaItemCount == 0) { play(tracks); return@withController }
        c.appendUnique(tracks)
    }

    fun toggle() = withController { c ->
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0)
            c.play()
        }
    }

    fun next() = withController { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    fun skipTo(index: Int) = withController { if (index in 0 until it.mediaItemCount) { it.seekTo(index, 0L); it.play() } }
    fun removeAt(index: Int) = withController { if (index in 0 until it.mediaItemCount) it.removeMediaItem(index) }
    fun move(from: Int, to: Int) = withController { if (from != to) it.moveMediaItem(from, to) }
    fun clearUpNext() = withController { c ->
        val i = c.currentMediaItemIndex
        if (i + 1 < c.mediaItemCount) c.removeMediaItems(i + 1, c.mediaItemCount)
    }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** minutes = 0 → stop at end of the current track. */
    fun sleepIn(minutes: Int) {
        sleepJob?.cancel()
        if (minutes == 0) {
            _sleepAt.value = Long.MAX_VALUE
            sleepJob = scope.launch {
                val id = _current.value?.id
                while (true) {
                    delay(500)
                    val c = controller ?: continue
                    val left = c.duration - c.currentPosition
                    if (c.currentMediaItem?.mediaId != id || (c.duration > 0 && left < 700)) { c.pause(); break }
                }
                _sleepAt.value = null
            }
            return
        }
        _sleepAt.value = System.currentTimeMillis() + minutes * 60_000L
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            controller?.pause()
            _sleepAt.value = null
        }
    }

    fun cancelSleep() { sleepJob?.cancel(); _sleepAt.value = null }

    fun currentMediaItem(): MediaItem? = controller?.currentMediaItem
}
