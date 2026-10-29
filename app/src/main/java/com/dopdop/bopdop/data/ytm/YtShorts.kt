package com.dopdop.bopdop.data.ytm

import com.dopdop.bopdop.data.model.SongVersion
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.ShortsTags
import com.dopdop.bopdop.data.net.Http
import com.dopdop.bopdop.data.net.TtlCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** A YouTube Short: its id, title, the view count as YouTube writes it ("19M views") and as a number, and its tall thumbnail. */
data class ShortClip(val id: String, val title: String, val views: String, val viewCount: Long, val thumbnail: String,
    val tags: Set<String> = emptySet())

/**
 * The sound a Short uses, as YouTube tags it: its name ("AZUL", "Original Sound"), the video it
 * comes from (a song's own upload when it's a song; null when YouTube names none) and who posted it.
 */
data class ShortSound(val title: String, val sourceId: String?, val by: String) {
    val original: Boolean get() = title.isBlank() || title.lowercase().startsWith("original sound") || title.lowercase().startsWith("original audio")
}

/**
 * Popular YouTube Shorts that use a song, for the strip under Now Playing: YouTube's own Shorts
 * search for the song and its artist, kept to the ones that name the song or the artist, most
 * viewed first.
 */
object YtShorts {
    private const val SEARCH = "https://www.youtube.com/youtubei/v1/search?prettyPrint=false"
    private const val REEL = "https://www.youtube.com/youtubei/v1/reel/reel_item_watch?prettyPrint=false"
    private const val CLIENT_VERSION = "2.20251020.01.00"
    /** YouTube's "Shorts" search filter. */
    private const val SHORTS_FILTER = "EgIQCQ%3D%3D"
    private const val KEEP = 10

    private val cache = TtlCache<String, List<ShortClip>>(30 * 60_000L, 120)
    private val videos = ConcurrentHashMap<String, ResolvedVideo>()

    /** Topic discovery is deliberately not constrained to the entry song or its artist. */
    suspend fun forTags(tags: List<String>): List<ShortClip> = withContext(Dispatchers.IO) {
        val query = tags.take(2).map { it.trim().take(40) }.filter { it.isNotBlank() }.joinToString(" ")
        if (query.isBlank()) return@withContext emptyList()
        val key = "tags:$query"
        cache.get(key)?.let { return@withContext it }
        search("$query shorts").distinctBy { it.id }.take(24).also { cache.put(key, it) }
    }

    suspend fun forSong(t: Track): List<ShortClip> = withContext(Dispatchers.IO) {
        if (t.isShortAudio) return@withContext listOf(ShortClip(t.rawId, t.title, "", 0, t.artwork, ShortsTags.extract(t.title)))
        cache.get(t.id)?.let { return@withContext it }
        val title = t.title.substringBefore(" (").substringBefore(" [").substringBefore(" - ").trim()
        val artist = SongVersion.artists(t.artist).firstOrNull().orEmpty().ifBlank { t.artist.substringBefore(",").trim() }
        if (title.isBlank()) return@withContext emptyList()
        val found = search("$title $artist".trim())
        val words = significant(title)
        val artistKey = artist.lowercase()
        val clips = found.distinctBy { it.id }
            .filter { c -> val s = c.title.lowercase(); words.any { it in s } || (artistKey.length >= 3 && artistKey in s) }
            .sortedByDescending { it.viewCount }
            .take(KEEP)
        cache.put(t.id, clips)
        clips
    }

