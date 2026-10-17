package com.dopdop.bopdop.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarsTest {
    private val black = 0xFF000000.toInt()
    private val scene = 0xFF7A6040.toInt()

    /** A [w]x[h] frame whose rows/columns inside the given band are picture and the rest black. */
    private fun frame(w: Int, h: Int, top: Int = 0, bottom: Int = 0, left: Int = 0, right: Int = 0) =
        IntArray(w * h) { i -> val x = i % w; val y = i / w; if (y < top || y >= h - bottom || x < left || x >= w - right) black else scene }

    @Test fun findsCinemaLetterbox() {
        val b = detectBars(frame(64, 36, top = 5, bottom = 5), 64, 36)!!
        assertEquals(5f / 36, b.top, 0.001f); assertEquals(5f / 36, b.bottom, 0.001f); assertEquals(0f, b.left, 0f)
    }

    @Test fun findsPillarbox() {
        val b = detectBars(frame(64, 36, left = 8, right = 8), 64, 36)!!
        assertEquals(8f / 64, b.left, 0.001f); assertEquals(0f, b.top, 0f)
    }

    @Test fun ignoresFullFramesAndBlackFrames() {
        assertEquals(Bars.NONE, detectBars(frame(64, 36), 64, 36))
        assertNull(detectBars(IntArray(64 * 36) { black }, 64, 36))
    }
}
