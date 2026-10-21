package com.dopdop.bopdop.data

import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.scrobble.ListenBrainz
import com.dopdop.bopdop.ui.screens.settingMatches
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Batch2Test {
    private val a = Track("yt:a", "Zebra", "Bob", durationSec = 200)
    private val b = Track("yt:b", "apple", "Alice", durationSec = 300)
    private val c = Track("sv:c", "Mango", "alice", album = "Fruit", durationSec = 100)

    @Test fun playlistSorts() {
        val list = listOf(a, b, c)
        assertEquals(list, PlaylistSort.CUSTOM.apply(list))
        assertEquals(listOf(c, b, a), PlaylistSort.RECENT.apply(list))
        assertEquals(listOf(b, c, a), PlaylistSort.TITLE.apply(list))
        assertEquals(listOf(b, c, a), PlaylistSort.ARTIST.apply(list))
        assertEquals(listOf(b, a, c), PlaylistSort.LONGEST.apply(list))
    }

    @Test fun pinnedPlaylistsComeFirstInTheirOrder() {
        val p = listOf(
            UserPlaylist("1", "One", 0, emptyList()),
            UserPlaylist("2", "Two", 0, emptyList(), pinned = true),
            UserPlaylist("3", "Three", 0, emptyList()),
            UserPlaylist("4", "Four", 0, emptyList(), pinned = true),
        )
        assertEquals(listOf("2", "4", "1", "3"), p.pinnedFirst().map { it.id })
    }

    @Test fun settingsRememberShuffleRepeatAndPlaylistTools() {
        val s = Settings(shuffle = true, repeat = 2, listenBrainzToken = "tok", listenBrainzUser = "me")
        assertEquals(s, Settings.fromJson(JSONObject(s.toJson().toString())))
        assertEquals(2, Settings.fromJson(JSONObject().put("repeat", 9)).repeat)
        assertFalse(Settings.fromJson(JSONObject()).shuffle)
    }

    @Test fun listenCountsAtHalfOrFourMinutes() {
        assertEquals(100, ListenBrainz.needed(200))
        assertEquals(240, ListenBrainz.needed(900))
        assertEquals(240, ListenBrainz.needed(0))
        assertEquals(1, ListenBrainz.needed(1))
    }

    @Test fun listenMetadata() {
        val m = ListenBrainz.metadata(a)
        assertEquals("Bob", m.getString("artist_name"))
        assertEquals("Zebra", m.getString("track_name"))
        assertFalse(m.has("release_name"))
        val info = m.getJSONObject("additional_info")
        assertEquals(200_000L, info.getLong("duration_ms"))
        assertEquals("https://music.youtube.com/watch?v=a", info.getString("origin_url"))
        assertEquals("Fruit", ListenBrainz.metadata(c).getString("release_name"))
    }

    @Test fun settingsSearchMatchesEveryWord() {
        assertTrue(settingMatches("", "anything"))
        assertTrue(settingMatches("audio wifi", "Sound", "Audio quality on Wi-Fi", "High plays"))
        assertTrue(settingMatches("  DARK ", "Look & feel", "Theme", "", "dark light mode"))
        assertFalse(settingMatches("audio lyrics", "Sound", "Audio quality on Wi-Fi"))
    }
}
