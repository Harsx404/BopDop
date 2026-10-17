package com.dopdop.bopdop.playback

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.C
import androidx.media3.exoplayer.source.ClippingMediaSource
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2
import androidx.media3.exoplayer.source.FilteringMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import com.dopdop.bopdop.data.ytm.ResolvedStream
import com.dopdop.bopdop.data.ytm.ResolvedVideo
import com.dopdop.bopdop.data.ytm.YtStreams
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

data class VideoPlaybackState(
    val trackId: String? = null,
    val loading: Boolean = false,
    val active: Boolean = false,
    val aspectRatio: Float = 16f / 9f,
    val height: Int = 0,
    val error: String? = null,
)

private const val EXTRA_VIDEO_ID = "bopdop.videoId"
private const val EXTRA_VIDEO_AUDIO = "bopdop.videoAudio"
private const val EXTRA_VIDEO_CAP = "bopdop.videoCap"
private const val EXTRA_SONG_AT = "bopdop.songAt"
private const val EXTRA_VIDEO_SECONDS = "bopdop.videoSeconds"

internal val MediaItem.videoId: String? get() = mediaMetadata.extras?.getString(EXTRA_VIDEO_ID)
internal val MediaItem.hasSeparateVideoAudio: Boolean get() = mediaMetadata.extras?.getBoolean(EXTRA_VIDEO_AUDIO, true) ?: true
/** Picture height cap the item was resolved with — pinned so a settings change never swaps streams mid-play. */
internal val MediaItem.videoCap: Int get() = mediaMetadata.extras?.getInt(EXTRA_VIDEO_CAP, 720) ?: 720
/** ms into the video where the song starts, when the song's own audio plays under the picture; else null. */
internal val MediaItem.songStartInVideo: Long? get() = mediaMetadata.extras?.getLong(EXTRA_SONG_AT, -1L)?.takeIf { it >= 0 }

/** Length of the music video, when known — lets a short video loop under a longer song. */
internal val MediaItem.videoSeconds: Int get() = mediaMetadata.extras?.getInt(EXTRA_VIDEO_SECONDS, 0) ?: 0

internal fun MediaItem.forVideo(id: String, separateAudio: Boolean, cap: Int, songAt: Long? = null, videoSeconds: Int = 0): MediaItem = buildUpon()
    .setUri(Uri.Builder().scheme(StreamResolver.SCHEME).authority("watch")
        .appendQueryParameter("id", mediaId).appendQueryParameter("video", id).appendQueryParameter("cap", cap.toString()).build())
    .setCustomCacheKey("video:$id:$cap:picture")
    .setMediaMetadata(MediaMetadata.Builder().populate(mediaMetadata)
        .setExtras(Bundle(mediaMetadata.extras ?: Bundle()).apply {
            putString(EXTRA_VIDEO_ID, id)
            putBoolean(EXTRA_VIDEO_AUDIO, separateAudio)
            putInt(EXTRA_VIDEO_CAP, cap)
            putLong(EXTRA_SONG_AT, songAt ?: -1L)
            putInt(EXTRA_VIDEO_SECONDS, videoSeconds)
        }).build())
    .build()

/** The video cache is separate from audio, including when hi-res audio comes from Saavn. */
internal object VideoResolver {
    private val streams = ConcurrentHashMap<String, ResolvedVideo>()

    fun prepare(id: String, cap: Int): ResolvedVideo {
        val key = "$id@$cap"
        streams[key]?.let {
            val expiry = minOf(it.video.expiresAtMs, it.audio?.expiresAtMs ?: Long.MAX_VALUE)
            if (expiry - System.currentTimeMillis() > 5 * 60_000) return it
        }
        return YtStreams.resolveVideo(id, cap).also { streams[key] = it }
    }

    fun resolve(uri: Uri): ResolvedStream {
        val id = uri.getQueryParameter("video") ?: throw IOException("Missing video id")
        val pair = prepare(id, uri.getQueryParameter("cap")?.toIntOrNull() ?: 720)
        return if (uri.getQueryParameter("part") == "audio") pair.audio ?: pair.video else pair.video
    }

    fun invalidate(id: String) { streams.keys.removeAll { it.startsWith("$id@") } }
}

@OptIn(UnstableApi::class)
internal class VideoMediaSourceFactory(
    private val delegate: MediaSource.Factory,
    /** Files on the phone: read directly, never through the stream cache. */
    private val local: MediaSource.Factory,
) : MediaSource.Factory by delegate {
    private fun sourceFor(item: MediaItem): MediaSource =
        if (item.localConfiguration?.uri?.scheme in LOCAL_SCHEMES) local.createMediaSource(item) else delegate.createMediaSource(item)

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val id = mediaItem.videoId ?: return sourceFor(mediaItem)
        val songAt = mediaItem.songStartInVideo
        // The song's stream, but carrying this item's identity: the merged source reports the first
        // child's item as current, and without the video extras the app would think video ended.
        val song = mediaItem.toTrack()?.toMediaItem()?.let { plain ->
            mediaItem.buildUpon().setUri(plain.localConfiguration!!.uri).setCustomCacheKey(plain.mediaId).build()
        }
        if (songAt != null && song != null) {
            // The song plays from its own source; the video gives only its picture (any soundtrack in
            // it is dropped), trimmed so its frame at songAt lands on the song's 0:00.
            // Unclipped durations: the song's own length decides when the item ends.
            val once = {
                FilteringMediaSource(delegate.createMediaSource(mediaItem), C.TRACK_TYPE_VIDEO).let {
                    if (songAt > 0) ClippingMediaSource(it, songAt * 1000, C.TIME_END_OF_SOURCE) else it
                }
            }
            // A video shorter than the song plays again from the song's start point, so the picture
            // never freezes on the last frame while the music carries on.
            val songMs = (mediaItem.toTrack()?.durationSec ?: 0) * 1000L
            val clipMs = mediaItem.videoSeconds * 1000L - songAt
            val loops = if (songMs > 0 && clipMs in 15_000 until songMs - 3_000) ((songMs + clipMs - 1) / clipMs).toInt().coerceIn(2, 6) else 1
            val picture = if (loops == 1) once() else ConcatenatingMediaSource2.Builder().setMediaItem(mediaItem)
                .apply { repeat(loops) { add(once(), clipMs) } }.build()
            return MergingMediaSource(true, false, sourceFor(song), picture)
        }
        if (!mediaItem.hasSeparateVideoAudio) return delegate.createMediaSource(mediaItem)
        val audio = mediaItem.buildUpon()
            .setUri(mediaItem.localConfiguration!!.uri.buildUpon().appendQueryParameter("part", "audio").build())
            .setCustomCacheKey("video:$id:${mediaItem.videoCap}:sound").build()
        return MergingMediaSource(true, true, delegate.createMediaSource(mediaItem), delegate.createMediaSource(audio))
    }
}

private val LOCAL_SCHEMES = setOf("content", "file")
