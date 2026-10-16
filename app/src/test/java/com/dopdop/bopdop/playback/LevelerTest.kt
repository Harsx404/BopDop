package com.dopdop.bopdop.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import com.dopdop.bopdop.data.LibraryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class LevelerTest {
    /** Ten seconds of a stereo sine at [amplitude] (0..1), fed in 20 ms blocks; returns the last block's RMS. */
    private fun run(amplitude: Double, normalize: Boolean): Double {
        LibraryStore.updateSettings { it.copy(normalize = normalize) }
        val p = Leveler()
        p.configure(AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))
        p.flush()
        var last = 0.0
        var n = 0
        repeat(500) {
            val frames = 882
            val buf = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
            repeat(frames) { val v = (amplitude * sin(2 * PI * 440 * n++ / 44_100) * 32767).toInt().toShort(); buf.putShort(v); buf.putShort(v) }
            buf.flip()
            p.queueInput(buf)
            val out = p.output.order(ByteOrder.nativeOrder()).asShortBuffer()
            var sum = 0.0
            for (i in 0 until out.remaining()) { val v = out.get(i) / 32768.0; sum += v * v }
            last = sqrt(sum / out.remaining())
        }
        return last
    }

    @Test fun bringsLoudAndQuietTracksTogether() {
        val loud = run(0.9, normalize = true)
        val quiet = run(0.12, normalize = true)
        // −16 dBFS target ≈ 0.158 RMS; quiet is capped at +5 dB of boost.
        assertTrue("loud was $loud", loud in 0.13..0.19)
        assertTrue("quiet was $quiet", quiet > 0.12 * 0.707 * 1.4)
    }

    @Test fun passesThroughWhenOff() {
        assertEquals(0.5 * 0.707, run(0.5, normalize = false), 0.01)
    }
}
