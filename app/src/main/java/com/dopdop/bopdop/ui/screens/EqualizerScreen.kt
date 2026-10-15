package com.dopdop.bopdop.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.playback.AudioFx
import com.dopdop.bopdop.playback.EqState
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.glass.glassLite
import com.dopdop.bopdop.ui.glass.softShadow
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType
import kotlin.math.abs
import kotlin.math.roundToInt

private val EqSpectrum = listOf(Color(0xFF6C5CFF), Color(0xFFB45CFF), Color(0xFFFF5C9D), Color(0xFFFF9F5C))
private val Off = Color(0xFF9097A3)

private fun set(s: EqState) {
    AudioFx.state.value = s
    LibraryStore.updateSettings { it.copy(eq = s) }
}

@Composable
fun EqualizerScreen() {
    val s by AudioFx.state.collectAsState()
    LargeTitleScaffold(title = "Equalizer", eyebrow = "Sound", showBack = true) {
        item {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp).fillMaxWidth()
                    .glassLite(RoundedCornerShape(30.dp), alpha = 0.5f, shadow = 16.dp)
                    .padding(top = 18.dp, bottom = 14.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (s.enabled) s.preset else "Off", style = BopType.Title, color = Bop.Text)
                        Text(if (s.enabled) "Drag a point to shape the sound" else "Turn on, or just drag a point", style = BopType.Caption, color = Bop.Text2)
                    }
                    Box(Modifier.bounceClick(0.92f) { set(s.copy(enabled = !s.enabled)) }) { GlassSwitch(s.enabled) }
                }
                Spacer(Modifier.height(14.dp))
                CurveEditor(s) { set(it) }
            }
        }
        item {
            Text("PRESETS", style = BopType.Micro, color = Bop.Text3, modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 10.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(AudioFx.PRESETS, key = { it.first }) { (name, gains) ->
                    PresetCard(name, gains, on = s.enabled && s.preset == name) {
                        set(s.copy(enabled = true, preset = name, gains = gains))
                    }
                }
            }
        }
        item {
            Text("ENHANCE", style = BopType.Micro, color = Bop.Text3, modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 10.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillSlider("Bass", "Low-end weight", s.bass, s.enabled, Modifier.weight(1f)) { set(s.copy(enabled = true, bass = it)) }
                PillSlider("Spatial", "Wider on headphones", s.spatial, s.enabled, Modifier.weight(1f)) { set(s.copy(enabled = true, spatial = it)) }
            }
        }
        item {
            Text(
                "Reset to flat", style = BopType.BodyStrong, color = Bop.Text2, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 36.dp).bounceClick { set(EqState(enabled = s.enabled)) }.padding(10.dp),
            )
        }
    }
}

