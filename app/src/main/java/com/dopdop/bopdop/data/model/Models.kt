package com.dopdop.bopdop.data.model

import org.json.JSONArray
import org.json.JSONObject

/** Where a track/collection comes from. Ids are prefixed so they never collide across sources. */
/** Where a song or card comes from. SPOTIFY is browse-only: its songs play from their YouTube Music copy. */
enum class Source { YTM, SAAVN, LOCAL, SPOTIFY }

data class Track(
    /** "yt:<videoId>", "sv:<saavnId>", "sp:<Spotify id>" (plays from YouTube) or "lc:<MediaStore id>" for a file on this phone. */
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String? = null,
    val album: String = "",
    val albumId: String? = null,
    val durationSec: Int = 0,
    val artwork: String = "",
    /** Direct url: JioSaavn's CDN, or a local file's content:// uri. YouTube urls are resolved at play time. */
    val streamUrl: String? = null,
    val bitrate: Int = 0,
    val explicit: Boolean = false,
) {
    val source: Source get() = when {
        id.startsWith("sv:") -> Source.SAAVN
        id.startsWith("lc:") -> Source.LOCAL
        id.startsWith("sp:") -> Source.SPOTIFY
        else -> Source.YTM
    }
    val rawId: String get() = id.substringAfter(':')

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("title", title); put("artist", artist)
        artistId?.let { put("artistId", it) }
        put("album", album)
        albumId?.let { put("albumId", it) }
        put("duration", durationSec); put("artwork", artwork)
        streamUrl?.let { put("url", it) }
        put("bitrate", bitrate); put("explicit", explicit)
    }

    companion object {
        fun fromJson(o: JSONObject) = Track(
            id = o.getString("id"),
            title = o.optString("title"),
            artist = o.optString("artist"),
            artistId = o.optString("artistId").ifEmpty { null },
            album = o.optString("album"),
            albumId = o.optString("albumId").ifEmpty { null },
            durationSec = o.optInt("duration"),
            artwork = o.optString("artwork"),
            streamUrl = o.optString("url").ifEmpty { null },
            bitrate = o.optInt("bitrate"),
            explicit = o.optBoolean("explicit"),
        )
    }
}

enum class CardKind { ALBUM, PLAYLIST, ARTIST, STATION, MOOD }

/** Something browsable: album, playlist, artist, radio station. */
data class Card(
    val id: String,
    val kind: CardKind,
    val title: String,
    val subtitle: String = "",
    val artwork: String = "",
) {
    val source: Source get() = when {
        id.startsWith("sv:") -> Source.SAAVN
        id.startsWith("local:") -> Source.LOCAL
        id.startsWith("sp:") -> Source.SPOTIFY
        else -> Source.YTM
    }
    val rawId: String get() = id.substringAfter(':')

    fun toJson(): JSONObject = JSONObject().put("id", id).put("kind", kind.name)
        .put("title", title).put("subtitle", subtitle).put("artwork", artwork)

    companion object {
        fun fromJson(o: JSONObject) = Card(
            id = o.getString("id"),
            kind = runCatching { CardKind.valueOf(o.optString("kind")) }.getOrDefault(CardKind.PLAYLIST),
            title = o.optString("title"),
            subtitle = o.optString("subtitle"),
            artwork = o.optString("artwork"),
        )
    }
}

enum class ShelfStyle { QUICK_PICKS, CARDS, LARGE_CARDS, ARTISTS, TRACKS }

data class Shelf(
    val title: String,
    val subtitle: String = "",
    val tracks: List<Track> = emptyList(),
    val cards: List<Card> = emptyList(),
    val style: ShelfStyle = ShelfStyle.CARDS,
)

/** Home: its shelves, and YouTube Music's mood chips for it (label to browse params). */
data class HomeFeed(val shelves: List<Shelf>, val chips: List<Pair<String, String>> = emptyList())

data class Collection(
    val id: String,
    val kind: CardKind,
    val title: String,
    val subtitle: String,
    val artwork: String,
    val tracks: List<Track>,
    val year: String = "",
)

data class ArtistPage(
    val id: String,
    val name: String,
    val artwork: String,
    val subtitle: String,
    val topSongs: List<Track>,
    val albums: List<Card>,
    val similar: List<Card>,
)

data class SearchResults(
    val tracks: List<Track> = emptyList(),
    val albums: List<Card> = emptyList(),
    val artists: List<Card> = emptyList(),
    val playlists: List<Card> = emptyList(),
) {
    val isEmpty get() = tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()
}

/** One timed word of a word-synced line (enhanced LRC): when it starts and its text, spaces included. */
data class LyricWord(val timeMs: Long, val text: String)

/** A timed lyric line; [words] is filled when the source times every word (karaoke). */
data class LyricLine(val timeMs: Long, val text: String, val words: List<LyricWord> = emptyList())

data class Lyrics(val synced: List<LyricLine>, val plain: String?) {
    val isSynced get() = synced.isNotEmpty()
    /** Every line is timed word by word. */
    val isWordSynced get() = synced.any { it.words.isNotEmpty() }
}

fun List<Track>.toJsonArray() = JSONArray().also { a -> forEach { a.put(it.toJson()) } }
fun JSONArray?.toTracks(): List<Track> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { runCatching { Track.fromJson(getJSONObject(it)) }.getOrNull() }

fun List<Card>.cardsToJson() = JSONArray().also { a -> forEach { a.put(it.toJson()) } }
fun JSONArray?.toCards(): List<Card> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { runCatching { Card.fromJson(getJSONObject(it)) }.getOrNull() }

/**
 * "title|tags|artist" used to dedupe the same recording across sources. Versions keep their own
 * key: "Kesariya" and "Kesariya (Lofi Flip)" both stay in search results.
 */
fun Track.matchKey(): String {
    val a = SongVersion.artists(artist.split(',', '&').firstOrNull().orEmpty()).firstOrNull().orEmpty()
    return SongVersion.recordingKey(title) + "|" + a
}

/**
 * Songs to recognise again, whichever source a copy comes from: the same id, or the same title
 * with at least one artist in common (YouTube Music and JioSaavn list artists in different orders).
 * By default only the same version counts ("Not for me" on a song leaves its lofi cut alone);
 * [anyVersion] treats every version as the song (Discover: nothing you've heard in any form).
 */
class SongSet(tracks: Iterable<Track> = emptyList(), private val anyVersion: Boolean = false) {
    private val ids = HashSet<String>()
    private val byTitle = HashMap<String, MutableSet<String>>()

    init { tracks.forEach(::add) }

    fun add(t: Track) {
        ids += t.id
        byTitle.getOrPut(titleKey(t)) { HashSet() } += SongVersion.artists(t.artist)
    }

    operator fun contains(t: Track): Boolean =
        t.id in ids || byTitle[titleKey(t)]?.let { known -> SongVersion.artists(t.artist).any { it in known } } == true

    private fun titleKey(t: Track) = if (anyVersion) SongVersion.parse(t.title).core else SongVersion.recordingKey(t.title)
}

fun formatDuration(sec: Int): String {
    if (sec <= 0) return ""
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatTotal(sec: Int): String {
    val h = sec / 3600; val m = (sec % 3600) / 60
    return when {
        h > 0 -> "$h hr $m min"
        else -> "$m min"
    }
}

fun songs(n: Int) = if (n == 1) "1 song" else "$n songs"
