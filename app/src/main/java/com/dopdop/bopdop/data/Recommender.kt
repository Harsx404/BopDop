package com.dopdop.bopdop.data

import com.dopdop.bopdop.data.model.SongVersion
import com.dopdop.bopdop.data.model.Track

/**
 * Turns YouTube Music's radio for a song into BopDop's up-next, the way Spotify's radio feels:
 *
 * - Out: songs marked "Not for me", the seed song in any version, and songs you heard recently
 *   (unless they're liked: favourites may come round again, just not right away).
 * - Spread: one artist doesn't take over; no artist twice in any three songs in a row, and at
 *   most three songs per artist in the first twenty.
 * - Familiar + new: every fifth song is one of your liked songs by an artist in this radio (or the
 *   seed's), so a long session stays yours while most of it is discovery.
 *
 * - Your taste: YouTube's order (it already ranks by how well each song follows the seed) is nudged
 *   by [Taste]: artists you play and like move up a few places, artists you keep skipping sink, and
 *   songs you've skipped twice lately are left out (liked ones excepted).
 */
object Recommender {
    private const val RECENT = 40
    private const val FAMILIAR_EVERY = 5

    fun primaryArtist(t: Track) = SongVersion.artists(t.artist).firstOrNull().orEmpty()

    fun rank(seed: Track, pool: List<Track>, liked: List<Track>, history: List<Track>, taste: Taste = Taste.NONE, disliked: (Track) -> Boolean): List<Track> {
        val recent = history.take(RECENT)
        val likedSet = liked.map { it.id }.toHashSet()
        fun heardRecently(t: Track) = t.id !in likedSet && recent.any { SongVersion.sameSong(it, t) }
        fun skippedOut(t: Track) = t.id !in likedSet && taste.skips(t) >= Taste.SKIPS_TO_DROP
        val fresh = pool.asSequence()
            .filter { !disliked(it) && !SongVersion.sameSong(it, seed) && !heardRecently(it) && !skippedOut(it) }
            .distinctBy { SongVersion.recordingKey(it.title) + "|" + primaryArtist(it) }
            .toList()
            .let(taste::reorder)

        // Familiar picks: liked songs by artists this radio is about.
        val artists = (fresh.map(::primaryArtist) + primaryArtist(seed)).filter { it.isNotEmpty() }.toSet()
        val familiar = liked.filter { l ->
            primaryArtist(l) in artists && !SongVersion.sameSong(l, seed) && recent.take(15).none { SongVersion.sameSong(it, l) } &&
                fresh.none { SongVersion.sameSong(it, l) } && !disliked(l) && taste.skips(l) < Taste.SKIPS_TO_DROP
        }.shuffled(kotlin.random.Random(seed.id.hashCode())).toMutableList()

        val out = ArrayList<Track>()
        val waiting = fresh.toMutableList()
        val perArtist = HashMap<String, Int>()
        fun fits(t: Track): Boolean {
            val a = primaryArtist(t)
            if (a.isEmpty()) return true
            if (out.takeLast(2).any { primaryArtist(it) == a }) return false
            return out.size >= 20 || (perArtist[a] ?: 0) < 3
        }
        fun notAdjacent(t: Track) = primaryArtist(t).let { a -> a.isEmpty() || out.takeLast(2).none { primaryArtist(it) == a } }
        var owed = false
        while (waiting.isNotEmpty() || familiar.isNotEmpty()) {
            if ((out.size + 1) % FAMILIAR_EVERY == 0 && familiar.isNotEmpty()) owed = true
            // A familiar song is due: in at this slot, or the first one after where it fits.
            val next = if (owed) familiar.firstOrNull(::fits)?.also { familiar.remove(it); owed = false } else null
            val chosen = next ?: waiting.firstOrNull(::fits)?.also { waiting.remove(it) }
                // Nothing fits every rule: at least don't repeat an artist within three.
                ?: waiting.firstOrNull(::notAdjacent)?.also { waiting.remove(it) }
                ?: waiting.firstOrNull()?.also { waiting.remove(it) }
                ?: familiar.removeAt(0)
            out += chosen
            primaryArtist(chosen).takeIf { it.isNotEmpty() }?.let { perArtist[it] = (perArtist[it] ?: 0) + 1 }
            if (waiting.isEmpty() && out.size >= pool.size) break
        }
        return out
    }
}

/**
 * What your own listening says, for nudging recommendations: how much you like each artist (plays
 * in the last three months, the last month counting double, plus likes, minus skips) and how often
 * you skipped each song lately. Built from on-phone history only.
 */
class Taste(private val artistScore: Map<String, Double>, private val songSkips: Map<String, Int>) {
    fun affinity(t: Track) = artistScore[Recommender.primaryArtist(t).lowercase()] ?: 0.0
    fun skips(t: Track) = songSkips[key(t)] ?: 0

    /**
     * YouTube's order, nudged: a liked or much-played artist moves up to about seven places, one
     * you skip far more than you play drops eight. A stable sort, so equal songs keep their order.
     */
    fun reorder(tracks: List<Track>): List<Track> {
        if (artistScore.isEmpty()) return tracks
        return tracks.withIndex().sortedBy { (i, t) ->
            val a = affinity(t)
            i - 2.5 * kotlin.math.ln(1.0 + a.coerceAtLeast(0.0)).coerceAtMost(3.0) + if (a <= -3.0) 8.0 else 0.0
        }.map { it.value }
    }

    companion object {
        const val SKIPS_TO_DROP = 2
        private const val DAY = 86_400_000L
        val NONE = Taste(emptyMap(), emptyMap())

        private fun key(t: Track) = SongVersion.recordingKey(t.title) + "|" + Recommender.primaryArtist(t).lowercase()

        fun from(stats: StatsState, liked: List<Track>, now: Long = System.currentTimeMillis()): Taste {
            val score = HashMap<String, Double>()
            fun add(t: Track?, w: Double) {
                val a = t?.let { Recommender.primaryArtist(it).lowercase() }?.takeIf { it.isNotEmpty() } ?: return
                score[a] = (score[a] ?: 0.0) + w
            }
            stats.events.forEach { e -> if (e.at >= now - 90 * DAY) add(stats.tracks[e.id], if (e.at >= now - 30 * DAY) 1.0 else 0.5) }
            liked.forEach { add(it, 3.0) }
            val songSkips = HashMap<String, Int>()
            stats.skips.forEach { e ->
                if (e.at < now - 60 * DAY) return@forEach
                val t = stats.tracks[e.id] ?: return@forEach
                add(t, -1.5)
                songSkips[key(t)] = (songSkips[key(t)] ?: 0) + 1
            }
            return Taste(score, songSkips)
        }
    }
}
