package com.dopdop.bopdop.playback

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
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
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.dopdop.bopdop.MainActivity
import com.dopdop.bopdop.R
import com.dopdop.bopdop.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Home-screen glass mini player. The playback service pushes every change here. */
class NowPlayingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetUpdater.redraw(context)
}

internal object WidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var track: Track? = null
    private var playing = false
    private var art: Bitmap? = null
    private var bg: Bitmap? = null
    private var artFor: String? = null

    /** Always remembers the latest state, so a widget added later shows it straight away. */
    fun update(context: Context, t: Track?, isPlaying: Boolean) {
        track = t
        playing = isPlaying
        redraw(context)
    }

    fun redraw(context: Context) {
        val ctx = context.applicationContext
        val manager = AppWidgetManager.getInstance(ctx)
        val ids = manager.getAppWidgetIds(ComponentName(ctx, NowPlayingWidget::class.java))
        if (ids.isEmpty()) return
        val t = track
        if (t != null && t.artwork != artFor) loadArt(ctx, t.artwork)
        val v = RemoteViews(ctx.packageName, R.layout.widget_now_playing)
        v.setTextViewText(R.id.widget_title, t?.title ?: "BopDop")
        v.setTextViewText(R.id.widget_artist, t?.artist ?: "Tap to play something")
        v.setImageViewResource(R.id.widget_toggle, if (playing) R.drawable.ic_w_pause else R.drawable.ic_w_play)
        art?.let { v.setImageViewBitmap(R.id.widget_art, it) } ?: v.setImageViewResource(R.id.widget_art, R.drawable.ic_launcher_fg)
        v.setImageViewBitmap(R.id.widget_bg, bg ?: backdrop(null))
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        v.setOnClickPendingIntent(R.id.widget_art, open)
        v.setOnClickPendingIntent(R.id.widget_title, open)
        v.setOnClickPendingIntent(R.id.widget_toggle, mediaButton(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        v.setOnClickPendingIntent(R.id.widget_next, mediaButton(ctx, KeyEvent.KEYCODE_MEDIA_NEXT))
        v.setOnClickPendingIntent(R.id.widget_prev, mediaButton(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        manager.updateAppWidget(ids, v)
    }

    private fun loadArt(ctx: Context, url: String) {
        artFor = url
        scope.launch {
            val bmp = runCatching {
                SingletonImageLoader.get(ctx).execute(
                    ImageRequest.Builder(ctx).data(url).size(300).allowHardware(false).build(),
                ).image?.toBitmap()
            }.getOrNull()
            if (artFor != url) return@launch
            art = bmp?.let(::rounded)
            bg = backdrop(bmp)
            redraw(ctx)
        }
    }

    /** The same media-button intent a headset sends; the session service handles it, even from cold. */
    private fun mediaButton(ctx: Context, code: Int): PendingIntent {
        val i = Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(ComponentName(ctx, PlaybackService::class.java))
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, code))
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return if (Build.VERSION.SDK_INT >= 26) PendingIntent.getForegroundService(ctx, code, i, flags)
        else PendingIntent.getService(ctx, code, i, flags)
    }

    private fun rounded(src: Bitmap): Bitmap {
        val size = 192
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val path = Path().apply { addRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 34f, 34f, Path.Direction.CW) }
        c.clipPath(path)
        c.drawBitmap(Bitmap.createScaledBitmap(src, size, size, true), 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** Rounded panel in the artwork's colours with a soft glass sheen across the top. */
    private fun backdrop(src: Bitmap?): Bitmap {
        val w = 900; val h = 280; val r = 72f
        val p = src?.let { Palette.from(it).generate() }
        val top = p?.getDarkVibrantColor(p.getDarkMutedColor(0xFF3A3550.toInt())) ?: 0xFF3A3550.toInt()
        val bottom = p?.getMutedColor(0xFF1A1A26.toInt())?.let { darken(it) } ?: 0xFF1A1A26.toInt()
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        c.drawRoundRect(rect, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), top, bottom, Shader.TileMode.CLAMP) })
        c.drawRoundRect(rect, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, h * 0.6f, 0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        })
        c.drawRoundRect(RectF(1.5f, 1.5f, w - 1.5f, h - 1.5f), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f; color = 0x40FFFFFF
        })
        return out
    }

    private fun darken(c: Int): Int {
        val f = 0.55f
        return (0xFF shl 24) or (((c shr 16 and 0xFF) * f).toInt() shl 16) or (((c shr 8 and 0xFF) * f).toInt() shl 8) or ((c and 0xFF) * f).toInt()
    }
}
