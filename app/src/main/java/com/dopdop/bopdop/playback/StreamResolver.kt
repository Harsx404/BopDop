package com.dopdop.bopdop.playback

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.saavn.SaavnApi
import com.dopdop.bopdop.data.ytm.ResolvedStream
import com.dopdop.bopdop.data.ytm.YtMusic
import com.dopdop.bopdop.data.ytm.YtStreams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** What actually reached the speaker for a track (shown as the quality badge). */
data class StreamQuality(val codec: String, val kbps: Int, val via: String, val hiRes: Boolean) {
    val label: String get() = buildString {
        append(codec)
        if (kbps > 0) append(" · $kbps kbps")
    }
}

/**
 * Turns `bopdop://play?id=…` uris into real CDN urls at load time, so queue
 * items never carry expiring googlevideo links. Results are memoised until
 * shortly before they expire; a 403 mid-play invalidates and re-resolves.
 */
object StreamResolver {
    private const val TAG = "StreamResolver"
    const val SCHEME = "bopdop"

    private val cache = ConcurrentHashMap<String, ResolvedStream>()
    private val _quality = MutableStateFlow<Map<String, StreamQuality>>(emptyMap())
    val quality: StateFlow<Map<String, StreamQuality>> = _quality.asStateFlow()
    private var prefs: android.content.SharedPreferences? = null

    /** Cached replays never hit the resolver, so remember what each track streamed as. */
    fun init(context: android.content.Context) {
        if (prefs != null) return
        val p = context.getSharedPreferences("stream_quality", android.content.Context.MODE_PRIVATE)
        prefs = p
        val all = p.all
        if (all.size > 800) p.edit().clear().apply()
        _quality.value = all.mapNotNull { (k, v) ->
            val f = (v as? String)?.split('|') ?: return@mapNotNull null
            if (f.size < 4) null else k to StreamQuality(f[0], f[1].toIntOrNull() ?: 0, f[2], f[3] == "1")
        }.toMap()
    }

    fun playUri(t: Track): Uri = Uri.Builder().scheme(SCHEME).authority("play")
        .appendQueryParameter("id", t.id)
        .appendQueryParameter("t", t.title)
        .appendQueryParameter("a", t.artist)
        .appendQueryParameter("d", t.durationSec.toString())
        .apply { t.streamUrl?.let { appendQueryParameter("u", it) } }
        .build()

    fun invalidate(id: String) {
        val dead = cache.remove(id) ?: return
        if (dead.via != "JioSaavn" && dead.via != "PoToken") YtStreams.reportDirectFailure()
    }

    /** Blocking — called on ExoPlayer's loader thread. */
    fun resolve(uri: Uri): ResolvedStream {
        if (uri.authority == "watch") return VideoResolver.resolve(uri)
        val id = uri.getQueryParameter("id") ?: throw IOException("bad uri")
        cache[id]?.let { if (it.expiresAtMs - System.currentTimeMillis() > 5 * 60_000) return it }
        val s = when {
            id.startsWith("sv:") -> {
                val u = uri.getQueryParameter("u") ?: throw IOException("missing saavn url")
                ResolvedStream(u, null, if (u.contains("_320")) 320 else 160, "AAC", "JioSaavn", Long.MAX_VALUE)
            }
            else -> {
                val vid = id.removePrefix("yt:")
                val hi = if (LibraryStore.settings.hiRes) hiRes(uri) else null
                hi ?: YtStreams.resolve(vid)
            }
        }
        Log.i(TAG, "$id → ${s.via} ${s.codec} ${s.bitrate}kbps")
        cache[id] = s
        val q = StreamQuality(s.codec, s.bitrate, s.via, s.via == "JioSaavn" && !id.startsWith("sv:") || s.bitrate >= 320)
        _quality.update { it + (id to q) }
        prefs?.edit()?.putString(id, "${q.codec}|${q.kbps}|${q.via}|${if (q.hiRes) 1 else 0}")?.apply()
        return s
    }

    private fun hiRes(uri: Uri): ResolvedStream? = runCatching {
        runBlocking {
            withTimeoutOrNull(3500) {
                SaavnApi.hiResMatch(uri.getQueryParameter("t").orEmpty(), uri.getQueryParameter("a").orEmpty(), uri.getQueryParameter("d")?.toIntOrNull() ?: 0)
            }
        }
    }.getOrNull()?.streamUrl?.let { ResolvedStream(it, null, if (it.contains("_320")) 320 else 160, "AAC", "JioSaavn", Long.MAX_VALUE) }
}

// --- Track <-> MediaItem ---------------------------------------------------------------

private const val EXTRA_TRACK = "bopdop.track"

fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(StreamResolver.playUri(this))
    .setCustomCacheKey(id)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(Uri.parse(YtMusic.resize(artwork, 1000)))
            .setDurationMs(if (durationSec > 0) durationSec * 1000L else null)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setExtras(Bundle().apply { putString(EXTRA_TRACK, toJson().toString()) })
            .build(),
    )
    .build()

fun MediaItem.toTrack(): Track? = mediaMetadata.extras?.getString(EXTRA_TRACK)?.let {
    runCatching { Track.fromJson(JSONObject(it)) }.getOrNull()
}

/** Items arriving from a MediaController lose their uri; rebuild them from the embedded track. */
fun MediaItem.withPlaybackUri(): MediaItem = toTrack()?.toMediaItem()?.let { audio ->
    videoId?.let { audio.forVideo(it, hasSeparateVideoAudio, videoCap, songStartInVideo) } ?: audio
} ?: this
