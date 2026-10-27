package com.dopdop.bopdop.playback

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.VocalMode
import com.dopdop.bopdop.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The vocal isolation model: UVR-MDX-NET-Inst_Main from Ultimate Vocal Remover, as the ONNX file
 * sherpa-onnx publishes. It predicts the instrumental; the vocals are the mix minus that. Not
 * bundled: it's fetched the first time vocal isolation is switched on.
 */
object VocalModel {
    sealed interface State {
        data object Missing : State
        data class Downloading(val progress: Float) : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    /** For the download prompt. */
    const val SIZE_MB = 53
    private const val URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/source-separation-models/UVR-MDX-NET-Inst_Main.onnx"
    private const val FILE = "UVR-MDX-NET-Inst_Main.onnx"
    private const val BYTES = 52_787_034L

    private val _state = MutableStateFlow<State>(State.Missing)
    val state: StateFlow<State> = _state.asStateFlow()
    val ready: Boolean get() = _state.value is State.Ready

    /** Vocal isolation is splitting songs right now (a mode is on and the model is here). */
    val active: Boolean get() = ready && LibraryStore.settings.vocalMode != VocalMode.OFF && !BitPerfect.bypassEffects

    private var dir: File? = null

    fun init(context: Context) {
        val d = File(context.filesDir, "models/uvr").also { dir = it }
        // The first experiment used Spleeter; its files have no use now.
        File(context.filesDir, "models/spleeter-2stems").takeIf { it.exists() }?.deleteRecursively()
        _state.value = if (File(d, FILE).length() == BYTES) State.Ready else State.Missing
    }

