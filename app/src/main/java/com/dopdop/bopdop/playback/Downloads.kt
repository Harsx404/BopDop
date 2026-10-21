package com.dopdop.bopdop.playback

import android.app.Notification
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.dopdop.bopdop.R
import com.dopdop.bopdop.data.MusicRepository
import com.dopdop.bopdop.data.lyrics.LyricsDisk
import com.dopdop.bopdop.data.model.Source
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.net.Http
import com.dopdop.bopdop.data.ytm.YtMusic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** One database for every ExoPlayer cache in the app (they share its index tables). */
@OptIn(UnstableApi::class)
internal object Databases {
    @Volatile private var provider: StandaloneDatabaseProvider? = null
    fun provider(ctx: Context): StandaloneDatabaseProvider = provider ?: synchronized(this) {
        provider ?: StandaloneDatabaseProvider(ctx.applicationContext).also { provider = it }
    }
}

/**
 * Offline downloads. Songs are fetched whole, at High quality, into their own cache that is
 * never trimmed (unlike the streaming cache), keyed by track id; playback reads it first, so a
 * downloaded song plays with no network at all. Lyrics and cover art are saved alongside.
 */
@OptIn(UnstableApi::class)
object Downloads {
    /** [state] is a [Download] state; [percent] 0..100 while downloading. */
    data class Status(val state: Int, val percent: Float)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _status = MutableStateFlow<Map<String, Status>>(emptyMap())
    val status: StateFlow<Map<String, Status>> = _status.asStateFlow()
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    /** Finished downloads, newest first. */
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    @Volatile private var cache: SimpleCache? = null
    @Volatile private var manager: DownloadManager? = null
    private var notifications: DownloadNotificationHelper? = null
    private var poll: Job? = null
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        manager(appContext)
    }

    fun isDownloaded(id: String) = _status.value[id]?.state == Download.STATE_COMPLETED

    internal fun cache(ctx: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(File(ctx.applicationContext.filesDir, "downloads"), NoOpCacheEvictor(), Databases.provider(ctx)).also { cache = it }
    }

    internal fun manager(ctx: Context): DownloadManager = manager ?: synchronized(this) {
        manager ?: run {
            val upstream = ResolvingDataSource.Factory(OkHttpDataSource.Factory(Http.client)) { spec -> StreamResolver.resolveSpec(spec) }
            DownloadManager(ctx.applicationContext, Databases.provider(ctx), cache(ctx), upstream, Executors.newFixedThreadPool(2)).apply {
                maxParallelDownloads = 2
                addListener(object : DownloadManager.Listener {
                    override fun onDownloadChanged(m: DownloadManager, download: Download, finalException: Exception?) {
                        refresh()
                        if (download.state == Download.STATE_COMPLETED) {
                            trackOf(download)?.let { t -> scope.launch { keepExtras(ctx.applicationContext, t) } }
                        }
                    }
                    override fun onDownloadRemoved(m: DownloadManager, download: Download) {
                        LyricsDisk.delete(ctx.applicationContext, download.request.id)
                        refresh()
                    }
                    override fun onInitialized(m: DownloadManager) = refresh()
                })
            }.also { manager = it }
        }
    }

    internal fun notificationHelper(ctx: Context): DownloadNotificationHelper = notifications
        ?: DownloadNotificationHelper(ctx.applicationContext, BopDopDownloadService.CHANNEL).also { notifications = it }

    /** Download songs (YouTube Music and JioSaavn; songs already on the phone are skipped). */
    fun download(ctx: Context, tracks: List<Track>) {
        tracks.filter { it.source != Source.LOCAL && !isDownloaded(it.id) }.forEach { t ->
            // Downloads always take High quality, whatever network they happen on.
            val uri = StreamResolver.playUri(t).buildUpon().appendQueryParameter("q", "HIGH").build()
            val req = DownloadRequest.Builder(t.id, uri)
                .setCustomCacheKey(t.id)
                .setData(t.toJson().toString().toByteArray())
                .build()
            DownloadService.sendAddDownload(ctx, BopDopDownloadService::class.java, req, false)
        }
    }

    fun remove(ctx: Context, id: String) =
        DownloadService.sendRemoveDownload(ctx, BopDopDownloadService::class.java, id, false)

    private fun trackOf(d: Download): Track? = runCatching { Track.fromJson(JSONObject(String(d.request.data))) }.getOrNull()

    private fun refresh() {
        val m = manager ?: return
        val status = HashMap<String, Status>()
        val done = ArrayList<Pair<Long, Track>>()
        m.downloadIndex.getDownloads().use { c ->
            while (c.moveToNext()) {
                val d = c.download
                status[d.request.id] = Status(d.state, d.percentDownloaded.coerceAtLeast(0f))
                if (d.state == Download.STATE_COMPLETED) trackOf(d)?.let { done += d.updateTimeMs to it }
            }
        }
        // Live progress lives on the manager's current downloads, not in the index.
        m.currentDownloads.forEach { d -> status[d.request.id] = Status(d.state, d.percentDownloaded.coerceAtLeast(0f)) }
        _status.value = status
        _tracks.value = done.sortedByDescending { it.first }.map { it.second }
        // While something is downloading, refresh its percentage every second.
        if (m.currentDownloads.any { it.state == Download.STATE_DOWNLOADING }) {
            if (poll?.isActive != true) poll = scope.launch(Dispatchers.Main) { while (isActive) { delay(1000); refresh() } }
        } else poll?.cancel()
    }

    /** Lyrics to disk, and cover art warmed into the image cache at the sizes the app shows. */
    private suspend fun keepExtras(ctx: Context, t: Track) {
        if (!LyricsDisk.has(ctx, t.id)) runCatching { MusicRepository.lyrics(t) }.getOrNull()?.let { LyricsDisk.write(ctx, t.id, it) }
        if (t.artwork.isNotBlank()) listOf(120, 226, 300, 544, 600, 1000).forEach { size ->
            runCatching { SingletonImageLoader.get(ctx).execute(ImageRequest.Builder(ctx).data(YtMusic.resize(t.artwork, size)).build()) }
        }
    }
}

/** Runs downloads in the background with a progress notification. */
@OptIn(UnstableApi::class)
class BopDopDownloadService : DownloadService(
    NOTIFICATION_ID, DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL, CHANNEL, R.string.downloads_channel, 0,
) {
    override fun getDownloadManager(): DownloadManager = Downloads.manager(this)
    override fun getScheduler(): Scheduler? = null
    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification =
        Downloads.notificationHelper(this).buildProgressNotification(this, R.drawable.ic_w_download, null, null, downloads, notMetRequirements)

    companion object {
        const val CHANNEL = "downloads"
        private const val NOTIFICATION_ID = 7301
    }
}
