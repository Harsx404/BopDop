package com.dopdop.bopdop.data.spotify

import android.util.Log
import com.dopdop.bopdop.data.net.Http
import com.dopdop.bopdop.data.net.TtlCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class SpotifyPlaylist(val id: String, val name: String, val owner: String, val cover: String, val count: Int = 0)
data class SpotifyTrack(val uri: String, val title: String, val artist: String, val album: String, val durationSec: Int, val cover: String)

/**
 * Your Spotify library through Pathfinder, the GraphQL endpoint the web player itself uses.
 *
 * Operations are persisted queries named by a hash the web player declares in its scripts
 * (`"libraryV3","query","<sha256>"`). Spotify changes them when it rebuilds the player, so they're
 * read live from open.spotify.com's bundles (and the search route's chunk), with the last known
 * ones as a fallback, and read again once when Spotify answers "PersistedQueryNotFound".
 */
object SpotifyApi {
    private const val TAG = "SpotifyApi"
    private const val GQL = "https://api-partner.spotify.com/pathfinder/v2/query"
    const val LIKED = "liked"
    private const val LIKED_COVER = "https://misc.scdn.co/liked-songs/liked-songs-640.png"

    // --- Persisted query hashes -------------------------------------------------------

    /** Last known good (2026-10-21); live values replace these. */
    private val KNOWN = mapOf(
        "libraryV3" to "390c78e5b951029bad359785e69b07b536a509c581cbcd0aded5e5067f187455",
        "fetchPlaylist" to "21ea11a6ac75a5f3e01fd365d29c18e0a4a8b7dc09f89bbb5d73910190c6acaf",
        "fetchLibraryTracks" to "087278b20b743578a6262c2b0b4bcd20d879c503cc359a2285baf083ef944240",
        "profileAttributes" to "08ffb4730af3746e04a8301396f20875dbbce10c75243803091a9274eacc8ac0",
        "canvas" to "575138ab27cd5c1b3e54da54d0a7cc8d85485402de26340c2145f0f6bb5e7a9f",
        "searchTracks" to "b02683192a98dde7966b5e6655a79eeb62713eab703eda9902c932818dd52751",
    )
    private val hashLock = Mutex()
    @Volatile private var live: Map<String, String>? = null
    private val OP = Regex("""["'](\w+)["']\s*,\s*["']query["']\s*,\s*["']([0-9a-f]{64})["']""")
    private val BUNDLE = Regex("""https://open\.spotifycdn\.com/cdn/build/web-player/[A-Za-z0-9._~-]+\.js""")

    private suspend fun hash(op: String, refresh: Boolean = false): String = hashLock.withLock {
        if (live == null || refresh) live = runCatching { discover() }.onFailure { Log.w(TAG, "hashes: ${it.message}") }.getOrNull()
            ?.also { Log.i(TAG, "hashes read live: ${it.keys.filter { k -> k in KNOWN }.sorted()}") }
        live?.get(op) ?: KNOWN.getValue(op)
    }

    /** Every `"op","query","hash"` in the web player's scripts, plus the search route's chunk. */
    internal fun discover(): Map<String, String> {
        val html = get(SpotifyAccount.ORIGIN + "/")
        val scripts = BUNDLE.findAll(html).map { it.value }.distinct().toList()
        val found = HashMap<String, String>()
        var main = ""
        for (s in scripts) {
            val js = runCatching { get(s) }.getOrDefault("")
            if ("web-player." in s.substringAfterLast('/') && !s.contains("~")) main = js
            OP.findAll(js).forEach { found[it.groupValues[1]] = it.groupValues[2] }
        }
        if ("searchTracks" !in found && main.isNotEmpty()) searchChunk(main)?.let { url ->
            OP.findAll(runCatching { get(url) }.getOrDefault("")).forEach { found[it.groupValues[1]] = it.groupValues[2] }
        }
        return found
    }

