package com.dopdop.bopdop.playback

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.dopdop.bopdop.MainActivity
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.MusicRepository
import com.dopdop.bopdop.data.PlayStats
import com.dopdop.bopdop.data.net.Http
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val scope = MainScope()
    private var extending = false
    private val retried = HashSet<String>()

    override fun onCreate() {
        super.onCreate()
        LibraryStore.init(this)
        PlayStats.init(this)

        val resolving = ResolvingDataSource.Factory(OkHttpDataSource.Factory(Http.client)) { spec -> resolve(spec) }
        val dataSource = CacheDataSource.Factory()
            .setCache(cache(this))
            .setUpstreamDataSourceFactory(resolving)
            .setCacheKeyFactory { spec -> spec.key ?: spec.uri.getQueryParameter("id") ?: spec.uri.toString() }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(listener)
        // Replay: count real listening time, one second at a time.
        scope.launch {
            while (isActive) {
                delay(1000)
                if (player.isPlaying) player.currentMediaItem?.toTrack()?.let { PlayStats.tick(it) }
            }
        }

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback)
            .setSessionActivity(open)
            .build()
    }

    /** `bopdop://play?...` → real CDN url, carrying the User-Agent the url is bound to. */
    private fun resolve(spec: DataSpec): DataSpec {
        if (spec.uri.scheme != StreamResolver.SCHEME) return spec
        val s = StreamResolver.resolve(spec.uri)
        val headers = HashMap(spec.httpRequestHeaders)
        s.userAgent?.let { headers["User-Agent"] = it }
        return spec.buildUpon().setUri(s.url).setHttpRequestHeaders(headers).build()
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.toTrack()?.let(LibraryStore::recordPlay)
            PlayStats.newSession()
            maybeExtendQueue()
        }

        override fun onPlayerError(error: PlaybackException) {
            val item = player.currentMediaItem ?: return
            val id = item.mediaId
            Log.w("PlaybackService", "error on $id: ${error.errorCodeName}", error)
            StreamResolver.invalidate(id)
            // A partial download from a dead url may be a different format than the next one — drop it.
            runCatching { cache(this@PlaybackService).removeResource(id) }
            if (retried.add(id)) {
                // Expired / IP-bound url: resolve again (other clients get a turn) and resume in place.
                player.prepare()
                player.play()
            } else if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) player.currentMediaItem?.mediaId?.let(retried::remove)
        }
    }

    /** Autoplay: when the queue is about to run dry, append related tracks. */
    private fun maybeExtendQueue() {
        if (!LibraryStore.settings.autoplay || extending) return
        if (player.repeatMode != Player.REPEAT_MODE_OFF) return
        val remaining = player.mediaItemCount - player.currentMediaItemIndex - 1
        if (remaining > 1) return
        val seed = player.currentMediaItem?.toTrack() ?: return
        extending = true
        scope.launch {
            try {
                val have = (0 until player.mediaItemCount).mapTo(HashSet()) { player.getMediaItemAt(it).mediaId }
                val more = MusicRepository.related(seed).filter { it.id !in have }.take(20)
                if (more.isNotEmpty()) player.addMediaItems(more.map { it.toMediaItem() })
            } catch (e: Exception) {
                Log.w("PlaybackService", "autoplay failed: ${e.message}")
            } finally {
                extending = false
            }
        }
    }

    private object SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { it.withPlaybackUri() }.toMutableList())
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var simpleCache: SimpleCache? = null

        fun cache(ctx: android.content.Context): SimpleCache = simpleCache ?: synchronized(this) {
            simpleCache ?: SimpleCache(
                File(ctx.cacheDir, "audio"),
                LeastRecentlyUsedCacheEvictor(LibraryStore.settings.cacheMb * 1024L * 1024L),
                StandaloneDatabaseProvider(ctx),
            ).also { simpleCache = it }
        }
    }
}
