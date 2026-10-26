package com.dopdop.bopdop.data.addon

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaCodecList
import android.util.Log
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.model.SongVersion
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.net.Http
import com.dopdop.bopdop.data.net.TtlCache
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AddonManifest(
    val id: String, val name: String, val version: String,
    val defaults: Map<String, String>, val allowDownloads: Boolean, val needsLosslessOutput: Boolean, val declaresAtmos: Boolean,
)

data class AddonTrack(
    val id: String, val title: String, val artist: String, val album: String, val durationSec: Int,
    val format: String, val atmos: Boolean, val streamUrl: String?,
)

data class AddonStream(val url: String, val codec: String, val kbps: Int, val sampleRate: Int, val bitDepth: Int, val atmos: Boolean)

/**
 * Lossless and Dolby Atmos from add-ons: HTTP servers the user adds by URL, speaking the
 * common music add-on protocol (`/manifest.json`, `/search?q=`, `/stream/{id}`, JSON only; nothing
 * from an add-on ever runs on the phone). BopDop ships none: what an add-on serves, and whether
 * you may use it, is between you and whoever runs it.
 *
 * When "Lossless from add-ons" is on, a song is looked up on each add-on in order and played
 * from the first copy that is the same recording (same name and version, an artist in common,
 * within 8 s). Anything else falls back to the usual stream.
 */
object Addons {
    private const val TAG = "Addons"
    private val manifests = TtlCache<String, AddonManifest>(6 * 3600_000L, 20)
    private val searches = TtlCache<String, List<AddonTrack>>(30 * 60_000L, 200)

    /** `https://host/addon` from whatever was pasted (with or without /manifest.json). */
    fun normalize(raw: String): String? {
        val t = raw.trim().removeSuffix("/").removeSuffix("/manifest.json").removeSuffix("/")
        return t.takeIf { it.toHttpUrlOrNull()?.let { u -> u.scheme == "https" || u.host == "localhost" || u.host.endsWith(".local") || u.host.startsWith("192.168.") || u.host.startsWith("10.") } == true }
    }

    suspend fun manifest(base: String): AddonManifest = manifests.getOrLoad(base) {
        val o = getJson("$base/manifest.json") as? JSONObject ?: throw IOException("That URL didn't answer with an add-on manifest")
        val id = o.optString("id").ifBlank { throw IOException("That URL answered, but not with an add-on manifest") }
        val resources = o.optJSONArray("resources")?.let { a -> (0 until a.length()).map { a.optString(it).lowercase() } }.orEmpty()
        if (resources.isNotEmpty() && "search" !in resources) throw IOException("This add-on can't search, so it has nothing to play")
        val settings = o.optJSONArray("settings") ?: JSONArray()
        val defaults = (0 until settings.length()).mapNotNull { i ->
            val s = settings.optJSONObject(i) ?: return@mapNotNull null
            val key = s.optString("key").ifBlank { return@mapNotNull null }
            val d = s.opt("default")?.takeIf { it != JSONObject.NULL } ?: return@mapNotNull null
            key to d.toString()
        }.toMap()
        AddonManifest(
            id = id, name = o.optString("name").ifBlank { id }, version = o.optString("version"),
            defaults = defaults,
            allowDownloads = flag(o.opt("allowDownloads")) ?: true,
            needsLosslessOutput = flag(o.opt("checkValidLossless")) ?: false,
            declaresAtmos = defaults.containsKey("atmos"),
        )
    }

