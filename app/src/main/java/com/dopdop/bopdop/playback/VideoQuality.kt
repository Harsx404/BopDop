package com.dopdop.bopdop.playback

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
import android.net.ConnectivityManager
import android.os.Build
import com.dopdop.bopdop.data.VideoQuality
import java.util.concurrent.ConcurrentHashMap

/** Turns the music-video quality setting into a height cap and checks what this phone can decode. */
object VideoQualityPolicy {
    private var appContext: Context? = null
    fun init(context: Context) { appContext = context.applicationContext }

    /** Auto: full HD on Wi-Fi and other unmetered networks, 480p on mobile data. */
    fun maxHeight(quality: VideoQuality): Int {
        if (quality != VideoQuality.AUTO) return quality.maxHeight
        val cm = appContext?.getSystemService(ConnectivityManager::class.java) ?: return 720
        return if (cm.isActiveNetworkMetered) 480 else 1080
    }

    private val support = ConcurrentHashMap<String, Boolean>()

    /**
     * Whether a stream of YouTube [mimeType] (e.g. `video/webm; codecs="vp9"`) plays here at that size.
     * Past full HD only hardware decoders count — software 4K stutters.
     */
    fun canDecode(mimeType: String, width: Int, height: Int, fps: Int): Boolean {
        val mime = decoderMime(mimeType) ?: return false
        if (width <= 0 || height <= 0) return mime == MediaFormat.MIMETYPE_VIDEO_AVC
        return support.getOrPut("$mime|$width|$height|$fps") {
            val needHardware = minOf(width, height) > 1080
            runCatching {
                MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
                    !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
                        (!needHardware || Build.VERSION.SDK_INT < 29 || info.isHardwareAccelerated) &&
                        info.getCapabilitiesForType(mime).videoCapabilities?.let { vc ->
                            fun fits(w: Int, h: Int) = if (fps > 0) vc.areSizeAndRateSupported(w, h, fps.toDouble()) else vc.isSizeSupported(w, h)
                            fits(width, height) || fits(height, width)
                        } == true
                }
            }.getOrDefault(false)
        }
    }
}

/** Decoder mime for a YouTube format's `codecs` parameter; null for codecs we don't play. */
internal fun decoderMime(mimeType: String): String? {
    val codec = mimeType.substringAfter("codecs=", "").trim('"', ' ').lowercase()
    return when {
        codec.startsWith("avc1") -> MediaFormat.MIMETYPE_VIDEO_AVC
        codec.startsWith("vp9") || codec.startsWith("vp09") -> MediaFormat.MIMETYPE_VIDEO_VP9
        codec.startsWith("av01") -> MediaFormat.MIMETYPE_VIDEO_AV1
        else -> null
    }
}

internal data class PictureOption<T>(val item: T, val mimeType: String, val width: Int, val height: Int, val fps: Int)

/**
 * The tallest picture within [cap] that [playable] accepts; at equal height H.264 beats VP9 beats AV1
 * (cheapest to decode), then the smoother frame rate. Nothing under the cap → the smallest playable.
 */
internal fun <T> choosePicture(options: List<PictureOption<T>>, cap: Int, playable: (PictureOption<T>) -> Boolean): T? {
    fun rank(o: PictureOption<T>) = when (decoderMime(o.mimeType)) {
        MediaFormat.MIMETYPE_VIDEO_AVC -> 0; MediaFormat.MIMETYPE_VIDEO_VP9 -> 1; else -> 2
    }
    val ok = options.filter { it.height > 0 && playable(it) }
    return ok.filter { it.height <= cap }
        .sortedWith(compareByDescending<PictureOption<T>> { it.height }.thenBy { rank(it) }.thenByDescending { it.fps })
        .firstOrNull()?.item
        ?: ok.minByOrNull { it.height }?.item
}
