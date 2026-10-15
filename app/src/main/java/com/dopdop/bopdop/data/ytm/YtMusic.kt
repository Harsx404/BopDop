package com.dopdop.bopdop.data.ytm

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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * YouTube Music InnerTube client (WEB_REMIX identity, anonymous) — port of the
 * DopDop desktop src/lib/ytmusic.mjs catalogue code. Responses are walked
 * generically because YouTube reshuffles wrapper renderers often, the
 * list-item / two-row-item renderers rarely.
 */
object YtMusic {
    private const val API = "https://music.youtube.com/youtubei/v1/"
    const val WEB_REMIX_VERSION = "1.20260213.01.00"
    const val WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    private const val FILTER_SONGS = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"

    val gl: String = Locale.getDefault().country.takeIf { it.length == 2 } ?: "IN"
    val hl: String = "en"

    @Volatile var visitorData: String? = null
        private set

    private val cache = TtlCache<String, JSONObject>(10 * 60_000L, 200)

    suspend fun post(endpoint: String, body: JSONObject, cacheable: Boolean = true): JSONObject {
        val key = "$endpoint|$body"
        if (cacheable) cache.get(key)?.let { return it }
        body.put("context", JSONObject().put("client", JSONObject()
            .put("hl", hl).put("gl", gl)
            .put("clientName", "WEB_REMIX").put("clientVersion", WEB_REMIX_VERSION)
            .apply { visitorData?.let { put("visitorData", it) } }))
        val res = Http.postJson("$API$endpoint?prettyPrint=false", body, buildMap {
            put("User-Agent", WEB_UA)
            put("X-YouTube-Client-Name", "67")
            put("X-YouTube-Client-Version", WEB_REMIX_VERSION)
            put("Origin", "https://music.youtube.com")
            put("Referer", "https://music.youtube.com/")
            visitorData?.let { put("X-Goog-Visitor-Id", it) }
        })
        res.optJSONObject("responseContext")?.optString("visitorData")?.takeIf { it.isNotEmpty() }?.let { visitorData = it }
        if (cacheable) cache.put(key, res)
        return res
    }

    // --- generic walking ----------------------------------------------------------------

    private fun walk(n: Any?, out: MutableList<JSONObject> = mutableListOf()): MutableList<JSONObject> {
        when (n) {
            is JSONArray -> for (i in 0 until n.length()) walk(n.opt(i), out)
            is JSONObject -> { out.add(n); n.keys().forEach { walk(n.opt(it), out) } }
        }
        return out
    }

    /** Visit every renderer of the given keys; matches aren't descended into. */
    private fun <T> collect(root: Any?, handlers: Map<String, (JSONObject) -> T?>): List<T> {
        val out = mutableListOf<T>()
        fun visit(n: Any?) {
            when (n) {
                is JSONArray -> for (i in 0 until n.length()) visit(n.opt(i))
                is JSONObject -> {
                    for ((key, fn) in handlers) {
                        val r = n.optJSONObject(key)
                        if (r != null) { fn(r)?.let(out::add); return }
                    }
                    n.keys().forEach { visit(n.opt(it)) }
                }
            }
        }
        visit(root)
        return out
    }

    private fun text(o: JSONObject?): String {
        val runs = o?.optJSONArray("runs") ?: return o?.optString("simpleText").orEmpty()
        return (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }
    }

    private fun runs(o: JSONObject?): List<JSONObject> {
        val r = o?.optJSONArray("runs") ?: return emptyList()
        return (0 until r.length()).mapNotNull { r.optJSONObject(it) }
    }

    private fun secs(s: String) = s.split(":").fold(0) { a, v -> a * 60 + (v.trim().toIntOrNull() ?: 0) }

    fun bigThumb(thumbs: JSONArray?, size: Int = 544): String {
        if (thumbs == null || thumbs.length() == 0) return ""
        val u = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url").orEmpty()
        return resize(u, size)
    }

