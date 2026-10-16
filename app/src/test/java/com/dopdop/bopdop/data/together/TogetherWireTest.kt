package com.dopdop.bopdop.data.together

import com.dopdop.bopdop.data.model.Track
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TogetherWireTest {
    @Test fun youtubeTracksRoundTrip() {
        val t = Track("yt:mbJxhvsgwO0", "Teri Aankhon Mein", "Darshan Raval", album = "Single", durationSec = 262, artwork = "https://i.ytimg.com/x.jpg")
        val back = Together.fromWire(Together.toWire(t))!!
        assertEquals(t.id, back.id); assertEquals(t.title, back.title); assertEquals(262, back.durationSec)
    }

    @Test fun saavnTracksKeepTheirCdnUrl() {
        val t = Track("sv:abc", "Song", "Artist", streamUrl = "https://aac.saavncdn.com/x_320.mp4")
        assertEquals("https://aac.saavncdn.com/x_320.mp4", Together.fromWire(Together.toWire(t))!!.streamUrl)
    }

    @Test fun refusesUntrustedPeers() {
        assertNull(Together.fromWire(JSONObject().put("title", "x").put("source", "ytm").put("videoId", "bad").put("id", "yt:bad")))
        assertNull(Together.fromWire(JSONObject().put("title", "x").put("source", "saavn").put("id", "1").put("url", "https://evil.example/a.mp4")))
    }
}
