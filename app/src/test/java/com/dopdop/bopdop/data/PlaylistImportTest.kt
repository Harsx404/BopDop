package com.dopdop.bopdop.data

import com.dopdop.bopdop.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistImportTest {
    private val song = ForeignSong("expectations (feat. Chappell Roan)", "Olivia Rodrigo, Chappell Roan", 224)

    @Test fun matchesTheSameSongAcrossCatalogues() {
        assertTrue(PlaylistImport.score(song, Track("yt:a", "expectations", "Olivia Rodrigo & Chappell Roan", durationSec = 225)) > 0)
    }

    @Test fun rejectsOtherSongsArtistsAndCuts() {
        assertEquals(0, PlaylistImport.score(song, Track("yt:b", "drivers license", "Olivia Rodrigo", durationSec = 242)))
        assertEquals(0, PlaylistImport.score(song, Track("yt:c", "expectations", "Someone Else", durationSec = 224)))
        assertEquals(0, PlaylistImport.score(song, Track("yt:d", "expectations", "Olivia Rodrigo", durationSec = 400)))
    }
}
