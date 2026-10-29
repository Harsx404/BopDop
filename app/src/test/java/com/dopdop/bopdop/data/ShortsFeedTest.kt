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
        nextSongs = { t -> val n = t.id.removePrefix("yt:s").toInt(); (n + 1..n + 8).map(::song) },
        taggedFor = { emptyList() }, lovedSongs = { emptyList() }, soundSong = { c -> if (c.title.startsWith("untagged")) null else ShortSong(Track("yt:tag-${c.id}", "Tagged ${c.id}", "A"), true) }, preferences = { ShortsHistoryState() })

    @Test fun opensOnTwoOfThePlayingSongsShorts() {
        val f = feed()
        assertEquals(2, f.items.size)
        assertTrue(f.items.all { it.song.id == "yt:s0" })
    }

    @Test fun thenOneOrTwoPerNextSongAndThePlayingSongDoesNotComeBack() = runBlocking {
        val f = feed()
        f.onPage(1)
        assertTrue(f.items.size >= 2 + ShortsFeed.AHEAD)
        val others = f.items.drop(2).groupBy { it.song.id }
        assertTrue(others.isNotEmpty() && others.values.all { it.size in 1..2 })
        // Other songs still have Shorts, so the playing song's leftovers stay out.
        assertTrue(f.items.drop(2).none { it.song.id == "yt:s0" })
    }

    @Test fun thePlayingSongsOtherShortsComeBackOnlyWhenNothingElseIsLeft() = runBlocking {
        val f = ShortsFeed(song(0), clips(0, 6), Random(3), shortsFor = { emptyList() }, nextSongs = { emptyList() },
            taggedFor = { emptyList() }, lovedSongs = { emptyList() }, soundSong = { c -> if (c.title.startsWith("untagged")) null else ShortSong(Track("yt:tag-${c.id}", "Tagged ${c.id}", "A"), true) }, preferences = { ShortsHistoryState() })
        f.onPage(1)
        assertTrue(f.items.size > 2)
        assertTrue(f.items.all { it.song.id == "yt:s0" })
    }

    @Test fun neverTheSameSongTwiceInARow() = runBlocking {
        val f = feed()
        repeat(20) { f.onPage(f.items.size - 1) }
        val songs = f.items.drop(2).map { f.song(it).id }
        assertTrue(songs.zipWithNext().count { (a, b) -> a == b } <= songs.size / 4)
    }

    @Test fun neverEndsAndNeverRepeatsAShort() = runBlocking {
        val f = feed()
        repeat(60) { f.onPage(f.items.size - 1) }
        assertTrue(f.items.size > 60)
        assertEquals(f.items.size, f.items.map { it.clip.id }.toSet().size)
    }

    @Test fun discoveryFollowsLearnedTopicsAndEveryShortCarriesAFullSong() = runBlocking {
        val requested = mutableListOf<List<String>>()
        val liked = FeedShort(clips(9).first().copy(title = "#girls #dance"), song(9))
        val f = ShortsFeed(song(0), clips(0), Random(4), shortsFor = { emptyList() }, nextSongs = { emptyList() },
            taggedFor = { tags -> requested += tags; (1..12).map { ShortClip("topic-${tags.first()}-$it", "#dance $it", "", 0, "") } },
            lovedSongs = { emptyList() }, soundSong = { c -> if (c.title.startsWith("untagged")) null else ShortSong(Track("yt:tag-${c.id}", "Tagged ${c.id}", "A"), true) }, preferences = { ShortsHistoryState(liked = listOf(LikedShort(liked, 1))) })
        f.onPage(1)
        assertTrue(requested.flatten().contains("dance"))
        assertTrue(requested.flatten().any { it in ShortsTaste.WOMEN_TAGS })
        assertTrue(f.items.size > 4)
        assertTrue(f.items.none { it.song.isShortAudio })
        assertTrue(f.items.take(2).all { it.song.id == song(0).id })
    }

    @Test fun fewOpeningClipsDoNotPreventTheFeedFromLoading() = runBlocking {
        val f = ShortsFeed(song(0), clips(0, 1), Random(4), shortsFor = { clips(1) }, nextSongs = { listOf(song(1)) },
            taggedFor = { emptyList() }, lovedSongs = { emptyList() }, soundSong = { c -> if (c.title.startsWith("untagged")) null else ShortSong(Track("yt:tag-${c.id}", "Tagged ${c.id}", "A"), true) }, preferences = { ShortsHistoryState() })
        f.onPage(0)
        assertTrue(f.items.size > 1)
    }

    @Test fun likingOrSkippingChangesUpcomingRankingWithoutMovingTheCurrentPage() = runBlocking {
        val dance = ShortClip("dance", "#dance", "", 0, "")
        val comedy = ShortClip("comedy", "#comedy", "", 0, "")
        var state = ShortsHistoryState(liked = listOf(LikedShort(FeedShort(comedy, song(0)), 1)))
        val f = ShortsFeed(song(0), clips(0, 3), Random(2), shortsFor = { emptyList() }, nextSongs = { emptyList() },
            taggedFor = { listOf(dance, comedy) }, lovedSongs = { emptyList() }, soundSong = { c -> if (c.title.startsWith("untagged")) null else ShortSong(Track("yt:tag-${c.id}", "Tagged ${c.id}", "A"), true) }, preferences = { state })
        f.onPage(1)
        val current = f.items[1]
        state = ShortsHistoryState(liked = listOf(LikedShort(FeedShort(dance, song(0)), 1)))
        f.onPage(1)
        assertEquals(current, f.items[1])
        assertEquals("dance", f.items[2].clip.id)
    }

    @Test fun savedClipsAreReopenedWithoutDuplicatePagerKeys() {
        val f = feed()
        val item = FeedShort(ShortClip("saved", "#dance", "", 0, ""), song(5))
        val index = f.openSaved(item)
        assertEquals(index, f.openSaved(item))
        assertEquals(item, f.items[index])
        assertEquals(f.items.size, f.items.map { it.clip.id }.toSet().size)
    }

    @Test fun failedTopicSearchFallsBackToSongs() = runBlocking {
        val f = ShortsFeed(song(0), clips(0, 3), Random(4), shortsFor = { clips(1) }, nextSongs = { listOf(song(1)) },
            taggedFor = { error("offline") }, lovedSongs = { emptyList() }, soundSong = { c -> if (c.title.startsWith("untagged")) null else ShortSong(Track("yt:tag-${c.id}", "Tagged ${c.id}", "A"), true) }, preferences = { ShortsHistoryState() })
        f.onPage(1)
        assertTrue(f.items.any { it.song.id == song(1).id })
    }

    @Test fun topicShortsWithoutARealSongAreLeftOutRatherThanMislabelled() = runBlocking {
        val f = ShortsFeed(song(0), clips(0, 2), Random(4), shortsFor = { emptyList() }, nextSongs = { emptyList() },
            taggedFor = { (1..6).map { ShortClip("untagged-$it", "untagged singing cover $it", "", 0, "") } + ShortClip("real", "#dance", "", 0, "") },
            lovedSongs = { emptyList() }, soundSong = { c -> if (c.title.startsWith("untagged")) null else ShortSong(Track("yt:real-song", "Real", "A"), true) },
            preferences = { ShortsHistoryState() })
        f.onPage(1)
        assertTrue(f.items.none { it.clip.id.startsWith("untagged") })
        assertTrue(f.items.filter { it.clip.id == "real" }.all { f.song(it).id == "yt:real-song" && !f.suggested(it) })
        assertTrue(f.items.drop(2).none { it.song.id == song(0).id })
    }
}
