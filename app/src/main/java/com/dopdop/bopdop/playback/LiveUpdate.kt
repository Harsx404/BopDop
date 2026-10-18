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
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_w_play)
            .setContentTitle(t.title)
            .setContentText(t.artist)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setShortCriticalText(t.title.take(14))
            .setStyle(Notification.ProgressStyle().setProgress(now).setProgressSegments(listOf(Notification.ProgressStyle.Segment(total))))
            .setRequestPromotedOngoing(true)
            .build()
        nm.notify(ID, n)
        shown = true
    }

    private companion object {
        const val CHANNEL = "now_playing_live"
        const val ID = 4242
    }
}
