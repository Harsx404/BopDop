package com.dopdop.bopdop.playback

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.util.LruCache
import android.view.KeyEvent
import android.widget.RemoteViews
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.dopdop.bopdop.MainActivity
import com.dopdop.bopdop.R
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Now Playing: resizable. Square (2x2) shows the cover with a play button, wide (4x1) a bar with
 * progress, large (4x2) the full player with like, times and transport.
 */
class NowPlayingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetUpdater.redraw(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) = WidgetUpdater.redraw(context)
}

/** Up Next: what's playing and the next four songs; tap one to jump to it. */
class UpNextWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetUpdater.redraw(context)
}

/** Quick mixes: one tap into Liked shuffle, a discovery station, Replay or search. */
class QuickMixWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetUpdater.redraw(context)
}

/** Like and jump-to-song from the widgets; acts on the running player. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_LIKE -> WidgetUpdater.current()?.let { t ->
                LibraryStore.init(context.applicationContext)
                LibraryStore.toggleLike(t)
                WidgetUpdater.redraw(context)
            }
            ACTION_JUMP -> PlaybackService.jumpTo(intent.getIntExtra(EXTRA_INDEX, -1))
        }
    }

    companion object {
        const val ACTION_LIKE = "com.dopdop.bopdop.widget.LIKE"
        const val ACTION_JUMP = "com.dopdop.bopdop.widget.JUMP"
        const val EXTRA_INDEX = "index"
    }
}

internal object WidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var track: Track? = null
    private var playing = false
    private var positionMs = 0L
    private var durationMs = 0L
    private var positionAt = 0L
    /** The next songs, with their place in the queue. */
    private var upNext: List<Pair<Int, Track>> = emptyList()

    private val images = LruCache<String, Bitmap>(24)
    private val loading = HashSet<String>()

    fun current(): Track? = track

    /** Always remembers the latest state, so a widget added later shows it straight away. */
    fun update(context: Context, t: Track?, isPlaying: Boolean, posMs: Long = 0, durMs: Long = 0, next: List<Pair<Int, Track>> = upNext) {
        track = t
        playing = isPlaying
        positionMs = posMs
        durationMs = durMs
        positionAt = SystemClock.elapsedRealtime()
        upNext = next
        redraw(context)
    }

    private fun ids(m: AppWidgetManager, ctx: Context, cls: Class<*>) = m.getAppWidgetIds(ComponentName(ctx, cls))

    fun redraw(context: Context) {
        val ctx = context.applicationContext
        val m = AppWidgetManager.getInstance(ctx)
        val np = ids(m, ctx, NowPlayingWidget::class.java)
        val up = ids(m, ctx, UpNextWidget::class.java)
        val quick = ids(m, ctx, QuickMixWidget::class.java)
        if (np.isEmpty() && up.isEmpty() && quick.isEmpty()) return
        // Each widget is drawn at its own size (see WidgetArt), so they're updated one by one.
        val state = WidgetArt.NowPlaying(
            track, playing, livePosition().coerceIn(0, durationMs.coerceAtLeast(0)), durationMs.coerceAtLeast(0),
            track?.let { LibraryStore.isLiked(it.id) } == true, track?.artwork?.let { image(ctx, it, 300) },
        )
        np.forEach { id -> runCatching { m.updateAppWidget(id, nowPlaying(ctx, WidgetArt.sizeOf(ctx, m.getAppWidgetOptions(id), 300f, 150f), state)) } }
        up.forEach { id -> runCatching { m.updateAppWidget(id, upNextViews(ctx, WidgetArt.sizeOf(ctx, m.getAppWidgetOptions(id), 300f, 280f), state)) } }
        quick.forEach { id -> runCatching { m.updateAppWidget(id, quickViews(ctx, WidgetArt.sizeOf(ctx, m.getAppWidgetOptions(id), 300f, 90f))) } }
    }

    private fun livePosition(): Long =
        if (playing) positionMs + (SystemClock.elapsedRealtime() - positionAt) else positionMs

    private fun nowPlaying(ctx: Context, size: WidgetArt.Size, state: WidgetArt.NowPlaying): RemoteViews {
        val shape = WidgetArt.npShape(size.wDp, size.hDp)
        val v = RemoteViews(ctx.packageName, when (shape) {
            WidgetArt.NpShape.STRIP -> R.layout.widget_np_strip
            WidgetArt.NpShape.SQUARE -> R.layout.widget_np_square
            WidgetArt.NpShape.FULL -> R.layout.widget_np_full
        })
        v.setImageViewBitmap(R.id.widget_canvas, WidgetArt.nowPlaying(ctx, size, state))
        v.setOnClickPendingIntent(R.id.widget_open, openApp(ctx, 0, null))
        v.setOnClickPendingIntent(R.id.widget_toggle, mediaButton(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        if (shape != WidgetArt.NpShape.SQUARE) v.setOnClickPendingIntent(R.id.widget_next, mediaButton(ctx, KeyEvent.KEYCODE_MEDIA_NEXT))
        if (shape == WidgetArt.NpShape.FULL) {
            v.setOnClickPendingIntent(R.id.widget_prev, mediaButton(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
            v.setOnClickPendingIntent(R.id.widget_like, action(ctx, WidgetActionReceiver.ACTION_LIKE, 0))
        }
        return v
    }

    private fun upNextViews(ctx: Context, size: WidgetArt.Size, state: WidgetArt.NowPlaying): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_up_next)
        val next = upNext.take(4).map { (_, song) -> song to image(ctx, song.artwork, 120) }
        v.setImageViewBitmap(R.id.widget_canvas, WidgetArt.upNext(ctx, size, state, next))
        v.setOnClickPendingIntent(R.id.widget_open, openApp(ctx, 0, null))
        v.setOnClickPendingIntent(R.id.widget_toggle, mediaButton(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        listOf(R.id.widget_q0, R.id.widget_q1, R.id.widget_q2, R.id.widget_q3).forEachIndexed { i, row ->
            val item = upNext.getOrNull(i)
            v.setViewVisibility(row, if (item == null) android.view.View.GONE else android.view.View.VISIBLE)
            if (item != null) v.setOnClickPendingIntent(row, action(ctx, WidgetActionReceiver.ACTION_JUMP, item.first))
        }
        return v
    }

    private val tiles = listOf(
        WidgetArt.Tile("Liked", "Shuffle your hearts", 0xFFFF5E7E.toInt(), 0xFFFF4D2D.toInt(), R.drawable.ic_w_heart_fill),
        WidgetArt.Tile("Discover", "New to you", 0xFF8C6BFF.toInt(), 0xFF4F7BFF.toInt(), R.drawable.ic_w_sparkle),
        WidgetArt.Tile("Replay", "Your top songs", 0xFFFFB347.toInt(), 0xFFFF6A3D.toInt(), R.drawable.ic_w_history),
        WidgetArt.Tile("Search", "Find anything", 0xFF3A3940.toInt(), 0xFF1E1D22.toInt(), R.drawable.ic_w_search),
    )

    private fun quickViews(ctx: Context, size: WidgetArt.Size): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_quick)
        v.setImageViewBitmap(R.id.widget_canvas, WidgetArt.quick(ctx, size, tiles))
        v.setOnClickPendingIntent(R.id.widget_quick_liked, openApp(ctx, 11, MainActivity.QUICK_LIKED))
        v.setOnClickPendingIntent(R.id.widget_quick_discover, openApp(ctx, 12, MainActivity.QUICK_DISCOVER))
        v.setOnClickPendingIntent(R.id.widget_quick_replay, openApp(ctx, 13, MainActivity.QUICK_REPLAY))
        v.setOnClickPendingIntent(R.id.widget_quick_search, openApp(ctx, 14, MainActivity.QUICK_SEARCH))
        return v
    }

    private fun openApp(ctx: Context, code: Int, quick: String?): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { if (quick == null) putExtra(MainActivity.EXTRA_OPEN_PLAYER, true) else putExtra(MainActivity.EXTRA_QUICK, quick) }
        return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun action(ctx: Context, action: String, index: Int): PendingIntent {
        val i = Intent(ctx, WidgetActionReceiver::class.java).setAction(action).putExtra(WidgetActionReceiver.EXTRA_INDEX, index)
        return PendingIntent.getBroadcast(ctx, action.hashCode() + index, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** The same media-button intent a headset sends; the session service handles it, even from cold. */
    private fun mediaButton(ctx: Context, code: Int): PendingIntent {
        val i = Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(ComponentName(ctx, PlaybackService::class.java))
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, code))
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return if (Build.VERSION.SDK_INT >= 26) PendingIntent.getForegroundService(ctx, code, i, flags)
        else PendingIntent.getService(ctx, code, i, flags)
    }

    /** A cover at [size] px, from memory, or null while it loads (the widgets redraw once it has). */
    private fun image(ctx: Context, url: String, size: Int): Bitmap? {
        if (url.isBlank()) return null
        val key = "$url@$size"
        images.get(key)?.let { return it }
        if (!loading.add(key)) return null
        scope.launch {
            val bmp = load(ctx, url, size)
            loading.remove(key)
            if (bmp != null) {
                images.put(key, WidgetArt.soft(bmp) ?: bmp)
                redraw(ctx)
            }
        }
        return null
    }

    private suspend fun load(ctx: Context, url: String, size: Int): Bitmap? = runCatching {
        SingletonImageLoader.get(ctx).execute(
            ImageRequest.Builder(ctx).data(url).size(size).allowHardware(false).build(),
        ).image?.toBitmap()
    }.getOrNull()
}
