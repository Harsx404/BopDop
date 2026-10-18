package com.dopdop.bopdop.playback

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.util.LruCache
import android.util.SizeF
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.dopdop.bopdop.MainActivity
import com.dopdop.bopdop.R
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.model.formatDuration
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
    private var bgFor: String? = null
    private var bgWide: Bitmap? = null
    private var bgTall: Bitmap? = null

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
        track?.artwork?.let { art -> if (art != bgFor) backdrops(ctx, art) }

        if (np.isNotEmpty()) {
            val views = if (Build.VERSION.SDK_INT >= 31) RemoteViews(
                mapOf(
                    SizeF(110f, 110f) to nowPlaying(ctx, R.layout.widget_np_small),
                    SizeF(220f, 60f) to nowPlaying(ctx, R.layout.widget_now_playing),
                    SizeF(220f, 160f) to nowPlaying(ctx, R.layout.widget_np_large),
                ),
            ) else nowPlaying(ctx, R.layout.widget_now_playing)
            m.updateAppWidget(np, views)
        }
        if (up.isNotEmpty()) m.updateAppWidget(up, upNextViews(ctx))
        if (quick.isNotEmpty()) m.updateAppWidget(quick, quickViews(ctx))
    }

    private fun livePosition(): Long =
        if (playing) positionMs + (SystemClock.elapsedRealtime() - positionAt) else positionMs

    private fun nowPlaying(ctx: Context, layout: Int): RemoteViews {
        val t = track
        val v = RemoteViews(ctx.packageName, layout)
        v.setTextViewText(R.id.widget_title, t?.title ?: "BopDop")
        v.setTextViewText(R.id.widget_artist, t?.artist ?: "Tap to play something")
        val big = layout == R.layout.widget_np_small
        val art = t?.artwork?.let { image(ctx, it, if (big) 600 else 240, rounded = !big) }
        if (art != null) v.setImageViewBitmap(R.id.widget_art, art) else v.setImageViewResource(R.id.widget_art, R.drawable.ic_launcher_fg)
        if (layout != R.layout.widget_np_small) v.setImageViewBitmap(R.id.widget_bg, (if (layout == R.layout.widget_np_large) bgTall else bgWide) ?: plainBackdrop())
        transport(ctx, v)
        val dur = durationMs.coerceAtLeast(0)
        val pos = livePosition().coerceIn(0, dur.coerceAtLeast(1))
        v.setProgressBar(R.id.widget_progress, 1000, if (dur > 0) (pos * 1000 / dur).toInt() else 0, false)
        if (layout == R.layout.widget_np_large) {
            v.setTextViewText(R.id.widget_pos, formatDuration((pos / 1000).toInt()).ifEmpty { "0:00" })
            v.setTextViewText(R.id.widget_dur, formatDuration((dur / 1000).toInt()).ifEmpty { "0:00" })
            val liked = t != null && LibraryStore.isLiked(t.id)
            v.setImageViewResource(R.id.widget_like, if (liked) R.drawable.ic_w_heart_fill else R.drawable.ic_w_heart)
            v.setInt(R.id.widget_like, "setColorFilter", if (liked) 0xFFFF3B6B.toInt() else 0xFFFFFFFF.toInt())
            v.setOnClickPendingIntent(R.id.widget_like, action(ctx, WidgetActionReceiver.ACTION_LIKE, 0))
        }
        return v
    }

    /** Play/pause (ink on the white disc), next and previous, plus tapping the song opens the player. */
    private fun transport(ctx: Context, v: RemoteViews) {
        v.setImageViewResource(R.id.widget_toggle, if (playing) R.drawable.ic_w_pause_ink else R.drawable.ic_w_play_ink)
        v.setOnClickPendingIntent(R.id.widget_toggle, mediaButton(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        v.setOnClickPendingIntent(R.id.widget_next, mediaButton(ctx, KeyEvent.KEYCODE_MEDIA_NEXT))
        v.setOnClickPendingIntent(R.id.widget_prev, mediaButton(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        val open = openApp(ctx, 0, null)
        v.setOnClickPendingIntent(R.id.widget_art, open)
        v.setOnClickPendingIntent(R.id.widget_text, open)
    }

    private fun upNextViews(ctx: Context): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_up_next)
        val t = track
        v.setTextViewText(R.id.widget_title, t?.title ?: "Up next")
        v.setTextViewText(R.id.widget_artist, t?.let { "Now playing · ${it.artist}" } ?: "Play something to fill your queue")
        v.setImageViewBitmap(R.id.widget_bg, bgTall ?: plainBackdrop())
        transport(ctx, v)
        val rows = listOf(
            listOf(R.id.widget_q0, R.id.widget_q0_art, R.id.widget_q0_title, R.id.widget_q0_artist),
            listOf(R.id.widget_q1, R.id.widget_q1_art, R.id.widget_q1_title, R.id.widget_q1_artist),
            listOf(R.id.widget_q2, R.id.widget_q2_art, R.id.widget_q2_title, R.id.widget_q2_artist),
            listOf(R.id.widget_q3, R.id.widget_q3_art, R.id.widget_q3_title, R.id.widget_q3_artist),
        )
        rows.forEachIndexed { i, (row, art, title, artist) ->
            val item = upNext.getOrNull(i)
            if (item == null) { v.setViewVisibility(row, android.view.View.INVISIBLE); return@forEachIndexed }
            val (index, song) = item
            v.setViewVisibility(row, android.view.View.VISIBLE)
            v.setTextViewText(title, song.title)
            v.setTextViewText(artist, song.artist)
            image(ctx, song.artwork, 120, rounded = true)?.let { v.setImageViewBitmap(art, it) } ?: v.setImageViewResource(art, R.drawable.ic_launcher_fg)
            v.setOnClickPendingIntent(row, action(ctx, WidgetActionReceiver.ACTION_JUMP, index))
        }
        return v
    }

    private fun quickViews(ctx: Context): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_quick)
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
    private fun image(ctx: Context, url: String, size: Int, rounded: Boolean): Bitmap? {
        if (url.isBlank()) return null
        val key = "$url@$size@$rounded"
        images.get(key)?.let { return it }
        if (!loading.add(key)) return null
        scope.launch {
            val bmp = load(ctx, url, size)
            loading.remove(key)
            if (bmp != null) {
                images.put(key, if (rounded) roundedCopy(bmp, size) else bmp)
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

    private fun backdrops(ctx: Context, url: String) {
        bgFor = url
        scope.launch {
            val bmp = load(ctx, url, 160)
            if (bgFor != url) return@launch
            bgWide = backdrop(bmp, 900, 280)
            bgTall = backdrop(bmp, 900, 560)
            redraw(ctx)
        }
    }

    private fun roundedCopy(src: Bitmap, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val r = size * 0.18f
        c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), r, r, Path.Direction.CW) })
        c.drawBitmap(Bitmap.createScaledBitmap(src, size, size, true), 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun plainBackdrop() = backdrop(null, 900, 280)

    /** A panel in the artwork's colours with a soft sheen across the top. */
    private fun backdrop(src: Bitmap?, w: Int, h: Int): Bitmap {
        val p = src?.let { Palette.from(it).generate() }
        val top = p?.getDarkVibrantColor(p.getDarkMutedColor(0xFF3A3550.toInt())) ?: 0xFF2A2833.toInt()
        val bottom = p?.getMutedColor(0xFF1A1A26.toInt())?.let { darken(it) } ?: 0xFF15141A.toInt()
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), top, bottom, Shader.TileMode.CLAMP) })
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, 0f, h * 0.6f, 0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP) })
        return out
    }

    private fun darken(c: Int): Int {
        val f = 0.5f
        return (0xFF shl 24) or (((c shr 16 and 0xFF) * f).toInt() shl 16) or (((c shr 8 and 0xFF) * f).toInt() shl 8) or ((c and 0xFF) * f).toInt()
    }
}
