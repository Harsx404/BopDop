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
import com.dopdop.bopdop.data.Festival
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
internal fun DrawScope.drawPet(pet: SeekPet, x: Float, feetY: Float, t: Float, mode: PetMode, facing: Float, costume: Festival? = null) {
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
            SeekPet.PEACOCK -> peacock(x, feetY, t, mode, u)
            SeekPet.RAVANA -> ravana(x, feetY, t, mode, u)
            SeekPet.GHOST -> ghost(x, feetY, t, mode, u)
            SeekPet.NONE -> {}
        }
        // Everyday pets dress up for the festival; festival pets are already dressed.
        if (costume != null && pet.festival == null) wear(costume, headTop(pet, x, feetY, t, mode, u), u)
    }
    if (mode == PetMode.SIT) sleepyZ(x + 6f * u * facing, feetY - 20f * u, t, u)
}

/** Top of the pet's head, where a hat or flower crown sits. Mirrors the drawing code below. */
private fun headTop(pet: SeekPet, x: Float, y: Float, t: Float, mode: PetMode, u: Float): Offset = when (pet) {
    SeekPet.CAT -> if (mode == PetMode.SIT) Offset(x + 3f * u, y - 17.5f * u)
        else Offset(x + 7f * u, y - 17.5f * u - abs(if (mode == PetMode.RUN) sin(t * 14f) else 0.6f) * 1.2f * u)
    SeekPet.BUNNY -> Offset(x + 4f * u, y - 15f * u - (if (mode == PetMode.RUN) abs(sin(t * 7f)) * 5f * u else 0f))
    SeekPet.DUCK -> Offset(x + 4f * u, y - 18f * u + (if (mode == PetMode.SIT) 2.5f * u else 0f))
    SeekPet.DINO -> if (mode == PetMode.SIT) Offset(x + 4.5f * u, y - 15f * u) else Offset(x + 5f * u, y - 21f * u)
    else -> Offset(x, y - 18f * u)
}

