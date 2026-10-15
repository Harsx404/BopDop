package com.dopdop.bopdop.data.lyrics

import android.util.Log
import com.dopdop.bopdop.data.model.LyricLine
import com.dopdop.bopdop.data.model.Lyrics
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.net.Http
import com.dopdop.bopdop.data.net.TtlCache
import com.dopdop.bopdop.data.ytm.YtMusic
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Lyrics, best source first:
 *  1. LRCLIB — time-synced, one search call per song (it rate-limits hard);
 *  2. YouTube Music — LyricFind text, strong on Bollywood / regional catalogues;
 *  3. JioSaavn — its own lyric sheets for Saavn tracks.
 */
object LyricsApi {
    private const val TAG = "Lyrics"
    private val cache = TtlCache<String, Lyrics>(60 * 60_000L, 120)
    private val misses = TtlCache<String, Boolean>(30 * 60_000L, 300)
    private val headers = mapOf("User-Agent" to "BopDop/1.0 (https://github.com/Harsx404)")

    /** Strip "(From …)", "[…]", "feat.", " - Movie | …" decorations common on Indian releases. */
    fun cleanTitle(s: String) = s.replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
        .replace(Regex("(?i)\\b(feat|ft)\\.?\\b.*$"), " ")
        .split(" | ", " - ", " – ").first()
        .replace(Regex("\\s+"), " ").trim()

    private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    suspend fun get(t: Track): Lyrics? {
        cache.get(t.id)?.let { return it }
        if (misses.get(t.id) == true) return null
        // 1) YouTube Music's own timed lyrics — exact for YT tracks, and they cover Bollywood.
        val vid = runCatching { videoIdFor(t) }.getOrNull()
        val timed = vid?.let { v -> runCatching { YtMusic.timedLyrics(v) }.onFailure { Log.w(TAG, "ytm timed: ${it.message}") }.getOrNull() }
            ?.let { lines -> Lyrics(lines.map { (s, _, text) -> LyricLine(s, text) }, null) }
        val result = timed
            // 2) LRCLIB synced, 3) any plain text.
            ?: runCatching { lrclib(t) }.onFailure { Log.w(TAG, "lrclib: ${it.message}") }.getOrNull()?.let { l ->
                if (l.isSynced) l else (vid?.let { runCatching { YtMusic.lyrics(it) }.getOrNull() }?.let { Lyrics(emptyList(), it) } ?: l)
            }
            ?: vid?.let { runCatching { YtMusic.lyrics(it) }.getOrNull() }?.let { Lyrics(emptyList(), it) }
            ?: runCatching { saavn(t) }.getOrNull()
        if (result == null) misses.put(t.id, true) else cache.put(t.id, result)
        return result
    }

    private suspend fun lrclib(t: Track): Lyrics? {
        val title = cleanTitle(t.title)
        if (title.isEmpty()) return null
        val artists = t.artist.split(',', '&').map { it.trim() }.filter { it.isNotEmpty() }
        val arr = JSONArray(Http.getText("https://lrclib.net/api/search?track_name=${enc(title)}", headers))
        val nt = norm(title)
        val wanted = artists.map(::norm).toSet()
        var best: JSONObject? = null
        var bestScore = -99.0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            var score = 0.0
            val ot = norm(o.optString("trackName"))
            score += when { ot == nt -> 3.0; ot.contains(nt) || nt.contains(ot) -> 1.5; else -> -2.0 }
            val oa = norm(o.optString("artistName"))
            // Credits differ across sources (composer vs singer) — any shared name counts.
            if (wanted.any { w -> w.isNotEmpty() && (oa.contains(w) || w.contains(oa)) }) score += 3.0
            val d = o.optDouble("duration", 0.0).toInt()
            if (t.durationSec > 0 && d > 0) {
                val diff = kotlin.math.abs(d - t.durationSec)
                score += when { diff <= 3 -> 2.5; diff <= 8 -> 1.0; diff > 20 -> -3.0; else -> 0.0 }
            }
            if (o.optString("syncedLyrics").let { it.isNotBlank() && it != "null" }) score += 1.5
            if (score > bestScore) { best = o; bestScore = score }
        }
        return if (bestScore >= 4.0) best?.let(::parse) else null
    }

    /** The YouTube video for a track: its own id, or the best title match for JioSaavn songs. */
    private suspend fun videoIdFor(t: Track): String? = if (t.id.startsWith("yt:")) t.rawId else
        YtMusic.searchSongs("${cleanTitle(t.title)} ${t.artist.split(",").first()}").firstOrNull {
            norm(cleanTitle(it.title)).let { a -> val b = norm(cleanTitle(t.title)); a.contains(b) || b.contains(a) } &&
                (t.durationSec <= 0 || it.durationSec <= 0 || kotlin.math.abs(it.durationSec - t.durationSec) <= 6)
        }?.rawId

    private suspend fun saavn(t: Track): Lyrics? {
        if (!t.id.startsWith("sv:")) return null
        val o = JSONObject(Http.getText(
            "https://www.jiosaavn.com/api.php?__call=lyrics.getLyrics&lyrics_id=${t.rawId}&ctx=web6dot0&api_version=4&_format=json&_marker=0",
            mapOf("User-Agent" to "Mozilla/5.0"),
        ))
        val raw = o.optString("lyrics").takeIf { it.isNotBlank() } ?: return null
        return Lyrics(emptyList(), raw.replace(Regex("(?i)<br\\s*/?>"), "\n").trim())
    }

    private fun parse(o: JSONObject): Lyrics? {
        if (o.optBoolean("instrumental")) return Lyrics(emptyList(), "♪ Instrumental ♪")
        val synced = o.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }?.let(::parseLrc).orEmpty()
        val plain = o.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
        if (synced.isEmpty() && plain == null) return null
        return Lyrics(synced, plain)
    }

    private val TAG_RE = Regex("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]")

    fun parseLrc(lrc: String): List<LyricLine> = lrc.lines().flatMap { line ->
        val tags = TAG_RE.findAll(line).toList()
        if (tags.isEmpty()) return@flatMap emptyList()
        val text = line.substring(tags.last().range.last + 1).trim()
        tags.map { m ->
            val frac = m.groupValues[3]
            val ms = when (frac.length) { 0 -> 0; 1 -> frac.toInt() * 100; 2 -> frac.toInt() * 10; else -> frac.take(3).toInt() }
            LyricLine(m.groupValues[1].toLong() * 60_000 + m.groupValues[2].toLong() * 1000 + ms, text)
        }
    }.sortedBy { it.timeMs }
}
