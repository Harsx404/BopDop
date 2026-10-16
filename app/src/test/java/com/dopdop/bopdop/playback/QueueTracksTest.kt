package com.dopdop.bopdop.playback

import com.dopdop.bopdop.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueTracksTest {
    private fun track(id: String, title: String = "Song", artist: String = "Artist", seconds: Int = 180) =
        Track(id = id, title = title, artist = artist, durationSec = seconds)

    @Test fun removesRepeatsFromNewQueueAndKeepsFirstCopy() {
        val original = track("yt:first")
        val second = track("yt:second", "Another song")

        assertEquals(listOf(original, second), uniqueQueueTracks(listOf(original, original, second)))
    }

    @Test fun ignoresSongsAlreadyAddedWhileRelatedTracksWereLoading() {
        val seed = track("yt:seed", "Seed")
        val alreadyQueued = track("yt:queued", "Queued")
        val newSong = track("yt:new", "New")

        assertEquals(
            listOf(newSong),
            uniqueQueueTracks(listOf(alreadyQueued, newSong, newSong), listOf(seed, alreadyQueued)),
        )
    }

    @Test fun recognisesCopiesAcrossCataloguesButKeepsDifferentRecordings() {
        val youtube = track("yt:song")
        val saavn = track("sv:song", seconds = 184)
        val differentRecording = track("sv:long-version", seconds = 230)

        assertTrue(sameQueueSong(youtube, saavn))
        assertFalse(sameQueueSong(youtube, differentRecording))
        assertEquals(listOf(youtube, differentRecording), uniqueQueueTracks(listOf(youtube, saavn, differentRecording)))
    }
}
