package com.dopdop.bopdop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.dopdop.bopdop.R
import com.dopdop.bopdop.ui.glass.LocalReduceMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * "First Beat", BopDop's launch screen (output/BopDop-Splash-Concept): the coral b springs out of
 * dark acoustic ripples with a shine travelling across it, pulses spread from it, the wordmark
 * arrives letter by letter and a small waveform starts to beat. It plays once when the app starts,
 * long enough to land the first beat, then fades into the app. Laid out on the concept's 830 x 1896
 * canvas, scaled to cover the screen.
 */

/** Shown once per launch of the app. */
private var splashPlayed = false

/** How long the splash plays before it starts to fade (it runs on while the app gets ready). */
private const val PLAY_MS = 1900L
private const val FADE_MS = 450L
/** With motion reduced: the finished frame, briefly. */
private const val STILL_MS = 700L

@Composable
fun FirstBeatSplash() {
    if (splashPlayed) return
    val reduce = LocalReduceMotion.current
    var seconds by remember { mutableFloatStateOf(0f) }
    var fade by remember { mutableFloatStateOf(1f) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        val hold = if (reduce) STILL_MS else PLAY_MS
        while (true) {
            val ms = withFrameNanos { (it - start) / 1_000_000 }
            seconds = ms / 1000f
            if (ms > hold) fade = (1f - (ms - hold).toFloat() / FADE_MS).coerceIn(0f, 1f)
            if (ms > hold + FADE_MS) break
        }
        splashPlayed = true
        done = true
    }
    // Light status-bar icons over the dark splash, put back as it leaves.
    val view = LocalView.current
    DisposableEffect(done) {
        val showing = !done
        val window = (view.context as? android.app.Activity)?.window
        val bars = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val was = bars?.isAppearanceLightStatusBars
        if (showing) bars?.isAppearanceLightStatusBars = false
        onDispose { if (showing && was != null) bars.isAppearanceLightStatusBars = was }
    }
    if (done) return

    val background = ImageBitmap.imageResource(R.drawable.splash_background)
    val logo = ImageBitmap.imageResource(R.drawable.splash_logo)
    val wordmark = rememberVectorPainter(ImageVector.vectorResource(R.drawable.splash_wordmark))
    Canvas(
        // Covers the app as it starts; taps don't reach it underneath.
        Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
            .semantics { contentDescription = "BopDop" },
    ) {
        val t = if (reduce) 2.6f else seconds
        // The concept's canvas, scaled to cover the screen and centred.
        val s = max(size.width / W, size.height / H)
        val ox = (size.width - W * s) / 2
        val oy = (size.height - H * s) / 2
        drawRect(Charcoal.copy(alpha = fade))
        withTransform({ translate(ox, oy); scale(s, s, Offset.Zero) }) {
            drawFirstBeat(t, reduce, fade, background, logo) { x, y, w, h, alpha ->
                translate(x, y) { with(wordmark) { draw(Size(w, h), alpha = alpha) } }
            }
        }
    }
}

private const val W = 830f
private const val H = 1896f
private const val CX = 415f
private const val CY = 745f
private val Charcoal = Color(0xFF101112)

private fun clamp(v: Float) = v.coerceIn(0f, 1f)
private fun ease(v: Float) = 1f - (1f - clamp(v)).pow(3)
private fun smooth(v: Float) = clamp(v).let { it * it * (3f - 2f * it) }
private fun spring(v: Float) = clamp(v).let { if (it == 1f) 1f else 1f - exp(-6f * it) * cos(it * 8.4f) }

