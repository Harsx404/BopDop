package com.dopdop.bopdop.playback

import androidx.media3.common.Player
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.model.matchKey
import kotlin.math.abs

/** Treat catalogue copies of the same recording as one queue entry. */
internal fun sameQueueSong(a: Track, b: Track): Boolean =
    a.id == b.id || (a.matchKey() == b.matchKey() &&
        (a.durationSec <= 0 || b.durationSec <= 0 || abs(a.durationSec - b.durationSec) <= 10))

internal fun uniqueQueueTracks(tracks: List<Track>, existing: List<Track> = emptyList()): List<Track> {
    val seen = existing.toMutableList()
    return tracks.filter { track ->
        if (seen.any { sameQueueSong(it, track) }) false else {
            seen += track
            true
        }
    }
}

internal fun Player.queueTracks(): List<Track> =
    (0 until mediaItemCount).mapNotNull { getMediaItemAt(it).toTrack() }

/** Check the live timeline immediately before appending; radio requests can finish out of order. */
internal fun Player.appendUnique(tracks: List<Track>, limit: Int = Int.MAX_VALUE): Int {
    val additions = uniqueQueueTracks(tracks, queueTracks()).take(limit)
    if (additions.isNotEmpty()) addMediaItems(additions.map { it.toMediaItem() })
    return additions.size
}
