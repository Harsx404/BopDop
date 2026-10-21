package com.dopdop.bopdop.data.ytm

import android.util.Log
import com.dopdop.bopdop.data.net.Http
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import com.dopdop.bopdop.playback.PictureOption
import com.dopdop.bopdop.playback.VideoQualityPolicy
import com.dopdop.bopdop.playback.choosePicture
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException

/** A resolved, directly playable audio url. Googlevideo urls are bound to the requesting UA + IP. */
data class ResolvedStream(
    val url: String,
    val userAgent: String?,
    val bitrate: Int,
    val codec: String,
    val via: String,
    val expiresAtMs: Long,
    val contentLength: Long = 0,
)

data class ResolvedVideo(val video: ResolvedStream, val audio: ResolvedStream?, val width: Int, val height: Int)

/**
 * YouTube audio stream resolution. Phones fetch googlevideo directly (no proxy),
 * so the urls just need a client identity YouTube serves without a PoToken.
 * Tried in order; each falls through on "confirm you're not a bot" / errors.
 */
object YtStreams {
    private const val TAG = "YtStreams"

    private data class Client(
        val name: String, val host: String, val id: String, val version: String, val ua: String,
        val extra: Map<String, Any> = emptyMap(),
    )

    private val CLIENTS = listOf(
        Client("IOS", "www.youtube.com", "5", "20.10.4",
            "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
            mapOf("deviceMake" to "Apple", "deviceModel" to "iPhone16,2", "osName" to "iPhone", "osVersion" to "18.3.2.22D82")),
        Client("VISIONOS", "music.youtube.com", "101", "0.1",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15",
            mapOf("osName" to "visionOS", "osVersion" to "1.3.21O771", "deviceMake" to "Apple", "deviceModel" to "RealityDevice14,1")),
        Client("ANDROID_VR", "www.youtube.com", "28", "1.65.10",
            "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            mapOf("deviceMake" to "Oculus", "deviceModel" to "Quest 3", "androidSdkVersion" to 32, "osName" to "Android", "osVersion" to "12L")),
    )

    /** Which client answered last — tried first next time (YouTube's moods shift per IP). */
    @Volatile private var preferred = 0

    /** Clients whose urls recently failed the mid-file probe (YouTube wants a PoToken from this network). */
    @Volatile private var directBlockedUntil = 0L

    /** The best audio for [videoId] at or under [maxKbps] (the lowest there is when none fit). */
    fun resolve(videoId: String, maxKbps: Int = Int.MAX_VALUE): ResolvedStream {
        var last = "no playable audio"
        if (System.currentTimeMillis() > directBlockedUntil) {
            val order = CLIENTS.indices.sortedBy { if (it == preferred) -1 else it }
            var probeFailures = 0
            for (i in order) {
                try {
                    val s = innertube(CLIENTS[i], videoId, maxKbps)
                    // Some networks get urls that serve only the first chunk then 403 — catch that before playback.
                    if (!probe(s)) { probeFailures++; throw IOException("url refused mid-file") }
                    preferred = i
                    return s
                } catch (e: Exception) {
                    last = e.message ?: e.toString()
                    Log.w(TAG, "${CLIENTS[i].name} failed for $videoId: $last")
                }
            }
            // Every direct client is being gated: skip straight to the PoToken path for a while.
            if (probeFailures > 0) directBlockedUntil = System.currentTimeMillis() + 20 * 60_000L
        }
        return try {
            newPipe(videoId, maxKbps).also { if (!probe(it)) { PoTokens.invalidate(); throw IOException("PoToken stream refused") } }
        } catch (e: Exception) {
            Log.w(TAG, "NewPipe failed for $videoId: ${e.message}")
            throw IOException(e.message ?: last)
        }
    }

    /** A direct-client url died mid-stream: stop trusting direct clients for a while. */
    fun reportDirectFailure() { directBlockedUntil = System.currentTimeMillis() + 20 * 60_000L }

    /** Video and its original soundtrack, resolved together so their timestamps match. [cap] is the tallest picture wanted. */
    fun resolveVideo(videoId: String, cap: Int): ResolvedVideo {
        if (System.currentTimeMillis() > directBlockedUntil) {
            for (i in CLIENTS.indices.sortedBy { if (it == preferred) -1 else it }) {
                try {
                    val c = CLIENTS[i]
                    val formats = playerResponse(c, videoId).optJSONObject("streamingData")
                        ?.optJSONArray("adaptiveFormats") ?: throw IOException("no video formats")
                    val all = (0 until formats.length()).mapNotNull { formats.optJSONObject(it) }
                        .filter { it.optString("url").isNotEmpty() }
                    val videos = all.filter { it.optString("mimeType").startsWith("video/") }
                    val picture = choosePicture(videos.map {
                        PictureOption(it, it.optString("mimeType"), it.optInt("width"), it.optInt("height"), it.optInt("fps"))
                    }, cap) { VideoQualityPolicy.canDecode(it.mimeType, it.width, it.height, it.fps) } ?: throw IOException("no direct video")
                    val sound = all.filter { it.optString("mimeType").startsWith("audio/") && !it.optBoolean("isDrc") &&
                        (it.optJSONObject("audioTrack")?.optBoolean("audioIsDefault", true) != false) }
                        .maxByOrNull { it.optInt("bitrate") } ?: throw IOException("no video soundtrack")
                    val video = directStream(picture, c)
                    val audio = directStream(sound, c)
                    if (!probe(video) || !probe(audio)) throw IOException("video stream refused")
                    preferred = i
                    return ResolvedVideo(video, audio, picture.optInt("width"), picture.optInt("height"))
                } catch (e: Exception) {
                    Log.w(TAG, "${CLIENTS[i].name} video failed for $videoId: ${e.message}")
                }
            }
        }
        check(newPipeReady)
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        val videos = (info.videoOnlyStreams + info.videoStreams).filter { it.isUrl && it.content.isNotBlank() }
        val best = choosePicture(videos.map {
            // NewPipe gives the bare codec ("avc1.64001f"); shape it like YouTube's mimeType.
            PictureOption(it, "video; codecs=\"${it.codec ?: if (it.format?.mimeType?.contains("mp4") == true) "avc1" else ""}\"", it.width, it.height, it.fps)
        }, cap) { VideoQualityPolicy.canDecode(it.mimeType, it.width, it.height, it.fps) } ?: throw IOException("No playable music video")
        val sound = if (best.isVideoOnly) info.audioStreams.filter { it.isUrl && it.content.isNotBlank() }
            .maxByOrNull { it.averageBitrate } ?: throw IOException("No video soundtrack") else null
        val video = ResolvedStream(best.content, NP_UA, best.bitrate / 1000, "Video", "PoToken", expiresAt(best.content))
        val audio = sound?.let { ResolvedStream(it.content, NP_UA, it.averageBitrate, "Audio", "PoToken", expiresAt(it.content)) }
        if (!probe(video) || (audio != null && !probe(audio))) throw IOException("Music video unavailable")
        return ResolvedVideo(video, audio, best.width, best.height)
    }

    private fun expiresAt(url: String) = Regex("[?&]expire=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()?.times(1000)
        ?: (System.currentTimeMillis() + 3 * 3600_000L)

    private fun directStream(format: JSONObject, client: Client): ResolvedStream {
        val mime = format.optString("mimeType")
        val url = format.getString("url")
        return ResolvedStream(url, client.ua, format.optInt("bitrate") / 1000,
            if (mime.contains("opus")) "Opus" else if (mime.contains("mp4a")) "AAC" else "Video",
            client.name, expiresAt(url), format.optString("contentLength").toLongOrNull() ?: 0L)
    }

    /** Reads 1 KB from mid-file; the first ~1 MB is served to anyone, past it a gated url answers 403. */
    private fun probe(s: ResolvedStream): Boolean = runCatching {
        val len = s.contentLength
        val at = if (len > 0) minOf(len - 2048, maxOf(len / 2, 1_600_000L)).coerceAtLeast(0) else 1_600_000L
        val req = Request.Builder().url(s.url).header("Range", "bytes=$at-${at + 1023}")
            .apply { s.userAgent?.let { header("User-Agent", it) } }.build()
        Http.client.newCall(req).execute().use { it.code == 206 || it.code == 200 || it.code == 416 }
    }.getOrDefault(false)

    private fun playerResponse(c: Client, videoId: String): JSONObject {
        val client = JSONObject().put("hl", "en").put("gl", YtMusic.gl).put("clientName", c.name).put("clientVersion", c.version)
        c.extra.forEach { (k, v) -> client.put(k, v) }
        YtMusic.visitorData?.let { client.put("visitorData", it) }
        val body = JSONObject().put("videoId", videoId).put("contentCheckOk", true).put("racyCheckOk", true)
            .put("context", JSONObject().put("client", client))
        val req = Request.Builder().url("https://${c.host}/youtubei/v1/player?prettyPrint=false")
            .post(body.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .header("User-Agent", c.ua)
            .header("X-YouTube-Client-Name", c.id)
            .header("X-YouTube-Client-Version", c.version)
            .apply { YtMusic.visitorData?.let { header("X-Goog-Visitor-Id", it) } }
            .build()
        val p = Http.client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
            JSONObject(res.body?.string().orEmpty())
        }
        val ps = p.optJSONObject("playabilityStatus")
        if (ps?.optString("status") != "OK") throw IOException(ps?.optString("reason")?.ifEmpty { null } ?: ps?.optString("status") ?: "unplayable")
        return p
    }

    private fun innertube(c: Client, videoId: String, maxKbps: Int = Int.MAX_VALUE): ResolvedStream {
        val p = playerResponse(c, videoId)
        val fmts = p.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats") ?: throw IOException("no formats")
        val usable = ArrayList<JSONObject>()
        for (i in 0 until fmts.length()) {
            val f = fmts.optJSONObject(i) ?: continue
            if (!f.optString("mimeType").startsWith("audio/") || f.optString("url").isEmpty()) continue
            // Skip DRC / dubbed tracks; prefer the original audio.
            if (f.optBoolean("isDrc")) continue
            val track = f.optJSONObject("audioTrack")
            if (track != null && !track.optBoolean("audioIsDefault", true)) continue
            usable += f
        }
        // The highest bitrate within the quality cap; the smallest one when even that is over it.
        val best = usable.filter { it.optInt("bitrate") / 1000 <= maxKbps }.maxByOrNull { it.optInt("bitrate") }
            ?: usable.minByOrNull { it.optInt("bitrate") } ?: throw IOException("no direct audio")
        val url = best.optString("url")
        val expire = Regex("[?&]expire=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()?.times(1000)
            ?: (System.currentTimeMillis() + 3 * 3600_000L)
        val mime = best.optString("mimeType")
        return ResolvedStream(
            url = url, userAgent = c.ua, bitrate = best.optInt("bitrate") / 1000,
            codec = if (mime.contains("opus")) "Opus" else if (mime.contains("mp4a")) "AAC" else "Audio",
            via = c.name, expiresAtMs = expire,
            contentLength = best.optString("contentLength").toLongOrNull() ?: 0L,
        )
    }

    // --- NewPipeExtractor fallback (handles signature deobfuscation itself) ---

    private val newPipeReady by lazy {
        NewPipe.init(OkHttpDownloader, Localization("en", YtMusic.gl), ContentCountry(YtMusic.gl))
        org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor.setPoTokenProvider(PoTokens)
        true
    }

    private fun newPipe(videoId: String, maxKbps: Int = Int.MAX_VALUE): ResolvedStream {
        check(newPipeReady)
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        val usable = info.audioStreams.filter { !it.content.isNullOrEmpty() && it.isUrl }
        val best = usable.filter { it.averageBitrate <= maxKbps }.maxByOrNull { it.averageBitrate }
            ?: usable.minByOrNull { it.averageBitrate } ?: throw IOException("NewPipe: no audio")
        val url = best.content
        val expire = Regex("[?&]expire=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()?.times(1000)
            ?: (System.currentTimeMillis() + 3 * 3600_000L)
        val codec = when (best.format?.name?.lowercase()) { "webma", "webm", "opus" -> "Opus"; "m4a", "mp4" -> "AAC"; else -> best.format?.name ?: "Audio" }
        return ResolvedStream(url, NP_UA, best.averageBitrate.coerceAtLeast(0), codec, "PoToken", expire)
    }

    private const val NP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

    private object OkHttpDownloader : Downloader() {
        override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): org.schabi.newpipe.extractor.downloader.Response {
            val data = request.dataToSend()
            val b = Request.Builder().url(request.url())
                .method(request.httpMethod(), data?.toRequestBody())
                .header("User-Agent", NP_UA)
            request.headers().forEach { (k, vs) ->
                b.removeHeader(k)
                vs.forEach { v -> b.addHeader(k, v) }
            }
            Http.client.newCall(b.build()).execute().use { res ->
                if (res.code == 429) throw org.schabi.newpipe.extractor.exceptions.ReCaptchaException("reCaptcha Challenge requested", request.url())
                return org.schabi.newpipe.extractor.downloader.Response(
                    res.code, res.message, res.headers.toMultimap(), res.body?.string(), res.request.url.toString(),
                )
            }
        }
    }
}
