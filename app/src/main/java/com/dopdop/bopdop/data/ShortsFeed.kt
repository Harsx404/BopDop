package com.dopdop.bopdop.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot
import com.dopdop.bopdop.data.model.Source
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.ytm.ShortClip
import com.dopdop.bopdop.data.ytm.YtShorts
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import kotlin.random.Random

/**
 * A Short in the feed, the song it came with, and which part of the feed found it ([source]:
 * seed, related, loved, topic or explore). [song] is always a full song; the Short's own sound,
 * once looked up, may name a better one (see [ShortsFeed.song]).
 */
data class FeedShort(val clip: ShortClip, val song: Track, val source: String = "") {
    companion object {
        fun fromJson(o: JSONObject): FeedShort = readFeedShort(o)
    }
}

/**
 * The Shorts feed. It opens on one or two Shorts of the song that's playing, then mixes four
 * sources, each Short labelled with a full song:
 * - related: Shorts of the songs the recommendations bring up next,
 * - loved: Shorts of songs from your own library,
 * - topic: Shorts about the topics you watch through, like and play the songs of,
 * - explore: dance, challenges and other discovery topics, so the feed never narrows to nothing.
 * Which source comes next is drawn from how each has done for you (see [ShortsTaste.sourceDraw]).
 * Nothing repeats: no Short twice, no song twice, never the same topic or song back to back, and
 * the playing song's other Shorts come back only when everything else has run dry.
 */
