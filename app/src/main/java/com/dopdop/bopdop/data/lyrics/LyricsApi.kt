package com.dopdop.bopdop.data.lyrics

import android.util.Log
import com.dopdop.bopdop.data.model.LyricLine
import com.dopdop.bopdop.data.model.LyricWord
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
 *  1. YouTube Music's own timed lyrics — exact for its tracks, strong on Bollywood;
 *  2. lrc.red — 29.8M tracks, many timed word by word; search by title, fetch the LRC by ISRC;
 *  3. LRCLIB — time-synced, one search call per song (it rate-limits hard);
 *  4. YouTube Music — LyricFind text;
 *  5. JioSaavn — its own lyric sheets for Saavn tracks.
 * A timed source always wins over plain text from an earlier one.
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

    private var appContext: android.content.Context? = null
    fun init(context: android.content.Context) { appContext = context.applicationContext }

    suspend fun get(t: Track): Lyrics? {
        cache.get(t.id)?.let { return it }
        // Downloaded songs keep their lyrics on the phone.
        appContext?.let { ctx -> LyricsDisk.read(ctx, t.id) }?.let { cache.put(t.id, it); return it }
        if (misses.get(t.id) == true) return null
        // lrc.red first: when it times every word, that beats line-timed lyrics for the karaoke sweep.
        val red = runCatching { lrcRed(t) }.onFailure { Log.w(TAG, "lrc.red: ${it.message}") }.getOrNull()
        // 1) YouTube Music's own timed lyrics — exact for YT tracks, and they cover Bollywood.
        val vid = runCatching { videoIdFor(t) }.getOrNull()
        val timed = if (red?.isWordSynced == true) null else vid?.let { v -> runCatching { YtMusic.timedLyrics(v) }.onFailure { Log.w(TAG, "ytm timed: ${it.message}") }.getOrNull() }
            ?.let { lines -> Lyrics(lines.map { (s, _, text) -> LyricLine(s, text) }, null) }
        val result = red?.takeIf { it.isWordSynced }
            ?: timed
            // 2) lrc.red line-timed.
            ?: red?.takeIf { it.isSynced }
            // 3) LRCLIB synced, then any plain text.
            ?: runCatching { lrclib(t) }.onFailure { Log.w(TAG, "lrclib: ${it.message}") }.getOrNull()?.let { l ->
                if (l.isSynced) l else (red ?: vid?.let { runCatching { YtMusic.lyrics(it) }.getOrNull() }?.let { Lyrics(emptyList(), it) } ?: l)
            }
            ?: red
            ?: vid?.let { runCatching { YtMusic.lyrics(it) }.getOrNull() }?.let { Lyrics(emptyList(), it) }
            ?: runCatching { saavn(t) }.getOrNull()
        if (result == null) misses.put(t.id, true) else cache.put(t.id, result)
        return result
    }

    /** How well a catalogue hit (title, artists, length in seconds) fits [t]; 4 and up is a match. */
    private fun matchScore(t: Track, title: String, artist: String, durationSec: Int): Double {
        val nt = norm(cleanTitle(t.title))
        val wanted = t.artist.split(',', '&').map { norm(it.trim()) }.filter { it.isNotEmpty() }
        var score = 0.0
        val ot = norm(cleanTitle(title))
        score += when { ot == nt -> 3.0; ot.contains(nt) || nt.contains(ot) -> 1.5; else -> -2.0 }
        val oa = norm(artist)
        // Credits differ across sources (composer vs singer) — any shared name counts.
        if (wanted.any { w -> oa.contains(w) || w.contains(oa) }) score += 3.0
        if (t.durationSec > 0 && durationSec > 0) {
            val diff = kotlin.math.abs(durationSec - t.durationSec)
            score += when { diff <= 3 -> 2.5; diff <= 8 -> 1.0; diff > 20 -> -3.0; else -> 0.0 }
        }
        return score
    }

    /** lrc.red: search by title and lead artist, then fetch the best hit's LRC by its ISRC. */
    private suspend fun lrcRed(t: Track): Lyrics? {
        val title = cleanTitle(t.title)
        if (title.isEmpty()) return null
        val lead = t.artist.split(',', '&').first().trim()
        val hits = JSONObject(Http.getText("https://lrc.red/search.json?q=${enc("$title $lead")}", headers)).optJSONArray("hits") ?: return null
        val best = (0 until hits.length()).mapNotNull { hits.optJSONObject(it) }
            .map { it to matchScore(t, it.optString("title"), it.optString("artist"), it.optDouble("duration", 0.0).toInt()) }
            .filter { it.second >= 4.0 }.maxByOrNull { it.second }?.first ?: return null
        val isrc = best.optString("isrc").takeIf { it.matches(Regex("[A-Z0-9]{12}")) } ?: return null
        val lrc = Http.getText("https://lrc.red/s/$isrc.lrc", headers)
        val synced = parseLrc(lrc)
        if (synced.isNotEmpty()) return Lyrics(synced, null)
        // Untimed sheet: the text lines, without the [ti:]/[ar:] header tags.
        val plain = lrc.lines().filterNot { it.trim().matches(Regex("\\[[a-z]+:.*]")) }.joinToString("\n").trim()
        return if (plain.isEmpty()) null else Lyrics(emptyList(), plain)
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
    private val WORD_RE = Regex("<\\d+:\\d+(?:[.:]\\d+)?>")

    private fun ms(min: String, sec: String, frac: String): Long =
        min.toLong() * 60_000 + sec.toLong() * 1000 + when (frac.length) { 0 -> 0; 1 -> frac.toInt() * 100; 2 -> frac.toInt() * 10; else -> frac.take(3).toInt() }

    private val WORD_TAG = Regex("<(\\d+):(\\d+)(?:[.:](\\d+))?>")

    /**
     * LRC to timed lines. Enhanced (word-timed) LRC — "<00:01.28>hai <00:01.51>kareya" — also
     * fills each line's [LyricLine.words], so the karaoke sweep follows the real singing.
     */
    fun parseLrc(lrc: String): List<LyricLine> = lrc.lines().flatMap { line ->
        val tags = TAG_RE.findAll(line).toList()
        if (tags.isEmpty()) return@flatMap emptyList()
        val body = line.substring(tags.last().range.last + 1)
        val text = body.replace(WORD_RE, "").replace(Regex("\\s+"), " ").trim()
        val words = words(body)
        tags.map { m -> LyricLine(ms(m.groupValues[1], m.groupValues[2], m.groupValues[3]), text, words) }
    }.sortedBy { it.timeMs }

    /** The words of an enhanced-LRC line with their start times; empty when the line isn't word-timed. */
    private fun words(body: String): List<LyricWord> {
        val marks = WORD_TAG.findAll(body).toList()
        if (marks.size < 2) return emptyList()
        val out = ArrayList<LyricWord>()
        marks.forEachIndexed { i, m ->
            val end = marks.getOrNull(i + 1)?.range?.first ?: body.length
            val piece = body.substring(m.range.last + 1, end)
            if (piece.isNotBlank()) out += LyricWord(ms(m.groupValues[1], m.groupValues[2], m.groupValues[3]), piece)
        }
        if (out.isEmpty()) return out
        // Trim the outer spaces the way the line text is trimmed, so the words join back into it.
        out[0] = out[0].copy(text = out[0].text.trimStart())
        out[out.lastIndex] = out.last().copy(text = out.last().text.trimEnd())
        return out
    }
}
