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

    @Test fun prefersOfficialThenNormalUploadsNeverLyricCards() {
        val omv = Track("yt:omv", "Chogada Full Video", "T-Series", durationSec = 260)
        val upload = Track("yt:ugc", "Chogada Video Song | Darshan Raval", "Some Channel", durationSec = 252)
        val lyric = Track("yt:lyr", "Chogada Lyrical | Darshan Raval", "Lyrics Hub", durationSec = 250)
        val types = mapOf("omv" to "MUSIC_VIDEO_TYPE_OMV", "ugc" to "MUSIC_VIDEO_TYPE_UGC", "lyr" to "MUSIC_VIDEO_TYPE_UGC")
        assertEquals("yt:omv", pickMusicVideo(song, listOf(lyric, upload, omv), types)?.id)
        assertEquals("yt:ugc", pickMusicVideo(song, listOf(lyric, upload), types)?.id)
        assertEquals(null, pickMusicVideo(song, listOf(lyric), types))
    }
}
