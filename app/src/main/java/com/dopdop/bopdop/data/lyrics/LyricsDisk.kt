package com.dopdop.bopdop.data.lyrics

import android.content.Context
import com.dopdop.bopdop.data.model.LyricLine
import com.dopdop.bopdop.data.model.LyricWord
import com.dopdop.bopdop.data.model.Lyrics
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Lyrics of downloaded songs, kept on the phone so they show offline. */
object LyricsDisk {
    private fun file(ctx: Context, id: String) =
        File(File(ctx.filesDir, "lyrics").apply { mkdirs() }, id.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json")

    fun has(ctx: Context, id: String) = file(ctx, id).exists()

    fun read(ctx: Context, id: String): Lyrics? = runCatching { decode(JSONObject(file(ctx, id).readText())) }.getOrNull()

    fun write(ctx: Context, id: String, l: Lyrics) { runCatching { file(ctx, id).writeText(encode(l).toString()) } }

    fun delete(ctx: Context, id: String) { file(ctx, id).delete() }

    // Lyrics picked by hand from the lyrics search (F44), kept apart from downloads.
    private fun pick(ctx: Context, id: String) =
        File(File(ctx.filesDir, "lyrics_picked").apply { mkdirs() }, id.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json")

    fun hasPick(ctx: Context, id: String) = pick(ctx, id).exists()
    fun readPick(ctx: Context, id: String): Lyrics? = runCatching { decode(JSONObject(pick(ctx, id).readText())) }.getOrNull()
    fun writePick(ctx: Context, id: String, l: Lyrics) { runCatching { pick(ctx, id).writeText(encode(l).toString()) } }
    fun deletePick(ctx: Context, id: String) { pick(ctx, id).delete() }

    internal fun encode(l: Lyrics): JSONObject = JSONObject().put("plain", l.plain).put("synced", JSONArray().apply {
        l.synced.forEach { line ->
            put(JSONObject().put("t", line.timeMs).put("x", line.text).apply {
                if (line.words.isNotEmpty()) put("w", JSONArray().apply { line.words.forEach { put(JSONObject().put("t", it.timeMs).put("x", it.text)) } })
            })
        }
    })

    internal fun decode(o: JSONObject): Lyrics {
        val a = o.optJSONArray("synced") ?: JSONArray()
        val lines = (0 until a.length()).map { i ->
            val l = a.getJSONObject(i)
            val w = l.optJSONArray("w")
            LyricLine(l.getLong("t"), l.getString("x"), if (w == null) emptyList() else (0 until w.length()).map { j ->
                w.getJSONObject(j).let { LyricWord(it.getLong("t"), it.getString("x")) }
            })
        }
        return Lyrics(lines, o.optString("plain").takeIf { o.has("plain") && !o.isNull("plain") })
    }
}