    /** googleusercontent / ytimg artwork at the requested square size. */
    fun resize(u: String, size: Int): String = when {
        u.contains("googleusercontent.com") || u.contains("ggpht.com") ->
            u.replace(Regex("=w\\d+-h\\d+[^=]*$"), "=w$size-h$size-l90-rj").replace(Regex("=s\\d+[^=]*$"), "=s$size")
        u.contains("i.ytimg.com") -> u.replace(Regex("/(default|mqdefault|hqdefault|sddefault)\\.jpg"), "/maxresdefault.jpg")
        else -> u
    }

    private fun pageType(be: JSONObject?) = be?.optJSONObject("browseEndpointContextSupportedConfigs")
        ?.optJSONObject("browseEndpointContextMusicConfig")?.optString("pageType").orEmpty()

    private data class Byline(val artist: String, val artistId: String?, val album: String, val albumId: String?, val duration: Int)

    /** Split "Artist • Album • 4:22" runs into parts. */
    private fun byline(rs: List<JSONObject>): Byline {
        val artists = mutableListOf<String>(); val artistIds = mutableListOf<String>()
        var album = ""; var albumId: String? = null; var duration = 0
        for (r in rs) {
            val be = r.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
            when (pageType(be)) {
                "MUSIC_PAGE_TYPE_ARTIST", "MUSIC_PAGE_TYPE_USER_CHANNEL" -> { artists += r.optString("text"); artistIds += be!!.optString("browseId") }
                "MUSIC_PAGE_TYPE_ALBUM" -> { album = r.optString("text"); albumId = be!!.optString("browseId") }
                else -> if (Regex("^\\d+:\\d{2}(:\\d{2})?$").matches(r.optString("text"))) duration = secs(r.optString("text"))
            }
        }
        if (artists.isEmpty()) {
            val first = rs.firstOrNull()?.optString("text")
            if (!first.isNullOrBlank() && first != " • " && !Regex("^\\d+:\\d{2}$").matches(first) && first !in setOf("Song", "Video", "Album", "Single", "EP", "Playlist", "Artist"))
                artists += first
            else if (first in setOf("Song", "Video") && rs.size > 2) artists += rs[2].optString("text")
        }
        return Byline(artists.joinToString(", "), artistIds.firstOrNull()?.let { "yt:$it" }, album, albumId?.let { "yt:$it" }, duration)
    }

    private fun track(videoId: String, title: String, b: Byline, thumb: String, explicit: Boolean = false) = Track(
        id = "yt:$videoId", title = title, artist = b.artist, artistId = b.artistId,
        album = b.album, albumId = b.albumId, durationSec = b.duration, artwork = thumb, explicit = explicit,
    )

    private val PAGE_KIND = mapOf(
        "MUSIC_PAGE_TYPE_PLAYLIST" to CardKind.PLAYLIST,
        "MUSIC_PAGE_TYPE_ALBUM" to CardKind.ALBUM,
        "MUSIC_PAGE_TYPE_AUDIOBOOK" to CardKind.ALBUM,
        "MUSIC_PAGE_TYPE_ARTIST" to CardKind.ARTIST,
        "MUSIC_PAGE_TYPE_LIBRARY_ARTIST" to CardKind.ARTIST,
        "MUSIC_PAGE_TYPE_USER_CHANNEL" to CardKind.ARTIST,
    )

    private fun browseCard(be: JSONObject?, title: String, subtitle: String, thumbs: JSONArray?): Card? {
        val kind = PAGE_KIND[pageType(be)] ?: return null
        if (title.isBlank()) return null
        val bid = be!!.optString("browseId")
        val id = if (kind == CardKind.PLAYLIST) bid.removePrefix("VL") else bid
        return Card("yt:$id", kind, title, subtitle, bigThumb(thumbs))
    }

    private fun isExplicit(r: JSONObject) = r.optJSONArray("badges")?.toString()?.contains("MUSIC_EXPLICIT_BADGE") == true

    private sealed interface Item { data class T(val t: Track) : Item; data class C(val c: Card) : Item }

