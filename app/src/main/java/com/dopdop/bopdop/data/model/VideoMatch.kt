package com.dopdop.bopdop.data.model

import java.util.Locale
import kotlin.math.abs

/** Conservative matching for catalogues that do not provide an explicit video counterpart. */
internal fun musicVideoMatchScore(song: Track, video: Track, requireArtist: Boolean = true): Int {
    fun words(value: String) = value.lowercase(Locale.ROOT)
        .replace(Regex("\\(from\\b.*?\\)|\\[from\\b.*?]"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().split(Regex("\\s+"))
        .filter { it.isNotBlank() }.toSet()
    val ignored = setOf("official", "music", "video", "audio", "full", "hd", "4k", "lyrical", "lyrics", "feat", "ft")
    val title = words(song.title) - ignored
    val candidate = words(video.title)
    val credits = candidate + words(video.artist)
    val artist = words(song.artist.split(',', '&').first())
    if (title.isEmpty() || !candidate.containsAll(title) || (requireArtist && (artist.isEmpty() || !credits.containsAll(artist)))) return 0
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

data class MusicVideoMatch(val videoId: String, val segments: List<VideoSegment> = emptyList(), val durationSec: Int = 0)

/**
 * The video for [song] among search [candidates]: an official music video when there is one,
 * otherwise a normal upload of the actual video (a label's "video song", a fan's full video).
 * Lyric cards, audio-only uploads, visualizers and other versions never qualify.
 */
internal fun pickMusicVideo(song: Track, candidates: List<Track>, types: Map<String, String>): Track? {
    val static = Regex("\\b(lyric|lyrics|lyrical|audio|visuali[sz]er|8d|jukebox|ringtone|status|shorts)\\b", RegexOption.IGNORE_CASE)
    return candidates.mapNotNull { v ->
        val type = types[v.rawId]
        // Official videos are often posted by the label ("T-Series"), with the singer missing from the title.
        val base = musicVideoMatchScore(song, v, requireArtist = type != "MUSIC_VIDEO_TYPE_OMV")
        when {
            base <= 0 || type == "MUSIC_VIDEO_TYPE_ATV" || type == "MUSIC_VIDEO_TYPE_PODCAST_EPISODE" -> null
            type == "MUSIC_VIDEO_TYPE_OMV" -> v to base + 40
            static.containsMatchIn(v.title) -> null
            base < 50 -> null
            else -> v to base + if (Regex("\\b(official|video song|full video|music video)\\b", RegexOption.IGNORE_CASE).containsMatchIn(v.title)) 15 else 0
        }
    }.maxByOrNull { it.second }?.first
}
