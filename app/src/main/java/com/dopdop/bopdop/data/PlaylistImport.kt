package com.dopdop.bopdop.data

import com.dopdop.bopdop.data.model.CardKind
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.net.Http
import com.dopdop.bopdop.data.ytm.YtMusic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

/** A song as another service lists it, before it is matched to something BopDop can play. */
data class ForeignSong(val title: String, val artist: String, val durationSec: Int)

data class ImportResult(val name: String, val matched: List<Track>, val missed: List<ForeignSong>)

/**
 * Turns a Spotify or YouTube / YouTube Music playlist link into a BopDop playlist.
 * YouTube playlists load directly; Spotify's public embed page lists the songs, and each
 * one is matched against YouTube Music by title, artist and length.
 */
object PlaylistImport {
    private val SPOTIFY = Regex("open\\.spotify\\.com/(?:intl-[a-z]+/)?(playlist|album)/([A-Za-z0-9]{10,40})")
    private val YT_LIST = Regex("[?&]list=([A-Za-z0-9_-]{10,80})")

    fun recognises(link: String) = SPOTIFY.containsMatchIn(link) || YT_LIST.containsMatchIn(link)

    suspend fun import(link: String, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): ImportResult = withContext(Dispatchers.IO) {
        YT_LIST.find(link)?.let { m ->
            val c = YtMusic.collection(CardKind.PLAYLIST, m.groupValues[1])
            onProgress(c.tracks.size, c.tracks.size)
            return@withContext ImportResult(c.title.ifBlank { "Imported playlist" }, c.tracks, emptyList())
        }
        val sp = SPOTIFY.find(link) ?: throw IllegalArgumentException("Paste a Spotify or YouTube playlist link")
        val (name, songs) = spotify(sp.groupValues[1], sp.groupValues[2])
        var done = 0
        val gate = Semaphore(4)
        val matches = coroutineScope {
            songs.map { s ->
                async {
                    gate.withPermit { match(s) }.also { synchronized(this@PlaylistImport) { onProgress(++done, songs.size) } }
                }
            }.awaitAll()
        }
        ImportResult(name, matches.filterNotNull().distinctBy { it.id }, songs.filterIndexed { i, _ -> matches[i] == null })
    }

    private suspend fun spotify(kind: String, id: String): Pair<String, List<ForeignSong>> {
        val html = Http.getText("https://open.spotify.com/embed/$kind/$id", mapOf("User-Agent" to UA))
        val json = Regex("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1) ?: throw IllegalStateException("Spotify didn't share that playlist")
        val entity = JSONObject(json).optJSONObject("props")?.optJSONObject("pageProps")?.optJSONObject("state")
            ?.optJSONObject("data")?.optJSONObject("entity") ?: throw IllegalStateException("Spotify didn't share that playlist")
        val list = entity.optJSONArray("trackList") ?: throw IllegalStateException("That playlist is empty")
        val songs = (0 until list.length()).mapNotNull { i ->
            val t = list.optJSONObject(i) ?: return@mapNotNull null
            val title = t.optString("title").ifBlank { return@mapNotNull null }
            ForeignSong(title, t.optString("subtitle").replace(' ', ' '), (t.optLong("duration") / 1000).toInt())
        }
        return entity.optString("name").ifBlank { "Imported playlist" } to songs
    }

    /** Best YouTube Music song for [s]: same title words, an artist in common, length within 15 s. */
    internal suspend fun match(s: ForeignSong): Track? {
        val artist = s.artist.split(",").first().trim()
        val results = runCatching { YtMusic.searchSongs("${cleanTitle(s.title)} $artist") }.getOrDefault(emptyList())
        return results.take(6).map { it to score(s, it) }.filter { it.second > 0 }.maxByOrNull { it.second }?.first
    }

    internal fun score(s: ForeignSong, t: Track): Int {
        val want = words(cleanTitle(s.title))
        val got = words(cleanTitle(t.title))
        if (want.isEmpty() || !got.containsAll(want) && !want.containsAll(got)) return 0
        val artists = s.artist.split(",").map { words(it) }.filter { it.isNotEmpty() }
        val theirs = words(t.artist)
        if (artists.isNotEmpty() && artists.none { theirs.containsAll(it) }) return 0
        val drift = if (s.durationSec > 0 && t.durationSec > 0) abs(s.durationSec - t.durationSec) else 0
        if (drift > 15) return 0
        return 100 - drift - (got - want).size * 3
    }

    private fun cleanTitle(s: String) = s.replace(Regex("\\((feat|ft|with)\\.?[^)]*\\)|\\[(feat|ft)\\.?[^]]*]|\\s-\\s.*(remaster|version|mix).*$", RegexOption.IGNORE_CASE), " ")
    private fun words(s: String) = s.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().split(" ").filter { it.isNotBlank() }.toSet()

    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Safari/537.36"
}
