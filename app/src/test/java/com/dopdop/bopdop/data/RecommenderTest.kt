package com.dopdop.bopdop.data

import com.dopdop.bopdop.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommenderTest {
    private fun t(id: String, title: String, artist: String) = Track("yt:$id", title, artist, durationSec = 200)

    private val seed = t("s", "Kesariya", "Arijit Singh")

    @Test fun dropsSeedVersionsDislikedAndRecentlyHeard() {
        val pool = listOf(
            t("1", "Kesariya (Lofi Flip)", "Arijit Singh"), t("2", "Tum Hi Ho", "Arijit Singh"),
            t("3", "Apna Bana Le", "Arijit Singh"), t("4", "Bad Song", "Someone"), t("5", "Raataan Lambiyan", "Jubin Nautiyal"),
        )
        val out = Recommender.rank(seed, pool, liked = emptyList(), history = listOf(t("h", "Tum Hi Ho", "Arijit Singh")), disliked = { it.title == "Bad Song" })
        val titles = out.map { it.title }
        assertFalse("Kesariya (Lofi Flip)" in titles)
        assertFalse("Tum Hi Ho" in titles)
        assertFalse("Bad Song" in titles)
        assertTrue("Apna Bana Le" in titles && "Raataan Lambiyan" in titles)
    }

    @Test fun noArtistTwiceInThreeAndAtMostThreeInTwenty() {
        val pool = (1..8).map { t("a$it", "A song $it", "Artist A") } + (1..8).map { t("b$it", "B song $it", "Artist B") } +
            (1..8).map { t("c$it", "C song $it", "Artist C") } + (1..8).map { t("d$it", "D song $it", "Artist D") } +
            (1..8).map { t("e$it", "E song $it", "Artist E") } + (1..8).map { t("f$it", "F song $it", "Artist F") } +
            (1..8).map { t("g$it", "G song $it", "Artist G") } + (1..8).map { t("h$it", "H song $it", "Artist H") }
        val out = Recommender.rank(seed, pool, emptyList(), emptyList()) { false }
        out.take(20).windowed(3).forEach { w -> assertEquals(3, w.map(Recommender::primaryArtist).toSet().size) }
        assertTrue(out.take(20).groupBy(Recommender::primaryArtist).values.all { it.size <= 3 })
    }

    @Test fun weavesInLikedSongsByTheseArtists() {
        val pool = (1..12).map { t("p$it", "New $it", listOf("Arijit Singh", "Pritam", "Shreya Ghoshal", "KK")[it % 4]) }
        val liked = listOf(t("l1", "Channa Mereya", "Arijit Singh"), t("l2", "Unrelated Fav", "Taylor Swift"))
        val out = Recommender.rank(seed, pool, liked, emptyList()) { false }
        // Due at the fifth slot; goes in there or at the first slot after where Arijit isn't just playing.
        assertTrue(out.indexOfFirst { it.title == "Channa Mereya" } in 4..6)
        assertFalse(out.any { it.title == "Unrelated Fav" })
    }
}
