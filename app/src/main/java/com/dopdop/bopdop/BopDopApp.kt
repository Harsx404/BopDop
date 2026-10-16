package com.dopdop.bopdop

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.dopdop.bopdop.data.LibraryStore

class BopDopApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        LibraryStore.init(this)
        com.dopdop.bopdop.data.PlayStats.init(this)
        com.dopdop.bopdop.data.account.AccountStore.init(this)
        com.dopdop.bopdop.playback.StreamResolver.init(this)
        com.dopdop.bopdop.data.ytm.PoTokens.init(this)
        com.dopdop.bopdop.playback.VideoQualityPolicy.init(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .crossfade(260)
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("artwork")).maxSizeBytes(256L * 1024 * 1024).build() }
        .build()
}
