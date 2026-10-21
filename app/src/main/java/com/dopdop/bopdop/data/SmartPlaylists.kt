package com.dopdop.bopdop.data

import com.dopdop.bopdop.data.model.Source
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.model.SongSet
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.time.ZoneId

data class SmartPlaylist(val id: String, val title: String, val subtitle: String, val tracks: List<Track>) {
    val artworks: List<String> get() = tracks.map { it.artwork }.filter { it.isNotBlank() }.distinct().take(4)
}

/**
 * Playlists kept for you from your own listening and rebuilt whenever they're looked at, so
 * they're always current: your week, what's on repeat, favourites you've drifted from, and
 * late-night songs. Discover is the one that fetches: songs like your recent favourites that
 * you haven't heard here yet. Songs marked "Not for me" never appear.
 */
object SmartPlaylists {
    private const val DAY = 86_400_000L
    private const val SIZE = 40
    const val DISCOVER = "discover"

    fun build(s: StatsState, lib: LibraryState, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): List<SmartPlaylist> {
        fun ok(t: Track) = !lib.isDisliked(t)
        fun ranked(counts: Map<String, Int>, min: Int) =
            counts.filter { it.value >= min }.entries.sortedByDescending { it.value }.mapNotNull { s.tracks[it.key] }.filter(::ok).take(SIZE)

        val month = s.events.filter { it.at >= now - 30 * DAY }.groupingBy { it.id }.eachCount()
        val week = s.events.filter { it.at >= now - 7 * DAY }.groupingBy { it.id }.eachCount()
        val last = HashMap<String, Long>()
        s.events.forEach { last[it.id] = maxOf(last[it.id] ?: 0L, it.at) }
        val all = s.events.groupingBy { it.id }.eachCount()

        // Played a lot once, or liked, but not in the last month.
        val drifted = all.filter { it.value >= 3 && (last[it.key] ?: 0L) < now - 30 * DAY }.entries
            .sortedByDescending { it.value }.mapNotNull { s.tracks[it.key] }
        val quietLikes = lib.liked.filter { it.id !in month && it.source != Source.LOCAL }
        val forgotten = (drifted + quietLikes).distinctBy { it.id }.filter(::ok).take(SIZE)

        val night = s.events.filter { Instant.ofEpochMilli(it.at).atZone(zone).hour.let { h -> h >= 22 || h < 4 } }
            .groupingBy { it.id }.eachCount()

        val weekTracks = ranked(week, 1)
        val minutes = (0 until 7).sumOf { back ->
            s.daily[Instant.ofEpochMilli(now).atZone(zone).toLocalDate().minusDays(back.toLong()).toEpochDay()] ?: 0
        } / 60
        val topArtist = weekTracks.map { DailyMixes.primaryArtist(it) }.filter { it.isNotBlank() }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val weekSub = listOfNotNull("$minutes min this week", topArtist?.let { "mostly $it" }).joinToString(" · ")

        return listOfNotNull(
            SmartPlaylist("week", "Your week", weekSub, weekTracks).takeIf { it.tracks.size >= 3 },
            SmartPlaylist("repeat", "On repeat", "The songs you keep coming back to", ranked(month, 2)).takeIf { it.tracks.size >= 3 },
            SmartPlaylist("forgotten", "Forgotten favourites", "Loved once, not played lately", forgotten).takeIf { it.tracks.size >= 5 },
            SmartPlaylist("night", "Night owl", "What you play after 10 pm", ranked(night, 2)).takeIf { it.tracks.size >= 5 },
        )
    }

    /** Your most-played songs of the last month (or what you played last), as seeds for Discover. */
    internal fun seeds(s: StatsState, lib: LibraryState, now: Long = System.currentTimeMillis(), n: Int = 6): List<Track> {
        val month = s.events.filter { it.at >= now - 30 * DAY }.groupingBy { it.id }.eachCount()
        val top = month.entries.sortedByDescending { it.value }.mapNotNull { s.tracks[it.key] }
        return (top + lib.history).filter { it.source != Source.LOCAL && !lib.isDisliked(it) }.distinctBy { it.id }.take(n)
    }

    /** Takes from each list in turn, skipping songs already heard here (by id or title and artist). */
    internal fun interleaveNew(lists: List<List<Track>>, seen: SongSet, limit: Int = SIZE): List<Track> {
        val out = ArrayList<Track>()
        val its = lists.map { it.iterator() }
        while (out.size < limit && its.any { it.hasNext() }) {
            for (src in its) {
                if (out.size >= limit) break
                while (src.hasNext()) {
                    val t = src.next()
                    if (t.source == Source.LOCAL || t in seen) continue
                    seen.add(t)
                    out += t
                    break
                }
            }
        }
        return out
    }

    /** Songs like your recent favourites that you haven't played or liked. */
    suspend fun discover(s: StatsState, lib: LibraryState): SmartPlaylist = coroutineScope {
        val seeds = seeds(s, lib)
        val related = seeds.map { t -> async { runCatching { MusicRepository.related(t) }.getOrDefault(emptyList()) } }.awaitAll()
        val known = SongSet(s.tracks.values + lib.history + lib.liked)
        val artists = seeds.map { DailyMixes.primaryArtist(it) }.filter { it.isNotBlank() }.distinct().take(3)
        SmartPlaylist(
            DISCOVER, "Discover",
            if (artists.isEmpty()) "New to you" else "New to you, for fans of " + artists.joinToString(", "),
            interleaveNew(related, known),
        )
    }
}
