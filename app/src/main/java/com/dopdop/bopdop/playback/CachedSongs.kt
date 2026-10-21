package com.dopdop.bopdop.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.ContentMetadata
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.model.Source
import com.dopdop.bopdop.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Songs kept whole in the stream cache — they play offline and without using data. */
@OptIn(UnstableApi::class)
object CachedSongs {
    suspend fun list(context: Context): List<Track> = withContext(Dispatchers.IO) {
        val cache = PlaybackService.cache(context)
        val lib = LibraryStore.state.value
        // The cache keeps bytes, not titles: look the songs up among the ones the app knows.
        (lib.history + lib.liked + lib.playlists.flatMap { it.tracks })
            .distinctBy { it.id }
            .filter { it.source != Source.LOCAL }
            .filter { t ->
                val length = ContentMetadata.getContentLength(cache.getContentMetadata(t.id))
                length > 0 && cache.getCachedBytes(t.id, 0, length) >= length
            }
    }

    /** True when [t] is fully in the play cache. */
    fun isCached(context: Context, t: Track): Boolean = runCatching {
        val cache = PlaybackService.cache(context)
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(t.id))
        length > 0 && cache.getCachedBytes(t.id, 0, length) >= length
    }.getOrDefault(false)

    /** Drop one song from the play cache (it streams again next time). Downloads are separate. */
    suspend fun remove(context: Context, t: Track) = withContext(Dispatchers.IO) {
        runCatching { PlaybackService.cache(context).removeResource(t.id) }
    }
}