/** The ten-band curve: a smooth line through draggable points, filled with the spectrum. */
@Composable
private fun CurveEditor(s: EqState, onChange: (EqState) -> Unit) {
    var dragging by remember { mutableIntStateOf(-1) }
    val n = AudioFx.FREQS.size
    val shown = List(n) { i ->
        animateFloatAsState(s.gains[i], if (dragging == i) snap() else spring(dampingRatio = 0.78f, stiffness = 260f), label = "g$i").value
    }
    val dim by animateFloatAsState(if (s.enabled) 1f else 0.45f, label = "dim")
    val latest = androidx.compose.runtime.rememberUpdatedState(s)

    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(236.dp).padding(horizontal = 6.dp)) {
            val padX = 22.dp
            val padY = 22.dp
            Canvas(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    val px = padX.toPx(); val py = padY.toPx()
                    fun bandAt(x: Float) = (((x - px) / (size.width - 2 * px)) * (n - 1)).roundToInt().coerceIn(0, n - 1)
                    fun dbAt(y: Float) = (((size.height / 2f - y) / (size.height / 2f - py)) * AudioFx.MAX_DB).coerceIn(-AudioFx.MAX_DB, AudioFx.MAX_DB)
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val band = bandAt(down.position.x)
                        dragging = band
                        fun push(y: Float) {
                            val cur = latest.value
                            val g = (dbAt(y) * 2).roundToInt() / 2f // half-dB steps
                            if (g != cur.gains[band]) onChange(cur.copy(enabled = true, preset = "Custom", gains = cur.gains.toMutableList().also { it[band] = g }))
                        }
                        push(down.position.y)
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!c.pressed) break
                            if (c.positionChange() != Offset.Zero) { c.consume(); push(c.position.y) }
                        }
                        dragging = -1
                    }
                },
            ) {
                val px = padX.toPx(); val py = padY.toPx()
                val w = size.width; val h = size.height
                val mid = h / 2f
                val span = mid - py
                fun xOf(i: Int) = px + i * (w - 2 * px) / (n - 1)
                fun yOf(db: Float) = mid - db / AudioFx.MAX_DB * span

                // Grid: ±12, ±6 dashed, 0 solid.
                val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 6.dp.toPx()))
                listOf(-12f, -6f, 6f, 12f).forEach { db ->
                    drawLine(Bop.Ink.copy(alpha = 0.08f), Offset(px, yOf(db)), Offset(w - px, yOf(db)), 1.dp.toPx(), pathEffect = dash)
                }
                drawLine(Bop.Ink.copy(alpha = 0.16f), Offset(px, mid), Offset(w - px, mid), 1.dp.toPx())
                for (i in 0 until n) drawLine(Bop.Ink.copy(alpha = 0.05f), Offset(xOf(i), py), Offset(xOf(i), h - py), 1.dp.toPx())

                // Smooth Catmull-Rom curve through the points.
                val pts = List(n) { Offset(xOf(it), yOf(shown[it])) }
                val curve = Path().apply {
                    moveTo(0f, pts[0].y); lineTo(pts[0].x, pts[0].y)
                    for (i in 0 until n - 1) {
                        val p0 = pts[(i - 1).coerceAtLeast(0)]; val p1 = pts[i]; val p2 = pts[i + 1]; val p3 = pts[(i + 2).coerceAtMost(n - 1)]
                        cubicTo(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f, p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f, p2.x, p2.y)
                    }
                    lineTo(w, pts.last().y)
                }
                val fill = Path().apply { addPath(curve); lineTo(w, mid); lineTo(0f, mid); close() }
                val colors = if (s.enabled) EqSpectrum else listOf(Off, Off)
                val spectrum = Brush.horizontalGradient(colors)
                drawPath(fill, Brush.horizontalGradient(colors.map { it.copy(alpha = 0.22f * dim) }))
                // Soft glow under the line, then the line.
                drawPath(curve, spectrum, alpha = 0.25f * dim, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
                drawPath(curve, spectrum, alpha = dim, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))

                // Knobs.
                pts.forEachIndexed { i, p ->
                    val active = i == dragging
                    val r = if (active) 11.dp.toPx() else 7.dp.toPx()
                    drawCircle(Color.Black.copy(alpha = 0.12f), r + 2.dp.toPx(), p.copy(y = p.y + 1.5.dp.toPx()))
                    drawCircle(Color.White, r, p)
                    drawCircle(if (s.enabled) lerpSpectrum(i / (n - 1f)) else Off, r * 0.45f, p)
                }
                // Value bubble over the dragged point.
                if (dragging >= 0) {
                    val p = pts[dragging]
                    val bw = 46.dp.toPx(); val bh = 24.dp.toPx()
                    val bx = (p.x - bw / 2).coerceIn(0f, w - bw)
                    val by = (p.y - bh - 18.dp.toPx()).let { if (it < 0f) p.y + 18.dp.toPx() else it }
                    drawRoundRect(Bop.Ink, Offset(bx, by), Size(bw, bh), CornerRadius(bh / 2))
                }
            }
            if (dragging >= 0) {
                // The bubble's label (text is easier as a composable).
                val g = shown[dragging]
                val density = androidx.compose.ui.platform.LocalDensity.current
                with(density) {
                    val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
                    val px = padX.toPx(); val py = padY.toPx()
                    val x = px + dragging * (w - 2 * px) / (n - 1)
                    val y = h / 2f - g / AudioFx.MAX_DB * (h / 2f - py)
                    val bw = 46.dp.toPx(); val bh = 24.dp.toPx()
                    val bx = (x - bw / 2).coerceIn(0f, w - bw)
                    val by = (y - bh - 18.dp.toPx()).let { if (it < 0f) y + 18.dp.toPx() else it }
                    Text(
                        (if (g > 0) "+" else "") + (if (abs(g % 1f) < 0.01f) g.toInt().toString() else "%.1f".format(g)) + " dB",
                        style = BopType.Micro.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified), color = Color.White, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(start = bx.toDp(), top = by.toDp() + 6.dp).width(bw.toDp()),
                    )
                }
            }
        }
        // Each label centred under its knob.
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
            val step = (maxWidth - 44.dp) / (n - 1)
            AudioFx.FREQS.forEachIndexed { i, f ->
                Text(
                    if (f >= 1000) "${f / 1000}k" else "$f", style = BopType.Micro.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
                    color = if (i == dragging) Bop.Text else Bop.Text3, textAlign = TextAlign.Center, maxLines = 1,
                    modifier = Modifier.padding(start = 22.dp + step * i - 18.dp).width(36.dp),
                )
            }
        }
    }
}

