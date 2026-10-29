package com.dopdop.bopdop.ui.festive

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dopdop.bopdop.ui.LocalTabActive
import com.dopdop.bopdop.ui.glass.LocalReduceMotion

/** Respect both BopDop's preference and Android's Remove animations setting. */
@Composable
fun rememberReducedMotion(preference: Boolean): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun systemReduced() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var system by remember(resolver) { mutableStateOf(systemReduced()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { system = systemReduced() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return preference || system
}

/** A draw-phase clock, bounded to 24 fps and stopped off-screen, in background, or with motion reduced. */
@Composable
fun rememberHalloweenTime(enabled: Boolean = true, fps: Int = 24): State<Float> {
    if (!enabled) return remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsState()
    val active = enabled && !LocalReduceMotion.current && LocalTabActive.current && state.isAtLeast(Lifecycle.State.STARTED)
    return produceState(0f, active, fps) {
        if (!active) return@produceState
        val start = withFrameNanos { it }
        val base = value
        var last = start
        val interval = 1_000_000_000L / fps.coerceIn(1, 30)
        while (true) withFrameNanos {
            if (it - last >= interval) { value = base + (it - start) / 1_000_000_000f; last = it }
        }
    }
}
