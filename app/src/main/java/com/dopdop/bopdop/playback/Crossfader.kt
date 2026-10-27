package com.dopdop.bopdop.playback

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import android.util.Log
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
            // Paused or stopped: drop the mix. A brief load while jumping to the next song isn't a
            // pause (play stays wanted), and cutting the tail there was itself an audible gap.
            if (!isPlaying && main?.playWhenReady != true) finish()
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
        // Vocal isolation starts each player a few seconds late, so a fade would fade into silence.
        if (fadeMs <= 0 || fade != null || !p.isPlaying || VocalModel.active) return
        val item = p.currentMediaItem ?: return
        val dur = p.duration
        if (dur == C.TIME_UNSET || dur < fadeMs * 3 || item.videoId != null ||
            p.repeatMode == Player.REPEAT_MODE_ONE || !p.hasNextMediaItem()) return
        val next = p.getMediaItemAt(p.nextMediaItemIndex)
        if (next.videoId != null || flowsInto(item.toTrack()?.album, next.toTrack()?.album)) return

        val left = dur - p.currentPosition
        val key = "${p.currentMediaItemIndex}:${item.mediaId}"
        if (left in (fadeMs + LEAD_MS + 1)..(fadeMs + 8_000) && primedFor != key) {
            val t = tail ?: newTail().also { tail = it }
            t.volume = 0f
            t.playWhenReady = false
            // Primed a little before the fade point: it starts there, muted, in step with the song.
            t.setMediaItem(item, dur - fadeMs - LEAD_MS)
            t.prepare()
            primedFor = key
        }
        // Too little left to be worth a fade (the tail was slow to load): let the song end normally.
        if (left <= fadeMs + LEAD_MS + 150 && left >= 1_500 + LEAD_MS && primedFor == key && tail?.playbackState == Player.STATE_READY) {
            // Start exactly where the tail was primed: follow the song's own clock (it can stall to
            // load), not a timer.
            val startAt = dur - fadeMs - LEAD_MS
            fade = scope.launch {
                while (p.currentPosition < startAt - 10) delay(if (startAt - p.currentPosition > 250) 40 else 4)
                cross(p, tail!!, dur)
            }
        }
    }

    private suspend fun cross(p: ExoPlayer, t: ExoPlayer, dur: Long) {
        // The tail starts muted where it was primed, a moment before the fade point, so its audio
        // output is already running when it takes over. (Seeking it here would throw away its
        // buffer, and muting the song before the tail actually sounds left a small hole.)
        if (!startInStep(p, t)) {
            // The tail never got going: no mix this time, the song just ends and the next begins.
            android.util.Log.i("Automix", "tail not ready, skipping the mix")
            fade = null; finish(); return
        }
        val from = p.currentPosition
        val span = (dur - from).coerceAtLeast(1_000)
        android.util.Log.i("Automix", "crossing into the next song over $span ms (tail ${t.currentPosition - from} ms off)")
        // Both play the same audio here; swap which one is heard in the same instant.
        t.volume = 1f
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

    /**
     * Starts [t] muted and waits until it plays in step with [p] (same song, same moment), so one
     * can be swapped for the other without a gap or a repeat. False if it never lines up.
     */
    private suspend fun startInStep(p: ExoPlayer, t: ExoPlayer): Boolean {
        fun off() = kotlin.math.abs(t.currentPosition - p.currentPosition)
        if (off() > 400) t.seekTo(p.currentPosition)
        t.volume = 0f
        t.play()
        val startWait = SystemClock.uptimeMillis()
        var resynced = false
        while (SystemClock.uptimeMillis() - startWait < LEAD_MS * 4) {
            if (t.isPlaying && off() <= IN_STEP_MS) break
            // Running but out of step (it started late or early): one re-seek, ahead by its start-up time.
            if (t.isPlaying && !resynced && SystemClock.uptimeMillis() - startWait > 150) {
                t.seekTo(p.currentPosition + 120); resynced = true
            }
            delay(10)
        }
        return t.isPlaying && off() <= IN_STEP_MS * 2
    }

    /**
     * Moves the playing song onto [better] (the same recording from a lossless add-on, found after
     * the song started) without a gap:
     *
     * 1. the tail loads the add-on copy a few seconds ahead of the song, muted;
     * 2. when the song reaches that point the tail starts in step and becomes the one heard;
     * 3. the main player switches to the add-on copy too (reading what the tail already fetched),
     *    lines up with the tail, and takes over again.
     *
     * Gives up quietly (the song carries on as it was) if the copy is slow to load or the song is
     * nearly over; the next song then simply starts on the add-on copy.
     */
    suspend fun upgrade(better: MediaItem): Boolean {
        val id = better.mediaId
        fun note(msg: String) = StreamResolver.note(id, msg)
        val p = main ?: return false
        if (fade != null) { note("Found, but Automix is mixing: next time"); return false }
        // The hand-over is lined up to the millisecond; vocal isolation's few seconds of lead-in can't be.
        if (VocalModel.active) { note("Found, but vocal isolation is on: next time"); return false }
        if (!p.isPlaying || p.currentMediaItem?.mediaId != id) return false
        if (p.duration != C.TIME_UNSET && p.duration - p.currentPosition < 25_000) { note("Found, too near the end to switch"); return false }
        val t = tail ?: newTail().also { tail = it }
        fade = kotlin.coroutines.coroutineContext[Job] // holds Automix off meanwhile
        fun stillOn() = p.currentMediaItem?.mediaId == id && p.playWhenReady
        try {
            note("Switching…")
            // Load the copy a little ahead of the song. A lossless file can be slow to start, so if it
            // isn't ready before the song gets there, try again further ahead: by then most of what it
            // needs has already been fetched.
            var ready = false
            for (warm in WARM_STEPS_MS) {
                if (p.duration != C.TIME_UNSET && p.duration - p.currentPosition < warm + 15_000) break
                val startAt = p.currentPosition + warm
                t.volume = 0f
                t.playWhenReady = false
                t.setMediaItem(better, startAt)
                t.prepare()
                val loadStart = SystemClock.uptimeMillis()
                while (t.playbackState != Player.STATE_READY && t.playerError == null && SystemClock.uptimeMillis() - loadStart < 25_000) {
                    if (!stillOn()) return false
                    delay(50)
                }
                if (t.playerError != null) { note("The add-on's file wouldn't play: ${t.playerError?.errorCodeName}"); return false }
                if (t.playbackState != Player.STATE_READY) continue
                if (p.currentPosition > startAt - 150) {
                    Log.i("Automix", "add-on copy of $id took ${SystemClock.uptimeMillis() - loadStart} ms to load; again further ahead")
                    continue
                }
                while (p.currentPosition < startAt - 10) {
                    if (!stillOn()) return false
                    delay(if (startAt - p.currentPosition > 250) 40 else 4)
                }
                ready = true
                break
            }
            if (!ready) { note("Found, but its file was too slow to start"); return false }
            if (!startInStep(p, t)) { note("Found, but couldn't line it up"); return false }
            t.volume = 1f
            p.volume = 0f
            Log.i("Automix", "add-on copy of ${better.mediaId} is playing; moving the song onto it")

            note("Switched")
            val idx = p.currentMediaItemIndex
            val original = p.currentMediaItem
            p.replaceMediaItem(idx, better)
            var lead = 250L
            ownSeek = true
            p.seekTo(idx, t.currentPosition + lead)
            repeat(6) {
                val w = SystemClock.uptimeMillis()
                while (!p.isPlaying && SystemClock.uptimeMillis() - w < 8_000) delay(10)
                if (!p.isPlaying) {
                    // It didn't start: put the song back on its usual stream where the tail has got to,
                    // rather than hand back to a player that isn't making a sound.
                    note("Switched back: the add-on copy was slow to start")
                    original?.let { o -> ownSeek = true; p.replaceMediaItem(idx, o); p.seekTo(idx, t.currentPosition) }
                    return false
                }
                delay(80) // let its clock settle after starting
                val d = p.currentPosition - t.currentPosition
                if (kotlin.math.abs(d) <= IN_STEP_MS) return true
                // It landed d ms off: aim that much the other way next time.
                lead = (lead - d).coerceIn(30, 1_500)
                ownSeek = true
                p.seekTo(idx, t.currentPosition + lead)
            }
            return true
        } finally {
            p.volume = 1f
            t.stop()
            t.clearMediaItems()
            primedFor = null
            fade = null
        }
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

    private companion object {
        /** How long before the fade point the muted tail starts, so its output is warm. */
        const val LEAD_MS = 400L
        /** How far ahead of the song the tail loads an add-on copy; each miss tries further ahead. */
        val WARM_STEPS_MS = longArrayOf(6_000L, 12_000L, 20_000L)
        /** Close enough that swapping from the song to the tail isn't heard. */
        const val IN_STEP_MS = 80L
    }

    /** Consecutive tracks of one album are often mixed to run into each other — keep them gapless. */
    private fun flowsInto(a: String?, b: String?) = !a.isNullOrBlank() && a == b
}
