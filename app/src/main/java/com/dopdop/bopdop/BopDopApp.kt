package com.dopdop.bopdop

import kotlinx.coroutines.launch
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
        com.dopdop.bopdop.data.ShortsStore.init(this)
        com.dopdop.bopdop.data.account.AccountStore.init(this)
        com.dopdop.bopdop.playback.StreamResolver.init(this)
        com.dopdop.bopdop.data.lyrics.LyricOffsets.init(this)
        com.dopdop.bopdop.data.lyrics.LyricsApi.init(this)
        com.dopdop.bopdop.data.scrobble.ListenBrainz.init(this)
        com.dopdop.bopdop.data.scrobble.ListenBrainz.flush()
        com.dopdop.bopdop.data.scrobble.LastFm.init(this)
        com.dopdop.bopdop.data.spotify.SpotifyAccount.init(this)
        com.dopdop.bopdop.data.spotify.SpotifyToken.init(this)
        com.dopdop.bopdop.data.spotify.SpotifyMatch.init(this)
        // Spotify playlists sync quietly a little after launch (at most every 12 hours).
        kotlinx.coroutines.MainScope().launch {
            kotlinx.coroutines.delay(45_000)
            com.dopdop.bopdop.data.spotify.SpotifyImport.auto(this@BopDopApp)
        }
        com.dopdop.bopdop.playback.BitPerfect.init(this)
        com.dopdop.bopdop.playback.QueueStore.init(this)
        com.dopdop.bopdop.data.ProblemReport.install(this)
        com.dopdop.bopdop.data.addon.DeviceAudio.warm()
        com.dopdop.bopdop.data.scrobble.LastFm.flush()
        com.dopdop.bopdop.playback.Downloads.init(this)
        com.dopdop.bopdop.data.ytm.PoTokens.init(this)
        com.dopdop.bopdop.playback.VideoQualityPolicy.init(this)
        com.dopdop.bopdop.data.DailyMixes.init(this)
        com.dopdop.bopdop.data.CatalogCache.init(this)
        com.dopdop.bopdop.playback.VocalModel.init(this)
        // Read last launch's Home off the disk now, so it's ready before Home's first frame.
        kotlinx.coroutines.MainScope().launch(kotlinx.coroutines.Dispatchers.IO) { com.dopdop.bopdop.data.CatalogCache.lastHome() }
        com.dopdop.bopdop.data.together.Together.init(this)
        runCatching { androidx.media3.cast.Cast.getSingletonInstance(this).initialize() }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .crossfade(260)
        .components { add(coil3.svg.SvgDecoder.Factory()) }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("artwork")).maxSizeBytes(256L * 1024 * 1024).build() }
        .build()
}
