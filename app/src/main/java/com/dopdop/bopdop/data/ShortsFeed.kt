package com.dopdop.bopdop.data

import androidx.compose.runtime.mutableStateListOf
import com.dopdop.bopdop.data.model.Source
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.ytm.ShortClip
import com.dopdop.bopdop.data.ytm.YtShorts
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/** A Short in the feed, and the song it uses. */
data class FeedShort(val clip: ShortClip, val song: Track)

/**
 * The endless Shorts feed, like Reels: it opens on the playing song's own Shorts, and once you've
 * swiped through a few it keeps going with one or two Shorts (picked at random from the most viewed)
 * for each song the recommendations bring up next, in a shuffled order, with the rest of the first
 * song's Shorts dropped in along the way. When those songs run out, the songs that follow the last
 * one carry it on, so the feed never ends while there's music to find.
 */
class ShortsFeed(
    private val seed: Track,
    first: List<ShortClip>,
    private val random: Random = Random.Default,
    private val shortsFor: suspend (Track) -> List<ShortClip> = YtShorts::forSong,
    private val nextSongs: suspend (Track) -> List<Track> = { MusicRepository.related(it) },
) {
    val items = mutableStateListOf<FeedShort>()

    private val seedLeft = ArrayDeque(first.drop(SEED_FIRST))
    private val upcoming = ArrayDeque<Track>()
    private val songsUsed = hashSetOf(seed.id)
    private val clipsUsed = HashSet<String>()
    private var chainFrom = seed
    private val lock = Mutex()

    init { first.take(SEED_FIRST).forEach { add(it, seed) } }

    /**
     * The viewer has settled on [page]. From the third Short on, keep at least [AHEAD] more loaded
     * beyond it, a song at a time.
     */
    suspend fun onPage(page: Int) {
        if (page < SEED_FIRST - 1) return
        lock.withLock {
            var songs = 0
            while (items.size - 1 - page < AHEAD && songs++ < MAX_SONGS_PER_LOAD) if (!addSong()) break
        }
    }

    /** One more song's Shorts (one or two of its top five, at random) and, while any are left, one more of the first song's. */
    private suspend fun addSong(): Boolean {
        val song = nextSong() ?: return false
        val clips = runCatching { shortsFor(song) }.getOrDefault(emptyList()).filter { it.id !in clipsUsed }
        clips.take(5).shuffled(random).take(1 + random.nextInt(2)).forEach { add(it, song) }
        seedLeft.removeFirstOrNull()?.let { add(it, seed) }
        return true
    }

    private suspend fun nextSong(): Track? {
        if (upcoming.isEmpty()) {
            val more = runCatching { nextSongs(chainFrom) }.getOrDefault(emptyList())
                .filter { it.id !in songsUsed && !it.isPodcast && it.source != Source.LOCAL }
                .take(8)
            if (more.isEmpty()) return null
            upcoming += more.shuffled(random)
        }
        return upcoming.removeFirst().also { songsUsed += it.id; chainFrom = it }
    }

    private fun add(clip: ShortClip, song: Track) {
        if (clipsUsed.add(clip.id)) items += FeedShort(clip, song)
    }

    companion object {
        /** Shorts of the playing song before others join in. */
        const val SEED_FIRST = 3
        /** Shorts kept loaded beyond the one on screen. */
        const val AHEAD = 4
        /** Songs tried in one go, so a run of songs with no Shorts can't hold it up forever. */
        private const val MAX_SONGS_PER_LOAD = 6
    }
}
