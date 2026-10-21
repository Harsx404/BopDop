package com.dopdop.bopdop.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongVersionTest {
    private fun tags(title: String) = SongVersion.parse(title).tags
    private fun core(title: String) = SongVersion.parse(title).core
    private fun t(title: String, artist: String = "Hamza Malik", sec: Int = 185) = Track("yt:$title|$artist|$sec", title, artist, durationSec = sec)

    @Test fun readsVersionsWhereverTheyAreWritten() {
        assertEquals(setOf("vocals"), tags("Alfaaz - Vocals"))
        assertEquals(setOf("lofi"), tags("Kesariya (Lofi Flip)"))
        assertEquals(setOf("slowed", "reverb"), tags("Tum Hi Ho (Slowed + Reverb)"))
        assertEquals(setOf("slowed", "reverb"), tags("Tum Hi Ho [Slowed & Reverb Mix]"))
        assertEquals(setOf("unplugged"), tags("Naina | Unplugged"))
        assertEquals(setOf("lofi"), tags("Kesariya lofi"))
        assertEquals(setOf("slowed", "reverb"), tags("Tum Hi Ho slowed reverb"))
        assertEquals(setOf("female"), tags("Alfaaz (Female Version)"))
        assertEquals(setOf("male"), tags("Alfaaz - Male Version"))
        assertEquals(setOf("sped"), tags("Husn (Sped Up)"))
        assertEquals(setOf("remix"), tags("Kala Chashma (Club Mix)"))
        assertEquals(setOf("live"), tags("Yellow - Live at Wembley"))
        assertEquals(setOf("acoustic"), tags("Pal Pal (Acoustic)"))
        assertEquals(setOf("instrumental"), tags("Raabta [Instrumental]"))
        assertEquals("alfaaz", core("Alfaaz - Vocals"))
        assertEquals("kesariya", core("Kesariya lofi"))
    }

    @Test fun creditsAndPackagingAreNotVersions() {
        assertEquals(SongTitle("lutt le gaya", emptySet()), SongVersion.parse("Lutt Le Gaya (From \"Dhurandhar\")"))
        assertEquals(emptySet<String>(), tags("Song (From \"Live Love Laugh\")"))
        assertEquals(emptySet<String>(), tags("Naina (feat. Badshah)"))
        assertEquals(emptySet<String>(), tags("Here Comes the Sun - Remastered 2009"))
        assertEquals(emptySet<String>(), tags("Song - Radio Edit"))
        assertEquals(emptySet<String>(), tags("Alfaaz | Hamza Malik | Official Video"))
        assertEquals(emptySet<String>(), tags("Tum Hi Ho - Aashiqui 2"))
        assertEquals("tum hi ho", core("Tum Hi Ho - Aashiqui 2"))
        // A song whose own name has a version word in it keeps it.
        assertEquals("live your life", core("Live Your Life"))
        assertEquals(emptySet<String>(), tags("Live Your Life"))
        assertEquals("lofi", core("Lofi"))
    }

    @Test fun sameRecordingNeedsTheSameVersion() {
        val song = t("Alfaaz", "Hamza Malik, Zain Zohaib")
        assertTrue(SongVersion.sameRecording(song, t("Alfaaz", "Zain Zohaib, Hamza Malik", 188)))
        assertFalse(SongVersion.sameRecording(song, t("Alfaaz - Vocals", "Hamza Malik", 185)))
        assertFalse(SongVersion.sameRecording(song, t("Alfaaz (Lofi)", "Hamza Malik", 190)))
        assertFalse(SongVersion.sameRecording(song, t("Alfaaz", "Abhishek Menon")))
        assertTrue(SongVersion.sameRecording(t("Alfaaz (Lofi)"), t("Alfaaz - Lofi")))
    }

    @Test fun sameSongIgnoresTheVersion() {
        val song = t("Alfaaz", "Hamza Malik, Zain Zohaib")
        assertTrue(SongVersion.sameSong(song, t("Alfaaz - Vocals", "Hamza Malik")))
        assertTrue(SongVersion.sameSong(song, t("Alfaaz (Slowed + Reverb)", "Hamza Malik", 240)))
        assertFalse(SongVersion.sameSong(song, t("Alfaaz", "Abhishek Menon")))
    }
}
