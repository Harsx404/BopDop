package com.dopdop.bopdop.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMusicTest {
    private fun file(folder: String, ms: Long = 200_000, mime: String = "audio/mpeg", music: Boolean = true) =
        LocalFile(1, "Song", "Artist", "Album", 1, ms, folder, mime, music)

    @Test fun keepsSongsInMusicFolders() {
        assertTrue(file("Music/Bollywood").looksLikeMusic())
        assertTrue(file("Download").looksLikeMusic())
    }

    @Test fun dropsClipsRecordingsAndSystemSounds() {
        assertFalse(file("Music", ms = 12_000).looksLikeMusic())
        assertFalse(file("Music", mime = "audio/x-wav").looksLikeMusic())
        assertFalse(file("Recordings/Call").looksLikeMusic())
        assertFalse(file("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes").looksLikeMusic())
        assertFalse(file("Notifications").looksLikeMusic())
        assertFalse(file("Music", music = false).looksLikeMusic())
    }
}
