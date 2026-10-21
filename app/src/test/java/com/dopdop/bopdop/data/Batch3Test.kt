package com.dopdop.bopdop.data

import com.dopdop.bopdop.data.model.SongSet
import com.dopdop.bopdop.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class Batch3Test {
    private val day = 86_400_000L
    // Noon UTC on a fixed day, so "night" hours are predictable.
    private val now = 1_790_000_000_000L - (1_790_000_000_000L % day) + 12 * 3_600_000L
    private fun t(id: String, artist: String = "Artist $id", title: String = "Song $id") = Track("yt:$id", title, artist, durationSec = 200)

    private fun stats(vararg plays: Pair<Track, Long>): StatsState = StatsState(
        events = plays.map { (tr, at) -> PlayEvent(tr.id, at) }.sortedBy { it.at },
        tracks = plays.associate { (tr, _) -> tr.id to tr },
    )

    @Test fun notForMeMatchesTheSameRecordingFromAnySource() {
        val yt = Track("yt:1", "Kesariya (From \"Brahmastra\")", "Arijit Singh, Pritam")
        val sv = Track("sv:9", "Kesariya", "Pritam, Arijit Singh")
        val lib = LibraryState(disliked = listOf(yt))
        assertTrue(lib.isDisliked(yt))
        assertTrue(lib.isDisliked(sv))
        assertFalse(lib.isDisliked(t("2")))
    }

    @Test fun onRepeatAndYourWeek() {
        val a = t("a"); val b = t("b"); val c = t("c"); val old = t("old")
        val s = stats(
            a to now - 1 * day, a to now - 2 * day, a to now - 3 * day,
            b to now - 2 * day, b to now - 20 * day,
            c to now - 1 * day,
            old to now - 40 * day,
        )
        val lists = SmartPlaylists.build(s, LibraryState(), now, ZoneOffset.UTC).associateBy { it.id }
        assertEquals(listOf(a, b, c), lists.getValue("week").tracks)
        assertTrue(lists.getValue("week").subtitle.contains("mostly Artist a"))
        // Only two songs played twice: too few for On repeat.
        assertFalse("repeat" in lists)
    }

    @Test fun forgottenFavouritesAndDislikesLeftOut() {
        val songs = (1..6).map { t("f$it") }
        val plays = songs.flatMap { tr -> (1..3).map { tr to now - (40 + it) * day } }
        val s = stats(*plays.toTypedArray())
        val lib = LibraryState(disliked = listOf(songs[0]))
        val forgotten = SmartPlaylists.build(s, lib, now, ZoneOffset.UTC).first { it.id == "forgotten" }
        assertEquals(songs.drop(1).toSet(), forgotten.tracks.toSet())
    }

    @Test fun discoverTakesInTurnAndSkipsWhatYouKnow() {
        val known = SongSet(listOf(Track("yt:k", "Song k (From \"Film\")", "Pritam, KK")))
        val first = listOf(Track("sv:k2", "Song k", "KK, Pritam"), t("1"), t("2"))
        val second = listOf(t("3"), Track("sv:x", "Song 1", "Someone, Artist 1"), t("4"))
        val out = SmartPlaylists.interleaveNew(listOf(first, second), known)
        assertEquals(listOf("yt:1", "yt:3", "yt:2", "yt:4"), out.map { it.id })
    }
}