    /** The search route's script: its chunk id from the name table, then that id's hash from the `.js` table. */
    internal fun searchChunk(main: String): String? {
        val id = Regex("""(\d+):"xpui-routes-search"""").find(main)?.groupValues?.get(1) ?: return null
        // Webpack's `…+{id:"hash",…}[e]+".js"`: the hash table ends right before that; the CSS table comes after.
        val jsTable = main.indexOf("[e]+\".js\"").takeIf { it > 0 } ?: return null
        val window = main.substring((jsTable - 200_000).coerceAtLeast(0), jsTable)
        val hash = Regex("""\b$id:"([0-9a-f]{8})"""").findAll(window).lastOrNull()?.groupValues?.get(1) ?: return null
        return "https://open.spotifycdn.com/cdn/build/web-player/xpui-routes-search.$hash.js"
    }

    private fun get(url: String): String =
        Http.client.newCall(Request.Builder().url(url).header("User-Agent", SpotifyToken.UA).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body?.string().orEmpty()
        }

    // --- Queries ------------------------------------------------------------------------

    private suspend fun query(op: String, variables: JSONObject, retried: Boolean = false): JSONObject = withContext(Dispatchers.IO) {
        val bearer = SpotifyToken.bearer() ?: throw IOException("Spotify sign-in expired")
        val clientToken = SpotifyToken.clientToken()
        val body = JSONObject().put("variables", variables).put("operationName", op)
            .put("extensions", JSONObject().put("persistedQuery", JSONObject().put("version", 1).put("sha256Hash", hash(op, refresh = retried))))
        val req = Request.Builder().url(GQL)
            .post(body.toString().toRequestBody("application/json;charset=UTF-8".toMediaType()))
            .header("Authorization", "Bearer $bearer").header("Accept", "application/json")
            .header("App-Platform", "WebPlayer").header("Origin", SpotifyAccount.ORIGIN).header("Referer", SpotifyAccount.ORIGIN + "/")
            .header("User-Agent", SpotifyToken.UA)
            .apply { clientToken?.let { header("Client-Token", it) } }
            .build()
        val (code, text) = Http.client.newCall(req).execute().use { it.code to it.body?.string().orEmpty() }
        if (code == 401) { SpotifyToken.invalidate(); throw IOException("Spotify sign-in expired") }
        val root = runCatching { JSONObject(text) }.getOrElse { throw IOException("Spotify $op: HTTP $code") }
        val err = root.optJSONArray("errors")?.optJSONObject(0)?.optString("message").orEmpty()
        if (err.contains("PersistedQueryNotFound", true) && !retried) return@withContext query(op, variables, retried = true)
        if (err.isNotBlank()) { Log.w(TAG, "$op failed: $err"); throw IOException("Spotify $op: $err") }
        if (code !in 200..299) throw IOException("Spotify $op: HTTP $code")
        root
    }

    suspend fun profile(): SpotifyProfile? = runCatching {
        val me = query("profileAttributes", JSONObject()).optJSONObject("data")?.optJSONObject("me")?.optJSONObject("profile") ?: return null
        SpotifyProfile(me.optString("name").ifBlank { "Spotify" }, me.optJSONObject("avatar")?.let(::largest).orEmpty())
    }.getOrNull()

    /** Liked Songs first (as on the phone), then every playlist in the library. */
    suspend fun playlists(): List<SpotifyPlaylist> {
        val out = mutableListOf(SpotifyPlaylist(LIKED, "Liked Songs", "", LIKED_COVER))
        var offset = 0
        while (true) {
            val v = JSONObject().put("filters", JSONArray().put("Playlists")).put("order", JSONObject.NULL).put("textFilter", "")
                .put("features", JSONArray().put("LIKED_SONGS").put("YOUR_EPISODES_V2").put("PRERELEASES").put("EVENTS"))
                .put("limit", 50).put("offset", offset).put("flatten", true).put("expandedFolders", JSONArray())
                .put("folderUri", JSONObject.NULL).put("includeFoldersWhenFlattening", false)
            val lib = query("libraryV3", v).optJSONObject("data")?.optJSONObject("me")?.optJSONObject("libraryV3") ?: break
            val (page, raw) = parsePlaylists(lib)
            out += page
            offset += raw
            if (raw == 0 || offset >= lib.optInt("totalCount", offset)) break
        }
        Log.i(TAG, "library: ${out.size - 1} playlists + Liked Songs")
        return out
    }

    /** Every track of a playlist (or [LIKED]), page by page; [onPage] sees the list grow. */
    suspend fun tracks(playlistId: String, onPage: (List<SpotifyTrack>) -> Unit = {}): List<SpotifyTrack> {
        val all = mutableListOf<SpotifyTrack>()
        var offset = 0
        while (true) {
            val (page, raw, total) = if (playlistId == LIKED) {
                val t = query("fetchLibraryTracks", JSONObject().put("offset", offset).put("limit", 100))
                    .optJSONObject("data")?.optJSONObject("me")?.optJSONObject("library")?.optJSONObject("tracks") ?: break
                Triple(parseLiked(t), t.optJSONArray("items")?.length() ?: 0, t.optInt("totalCount"))
            } else {
                val c = query("fetchPlaylist", JSONObject().put("uri", "spotify:playlist:$playlistId").put("offset", offset).put("limit", 100)
                    .put("enableWatchFeedEntrypoint", false))
                    .optJSONObject("data")?.optJSONObject("playlistV2")?.optJSONObject("content") ?: break
                Triple(parsePlaylistTracks(c), c.optJSONArray("items")?.length() ?: 0, c.optInt("totalCount"))
            }
            all += page
            offset += raw
            onPage(all.toList())
            if (raw == 0 || offset >= total) break
        }
        Log.i(TAG, "playlist $playlistId: ${all.size} tracks")
        return all
    }

    private val uriCache = TtlCache<String, String>(24 * 3600_000L, 500)
    private val canvasCache = TtlCache<String, String>(12 * 3600_000L, 300)

    /** The Spotify track for a song from another catalogue (best search hit), for its Canvas. */
    suspend fun findTrack(title: String, artist: String): String? {
        val key = "$title|$artist".lowercase()
        uriCache.get(key)?.let { return it.ifEmpty { null } }
        val v = JSONObject().put("searchTerm", "$title ${artist.split(",").first()}").put("offset", 0).put("limit", 5)
            .put("numberOfTopResults", 5).put("includeAudiobooks", false).put("includePreReleases", false)
        val uri = runCatching {
            query("searchTracks", v).optJSONObject("data")?.optJSONObject("searchV2")?.optJSONObject("tracksV2")
                ?.optJSONArray("items")?.optJSONObject(0)?.optJSONObject("item")?.optJSONObject("data")?.optString("uri")
        }.getOrNull()?.ifBlank { null }
        uriCache.put(key, uri.orEmpty())
        Log.i(TAG, "search “$title”: ${uri ?: "no hit"}")
        return uri
    }

    /** The looping video Spotify shows behind a track ("Canvas"), or null when it has none. */
    suspend fun canvas(trackUri: String): String? {
        canvasCache.get(trackUri)?.let { return it.ifEmpty { null } }
        val url = runCatching {
            query("canvas", JSONObject().put("trackUri", trackUri)).optJSONObject("data")?.optJSONObject("trackUnion")
                ?.optJSONObject("canvas")?.optString("url")
        }.getOrNull()?.takeIf { it.startsWith("https://") && it.contains(".mp4") }
        canvasCache.put(trackUri, url.orEmpty())
        Log.i(TAG, "canvas $trackUri: ${if (url != null) "found" else "none"}")
        return url
    }

    // --- Parsing ------------------------------------------------------------------------

    internal fun parsePlaylists(lib: JSONObject): Pair<List<SpotifyPlaylist>, Int> {
        val items = lib.optJSONArray("items") ?: JSONArray()
        val out = (0 until items.length()).mapNotNull { i ->
            val w = items.optJSONObject(i)?.optJSONObject("item") ?: return@mapNotNull null
            val d = w.optJSONObject("data") ?: return@mapNotNull null
            if (d.optString("__typename") != "Playlist") return@mapNotNull null
            val uri = w.optString("_uri").ifBlank { return@mapNotNull null }
            SpotifyPlaylist(
                id = uri.substringAfterLast(':'), name = d.optString("name").ifBlank { return@mapNotNull null },
                owner = d.optJSONObject("ownerV2")?.optJSONObject("data")?.optString("name").orEmpty(),
                cover = d.optJSONObject("images")?.optJSONArray("items")?.optJSONObject(0)?.let(::largest).orEmpty(),
            )
        }
        return out to items.length()
    }

    internal fun parsePlaylistTracks(content: JSONObject): List<SpotifyTrack> {
        val items = content.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i -> items.optJSONObject(i)?.optJSONObject("itemV2")?.optJSONObject("data")?.let(::track) }
    }

    internal fun parseLiked(tracks: JSONObject): List<SpotifyTrack> {
        val items = tracks.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val w = items.optJSONObject(i)?.optJSONObject("track") ?: return@mapNotNull null
            w.optJSONObject("data")?.let { d -> track(d, w.optString("_uri")) }
        }
    }

    private fun track(d: JSONObject, wrapperUri: String = ""): SpotifyTrack? {
        val title = d.optString("name").ifBlank { return null }
        val uri = d.optString("uri").ifBlank { wrapperUri }.ifBlank { return null }
        val artists = d.optJSONObject("artists")?.optJSONArray("items")
        val artist = (0 until (artists?.length() ?: 0)).mapNotNull { artists!!.optJSONObject(it)?.optJSONObject("profile")?.optString("name")?.ifBlank { null } }.joinToString(", ")
        val album = d.optJSONObject("albumOfTrack")
        val ms = (d.optJSONObject("trackDuration") ?: d.optJSONObject("duration"))?.optLong("totalMilliseconds") ?: 0
        return SpotifyTrack(uri, title, artist, album?.optString("name").orEmpty(), (ms / 1000).toInt(), album?.optJSONObject("coverArt")?.let(::largest).orEmpty())
    }

    /** The widest of an image's `sources` (Spotify lists sizes in no fixed order). */
    private fun largest(image: JSONObject): String {
        val src = image.optJSONArray("sources") ?: return ""
        return (0 until src.length()).mapNotNull { src.optJSONObject(it) }.maxByOrNull { it.optInt("width") }?.optString("url").orEmpty()
    }
}
