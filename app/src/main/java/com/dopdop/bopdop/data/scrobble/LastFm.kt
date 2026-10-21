package com.dopdop.bopdop.data.scrobble

import android.content.Context
import android.util.Log
import com.dopdop.bopdop.BuildConfig
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Scrobbling to Last.fm (or Libre.fm, which speaks the same API).
 *
 * Signing in is Last.fm's web approval: BopDop asks for a token, the user allows BopDop on
 * last.fm in the browser, and the token is traded for a session key. No password passes through
 * the app. The API key and secret come from the build (local.properties `lastfm.key` /
 * `lastfm.secret`, or the LASTFM_API_KEY / LASTFM_SECRET environment in CI), or from the user's
 * own pasted in Settings when the build has none.
 *
 * A song counts after half its length or four minutes, and only if it's longer than 30 s
 * (Last.fm's rule). Scrobbles that can't be sent wait in a file, up to 50 per request.
 */
object LastFm {
    const val LASTFM = "https://ws.audioscrobbler.com/2.0/"
    const val LIBREFM = "https://libre.fm/2.0/"
    private const val BATCH = 50
    private const val KEEP = 2000
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var pending: File? = null

    private var trackId: String? = null
    private var startedAt = 0L
    private var heardSec = 0
    private var counted = false

    fun init(context: Context) {
        if (pending == null) pending = File(context.filesDir, "lastfm_pending.json")
    }

    private val s get() = LibraryStore.settings
    val endpoint get() = if (s.lastFmLibre) LIBREFM else LASTFM
    val apiKey get() = s.lastFmKey.ifBlank { BuildConfig.LASTFM_KEY }
    private val secret get() = s.lastFmSecret.ifBlank { BuildConfig.LASTFM_SECRET }

    /** Libre.fm takes any key; Last.fm needs a real one from last.fm/api/account/create. */
    val hasKeys get() = s.lastFmLibre || (apiKey.isNotBlank() && secret.isNotBlank())
    private val signedIn get() = s.lastFmSession.isNotBlank()

    /** Last.fm's rule: half the song or four minutes; songs of 30 s or less never count. */
    internal fun needed(durationSec: Int) = if (durationSec > 0) minOf(durationSec / 2, 240) else 240

    fun onTrack(t: Track?) {
        trackId = t?.id
        startedAt = System.currentTimeMillis() / 1000
        heardSec = 0
        counted = false
        if (t == null || !signedIn) return
        scope.launch {
            runCatching { call("track.updateNowPlaying", trackParams(t, null), post = true) }
                .onFailure { Log.i("LastFm", "now playing: ${it.message}") }
        }
    }

    fun tick(t: Track) {
        if (t.id != trackId) onTrack(t)
        if (counted || !signedIn || t.durationSec in 1..30) return
        if (++heardSec < needed(t.durationSec)) return
        counted = true
        val scrobble = JSONObject(trackParams(t, startedAt))
        scope.launch { queueAndFlush(scrobble) }
    }

    fun flush() {
        if (signedIn) scope.launch { queueAndFlush(null) }
    }

    // --- Sign-in ----------------------------------------------------------------------

    /** Step 1: a request token, and the page where the user allows BopDop. */
    suspend fun startAuth(): Pair<String, String> = withContext(Dispatchers.IO) {
        val token = call("auth.getToken", emptyMap(), post = false).getString("token")
        val site = if (s.lastFmLibre) "https://libre.fm/api/auth/" else "https://www.last.fm/api/auth/"
        token to "$site?api_key=$apiKey&token=$token"
    }

    /** Step 2, after the user allowed it: the session key and user name. Null while not yet allowed. */
    suspend fun finishAuth(token: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        runCatching {
            val sess = call("auth.getSession", mapOf("token" to token), post = false).getJSONObject("session")
            sess.getString("key") to sess.optString("name")
        }.getOrNull()
    }

    // --- Wire ---------------------------------------------------------------------------

    private fun trackParams(t: Track, timestamp: Long?, index: Int? = null): Map<String, String> {
        val k = { name: String -> if (index == null) name else "$name[$index]" }
        return buildMap {
            put(k("artist"), t.artist.split(",").first().trim())
            put(k("track"), t.title)
            if (t.album.isNotBlank()) put(k("album"), t.album)
            if (t.durationSec > 0) put(k("duration"), t.durationSec.toString())
            if (timestamp != null) put(k("timestamp"), timestamp.toString())
        }
    }

    private fun queueAndFlush(scrobble: JSONObject?) = synchronized(lock) {
        val file = pending ?: return@synchronized
        val queue = runCatching { JSONArray(file.readText()) }.getOrDefault(JSONArray())
        if (scrobble != null) queue.put(scrobble)
        var sent = 0
        try {
            while (sent < queue.length()) {
                val params = HashMap<String, String>()
                val end = minOf(queue.length(), sent + BATCH)
                for (i in sent until end) {
                    val o = queue.getJSONObject(i)
                    o.keys().forEach { key -> params["$key[${i - sent}]"] = o.getString(key) }
                }
                call("track.scrobble", params, post = true)
                sent = end
            }
        } catch (e: Exception) {
            Log.i("LastFm", "keeping ${queue.length() - sent} scrobbles for later: ${e.message}")
        }
        val left = JSONArray()
        for (i in maxOf(sent, queue.length() - KEEP) until queue.length()) left.put(queue.get(i))
        if (left.length() == 0) file.delete() else file.writeText(left.toString())
    }

    /** Signed API call. Session calls carry `sk`; every call is signed with the secret. */
    private fun call(method: String, params: Map<String, String>, post: Boolean): JSONObject {
        val all = HashMap(params)
        all["method"] = method
        all["api_key"] = apiKey.ifBlank { "bopdop" }
        if (method != "auth.getToken" && method != "auth.getSession") all["sk"] = s.lastFmSession
        all["api_sig"] = sign(all, secret)
        all["format"] = "json"
        val req = if (post) {
            Request.Builder().url(endpoint).post(FormBody.Builder().apply { all.forEach { (k, v) -> add(k, v) } }.build())
        } else {
            val q = all.entries.joinToString("&") { (k, v) -> k + "=" + java.net.URLEncoder.encode(v, "UTF-8") }
            Request.Builder().url("$endpoint?$q")
        }.header("User-Agent", "BopDop/${BuildConfig.VERSION_NAME}").build()
        Http.client.newCall(req).execute().use { r ->
            val o = JSONObject(r.body?.string().orEmpty().ifBlank { "{}" })
            if (o.has("error")) throw java.io.IOException("Last.fm ${o.optInt("error")}: ${o.optString("message")}")
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            return o
        }
    }

    /** api_sig: every parameter but format/callback, sorted by name, name+value concatenated, then the secret, md5. */
    internal fun sign(params: Map<String, String>, secret: String): String {
        val text = params.filterKeys { it != "format" && it != "callback" }.toSortedMap()
            .entries.joinToString("") { it.key + it.value } + secret
        return MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
