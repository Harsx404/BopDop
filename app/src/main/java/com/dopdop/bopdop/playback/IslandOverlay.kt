package com.dopdop.bopdop.playback

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.media3.common.Player
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dopdop.bopdop.MainActivity
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.ui.island.IslandModel
import com.dopdop.bopdop.ui.island.IslandPill
import com.dopdop.bopdop.ui.island.cameraHole
import com.dopdop.bopdop.ui.theme.BopDopTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether BopDop's own screen is showing; the island moves out of the app only when it isn't. */
object AppVisibility {
    private val _foreground = MutableStateFlow(false)
    val foreground: StateFlow<Boolean> = _foreground.asStateFlow()
    fun set(visible: Boolean) { _foreground.value = visible }
}

/**
 * The now-playing island drawn over other apps, around the camera cutout (Android 11+, opt-in,
 * needs "display over other apps"). It lives in the playback service and talks to the player
 * directly. The window is only as tall as the pill (and sits under the status bar, so pulling
 * down the shade still works); it grows while expanded and shrinks back on any touch outside.
 */
internal class IslandOverlay(private val ctx: Context, private val player: () -> Player) : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this).apply { performRestore(null) }
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    private val wm = ctx.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var track by mutableStateOf<Track?>(null)
    private var playing by mutableStateOf(false)
    private var expanded by mutableStateOf(false)

    fun update(t: Track?, isPlaying: Boolean) {
        track = t
        playing = isPlaying
        refresh()
    }

    /** Show or hide to match the setting, the permission, the app's visibility and the music. */
    fun refresh() {
        val cut = cutout()
        val want = Build.VERSION.SDK_INT >= 30 && LibraryStore.settings.islandOverlay && Settings.canDrawOverlays(ctx) &&
            !AppVisibility.foreground.value && track != null && cut != null
        if (want) show() else hide()
    }

    private fun cutout(): android.graphics.Rect? {
        if (Build.VERSION.SDK_INT < 30) return null
        val m = wm.currentWindowMetrics
        val cutout = m.windowInsets.displayCutout ?: return null
        return cameraHole(cutout, m.bounds.width())
    }

    /** Collapsed, the window is just the pill and its tap tab, so it never covers the app below. */
    private var windowLeft by mutableStateOf(0)

    private fun params(expanded: Boolean): WindowManager.LayoutParams {
        val density = ctx.resources.displayMetrics.density
        val cut = cutout()
        val screenW = wm.currentWindowMetrics.bounds.width()
        val box = cut?.let { com.dopdop.bopdop.ui.island.collapsedBounds(it, density, screenW) }
        val h = if (expanded) (cut?.centerY() ?: 0) + (180 * density).toInt() else box?.bottom ?: 0
        windowLeft = if (expanded) 0 else box?.left ?: 0
        return WindowManager.LayoutParams(
            if (expanded || box == null) WindowManager.LayoutParams.MATCH_PARENT else box.width(), h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = windowLeft
            if (Build.VERSION.SDK_INT >= 30) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun show() {
        if (view != null) return
        val v = ComposeView(ctx).apply {
            setViewTreeLifecycleOwner(this@IslandOverlay)
            setViewTreeSavedStateRegistryOwner(this@IslandOverlay)
            setOnTouchListener { _, e -> if (e.action == MotionEvent.ACTION_OUTSIDE && expanded) expandTo(false); false }
            setContent {
                BopDopTheme {
                    val cut = cutout() ?: return@BopDopTheme
                    val p = player()
                    IslandPill(
                        IslandModel(
                            track, playing, { p.currentPosition }, { p.duration.coerceAtLeast(0) },
                            toggle = { if (p.isPlaying) p.pause() else p.play() },
                            next = { p.seekToNextMediaItem() }, previous = { p.seekToPreviousMediaItem() },
                            open = { expandTo(false); openApp() },
                        ),
                        cut, expanded, ::expandTo, wm.currentWindowMetrics.bounds.width(), windowLeft,
                    )
                }
            }
        }
        runCatching { wm.addView(v, params(expanded)) }.onSuccess {
            view = v
            registry.currentState = Lifecycle.State.RESUMED
        }
    }

    private fun expandTo(on: Boolean) {
        expanded = on
        // Grow the window before the card grows; shrink it once the card has shrunk.
        val v = view ?: return
        if (on) runCatching { wm.updateViewLayout(v, params(true)) }
        else v.postDelayed({ if (!expanded && view === v) runCatching { wm.updateViewLayout(v, params(false)) } }, 380)
    }

    private fun openApp() {
        ctx.startActivity(
            Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }

    private fun hide() {
        val v = view ?: return
        view = null
        expanded = false
        runCatching { wm.removeView(v) }
        registry.currentState = Lifecycle.State.CREATED
    }

    fun release() {
        hide()
        if (registry.currentState != Lifecycle.State.INITIALIZED) registry.currentState = Lifecycle.State.DESTROYED
    }
}
