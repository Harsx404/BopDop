package com.dopdop.bopdop.data.scrobble

import android.content.Context
import android.util.Log
import com.dopdop.bopdop.BuildConfig
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.model.Source
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Scrobbling to ListenBrainz with the user's own token. A song counts once it has really been
 * heard for half its length or four minutes, whichever comes first (ListenBrainz's rule).
 * Listens that can't be sent (offline, server down) wait in a file and go with the next one.
 */
object ListenBrainz {
    private const val API = "https://api.listenbrainz.org/1"
    /** ListenBrainz takes at most this many listens per import request. */
    private const val BATCH = 500
    private const val KEEP = 2000
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var pending: File? = null

    private var trackId: String? = null
    private var startedAt = 0L
    private var heardSec = 0
    private var counted = false

    fun init(context: Context) {
        if (pending == null) pending = File(context.filesDir, "listenbrainz_pending.json")
    }

    private val token get() = LibraryStore.settings.listenBrainzToken

    /** Seconds of listening a song needs before it counts. */
    internal fun needed(durationSec: Int) = if (durationSec > 0) minOf(durationSec / 2, 240).coerceAtLeast(1) else 240

    /** A new song started: start counting, and tell ListenBrainz what's playing now. */
    fun onTrack(t: Track?) {
        trackId = t?.id
        startedAt = System.currentTimeMillis() / 1000
        heardSec = 0
        counted = false
        if (t == null || token.isBlank()) return
        val now = JSONArray().put(JSONObject().put("track_metadata", metadata(t)))
        scope.launch { runCatching { submit("playing_now", now) }.onFailure { Log.i("ListenBrainz", "playing_now: ${it.message}") } }
    }

    /** One more second of [t] heard (called only while it's actually playing). */
    fun tick(t: Track) {
        if (t.id != trackId) onTrack(t)
        if (counted || token.isBlank()) return
        if (++heardSec < needed(t.durationSec)) return
        counted = true
        val listen = JSONObject().put("listened_at", startedAt).put("track_metadata", metadata(t))
        scope.launch { queueAndFlush(listen) }
    }

    /** The account a token belongs to, or null when ListenBrainz says it's not valid. */
    suspend fun validate(token: String): String? {
        val o = Http.getJson("$API/validate-token", mapOf("Authorization" to "Token ${token.trim()}"))
        return if (o.optBoolean("valid")) o.optString("user_name").ifBlank { null } else null
    }

    private fun queueAndFlush(listen: JSONObject?) = synchronized(lock) {
        val file = pending ?: return@synchronized
        val queue = runCatching { JSONArray(file.readText()) }.getOrDefault(JSONArray())
        if (listen != null) queue.put(listen)
        var sent = 0
        try {
            while (sent < queue.length()) {
                val batch = JSONArray()
                for (i in sent until minOf(queue.length(), sent + BATCH)) batch.put(queue.get(i))
                submit(if (batch.length() == 1) "single" else "import", batch)
                sent += batch.length()
            }
        } catch (e: Exception) {
            Log.i("ListenBrainz", "keeping ${queue.length() - sent} listens for later: ${e.message}")
        }
        val left = JSONArray()
        for (i in maxOf(sent, queue.length() - KEEP) until queue.length()) left.put(queue.get(i))
        if (left.length() == 0) file.delete() else file.writeText(left.toString())
    }

    /** Send what was waiting (after connecting, or when the app starts). */
    fun flush() {
        if (token.isNotBlank()) scope.launch { queueAndFlush(null) }
    }

    private fun submit(type: String, payload: JSONArray) {
        val body = JSONObject().put("listen_type", type).put("payload", payload)
        val req = Request.Builder().url("$API/submit-listens")
            .header("Authorization", "Token $token")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        Http.client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw java.io.IOException("HTTP ${res.code}")
        }
    }

    internal fun metadata(t: Track): JSONObject {
        val info = JSONObject()
            .put("media_player", "BopDop")
            .put("submission_client", "BopDop")
            .put("submission_client_version", BuildConfig.VERSION_NAME)
        if (t.durationSec > 0) info.put("duration_ms", t.durationSec * 1000L)
        when (t.source) {
            Source.YTM -> info.put("music_service", "music.youtube.com").put("origin_url", "https://music.youtube.com/watch?v=${t.id.removePrefix("yt:")}")
            Source.SAAVN -> info.put("music_service", "jiosaavn.com")
            Source.SPOTIFY -> info.put("music_service", "spotify.com").put("origin_url", "https://open.spotify.com/track/${t.rawId}")
            Source.LOCAL -> {}
        }
        return JSONObject().put("artist_name", t.artist).put("track_name", t.title).put("additional_info", info).apply {
            if (t.album.isNotBlank()) put("release_name", t.album)
        }
    }
}
