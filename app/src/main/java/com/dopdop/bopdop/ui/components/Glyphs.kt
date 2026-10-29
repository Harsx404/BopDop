package com.dopdop.bopdop.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Thin, rounded line glyphs — the reference's hairline icon style. */

private fun DrawScope.triangle(cx: Float, cy: Float, r: Float, pointRight: Boolean): Path {
    val dir = if (pointRight) 1f else -1f
    return Path().apply {
        moveTo(cx - dir * r * 0.55f, cy - r * 0.78f)
        lineTo(cx + dir * r * 0.85f, cy)
        lineTo(cx - dir * r * 0.55f, cy + r * 0.78f)
        close()
    }
}

/** Outline play ▷ that turns into two hairline pause bars. */
@Composable
fun LinePlayPause(playing: Boolean, modifier: Modifier = Modifier, color: Color = Color.Black, stroke: Dp = 2.2.dp) {
    if (com.dopdop.bopdop.ui.theme.Bop.halloween) {
        com.dopdop.bopdop.ui.festive.HalloweenGlyphIcon(if (playing) com.dopdop.bopdop.ui.festive.HalloweenGlyph.PAUSE else com.dopdop.bopdop.ui.festive.HalloweenGlyph.PLAY,
            if (playing) "Pause" else "Play", modifier, color)
        return
    }
    val p by animateFloatAsState(if (playing) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "lpp")
    Canvas(modifier) {
        val w = stroke.toPx()
        val st = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val c = center
        val r = size.minDimension / 2f
        if (p < 1f) scale(1f - p * 0.4f, c) {
            drawPath(triangle(c.x + r * 0.08f, c.y, r * 0.78f, true), color.copy(alpha = color.alpha * (1f - p)), style = st)
        }
        if (p > 0f) scale(0.6f + p * 0.4f, c) {
            val a = color.copy(alpha = color.alpha * p)
            val h = r * 0.62f
            drawLine(a, Offset(c.x - r * 0.26f, c.y - h), Offset(c.x - r * 0.26f, c.y + h), w * 1.15f, StrokeCap.Round)
            drawLine(a, Offset(c.x + r * 0.26f, c.y - h), Offset(c.x + r * 0.26f, c.y + h), w * 1.15f, StrokeCap.Round)
        }
    }
}

/** Hairline skip glyph: |◁ or ▷| */
@Composable
fun LineSkip(next: Boolean, modifier: Modifier = Modifier, color: Color = Color.White, stroke: Dp = 2.dp) {
    if (com.dopdop.bopdop.ui.theme.Bop.halloween) {
        com.dopdop.bopdop.ui.festive.HalloweenGlyphIcon(if (next) com.dopdop.bopdop.ui.festive.HalloweenGlyph.NEXT else com.dopdop.bopdop.ui.festive.HalloweenGlyph.PREVIOUS,
            if (next) "Next" else "Previous", modifier, color)
        return
    }
    Canvas(modifier) {
        val w = stroke.toPx()
        val r = size.minDimension / 2f
        val c = center
        val st = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val dir = if (next) 1f else -1f
        drawPath(triangle(c.x - dir * r * 0.12f, c.y, r * 0.62f, next), color, style = st)
        val x = c.x + dir * r * 0.66f
        drawLine(color, Offset(x, c.y - r * 0.5f), Offset(x, c.y + r * 0.5f), w, StrokeCap.Round)
    }
}
