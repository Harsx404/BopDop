package com.dopdop.bopdop.ui.player

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.dopdop.bopdop.data.SeekPet
import kotlin.math.abs
import kotlin.math.sin

/** What the pet is doing: trotting with the music, napping while paused, hopping while you scrub. */
internal enum class PetMode { RUN, SIT, JUMP }

private val Ink = Color(0xFF1E1A22)

/**
 * Draws [pet] standing on the point ([x], [feetY]), facing right (or left when [facing] < 0).
 * [t] is seconds of animation time; all sizes come from dp so the pet looks the same on any phone.
 * Shapes, not bitmaps, so every seek bar style can host it at any position.
 */
internal fun DrawScope.drawPet(pet: SeekPet, x: Float, feetY: Float, t: Float, mode: PetMode, facing: Float) {
    if (pet == SeekPet.NONE) return
    val u = 1.dp.toPx()
    // A soft shadow keeps it standing on the bar instead of floating over it.
    val lift = when (mode) { PetMode.JUMP -> 7f * u * abs(sin(t * 9f)); else -> 0f }
    drawOval(Color.Black.copy(alpha = 0.22f * (1f - lift / (9f * u))), Offset(x - 8f * u, feetY - 1.5f * u), Size(16f * u, 3f * u))
    withTransform({ scale(facing, 1f, Offset(x, feetY)); translate(0f, -lift) }) {
        when (pet) {
            SeekPet.CAT -> cat(x, feetY, t, mode, u)
            SeekPet.BUNNY -> bunny(x, feetY, t, mode, u)
            SeekPet.DUCK -> duck(x, feetY, t, mode, u)
            SeekPet.DINO -> dino(x, feetY, t, mode, u)
            SeekPet.NONE -> {}
        }
    }
    if (mode == PetMode.SIT) sleepyZ(x + 6f * u * facing, feetY - 20f * u, t, u)
}

/** Little "z"s drifting up from a napping pet. */
private fun DrawScope.sleepyZ(x: Float, y: Float, t: Float, u: Float) {
    for (k in 0..1) {
        val p = ((t * 0.45f + k * 0.5f) % 1f)
        val s = (2.2f + p * 1.6f) * u
        val zx = x + p * 6f * u + k * 2f * u
        val zy = y - p * 10f * u
        val a = (1f - p) * 0.9f
        val z = Path().apply { moveTo(zx, zy); lineTo(zx + s, zy); lineTo(zx, zy + s); lineTo(zx + s, zy + s) }
        drawPath(z, Color.White.copy(alpha = a), style = Stroke(0.9f * u, cap = StrokeCap.Round))
    }
}

private fun DrawScope.leg(x: Float, top: Float, bottom: Float, swing: Float, color: Color, u: Float) =
    drawLine(color, Offset(x, top), Offset(x + swing, bottom), 2.2f * u, StrokeCap.Round)

