package com.dopdop.bopdop.data

import com.dopdop.bopdop.data.addon.Addons
import com.dopdop.bopdop.data.spotify.SpotifyApi
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Batch5Test {
    @Test fun addonUrlsAreNormalisedAndMustBeSecure() {
        assertEquals("https://addon.example.org/x", Addons.normalize(" https://addon.example.org/x/manifest.json "))
        assertEquals("https://addon.example.org", Addons.normalize("https://addon.example.org/"))
        assertNull(Addons.normalize("http://addon.example.org"))
        assertEquals("http://192.168.1.4:4533", Addons.normalize("http://192.168.1.4:4533"))
        assertNull(Addons.normalize("not a url"))
    }

    @Test fun findsTheSearchRouteChunk() {
        val main = """a={4146:"xpui-routes-your-library-x",4406:"xpui-routes-search"}[e]||e)+"."+{4146:"11111111",4406:"0bd78592"}[e]+".js",u.miniCssF=e=>({4406:"a0a5ee13"})"""
        assertEquals("https://open.spotifycdn.com/cdn/build/web-player/xpui-routes-search.0bd78592.js", SpotifyApi.searchChunk(main))
    }

    @Test fun readsSpotifyLibraryPages() {
        val lib = JSONObject("""{"totalCount":2,"items":[
            {"item":{"_uri":"spotify:playlist:abc","data":{"__typename":"Playlist","name":"Road trip","ownerV2":{"data":{"name":"me"}},
              "images":{"items":[{"sources":[{"url":"s","width":60},{"url":"l","width":640}]}]}}}},
            {"item":{"_uri":"spotify:album:x","data":{"__typename":"Album","name":"Not a playlist"}}}]}""")
        val (page, raw) = SpotifyApi.parsePlaylists(lib)
        assertEquals(2, raw)
        assertEquals(listOf("abc"), page.map { it.id })
        assertEquals("l", page[0].cover)

        val content = JSONObject("""{"totalCount":1,"items":[{"itemV2":{"data":{"uri":"spotify:track:t1","name":"Espresso",
            "artists":{"items":[{"profile":{"name":"Sabrina Carpenter"}}]},"albumOfTrack":{"name":"Espresso","coverArt":{"sources":[{"url":"c","width":300}]}},
            "trackDuration":{"totalMilliseconds":175000}}}}]}""")
        val t = SpotifyApi.parsePlaylistTracks(content).single()
        assertEquals("Sabrina Carpenter", t.artist)
        assertEquals(175, t.durationSec)
    }
}