    private fun listItem(r: JSONObject): Item? {
        val flex = r.optJSONArray("flexColumns")
        val cols = (0 until (flex?.length() ?: 0)).map {
            flex!!.optJSONObject(it)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")
        }
        val thumbs = r.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val videoId = r.optJSONObject("playlistItemData")?.optString("videoId")?.ifEmpty { null }
            ?: runs(cols.getOrNull(0)).firstOrNull()?.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")?.ifEmpty { null }
            ?: r.optJSONObject("overlay")?.optJSONObject("musicItemThumbnailOverlayRenderer")?.optJSONObject("content")
                ?.optJSONObject("musicPlayButtonRenderer")?.optJSONObject("playNavigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")?.ifEmpty { null }
        if (videoId == null) {
            val be = r.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint") ?: return null
            return browseCard(be, text(cols.getOrNull(0)), text(cols.getOrNull(1)), thumbs)?.let { Item.C(it) }
        }
        if (r.optString("musicItemRendererDisplayPolicy") == "MUSIC_ITEM_RENDERER_DISPLAY_POLICY_GREY_OUT") return null
        var b = byline(runs(cols.getOrNull(1)))
        if (b.album.isEmpty() && cols.getOrNull(2) != null && text(cols[2]).isNotEmpty() && !Regex("^[\\d.,]+[KMB]? (plays|views)$").matches(text(cols[2])))
            b = b.copy(album = text(cols[2]))
        if (b.duration == 0) {
            val fixed = r.optJSONArray("fixedColumns")?.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")?.optJSONObject("text")
            b = b.copy(duration = secs(text(fixed)))
        }
        return Item.T(track(videoId, text(cols.getOrNull(0)), b, bigThumb(thumbs), isExplicit(r)))
    }

