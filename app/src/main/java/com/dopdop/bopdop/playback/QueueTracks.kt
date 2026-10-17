package com.dopdop.bopdop.playback

import androidx.media3.common.Player
import com.dopdop.bopdop.data.model.Track
import kotlin.math.abs

/**
 * Treat catalogue copies of the same recording as one queue entry: "Lutt Le Gaya (From
 * "Dhurandhar")" by Shashwat Sachdev, Simran Choudhary and "Lutt Le Gaya" by Simran Choudhary are
 * one song. Same core title, at least one artist in common, lengths within 25 s. A remix, lofi,
 * slowed, live or cover version stays a different song.
 */
internal fun sameQueueSong(a: Track, b: Track): Boolean {
    if (a.id == b.id) return true
    if (titleKey(a.title) != titleKey(b.title)) return false
    if ((artists(a.artist) intersect artists(b.artist)).isEmpty()) return false
    return a.durationSec <= 0 || b.durationSec <= 0 || abs(a.durationSec - b.durationSec) <= 25
}

private val VERSION = Regex("\\b(remix|mix|lofi|lo-fi|slowed|reverb|sped|nightcore|unplugged|acoustic|live|cover|mashup|reprise|instrumental|karaoke|8d|version)\\b", RegexOption.IGNORE_CASE)

/** Title without credits ("From …", "feat. …"), but keeping what kind of version it is. */
internal fun titleKey(title: String): String {
    val versions = VERSION.findAll(title).map { it.value.lowercase() }.toSortedSet().joinToString(" ")
    val core = title.lowercase()
        .replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
        .replace(Regex("\\s[-|–]\\s.*$"), " ")
        .replace(Regex("\\b(feat|ft|with)\\b.*$"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    return if (versions.isEmpty()) core else "$core|$versions"
}

private fun artists(s: String): Set<String> = s.lowercase()
    .split(Regex(",|&|\\band\\b|\\bx\\b|\\bfeat\\.?|\\bft\\.?|/"))
    .map { it.replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim() }
    .filter { it.isNotEmpty() }.toSet()

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
