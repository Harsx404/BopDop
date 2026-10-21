package com.dopdop.bopdop.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import com.dopdop.bopdop.data.LibraryStore
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Bit-perfect output to USB DACs (Android 14+). With it on and a USB DAC attached, BopDop asks
 * Android for the DAC's bit-perfect mixer at the song's own sample rate and bit depth, so samples
 * reach the DAC untouched: no resampling, no system volume, no mixing with other sounds. Effects
 * that change samples (equalizer, normalization, crossfade) are bypassed while it's active.
 */
object BitPerfect {
    private const val TAG = "BitPerfect"
    private val _active = MutableStateFlow<String?>(null)
    /** "USB DAC name · 96 kHz / 24-bit" while bit-perfect is in force, else null. */
    val active: StateFlow<String?> = _active.asStateFlow()
    private var am: AudioManager? = null
    private var applied: AudioDeviceInfo? = null

    val supported get() = Build.VERSION.SDK_INT >= 34

    fun init(context: Context) {
        if (!supported || am != null) return
        val m = context.getSystemService(AudioManager::class.java) ?: return
        am = m
        m.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) = refresh()
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) = refresh()
        }, Handler(Looper.getMainLooper()))
        // A new song can change the sample rate or depth the DAC should run at.
        AudioPipeline.info.map { it.sampleRate to it.bitDepth }.distinctUntilChanged().onEach { refresh() }.launchIn(MainScope())
        LibraryStore.state.map { it.settings.bitPerfect }.distinctUntilChanged().onEach { refresh() }.launchIn(MainScope())
    }

    fun refresh() {
        if (!supported) return
        runCatching { apply34() }.onFailure { Log.w(TAG, "bit-perfect: ${it.message}") }
    }

    private val media by lazy { AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build() }

    @RequiresApi(34)
    private fun apply34() {
        val m = am ?: return
        val dac = m.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        if (!LibraryStore.settings.bitPerfect || dac == null) { clear(m); return }
        val info = AudioPipeline.info.value
        val options = m.getSupportedMixerAttributes(dac).filter { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
        if (options.isEmpty()) { clear(m); _active.value = null; return }
        val wantRate = info.sampleRate.takeIf { it > 0 } ?: 48_000
        val wantEnc = when (info.bitDepth) { 24 -> AudioFormat.ENCODING_PCM_24BIT_PACKED; 32 -> AudioFormat.ENCODING_PCM_32BIT; else -> AudioFormat.ENCODING_PCM_16BIT }
        // Exact rate and depth if the DAC has it; else the same rate at any depth; else the DAC's first.
        val pick = options.firstOrNull { it.format.sampleRate == wantRate && it.format.encoding == wantEnc }
            ?: options.firstOrNull { it.format.sampleRate == wantRate }
            ?: options.first()
        m.setPreferredMixerAttributes(media, dac, pick)
        applied = dac
        val depth = when (pick.format.encoding) { AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24; AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 32; else -> 16 }
        _active.value = "${dac.productName} · ${pick.format.sampleRate / 1000.0} kHz / $depth-bit".replace(".0 kHz", " kHz")
        AudioFx.apply(AudioFx.state.value)
    }

    @RequiresApi(34)
    private fun clear(m: AudioManager) {
        applied?.let { runCatching { m.clearPreferredMixerAttributes(media, it) } }
        applied = null
        val was = _active.value != null
        _active.value = null
        if (was) AudioFx.apply(AudioFx.state.value)
    }

    /** True while samples must pass untouched: effects that change them stand aside. */
    val bypassEffects get() = _active.value != null
}