private fun DrawScope.drawFirstBeat(
    t: Float, reduce: Boolean, fade: Float, background: ImageBitmap, logo: ImageBitmap,
    wordmark: DrawScope.(x: Float, y: Float, w: Float, h: Float, alpha: Float) -> Unit,
) {
    val arrive = if (reduce) 1f else smooth((t - 0.04f) / 0.9f)
    val beatTime = max(0f, t - 1.18f)
    val beatPhase = (beatTime % 1.25f) / 1.25f
    val beat = if (reduce || t < 1.18f) 0f else max(0f, 1f - beatPhase * 3.5f).pow(2)

    // The rippled backdrop, breathing a hair, with a soft coral light that swells on each beat.
    val breathe = if (reduce) 1f else 1f + 0.003f * sin(t * PI.toFloat() * 0.8f)
    drawImage(background, dstOffset = IntOffset(((W - W * breathe) / 2).roundToInt(), (CY - CY * breathe).roundToInt()),
        dstSize = IntSize((W * breathe).roundToInt(), (H * breathe).roundToInt()), alpha = arrive * fade)
    if (!reduce) {
        drawRect(Brush.radialGradient(listOf(Coral.copy(alpha = (0.035f + 0.07f * beat) * fade), Coral.copy(alpha = 0f)), Offset(CX, CY), 350f),
            Offset(0f, 300f), Size(W, 1000f))
        // Sound pulses spreading out from the logo, one each beat.
        for (j in 0 until 4) {
            val p = (t - 0.88f - j * 1.25f) / 2.3f
            if (p < 0f || p > 1f) continue
            val r = 172f + ease(p) * 405f
            val a = sin(p * PI.toFloat()) * 0.25f * arrive * fade
            drawCircle(Glow.copy(alpha = a * 0.35f), r, Offset(CX, CY), style = Stroke(10f))
            drawCircle(Peach.copy(alpha = a), r, Offset(CX, CY), style = Stroke(2.3f * (1f - p) + 0.6f))
        }
        // A rim of light turning slowly round the inner ring.
        withTransform({ rotateRad(t * 0.5f, Offset(CX, CY)) }) {
            drawArc(Brush.linearGradient(0f to Color(0x00FF8959), 0.7f to Color(0x80FF9D6C), 1f to Color(0x00FFC493), start = Offset(CX - 230f, CY), end = Offset(CX + 230f, CY)),
                startAngle = (-0.9f * 180f / PI).toFloat(), sweepAngle = (1.28f * 180f / PI).toFloat(), useCenter = false,
                topLeft = Offset(CX - 226f, CY - 226f), size = Size(452f, 452f), alpha = arrive * 0.7f * fade, style = Stroke(2.4f))
        }
    }

    // The b springs up into place, floats a touch, beats with the music; a shine crosses it now and then.
    val appear = if (reduce) 1f else smooth((t - 0.16f) / 0.6f)
    val reveal = if (reduce) 1f else spring((t - 0.16f) / 1.05f)
    val pulse = if (reduce) 1f else 1f + 0.018f * beat
    val scale = (0.79f + 0.21f * reveal) * pulse
    val lh = 348f * scale
    val lw = lh * logo.width / logo.height
    val float = if (reduce) 0f else sin(max(0f, t - 1.05f) * PI.toFloat() * 0.7f) * 2.5f
    val logoTop = CY - lh / 2 + (1f - reveal) * 42f + float
    val logoRect = Rect(CX - lw / 2, logoTop, CX + lw / 2, logoTop + lh)
    drawIntoCanvas { c ->
        // A soft drop shadow, then the logo in its own layer so the shine only lands on the logo.
        drawOval(Color.Black.copy(alpha = 0.28f * appear * fade), Offset(logoRect.left + lw * 0.12f, logoRect.bottom - lh * 0.06f), Size(lw * 0.76f, lh * 0.12f))
        c.saveLayer(logoRect, Paint())
        drawImage(logo, dstOffset = IntOffset(logoRect.left.roundToInt(), logoRect.top.roundToInt()),
            dstSize = IntSize(lw.roundToInt(), lh.roundToInt()), alpha = appear * fade)
        if (!reduce) {
            val sweep = (t - 0.6f) % 2.7f
            val progress = sweep / 1.15f
            if (sweep >= 0f && progress <= 1f) {
                val sx = logoRect.left + (-430f + progress * 1400f) * lw / 500f
                drawRect(Brush.linearGradient(0f to Color(0x00FFF0DC), 0.46f to Color(0x00FFF0DC), 0.58f to Color(0x61FFE2BF), 0.72f to Color(0x00FFF0DC),
                    1f to Color(0x00FFF0DC), start = Offset(sx, logoRect.top), end = Offset(sx + 300f * lw / 500f, logoRect.top + 180f * lh / 634f)),
                    logoRect.topLeft, logoRect.size, blendMode = BlendMode.SrcAtop)
            }
        }
        c.restore()
    }

    // The wordmark, its six letters arriving left to right.
    val bounds = floatArrayOf(0f, 130f, 240f, 350f, 460f, 570f, 678f)
    val ww = 416f
    val wh = 112f
    for (i in 0 until 6) {
        val part = if (reduce) 1f else ease((t - 0.72f - i * 0.045f) / 0.55f)
        val x = 207f + bounds[i] / 678f * ww
        val w = (bounds[i + 1] - bounds[i]) / 678f * ww
        clipRect(x, 1210f, x + w, 1370f) { wordmark(207f, 1231f + (1f - part) * 18f, ww, wh, part * fade) }
    }

    // The little waveform, keeping time.
    val wave = (if (reduce) 1f else ease((t - 1.1f) / 0.45f)) * fade
    val heights = floatArrayOf(19f, 33f, 49f, 33f, 19f)
    for (i in 0 until 5) {
        val rhythm = if (reduce) 0.5f else sin(beatTime * 8.2f - i * 0.83f) * 0.5f + 0.5f
        val h = 15f + heights[i] * (0.25f + 0.55f * rhythm + 0.28f * beat)
        val x = CX + (i - 2) * 20f - 5.5f
        val y = 1584f - h / 2
        if (!reduce) drawRoundRect(Glow.copy(alpha = (0.18f + 0.2f * beat) * wave), Offset(x - 3f, y - 3f), Size(17f, h + 6f), CornerRadius(8.5f))
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFFB38B), Color(0xFFFF5637)), startY = y, endY = y + h), Offset(x, y), Size(11f, h),
            CornerRadius(5.5f), alpha = wave)
    }
}

private val Coral = Color(0xFFFF5B30)
private val Peach = Color(0xFFFF976A)
private val Glow = Color(0xFFFF632E)
