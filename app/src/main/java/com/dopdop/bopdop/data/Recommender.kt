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
 * YouTube's own order is kept otherwise: it already ranks by how well each song follows the seed.
 */
object Recommender {
    private const val RECENT = 40
    private const val FAMILIAR_EVERY = 5

    fun primaryArtist(t: Track) = SongVersion.artists(t.artist).firstOrNull().orEmpty()

    fun rank(seed: Track, pool: List<Track>, liked: List<Track>, history: List<Track>, disliked: (Track) -> Boolean): List<Track> {
        val recent = history.take(RECENT)
        val likedSet = liked.map { it.id }.toHashSet()
        fun heardRecently(t: Track) = t.id !in likedSet && recent.any { SongVersion.sameSong(it, t) }
        val fresh = pool.asSequence()
            .filter { !disliked(it) && !SongVersion.sameSong(it, seed) && !heardRecently(it) }
            .distinctBy { SongVersion.recordingKey(it.title) + "|" + primaryArtist(it) }
            .toList()

        // Familiar picks: liked songs by artists this radio is about.
        val artists = (fresh.map(::primaryArtist) + primaryArtist(seed)).filter { it.isNotEmpty() }.toSet()
        val familiar = liked.filter { l ->
            primaryArtist(l) in artists && !SongVersion.sameSong(l, seed) && recent.take(15).none { SongVersion.sameSong(it, l) } &&
                fresh.none { SongVersion.sameSong(it, l) } && !disliked(l)
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