    private fun flag(v: Any?): Boolean? = when (v) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> v.lowercase() !in setOf("0", "false", "no", "off", "")
        else -> null
    }

    /** The query an add-on is asked with: its own declared defaults, plus `atmos=auto` when this phone plays Atmos. */
    private fun params(m: AddonManifest, context: Context?): Map<String, String> = buildMap {
        putAll(m.defaults)
        if (context != null && LibraryStore.settings.atmos && DeviceAudio.playsAtmos()) put("atmos", "auto")
    }

    suspend fun search(base: String, query: String, context: Context?): List<AddonTrack> {
        val m = manifest(base)
        val key = "$base|$query|${LibraryStore.settings.atmos}"
        return searches.getOrLoad(key) {
            val url = "$base/search".toHttpUrlOrNull()!!.newBuilder().addQueryParameter("q", query)
                .apply { params(m, context).forEach { (k, v) -> addQueryParameter(k, v) } }.build().toString()
            val root = getJson(url)
            val arr = (root as? JSONObject)?.optJSONArray("tracks") ?: root as? JSONArray ?: JSONArray()
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::track) }
        }
    }

    private fun track(o: JSONObject): AddonTrack? {
        val modes = o.optJSONArray("audioModes")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty() + o.optString("audioMode")
        return AddonTrack(
            id = o.optString("id").ifBlank { return null }, title = o.optString("title").ifBlank { return null },
            artist = o.optString("artist"), album = o.optString("album"), durationSec = o.optDouble("duration", 0.0).toInt(),
            format = o.optString("format").ifBlank { o.optString("audioQuality") },
            atmos = o.optBoolean("atmos") || modes.any { it.contains("ATMOS", true) },
            streamUrl = o.optString("streamURL").ifBlank { null },
        )
    }

    suspend fun stream(base: String, t: AddonTrack, context: Context?): AddonStream? {
        t.streamUrl?.let { return AddonStream(it, t.format.uppercase().ifBlank { "FLAC" }, 0, 0, 0, t.atmos) }
        val m = manifest(base)
        val url = "$base/stream/${java.net.URLEncoder.encode(t.id, "UTF-8")}".toHttpUrlOrNull()!!.newBuilder()
            .apply { params(m, context).forEach { (k, v) -> addQueryParameter(k, v) } }.build().toString()
        val o = getJson(url) as? JSONObject ?: return null
        // DRM-protected or manifest-only answers can't be played as a plain file.
        if (flag(o.opt("encrypted")) == true) return null
        val u = o.optString("url").ifBlank { return null }
        val modes = o.optJSONArray("audioModes")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty() + o.optString("audioMode")
        val codec = (o.optString("codec").ifBlank { o.optString("format") }).uppercase().ifBlank { t.format.uppercase() }
        return AddonStream(
            u, codec, (o.optDouble("bitrate", 0.0) / (if (o.optDouble("bitrate", 0.0) > 10_000) 1000 else 1)).toInt(),
            o.optDouble("sampleRate", 0.0).toInt(), o.optDouble("bitDepth", 0.0).toInt(),
            t.atmos || modes.any { it.contains("ATMOS", true) },
        )
    }

    /**
     * The first copy of [t] on [urls] (in that order) that is the same recording, as a playable stream. [download]
     * skips add-ons that only allow playback; add-ons that want a lossless output are skipped on
     * the phone speaker and Bluetooth.
     */
    suspend fun resolve(context: Context, t: Track, download: Boolean, urls: List<String>): Pair<AddonStream, String>? {
        if (urls.isEmpty()) return null
        for (base in urls) {
            val m = runCatching { manifest(base) }.getOrNull() ?: continue
            if (download && !m.allowDownloads) continue
            if (m.needsLosslessOutput && !DeviceAudio.losslessOutput(context)) continue
            val hits = runCatching { search(base, "${t.title} ${t.artist.split(",").first()}", context) }
                .onFailure { Log.w(TAG, "${m.name} search: ${it.message}") }.getOrNull() ?: continue
            val hit = hits.firstOrNull { SongVersion.sameRecording(t, Track("ad:${it.id}", it.title, it.artist, durationSec = it.durationSec), toleranceSec = 8) } ?: continue
            val s = runCatching { stream(base, hit, context) }.getOrNull() ?: continue
            return s to m.name
        }
        return null
    }

    /** Off the main thread whoever calls: adding an add-on in Settings asks from the UI. */
    /** What a Settings › Sources test found: [ok], a line for the user, and how long it took. */
    data class TestResult(val ok: Boolean, val message: String, val ms: Long)

    /**
     * Checks [base] end to end the way playback uses it: the manifest, a search for [probe] (the
     * playing song, or a well-known one), and a stream for the best hit. Nothing is played.
     */
    suspend fun test(base: String, probe: Track?, context: Context?): TestResult {
        val start = System.currentTimeMillis()
        fun done(ok: Boolean, msg: String) = TestResult(ok, msg, System.currentTimeMillis() - start)
        manifests.remove(base)
        val m = runCatching { manifest(base) }.getOrElse { return done(false, it.message ?: "No manifest") }
        val t = probe ?: Track("probe", "Blinding Lights", "The Weeknd", durationSec = 200)
        val q = "${t.title} ${t.artist.split(",").first()}"
        val hits = runCatching { search(base, q, context) }.getOrElse { return done(false, "Search failed: ${it.message}") }
        if (hits.isEmpty()) return done(false, "Answers, but found nothing for \"${t.title}\"")
        // Playback only ever switches to the same recording; say so plainly when this one isn't there,
        // rather than testing a look-alike ("Tum Se" for "Tum Se Tum Tak").
        val hit = hits.firstOrNull { SongVersion.sameRecording(t, Track("ad:${it.id}", it.title, it.artist, durationSec = it.durationSec), toleranceSec = 8) }
            ?: return done(true, "Working, but it doesn't have \"${t.title}\" (closest: \"${hits.first().title}\" by ${hits.first().artist.ifBlank { "?" }})")
        val st = runCatching { stream(base, hit, context) }.getOrElse { return done(false, "Found it, but the stream failed: ${it.message}") }
            ?: return done(false, "Found it, but gave no playable stream")
        val q2 = listOfNotNull(st.codec.ifBlank { null }, if (st.bitDepth > 0 && st.sampleRate > 0) "${st.bitDepth}/${st.sampleRate / 1000}" else null, if (st.atmos) "Atmos" else null).joinToString(" ")
        val note = if (m.needsLosslessOutput && context != null && !DeviceAudio.losslessOutput(context)) " · plays only on wired/USB audio" else ""
        return done(true, "Working · \"${hit.title}\" as ${q2.ifBlank { "audio" }}$note")
    }

    private suspend fun getJson(url: String): Any = withContext(Dispatchers.IO) {
        Http.client.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use {
            if (it.code == 429) throw IOException("The add-on asked to slow down")
            if (!it.isSuccessful) throw IOException("The add-on answered HTTP ${it.code}")
            val text = it.body?.string().orEmpty().trim()
            if (text.startsWith("[")) JSONArray(text) else JSONObject(text)
        }
    }
}

/** What this phone's audio hardware can do, for lossless and Atmos choices. */
object DeviceAudio {
    /**
     * A Dolby Atmos (E-AC-3 JOC or AC-4) decoder on the phone. Listing codecs is slow on some
     * phones, so it's done once in the background ([warm]); until then this answers false.
     */
    fun playsAtmos(): Boolean = atmos ?: false
    @Volatile private var atmos: Boolean? = null

    fun warm() {
        if (atmos != null) return
        Thread { atmos = scan() }.apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }

    private fun scan(): Boolean =
        runCatching {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals("audio/eac3-joc", true) || it.equals("audio/ac4", true) }
            }
        }.getOrDefault(false)

    /** Wired, USB or HDMI output: somewhere lossless arrives intact (Bluetooth is lossy on most phones). */
    fun losslessOutput(context: Context): Boolean {
        val am = context.getSystemService(AudioManager::class.java) ?: return false
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type in setOf(
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL,
            )
        }
    }
}
