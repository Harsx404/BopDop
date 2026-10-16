package com.dopdop.bopdop.ui

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import kotlin.math.abs
import kotlin.math.roundToInt

private fun Context.mainDisplay(): Display? = getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)

/** Refresh rates the screen offers at its current resolution, e.g. [60, 90, 120]. */
fun Context.refreshRates(): List<Int> {
    val d = mainDisplay() ?: return emptyList()
    val now = d.mode
    return d.supportedModes.filter { it.physicalWidth == now.physicalWidth && it.physicalHeight == now.physicalHeight }
        .map { it.refreshRate.roundToInt() }.distinct().sorted()
}

/** Ask the window for [hz] (0 = let the system decide). Android may still lower it to save power. */
fun Activity.applyRefreshRate(hz: Int) {
    val d = mainDisplay() ?: return
    val now = d.mode
    val mode = if (hz <= 0) null else d.supportedModes
        .filter { it.physicalWidth == now.physicalWidth && it.physicalHeight == now.physicalHeight }
        .minByOrNull { abs(it.refreshRate - hz) }
    window.attributes = window.attributes.apply {
        preferredDisplayModeId = mode?.modeId ?: 0
        preferredRefreshRate = mode?.refreshRate ?: 0f
    }
}
