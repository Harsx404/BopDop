package com.dopdop.bopdop.playback

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The live signal path, decoder to speaker, for the Audio Pipeline sheet. */
data class PipelineInfo(
    val codec: String = "—",
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val bitrateKbps: Int = 0,
    /** Bits per sample of the source, when it has one (lossless and PCM); 0 for lossy codecs. */
    val bitDepth: Int = 0,
    val decoder: String = "—",
    val sinkEncoding: String = "—",
    val sinkRate: Int = 0,
    val bufferMs: Int = 0,
    val offload: Boolean = false,
)

data class OutputDevice(val name: String, val route: String)

@OptIn(UnstableApi::class)
object AudioPipeline {
    private val _info = MutableStateFlow(PipelineInfo())
    val info: StateFlow<PipelineInfo> = _info.asStateFlow()

    /** The normalization stage, so the sheet can show the gain it is applying. */
    @Volatile var leveler: Leveler? = null

    internal val listener = object : AnalyticsListener {
        override fun onAudioInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
            _info.update {
                it.copy(
                    codec = codecName(format), sampleRate = format.sampleRate, channels = format.channelCount,
                    bitrateKbps = maxOf(format.averageBitrate, format.bitrate).takeIf { b -> b > 0 }?.div(1000) ?: 0,
                    bitDepth = bytesPer(format.pcmEncoding) * 8,
                )
            }
        }

        override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
            _info.update { it.copy(decoder = decoderName) }
        }

        override fun onAudioTrackInitialized(eventTime: AnalyticsListener.EventTime, audioTrackConfig: AudioSink.AudioTrackConfig) {
            val frameBytes = audioTrackConfig.channelConfig.let { Integer.bitCount(it).coerceAtLeast(1) } * bytesPer(audioTrackConfig.encoding)
            _info.update {
                it.copy(
                    sinkEncoding = encodingName(audioTrackConfig.encoding),
                    sinkRate = audioTrackConfig.sampleRate, offload = audioTrackConfig.offload,
                    bufferMs = if (frameBytes > 0 && audioTrackConfig.sampleRate > 0)
                        (audioTrackConfig.bufferSize.toLong() * 1000 / (frameBytes * audioTrackConfig.sampleRate)).toInt() else 0,
                )
            }
        }
    }

    /** Where sound is going right now: a headset/USB/BT device wins over the phone speaker. */
    fun output(context: Context): OutputDevice {
        val am = context.getSystemService(AudioManager::class.java) ?: return OutputDevice("Phone", "Speaker")
        val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val rank = listOf(
            AudioDeviceInfo.TYPE_USB_HEADSET to "USB", AudioDeviceInfo.TYPE_USB_DEVICE to "USB",
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES to "Wired", AudioDeviceInfo.TYPE_WIRED_HEADSET to "Wired",
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP to "Bluetooth",
        )
        for ((type, route) in rank) {
            devices.firstOrNull { it.type == type }?.let { return OutputDevice(it.productName?.toString()?.ifBlank { null } ?: route, route) }
        }
        return OutputDevice(android.os.Build.MODEL, "Speaker")
    }

    /** [output], kept current as headphones, Bluetooth or USB come and go. */
    fun outputFlow(context: Context): Flow<OutputDevice> = callbackFlow {
        val am = context.getSystemService(AudioManager::class.java)
        trySend(output(context))
        val cb = object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) { trySend(output(context)) }
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) { trySend(output(context)) }
        }
        am?.registerAudioDeviceCallback(cb, android.os.Handler(android.os.Looper.getMainLooper()))
        awaitClose { am?.unregisterAudioDeviceCallback(cb) }
    }.distinctUntilChanged()

    /** The system's own "play on" picker (Android 14+); older phones get Bluetooth settings. */
    fun showOutputSwitcher(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= 34 &&
            runCatching { android.media.MediaRouter2.getInstance(context).showSystemOutputSwitcher() }.getOrDefault(false)) return
        runCatching {
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun codecName(f: Format) = when (f.sampleMimeType) {
        MimeTypes.AUDIO_OPUS -> "Opus"
        MimeTypes.AUDIO_AAC -> "AAC"
        MimeTypes.AUDIO_E_AC3_JOC, MimeTypes.AUDIO_AC4 -> "Dolby Atmos"
        MimeTypes.AUDIO_E_AC3 -> "Dolby Digital Plus"
        MimeTypes.AUDIO_AC3 -> "Dolby Digital"
        MimeTypes.AUDIO_FLAC -> "FLAC"
        MimeTypes.AUDIO_MPEG -> "MP3"
        MimeTypes.AUDIO_VORBIS -> "Vorbis"
        MimeTypes.AUDIO_ALAC -> "ALAC"
        MimeTypes.AUDIO_RAW, MimeTypes.AUDIO_WAV -> "PCM"
        else -> f.sampleMimeType?.substringAfter('/')?.uppercase() ?: "—"
    }

    private fun encodingName(e: Int) = when (e) {
        C.ENCODING_PCM_16BIT -> "16-bit PCM"
        C.ENCODING_PCM_24BIT -> "24-bit PCM"
        C.ENCODING_PCM_32BIT -> "32-bit PCM"
        C.ENCODING_PCM_FLOAT -> "32-bit float"
        else -> "Compressed (offload)"
    }

    private fun bytesPer(e: Int) = when (e) {
        C.ENCODING_PCM_16BIT -> 2; C.ENCODING_PCM_24BIT -> 3; C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4; else -> 0
    }
}
