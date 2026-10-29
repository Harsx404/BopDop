package com.dopdop.bopdop.data.ytm

import com.dopdop.bopdop.data.model.SongVersion
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.net.Http
import com.dopdop.bopdop.data.net.TtlCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** A YouTube Short: its id, title, the view count as YouTube writes it ("19M views") and as a number, and its tall thumbnail. */
data class ShortClip(val id: String, val title: String, val views: String, val viewCount: Long, val thumbnail: String)

/**
 * Popular YouTube Shorts that use a song, for the strip under Now Playing: YouTube's own Shorts
 * search for the song and its artist, kept to the ones that name the song or the artist, most
 * viewed first.
 */
object YtShorts {
    private const val SEARCH = "https://www.youtube.com/youtubei/v1/search?prettyPrint=false"
    private const val CLIENT_VERSION = "2.20251020.01.00"
    /** YouTube's "Shorts" search filter. */
    private const val SHORTS_FILTER = "EgIQCQ%3D%3D"
    private const val KEEP = 10

    private val cache = TtlCache<String, List<ShortClip>>(30 * 60_000L, 120)
    private val videos = ConcurrentHashMap<String, ResolvedVideo>()

    suspend fun forSong(t: Track): List<ShortClip> = withContext(Dispatchers.IO) {
        cache.get(t.id)?.let { return@withContext it }
        val title = t.title.substringBefore(" (").substringBefore(" [").substringBefore(" - ").trim()
        val artist = SongVersion.artists(t.artist).firstOrNull().orEmpty().ifBlank { t.artist.substringBefore(",").trim() }
        if (title.isBlank()) return@withContext emptyList()
        val country = Locale.getDefault().country.ifBlank { "US" }
        val body = JSONObject()
            .put("context", JSONObject().put("client", JSONObject()
                .put("clientName", "WEB").put("clientVersion", CLIENT_VERSION).put("hl", "en").put("gl", country)))
            .put("query", "$title $artist".trim())
            .put("params", java.net.URLDecoder.decode(SHORTS_FILTER, "UTF-8"))
        val d = Http.postJson(SEARCH, body, mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Safari/537.36",
            "X-YouTube-Client-Name" to "1", "X-YouTube-Client-Version" to CLIENT_VERSION,
        ))
        val found = ArrayList<ShortClip>()
        collect(d, found)
        // Only Shorts that are about this song: they name it, or its artist.
        val words = significant(title)
        val artistKey = artist.lowercase()
        val clips = found.distinctBy { it.id }
            .filter { c -> val s = c.title.lowercase(); words.any { it in s } || (artistKey.length >= 3 && artistKey in s) }
            .sortedByDescending { it.viewCount }
            .take(KEEP)
        cache.put(t.id, clips)
        clips
    }

    /** The Short's picture (and, when [withSound], its soundtrack) at up to [cap] lines, cached until its urls expire. */
    fun video(id: String, cap: Int): ResolvedVideo {
        val key = "$id@$cap"
        videos[key]?.let { if (it.video.expiresAtMs - System.currentTimeMillis() > 5 * 60_000) return it }
        return YtStreams.resolveVideo(id, cap).also { videos[key] = it }
    }

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
        return ShortClip(id, title, views, viewCount(views), thumb)
    }

    /** "19M views" → 19 000 000; "8.7K views" → 8 700; anything else → 0. */
    internal fun viewCount(text: String): Long {
        val m = Regex("([\\d.,]+)\\s*([KMB]?)", RegexOption.IGNORE_CASE).find(text) ?: return 0
        val n = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return 0
        val mul = when (m.groupValues[2].uppercase()) { "K" -> 1e3; "M" -> 1e6; "B" -> 1e9; else -> 1.0 }
        return (n * mul).toLong()
    }
}