private fun DrawScope.cat(x: Float, y: Float, t: Float, mode: PetMode, u: Float) {
    val fur = Color(0xFFF4A259); val dark = Color(0xFFC9753A)
    if (mode == PetMode.SIT) {
        // Curled up, tail wrapped round the front.
        drawLine(dark, Offset(x - 7f * u, y - 1.5f * u), Offset(x + 4f * u, y - 1f * u), 2.4f * u, StrokeCap.Round)
        drawOval(fur, Offset(x - 7f * u, y - 12f * u), Size(12f * u, 11f * u))
        drawCircle(fur, 5f * u, Offset(x + 3f * u, y - 13f * u))
        ears(x + 3f * u, y - 13f * u, fur, u)
        drawLine(Ink, Offset(x + 3.8f * u, y - 13.5f * u), Offset(x + 6.2f * u, y - 13.5f * u), 0.9f * u, StrokeCap.Round)
        return
    }
    val run = if (mode == PetMode.RUN) sin(t * 14f) else 0.6f
    val bob = abs(run) * 1.2f * u
    // Tail flicks behind.
    val tail = Path().apply { moveTo(x - 8f * u, y - 8f * u - bob); quadraticTo(x - 13f * u, y - 9f * u, x - 13f * u, y - 15f * u + sin(t * 8f) * 2f * u) }
    drawPath(tail, fur, style = Stroke(2.2f * u, cap = StrokeCap.Round))
    leg(x - 6f * u, y - 5f * u - bob, y, 2.5f * u * run, dark, u)
    leg(x + 3f * u, y - 5f * u - bob, y, -2.5f * u * run, dark, u)
    drawOval(fur, Offset(x - 10f * u, y - 12f * u - bob), Size(16f * u, 8.5f * u))
    leg(x - 4f * u, y - 5f * u - bob, y, -2.5f * u * run, fur, u)
    leg(x + 5f * u, y - 5f * u - bob, y, 2.5f * u * run, fur, u)
    drawLine(dark, Offset(x - 5f * u, y - 11.5f * u - bob), Offset(x - 4f * u, y - 8.5f * u - bob), 1.2f * u, StrokeCap.Round)
    drawLine(dark, Offset(x - 2f * u, y - 11.5f * u - bob), Offset(x - 1f * u, y - 8.5f * u - bob), 1.2f * u, StrokeCap.Round)
    drawCircle(fur, 5f * u, Offset(x + 7f * u, y - 13f * u - bob))
    ears(x + 7f * u, y - 13f * u - bob, fur, u)
    drawCircle(Ink, 0.95f * u, Offset(x + 9f * u, y - 13.6f * u - bob))
    drawCircle(Color(0xFFFF8FA3), 0.8f * u, Offset(x + 11.6f * u, y - 12f * u - bob))
}

private fun DrawScope.ears(cx: Float, cy: Float, color: Color, u: Float) {
    drawPath(Path().apply { moveTo(cx - 4.5f * u, cy - 2f * u); lineTo(cx - 3f * u, cy - 8f * u); lineTo(cx - 0.5f * u, cy - 4.5f * u); close() }, color)
    drawPath(Path().apply { moveTo(cx + 0.5f * u, cy - 4.5f * u); lineTo(cx + 3f * u, cy - 8f * u); lineTo(cx + 4.5f * u, cy - 2f * u); close() }, color)
}

private fun DrawScope.bunny(x: Float, y: Float, t: Float, mode: PetMode, u: Float) {
    val fur = Color(0xFFF7F4F2); val pink = Color(0xFFFFB3C6)
    val hop = if (mode == PetMode.RUN) abs(sin(t * 7f)) * 5f * u else 0f
    val earTilt = if (mode == PetMode.SIT) -35f else -8f + sin(t * 7f) * 6f
    val hx = x + 4f * u; val hy = y - 11f * u - hop
    // Ears first, so the head sits over their roots.
    for ((dx, extra) in listOf(-1.5f to 0f, 1.5f to 10f)) {
        rotate(earTilt + extra, Offset(hx + dx * u, hy - 3f * u)) {
            drawOval(fur, Offset(hx + dx * u - 1.6f * u, hy - 13f * u), Size(3.2f * u, 10f * u))
            drawOval(pink, Offset(hx + dx * u - 0.8f * u, hy - 11.5f * u), Size(1.6f * u, 7f * u))
        }
    }
    drawCircle(fur, 2.6f * u, Offset(x - 8.5f * u, y - 7f * u - hop))
    drawOval(fur, Offset(x - 9f * u, y - 11f * u - hop), Size(13f * u, 10f * u))
    drawOval(fur, Offset(x - 1f * u, y - 2.2f * u - hop * 0.3f), Size(5f * u, 2.4f * u))
    drawCircle(fur, 4.6f * u, Offset(hx, hy))
    if (mode == PetMode.SIT) drawLine(Ink, Offset(hx + 1f * u, hy - 0.5f * u), Offset(hx + 3f * u, hy - 0.5f * u), 0.9f * u, StrokeCap.Round)
    else drawCircle(Ink, 0.95f * u, Offset(hx + 2f * u, hy - 0.8f * u))
    drawCircle(pink, 0.8f * u, Offset(hx + 4.4f * u, hy + 0.8f * u))
}

