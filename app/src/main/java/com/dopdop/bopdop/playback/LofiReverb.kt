package com.dopdop.bopdop.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.dopdop.bopdop.data.LibraryStore
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The lo-fi mix's reverb: Freeverb (Jezar at Dreampoint's public-domain design), eight damped comb
 * filters and four all-passes per channel, with the right channel's delays a little longer for
 * width. The amount sets both the room size and how much of it is heard; the tail is darkened a
 * touch more than Freeverb's default, which suits lo-fi. 16-bit PCM; passes untouched while the
 * lo-fi mix is off or its reverb is at zero, and with bit-perfect output.
 */
@OptIn(UnstableApi::class)
class LofiReverb : BaseAudioProcessor() {
    private var channels = 2
    private var combs: Array<Array<Comb>> = emptyArray()
    private var allpasses: Array<Array<Allpass>> = emptyArray()

    private val amount: Float
        get() = LibraryStore.settings.let { if (it.lofi && !BitPerfect.bypassEffects) it.lofiReverb else 0f }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) return AudioFormat.NOT_SET
        channels = inputAudioFormat.channelCount
        val scale = inputAudioFormat.sampleRate / 44_100.0
        combs = Array(2) { c -> Array(COMBS.size) { i -> Comb(((COMBS[i] + c * SPREAD) * scale).toInt()) } }
        allpasses = Array(2) { c -> Array(ALLPASSES.size) { i -> Allpass(((ALLPASSES[i] + c * SPREAD) * scale).toInt()) } }
        return inputAudioFormat
    }

    override fun onFlush() {
        combs.forEach { ch -> ch.forEach { it.clear() } }
        allpasses.forEach { ch -> ch.forEach { it.clear() } }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        val a = amount
        if (a <= 0.001f) {
            replaceOutputBuffer(bytes).put(inputBuffer).flip()
            return
        }
        val input = inputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
        val out = replaceOutputBuffer(bytes)
        val frames = input.remaining() / channels
        val feedback = ROOM_OFFSET + ROOM_SCALE * a
        val wet = a
        val dry = 1f - 0.3f * a
        combs.forEach { ch -> ch.forEach { it.feedback = feedback } }
        for (f in 0 until frames) {
            val l = input.get(f * channels) / 32768f
            val r = if (channels == 2) input.get(f * channels + 1) / 32768f else l
            val mono = (l + r) * FIXED_GAIN
            var wl = 0f; var wr = 0f
            for (c in combs[0]) wl += c.process(mono)
            for (c in combs[1]) wr += c.process(mono)
            for (p in allpasses[0]) wl = p.process(wl)
            for (p in allpasses[1]) wr = p.process(wr)
            out.putShort(pcm(l * dry + wl * wet))
            if (channels == 2) out.putShort(pcm(r * dry + wr * wet))
        }
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }

    private fun pcm(v: Float): Short = (v * 32767f).toInt().coerceIn(-32768, 32767).toShort()

    /** Delay line feeding back through a one-pole low-pass, which darkens the tail as it decays. */
    private class Comb(size: Int) {
        private val buf = FloatArray(size.coerceAtLeast(1))
        private var i = 0
        private var store = 0f
        var feedback = 0.84f

        fun clear() { buf.fill(0f); store = 0f }

        fun process(x: Float): Float {
            val y = buf[i]
            store = y * (1 - DAMP) + store * DAMP
            buf[i] = x + store * feedback
            if (++i == buf.size) i = 0
            return y
        }
    }

    /** Smears the comb echoes into a dense tail. */
    private class Allpass(size: Int) {
        private val buf = FloatArray(size.coerceAtLeast(1))
        private var i = 0

        fun clear() = buf.fill(0f)

        fun process(x: Float): Float {
            val b = buf[i]
            buf[i] = x + b * 0.5f
            if (++i == buf.size) i = 0
            return b - x
        }
    }

    private companion object {
        // Freeverb's delay lengths at 44.1 kHz, and the right channel's extra.
        val COMBS = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        val ALLPASSES = intArrayOf(556, 441, 341, 225)
        const val SPREAD = 23
        const val FIXED_GAIN = 0.015f
        const val ROOM_OFFSET = 0.7f
        const val ROOM_SCALE = 0.28f
        /** Freeverb uses 0.2; a little more for a warmer, duller tail. */
        const val DAMP = 0.3f
    }
}
