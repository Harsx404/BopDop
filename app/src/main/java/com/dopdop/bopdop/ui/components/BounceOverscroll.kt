package com.dopdop.bopdop.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * iOS-style rubber band at the ends of every list and scroller: dragging past the edge pulls the
 * content with growing resistance, and it springs back on release. A fling that hits the edge
 * carries a little of its speed into the bounce. Provided once at the root (LocalOverscrollFactory).
 */
class BounceOverscrollFactory(private val scope: CoroutineScope, private val maxPullPx: Float) : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect = BounceOverscroll(scope, maxPullPx)
    override fun equals(other: Any?) = other is BounceOverscrollFactory && other.scope == scope && other.maxPullPx == maxPullPx
    override fun hashCode() = 31 * scope.hashCode() + maxPullPx.hashCode()
}

private class BounceOverscroll(private val scope: CoroutineScope, private val maxPull: Float) : OverscrollEffect {
    private val pull = Animatable(Offset.Zero, Offset.VectorConverter)

    override val isInProgress: Boolean get() = pull.isRunning || pull.value != Offset.Zero

    /** Leftover drag becomes pull, less and less the further it's already pulled. */
    private fun stretch(current: Float, extra: Float): Float {
        val room = (1f - abs(current) / maxPull).coerceIn(0f, 1f)
        return (current + extra * 0.55f * room).coerceIn(-maxPull, maxPull)
    }

    /** Scrolling back the other way first takes up the pull, then scrolls. */
    private fun release(current: Float, delta: Float): Pair<Float, Float> {
        if (current == 0f || delta == 0f || sign(delta) == sign(current)) return current to 0f
        val next = current + delta
        return if (sign(next) == sign(current)) next to delta else 0f to -current
    }

    override fun applyToScroll(delta: Offset, source: NestedScrollSource, performScroll: (Offset) -> Offset): Offset {
        val cur = pull.value
        val (px, usedX) = release(cur.x, delta.x)
        val (py, usedY) = release(cur.y, delta.y)
        val rest = Offset(delta.x - usedX, delta.y - usedY)
        val consumed = performScroll(rest)
        val left = rest - consumed
        var next = Offset(px, py)
        if (source == NestedScrollSource.UserInput) next = Offset(stretch(next.x, left.x), stretch(next.y, left.y))
        if (next != cur) scope.launch { pull.snapTo(next) }
        return Offset(usedX, usedY) + consumed + if (source == NestedScrollSource.UserInput) left else Offset.Zero
    }

    override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
        val left = velocity - performFling(velocity)
        // A fling that runs into the edge nudges it a little, then everything springs home.
        val kick = Offset(left.x, left.y) * 0.08f
        if (pull.value == Offset.Zero && kick.getDistance() < 1f) return
        pull.animateTo(Offset.Zero, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 260f), initialVelocity = kick)
    }

    override val node: DelegatableNode = object : Modifier.Node(), LayoutModifierNode {
        override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
            val p = measurable.measure(constraints)
            return layout(p.width, p.height) {
                // Read inside the layer block, so a bounce only re-draws, never re-measures.
                p.placeWithLayer(0, 0) { translationX = pull.value.x; translationY = pull.value.y }
            }
        }
    }
}