    suspend fun download() = withContext(Dispatchers.IO) {
        val d = dir ?: return@withContext
        if (_state.value is State.Ready || _state.value is State.Downloading) return@withContext
        _state.value = State.Downloading(0f)
        try {
            d.mkdirs()
            val tmp = File(d, "$FILE.part")
            // Phone connections drop: carry on from where the file got to, a few times over.
            var attempt = 0
            while (tmp.length() < BYTES) {
                try {
                    val have = tmp.length()
                    val req = Request.Builder().url(URL).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
                    Http.client.newCall(req).execute().use { res ->
                        if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
                        val resumed = res.code == 206
                        val body = res.body ?: throw IOException("Empty response")
                        body.byteStream().use { input ->
                            java.io.FileOutputStream(tmp, resumed).use { out ->
                                val buf = ByteArray(64 * 1024)
                                var done = if (resumed) have else 0L
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                    done += n
                                    _state.value = State.Downloading((done.toFloat() / BYTES).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                } catch (e: IOException) {
                    if (++attempt >= 4) throw e
                    Log.w("VocalModel", "download interrupted (${e.message}), resuming")
                    kotlinx.coroutines.delay(1_500L * attempt)
                }
            }
            if (tmp.length() != BYTES || !tmp.renameTo(File(d, FILE))) throw IOException("Incomplete download")
            _state.value = State.Ready
        } catch (e: Exception) {
            Log.w("VocalModel", "download failed: ${e.message}")
            _state.value = State.Failed(e.message ?: "Download failed")
        }
    }

    /** Frees the space; vocal isolation turns off with it. */
    @Synchronized fun delete() {
        session?.let { runCatching { it.close() } }
        session = null
        dir?.deleteRecursively()
        _state.value = State.Missing
    }

    private var session: OrtSession? = null

    /** The model, loaded once (a second or two) and kept while in use. */
    @Synchronized fun session(): OrtSession = session ?: run {
        val d = dir ?: throw IllegalStateException("not initialised")
        // It's a heavy model: give it most of the phone's big cores, leave one for the rest.
        val threads = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(2, 4)
        // (XNNPACK was tried and ran slower than ONNX Runtime's own CPU kernels for this model.)
        val opts = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(threads)
        }
        Log.i("VocalModel", "model loading with $threads threads")
        OrtEnvironment.getEnvironment().createSession(File(d, FILE).path, opts).also { session = it }
    }
}

/**
 * Plays only the vocals or only the instruments. The song is cut into segments of about six
 * seconds; a background thread converts them in order, running ahead of playback, so while one
 * segment plays the next ones are being made. Sound starts once the first segment is ready, and
 * if the phone is slower than the song, playback waits for the next segment rather than playing
 * anything less.
 *
 * Each segment overlaps its neighbours by [TRIM] samples on each side, which are thrown away, so
 * segments meet without seams. 16-bit PCM only; with bit-perfect output, or before the model is
 * downloaded, audio passes untouched. The mode is read when playback is flushed, so changing it
 * seeks to apply it.
 */
@OptIn(UnstableApi::class)
class VocalSplitter : BaseAudioProcessor() {
    private var channels = 2
    private var mode = VocalMode.OFF

    // Input as float per channel, addressed by real sample index from the start of the stream.
    private val store = Array(2) { Samples() }
    private var received = 0L
    private var ended = false
    private var realTotal = -1L

    /** Bumped on every flush; segments made for an older generation are dropped. Read by the worker. */
    @Volatile private var generation = 0
    private val made = ConcurrentHashMap<Int, ShortArray>()
    private val busy = AtomicBoolean(false)
    private var nextToMake = 0
    private var nextToPlay = 0
    private var emitted = 0L

    // A segment is handed on a slice at a time, as a decoder would.
    private var slice: ShortArray? = null
    private var sliceAt = 0

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) return AudioFormat.NOT_SET
        channels = inputAudioFormat.channelCount
        return inputAudioFormat
    }

    override fun onFlush() = restart()
    override fun onReset() = restart()

    private fun restart() {
        generation++
        made.clear()
        mode = if (VocalModel.active) LibraryStore.settings.vocalMode else VocalMode.OFF
        store.forEach { it.clear() }
        received = 0; ended = false; realTotal = -1
        nextToMake = 0; nextToPlay = 0; emitted = 0
        slice = null; sliceAt = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        if (mode == VocalMode.OFF) {
            replaceOutputBuffer(bytes).put(inputBuffer).flip()
            return
        }
        // Far enough ahead of what's playing: leave the input for later, which holds the decoder back.
        if (received - emitted > MAX_AHEAD) return
        val shorts = inputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
        val frames = shorts.remaining() / channels
        val l = FloatArray(frames); val r = FloatArray(frames)
        for (i in 0 until frames) {
            l[i] = shorts.get(i * channels) / 32768f
            r[i] = if (channels == 2) shorts.get(i * channels + 1) / 32768f else l[i]
        }
        store[0].append(l, frames); store[1].append(r, frames)
        received += frames
        inputBuffer.position(inputBuffer.limit())
        make()
    }

    // The pipeline repeats end of stream until this processor reports it has ended (it drains the
    // chain at the end of a song, and when the source changes mid-song): act on the first only.
    override fun onQueueEndOfStream() {
        if (mode == VocalMode.OFF || ended) return
        ended = true
        realTotal = received
        make()
    }

    override fun getOutput(): ByteBuffer {
        if (mode != VocalMode.OFF && !hasPendingOutput()) {
            if (slice == null) made.remove(nextToPlay)?.let {
                slice = it; sliceAt = 0; nextToPlay++
                // Keep only what segments still to be made need.
                val keepFrom = nextToMake.toLong() * GEN - TRIM
                store.forEach { s -> s.dropBefore(keepFrom) }
            }
            slice?.let { pcm ->
                val n = minOf(SLICE * channels, pcm.size - sliceAt)
                val out = replaceOutputBuffer(n * 2)
                out.asShortBuffer().put(pcm, sliceAt, n)
                out.position(n * 2); out.flip()
                sliceAt += n
                emitted += n / channels
                if (sliceAt >= pcm.size) slice = null
            }
            make()
        }
        return super.getOutput()
    }

    override fun isEnded(): Boolean =
        super.isEnded() && (mode == VocalMode.OFF || (ended && slice == null && nextToPlay >= segments()))

    /** Segments in the stream, once its end is known. */
    private fun segments(): Int = if (realTotal < 0) Int.MAX_VALUE else ((realTotal + GEN - 1) / GEN).toInt()

    /** Starts the next segment once its samples (and the context after it) are in. One at a time. */
    private fun make() {
        val s = nextToMake
        if (s >= segments() || busy.get()) return
        val from = s.toLong() * GEN - TRIM
        val to = from + CHUNK
        if (!ended && received < to) return
        val job = Segment(generation, mode, s, from, store[0].copy(from, to), store[1].copy(from, to), realTotal, channels)
        nextToMake++
        busy.set(true)
        worker.execute {
            val t0 = SystemClock.elapsedRealtime()
            runCatching { job.run() }
                .onSuccess { pcm ->
                    val ms = SystemClock.elapsedRealtime() - t0
                    Log.i("VocalSplitter", "segment ${job.index}: ${"%.2f".format(GEN / 44.1 / ms.coerceAtLeast(1))}x real time ($ms ms)")
                    if (job.generation == generation) made[job.index] = pcm
                }
                .onFailure { e ->
                    // Never silence: the segment plays as it came in.
                    Log.w("VocalSplitter", "segment ${job.index} failed, playing it as is: ${e.message}")
                    if (job.generation == generation) made[job.index] = job.passThrough()
                }
            busy.set(false)
        }
    }

    /** One segment: the model sees samples from..from+CHUNK and gives back the middle GEN of them. */
    private class Segment(
        val generation: Int, val mode: VocalMode, val index: Int, val from: Long,
        val l: FloatArray, val r: FloatArray, val realTotal: Long, val channels: Int,
    ) {
        /** Real samples this segment outputs: the middle of its input, cut at the end of the song. */
        private val outLen = (if (realTotal >= 0) minOf(GEN.toLong(), realTotal - (from + TRIM)) else GEN.toLong()).toInt().coerceAtLeast(0)

        fun run(): ShortArray {
            val fft = Fft.get()
            val re = FloatArray(N); val im = FloatArray(N)
            // Spectrum as the model wants it: (1, 4, DF, DT); planes are left real, left imaginary,
            // right real, right imaginary, each frequency-major.
            val input = FloatArray(4 * DF * DT)
            for (c in 0..1) {
                val x = if (c == 0) l else r
                for (t in 0 until DT) {
                    // Centred frames over the segment, reflected at its edges.
                    val start = t * H - N / 2
                    for (i in 0 until N) { re[i] = x[reflect(start + i)] * WINDOW[i]; im[i] = 0f }
                    fft.transform(re, im)
                    val pr = (2 * c) * DF * DT; val pi = (2 * c + 1) * DF * DT
                    for (k in 0 until DF) { input[pr + k * DT + t] = re[k]; input[pi + k * DT + t] = im[k] }
                }
            }
            val env = OrtEnvironment.getEnvironment()
            val y: FloatBuffer = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 4, DF.toLong(), DT.toLong())).use { t ->
                VocalModel.session().run(mapOf("input" to t)).use { (it[0] as OnnxTensor).floatBuffer }
            }
            // Back to audio: the predicted instrumental, overlap-added and evened out by the window.
            val inst = Array(2) { FloatArray(outLen) }
            for (c in 0..1) {
                val acc = FloatArray(CHUNK + N)
                val pr = (2 * c) * DF * DT; val pi = (2 * c + 1) * DF * DT
                for (t in 0 until DT) {
                    for (k in 0 until DF) { re[k] = y.get(pr + k * DT + t); im[k] = y.get(pi + k * DT + t) }
                    re[DF] = 0f; im[DF] = 0f
                    for (k in 1 until N / 2) { re[N - k] = re[k]; im[N - k] = -im[k] }
                    fft.inverse(re, im)
                    val at = t * H
                    for (i in 0 until N) acc[at + i] += re[i] * WINDOW[i]
                }
                // acc index j is segment sample j - N/2; the output starts TRIM into the segment.
                for (j in 0 until outLen) {
                    val a = N / 2 + TRIM + j
                    inst[c][j] = acc[a] / NORM[a]
                }
            }
            return pcm { c, j ->
                if (mode == VocalMode.INSTRUMENTAL) inst[c][j]
                else (if (c == 0) l else r)[TRIM + j] - inst[c][j]
            }
        }

