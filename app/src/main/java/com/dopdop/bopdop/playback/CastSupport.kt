package com.dopdop.bopdop.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.dopdop.bopdop.data.model.Source
import com.dopdop.bopdop.data.model.Track
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.common.images.WebImage
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Casting: the TV or speaker fetches the audio itself, so each queue item has to carry a real
 * CDN url rather than BopDop's own `bopdop://` address. Urls come from the resolver's cache,
 * which [CastPrefetch] keeps warm for the next few songs whenever a Cast device is around.
 * The track itself rides along in customData, so items come back intact when casting stops.
 */
@OptIn(UnstableApi::class)
internal object CastConverter : MediaItemConverter {
    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val track = mediaItem.toTrack()
        val stream = track?.let { StreamResolver.peek(it) }
        val meta = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, track?.title ?: mediaItem.mediaMetadata.title?.toString().orEmpty())
            putString(MediaMetadata.KEY_ARTIST, track?.artist ?: mediaItem.mediaMetadata.artist?.toString().orEmpty())
            track?.album?.takeIf { it.isNotBlank() }?.let { putString(MediaMetadata.KEY_ALBUM_TITLE, it) }
            track?.artwork?.takeIf { it.startsWith("http") }?.let { addImage(WebImage(Uri.parse(it))) }
        }
        val info = MediaInfo.Builder(stream?.url ?: "")
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(if (stream?.codec == "Opus") "audio/webm" else "audio/mp4")
            .setMetadata(meta)
            .apply { track?.let { setStreamDuration(it.durationSec * 1000L) } }
            .setCustomData(JSONObject().put("mediaId", mediaItem.mediaId).apply { track?.let { put("track", it.toJson()) } })
            .build()
        return MediaQueueItem.Builder(info).build()
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
        val data = mediaQueueItem.media?.customData
        val track = data?.optJSONObject("track")?.let { runCatching { Track.fromJson(it) }.getOrNull() }
        return track?.toMediaItem() ?: MediaItem.Builder().setMediaId(data?.optString("mediaId").orEmpty()).build()
    }
}

/** Resolves the next few songs ahead of time while a Cast device is in reach. */
internal object CastPrefetch {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val warmed = HashSet<String>()

    fun onQueue(tracks: List<Track>) {
        val inReach = runCatching { CastContext.getSharedInstance()?.castState }.getOrNull()
            ?.let { it != CastState.NO_DEVICES_AVAILABLE } ?: false
        if (!inReach) return
        tracks.filter { it.source != Source.LOCAL && warmed.add(it.id) }.forEach { t ->
            scope.launch { runCatching { StreamResolver.resolve(StreamResolver.playUri(t)) }.onFailure { warmed.remove(t.id) } }
        }
    }
}
