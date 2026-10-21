package com.dopdop.bopdop.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import com.dopdop.bopdop.MainActivity
import com.dopdop.bopdop.R
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.model.Track
import kotlinx.coroutines.launch
import coil3.request.allowHardware

/**
 * Android 16 "Live Update" for the song playing: a promoted ongoing notification with a progress
 * bar. Phones show these in their own island or status-bar chip — Samsung's Now Bar, OnePlus Live
 * Alerts, vivo/iQOO Origin Island, Xiaomi Super Island, Pixel's status chip — which don't pick up
 * ordinary media notifications from apps they haven't listed. Shown while music plays, gone when
 * it pauses or stops; off in Settings, or when the user turns promotion off for BopDop.
 */
internal class LiveUpdate(private val ctx: Context) {
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private var shown = false
    /** The cover, loaded once per song (the notification is re-posted every few seconds). */
    private var art: Pair<String, android.graphics.Bitmap?>? = null
    private val scope = kotlinx.coroutines.MainScope()

    fun update(t: Track?, playing: Boolean, positionMs: Long, durationMs: Long) {
        if (Build.VERSION.SDK_INT < 36) return
        if (t == null || !playing || !LibraryStore.settings.liveUpdate || !nm.canPostPromotedNotifications()) { cancel(); return }
        post(t, positionMs, durationMs)
    }

    fun cancel() {
        if (!shown) return
        nm.cancel(ID)
        shown = false
    }

    @RequiresApi(36)
    private fun post(t: Track, positionMs: Long, durationMs: Long) {
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Now playing (island)", NotificationManager.IMPORTANCE_LOW).apply {
                description = "What's playing, in your phone's island or status bar"
                setShowBadge(false)
            })
        }
        val open = PendingIntent.getActivity(
            ctx, 7, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val total = (durationMs / 1000).toInt().coerceAtLeast(1)
        val now = (positionMs / 1000).toInt().coerceIn(0, total)
        val cover = coverFor(t)
        val style = Notification.ProgressStyle()
            .setProgress(now)
            .setProgressSegments(listOf(Notification.ProgressStyle.Segment(total).setColor(ACCENT)))
            // The cover rides along the progress bar where the phone draws one (Pixel, Samsung).
            .apply { cover?.let { setProgressTrackerIcon(android.graphics.drawable.Icon.createWithBitmap(round(it, 96))) } }
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_mono)
            .setContentTitle(t.title)
            .setContentText(t.artist)
            .setSubText("BopDop")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(ACCENT)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .apply { cover?.let { setLargeIcon(it) } }
            // The status-bar chip: as much of the title as fits, without "(From …)" tails.
            .setShortCriticalText(chip(t.title))
            .setStyle(style)
            .addAction(Notification.Action.Builder(null, "Pause", mediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)).build())
            .addAction(Notification.Action.Builder(null, "Next", mediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT)).build())
            .setRequestPromotedOngoing(true)
            .build()
        nm.notify(ID, n)
        shown = true
    }

    /** A media key sent to the playback service, which hands it to the player. */
    private fun mediaKey(code: Int): PendingIntent = PendingIntent.getService(
        ctx, code,
        Intent(Intent.ACTION_MEDIA_BUTTON).setClass(ctx, PlaybackService::class.java)
            .putExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code)),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The cover for [t]; null until it has loaded (it's fetched once, then the next post shows it). */
    private fun coverFor(t: Track): android.graphics.Bitmap? {
        val cached = art
        if (cached != null && cached.first == t.id) return cached.second
        art = t.id to null
        if (t.artwork.isNotBlank()) scope.launch {
            val bmp = runCatching {
                val req = coil3.request.ImageRequest.Builder(ctx).data(com.dopdop.bopdop.data.ytm.YtMusic.resize(t.artwork, 226)).size(226)
                    // A software bitmap: it's drawn onto a canvas to round it, which hardware bitmaps can't do.
                    .allowHardware(false).build()
                (coil3.SingletonImageLoader.get(ctx).execute(req).image as? coil3.BitmapImage)?.bitmap
            }.getOrNull()
            if (art?.first == t.id) art = t.id to bmp
        }
        return null
    }

    private fun round(cover: android.graphics.Bitmap, size: Int): android.graphics.Bitmap {
        val src = if (cover.config == android.graphics.Bitmap.Config.HARDWARE) cover.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else cover
        val out = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
        c.drawCircle(size / 2f, size / 2f, size / 2f, p)
        p.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        c.drawBitmap(src, null, android.graphics.Rect(0, 0, size, size), p)
        return out
    }

    private companion object {
        const val CHANNEL = "now_playing_live"
        const val ID = 4242
        const val ACCENT = 0xFFFF4D2D.toInt()

        fun chip(title: String) = title.replace(Regex("\\s*[(\\[].*$"), "").trim().ifEmpty { title }.take(16)
    }
}
