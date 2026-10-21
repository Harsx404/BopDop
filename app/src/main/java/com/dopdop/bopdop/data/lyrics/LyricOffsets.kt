package com.dopdop.bopdop.data.lyrics

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf

/**
 * Per-song lyrics timing nudges, in milliseconds. Positive shows the lyrics sooner (for lyrics
 * that lag behind the singing), negative later. Kept on the phone, one number per song.
 */
object LyricOffsets {
    const val STEP = 250L
    const val LIMIT = 10_000L

    private var prefs: SharedPreferences? = null
    /** Live values, so lyrics on screen move the moment the offset changes. */
    private val live = mutableStateMapOf<String, Long>()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("lyric_offsets", Context.MODE_PRIVATE)
        prefs = p
        p.all.forEach { (k, v) -> (v as? Long)?.let { live[k] = it } }
    }

    fun of(trackId: String): Long = live[trackId] ?: 0L

    fun set(trackId: String, ms: Long) {
        val v = ms.coerceIn(-LIMIT, LIMIT)
        if (v == 0L) live.remove(trackId) else live[trackId] = v
        prefs?.edit()?.apply { if (v == 0L) remove(trackId) else putLong(trackId, v) }?.apply()
    }

    fun nudge(trackId: String, by: Long) = set(trackId, of(trackId) + by)

    /** "+0.50 s" / "−0.25 s" / "In sync". */
    fun label(ms: Long): String =
        if (ms == 0L) "In sync" else (if (ms > 0) "+" else "−") + "%.2f s".format(kotlin.math.abs(ms) / 1000f)
}
