package com.dopdop.bopdop.playback

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.dopdop.bopdop.data.AudioQuality
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
    private var appContext: android.content.Context? = null

    /** Cached replays never hit the resolver, so remember what each track streamed as. */
    fun init(context: android.content.Context) {
        if (prefs != null) return
        appContext = context.applicationContext
        val p = context.getSharedPreferences("stream_quality", android.content.Context.MODE_PRIVATE)
        prefs = p
        val all = p.all
        if (all.size > 800) p.edit().clear().apply()
        _quality.value = all.mapNotNull { (k, v) ->
            val f = (v as? String)?.split('|') ?: return@mapNotNull null
            if (f.size < 4) null else k to StreamQuality(f[0], f[1].toIntOrNull() ?: 0, f[2], f[3] == "1")
        }.toMap()
    }

    /**
     * Once, after Hi-Res matching became strict: YouTube songs whose audio came from a JioSaavn
     * match may hold the wrong version ("Alfaaz - Vocals") in the play cache under their own id.
     * Drop those so the next play matches again. Downloads are left alone.
     */
    fun forgetLooseHiResMatches(context: android.content.Context, cache: androidx.media3.datasource.cache.Cache) {
        val p = prefs ?: return
        val flags = context.getSharedPreferences("migrations", android.content.Context.MODE_PRIVATE)
        if (flags.getBoolean("strictHiRes", false)) return
        val stale = p.all.filter { (k, v) -> k.startsWith("yt:") && (v as? String)?.split('|')?.getOrNull(2) == "JioSaavn" }.keys
        stale.forEach { id -> runCatching { cache.removeResource(id) } }
        p.edit().apply { stale.forEach(::remove) }.apply()
        _quality.update { it - stale }
        flags.edit().putBoolean("strictHiRes", true).apply()
        Log.i(TAG, "dropped ${stale.size} cached Hi-Res matches")
    }

    fun playUri(t: Track): Uri = Uri.Builder().scheme(SCHEME).authority("play")
        .appendQueryParameter("id", t.id)
        .appendQueryParameter("t", t.title)
        .appendQueryParameter("a", t.artist)
        .appendQueryParameter("d", t.durationSec.toString())
        .apply { t.streamUrl?.let { appendQueryParameter("u", it) } }
        .build()

    /** A still-valid stream already resolved for [t] — or its own JioSaavn url. Never blocks. */
    fun peek(t: Track): ResolvedStream? {
        cache[t.id]?.takeIf { it.expiresAtMs - System.currentTimeMillis() > 5 * 60_000 }?.let { return it }
        return t.streamUrl?.takeIf { t.id.startsWith("sv:") }?.let { ResolvedStream(it, null, if (it.contains("_320")) 320 else 160, "AAC", "JioSaavn", Long.MAX_VALUE) }
    }

    fun invalidate(id: String) {
        val dead = cache.remove(id) ?: return
        if (dead.via != "JioSaavn" && dead.via != "PoToken") YtStreams.reportDirectFailure()
    }

    /** An ExoPlayer request for a bopdop:// song, pointed at its real stream url (with the user agent it needs). */
    fun resolveSpec(spec: androidx.media3.datasource.DataSpec): androidx.media3.datasource.DataSpec {
        if (spec.uri.scheme != SCHEME) return spec
        val s = resolve(spec.uri)
        val headers = HashMap(spec.httpRequestHeaders)
        s.userAgent?.let { headers["User-Agent"] = it }
        return spec.buildUpon().setUri(s.url).setHttpRequestHeaders(headers).build()
    }

    /** Blocking — called on ExoPlayer's loader thread. */
    fun resolve(uri: Uri): ResolvedStream {
        if (uri.authority == "watch") return VideoResolver.resolve(uri)
        val id = uri.getQueryParameter("id") ?: throw IOException("bad uri")
        // Wi-Fi and mobile data each have their own quality (downloads ask for one with ?q=);
        // a resolved url is reused only at the same quality.
        val quality = uri.getQueryParameter("q")?.let { runCatching { AudioQuality.valueOf(it) }.getOrNull() } ?: VideoQualityPolicy.audio()
        val key = "$id@${quality.name}"
        cache[key]?.let { if (it.expiresAtMs - System.currentTimeMillis() > 5 * 60_000) return it }
        // Lossless (and Atmos) from the user's add-ons first, when on; any miss falls through.
        val lossless = if (LibraryStore.settings.lossless && quality == AudioQuality.HIGH) addon(uri, id) else null
        val s = lossless ?: when {
            id.startsWith("sv:") -> {
                val u = saavnAt(uri.getQueryParameter("u") ?: throw IOException("missing saavn url"), quality)
                ResolvedStream(u, null, saavnKbps(u), "AAC", "JioSaavn", Long.MAX_VALUE)
            }
            else -> {
                val vid = id.removePrefix("yt:")
                // The 320 kbps JioSaavn match is the High quality option; lower ones stay on YouTube.
                val hi = if (LibraryStore.settings.hiRes && quality == AudioQuality.HIGH) hiRes(uri) else null
                hi ?: YtStreams.resolve(vid, quality.maxKbps)
            }
        }
        Log.i(TAG, "$id → ${s.via} ${s.codec} ${s.bitrate}kbps (${quality.label})")
        cache[key] = s
        cache[id] = s
        val q = StreamQuality(s.codec, s.bitrate, s.via, lossless != null || s.via == "JioSaavn" && !id.startsWith("sv:") || s.bitrate >= 320)
        _quality.update { it + (id to q) }
        prefs?.edit()?.putString(id, "${q.codec}|${q.kbps}|${q.via}|${if (q.hiRes) 1 else 0}")?.apply()
        return s
    }

    /** A JioSaavn file url at [q]'s bitrate (their files differ only by the _96 / _160 / _320 suffix). */
    internal fun saavnAt(url: String, q: AudioQuality): String =
        if (q == AudioQuality.HIGH) url else url.replace(Regex("_(96|160|320)\\."), "${q.saavn}.")

    private fun saavnKbps(url: String) = when { url.contains("_320") -> 320; url.contains("_96.") -> 96; else -> 160 }

    /** The same recording from an add-on, as "FLAC 24/96" or "Dolby Atmos", within 6 s. Downloads ask with ?q=. */
    private fun addon(uri: Uri, id: String): ResolvedStream? {
        val ctx = appContext ?: return null
        val t = Track(id, uri.getQueryParameter("t").orEmpty(), uri.getQueryParameter("a").orEmpty(), durationSec = uri.getQueryParameter("d")?.toIntOrNull() ?: 0)
        val download = uri.getQueryParameter("q") != null
        val (s, name) = runCatching { runBlocking { withTimeoutOrNull(6000) { com.dopdop.bopdop.data.addon.Addons.resolve(ctx, t, download) } } }.getOrNull() ?: return null
        val codec = when {
            s.atmos -> "Dolby Atmos"
            s.bitDepth > 0 && s.sampleRate > 0 -> "${s.codec} ${s.bitDepth}/${if (s.sampleRate % 1000 == 0) s.sampleRate / 1000 else "%.1f".format(s.sampleRate / 1000f)}"
            else -> s.codec
        }
        return ResolvedStream(s.url, null, s.kbps, codec, name, System.currentTimeMillis() + 30 * 60_000)
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
    .setUri(if (source == com.dopdop.bopdop.data.model.Source.LOCAL && streamUrl != null) Uri.parse(streamUrl) else StreamResolver.playUri(this))
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
    videoId?.let { audio.forVideo(it, hasSeparateVideoAudio, videoCap, songStartInVideo, videoSeconds) } ?: audio
} ?: this