private fun lerpSpectrum(t: Float): Color {
    val seg = t * (EqSpectrum.size - 1)
    val i = seg.toInt().coerceAtMost(EqSpectrum.size - 2)
    return androidx.compose.ui.graphics.lerp(EqSpectrum[i], EqSpectrum[i + 1], seg - i)
}

@Composable
private fun PresetCard(name: String, gains: List<Float>, on: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) Bop.Ink else Color.White.copy(alpha = 0.55f), label = "pc")
    Column(
        Modifier.width(112.dp).bounceClick(0.94f, onClick = onClick)
            .softShadow(RoundedCornerShape(20.dp), 10.dp, alpha = if (on) 0.22f else 0.08f)
            .clip(RoundedCornerShape(20.dp)).background(bg).padding(14.dp),
    ) {
        EqMini(gains, true, Modifier.fillMaxWidth().height(30.dp), color = if (on) Color.White else Bop.Ink)
        Spacer(Modifier.height(10.dp))
        Text(name, style = BopType.BodyStrong, color = if (on) Color.White else Bop.Text, maxLines = 1)
    }
}

/** A tiny drawing of a curve — preset cards and the Settings row. */
@Composable
fun EqMini(gains: List<Float>, on: Boolean, modifier: Modifier = Modifier.size(46.dp, 22.dp), color: Color? = null) {
    Canvas(modifier) {
        val n = gains.size
        val mid = size.height / 2f
        val pts = List(n) { Offset(it * size.width / (n - 1), mid - gains[it] / AudioFx.MAX_DB * mid * 0.9f) }
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 0 until n - 1) {
                val p0 = pts[(i - 1).coerceAtLeast(0)]; val p1 = pts[i]; val p2 = pts[i + 1]; val p3 = pts[(i + 2).coerceAtMost(n - 1)]
                cubicTo(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f, p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f, p2.x, p2.y)
            }
        }
        if (color != null) drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        else drawPath(path, if (on) Brush.horizontalGradient(EqSpectrum) else Brush.horizontalGradient(listOf(Off, Off)), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** A fat pill you drag across; the fill is the value. */
@Composable
private fun PillSlider(title: String, sub: String, value: Float, enabled: Boolean, modifier: Modifier, onChange: (Float) -> Unit) {
    val latest = androidx.compose.runtime.rememberUpdatedState(value)
    Column(modifier.glassLite(RoundedCornerShape(24.dp), alpha = 0.5f, shadow = 12.dp).padding(14.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, style = BopType.BodyStrong, color = Bop.Text, modifier = Modifier.weight(1f))
            Text("${(value * 100).roundToInt()}%", style = BopType.Caption, color = Bop.Text2)
        }
        Text(sub, style = BopType.Caption, color = Bop.Text3, maxLines = 1)
        Spacer(Modifier.height(12.dp))
        val shown by animateFloatAsState(value, spring(stiffness = 900f), label = "pill")
        Box(
            Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(50)).background(Bop.Ink.copy(alpha = 0.07f))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var v = latest.value
                        val startX = down.position.x
                        var moved = false
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!c.pressed) {
                                // A tap jumps straight to where you touched.
                                if (!moved) onChange((startX / size.width).coerceIn(0f, 1f).let { (it * 20).roundToInt() / 20f })
                                break
                            }
                            val d = c.positionChange().x
                            if (abs(c.position.x - startX) > 4f) moved = true
                            if (moved && d != 0f) {
                                c.consume()
                                v = (v + d / size.width).coerceIn(0f, 1f)
                                onChange((v * 100).roundToInt() / 100f)
                            }
                        }
                    }
                },
        ) {
            Box(
                Modifier.fillMaxSize().alpha(if (enabled) 1f else 0.45f)
                    .padding(4.dp)
                    .fillMaxWidth(),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.height / 2f
                    val fw = (r * 2 + (size.width - r * 2) * shown)
                    drawRoundRect(Brush.horizontalGradient(if (enabled) EqSpectrum else listOf(Off, Off), endX = size.width), size = Size(fw, size.height), cornerRadius = CornerRadius(r))
                    drawCircle(Color.White, r * 0.62f, Offset(fw - r, r))
                }
            }
        }
    }
}
