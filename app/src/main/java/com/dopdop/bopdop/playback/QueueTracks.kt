package com.dopdop.bopdop.playback

import androidx.media3.common.Player
import com.dopdop.bopdop.data.model.SongVersion
import com.dopdop.bopdop.data.model.Track

/**
 * Treat catalogue copies of the same recording as one queue entry: "Lutt Le Gaya (From
 * "Dhurandhar")" by Shashwat Sachdev, Simran Choudhary and "Lutt Le Gaya" by Simran Choudhary are
 * one song. A remix, lofi, slowed, vocals or live version stays a different song (see SongVersion).
 */
internal fun sameQueueSong(a: Track, b: Track): Boolean = SongVersion.sameRecording(a, b)

internal fun uniqueQueueTracks(tracks: List<Track>, existing: List<Track> = emptyList(), anyVersion: Boolean = false): List<Track> {
    val seen = existing.toMutableList()
    val same: (Track, Track) -> Boolean = if (anyVersion) SongVersion::sameSong else ::sameQueueSong
    return tracks.filter { track ->
        if (seen.any { same(it, track) }) false else {
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

/**
 * Radio and autoplay: one version of a song is enough. Anything that is another take of a song
 * already in the queue or among the last [RECENT] played ("Alfaaz (Lofi)", "Alfaaz - Vocals") is
 * skipped. Songs you pick yourself are never filtered this way.
 */
internal fun Player.appendRadio(tracks: List<Track>, limit: Int = Int.MAX_VALUE): Int {
    val recent = com.dopdop.bopdop.data.LibraryStore.state.value.history.take(RECENT)
    val additions = uniqueQueueTracks(tracks, queueTracks() + recent, anyVersion = true).take(limit)
    if (additions.isNotEmpty()) addMediaItems(additions.map { it.toMediaItem() })
    return additions.size
}

private const val RECENT = 25
