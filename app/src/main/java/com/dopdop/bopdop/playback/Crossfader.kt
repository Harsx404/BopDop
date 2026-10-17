package com.dopdop.bopdop.playback

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.dopdop.bopdop.data.LibraryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Automix: overlaps the end of one song with the start of the next.
 *
 * The main player stays the single source of truth for the session and UI — at the fade point it
 * simply moves on to the next song. A second, silent "tail" player, primed a few seconds earlier
 * on the outgoing song, carries that song's last seconds while the two cross with an equal-power
 * curve. Albums that flow track-to-track, videos and repeat-one are left alone.
 */
@OptIn(UnstableApi::class)
internal class Crossfader(private val scope: CoroutineScope, private val newTail: () -> ExoPlayer) {
    private var main: ExoPlayer? = null
    private var tail: ExoPlayer? = null
    private var primedFor: String? = null
    private var fade: Job? = null
    private var ownSeek = false
    private val loop: Job = scope.launch {
        while (isActive) { delay(100); tick() }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying && main?.playbackState != Player.STATE_BUFFERING) finish()
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            // The user skipped or scrubbed mid-fade: drop the tail and play the new spot at full volume.
            if (reason == Player.DISCONTINUITY_REASON_SEEK && !ownSeek) finish()
            ownSeek = false
        }
    }

    fun attach(player: ExoPlayer) {
        main?.removeListener(listener)
        finish()
        main = player
        player.addListener(listener)
    }

    private fun tick() {
        val p = main ?: return
        val fadeMs = LibraryStore.settings.crossfadeSec * 1000L
        if (fadeMs <= 0 || fade != null || !p.isPlaying) return
        val item = p.currentMediaItem ?: return
        val dur = p.duration
        if (dur == C.TIME_UNSET || dur < fadeMs * 3 || item.videoId != null ||
            p.repeatMode == Player.REPEAT_MODE_ONE || !p.hasNextMediaItem()) return
        val next = p.getMediaItemAt(p.nextMediaItemIndex)
        if (next.videoId != null || flowsInto(item.toTrack()?.album, next.toTrack()?.album)) return

        val left = dur - p.currentPosition
        val key = "${p.currentMediaItemIndex}:${item.mediaId}"
        if (left in (fadeMs + 1)..(fadeMs + 5_000) && primedFor != key) {
            val t = tail ?: newTail().also { tail = it }
            t.volume = 0f
            t.playWhenReady = false
            t.setMediaItem(item, dur - fadeMs)
            t.prepare()
            primedFor = key
        }
        // Too little left to be worth a fade (the tail was slow to load): let the song end normally.
        if (left <= fadeMs + 150 && left >= 1_500 && primedFor == key && tail?.playbackState == Player.STATE_READY) {
            // Land on the exact fade point rather than the next 100 ms tick.
            fade = scope.launch {
                delay((left - fadeMs).coerceAtLeast(0))
                cross(p, tail!!, dur)
            }
        }
    }

    private suspend fun cross(p: ExoPlayer, t: ExoPlayer, dur: Long) {
        // Start the tail exactly where the song is now, so it never repeats what was just heard
        // (it was primed for the fade point, but may have become ready after it).
        val from = p.currentPosition
        val span = (dur - from).coerceAtLeast(1_000)
        android.util.Log.i("Automix", "crossing into the next song over $span ms")
        t.seekTo(from)
        t.volume = 1f
        t.play()
        p.volume = 0f
        ownSeek = true
        p.seekToNextMediaItem()
        // Hold the outgoing song at full volume until the next one is actually playing, so the
        // fade never runs into silence while the new song is still loading.
        val waitStart = SystemClock.uptimeMillis()
        while (!p.isPlaying && SystemClock.uptimeMillis() - waitStart < span / 2) delay(30)
        val fadeMs = (span - (SystemClock.uptimeMillis() - waitStart)).coerceAtLeast(800)
        val start = SystemClock.uptimeMillis()
        while (true) {
            val x = ((SystemClock.uptimeMillis() - start).toFloat() / fadeMs).coerceIn(0f, 1f)
            t.volume = cos(x * PI / 2).toFloat()
            p.volume = sin(x * PI / 2).toFloat()
            if (x >= 1f) break
            delay(30)
        }
        fade = null
        finish()
    }

    /** Stop any fade in progress and put the main player back at full volume. */
    fun finish() {
        fade?.cancel(); fade = null
        tail?.run { stop(); clearMediaItems() }
        primedFor = null
        main?.volume = 1f
    }

    fun release() {
        loop.cancel()
        finish()
        main?.removeListener(listener)
        tail?.release(); tail = null
    }

    /** Consecutive tracks of one album are often mixed to run into each other — keep them gapless. */
    private fun flowsInto(a: String?, b: String?) = !a.isNullOrBlank() && a == b
}
