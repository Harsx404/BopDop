package com.dopdop.bopdop.data.model

import java.util.Locale
import kotlin.math.abs

/** Conservative matching for catalogues that do not provide an explicit video counterpart. */
internal fun musicVideoMatchScore(song: Track, video: Track): Int {
    fun words(value: String) = value.lowercase(Locale.ROOT)
        .replace(Regex("\\(from\\b.*?\\)|\\[from\\b.*?]"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().split(Regex("\\s+"))
        .filter { it.isNotBlank() }.toSet()
    val ignored = setOf("official", "music", "video", "audio", "full", "hd", "4k", "lyrical", "lyrics", "feat", "ft")
    val title = words(song.title) - ignored
    val candidate = words(video.title)
    val credits = candidate + words(video.artist)
    val artist = words(song.artist.split(',', '&').first())
    if (title.isEmpty() || artist.isEmpty() || !candidate.containsAll(title) || !credits.containsAll(artist)) return 0
    val versions = setOf("cover", "remix", "live", "karaoke", "slowed", "sped", "instrumental")
    if ((candidate intersect versions) != (title intersect versions)) return 0
    if (song.durationSec > 0 && video.durationSec > 0 &&
        abs(song.durationSec - video.durationSec) > maxOf(180, song.durationSec / 2)) return 0
    // Static uploads ("Official Audio", lyric cards, visualizers) are not the picture we want.
    val static = setOf("audio", "lyric", "lyrics", "lyrical", "visualizer", "visualiser")
    val staticPenalty = if ((candidate intersect static).isNotEmpty() && (words(song.title) intersect static).isEmpty()) 40 else 0
    // The closer the cut runs to the song, the better the picture lines up with what you hear.
    val lengthPenalty = if (song.durationSec > 0 && video.durationSec > 0) (abs(song.durationSec - video.durationSec) / 4).coerceAtMost(40) else 10
    return (100 - (candidate - title - ignored - artist).size.coerceAtMost(30) - staticPenalty - lengthPenalty).coerceAtLeast(1)
}

/** One stretch where the song and its video play the same music, as YouTube Music maps them. */
data class VideoSegment(val songStartMs: Long, val videoStartMs: Long, val durationMs: Long)

data class MusicVideoMatch(val videoId: String, val segments: List<VideoSegment> = emptyList())

/** Where [ms] into the song is heard in the video; without a map both timelines are the same. */
internal fun List<VideoSegment>.songToVideo(ms: Long) = remap(ms, toVideo = true)
internal fun List<VideoSegment>.videoToSong(ms: Long) = remap(ms, toVideo = false)

private fun List<VideoSegment>.remap(ms: Long, toVideo: Boolean): Long {
    if (isEmpty()) return ms
    fun from(s: VideoSegment) = if (toVideo) s.songStartMs else s.videoStartMs
    fun to(s: VideoSegment) = if (toVideo) s.videoStartMs else s.songStartMs
    val seg = firstOrNull { ms >= from(it) && ms < from(it) + it.durationMs } ?: lastOrNull { from(it) <= ms } ?: first()
    return (to(seg) + (ms - from(seg)).coerceIn(0, seg.durationMs)).coerceAtLeast(0)
}
