package com.dopdop.bopdop.data.spotify

import android.util.Log
import com.dopdop.bopdop.data.model.ArtistPage
import com.dopdop.bopdop.data.model.Card
import com.dopdop.bopdop.data.model.CardKind
import com.dopdop.bopdop.data.model.Collection
import com.dopdop.bopdop.data.model.SearchResults
import com.dopdop.bopdop.data.model.Shelf
import com.dopdop.bopdop.data.model.ShelfStyle
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.net.Http
import com.dopdop.bopdop.data.net.TtlCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Spotify's catalogue to browse beside YouTube Music, for people signed in to Spotify: search,
 * albums, artists, playlists and their own Home shelves. Songs never play from Spotify (it lets no
 * other app play its audio): a Spotify song is a `sp:<id>` track that plays from its YouTube Music
 * copy (see StreamResolver), and from there JioSaavn or an add-on like any other.
 *
 * Ids: tracks `sp:<id>`; cards `sp:album:<id>`, `sp:playlist:<id>`, `sp:artist:<id>`.
 */
object SpotifyCatalog {
    private const val TAG = "SpotifyCatalog"
    private const val API = "https://api.spotify.com/v1"
    private val searches = TtlCache<String, SearchResults>(15 * 60_000L, 100)

    val available: Boolean get() = SpotifyAccount.signedIn

    // --- Web API ------------------------------------------------------------------------

    private suspend fun v1(path: String, params: Map<String, String> = emptyMap(), retried: Boolean = false): JSONObject = withContext(Dispatchers.IO) {
        val bearer = SpotifyToken.bearer() ?: throw IOException("Spotify sign-in expired")
        val url = "$API/$path".toHttpUrl().newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val req = Request.Builder().url(url).header("Authorization", "Bearer $bearer").header("Accept", "application/json")
            .header("User-Agent", SpotifyToken.UA).build()
        val (code, text) = Http.client.newCall(req).execute().use { it.code to it.body?.string().orEmpty() }
        when {
            code == 401 && !retried -> { SpotifyToken.invalidate(); return@withContext v1(path, params, retried = true) }
            code == 429 -> throw IOException("Spotify asked to slow down")
            code !in 200..299 -> throw IOException("Spotify $path: HTTP $code")
        }
        JSONObject(text)
    }

    // --- Search -------------------------------------------------------------------------

    suspend fun search(q: String): SearchResults = searches.getOrLoad(q.trim().lowercase()) {
        val o = v1("search", mapOf("q" to q, "type" to "track,artist,album,playlist", "limit" to "20", "market" to "from_token"))
        SearchResults(
            tracks = o.optJSONObject("tracks")?.optJSONArray("items").objects().mapNotNull(::track),
            artists = o.optJSONObject("artists")?.optJSONArray("items").objects().mapNotNull(::artistCard),
            albums = o.optJSONObject("albums")?.optJSONArray("items").objects().mapNotNull(::albumCard),
            playlists = o.optJSONObject("playlists")?.optJSONArray("items").objects().mapNotNull(::playlistCard),
        ).also { Log.i(TAG, "search “$q”: ${it.tracks.size} songs, ${it.albums.size} albums") }
    }

    // --- Albums, playlists, artists -----------------------------------------------------

    suspend fun album(id: String): Collection {
        val a = v1("albums/$id", mapOf("market" to "from_token"))
        val art = image(a.optJSONArray("images"))
        val artists = names(a.optJSONArray("artists"))
        val tracks = a.optJSONObject("tracks")?.optJSONArray("items").objects().mapNotNull { t ->
            // Album tracks come without their album: lend it the album's.
            track(t.put("album", JSONObject().put("id", id).put("name", a.optString("name")).put("images", a.optJSONArray("images"))))
        }
        return Collection(
            id = "sp:album:$id", kind = CardKind.ALBUM, title = a.optString("name"), subtitle = artists,
            artwork = art, tracks = tracks, year = a.optString("release_date").take(4),
        )
    }

    /** A playlist through the web player's own query (Spotify's own mixes included). */
    suspend fun playlist(id: String): Collection {
        val (meta, tracks) = SpotifyApi.playlist(id)
        return Collection(
            id = "sp:playlist:$id", kind = CardKind.PLAYLIST, title = meta.name,
            subtitle = meta.owner.ifBlank { "Spotify" }, artwork = meta.cover,
            tracks = tracks.map { it.toTrack() },
        )
    }