private fun DrawScope.duck(x: Float, y: Float, t: Float, mode: PetMode, u: Float) {
    val body = Color(0xFFFFD84D); val beak = Color(0xFFFF9A3C); val wing = Color(0xFFF2C230)
    val waddle = if (mode == PetMode.RUN) sin(t * 12f) * 8f else 0f
    rotate(waddle, Offset(x, y)) {
        if (mode != PetMode.SIT) {
            val step = if (mode == PetMode.RUN) sin(t * 12f) * 1.5f * u else 0f
            drawLine(beak, Offset(x - 2f * u, y - 3f * u), Offset(x - 2f * u + step, y), 1.6f * u, StrokeCap.Round)
            drawLine(beak, Offset(x + 2f * u, y - 3f * u), Offset(x + 2f * u - step, y), 1.6f * u, StrokeCap.Round)
        }
        val sink = if (mode == PetMode.SIT) 2.5f * u else 0f
        drawOval(body, Offset(x - 8f * u, y - 11f * u + sink), Size(15f * u, 9f * u))
        drawPath(Path().apply { moveTo(x - 8f * u, y - 8f * u + sink); lineTo(x - 11f * u, y - 11f * u + sink); lineTo(x - 7f * u, y - 9.5f * u + sink); close() }, body)
        drawOval(wing, Offset(x - 5f * u, y - 9f * u + sink), Size(7f * u, 4f * u))
        drawCircle(body, 4.4f * u, Offset(x + 4f * u, y - 14f * u + sink))
        drawPath(Path().apply { moveTo(x + 7.5f * u, y - 14.5f * u + sink); lineTo(x + 11.5f * u, y - 13f * u + sink); lineTo(x + 7.5f * u, y - 11.8f * u + sink); close() }, beak)
        if (mode == PetMode.SIT) drawLine(Ink, Offset(x + 4.2f * u, y - 15f * u + sink), Offset(x + 6.2f * u, y - 15f * u + sink), 0.9f * u, StrokeCap.Round)
        else drawCircle(Ink, 0.95f * u, Offset(x + 5.3f * u, y - 15.2f * u))
    }
}

/** A blocky little dino, like the one that runs when the internet is down. */
private fun DrawScope.dino(x: Float, y: Float, t: Float, mode: PetMode, u: Float) {
    val skin = Color(0xFF8BD17C); val dark = Color(0xFF5FA653)
    fun block(l: Float, top: Float, w: Float, h: Float, c: Color = skin) = drawRect(c, Offset(x + l * u, y + top * u), Size(w * u, h * u))
    if (mode == PetMode.SIT) {
        block(-9f, -4f, 4f, 2.5f); block(-6f, -9f, 10f, 9f); block(0f, -15f, 9f, 6.5f)
        block(5.5f, -13.5f, 3.5f, 0.9f, Ink); block(2f, -5f, 3f, 1.4f, dark)
        return
    }
    val stride = if (mode == PetMode.RUN) ((t * 10f).toInt() % 2) else 0
    block(-12f, -12f, 3f, 2.5f); block(-10f, -13f, 4f, 3.5f)
    block(-7f, -15f, 9f, 10f)
    block(0f, -21f, 10f, 7f)
    block(6.5f, -19.5f, 1.4f, 1.4f, Ink)
    block(4f, -15.8f, 6f, 1f, dark)
    block(2f, -11f, 3f, 1.5f, dark)
    block(-5f, -5f, 2.4f, if (stride == 0) 5f else 3f)
    block(-1f, -5f, 2.4f, if (stride == 1) 5f else 3f)
}
