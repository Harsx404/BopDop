package com.dopdop.bopdop.playback

import android.content.Context
import androidx.media3.common.Player
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.model.toJsonArray
import com.dopdop.bopdop.data.model.toTracks
import org.json.JSONObject
import java.io.File

/** What was playing when the app was last closed: the queue, where in it, and how far into the song. */
data class SavedQueue(val tracks: List<Track>, val index: Int, val positionMs: Long, val context: String)

/**
 * Keeps the queue on disk so closing the app (or Android stopping it) loses nothing: the same
 * songs, the same song, the same second. Music videos are saved as their songs.
 */
object QueueStore {
    private const val MAX = 500
    private var file: File? = null
    @Volatile private var lastWritten = ""

    fun init(context: Context) {
        if (file == null) file = File(context.filesDir, "queue.json")
    }

    fun load(): SavedQueue? = runCatching {
        val o = JSONObject(file?.readText() ?: return null)
        val tracks = o.optJSONArray("tracks").toTracks()
        if (tracks.isEmpty()) null
        else SavedQueue(tracks, o.optInt("index").coerceIn(0, tracks.lastIndex), o.optLong("position").coerceAtLeast(0), o.optString("context"))
    }.getOrNull()

    /** Write the player's queue now (cheap to call often: an unchanged queue only updates the position). */
    fun save(p: Player, context: String) {
        val f = file ?: return
        val n = p.mediaItemCount
        if (n == 0) { f.delete(); lastWritten = ""; return }
        // Very long queues keep the part around the current song.
        val from = (p.currentMediaItemIndex - MAX / 2).coerceIn(0, maxOf(0, n - MAX))
        val until = minOf(n, from + MAX)
        // JioSaavn and on-phone songs keep their own address; YouTube ones are found again at play time.
        val tracks = (from until until).mapNotNull { p.getMediaItemAt(it).toTrack() }
        val o = JSONObject()
            .put("tracks", tracks.toJsonArray())
            .put("index", p.currentMediaItemIndex - from)
            .put("position", p.currentPosition)
            .put("context", context)
        runCatching {
            val text = o.toString()
            if (text == lastWritten) return
            val tmp = File(f.parentFile, "queue.json.tmp")
            tmp.writeText(text)
            tmp.renameTo(f)
            lastWritten = text
        }
    }
}
