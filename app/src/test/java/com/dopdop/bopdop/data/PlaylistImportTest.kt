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

    @Test fun keepsThePlaylistsOwnVersion() {
        val plain = ForeignSong("Kesariya", "Arijit Singh", 268)
        assertEquals(0, PlaylistImport.score(plain, Track("yt:l", "Kesariya (Lofi Flip)", "Arijit Singh", durationSec = 270)))
        assertTrue(PlaylistImport.score(plain, Track("yt:k", "Kesariya (From \"Brahmastra\")", "Arijit Singh, Pritam", durationSec = 268)) > 0)
        val slowed = ForeignSong("Kesariya - Slowed + Reverb", "Arijit Singh", 300)
        assertEquals(0, PlaylistImport.score(slowed, Track("yt:k", "Kesariya", "Arijit Singh", durationSec = 300)))
    }

    @Test fun matchesBilingualJapaneseTitlesAndOtherScriptArtists() {
        // "原題 - Romaji" on YouTube Music.
        assertTrue(PlaylistImport.score(ForeignSong("好きだから。", "Yuika", 298), Track("yt:a", "好きだから。 - Sukidakara", "Yuika", durationSec = 299)) > 0)
        // Artist in another script: an exact title within 3 s still counts…
        assertTrue(PlaylistImport.score(ForeignSong("YELLOW", "Yoh kamiyama", 178), Track("yt:b", "YELLOW", "神山 羊", durationSec = 179)) > 0)
        // …but not a looser one, and an artist match always ranks higher.
        assertEquals(0, PlaylistImport.score(ForeignSong("Summertime", "Maggie, Nyan", 154), Track("yt:c", "Summertime", "illijahbeats", durationSec = 102)))
        assertTrue(PlaylistImport.score(ForeignSong("YELLOW", "神山 羊", 178), Track("yt:b", "YELLOW", "神山 羊", durationSec = 179)) >
            PlaylistImport.score(ForeignSong("YELLOW", "Yoh kamiyama", 178), Track("yt:b", "YELLOW", "神山 羊", durationSec = 179)))
    }
}