        /** The segment's audio as it came in, when the model can't run. */
        fun passThrough(): ShortArray = pcm { c, j -> (if (c == 0) l else r)[TRIM + j] }

        private inline fun pcm(sample: (Int, Int) -> Float): ShortArray {
            val out = ShortArray(outLen * channels)
            for (j in 0 until outLen) for (c in 0 until channels) {
                out[j * channels + c] = (sample(c, j) * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            }
            return out
        }

        /** Index into the segment with reflection at both ends, as a centred STFT pads. */
        private fun reflect(i: Int): Int = when {
            i < 0 -> -i
            i >= CHUNK -> 2 * (CHUNK - 1) - i
            else -> i
        }
    }

    /** A growing float buffer addressed by absolute sample index, trimmed from the front. Reads outside it are silence. */
    private class Samples {
        private var data = FloatArray(1 shl 19)
        private var base = 0L
        private var size = 0

        fun clear() { base = 0; size = 0 }

        fun append(src: FloatArray, n: Int) {
            if (size + n > data.size) data = data.copyOf(maxOf(data.size * 2, size + n))
            System.arraycopy(src, 0, data, size, n)
            size += n
        }

        fun copy(from: Long, to: Long): FloatArray {
            val out = FloatArray((to - from).toInt())
            val s = maxOf(from, base); val e = minOf(to, base + size)
            if (e > s) System.arraycopy(data, (s - base).toInt(), out, (s - from).toInt(), (e - s).toInt())
            return out
        }

