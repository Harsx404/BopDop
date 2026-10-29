package com.dopdop.bopdop.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.dopdop.bopdop.data.net.Http
import com.dopdop.bopdop.data.ytm.ResolvedStream
import com.dopdop.bopdop.data.ytm.ResolvedVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File

/** The same bounded byte cache serves prefetch and foreground playback. Never caches whole feeds. */
@OptIn(UnstableApi::class)
object ShortsVideoCache {
    @Volatile private var stored: SimpleCache? = null
    private fun cache(context: Context): SimpleCache = stored ?: synchronized(this) {
        stored ?: SimpleCache(File(context.applicationContext.cacheDir, "shorts-video"),
            LeastRecentlyUsedCacheEvictor(64L * 1024 * 1024), Databases.provider(context)).also { stored = it }
    }

    private fun factory(context: Context, stream: ResolvedStream) = CacheDataSource.Factory()
        .setCache(cache(context))
        .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(Http.client).setUserAgent(stream.userAgent))
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    suspend fun source(context: Context, resolved: ResolvedVideo): MediaSource = withContext(Dispatchers.IO) {
        fun progressive(stream: ResolvedStream) = ProgressiveMediaSource.Factory(factory(context, stream))
            .createMediaSource(MediaItem.fromUri(stream.url))
        val video = progressive(resolved.video)
        resolved.audio?.let { MergingMediaSource(video, progressive(it)) } ?: video
    }

    suspend fun warm(context: Context, resolved: ResolvedVideo, metered: Boolean) = withContext(Dispatchers.IO) {
        suspend fun start(stream: ResolvedStream, bytes: Long) {
            val source = factory(context, stream).createDataSourceForDownloading()
            val writer = CacheWriter(source, DataSpec.Builder().setUri(stream.url).setPosition(0).setLength(bytes).build(), null, null)
            coroutineScope {
                val work = async(Dispatchers.IO) { writer.cache() }
                try { work.await() } finally { writer.cancel() }
            }
        }
        // Include the soundtrack's header/data as well as the picture, without blocking playback.
        resolved.audio?.let { start(it, 64 * 1024L) }
        start(resolved.video, if (metered) 256 * 1024L else 768 * 1024L)
    }
}
