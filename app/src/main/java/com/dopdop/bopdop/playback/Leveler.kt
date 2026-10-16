package com.dopdop.bopdop.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Loudness normalization: tracks each song's running loudness and steers a slow gain toward a
 * common level, so a quiet 90s ballad and a loud modern master play at the same volume.
 * Boosts are capped and a soft knee keeps peaks from clipping. 16-bit PCM only.
 */
@OptIn(UnstableApi::class)
class Leveler : BaseAudioProcessor() {
    private val enabled get() = com.dopdop.bopdop.data.LibraryStore.settings.normalize

    /** Live readouts for the audio pipeline sheet. */
    @Volatile var gainDb = 0f; private set
    @Volatile var measuredDb = Float.NEGATIVE_INFINITY; private set

    private var sampleRate = 44_100
    private var channels = 2
    private var meanSquare = 0.0
    private var heardMs = 0L
    private var gain = 1.0

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioFormat.NOT_SET
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        val input = inputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
        val samples = input.remaining()
        val out = replaceOutputBuffer(bytes)

        // Loudness of this block; silence doesn't count toward the song's level.
        var sum = 0.0
        for (i in 0 until samples) { val v = input.get(i) / 32768.0; sum += v * v }
        val blockMs = samples * 1000L / (channels * sampleRate).coerceAtLeast(1)
        val blockPower = sum / samples.coerceAtLeast(1)
        if (blockPower > SILENCE) {
            // Settle fast at the start of a song, then follow slowly so dynamics survive.
            val tau = if (heardMs < 2_000) 400.0 else 4_000.0
            val a = 1 - exp(-blockMs / tau)
            meanSquare = if (heardMs == 0L) blockPower else meanSquare + (blockPower - meanSquare) * a
            heardMs += blockMs
        }
        val rms = sqrt(meanSquare)
        measuredDb = if (rms > 0) (20 * log10(rms)).toFloat() else Float.NEGATIVE_INFINITY
        val want = if (!enabled || heardMs == 0L) 1.0 else (TARGET / rms).coerceIn(MIN_GAIN, MAX_GAIN)

        // ~250 ms glide per sample frame so gain changes never click.
        val k = 1 - exp(-1.0 / (0.25 * sampleRate))
        for (i in 0 until samples) {
            if (i % channels == 0) gain += (want - gain) * k
            var v = input.get(i) / 32768.0 * gain
            val m = abs(v)
            if (m > KNEE) v = sign(v) * (KNEE + (1 - KNEE) * tanh((m - KNEE) / (1 - KNEE)))
            out.putShort((v * 32767).toInt().coerceIn(-32768, 32767).toShort())
        }
        gainDb = (20 * log10(gain)).toFloat()
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }

    /** A seek or a new song: re-measure, keeping the current gain so nothing jumps. */
    override fun onFlush() { heardMs = 0; meanSquare = 0.0 }

    override fun onReset() { heardMs = 0; meanSquare = 0.0; gain = 1.0 }

    private companion object {
        /** About −16 dBFS RMS, roughly −14 LUFS for typical music. */
        val TARGET = 10.0.pow(-16 / 20.0)
        val MIN_GAIN = 10.0.pow(-12 / 20.0)
        val MAX_GAIN = 10.0.pow(5 / 20.0)
        val SILENCE = 10.0.pow(-50 / 10.0)
        const val KNEE = 0.89
    }
}
