package com.dopdop.bopdop.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.dopdop.bopdop.data.MusicRepository
import com.dopdop.bopdop.data.model.Track
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RepeatMode { OFF, ALL, ONE }

/** UI-side handle on the playback service: commands in, observable state out. */
object PlayerConnection {
    private val scope = MainScope()
    private var controller: MediaController? = null
    private val pending = mutableListOf<(MediaController) -> Unit>()

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
        _context.value = context
        withController { c ->
            c.shuffleModeEnabled = shuffle
            val first = if (shuffle) tracks.indices.random() else start.coerceIn(tracks.indices)
            c.setMediaItems(tracks.map { it.toMediaItem() }, first, 0L)
            c.prepare()
            c.play()
        }
    }

    fun playSingle(t: Track, context: String = "") = startRadio(t, context)

    /** Plays the track now and fills the queue with its radio. */
    fun startRadio(t: Track, context: String = "${t.title} Radio") {
        play(listOf(t), 0, context)
        scope.launch {
            val more = runCatching { MusicRepository.related(t) }.getOrDefault(emptyList()).take(25)
            withController { c ->
                if (c.currentMediaItem?.mediaId == t.id && more.isNotEmpty()) c.addMediaItems(more.map { it.toMediaItem() })
            }
        }
    }

    fun playNext(t: Track) = withController { c ->
        if (c.mediaItemCount == 0) { play(listOf(t)); return@withController }
        c.addMediaItem(c.currentMediaItemIndex + 1, t.toMediaItem())
    }

    fun addToQueue(tracks: List<Track>) = withController { c ->
        if (c.mediaItemCount == 0) { play(tracks); return@withController }
        c.addMediaItems(tracks.map { it.toMediaItem() })
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
