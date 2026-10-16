package com.dopdop.bopdop.data.lyrics

import com.dopdop.bopdop.data.net.Http
import com.dopdop.bopdop.data.net.TtlCache
import org.json.JSONArray
import java.net.URLEncoder
import java.util.Locale

/**
 * Line-by-line lyric translation through Google's free, keyless "gtx" endpoint (the same one
 * the web app uses). Lines go newline-joined in chunks and come back aligned by position.
 */
object LyricsTranslate {
    private const val MAX_CHARS = 1500
    private val cache = TtlCache<String, List<String?>>(12 * 3600_000L, 60)

    /** The phone's language, which lyrics are translated into. */
    val target: String get() = Locale.getDefault().language.ifBlank { "en" }

    /** One entry per input line; null where a line is blank or couldn't be aligned. */
    suspend fun translate(key: String, lines: List<String>, to: String = target): List<String?> =
        cache.getOrLoad("$key|$to") {
            val out = arrayOfNulls<String>(lines.size)
            var start = 0
            while (start < lines.size) {
                var end = start
                var chars = 0
                while (end < lines.size && (end == start || chars + lines[end].length + 1 <= MAX_CHARS)) { chars += lines[end].length + 1; end++ }
                val chunk = lines.subList(start, end)
                runCatching { request(chunk.joinToString("\n"), to) }.getOrNull()?.let { translated ->
                    val back = translated.split("\n")
                    if (back.size == chunk.size) back.forEachIndexed { i, s -> out[start + i] = s.trim().takeIf { chunk[i].isNotBlank() && it.isNotEmpty() } }
                }
                start = end
            }
            out.toList()
        }

    private suspend fun request(text: String, to: String): String {
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$to&dt=t&q=" + URLEncoder.encode(text, "UTF-8")
        val d = JSONArray(Http.getText(url, mapOf("User-Agent" to "Mozilla/5.0")))
        val segs = d.optJSONArray(0) ?: return ""
        return buildString { for (i in 0 until segs.length()) segs.optJSONArray(i)?.optString(0)?.takeIf { it != "null" }?.let(::append) }
    }
}
