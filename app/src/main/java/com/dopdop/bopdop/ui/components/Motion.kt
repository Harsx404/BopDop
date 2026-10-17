package com.dopdop.bopdop.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.dopdop.bopdop.ui.glass.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rises and fades in the first time it appears, [order] steps after its neighbours. Remembered per
 * list item, so scrolling back up doesn't replay it; off when motion is reduced.
 */
fun Modifier.riseIn(order: Int = 0): Modifier = composed {
    val reduce = LocalReduceMotion.current
    var done by rememberSaveable { mutableStateOf(reduce) }
    val a = remember { Animatable(if (done) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (done) return@LaunchedEffect
        delay(45L * order.coerceAtMost(6))
        a.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 220f))
        done = true
    }
    graphicsLayer {
        alpha = a.value.coerceIn(0f, 1f)
        translationY = (1f - a.value) * 28.dp.toPx()
        val s = 0.97f + 0.03f * a.value
        scaleX = s; scaleY = s
    }
}

/**
 * A little burst of hearts and sparks from the centre, played each time [trigger] goes up. Draw it
 * over (and a bit larger than) the button it celebrates.
 */
@Composable
fun Burst(trigger: Int, color: Color, modifier: Modifier = Modifier) {
    val p = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        p.snapTo(0f)
        p.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    if (p.value >= 1f) return
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val reach = size.minDimension / 2
        val t = p.value
        val fade = (1f - t)
        // A ring that expands and thins out.
        drawCircle(color.copy(alpha = 0.45f * fade), reach * (0.35f + 0.65f * t), c, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx() * fade))
        for (i in 0 until 8) {
            val a = (i / 8f) * 2f * Math.PI.toFloat() + 0.3f
            val d = reach * (0.45f + 0.55f * t)
            val o = Offset(c.x + cos(a) * d, c.y + sin(a) * d)
            if (i % 2 == 0) heart(o, 4.dp.toPx() * (1f - 0.4f * t), color.copy(alpha = fade))
            else drawCircle(color.copy(alpha = fade), 2.dp.toPx() * (1f - 0.5f * t), o)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.heart(at: Offset, r: Float, color: Color) {
    val path = Path().apply {
        moveTo(at.x, at.y + r * 0.9f)
        cubicTo(at.x - r * 1.6f, at.y - r * 0.2f, at.x - r * 0.6f, at.y - r * 1.4f, at.x, at.y - r * 0.4f)
        cubicTo(at.x + r * 0.6f, at.y - r * 1.4f, at.x + r * 1.6f, at.y - r * 0.2f, at.x, at.y + r * 0.9f)
        close()
    }
    drawPath(path, color)
}
