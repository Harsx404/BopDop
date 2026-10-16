package com.dopdop.bopdop.playback

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.dopdop.bopdop.data.ytm.ResolvedStream
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Finds where a song's recording sits inside its music video's soundtrack, so the video's
 * picture can play under the song's own (JioSaavn / YouTube Music) audio in sync.
 * Both are decoded to a 10 ms loudness-onset envelope and cross-correlated.
 */
internal object AudioAlign {
    private const val TAG = "AudioAlign"
    private const val HOP_MS = 10
    private const val TEMPLATE_FROM_S = 10
    private const val TEMPLATE_TO_S = 25
    /** How far into the video the song may start (story intros run long). */
    private const val MAX_INTRO_S = 150
    private const val BUDGET_MS = 14_000L

    /** ms into the video where the song's 0:00 plays; null when the two don't line up confidently. */
    fun songStartInVideo(song: ResolvedStream, videoSound: ResolvedStream): Long? = runCatching {
        val started = System.currentTimeMillis()
        val deadline = started + BUDGET_MS
        val from = TEMPLATE_FROM_S * 1000 / HOP_MS
        val to = TEMPLATE_TO_S * 1000 / HOP_MS
        // Both downloads run at once; the song's is short.
        var songEnv: FloatArray? = null
        val songThread = thread(name = "align-song") {
            songEnv = runCatching { onsets(Envelope(TEMPLATE_TO_S).apply { decode(song.url, song.userAgent, deadline) }.values()) }.getOrNull()
        }
        // googlevideo trickles to a streaming reader, so pull the soundtrack's head in one ranged
        // request and decode it locally: first a minute (most videos open within it), then the rest.
        var lag: Int? = null
        for (seconds in listOf(60, TEMPLATE_TO_S + MAX_INTRO_S)) {
            if (System.currentTimeMillis() > deadline) break
            val file = head(videoSound, seconds) ?: break
            val video = try { Envelope(seconds).apply { decode(file.path, null, deadline) }.values() } finally { file.delete() }
            songThread.join(maxOf(1, deadline - System.currentTimeMillis()))
            val tpl = songEnv ?: break
            lag = bestLag(tpl, onsets(video), from, to)
            if (lag != null) break
        }
        Log.i(TAG, "song starts at ${lag?.let { "${it * HOP_MS} ms" } ?: "— no confident match"} in the video (${System.currentTimeMillis() - started} ms)")
        lag?.let { it.toLong() * HOP_MS }
    }.onFailure { Log.w(TAG, "alignment failed: ${it.message}") }.getOrNull()

    /** Lag (in hops) of the song inside the video, or null if the match is weak or ambiguous. */
    internal fun bestLag(song: FloatArray, video: FloatArray, from: Int, to: Int): Int? {
        val end = minOf(to, song.size)
        if (end - from < 500) return null
        val tpl = song.copyOfRange(from, end)
        val mean = tpl.average().toFloat()
        for (i in tpl.indices) tpl[i] -= mean
        val tplNorm = sqrt(tpl.fold(0.0) { a, v -> a + v * v })
        if (tplNorm == 0.0) return null
        val lags = video.size - tpl.size + 1
        if (lags <= 0) return null
        val score = FloatArray(lags)
        for (lag in 0 until lags) {
            var dot = 0.0; var sum = 0.0; var sq = 0.0
            for (i in tpl.indices) { val v = video[lag + i]; dot += tpl[i] * v; sum += v; sq += v * v }
            val varV = sq - sum * sum / tpl.size
            score[lag] = if (varV <= 0) 0f else (dot / (tplNorm * sqrt(varV))).toFloat()
        }
        val best = score.indices.maxBy { score[it] }
        // The same recording stands well clear of every other alignment; a repeated chorus
        // or an unrelated mix doesn't.
        val rival = score.indices.filter { abs(it - best) > 50 }.maxOfOrNull { score[it] } ?: 0f
        if (score[best] < 0.3f || rival > score[best] * 0.55f) return null
        return best - from
    }

    /** The first [seconds] of a stream (by its bitrate, with headroom for headers) in a temp file. */
    private fun head(stream: ResolvedStream, seconds: Int): java.io.File? {
        val kbps = stream.bitrate.takeIf { it > 0 } ?: 160
        var bytes = kbps * 125L * seconds * 6 / 5 + 96_000
        if (stream.contentLength > 0) bytes = minOf(bytes, stream.contentLength)
        val req = okhttp3.Request.Builder().url(stream.url).header("Range", "bytes=0-${bytes - 1}")
            .apply { stream.userAgent?.let { header("User-Agent", it) } }.build()
        return com.dopdop.bopdop.data.net.Http.client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return null
            java.io.File.createTempFile("align", ".bin").also { f -> res.body?.byteStream()?.use { input -> f.outputStream().use { input.copyTo(it) } } }
        }
    }

    /** Rises in loudness — robust to the two masters being levelled or EQ'd differently. */
    private fun onsets(env: FloatArray): FloatArray {
        val out = FloatArray(env.size)
        for (i in 1 until env.size) out[i] = max(0f, env[i] - env[i - 1])
        return out
    }

    /** Log-RMS per 10 ms hop of the first [seconds] of a stream, mono. */
    private class Envelope(seconds: Int) {
        private val hops = seconds * 1000 / HOP_MS
        private val energy = DoubleArray(hops)
        private val count = IntArray(hops)

        fun values() = FloatArray(hops) {
            if (count[it] == 0) 0f else ln(1.0 + sqrt(energy[it] / count[it])).toFloat()
        }

        /** Decodes [source] (a url or a local path) until the window is full, the data ends, or [deadline] passes. */
        fun decode(source: String, userAgent: String?, deadline: Long) {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            try {
                if (source.startsWith("http")) extractor.setDataSource(source, buildMap { userAgent?.let { put("User-Agent", it) } })
                else extractor.setDataSource(source)
                val track = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                extractor.selectTrack(track)
                val format = extractor.getTrackFormat(track)
                codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!).apply { configure(format, null, null, 0); start() }
                var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                val info = MediaCodec.BufferInfo()
                val limitUs = hops * HOP_MS * 1000L
                var inputDone = false
                while (System.currentTimeMillis() < deadline) {
                    if (!inputDone) {
                        val i = codec.dequeueInputBuffer(5_000)
                        if (i >= 0) {
                            val n = extractor.readSampleData(codec.getInputBuffer(i)!!, 0)
                            if (n < 0 || extractor.sampleTime > limitUs) {
                                codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true
                            } else { codec.queueInputBuffer(i, 0, n, extractor.sampleTime, 0); extractor.advance() }
                        }
                    }
                    val o = codec.dequeueOutputBuffer(info, 5_000)
                    if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        rate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    } else if (o >= 0) {
                        val pcm = codec.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val frames = pcm.remaining() / channels
                        for (f in 0 until frames) {
                            var s = 0.0
                            for (c in 0 until channels) s += pcm.get(f * channels + c)
                            s /= channels
                            val hop = ((info.presentationTimeUs + f * 1_000_000L / rate) / (HOP_MS * 1000)).toInt()
                            if (hop in 0 until hops) { energy[hop] += s * s; count[hop]++ }
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            } finally {
                runCatching { codec?.stop() }; runCatching { codec?.release() }; extractor.release()
            }
        }
    }
}
