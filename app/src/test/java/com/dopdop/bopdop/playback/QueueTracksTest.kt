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

    @Test fun collapsesFilmAndSingleCopiesOfOneSong() {
        val film = track("yt:a", "Lutt Le Gaya (From \"Dhurandhar\")", "Shashwat Sachdev, Simran Choudhary", 253)
        val single = track("yt:b", "Lutt Le Gaya", "Simran Choudhary", 248)
        assertTrue(sameQueueSong(film, single))
        val crew = track("sv:c", "Naina (From \"Crew\")", "Diljit Dosanjh, Badshah, Raj Ranjodh", 202)
        val feat = track("yt:d", "Naina (feat. Badshah)", "Diljit Dosanjh", 219)
        assertTrue(sameQueueSong(crew, feat))
    }

    @Test fun keepsVersionsAndOtherArtistsApart() {
        val song = track("yt:a", "Naina", "Diljit Dosanjh", 200)
        assertFalse(sameQueueSong(song, track("yt:b", "Naina (Slowed + Reverb)", "Diljit Dosanjh", 205)))
        assertFalse(sameQueueSong(song, track("yt:c", "Naina", "Arijit Singh", 200)))
        assertFalse(sameQueueSong(song, track("yt:d", "Naina - Lofi Mix", "Diljit Dosanjh", 200)))
    }

    @Test fun radioTakesOneVersionOfEachSong() {
        val playing = track("yt:a", "Alfaaz", "Hamza Malik, Zain Zohaib", 185)
        val related = listOf(
            track("sv:b", "Alfaaz - Vocals", "Hamza Malik", 185),
            track("yt:c", "Alfaaz (Slowed + Reverb)", "Hamza Malik", 230),
            track("yt:d", "Faasle", "Aditya Rikhari", 200),
            track("yt:e", "Faasle (Lofi)", "Aditya Rikhari", 210),
            track("yt:f", "Alfaaz", "Abhishek Menon", 190),
        )
        assertEquals(listOf("yt:d", "yt:f"), uniqueQueueTracks(related, listOf(playing), anyVersion = true).map { it.id })
        // Picking songs yourself keeps every version you chose.
        assertEquals(5, uniqueQueueTracks(related, listOf(playing)).size)
    }
}