class ShortsFeed(
    private val seed: Track,
    first: List<ShortClip>,
    private val random: Random = Random.Default,
    private val shortsFor: suspend (Track) -> List<ShortClip> = YtShorts::forSong,
    private val nextSongs: suspend (Track) -> List<Track> = { MusicRepository.related(it) },
    private val taggedFor: suspend (List<String>) -> List<ShortClip> = YtShorts::forTags,
    private val lovedSongs: () -> List<Track> = { LibraryStore.state.value.let { (it.liked + it.history).distinctBy { t -> t.id } } },
    private val preferences: () -> ShortsHistoryState = { ShortsStore.state.value },
    /** The song a Short's own sound uses, if it's a real song (null, or not [ShortSong.tagged], when it isn't). */
    private val soundSong: suspend (ShortClip) -> ShortSong? = { ShortsSongs.of(it, null) },
) {
    val items = mutableStateListOf<FeedShort>()
    /** The full song each Short's own sound turned out to be (or a suggestion), looked up as they come on screen. */
    val songs = mutableStateMapOf<String, ShortSong>()

    private val opening = ShortsTaste(preferences()).let { t -> t.rank(first.filterNot { c -> t.hidden(c.id) }.ifEmpty { first }, random) }
    private val seedLeft = ArrayDeque(opening.drop(SEED_FIRST))
    private val upcoming = ArrayDeque<Track>()
    private val loved = ArrayDeque<Track>()
    private val songsUsed = hashSetOf(seed.id)
    private val clipsUsed = HashSet<String>()
    private val recentTopics = ArrayDeque<String>()
    private val later = ArrayDeque<FeedShort>()
    private var chainFrom = seed
    private val lock = Mutex()

    init { opening.take(SEED_FIRST).forEach { add(it, seed, SEED) } }

    /** The full song to show and play for [item]: its own sound's song when known, else the one it came with. */
    fun song(item: FeedShort): Track = songs[item.clip.id]?.track ?: item.song
    /** True when [song] is a suggestion, because the Short uses its own sound. */
    fun suggested(item: FeedShort): Boolean = songs[item.clip.id]?.tagged == false

    /** Look up the song [item] really uses (once each), so its label and "play" use the full track. */
    suspend fun resolve(item: FeedShort) {
        if (item.clip.id in songs) return
        ShortsSongs.of(item.clip, item.song)?.let { songs[item.clip.id] = it }
    }

    /**
     * The viewer has settled on [page]. Keep at least [AHEAD] more loaded beyond it. Feedback may
     * reorder what's still to come, never the current or already watched pages.
     */
    suspend fun onPage(page: Int) {
        lock.withLock {
            val start = (page + 1).coerceAtMost(items.size)
            reorder(start, ShortsTaste(preferences()))
            var attempts = 0
            while (items.size - 1 - page < AHEAD && attempts++ < MAX_TRIES) {
                val before = items.size
                val taste = ShortsTaste(preferences())
                val draw = taste.sourceDraw(SOURCES, random)
                // Best source first, falling back down the list when one comes back empty.
                for (src in SOURCES.sortedByDescending { draw[it] }) {
                    if (fill(src, taste)) break
                }
                // A song's second Short, held back from its first, goes in once something else has.
                later.firstOrNull { song(it).id != items.lastOrNull()?.let(::song)?.id }?.let { later.remove(it); items += it }
                if (items.size == before) {
                    // Only when nothing else has anything left: the playing song's other Shorts.
                    seedLeft.removeFirstOrNull()?.let { add(it, seed, SEED) } ?: break
                }
            }
        }
    }

    /** Reopening a saved Short preserves its soundtrack and never duplicates a pager key. */
    fun openSaved(item: FeedShort): Int {
        val existing = items.indexOfFirst { it.clip.id == item.clip.id }
        if (existing >= 0) return existing
        if (clipsUsed.add(item.clip.id)) items += item
        return items.lastIndex
    }

    /** One batch from [src]; false when it had nothing new. */
    private suspend fun fill(src: String, taste: ShortsTaste): Boolean = when (src) {
        RELATED -> nextSong()?.let { song -> addFrom(attempt { shortsFor(song) }, song, src, taste, 1 + random.nextInt(2)) } ?: false
        LOVED -> nextLoved()?.let { song -> addFrom(attempt { shortsFor(song) }, song, src, taste, 1) } ?: false
        TOPIC, EXPLORE -> {
            val tags = taste.queryTags(random, explore = src == EXPLORE, avoid = recentTopics.toSet())
            val found = attempt { taggedFor(tags) }
            val topicToRemember = tags.firstOrNull { it !in ShortsTaste.WOMEN_TAGS } ?: tags.first()
            addTagged(found, src, taste, 2).also { if (it) remember(topicToRemember) }
        }
        else -> false
    }

    /**
     * Topic and discovery Shorts weren't found through a song, so each must bring its own: the best
     * few are looked up together, and only those whose sound is a real song go in, labelled with it.
     * A cover or a clip with its own sound is never passed off as some other song.
     */
    private suspend fun addTagged(clips: List<ShortClip>, src: String, taste: ShortsTaste, n: Int): Boolean {
        val lastTopics = items.takeLast(2).mapNotNull { primaryTopic(it.clip) }.toSet()
        val lastSong = items.lastOrNull()?.let(::song)?.id
        val fresh = taste.rank(clips.filter { it.id !in clipsUsed && !taste.hidden(it.id) }, random).take(LOOKUPS)
        val withSongs = coroutineScope { fresh.map { c -> async { c to attemptOne { soundSong(c) } } }.awaitAll() }
            .mapNotNull { (c, s) -> s?.takeIf { it.tagged }?.let { c to it } }
            .filter { (_, s) -> s.track.id !in songsUsed || s.track.id == lastSong }
            .sortedBy { (c, s) -> (if (primaryTopic(c) in lastTopics) 1 else 0) + (if (s.track.id == lastSong) 2 else 0) }
            .distinctBy { it.second.track.id }
            .take(n)
        withSongs.forEach { (c, s) -> songs[c.id] = s; songsUsed += s.track.id; add(c, s.track, src) }
        return withSongs.isNotEmpty()
    }

    private suspend fun <T> attemptOne(block: suspend () -> T?): T? = try { block() }
        catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    /** The best [n] of [clips] not seen, not hidden and not more of what's just been shown. */
    private fun addFrom(clips: List<ShortClip>, song: Track, src: String, taste: ShortsTaste, n: Int): Boolean {
        val lastTopics = items.takeLast(2).mapNotNull { primaryTopic(it.clip) }.toSet()
        val fresh = clips.filter { it.id !in clipsUsed && !taste.hidden(it.id) }
        // Variety: a Short on the same topic as the last two goes to the back of the line.
        val picks = taste.rank(fresh, random).sortedBy { c -> if (primaryTopic(c) in lastTopics) 1 else 0 }.take(n)
        // Never two of one song back to back: the first goes in now, the rest after something else.
        picks.forEachIndexed { i, c ->
            if (i == 0 || src == TOPIC || src == EXPLORE) add(c, song, src)
            else if (clipsUsed.add(c.id)) later += FeedShort(c, song, src)
        }
        return picks.isNotEmpty()
    }

    /** Upcoming Shorts by score, then spread so neither a topic nor a song shows twice in a row. */
    private fun reorder(start: Int, taste: ShortsTaste) {
        if (start >= items.size) return
        val pool = items.drop(start).sortedByDescending { taste.score(it.clip) }.toMutableList()
        val out = ArrayList<FeedShort>(pool.size)
        var prev = items.getOrNull(start - 1)
        while (pool.isNotEmpty()) {
            val pick = pool.firstOrNull { prev == null || (primaryTopic(it.clip) != primaryTopic(prev!!.clip) || primaryTopic(it.clip) == null) && song(it).id != song(prev!!).id }
                ?: pool.first()
            pool.remove(pick); out += pick; prev = pick
        }
        Snapshot.withMutableSnapshot { out.forEachIndexed { i, item -> items[start + i] = item } }
    }

    private fun primaryTopic(c: ShortClip): String? {
        val usable = ShortsTaste.usable(ShortsTags.extract(c.title, c.tags))
        return usable.firstOrNull { it !in ShortsTaste.WOMEN_TAGS } ?: usable.firstOrNull()
    }

    private fun remember(topic: String) {
        recentTopics.addLast(topic)
        while (recentTopics.size > 3) recentTopics.removeFirst()
    }

    private suspend fun nextSong(): Track? {
        if (upcoming.isEmpty()) {
            val more = attempt { nextSongs(chainFrom) }.filter { playable(it) && it.id !in songsUsed }.distinctBy { it.id }.take(8)
            if (more.isEmpty()) return null
            upcoming += more.shuffled(random)
        }
        return upcoming.removeFirst().also { songsUsed += it.id; chainFrom = it }
    }

    private fun nextLoved(): Track? {
        if (loved.isEmpty()) loved += lovedSongs().filter { playable(it) && it.id !in songsUsed }.take(40).shuffled(random).take(12)
        return loved.removeFirstOrNull()?.also { songsUsed += it.id }
    }

    private fun playable(t: Track) = !t.isPodcast && !t.isShortAudio && t.source != Source.LOCAL

    private fun add(clip: ShortClip, song: Track, src: String) {
        if (clipsUsed.add(clip.id)) items += FeedShort(clip, song, src)
    }

    private suspend fun <T> attempt(block: suspend () -> List<T>): List<T> = try { block() }
        catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }

    companion object {
        /** Shorts of the playing song before others join in: one or two is plenty. */
        const val SEED_FIRST = 2
        /** Shorts kept loaded beyond the one on screen. */
        const val AHEAD = 6
        /** Batches tried in one go, so sources with nothing new can't hold it up forever. */
        private const val MAX_TRIES = 8
        /** Topic Shorts whose sound is looked up per batch, to find ones that use a real song. */
        private const val LOOKUPS = 8
        const val SEED = "seed"
        const val RELATED = "related"
        const val LOVED = "loved"
        const val TOPIC = "topic"
        const val EXPLORE = "explore"
        private val SOURCES = listOf(RELATED, TOPIC, LOVED, EXPLORE)
    }
}
