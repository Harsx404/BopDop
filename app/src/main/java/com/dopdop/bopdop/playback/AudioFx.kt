package com.dopdop.bopdop.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ln
import kotlin.math.roundToInt

/** What the equalizer is set to. Gains are in dB at [AudioFx.FREQS], independent of the device's own bands. */
data class EqState(
    val enabled: Boolean = false,
    val preset: String = "Flat",
    val gains: List<Float> = List(AudioFx.FREQS.size) { 0f },
    /** 0..1 */
    val bass: Float = 0f,
    /** 0..1, headphone widening. */
    val spatial: Float = 0f,
) {
    fun toJson(): JSONObject = JSONObject().put("enabled", enabled).put("preset", preset)
        .put("gains", JSONArray(gains.map { it.toDouble() })).put("bass", bass.toDouble()).put("spatial", spatial.toDouble())

    companion object {
        fun fromJson(o: JSONObject?): EqState {
            if (o == null) return EqState()
            val g = o.optJSONArray("gains")
            val gains = if (g != null && g.length() == AudioFx.FREQS.size) List(g.length()) { g.optDouble(it, 0.0).toFloat() } else EqState().gains
            return EqState(o.optBoolean("enabled", false), o.optString("preset", "Flat"), gains, o.optDouble("bass", 0.0).toFloat(), o.optDouble("spatial", 0.0).toFloat())
        }
    }
}

/**
 * Ten-band EQ + bass + spatial on the player's audio session. The UI works on a fixed ten-band curve;
 * whatever bands the device's Equalizer has are fed from that curve, sampled at their centre frequencies.
 */
object AudioFx {
    val FREQS = intArrayOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    const val MAX_DB = 12f

    val PRESETS: List<Pair<String, List<Float>>> = listOf(
        "Flat" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Bass Boost" to listOf(7f, 6f, 4.5f, 2.5f, 0.5f, 0f, 0f, 0f, 0.5f, 1f),
        "Vocal" to listOf(-2f, -1.5f, -1f, 0.5f, 2.5f, 4f, 4f, 2.5f, 1f, 0f),
        "Treble" to listOf(0f, 0f, 0f, 0f, 0f, 0.5f, 2f, 4f, 5.5f, 6.5f),
        "Electronic" to listOf(5f, 4.5f, 2f, 0f, -1.5f, 1f, 0.5f, 2f, 4f, 5f),
        "Acoustic" to listOf(3f, 2.5f, 2f, 1f, 1.5f, 1.5f, 2.5f, 3f, 2.5f, 1.5f),
        "Hip-Hop" to listOf(6f, 5f, 2f, 3f, -1f, -1f, 1f, -0.5f, 2f, 3f),
        "Rock" to listOf(4.5f, 3.5f, 2.5f, 0.5f, -1f, -0.5f, 1.5f, 3f, 3.5f, 4f),
        "Late Night" to listOf(2.5f, 2f, 1f, 0f, -1f, -1.5f, -1f, -2f, -3f, -4f),
    )

    /** The live setting — the screen writes here while dragging, the service listens. */
    val state = MutableStateFlow(EqState())

    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null

    fun attach(sessionId: Int) {
        release()
        eq = runCatching { Equalizer(0, sessionId) }.onFailure { Log.w("AudioFx", "no equalizer: ${it.message}") }.getOrNull()
        bass = runCatching { BassBoost(0, sessionId) }.getOrNull()
        virt = runCatching { Virtualizer(0, sessionId) }.getOrNull()
        apply(state.value)
    }

    fun apply(requested: EqState) {
        // Bit-perfect output to a USB DAC: the equalizer and its friends stand aside.
        val s = if (BitPerfect.bypassEffects) requested.copy(enabled = false) else requested
        eq?.let { e ->
            runCatching {
                val range = e.bandLevelRange
                for (b in 0 until e.numberOfBands) {
                    val hz = e.getCenterFreq(b.toShort()) / 1000f
                    val mb = (curveAt(s.gains, hz) * 100f).roundToInt().coerceIn(range[0].toInt(), range[1].toInt())
                    e.setBandLevel(b.toShort(), mb.toShort())
                }
                e.enabled = s.enabled
            }
        }
        bass?.let { b -> runCatching { if (b.strengthSupported) b.setStrength((s.bass * 1000).roundToInt().toShort()); b.enabled = s.enabled && s.bass > 0.01f } }
        virt?.let { v -> runCatching { if (v.strengthSupported) v.setStrength((s.spatial * 1000).roundToInt().toShort()); v.enabled = s.enabled && s.spatial > 0.01f } }
    }

    /** dB of the ten-band curve at [hz], linear in log-frequency between band centres. */
    fun curveAt(gains: List<Float>, hz: Float): Float {
        val x = ln(hz.coerceIn(FREQS.first().toFloat(), FREQS.last().toFloat()))
        for (i in 0 until FREQS.size - 1) {
            val a = ln(FREQS[i].toFloat()); val b = ln(FREQS[i + 1].toFloat())
            if (x <= b) return gains[i] + (gains[i + 1] - gains[i]) * ((x - a) / (b - a))
        }
        return gains.last()
    }

    fun release() {
        runCatching { eq?.release() }; runCatching { bass?.release() }; runCatching { virt?.release() }
        eq = null; bass = null; virt = null
    }
}
