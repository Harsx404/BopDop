package com.dopdop.bopdop.data

import android.content.Context
import android.net.Uri
import com.dopdop.bopdop.data.lyrics.LyricOffsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

/** What a backup file holds, shown before it replaces anything. */
data class BackupSummary(val createdAt: Long, val liked: Int, val playlists: Int, val plays: Int)

/**
 * Library, settings, listening history and lyric timings in one JSON file the user keeps. The
 * YouTube Music sign-in and the ListenBrainz token stay on the phone: a backup is a file people
 * move around, and those are passwords in all but name.
 */
object Backup {
    private const val FORMAT = "bopdop-backup"
    const val VERSION = 1

    fun fileName() = "BopDop-backup-${LocalDate.now()}.json"

    fun build(now: Long = System.currentTimeMillis()): JSONObject {
        val library = LibraryStore.toJson()
        library.optJSONObject("settings")?.apply {
            listOf("listenBrainzToken", "listenBrainzUser", "lastFmSession", "lastFmUser", "lastFmKey", "lastFmSecret").forEach(::remove)
        }
        return JSONObject()
            .put("format", FORMAT).put("version", VERSION).put("createdAt", now)
            .put("library", library)
            .put("plays", PlayStats.toJson())
            .put("shorts", ShortsStore.toJson())
            .put("lyricOffsets", JSONObject().also { o -> LyricOffsets.all().forEach { (k, v) -> o.put(k, v) } })
    }

    /** Null when the file isn't a BopDop backup (or is from a newer app). */
    fun parse(text: String): JSONObject? = runCatching { JSONObject(text) }.getOrNull()
        ?.takeIf { it.optString("format") == FORMAT && it.optInt("version") in 1..VERSION && it.optJSONObject("library") != null }

    fun summary(o: JSONObject) = BackupSummary(
        createdAt = o.optLong("createdAt"),
        liked = o.optJSONObject("library")?.optJSONArray("liked")?.length() ?: 0,
        playlists = o.optJSONObject("library")?.optJSONArray("playlists")?.length() ?: 0,
        plays = o.optJSONObject("plays")?.optJSONArray("events")?.length() ?: 0,
    )

    suspend fun write(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val text = build().toString()
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: error("Couldn't open the file")
    }

    suspend fun read(context: Context, uri: Uri): JSONObject? = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }?.let(::parse)
    }

    /** Replace what's on the phone with the backup. */
    fun restore(o: JSONObject) {
        LibraryStore.restore(o.getJSONObject("library"))
        o.optJSONObject("plays")?.let(PlayStats::restore)
        o.optJSONObject("shorts")?.let(ShortsStore::restore)
        o.optJSONObject("lyricOffsets")?.let { m -> LyricOffsets.replaceAll(m.keys().asSequence().associateWith { m.optLong(it) }) }
    }
}
