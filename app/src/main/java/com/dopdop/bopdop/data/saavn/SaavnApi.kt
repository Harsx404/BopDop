package com.dopdop.bopdop.data.saavn

import com.dopdop.bopdop.data.model.ArtistPage
import com.dopdop.bopdop.data.model.Card
import com.dopdop.bopdop.data.model.CardKind
import com.dopdop.bopdop.data.model.Collection
import com.dopdop.bopdop.data.model.Episode
import com.dopdop.bopdop.data.model.PodcastShow
import com.dopdop.bopdop.data.model.SAAVN_SHOW
import com.dopdop.bopdop.data.model.SearchResults
import com.dopdop.bopdop.data.model.Shelf
import com.dopdop.bopdop.data.model.ShelfStyle
import com.dopdop.bopdop.data.model.SongVersion
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.net.Http
import com.dopdop.bopdop.data.net.TtlCache
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.abs

/**
 * JioSaavn official API — port of the DopDop desktop music catalogue
 * (src/proxy-server.mjs: saavnCall / saavnTracks / saavnCard / musicHome …).
 * 320kbps AAC straight from the CDN; no proxy needed on a phone.
 */
object SaavnApi {
    private const val API = "https://www.jiosaavn.com/api.php?_format=json&_marker=0&api_version=4"
    private val cache = TtlCache<String, String>(20 * 60_000L, 300)

