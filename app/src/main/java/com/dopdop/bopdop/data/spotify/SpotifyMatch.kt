package com.dopdop.bopdop.data.spotify

import android.content.Context
import com.dopdop.bopdop.data.ForeignSong
import com.dopdop.bopdop.data.PlaylistImport
import com.dopdop.bopdop.data.model.Track
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Spotify tracks play from YouTube Music: each is matched once (same song, same version, an artist
 * in common, within 15 s — see PlaylistImport.score). Matches are kept in a file, so automatic
 * syncs only search songs that are new; misses are tried again next time. The reverse map lets the
 * player find a song's Canvas without searching Spotify again.
 */
object SpotifyMatch {
    private val byUri = ConcurrentHashMap<String, Track>()
    private val missing = ConcurrentHashMap.newKeySet<String>()
    private val uriOf = ConcurrentHashMap<String, String>()
    private var file: File? = null

    fun init(context: Context) {
        if (file != null) return
        val f = File(context.filesDir, "spotify_matches.json").also { file = it }
        runCatching {
            val o = JSONObject(f.readText())
            o.keys().forEach { uri -> runCatching { Track.fromJson(o.getJSONObject(uri)) }.getOrNull()?.let { byUri[uri] = it; uriOf[it.id] = uri } }
        }
    }

    fun cached(uri: String): Track? = byUri[uri]
    fun failed(uri: String) = uri in missing

    /** The Spotify track a playing song came from, if it was matched from Spotify. */
    fun spotifyUri(trackId: String): String? = uriOf[trackId]

    suspend fun match(s: SpotifyTrack): Track? {
        byUri[s.uri]?.let { return it }
        if (s.uri in missing) return null
        val t = runCatching { PlaylistImport.match(ForeignSong(s.title, s.artist, s.durationSec)) }.getOrNull()
        if (t == null) missing += s.uri else { byUri[s.uri] = t; uriOf[t.id] = s.uri }
        android.util.Log.i("SpotifyMatch", "“${s.title}” → ${t?.let { "${it.id} “${it.title}”" } ?: "no match"}")
        return t
    }

    /** Write the matches out (after an import); misses aren't kept, so they're retried. */
    fun save() {
        val f = file ?: return
        runCatching {
            val o = JSONObject()
            byUri.forEach { (uri, t) -> o.put(uri, t.copy(streamUrl = null).toJson()) }
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(f)
        }
    }

    fun clear() { byUri.clear(); missing.clear(); uriOf.clear(); file?.delete() }
}
