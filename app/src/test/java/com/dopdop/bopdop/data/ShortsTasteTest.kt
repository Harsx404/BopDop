package com.dopdop.bopdop.data

import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.ytm.ShortClip
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ShortsTasteTest {
    private val now = 1_800_000_000_000L
    private fun item(id: String, title: String, source: String = "") = FeedShort(ShortClip(id, title, "1M views", 1_000_000, "thumb"), Track("yt:song-$id", "Song", "Artist"), source)
    private fun watch(id: String, title: String, ms: Long, at: Long = now) = ShortWatch(item(id, title), at, ms, 10_000)

    @Test fun normalizesVisibleCategoriesAndIgnoresEngagementSpam() {
        val tags = ShortsTags.extract("Girls dancing! #GirlsDance #DanceChallenge #Viral #YouTubeShorts #fyp #Bollywood")
        assertTrue(tags.containsAll(setOf("girls", "dance", "bollywood")))
        assertFalse(tags.any { it in setOf("viral", "youtubeshorts", "fyp") })
        assertTrue(ShortsTags.extract("An ordinary afternoon").isEmpty())
    }

    @Test fun titleKeywordsDoNotMatchPartsOfUnrelatedWords() {
        assertFalse("girls" in ShortsTags.extract("Girlfriend giveaway"))
        assertFalse("dance" in ShortsTags.extract("Attendance is required"))
    }

    @Test fun longViewsAndLikesOutrankPopularityAlone() {
        val liked = item("liked", "#girls #dance")
        val state = ShortsHistoryState(watches = (1..5).map { watch("$it", "#girls #dance", 9_500) }, liked = listOf(LikedShort(liked, now)))
        val taste = ShortsTaste(state, now)
        val dance = item("new", "#dance #girls").clip.copy(viewCount = 10)
        val viral = item("other", "#comedy").clip.copy(viewCount = 1_000_000_000)
        assertTrue(taste.score(dance) > taste.score(viral))
    }

    @Test fun fastSwipesReduceAffinityAndRecentRepeatsAreDemoted() {
        val seen = item("seen", "#dance")
        val taste = ShortsTaste(ShortsHistoryState(watches = listOf(watch("seen", "#dance", 700))), now)
        assertTrue(taste.tagWeights.getValue("dance") < 0)
        assertTrue(taste.score(seen.clip) < taste.score(seen.clip.copy(id = "fresh")))
    }

    @Test fun bufferingAndVeryBriefPassesAreNeutral() {
        assertEquals(0.0, watch("a", "#dance", 0).signal(), .001)
        assertEquals(0.0, watch("a", "#dance", 100).signal(), .001)
        assertFalse(watch("a", "#dance", 100).quickSkip)
    }

    @Test fun tastesCanChangeAndExplorationDoesNotGetLockedToFavouriteTag() {
        val old = watch("old", "#dance", 9_500, now - 180L * 86_400_000)
        val recent = watch("new", "#comedy", 9_500)
        val taste = ShortsTaste(ShortsHistoryState(watches = listOf(old, recent)), now)
        assertTrue(taste.tagWeights.getValue("comedy") > taste.tagWeights.getValue("dance"))
        val choices = (1..50).flatMap { taste.queryTags(Random(it), explore = true) }.toSet()
        assertTrue(choices.size > 2)
    }

    @Test fun pauseBufferingAndClockGapsCannotInflateWatchTime() {
        val s = ShortWatchSession(item("a", "#dance"), now)
        s.sample(0, true, 10_000); s.sample(200, true, 10_000)
        s.sample(400, false, 10_000); s.sample(60_000, false, 10_000)
        s.sample(60_200, true, 10_000); s.sample(60_400, true, 10_000)
        assertEquals(400L, s.snapshot().watchedMs)
        val id = s.snapshot().eventId
        s.loopCompleted()
        assertEquals(id, s.snapshot().eventId)
        assertEquals(1, s.snapshot().completedLoops)
    }

    @Test fun savedHistorySurvivesJsonRoundTripWithExactAudioIdentity() {
        val w = watch("a", "#girls #dance", 8_000)
        val state = ShortsHistoryState(listOf(w), listOf(LikedShort(w.item, now)))
        val restored = ShortsHistoryState.fromJson(state.toJson())
        assertEquals(state.watches, restored.watches)
        assertEquals(state.liked, restored.liked)
        assertEquals(setOf("a"), restored.likedIds)
        // A Short is saved with its full song, never its clip's own audio.
        assertFalse(restored.liked.single().item.song.isShortAudio)
        assertEquals("yt:song-a", restored.liked.single().item.song.id)
    }

    @Test fun songMetadataSurvivesWithoutAnExpiringStreamUrl() {
        val item = FeedShort(ShortClip("a", "#dance", "", 0, ""), Track("yt:1", "Song", "Artist", streamUrl = "expired"))
        val restored = FeedShort.fromJson(item.toJson())
        assertEquals(item.song.id, restored.song.id)
        assertNull(restored.song.streamUrl)
        assertFalse(restored.song.isShortAudio)
    }

    @Test fun savedSaavnSongsKeepTheirRequiredCatalogueUrl() {
        val song = Track("sv:1", "Song", "Artist", streamUrl = "https://cdn.example/song_320.mp4")
        val item = FeedShort(ShortClip("a", "#dance", "", 0, ""), song)
        assertEquals(song, FeedShort.fromJson(item.toJson()).song)
    }

    @Test fun womenContentIsRankedBoostedAndSuggestedInShortsAlgorithm() {
        val state = ShortsHistoryState(watches = (1..6).map { watch("$it", "#girls #dance", 9_500) })
        val taste = ShortsTaste(state, now)
        assertTrue("girls" in taste.tagWeights)
        assertTrue("women" in taste.tagWeights)
        assertTrue("dance" in taste.tagWeights)
        assertFalse("boys" in taste.tagWeights)
        val asked = (1..40).flatMap { taste.queryTags(Random(it), explore = false) }
        assertTrue(asked.any { it in ShortsTaste.WOMEN_TAGS })
        val exploreQueries = (1..40).flatMap { taste.queryTags(Random(it), explore = true) }
        assertTrue(exploreQueries.any { it.contains("women") || it.contains("girls") || it.contains("female") })
        val womanClip = item("w", "#women #dance").clip
        val plainClip = item("p", "#dance").clip
        assertTrue(taste.score(womanClip) > taste.score(plainClip))
    }

    @Test fun notInterestedIsTheStrongestNegativeAndHidesTheShortAndItsTopic() {
        val no = ShortWatch(item("x", "#comedy"), now, 0, 0, dismissed = true)
        assertTrue(no.signal() < watch("y", "#comedy", 700).signal())
        val taste = ShortsTaste(ShortsHistoryState(watches = listOf(no)), now)
        assertTrue(taste.hidden("x"))
        assertTrue(taste.score(ShortClip("z", "#comedy", "", 0, "")) < taste.score(ShortClip("w", "#cooking", "", 0, "")))
    }

    @Test fun watchedAndSkippedShortsStayAwayForAWhileThenComeBack() {
        val skipped = watch("s", "#dance", 700, now - 1L * 86_400_000)
        val watched = watch("w", "#dance", 9_500, now - 2L * 86_400_000)
        val old = watch("o", "#dance", 9_500, now - 9L * 86_400_000)
        val taste = ShortsTaste(ShortsHistoryState(watches = listOf(skipped, watched, old)), now)
        assertTrue(taste.hidden("s")); assertTrue(taste.hidden("w")); assertFalse(taste.hidden("o")); assertFalse(taste.hidden("new"))
    }

    @Test fun sourcesThatHoldAttentionAreDrawnMoreOften() {
        val good = (1..12).map { ShortWatch(item("t$it", "#dance", "topic"), now, 9_500, 10_000) }
        val bad = (1..12).map { ShortWatch(item("e$it", "#dance", "explore"), now, 700, 10_000) }
        val taste = ShortsTaste(ShortsHistoryState(watches = good + bad), now)
        val wins = (1..200).count { val d = taste.sourceDraw(listOf("topic", "explore"), Random(it)); d.getValue("topic") > d.getValue("explore") }
        assertTrue(wins > 180)
    }

    @Test fun usingTheSongCountsMoreThanWatchingThrough() {
        assertTrue(ShortWatch(item("a", ""), now, 1_000, 10_000, acted = true).signal() > watch("b", "", 10_000).signal())
    }

    @Test fun womenContentGetsHighSuggestionPriorityEvenWithoutWatchHistory() {
        val freshTaste = ShortsTaste(ShortsHistoryState(), now)
        val womanClip = item("w", "#women #dance").clip.copy(viewCount = 1_000)
        val otherClip = item("o", "#dance").clip.copy(viewCount = 100_000)
        val ranked = freshTaste.rank(listOf(otherClip, womanClip), Random(1))
        assertEquals("w", ranked.first().id)
        val explore = (1..50).map { freshTaste.queryTags(Random(it), explore = true).first() }
        val womenExploreCount = explore.count { it.contains("women") || it.contains("girls") || it.contains("female") }
        assertTrue(womenExploreCount > 20)
    }
}