    private suspend fun call(
        call: String,
        params: Map<String, String> = emptyMap(),
        ctx: String = "web6dot0",
        lang: String? = null,
        cacheable: Boolean = true,
    ): Any {
        val qs = (mapOf("__call" to call, "ctx" to ctx) + params).entries.joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, "UTF-8")}"
        }
        val key = "$qs|${lang.orEmpty()}"
        val body = (if (cacheable) cache.get(key) else null) ?: Http.getText(
            "$API&$qs",
            buildMap {
                put("Accept", "application/json")
                put("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36")
                if (!lang.isNullOrBlank()) put("Cookie", "L=${URLEncoder.encode(lang, "UTF-8")}")
            },
        ).also { if (cacheable) cache.put(key, it) }
        val t = body.trim()
        return if (t.startsWith("[")) JSONArray(t) else JSONObject(t)
    }

    private suspend fun obj(call: String, params: Map<String, String> = emptyMap(), ctx: String = "web6dot0", lang: String? = null, cacheable: Boolean = true) =
        call(call, params, ctx, lang, cacheable) as? JSONObject ?: JSONObject()

    fun unescape(s: String?): String = (s ?: "").replace("&quot;", "\"").replace("&amp;", "&").replace("&#039;", "'")

    fun img(u: String?, size: String = "500x500"): String =
        (u ?: "").replace(Regex("\\d{2,3}x\\d{2,3}"), size).replace(Regex("^http:"), "https:")

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    /** api_version 4 song objects → playable tracks (media url DES-decrypted in place). */
    fun tracks(songs: List<JSONObject>): List<Track> = songs.mapNotNull { s ->
        val m = s.optJSONObject("more_info") ?: return@mapNotNull null
        if (s.optString("type") != "song" && !m.has("encrypted_media_url")) return@mapNotNull null
        val enc = m.optString("encrypted_media_url")
        val hi = m.optString("320kbps") == "true"
        val url = Des.decryptSaavn(enc)?.replace("_96", if (hi) "_320" else "_160") ?: return@mapNotNull null
        val primary = m.optJSONObject("artistMap")?.optJSONArray("primary_artists").objects()
        val artist = primary.joinToString(", ") { it.optString("name") }.ifEmpty {
            m.optString("music").ifEmpty { s.optString("subtitle").substringBefore(" - ") }
        }
        Track(
            id = "sv:${s.optString("id")}",
            title = unescape(s.optString("title").ifEmpty { s.optString("song") }),
            artist = unescape(artist),
            artistId = primary.firstOrNull()?.optString("id")?.takeIf { it.isNotEmpty() }?.let { "sv:$it" },
            album = unescape(m.optString("album")),
            albumId = m.optString("album_id").takeIf { it.isNotEmpty() }?.let { "sv:$it" },
            durationSec = m.optString("duration").toIntOrNull() ?: 0,
            artwork = img(s.optString("image")),
            streamUrl = url,
            bitrate = if (hi) 320 else 160,
            explicit = s.optString("explicit_content") == "1",
        )
    }

    private fun card(x: JSONObject): Card? {
        val m = x.optJSONObject("more_info") ?: JSONObject()
        val type = x.optString("type")
        val kind = when (type) {
            "album" -> CardKind.ALBUM
            "playlist" -> CardKind.PLAYLIST
            "artist" -> CardKind.ARTIST
            "radio_station" -> CardKind.STATION
            "channel" -> CardKind.MOOD
            else -> return null
        }
        val id = when (kind) {
            CardKind.STATION -> if (m.optString("featured_station_type") == "artist") "artist:" + unescape(x.optString("title"))
                else "featured:" + (m.optString("query").ifEmpty { x.optString("title") })
            else -> x.optString("id")
        }
        val title = unescape(x.optString("title").ifEmpty { x.optString("name") })
        if (id.isBlank() || title.isBlank()) return null
        val sub = unescape(x.optString("subtitle").ifEmpty {
            m.optString("description").ifEmpty { if (type == "playlist" && m.optString("song_count").isNotEmpty()) "${m.optString("song_count")} songs" else "" }
        })
        return Card("sv:$id", kind, title, sub, img(x.optString("image").ifEmpty { x.optString("image_url") }))
    }

    private fun cards(arr: JSONArray?) = arr.objects().mapNotNull(::card)

    suspend fun home(lang: String): List<Shelf> {
        val d = obj("webapi.getLaunchData", lang = lang)
        val trending = d.optJSONArray("new_trending").objects()
        return listOf(
            Shelf("Trending now", "JioSaavn · 320 kbps", tracks = tracks(trending.filter { it.optString("type") == "song" }), style = ShelfStyle.TRACKS),
            Shelf("New releases", cards = cards(d.optJSONArray("new_albums")), style = ShelfStyle.LARGE_CARDS),
            Shelf("Top charts", cards = cards(d.optJSONArray("charts"))),
            Shelf("Trending albums & playlists", cards = trending.filter { it.optString("type") != "song" }.mapNotNull(::card)),
            Shelf("Editorial picks", cards = cards(d.optJSONArray("top_playlists"))),
            Shelf("Artist radio", cards = cards(d.optJSONArray("artist_recos")), style = ShelfStyle.ARTISTS),
            Shelf("Radio stations", cards = cards(d.optJSONArray("radio"))),
        ).filter { it.tracks.isNotEmpty() || it.cards.isNotEmpty() }
    }

    suspend fun album(id: String): Collection {
        val d = obj("content.getAlbumDetails", mapOf("albumid" to id))
        return Collection(
            id = "sv:$id", kind = CardKind.ALBUM,
            title = unescape(d.optString("title")),
            subtitle = unescape(d.optString("subtitle").ifEmpty { d.optJSONObject("more_info")?.optString("music").orEmpty() }),
            artwork = img(d.optString("image")),
            tracks = tracks(d.optJSONArray("list").objects()),
            year = d.optString("year"),
        )
    }

    suspend fun playlist(id: String): Collection {
        val d = obj("playlist.getDetails", mapOf("listid" to id, "n" to "100", "p" to "1"))
        return Collection(
            id = "sv:$id", kind = CardKind.PLAYLIST,
            title = unescape(d.optString("title")),
            subtitle = unescape(d.optString("subtitle").ifEmpty { d.optString("header_desc") }),
            artwork = img(d.optString("image")),
            tracks = tracks(d.optJSONArray("list").objects()),
        )
    }

    suspend fun artist(id: String): ArtistPage {
        val d = obj("artist.getArtistPageDetails", mapOf("artistId" to id, "n_song" to "25", "n_album" to "20", "page" to "0", "sort_order" to "", "category" to ""))
        val albums = (d.optJSONArray("topAlbums").objects() + d.optJSONArray("singles").objects()).mapNotNull(::card)
        return ArtistPage(
            id = "sv:$id",
            name = unescape(d.optString("name")),
            artwork = img(d.optString("image")),
            subtitle = unescape(d.optString("subtitle").ifEmpty { d.optString("dominantType") }).ifEmpty { "Artist" },
            topSongs = tracks(d.optJSONArray("topSongs").objects()),
            albums = albums,
            similar = d.optJSONArray("similarArtists").objects().mapNotNull { a ->
                val aid = a.optString("id").ifEmpty { return@mapNotNull null }
                Card("sv:$aid", CardKind.ARTIST, unescape(a.optString("name")), "Artist", img(a.optString("image_url").ifEmpty { a.optString("image") }))
            },
        )
    }

    /** Song objects for bare JioSaavn ids (shared playlists carry ids only). */
    suspend fun songs(ids: List<String>): List<Track> {
        if (ids.isEmpty()) return emptyList()
        val d = obj("song.getDetails", mapOf("pids" to ids.joinToString(",")))
        val list = d.optJSONArray("songs")?.objects()
            ?: d.keys().asSequence().mapNotNull { d.optJSONObject(it) }.toList()
        val byId = tracks(list).associateBy { it.rawId }
        return ids.mapNotNull { byId[it] }
    }

    /** The raw song object for one id, with release date, label and credits (song info panel). */
    suspend fun songObject(id: String): JSONObject? {
        val d = obj("song.getDetails", mapOf("pids" to id))
        return d.optJSONArray("songs")?.objects()?.firstOrNull() ?: d.optJSONObject(id)
    }

    suspend fun reco(id: String): List<Track> {
        val d = call("reco.getreco", mapOf("pid" to id), ctx = "android")
        val list = when (d) {
            is JSONArray -> d.objects()
            is JSONObject -> d.keys().asSequence().flatMap { k -> d.optJSONArray(k).objects() }.toList()
            else -> emptyList()
        }
        return tracks(list)
    }

    /** Radio: seed song ids, an artist name or a featured station name (desktop musicStation). */
    suspend fun station(ids: List<String>? = null, artist: String? = null, featured: String? = null, language: String = "", count: Int = 25): List<Track> {
        val created = when {
            !ids.isNullOrEmpty() -> obj("webradio.createEntityStation", mapOf("entity_id" to JSONArray(ids.take(5)).toString(), "entity_type" to "queue"), ctx = "android", cacheable = false)
            artist != null -> obj("webradio.createArtistStation", mapOf("artistid" to "", "name" to artist, "query" to artist, "language" to language), ctx = "android", cacheable = false)
            else -> obj("webradio.createFeaturedStation", mapOf("name" to featured.orEmpty(), "language" to language), ctx = "android", cacheable = false)
        }
        val sid = created.optString("stationid")
        if (sid.isNotEmpty()) {
            val d = obj("webradio.getSong", mapOf("stationid" to sid, "k" to count.toString(), "next" to "1"), ctx = "android", cacheable = false)
            val songs = d.keys().asSequence().mapNotNull { d.optJSONObject(it)?.optJSONObject("song") }.toList()
            if (songs.isNotEmpty()) return tracks(songs)
        }
        if (featured == null) return emptyList()
        // Featured stations often answer "No new song found": fall back to the best editorial playlist, shuffled.
        for (q in listOf("$featured $language".trim(), featured, "$featured songs")) {
            val pick = obj("search.getPlaylistResults", mapOf("q" to q, "n" to "5", "p" to "1")).optJSONArray("results").objects().firstOrNull()
            if (pick != null) return playlist(pick.optString("id")).tracks.shuffled().take(count)
        }
        return emptyList()
    }

    suspend fun search(q: String): SearchResults = coroutineScope {
        val songs = async { runCatching { obj("search.getResults", mapOf("q" to q, "n" to "30", "p" to "1")) }.getOrDefault(JSONObject()) }
        val albums = async { runCatching { obj("search.getAlbumResults", mapOf("q" to q, "n" to "12", "p" to "1")) }.getOrDefault(JSONObject()) }
        val artists = async { runCatching { obj("search.getArtistResults", mapOf("q" to q, "n" to "12", "p" to "1")) }.getOrDefault(JSONObject()) }
        val playlists = async { runCatching { obj("search.getPlaylistResults", mapOf("q" to q, "n" to "12", "p" to "1")) }.getOrDefault(JSONObject()) }
        SearchResults(
            tracks = tracks(songs.await().optJSONArray("results").objects()),
            albums = cards(albums.await().optJSONArray("results")),
            artists = artists.await().optJSONArray("results").objects().mapNotNull { a ->
                val aid = a.optString("id").ifEmpty { return@mapNotNull null }
                Card("sv:$aid", CardKind.ARTIST, unescape(a.optString("name").ifEmpty { a.optString("title") }), "Artist", img(a.optString("image")))
            },
            playlists = cards(playlists.await().optJSONArray("results")),
        )
    }

    suspend fun searchSongs(q: String, n: Int = 15): List<Track> =
        tracks(obj("search.getResults", mapOf("q" to q, "n" to n.toString(), "p" to "1")).optJSONArray("results").objects())

    // --- Hi-Res: find the same recording on JioSaavn for a YouTube Music track (desktop musicHiRes) ---
    /**
     * JioSaavn's copy of a YouTube Music song, for Hi-Res playback. Only the same recording counts
     * (SongVersion.sameRecording): same name, same version, an artist in common and within 8 s.
     * "Alfaaz - Vocals" or "Alfaaz (Lofi)" never stand in for "Alfaaz"; with no sure match the
     * song keeps its YouTube stream.
     */
    suspend fun hiResMatch(title: String, artist: String, duration: Int): Track? {
        val want = Track("yt:want", title, artist, durationSec = duration)
        if (SongVersion.parse(title).core.isEmpty()) return null
        val q = (title + " " + artist.split(",").first()).trim()
        return searchSongs(q).filter { SongVersion.sameRecording(want, it, toleranceSec = HI_RES_DRIFT) }
            .minWithOrNull(compareBy<Track>({ if (duration > 0 && it.durationSec > 0) abs(it.durationSec - duration) else HI_RES_DRIFT }, { -it.bitrate }))
    }

    private const val HI_RES_DRIFT = 8

    /**
     * JioSaavn's own release of a song, to play in its place: the same name, the same version and an
     * artist in common, but any length. YouTube Music often carries a shorter edit of a film song
     * (Gehra Hua is 3:51 there, 6:02 on JioSaavn), so this is a different file, played as itself with its
     * own length and lyrics, never swapped in under the YouTube track. 320 kbps first, then the closest length.
     */
    suspend fun fullCopy(t: Track): Track? {
        if (SongVersion.parse(t.title).core.isEmpty()) return null
        val q = (t.title + " " + t.artist.split(",").first()).trim()
        return searchSongs(q).filter { SongVersion.sameRecording(t, it, toleranceSec = Int.MAX_VALUE) }
            .minWithOrNull(compareBy<Track>({ -it.bitrate }, { if (t.durationSec > 0 && it.durationSec > 0) abs(it.durationSec - t.durationSec) else 0 }))
    }

    // --- Podcasts ---

    /** A show tile as a podcast card ("sv:show:" + its id), on its square art. */
    private fun showCard(x: JSONObject): Card? {
        if (x.optString("type") != "show") return null
        val id = x.optString("id").ifEmpty { return null }
        val title = unescape(x.optString("title")).ifEmpty { return null }
        val square = x.optJSONObject("more_info")?.optString("square_image").orEmpty()
        val sub = unescape(x.optString("subtitle").ifEmpty { x.optString("secondary_subtitle") }).ifEmpty { "Podcast" }
        return Card(SAAVN_SHOW + id, CardKind.PLAYLIST, title, sub, img(square.ifEmpty { x.optString("image") }))
    }

    /** JioSaavn's podcasts: its trending shelves ("Most heard in India"), then its top shows. */
    suspend fun podcasts(): List<Shelf> {
        val d = obj("content.getTopShows", mapOf("n" to "30", "p" to "1"))
        val trending = d.optJSONArray("trendingPodcasts").objects().map { sec ->
            val module = sec.optJSONObject("module")
            Shelf(unescape(module?.optString("title")).ifEmpty { "Trending podcasts" },
                unescape(module?.optString("subtitle")).ifEmpty { "On JioSaavn" } + " · JioSaavn",
                cards = sec.optJSONArray("items").objects().mapNotNull(::showCard))
        }
        return (trending + Shelf("Top shows", "JioSaavn", cards = d.optJSONArray("data").objects().mapNotNull(::showCard)))
            .filter { it.cards.size >= 2 }
    }

    /**
     * One show with its episodes: the newest season first, a few seasons deep, each newest first.
     * Episodes play from JioSaavn's own files, like its songs.
     */
    suspend fun podcastShow(showId: String): PodcastShow = coroutineScope {
        val d = obj("show.getHomePage", mapOf("show_id" to showId))
        val s = d.optJSONObject("show_details") ?: JSONObject()
        val m = s.optJSONObject("more_info") ?: JSONObject()
        val title = unescape(s.optString("title")).ifEmpty { "Podcast" }
        val seasons = d.optJSONArray("seasons").objects()
            .mapNotNull { it.optJSONObject("more_info")?.optString("season_number")?.toIntOrNull() }.distinct().sortedDescending()
        val bySeason = seasons.take(4).map { n ->
            async {
                runCatching {
                    (call("show.getAllEpisodes", mapOf("show_id" to showId, "season_number" to "$n", "sort_order" to "desc", "n" to "50", "p" to "1")) as? JSONArray).objects()
                }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten()
        val list = bySeason.ifEmpty { d.optJSONArray("episodes").objects() }
        val host = m.optJSONObject("artistMap")?.optJSONArray("primary_artists").objects().firstOrNull()
        PodcastShow(
            id = SAAVN_SHOW + showId,
            title = title,
            author = unescape(s.optString("subtitle")).ifEmpty { unescape(host?.optString("name")) },
            authorId = host?.optString("id")?.takeIf { it.isNotEmpty() }?.let { "sv:$it" },
            artwork = img(m.optString("square_image").ifEmpty { s.optString("image") }),
            description = unescape(m.optString("description").ifEmpty { s.optString("header_desc") }),
            episodes = list.mapNotNull { episode(it, showId, title) }.distinctBy { it.track.id },
        )
    }

    private fun episode(x: JSONObject, showId: String, show: String): Episode? {
        val m = x.optJSONObject("more_info") ?: return null
        val url = Des.decryptSaavn(m.optString("encrypted_media_url"))?.replace("_96.", "_160.") ?: return null
        val track = Track(
            id = "sv:${x.optString("id")}",
            title = unescape(x.optString("title")),
            artist = unescape(x.optString("subtitle")).ifEmpty { show },
            album = show,
            albumId = SAAVN_SHOW + showId,
            durationSec = m.optString("duration").toIntOrNull() ?: 0,
            artwork = img(m.optString("square_image_url").ifEmpty { x.optString("image") }),
            streamUrl = url,
            bitrate = 160,
        )
        val date = m.optString("release_date").let { d ->
            runCatching { java.time.LocalDate.parse(d).format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy")) }.getOrDefault(d)
        }
        return Episode(track, date, unescape(m.optString("description")))
    }
}
