@file:OptIn(ExperimentalHazeApi::class)

package com.dopdop.bopdop.ui.glass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dopdop.bopdop.data.GlassLevel
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.ChromaticAberrationMode
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.RefractionProfile
import dev.chrisbanes.haze.glass.SurfaceProfile
import kotlin.math.roundToInt

/**
 * Where the light comes from, in glass bias space (-1..1 on each axis). Driven
 * by the gravity sensor: tilt the phone and every glass surface's specular
 * highlight slides with it, like iOS Liquid Glass.
 */
@Immutable
data class GlassLight(val x: Float, val y: Float) {
    companion object { val Default = GlassLight(-0.55f, -0.85f) }
}

val LocalGlassLight = staticCompositionLocalOf<State<GlassLight>> { mutableStateOf(GlassLight.Default) }

/**
 * Low-passed gravity → light position. Quantised to 0.05 steps so glass only
 * re-styles when the phone actually tilts, not on sensor noise.
 */
@Composable
fun rememberTiltLight(enabled: Boolean): State<GlassLight> {
    val ctx = LocalContext.current
    val state = remember { mutableStateOf(GlassLight.Default) }
    DisposableEffect(enabled) {
        if (!enabled) { state.value = GlassLight.Default; return@DisposableEffect onDispose {} }
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var fx = 0f; var fy = 9.8f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                fx += (e.values[0] - fx) * 0.12f
                fy += (e.values[1] - fy) * 0.12f
                // Phone held upright: light sits above and to the left; tilting moves it.
                val x = (-0.55f - fx / 9.8f * 1.1f).coerceIn(-1.3f, 1.3f)
                val y = (-0.85f + (9.8f - fy) / 9.8f * 1.4f).coerceIn(-1.3f, 1.3f)
                val q = GlassLight((x * 20).roundToInt() / 20f, (y * 20).roundToInt() / 20f)
                if (q != state.value) state.value = q
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(listener) }
    }
    return state
}

/** Optics per glass level: Clear refracts hardest with almost no frost; Frosted diffuses most. */
/** [pane] = a bar floating over scrolling text: Clear softens what is behind so its own labels stay readable. */
private fun optics(level: GlassLevel, strong: Boolean, pane: Boolean = false): GlassOptics = when (level) {
    GlassLevel.CLEAR -> GlassOptics(
        refractionStrength = if (strong) 0.9f else 0.8f, refractionHeightFraction = 0.25f, refractionDisplacement = if (strong) 26.dp else 20.dp,
        depth = OpticalSizeValue.Fixed(1f), blurRadius = OpticalSizeValue.Fixed(if (pane) 9.dp else 1.5.dp), // Clear: no white frost; bars soften the text behind them
        refractionProfile = RefractionProfile.Edge(16.dp), refractionDetailIntensity = 0.8f,
    )
    GlassLevel.REGULAR -> GlassOptics(
        refractionStrength = if (strong) 0.95f else 0.75f, refractionHeightFraction = 0.3f, refractionDisplacement = if (strong) 52.dp else 34.dp,
        depth = OpticalSizeValue.Fixed(1f), blurRadius = OpticalSizeValue.Fixed(14.dp),
        refractionProfile = RefractionProfile.Edge(22.dp), refractionDetailIntensity = 0.76f,
    )
    GlassLevel.FROSTED -> GlassOptics(
        refractionStrength = 0.6f, refractionHeightFraction = 0.25f, refractionDisplacement = 26.dp,
        depth = OpticalSizeValue.Fixed(1f), blurRadius = OpticalSizeValue.Fixed(22.dp),
        refractionProfile = RefractionProfile.Edge(20.dp), refractionDetailIntensity = 0.7f,
    )
}

/**
 * The Liquid Glass material. [tint] washes the glass (milky white on the metal
 * backdrop, near-clear over artwork); [strong] = more lensing for small, hero
 * controls; [onMedia] tunes the lighting for glass over photos and colour.
 */
fun liquidStyle(
    level: GlassLevel,
    light: GlassLight,
    shape: RoundedCornerShape,
    tint: Color,
    strong: Boolean = false,
    onMedia: Boolean = false,
    pane: Boolean = false,
): GlassStyle = GlassStyle {
    shape(shape)
    optics(optics(level, strong, pane))
    tint(tint)
    lightPosition(BiasAlignment(light.x, light.y))
    surfaceProfile(if (strong) SurfaceProfile.Circle else SurfaceProfile.Squircle)
    specularIntensity(if (onMedia) 0.62f else 0.5f)
    specularExponent(if (strong) 20f else 26f)
    fresnelExponent(2.6f)
    edgeShadow(Color.Black.copy(alpha = if (onMedia) 0.22f else 0.12f))
    edgeSoftness(1.dp)
    val clear = level == GlassLevel.CLEAR
    ambientResponse(if (clear) 0.06f else if (onMedia) 0.3f else 0.2f)
    // A whisper of dispersion on the rim — the rainbow edge real glass throws.
    chromaticAberrationStrength(if (level == GlassLevel.CLEAR) 0.22f else 0.14f)
    chromaticAberrationMode(ChromaticAberrationMode.Simple)
    contrast(if (onMedia) 0.06f else 0.04f)
    // Light panes lift toward white; dark panes stay smoked.
    whitePoint(if (clear) 0f else if (onMedia) 0.05f else if (com.dopdop.bopdop.ui.theme.Bop.dark) 0f else 0.45f)
    chromaMultiplier(if (clear) 1f else if (onMedia) 1.15f else 1.25f)
    if (clear) contrast(0f)
    contentNormalBlend(0.15f)
    // Touch: the glass gives under the finger, brightens and bends light harder; the
    // light pools where you touch (Haze pivots the response on the pointer).
    interactionLightRadiusFraction(0.75f)
    interactionPositionAnimationSpec(spring(dampingRatio = 0.8f, stiffness = 500f))
    pressed {
        animate(spring(dampingRatio = 0.55f, stiffness = 600f), spring(dampingRatio = 0.6f, stiffness = 300f)) {
            scale(0.965f)
            lightingIntensity(1f)
            refractionMultiplier(1.45f)
            whitePointDelta(0.08f)
        }
    }
}