/** A marigold crown for Navaratri, a little witch hat for Halloween. */
private fun DrawScope.wear(f: Festival, head: Offset, u: Float) {
    when (f) {
        Festival.NAVARATRI -> {
            val flowers = listOf(-3.2f to 0.8f, 0f to -0.4f, 3.2f to 0.8f)
            flowers.forEachIndexed { i, (dx, dy) ->
                drawCircle(if (i == 1) Color(0xFFFF8F1F) else Color(0xFFFFC53D), 1.7f * u, Offset(head.x + dx * u, head.y + dy * u))
                drawCircle(Color(0xFFD9541A), 0.6f * u, Offset(head.x + dx * u, head.y + dy * u))
            }
        }
        Festival.HALLOWEEN -> {
            val hat = Color(0xFF2B1F3A)
            drawOval(hat, Offset(head.x - 5.5f * u, head.y - 0.6f * u), Size(11f * u, 2.4f * u))
            drawPath(Path().apply {
                moveTo(head.x - 3.5f * u, head.y + 0.2f * u)
                lineTo(head.x + 3.5f * u, head.y + 0.2f * u)
                quadraticTo(head.x + 1f * u, head.y - 5f * u, head.x - 2.5f * u, head.y - 8f * u)
                close()
            }, hat)
            drawLine(Color(0xFFFF7A1A), Offset(head.x - 3.2f * u, head.y - 1f * u), Offset(head.x + 3.2f * u, head.y - 1f * u), 1.1f * u)
        }
    }
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

/**
 * Navaratri's peacock: struts with its train trailing behind, and fans the tail out in a full
 * display while the music is paused.
 */
private fun DrawScope.peacock(x: Float, y: Float, t: Float, mode: PetMode, u: Float) {
    val blue = Color(0xFF1F5FBF); val green = Color(0xFF2E9E6A); val teal = Color(0xFF22B5A6); val gold = Color(0xFFE8B23A)
    fun eyeSpot(c: Offset, r: Float) {
        drawCircle(gold, r, c); drawCircle(teal, r * 0.72f, c); drawCircle(Color(0xFF173A7A), r * 0.4f, c)
    }
    if (mode == PetMode.SIT) {
        // The display: a fan of feathers behind the body.
        val base = Offset(x - 2f * u, y - 6f * u)
        for (i in 0..8) {
            val a = Math.toRadians((200.0 + i * 17.5)).toFloat()
            val tip = Offset(base.x + kotlin.math.cos(a) * 16f * u, base.y + sin(a) * 16f * u)
            drawLine(green, base, tip, 2f * u, StrokeCap.Round)
            eyeSpot(tip, 2.2f * u)
        }
        drawOval(blue, Offset(x - 5f * u, y - 11f * u), Size(8f * u, 10f * u))
        drawCircle(blue, 2.3f * u, Offset(x + 1.5f * u, y - 14f * u))
        drawLine(Ink, Offset(x + 1.8f * u, y - 14.3f * u), Offset(x + 3.2f * u, y - 14.3f * u), 0.8f * u, StrokeCap.Round)
        return
    }
    val run = if (mode == PetMode.RUN) sin(t * 12f) else 0f
    val sway = sin(t * 5f) * 1.2f * u
    // The train: long and closed, with eye spots along it.
    val train = Path().apply { moveTo(x - 4f * u, y - 9f * u); quadraticTo(x - 11f * u, y - 9f * u + sway, x - 18f * u, y - 5f * u + sway) }
    drawPath(train, green, style = Stroke(4f * u, cap = StrokeCap.Round))
    eyeSpot(Offset(x - 11f * u, y - 8.4f * u + sway * 0.6f), 1.6f * u)
    eyeSpot(Offset(x - 17f * u, y - 5.5f * u + sway), 1.8f * u)
    leg(x - 2f * u, y - 5f * u, y, 2f * u * run, Color(0xFFB59C7A), u)
    leg(x + 1f * u, y - 5f * u, y, -2f * u * run, Color(0xFFB59C7A), u)
    drawOval(blue, Offset(x - 5.5f * u, y - 13f * u), Size(9f * u, 8.5f * u))
    drawLine(blue, Offset(x + 2.5f * u, y - 10f * u), Offset(x + 4.5f * u, y - 16.5f * u), 2.8f * u, StrokeCap.Round)
    val head = Offset(x + 5f * u, y - 17.5f * u)
    drawCircle(blue, 2.3f * u, head)
    drawPath(Path().apply { moveTo(head.x + 2f * u, head.y - 0.4f * u); lineTo(head.x + 4.2f * u, head.y + 0.4f * u); lineTo(head.x + 2f * u, head.y + 1f * u); close() }, gold)
    drawCircle(Color.White, 0.7f * u, Offset(head.x + 0.8f * u, head.y - 0.5f * u))
    drawCircle(Ink, 0.4f * u, Offset(head.x + 0.9f * u, head.y - 0.5f * u))
    // Crest: three little plumes.
    for (k in -1..1) {
        val tip = Offset(head.x + k * 1.2f * u, head.y - 4.6f * u)
        drawLine(blue, Offset(head.x, head.y - 2f * u), tip, 0.6f * u)
        drawCircle(teal, 0.7f * u, tip)
    }
}

/**
 * Dussehra's Ravana effigy: ten crowned heads on a tall robed frame, marching stiffly on its
 * bamboo legs. When the music pauses it stands still and a few sparks fly off it, as the
 * effigies do on Dussehra night.
 */
private fun DrawScope.ravana(x: Float, y: Float, t: Float, mode: PetMode, u: Float) {
    val robe = Color(0xFFC62D2D); val trim = Color(0xFFF2B33D); val skin = Color(0xFF6B8FD9); val bamboo = Color(0xFFB98D5A)
    val stride = if (mode == PetMode.RUN) sin(t * 10f) * 1.6f * u else 0f
    // Bamboo legs.
    drawLine(bamboo, Offset(x - 2f * u, y - 6f * u), Offset(x - 2f * u + stride, y), 1.6f * u, StrokeCap.Round)
    drawLine(bamboo, Offset(x + 2f * u, y - 6f * u), Offset(x + 2f * u - stride, y), 1.6f * u, StrokeCap.Round)
    // Robe: a flared tunic with a gold hem and belt.
    drawPath(Path().apply {
        moveTo(x - 3.5f * u, y - 15f * u); lineTo(x + 3.5f * u, y - 15f * u)
        lineTo(x + 5.5f * u, y - 6f * u); lineTo(x - 5.5f * u, y - 6f * u); close()
    }, robe)
    drawLine(trim, Offset(x - 5.3f * u, y - 6.4f * u), Offset(x + 5.3f * u, y - 6.4f * u), 1f * u)
    drawLine(trim, Offset(x - 3.8f * u, y - 11f * u), Offset(x + 3.8f * u, y - 11f * u), 0.9f * u)
    // Arms out, holding a little sword on the right.
    drawLine(robe, Offset(x - 3.5f * u, y - 14f * u), Offset(x - 7f * u, y - 10.5f * u), 1.6f * u, StrokeCap.Round)
    drawLine(robe, Offset(x + 3.5f * u, y - 14f * u), Offset(x + 7f * u, y - 10.5f * u), 1.6f * u, StrokeCap.Round)
    drawLine(Color(0xFFD8DEE8), Offset(x + 7f * u, y - 10.5f * u), Offset(x + 9.5f * u, y - 16f * u), 0.9f * u, StrokeCap.Round)
    // Ten heads in a row: the main one in the middle, four to the left and five to the right.
    val headY = y - 18.5f * u
    for (k in -4..5) {
        if (k == 0) continue
        val hx = x + k * 2.5f * u
        val hy = headY + kotlin.math.abs(k) * 0.35f * u
        drawCircle(skin, 1.4f * u, Offset(hx, hy))
        drawPath(Path().apply { moveTo(hx - 1.2f * u, hy - 1f * u); lineTo(hx, hy - 3f * u); lineTo(hx + 1.2f * u, hy - 1f * u); close() }, trim)
    }
    drawCircle(skin, 2.6f * u, Offset(x, headY))
    // Main crown, eyes and moustache.
    drawPath(Path().apply {
        moveTo(x - 2.4f * u, headY - 1.6f * u); lineTo(x - 1.6f * u, headY - 5f * u); lineTo(x, headY - 3.4f * u)
        lineTo(x + 1.6f * u, headY - 5f * u); lineTo(x + 2.4f * u, headY - 1.6f * u); close()
    }, trim)
    drawCircle(Ink, 0.45f * u, Offset(x - 0.9f * u, headY - 0.3f * u))
    drawCircle(Ink, 0.45f * u, Offset(x + 0.9f * u, headY - 0.3f * u))
    drawPath(Path().apply {
        moveTo(x - 1.8f * u, headY + 1.6f * u); quadraticTo(x, headY + 0.6f * u, x + 1.8f * u, headY + 1.6f * u)
    }, Ink, style = Stroke(0.6f * u, cap = StrokeCap.Round))
    if (mode == PetMode.SIT) {
        // Dussehra night: sparks rising off the effigy.
        for (i in 0 until 5) {
            val p = ((t * 0.8f + i * 0.21f) % 1f)
            val sx = x + (i - 2) * 2.4f * u + sin(t * 3f + i) * 1f * u
            val sy = y - 8f * u - p * 18f * u
            drawCircle((if (i % 2 == 0) Color(0xFFFFC53D) else Color(0xFFFF8F1F)).copy(alpha = 1f - p), 0.8f * u, Offset(sx, sy))
        }
    }
}

/** Halloween's ghost: floats rather than walks, its hem rippling; dozes off when paused. */
private fun DrawScope.ghost(x: Float, y: Float, t: Float, mode: PetMode, u: Float) {
    val float = sin(t * 3f) * 1.4f * u
    val sheet = Color.White.copy(alpha = if (mode == PetMode.SIT) 0.8f else 0.95f)
    val top = y - 19f * u + float
    val hem = y - 3f * u + float
    val body = Path().apply {
        moveTo(x - 7f * u, hem)
        lineTo(x - 7f * u, top + 7f * u)
        cubicTo(x - 7f * u, top - 2f * u, x + 7f * u, top - 2f * u, x + 7f * u, top + 7f * u)
        lineTo(x + 7f * u, hem)
        // Three scallops along the hem, rippling.
        val ripple = if (mode == PetMode.SIT) 0f else t * 7f
        for (i in 0 until 3) {
            val x1 = x + 7f * u - (i + 1) * (14f / 3f) * u
            val dip = (2.2f + sin(ripple + i * 1.7f)) * u
            quadraticTo((x1 + x + 7f * u - i * (14f / 3f) * u) / 2f, hem + dip, x1, hem)
        }
        close()
    }
    drawPath(body, sheet)
    if (mode == PetMode.SIT) {
        drawLine(Ink, Offset(x + 0.5f * u, top + 6f * u), Offset(x + 2.5f * u, top + 6f * u), 0.9f * u, StrokeCap.Round)
        drawLine(Ink, Offset(x + 4f * u, top + 6f * u), Offset(x + 6f * u, top + 6f * u), 0.9f * u, StrokeCap.Round)
    } else {
        drawOval(Ink, Offset(x + 0.6f * u, top + 4.5f * u), Size(1.8f * u, 2.6f * u))
        drawOval(Ink, Offset(x + 4f * u, top + 4.5f * u), Size(1.8f * u, 2.6f * u))
        drawOval(Ink, Offset(x + 2.3f * u, top + 8.6f * u), Size(1.6f * u, 1.9f * u))
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
