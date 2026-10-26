package com.dopdop.bopdop.data.ytm

import android.util.Log
import com.dopdop.bopdop.data.account.AccountStore
import com.dopdop.bopdop.data.model.Source
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONObject
import java.security.SecureRandom

/**
 * Two-way sync, listening side: songs played in BopDop go into your YouTube Music history, the way
 * the YouTube Music app reports them, so its Quick picks, Listen again and mixes learn from what you
 * play here. Only when signed in and on in Settings, once per song played for 30 s (or half a shorter song). JioSaavn
 * and on-phone songs are reported as their YouTube Music copy when they have one.
 */
object YtHistory {
    private const val TAG = "YtHistory"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trackId: String? = null
    private var heard = 0
    private var sent = false
    @Volatile private var unsynced = 0

    /** Plays reported since Home last loaded; Home refreshes when this grows (see HomeScreen). */
    val sinceRefresh get() = unsynced
    fun refreshed() { unsynced = 0 }

    /** One more second of [t] heard (called only while it plays). */
    fun tick(t: Track) {
        if (t.id != trackId) { trackId = t.id; heard = 0; sent = false }
        if (sent || !AccountStore.signedIn || !com.dopdop.bopdop.data.LibraryStore.settings.ytHistory) return
        heard++
        val need = if (t.durationSec in 1..59) (t.durationSec / 2).coerceAtLeast(5) else 30
        if (heard < need) return
        sent = true
        scope.launch {
            runCatching {
                val vid = when (t.source) {
                    Source.YTM -> t.rawId
                    else -> YtMusic.searchSongs("${t.title} ${t.artist.split(",").first()}").firstOrNull {
                        com.dopdop.bopdop.data.model.SongVersion.sameRecording(t, it)
                    }?.rawId ?: return@runCatching
                }
                report(vid)
                unsynced++
                Log.i(TAG, "added to YouTube Music history: ${t.title}")
            }.onFailure { Log.w(TAG, "history: ${it.message}") }
        }
    }

    /** What the YouTube Music web player does: ask the player for its tracking url, then ping it. */
    private suspend fun report(videoId: String) {
        val player = YtMusic.post("player", JSONObject().put("videoId", videoId), cacheable = false)
        val base = player.optJSONObject("playbackTracking")?.optJSONObject("videostatsPlaybackUrl")?.optString("baseUrl")
            ?.ifBlank { null } ?: error("no tracking url")
        val cpn = (1..16).map { CPN[rng.nextInt(CPN.length)] }.joinToString("")
        val url = base + (if ('?' in base) "&" else "?") + "ver=2&c=WEB_REMIX&cpn=$cpn"
        val req = Request.Builder().url(url).header("User-Agent", YtMusic.WEB_UA)
            .apply { AccountStore.authHeaders().forEach { (k, v) -> header(k, v) } }.build()
        Http.client.newCall(req).execute().use { if (it.code !in 200..299 && it.code != 204) error("HTTP ${it.code}") }
    }

    private const val CPN = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private val rng = SecureRandom()
}
