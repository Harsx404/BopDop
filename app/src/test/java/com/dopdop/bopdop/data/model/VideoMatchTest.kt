package com.dopdop.bopdop.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoMatchTest {
    private val song = Track("sv:song", "Chogada (From \"Loveyatri\")", "Darshan Raval, Asees Kaur", durationSec = 250)

    @Test fun acceptsOfficialVideoWithAdditionalTitleCredits() {
        val video = Track("yt:video", "Chogada Full Video | Loveyatri | Darshan Raval", "T-Series", durationSec = 275)
        assertTrue(musicVideoMatchScore(song, video) > 0)
    }

    @Test fun rejectsUnrelatedSongOrDifferentArtist() {
        assertEquals(0, musicVideoMatchScore(song, Track("yt:other", "Another song", "Darshan Raval")))
        assertEquals(0, musicVideoMatchScore(song, Track("yt:other", "Chogada", "Someone else")))
    }

    @Test fun doesNotSwitchToACoverRemixOrLongCompilation() {
        for (suffix in listOf("Cover", "Remix", "Live", "Karaoke")) {
            assertEquals(0, musicVideoMatchScore(song, Track("yt:version", "Chogada $suffix", "Darshan Raval")))
        }
        assertEquals(0, musicVideoMatchScore(song, Track("yt:mix", "Chogada", "Darshan Raval", durationSec = 3600)))
    }

    @Test fun prefersTheRealVideoOverStaticUploadsAndLongerCuts() {
        val video = Track("yt:omv", "Chogada (Official Video)", "Darshan Raval", durationSec = 254)
        val audio = Track("yt:audio", "Chogada (Official Audio)", "Darshan Raval", durationSec = 250)
        val extended = Track("yt:long", "Chogada (Official Video)", "Darshan Raval", durationSec = 395)
        assertTrue(musicVideoMatchScore(song, video) > musicVideoMatchScore(song, audio))
        assertTrue(musicVideoMatchScore(song, video) > musicVideoMatchScore(song, extended))
    }

    @Test fun timingMapCarriesPositionBothWays() {
        // Video opens with a 30 s scene, then plays the song through.
        val map = listOf(VideoSegment(songStartMs = 0, videoStartMs = 30_000, durationMs = 250_000))
        assertEquals(90_000L, map.songToVideo(60_000))
        assertEquals(60_000L, map.videoToSong(90_000))
        assertEquals(0L, map.videoToSong(10_000))
        assertEquals(42_000L, emptyList<VideoSegment>().songToVideo(42_000))
    }
}
