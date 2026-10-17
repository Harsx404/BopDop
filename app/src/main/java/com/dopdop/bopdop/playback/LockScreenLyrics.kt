package com.dopdop.bopdop.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.MusicRepository
import com.dopdop.bopdop.data.model.LyricLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Lock-screen lyrics: the line being sung replaces the artist in the media notification
 * (lock screen, shade, watch). Only metadata changes — the item keeps its stream, so
 * playback never re-buffers. The artist comes back when the setting is off or a line ends.
 */
@OptIn(UnstableApi::class)
internal class LockScreenLyrics(private val scope: CoroutineScope, private val player: () -> ExoPlayer) {
    private var current: String? = null
    private var fetchedFor: String? = null
    private var lines: List<LyricLine> = emptyList()
    private var shown: String? = null

    init {
        scope.launch {
            while (isActive) {
                delay(250)
                runCatching { tick() }
            }
        }
    }

    private fun tick() {
        val p = player()
        val item = p.currentMediaItem ?: return
        val track = item.toTrack() ?: return
        val on = LibraryStore.settings.lockScreenLyrics && item.videoId == null
        if (track.id != current) { current = track.id; lines = emptyList(); shown = null; fetchedFor = null }
        // Fetch once per song, whenever the setting is (or becomes) on.
        if (on && fetchedFor != track.id) {
            fetchedFor = track.id
            val id = track.id
            scope.launch {
                val synced = runCatching { MusicRepository.lyrics(track) }.getOrNull()?.synced.orEmpty()
                if (current == id) lines = synced
            }
        }
        val line = if (!on || !p.isPlaying) null
            else lines.lastOrNull { it.timeMs <= p.currentPosition + 200 }?.text?.trim()?.takeIf { it.isNotEmpty() && it != "♪" }
        if (line == shown) return
        shown = line
        val meta = item.mediaMetadata.buildUpon().setArtist(line ?: track.artist).build()
        p.replaceMediaItem(p.currentMediaItemIndex, item.buildUpon().setMediaMetadata(meta).build())
    }
}