    suspend fun artist(id: String): ArtistPage = coroutineScope {
        val info = async { v1("artists/$id") }
        val top = async { runCatching { v1("artists/$id/top-tracks", mapOf("market" to "from_token")) }.getOrNull() }
        val albums = async { runCatching { v1("artists/$id/albums", mapOf("include_groups" to "album,single", "limit" to "30", "market" to "from_token")) }.getOrNull() }
        val a = info.await()
        val followers = a.optJSONObject("followers")?.optInt("total") ?: 0
        ArtistPage(
            id = "sp:artist:$id", name = a.optString("name"), artwork = image(a.optJSONArray("images")),
            subtitle = if (followers > 0) "%,d followers on Spotify".format(followers) else "Spotify",
            topSongs = top.await()?.optJSONArray("tracks").objects().mapNotNull(::track),
            albums = albums.await()?.optJSONArray("items").objects().mapNotNull(::albumCard).distinctBy { it.title.lowercase() },
            similar = emptyList(),
        )
    }

    // --- Home ---------------------------------------------------------------------------

    /**
     * Your own Spotify shelves for BopDop's Home: the personal ones ("Made For You", "Recently
     * played", your mixes, "Jump back in"); generic charts and podcasts stay out. Falls back to your
     * recently played songs when Spotify's home feed can't be read.
     */
    suspend fun homeShelves(): List<Shelf> {
        val shelves = runCatching { SpotifyApi.homeSections() }.onFailure { Log.w(TAG, "home: ${it.message}") }.getOrNull().orEmpty()
            .filter { (title, _) -> PERSONAL.any { title.contains(it, true) } }
            .mapNotNull { (title, cards) -> cards.takeIf { it.size >= 3 }?.let { Shelf(title, "From your Spotify", cards = it, style = ShelfStyle.CARDS) } }
            .take(4)
        if (shelves.isNotEmpty()) return shelves
        val recent = runCatching {
            v1("me/player/recently-played", mapOf("limit" to "30")).optJSONArray("items").objects()
                .mapNotNull { it.optJSONObject("track")?.let(::track) }.distinctBy { it.id }
        }.getOrNull().orEmpty()
        return if (recent.size >= 4) listOf(Shelf("Recently played", "From your Spotify", tracks = recent.take(20), style = ShelfStyle.TRACKS)) else emptyList()
    }

    private val PERSONAL = listOf("made for", "recently played", "your top", "mixes", "jump back", "your daily", "for you", "your favorite", "discover weekly", "release radar", "on repeat")

    // --- Parsing ------------------------------------------------------------------------

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun names(a: JSONArray?) = a.objects().mapNotNull { it.optString("name").ifBlank { null } }.joinToString(", ")

    /** The widest image (Spotify lists them widest first, but not always). */
    private fun image(a: JSONArray?) = a.objects().maxByOrNull { it.optInt("width") }?.optString("url").orEmpty()

    internal fun track(t: JSONObject): Track? {
        val id = t.optString("id").ifBlank { return null }
        val album = t.optJSONObject("album")
        val artists = t.optJSONArray("artists")
        return Track(
            id = "sp:$id", title = t.optString("name").ifBlank { return null }, artist = names(artists),
            artistId = artists.objects().firstOrNull()?.optString("id")?.ifBlank { null }?.let { "sp:artist:$it" },
            album = album?.optString("name").orEmpty(), albumId = album?.optString("id")?.ifBlank { null }?.let { "sp:album:$it" },
            durationSec = (t.optLong("duration_ms") / 1000).toInt(), artwork = image(album?.optJSONArray("images")),
            explicit = t.optBoolean("explicit"),
        )
    }

    private fun artistCard(a: JSONObject): Card? = Card(
        "sp:artist:" + a.optString("id").ifBlank { return null }, CardKind.ARTIST, a.optString("name").ifBlank { return null },
        "Artist", image(a.optJSONArray("images")),
    )

    private fun albumCard(a: JSONObject): Card? = Card(
        "sp:album:" + a.optString("id").ifBlank { return null }, CardKind.ALBUM, a.optString("name").ifBlank { return null },
        listOfNotNull(names(a.optJSONArray("artists")).ifBlank { null }, a.optString("release_date").take(4).ifBlank { null }).joinToString(" · "),
        image(a.optJSONArray("images")),
    )

    private fun playlistCard(p: JSONObject): Card? = Card(
        "sp:playlist:" + p.optString("id").ifBlank { return null }, CardKind.PLAYLIST, p.optString("name").ifBlank { return null },
        p.optJSONObject("owner")?.optString("display_name").orEmpty(), image(p.optJSONArray("images")),
    )
}

/** A Spotify song as a BopDop track: it plays from its YouTube Music copy. */
fun SpotifyTrack.toTrack() = Track(
    id = "sp:" + uri.substringAfterLast(':'), title = title, artist = artist, album = album, durationSec = durationSec, artwork = cover,
)
