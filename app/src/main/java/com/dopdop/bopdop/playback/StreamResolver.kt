package com.dopdop.bopdop.playback

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.dopdop.bopdop.data.AudioQuality
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.Settings
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.saavn.SaavnApi
import com.dopdop.bopdop.data.ytm.ResolvedStream
import com.dopdop.bopdop.data.ytm.YtMusic
import com.dopdop.bopdop.data.ytm.YtStreams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
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

    // Add-on copies, looked up in the background (see [lookUpAddon]): a song never waits on them.
    /** An add-on copy and the add-on (base url) it came from. */
    private class AddonHit(val base: String, val stream: ResolvedStream)
    private val addonHits = ConcurrentHashMap<String, AddonHit>()
    private val addonMisses = ConcurrentHashMap<String, Long>()
    private val addonLookups = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<ResolvedStream?>>()
    private val _addonNotes = MutableStateFlow<Map<String, String>>(emptyMap())
    /** What the add-ons did for each song lately ("Searching…", "Switched", why not), shown in Settings › Sources. */
    val addonNotes: StateFlow<Map<String, String>> = _addonNotes.asStateFlow()
    fun note(id: String, msg: String) = _addonNotes.update { (it + (id to msg)).entries.toList().takeLast(30).associate { e -> e.key to e.value } }

    private val addonScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
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

    /** [addon]: play the add-on copy found by [lookUpAddon] instead of the usual stream. */
    fun playUri(t: Track, addon: Boolean = false): Uri = Uri.Builder().scheme(SCHEME).authority("play")
        .appendQueryParameter("id", t.id)
        .appendQueryParameter("t", t.title)
        .appendQueryParameter("a", t.artist)
        .appendQueryParameter("d", t.durationSec.toString())
        .apply { t.streamUrl?.let { appendQueryParameter("u", it) } }
        .apply { if (addon) appendQueryParameter("src", "addon") }
        .build()

    /** Some add-on is switched on, and the connection's quality is High (add-ons are only for that). */
    fun addonsWanted(): Boolean =
        VideoQualityPolicy.audio() == AudioQuality.HIGH && LibraryStore.settings.let { s -> s.addons.any(s::sourceOn) }

    /** The built-in source a song starting now would play from at best: JioSaavn if on and ranked first, else YouTube. */
    fun bestBuiltIn(): String = LibraryStore.settings.let { s -> s.sources().first { it == Settings.YOUTUBE || it == Settings.SAAVN && s.sourceOn(it) } }

    /** The built-in source [id] is playing from now: JioSaavn for its 320 kbps copy, else YouTube. */
    fun playingFrom(id: String): String = if (cache[id]?.via == "JioSaavn") Settings.SAAVN else Settings.YOUTUBE

    /** An add-on copy of [id] already found, from an add-on still on and ranked above [above]; never blocks. */
    fun addonFor(id: String, above: String = Settings.YOUTUBE): ResolvedStream? {
        val hit = addonHits[id]?.takeIf { it.stream.expiresAtMs - System.currentTimeMillis() > 2 * 60_000 } ?: return null
        return hit.stream.takeIf { hit.base in LibraryStore.settings.addonsAbove(above) }
    }

    /**
     * Looks [t] up on the switched-on add-ons ranked above [above], best first (once at a time per
     * song; a miss isn't asked again for 30 minutes unless the add-ons or their order change).
     * Songs start on their built-in source and move to what this finds, so it may take its time.
     */
    suspend fun lookUpAddon(t: Track, above: String = Settings.YOUTUBE): ResolvedStream? {
        if (t.source == com.dopdop.bopdop.data.model.Source.LOCAL || VideoQualityPolicy.audio() != AudioQuality.HIGH) return null
        val bases = LibraryStore.settings.addonsAbove(above).ifEmpty {
            note(t.id, if (above == Settings.SAAVN) "Playing from JioSaavn, which ranks above your add-ons" else "No add-on ranks above YouTube")
            return null
        }
        addonFor(t.id, above)?.let { return it }
        val key = "${t.id}|${bases.joinToString("|")}"
        addonMisses[key]?.let { if (System.currentTimeMillis() - it < 30 * 60_000) { note(t.id, "Not on your add-ons"); return null } }
        note(t.id, "Searching your add-ons…")
        val job = addonLookups.computeIfAbsent(key) {
            addonScope.async {
                val hit = withTimeoutOrNull(25_000) { addonStream(t, download = false, bases) }
                if (hit != null) addonHits[t.id] = hit else addonMisses[key] = System.currentTimeMillis()
                note(t.id, hit?.let { "Found on ${it.stream.via} as ${it.stream.codec}" } ?: "Not on your add-ons")
                Log.i(TAG, "${t.id} add-on lookup: ${hit?.let { "${it.stream.via} ${it.stream.codec}" } ?: "none"}")
                addonLookups.remove(key)
                hit?.stream
            }
        }
        return job.await()
    }

    /** The add-on copy of [id] failed to play: forget it, so the song goes back to its usual stream. */
    fun dropAddon(id: String) {
        val hit = addonHits.remove(id) ?: return
        addonMisses["$id|${hit.base}"] = System.currentTimeMillis()
        LibraryStore.settings.addonsAbove(Settings.YOUTUBE).let { addonMisses["$id|${it.joinToString("|")}"] = System.currentTimeMillis() }
    }

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
        if (uri.getQueryParameter("src") == "addon") {
            // Found earlier in the background; re-asked only if it expired mid-song.
            val known = addonHits[id]?.takeIf { it.stream.expiresAtMs - System.currentTimeMillis() > 2 * 60_000 && LibraryStore.settings.sourceOn(it.base) }
            val s = known?.stream ?: runBlocking {
                withTimeoutOrNull(10_000) { addonStream(trackOf(uri, id), download = false, LibraryStore.settings.addonsAbove(Settings.YOUTUBE)) }
            }?.also { addonHits[id] = it }?.stream ?: throw IOException("the add-on copy of $id is gone")
            remember(id, s, lossless = true)
            return s
        }
        // Wi-Fi and mobile data each have their own quality (downloads ask for one with ?q=);
        // a resolved url is reused only at the same quality.
        val quality = uri.getQueryParameter("q")?.let { runCatching { AudioQuality.valueOf(it) }.getOrNull() } ?: VideoQualityPolicy.audio()
        val key = "$id@${quality.name}"
        cache[key]?.let { if (it.expiresAtMs - System.currentTimeMillis() > 5 * 60_000) return it }
        val settings = LibraryStore.settings
        val order = settings.sources().filter(settings::sourceOn)
        val download = uri.getQueryParameter("q") != null
        var fromAddon = false
        val s: ResolvedStream = if (id.startsWith("sv:")) {
            // A JioSaavn song: its own file is the only copy.
            val u = saavnAt(uri.getQueryParameter("u") ?: throw IOException("missing saavn url"), quality)
            ResolvedStream(u, null, saavnKbps(u), "AAC", "JioSaavn", Long.MAX_VALUE)
        } else {
            // A Spotify song (browsed from Spotify's catalogue) plays from its YouTube Music copy.
            val ytId = if (id.startsWith("sp:")) {
                val t0 = trackOf(uri, id)
                runBlocking {
                    withTimeoutOrNull(10_000) {
                        com.dopdop.bopdop.data.spotify.SpotifyMatch.match(
                            com.dopdop.bopdop.data.spotify.SpotifyTrack("spotify:track:${id.removePrefix("sp:")}", t0.title, t0.artist, "", t0.durationSec, ""),
                        )
                    }
                }?.id?.also { Log.i(TAG, "$id (Spotify) plays from $it") } ?: throw IOException("no YouTube Music copy of $id")
            } else id
            val vid = ytId.removePrefix("yt:")
            val t = trackOf(uri, id)
            fun addon(base: String, wait: Long) = runBlocking { withTimeoutOrNull(wait) { addonStream(t, download, listOf(base)) } }?.stream
            // The sources in the user's order. Playback skips add-ons here (a background lookup moves the
            // song onto one, so it never waits); downloads ask them in turn. JioSaavn's 320 kbps copy is
            // the High quality option only.
            var ytError: Throwable? = null
            var found: ResolvedStream? = null
            for (k in order) {
                found = when (k) {
                    Settings.SAAVN -> if (quality == AudioQuality.HIGH) hiRes(uri) else null
                    Settings.YOUTUBE -> runCatching { youtube(vid, quality.maxKbps) }.onFailure { ytError = it }.getOrNull()
                    else -> if (download && quality == AudioQuality.HIGH) addon(k, 20_000)?.also { fromAddon = true } else null
                }
                if (found != null || k == Settings.YOUTUBE && ytError == null) break
            }
            // YouTube couldn't play it: anything else that's on, best first, before giving up.
            if (found == null && ytError != null) for (k in order) {
                found = when (k) {
                    Settings.YOUTUBE -> null
                    Settings.SAAVN -> hiRes(uri)
                    else -> addon(k, 8_000)?.also { fromAddon = true }
                }
                if (found != null) break
            }
            found ?: throw (ytError ?: IOException("no source could play $id"))
        }
        Log.i(TAG, "$id → ${s.via} ${s.codec} ${s.bitrate}kbps (${quality.label})")
        cache[key] = s
        cache[id] = s
        remember(id, s, lossless = fromAddon || s.via == "JioSaavn" && !id.startsWith("sv:") || s.bitrate >= 320)
        return s
    }

    /** A JioSaavn file url at [q]'s bitrate (their files differ only by the _96 / _160 / _320 suffix). */
    internal fun saavnAt(url: String, q: AudioQuality): String =
        if (q == AudioQuality.HIGH) url else url.replace(Regex("_(96|160|320)\\."), "${q.saavn}.")

    private fun saavnKbps(url: String) = when { url.contains("_320") -> 320; url.contains("_96.") -> 96; else -> 160 }

    /**
     * The YouTube stream for [vid]. A music video that found its way into a queue (a shelf, a shared
     * link) plays as its song version; asking which it is runs alongside the stream lookup, so
     * ordinary songs (nearly all of them) start no later than before.
     */
    private fun youtube(vid: String, maxKbps: Int): ResolvedStream = runBlocking {
        kotlinx.coroutines.coroutineScope {
            val song = async(kotlinx.coroutines.Dispatchers.IO) { runCatching { withTimeoutOrNull(2500) { YtMusic.songVersionOf(vid) } }.getOrNull() }
            val direct = async(kotlinx.coroutines.Dispatchers.IO) { runCatching { YtStreams.resolve(vid, maxKbps) } }
            val other = song.await()?.takeIf { it != vid }
            if (other != null) {
                Log.i(TAG, "$vid is a music video: playing its song $other")
                direct.cancel()
                YtStreams.resolve(other, maxKbps)
            } else direct.await().getOrThrow()
        }
    }

    /** The quality badge for what [id] is playing as, kept for cached replays. */
    private fun remember(id: String, s: ResolvedStream, lossless: Boolean) {
        val q = StreamQuality(s.codec, s.bitrate, s.via, lossless)
        _quality.update { it + (id to q) }
        prefs?.edit()?.putString(id, "${q.codec}|${q.kbps}|${q.via}|${if (q.hiRes) 1 else 0}")?.apply()
    }

    private fun trackOf(uri: Uri, id: String) =
        Track(id, uri.getQueryParameter("t").orEmpty(), uri.getQueryParameter("a").orEmpty(), durationSec = uri.getQueryParameter("d")?.toIntOrNull() ?: 0)

    /** The same recording from the first of [bases] that has it, as "FLAC 24/96" or "Dolby Atmos". */
    private suspend fun addonStream(t: Track, download: Boolean, bases: List<String>): AddonHit? {
        for (base in bases) addonStreamFrom(t, download, base)?.let { return AddonHit(base, it) }
        return null
    }

    private suspend fun addonStreamFrom(t: Track, download: Boolean, base: String): ResolvedStream? {
        val ctx = appContext ?: return null
        val (s, name) = runCatching { com.dopdop.bopdop.data.addon.Addons.resolve(ctx, t, download, listOf(base)) }.getOrNull() ?: return null
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

/**
 * [addon]: the add-on copy found for this song. Only the seamless switch asks for it: a song always
 * starts on its fastest source (a lossless file is slow to begin) and moves to the copy while playing.
 */
fun Track.toMediaItem(addon: Boolean = false): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .apply {
        // The add-on copy gets its own cache key: it's a different file from the usual stream.
        val local = source == com.dopdop.bopdop.data.model.Source.LOCAL && streamUrl != null
        val useAddon = addon && !local
        setUri(if (local) Uri.parse(streamUrl) else StreamResolver.playUri(this@toMediaItem, useAddon))
        setCustomCacheKey(if (useAddon) "$id#addon" else id)
    }
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

/** Plays an add-on copy rather than the usual stream. */
val MediaItem.isAddonCopy: Boolean get() = localConfiguration?.uri?.getQueryParameter("src") == "addon"

fun MediaItem.toTrack(): Track? = mediaMetadata.extras?.getString(EXTRA_TRACK)?.let {
    runCatching { Track.fromJson(JSONObject(it)) }.getOrNull()
}

/** Items arriving from a MediaController lose their uri; rebuild them from the embedded track. */
fun MediaItem.withPlaybackUri(): MediaItem = toTrack()?.toMediaItem()?.let { audio ->
    videoId?.let { audio.forVideo(it, hasSeparateVideoAudio, videoCap, songStartInVideo, videoSeconds) } ?: audio
} ?: this