    private suspend fun search(query: String): List<ShortClip> {
        val country = Locale.getDefault().country.ifBlank { "US" }
        val body = JSONObject()
            .put("context", JSONObject().put("client", JSONObject()
                .put("clientName", "WEB").put("clientVersion", CLIENT_VERSION).put("hl", "en").put("gl", country)))
            .put("query", query)
            .put("params", java.net.URLDecoder.decode(SHORTS_FILTER, "UTF-8"))
        val d = Http.postJson(SEARCH, body, mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Safari/537.36",
            "X-YouTube-Client-Name" to "1", "X-YouTube-Client-Version" to CLIENT_VERSION,
        ))
        val found = ArrayList<ShortClip>()
        collect(d, found)
        return found
    }

    private val sounds = TtlCache<String, ShortSound>(6 * 3600_000L, 300)

    /**
     * The sound [clipId] uses, from the Short's own page: the "sound" line under it and, in its
     * audio panel, the video that sound comes from.
     */
    suspend fun sound(clipId: String): ShortSound? = withContext(Dispatchers.IO) {
        sounds.get(clipId)?.let { return@withContext it }
        val body = JSONObject()
            .put("context", JSONObject().put("client", JSONObject()
                .put("clientName", "WEB").put("clientVersion", CLIENT_VERSION).put("hl", "en").put("gl", Locale.getDefault().country.ifBlank { "US" })))
            .put("playerRequest", JSONObject().put("videoId", clipId))
            .put("params", "CAUwAg==")
            .put("disablePlayerResponse", true)
        val d = runCatching {
            Http.postJson(REEL, body, mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Safari/537.36",
                "X-YouTube-Client-Name" to "1", "X-YouTube-Client-Version" to CLIENT_VERSION,
            ))
        }.getOrNull() ?: return@withContext null
        val title = find(d) { it.optJSONObject("soundAttributionTitle")?.optString("content") }.orEmpty()
        // The audio panel's header links the sound's own video, and names who posted it.
        val header = find(d) { it.optJSONObject("pageHeaderViewModel") }
        val source = header?.let { h -> find(h) { it.optJSONObject("watchEndpoint")?.optString("videoId")?.ifEmpty { null } } }
        val by = header?.optJSONObject("metadata")?.let { m -> find(m) { o -> o.optJSONObject("text")?.optString("content")?.ifBlank { null } } }.orEmpty()
        val panelTitle = header?.let { h -> find(h) { it.optJSONObject("text")?.optString("content")?.ifBlank { null } } }.orEmpty()
        ShortSound(title.ifBlank { panelTitle }, source?.takeIf { it != clipId }, by).also { sounds.put(clipId, it) }
    }

    /** The first value [pick] finds anywhere under [o], depth first. */
    private fun <T> find(o: Any?, pick: (JSONObject) -> T?): T? {
        when (o) {
            is JSONObject -> { pick(o)?.let { return it }; for (k in o.keys()) find(o.opt(k), pick)?.let { return it } }
            is JSONArray -> for (i in 0 until o.length()) find(o.opt(i), pick)?.let { return it }
        }
        return null
    }

    /** The Short's picture (and, when [withSound], its soundtrack) at up to [cap] lines, cached until its urls expire. */
    fun video(id: String, cap: Int): ResolvedVideo {
        val key = "$id@$cap"
        videos[key]?.let { if (it.video.expiresAtMs - System.currentTimeMillis() > 5 * 60_000) return it }
        return YtStreams.resolveVideo(id, cap).also {
            if (videos.size >= 48) videos.clear()
            videos[key] = it
        }
    }

    fun invalidateVideo(id: String, cap: Int) { videos.remove("$id@$cap") }

    private fun significant(title: String) = title.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }.take(3)
        .ifEmpty { listOf(title.lowercase()) }

    private fun collect(o: Any?, out: MutableList<ShortClip>) {
        when (o) {
            is JSONObject -> {
                o.optJSONObject("shortsLockupViewModel")?.let { clip(it)?.let(out::add) }
                o.keys().forEach { k -> if (k != "shortsLockupViewModel") collect(o.opt(k), out) }
            }
            is JSONArray -> for (i in 0 until o.length()) collect(o.opt(i), out)
        }
    }

    private fun clip(v: JSONObject): ShortClip? {
        val watch = v.optJSONObject("onTap")?.optJSONObject("innertubeCommand")?.optJSONObject("reelWatchEndpoint") ?: return null
        val id = watch.optString("videoId").ifEmpty { return null }
        val meta = v.optJSONObject("overlayMetadata")
        val title = meta?.optJSONObject("primaryText")?.optString("content").orEmpty()
        val views = meta?.optJSONObject("secondaryText")?.optString("content").orEmpty()
        val thumb = watch.optJSONObject("thumbnail")?.optJSONArray("thumbnails")?.optJSONObject(0)?.optString("url")
            ?.takeIf { it.isNotBlank() } ?: "https://i.ytimg.com/vi/$id/oar2.jpg"
        return ShortClip(id, title, views, viewCount(views), thumb, ShortsTags.extract(title))
    }

    /** "19M views" → 19 000 000; "8.7K views" → 8 700; anything else → 0. */
    internal fun viewCount(text: String): Long {
        val m = Regex("([\\d.,]+)\\s*([KMB]?)", RegexOption.IGNORE_CASE).find(text) ?: return 0
        val n = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return 0
        val mul = when (m.groupValues[2].uppercase()) { "K" -> 1e3; "M" -> 1e6; "B" -> 1e9; else -> 1.0 }
        return (n * mul).toLong()
    }
}
