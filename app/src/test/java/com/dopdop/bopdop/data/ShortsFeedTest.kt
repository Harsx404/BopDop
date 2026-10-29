package com.dopdop.bopdop.data

import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.ytm.ShortClip
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShortsFeedTest {
    private fun song(n: Int) = Track("yt:s$n", "Song $n", "Artist $n", durationSec = 200)
    private fun clips(n: Int, count: Int = 8) = (1..count).map { ShortClip("c$n-$it", "Short $it of song $n", "1M views", 1_000_000L, "") }

    /** Each song's radio is the next eight songs along, so the feed can always go on. */
    private fun feed(seed: Track = song(0)) = ShortsFeed(seed, clips(0, 10), Random(42),
        shortsFor = { t -> clips(t.id.removePrefix("yt:s").toInt()) },
        nextSongs = { t -> val n = t.id.removePrefix("yt:s").toInt(); (n + 1..n + 8).map(::song) })

    @Test fun opensOnThreeOfThePlayingSongsShorts() {
        val f = feed()
        assertEquals(3, f.items.size)
        assertTrue(f.items.all { it.song.id == "yt:s0" })
    }

    @Test fun afterThreeItKeepsGoingWithOneOrTwoPerNextSong() = runBlocking {
        val f = feed()
        f.onPage(2)
        assertTrue(f.items.size >= 3 + ShortsFeed.AHEAD)
        val others = f.items.drop(3).filter { it.song.id != "yt:s0" }.groupBy { it.song.id }
        assertTrue(others.isNotEmpty() && others.values.all { it.size in 1..2 })
        // The rest of the first song's Shorts are dropped in along the way.
        assertTrue(f.items.drop(3).any { it.song.id == "yt:s0" })
    }

    @Test fun neverEndsAndNeverRepeatsAShort() = runBlocking {
        val f = feed()
        repeat(60) { f.onPage(f.items.size - 1) }
        assertTrue(f.items.size > 60)
        assertEquals(f.items.size, f.items.map { it.clip.id }.toSet().size)
    }
}