    private fun twoRowItem(r: JSONObject): Item? {
        val thumbs = r.optJSONObject("thumbnailRenderer")?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val vid = r.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")?.ifEmpty { null }
        if (vid != null) return Item.T(track(vid, text(r.optJSONObject("title")), byline(runs(r.optJSONObject("subtitle"))), bigThumb(thumbs), isExplicit(r)))
        return browseCard(r.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint"), text(r.optJSONObject("title")), text(r.optJSONObject("subtitle")), thumbs)?.let { Item.C(it) }
    }

    private val itemHandlers: Map<String, (JSONObject) -> Item?> = mapOf(
        "musicResponsiveListItemRenderer" to ::listItem,
        "musicTwoRowItemRenderer" to ::twoRowItem,
    )

    private fun items(root: Any?) = collect(root, itemHandlers)
    private fun List<Item>.tracks() = filterIsInstance<Item.T>().map { it.t }
    private fun List<Item>.cards() = filterIsInstance<Item.C>().map { it.c }

    private fun contToken(d: JSONObject): String? {
        for (n in walk(d)) {
            n.optJSONObject("nextContinuationData")?.optString("continuation")?.takeIf { it.isNotEmpty() }?.let { return it }
            n.optJSONObject("continuationCommand")?.optString("token")?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    private suspend fun pageAll(first: JSONObject, pages: Int): List<Item> {
        var d = first
        val all = items(d).toMutableList()
        for (i in 1 until pages) {
            val t = contToken(d) ?: break
            d = post("browse", JSONObject().put("continuation", t))
            val more = items(d)
            if (more.isEmpty()) break
            all += more
        }
        return all
    }

    private suspend fun browse(id: String) = post("browse", JSONObject().put("browseId", id))

    // --- public API -----------------------------------------------------------------------

    suspend fun searchSongs(q: String): List<Track> {
        val d = post("search", JSONObject().put("query", q).put("params", java.net.URLDecoder.decode(FILTER_SONGS, "UTF-8")))
        return items(d).tracks()
    }

    suspend fun search(q: String): SearchResults = coroutineScope {
        val songs = async { runCatching { searchSongs(q) }.getOrDefault(emptyList()) }
        val all = async {
            runCatching {
                val d = post("search", JSONObject().put("query", q))
                // Top-result card (artist / album) sits in a musicCardShelfRenderer.
                val top = collect(d, mapOf("musicCardShelfRenderer" to { r: JSONObject ->
                    val t = runs(r.optJSONObject("title")).firstOrNull()
                    browseCard(t?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint"), t?.optString("text").orEmpty(), text(r.optJSONObject("subtitle")),
                        r.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail")?.optJSONArray("thumbnails"))
                }))
                top + items(d).cards()
            }.getOrDefault(emptyList())
        }
        val cards = all.await().distinctBy { it.id }
        SearchResults(
            tracks = songs.await(),
            albums = cards.filter { it.kind == CardKind.ALBUM },
            artists = cards.filter { it.kind == CardKind.ARTIST },
            playlists = cards.filter { it.kind == CardKind.PLAYLIST },
        )
    }

    suspend fun suggestions(q: String): List<String> {
        val d = post("music/get_search_suggestions", JSONObject().put("input", q))
        return collect(d, mapOf("searchSuggestionRenderer" to { r: JSONObject -> text(r.optJSONObject("suggestion")) }))
            .filter { it.isNotBlank() }.distinct().take(8)
    }

    /** Up-next / radio for a video. */
    suspend fun next(videoId: String): List<Track> {
        val d = post("next", JSONObject().put("videoId", videoId).put("playlistId", "RDAMVM$videoId").put("isAudioOnly", true))
        return collect(d, mapOf("playlistPanelVideoRenderer" to { r: JSONObject ->
            val vid = r.optString("videoId")
            if (vid.isEmpty() || vid == videoId) null else {
                var b = byline(runs(r.optJSONObject("longBylineText")))
                if (b.duration == 0) b = b.copy(duration = secs(text(r.optJSONObject("lengthText"))))
                track(vid, text(r.optJSONObject("title")), b, bigThumb(r.optJSONObject("thumbnail")?.optJSONArray("thumbnails")))
            }
        }))
    }

    /** Plain lyrics from YouTube Music's "Lyrics" tab (LyricFind / Musixmatch text). */
    suspend fun lyrics(videoId: String): String? {
        val d = post("next", JSONObject().put("videoId", videoId).put("isAudioOnly", true))
        val bid = walk(d).firstNotNullOfOrNull { n ->
            n.optJSONObject("browseEndpoint")?.takeIf { pageType(it) == "MUSIC_PAGE_TYPE_TRACK_LYRICS" }?.optString("browseId")
        } ?: return null
        val b = browse(bid)
        return collect(b, mapOf("musicDescriptionShelfRenderer" to { r: JSONObject -> text(r.optJSONObject("description")) }))
            .firstOrNull { it.isNotBlank() }?.replace("\r\n", "\n")?.replace('\r', '\n')
    }

    suspend fun collection(kind: CardKind, id: String): Collection {
        val d = browse(if (kind == CardKind.ALBUM) id else "VL$id")
        val hdr = walk(d).firstNotNullOfOrNull { it.optJSONObject("musicResponsiveHeaderRenderer") ?: it.optJSONObject("musicDetailHeaderRenderer") }
        val thumbs = hdr?.let { h -> walk(h.opt("thumbnail") ?: h).firstOrNull { it.has("thumbnails") }?.optJSONArray("thumbnails") }
        val image = bigThumb(thumbs, 1000)
        val title = text(hdr?.optJSONObject("title"))
        val sub = byline(runs(hdr?.optJSONObject("straplineTextOne")).ifEmpty { runs(hdr?.optJSONObject("subtitle")) })
        val year = runs(hdr?.optJSONObject("subtitle")).map { it.optString("text") }.firstOrNull { Regex("^\\d{4}$").matches(it) }.orEmpty()
        val tracks = pageAll(d, 10).tracks().map {
            if (kind == CardKind.ALBUM) it.copy(artwork = it.artwork.ifEmpty { resize(image, 544) }, artist = it.artist.ifEmpty { sub.artist }, artistId = it.artistId ?: sub.artistId, album = title.ifEmpty { it.album }, albumId = "yt:$id")
            else it
        }
        return Collection(
            id = "yt:$id", kind = kind,
            title = title.ifEmpty { if (id == "LM") "Liked Songs" else "Playlist" },
            subtitle = if (kind == CardKind.ALBUM) sub.artist else text(hdr?.optJSONObject("subtitle")).removePrefix("Playlist • "),
            artwork = image, tracks = tracks, year = year,
        )
    }

    suspend fun artist(id: String): ArtistPage {
        val d = browse(id)
        val hdr = walk(d).firstNotNullOfOrNull { it.optJSONObject("musicImmersiveHeaderRenderer") ?: it.optJSONObject("musicVisualHeaderRenderer") }
        val thumbs = hdr?.let { h -> walk(h.opt("thumbnail") ?: h).firstOrNull { it.has("thumbnails") }?.optJSONArray("thumbnails") }
        val all = items(d)
        val cards = all.cards()
        return ArtistPage(
            id = "yt:$id",
            name = text(hdr?.optJSONObject("title")).ifEmpty { "Artist" },
            artwork = bigThumb(thumbs, 1200),
            subtitle = text(hdr?.optJSONObject("subscriptionButton")?.optJSONObject("subscribeButtonRenderer")?.optJSONObject("subscriberCountText"))
                .let { if (it.isNotEmpty()) "$it subscribers" else "Artist" },
            topSongs = all.tracks().distinctBy { it.id },
            albums = cards.filter { it.kind == CardKind.ALBUM || it.kind == CardKind.PLAYLIST },
            similar = cards.filter { it.kind == CardKind.ARTIST && it.id != "yt:$id" },
        )
    }

    private fun shelves(d: JSONObject): List<Shelf> = collect(d, mapOf("musicCarouselShelfRenderer" to { sh: JSONObject ->
        val h = sh.optJSONObject("header")?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
        val found = collect(sh.optJSONArray("contents"), itemHandlers)
        val title = text(h?.optJSONObject("title"))
        if (title.isBlank() || found.isEmpty()) null else {
            val tracks = found.tracks(); val cards = found.cards()
            val listy = sh.optJSONArray("contents")?.optJSONObject(0)?.has("musicResponsiveListItemRenderer") == true
            Shelf(
                title = title,
                subtitle = text(h?.optJSONObject("strapline")),
                tracks = tracks, cards = cards,
                style = when {
                    listy && tracks.isNotEmpty() -> ShelfStyle.QUICK_PICKS
                    cards.isNotEmpty() && cards.all { it.kind == CardKind.ARTIST } -> ShelfStyle.ARTISTS
                    else -> ShelfStyle.CARDS
                },
            )
        }
    }))

    private suspend fun feed(browseId: String, pages: Int): List<Shelf> {
        var d = browse(browseId)
        val out = shelves(d).toMutableList()
        for (i in 1 until pages) {
            val t = contToken(d) ?: break
            d = post("browse", JSONObject().put("continuation", t))
            out += shelves(d)
        }
        return out
    }

    suspend fun home(): List<Shelf> = feed("FEmusic_home", 3)
    suspend fun explore(): List<Shelf> = feed("FEmusic_explore", 2)

    /** Moods & genres grid: (title, params) pairs for FEmusic_moods_and_genres_category. */
    suspend fun moods(): List<Pair<String, String>> {
        val d = browse("FEmusic_moods_and_genres")
        return collect(d, mapOf("musicNavigationButtonRenderer" to { r: JSONObject ->
            val be = r.optJSONObject("clickCommand")?.optJSONObject("browseEndpoint")
            val title = text(r.optJSONObject("buttonText"))
            val params = be?.optString("params").orEmpty()
            if (title.isBlank() || params.isEmpty()) null else title to params
        }))
    }

    suspend fun moodCategory(params: String): List<Shelf> {
        val d = post("browse", JSONObject().put("browseId", "FEmusic_moods_and_genres_category").put("params", params))
        val grid = collect(d, mapOf("gridRenderer" to { g: JSONObject ->
            val title = text(g.optJSONObject("header")?.optJSONObject("gridHeaderRenderer")?.optJSONObject("title"))
            val found = collect(g.optJSONArray("items"), itemHandlers)
            if (found.isEmpty()) null else Shelf(title.ifEmpty { "Playlists" }, cards = found.cards(), tracks = found.tracks())
        }))
        return shelves(d) + grid
    }
}