        fun dropBefore(index: Long) {
            val drop = (index - base).toInt().coerceIn(0, size)
            if (drop == 0) return
            System.arraycopy(data, drop, data, 0, size - drop)
            size -= drop; base += drop
        }
    }

    private companion object {
        // The model's STFT and input size (from its metadata).
        const val N = 4096
        const val H = 1024
        const val DF = 2048
        const val DT = 256
        /** Samples per model call, and what it outputs once TRIM is cut from each side. */
        const val CHUNK = H * (DT - 1)
        const val TRIM = N / 2
        const val GEN = CHUNK - 2 * TRIM
        /** About 30 seconds at 44.1 kHz: how far the decoder may run ahead. */
        const val MAX_AHEAD = 30L * 44_100
        /** Frames handed on per call: about 90 ms. */
        const val SLICE = 4096
        val WINDOW = FloatArray(N) { (0.5 - 0.5 * cos(2 * PI * it / N)).toFloat() }
        /** Sum of the squared window under each sample of a segment's overlap-add. */
        val NORM = FloatArray(CHUNK + N).also { n ->
            for (t in 0 until DT) for (i in 0 until N) n[t * H + i] += WINDOW[i] * WINDOW[i]
            for (i in n.indices) n[i] = n[i].coerceAtLeast(1e-8f)
        }
        val worker = Executors.newSingleThreadExecutor { Thread(it, "vocal-split").apply { priority = Thread.NORM_PRIORITY - 1 } }
    }
}

/** In-place radix-2 FFT for the splitter's 4096-point frames. One per thread; tables shared. */
private class Fft(private val n: Int) {
    private val cosT = FloatArray(n / 2) { cos(2 * PI * it / n).toFloat() }
    private val sinT = FloatArray(n / 2) { sin(2 * PI * it / n).toFloat() }
    private val rev = IntArray(n).also { r ->
        val bits = Integer.numberOfTrailingZeros(n)
        for (i in 0 until n) r[i] = Integer.reverse(i) ushr (32 - bits)
    }

    fun transform(re: FloatArray, im: FloatArray) {
        for (i in 0 until n) {
            val j = rev[i]
            if (j > i) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t }
        }
        var size = 2
        while (size <= n) {
            val half = size / 2; val step = n / size
            var i = 0
            while (i < n) {
                var k = 0
                for (j in i until i + half) {
                    val wr = cosT[k]; val wi = -sinT[k]
                    val xr = re[j + half] * wr - im[j + half] * wi
                    val xi = re[j + half] * wi + im[j + half] * wr
                    re[j + half] = re[j] - xr; im[j + half] = im[j] - xi
                    re[j] += xr; im[j] += xi
                    k += step
                }
                i += size
            }
            size *= 2
        }
    }

    /** Inverse transform; the result's real part is the signal. */
    fun inverse(re: FloatArray, im: FloatArray) {
        for (i in 0 until n) im[i] = -im[i]
        transform(re, im)
        val s = 1f / n
        for (i in 0 until n) { re[i] *= s; im[i] = -im[i] * s }
    }

    companion object {
        private val local = ThreadLocal.withInitial { Fft(4096) }
        fun get(): Fft = local.get()!!
    }
}
