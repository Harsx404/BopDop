package com.dopdop.bopdop.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explicit
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.dopdop.bopdop.data.model.Card
import com.dopdop.bopdop.data.model.CardKind
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.model.formatDuration
import com.dopdop.bopdop.data.ytm.YtMusic
import com.dopdop.bopdop.ui.glass.glassLite
import com.dopdop.bopdop.ui.glass.softShadow
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType
import kotlinx.coroutines.launch

// --- Interaction ---------------------------------------------------------------------

/** Springy press feedback (scale + dim) with a light haptic tick on long-press. */
fun Modifier.bounceClick(
    scaleDown: Float = 0.96f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) scaleDown else 1f, spring(dampingRatio = 0.55f, stiffness = 900f), label = "press")
    val haptics = LocalHapticFeedback.current
    this
        .graphicsLayer { scaleX = s; scaleY = s; alpha = if (pressed) 0.92f else 1f }
        .combinedClickable(
            interactionSource = source, indication = null,
            onLongClick = onLongClick?.let { { haptics.performHapticFeedback(HapticFeedbackType.LongPress); it() } },
            onClick = onClick,
        )
}

// --- Artwork -------------------------------------------------------------------------

@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
    size: Int = 544,
    elevation: Dp = 0.dp,
    circle: Boolean = false,
) {
    val shape = if (circle) CircleShape else RoundedCornerShape(corner)
    Box(
        modifier
            .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape, ambientColor = Color.Black, spotColor = Color.Black) else Modifier)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFFEDEFF2), Color(0xFFD3D8DF)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.MusicNote, null, tint = Bop.Text.copy(alpha = 0.14f), modifier = Modifier.fillMaxSize(0.38f))
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current).data(YtMusic.resize(url, size)).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// --- Rows & cards --------------------------------------------------------------------

@Composable
fun TrackRow(
    track: Track,
    modifier: Modifier = Modifier,
    index: Int? = null,
    playing: Boolean = false,
    showArt: Boolean = true,
    onMore: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .bounceClick(0.985f, onLongClick = onMore, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Box(Modifier.width(30.dp), contentAlignment = Alignment.CenterStart) {
                if (playing) EqualizerBars(Modifier.size(14.dp), color = Bop.Text)
                else Text("$index", style = BopType.Body, color = Bop.Text3)
            }
        }
        if (showArt) {
            Box {
                Artwork(track.artwork, Modifier.size(52.dp), corner = 14.dp, size = 226)
                if (playing && index == null) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.38f)), contentAlignment = Alignment.Center) {
                        EqualizerBars(Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                track.title, style = BopType.BodyStrong,
                color = if (playing) Bop.Text else Bop.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.explicit) {
                    Icon(Icons.Rounded.Explicit, null, tint = Bop.Text3, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                if (track.bitrate >= 320) {
                    QualityDot()
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    listOf(track.artist, if (index != null) formatDuration(track.durationSec) else track.album).filter { it.isNotBlank() }.joinToString(" · "),
                    style = BopType.Caption, color = Bop.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onMore != null) {
            Box(Modifier.size(40.dp).clip(CircleShape).bounceClick(onClick = onMore), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MoreHoriz, "More", tint = Bop.Text2)
            }
        }
    }
}

@Composable
fun QualityDot() {
    Box(
        Modifier.clip(RoundedCornerShape(3.dp)).background(Bop.Text.copy(alpha = 0.16f)).padding(horizontal = 4.dp, vertical = 1.dp),
    ) { Text("HQ", style = BopType.Micro, color = Bop.Text2) }
}

@Composable
fun CardTile(
    card: Card,
    modifier: Modifier = Modifier,
    width: Dp = 152.dp,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val circle = card.kind == CardKind.ARTIST
    Column(modifier.width(width).bounceClick(onLongClick = onLongClick, onClick = onClick)) {
        Artwork(card.artwork, Modifier.size(width), corner = 24.dp, circle = circle, elevation = 12.dp)
        Spacer(Modifier.height(9.dp))
        Text(
            card.title, style = BopType.BodyStrong, color = Bop.Text, maxLines = if (circle) 1 else 2, overflow = TextOverflow.Ellipsis,
            modifier = if (circle) Modifier.align(Alignment.CenterHorizontally) else Modifier,
        )
        if (card.subtitle.isNotBlank() && !circle) {
            Text(card.subtitle, style = BopType.Caption, color = Bop.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun TrackTile(track: Track, playing: Boolean, modifier: Modifier = Modifier, width: Dp = 152.dp, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Column(modifier.width(width).bounceClick(onLongClick = onLongClick, onClick = onClick)) {
        Box {
            Artwork(track.artwork, Modifier.size(width), corner = 24.dp, elevation = 12.dp)
            if (playing) Box(
                Modifier.padding(10.dp).size(32.dp).glassLite(CircleShape, tint = Color.Black, alpha = 0.32f).align(Alignment.BottomEnd),
                contentAlignment = Alignment.Center,
            ) { EqualizerBars(Modifier.size(14.dp)) }
        }
        Spacer(Modifier.height(9.dp))
        Text(track.title, style = BopType.BodyStrong, color = Bop.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artist, style = BopType.Caption, color = Bop.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String = "", modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 26.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            if (subtitle.isNotBlank()) Text(subtitle.uppercase(), style = BopType.Micro, color = Bop.Text3)
            Text(title, style = BopType.Title, color = Bop.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (action != null && onAction != null) {
            Text(
                action, style = BopType.BodyStrong, color = Bop.Text2,
                modifier = Modifier.clip(RoundedCornerShape(50)).bounceClick(onClick = onAction).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

// --- Motion bits ---------------------------------------------------------------------

@Composable
fun EqualizerBars(modifier: Modifier = Modifier, color: Color = Color.White, animate: Boolean = true) {
    val t = rememberInfiniteTransition(label = "eq")
    val bars = listOf(520, 380, 610).mapIndexed { i, d ->
        t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(d, easing = LinearEasing), RepeatMode.Reverse), label = "b$i")
    }
    Canvas(modifier) {
        val w = size.width / 5f
        bars.forEachIndexed { i, v ->
            val h = size.height * (if (animate) v.value else 0.35f)
            drawRoundRect(color, Offset(i * 2 * w, size.height - h), androidx.compose.ui.geometry.Size(w, h), CornerRadius(w / 2))
        }
    }
}

/** Play ▸ ⇄ pause ❚❚ morph: the triangle splits into two bars. */
@Composable
fun PlayPauseGlyph(playing: Boolean, modifier: Modifier = Modifier, color: Color = Color.White) {
    val p by animateFloatAsState(if (playing) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "pp")
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val path = Path()
        // Left half: play → left bar
        val l = { x0: Float, x1: Float -> androidx.compose.ui.util.lerp(x0, x1, p) }
        val top = h * 0.14f; val bot = h * 0.86f
        path.moveTo(l(w * 0.22f, w * 0.20f), l(top, top))
        path.lineTo(l(w * 0.56f, w * 0.40f), l(h * 0.32f, top))
        path.lineTo(l(w * 0.56f, w * 0.40f), l(h * 0.68f, bot))
        path.lineTo(l(w * 0.22f, w * 0.20f), l(bot, bot))
        path.close()
        path.moveTo(l(w * 0.56f, w * 0.60f), l(h * 0.32f, top))
        path.lineTo(l(w * 0.84f, w * 0.80f), l(h * 0.5f, top))
        path.lineTo(l(w * 0.84f, w * 0.80f), l(h * 0.5f, bot))
        path.lineTo(l(w * 0.56f, w * 0.60f), l(h * 0.68f, bot))
        path.close()
        drawPath(path, color)
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = Bop.Text,
    active: Boolean = false,
    onMedia: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier.size(size).bounceClick(0.9f, onClick = onClick)
            .then(if (onMedia) Modifier.glassLite(CircleShape, alpha = if (active) 0.42f else 0.2f)
            else Modifier.glassLite(CircleShape, tint = if (active) Bop.Ink else Color.White, alpha = if (active) 0.92f else 0.6f, shadow = 10.dp)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, tint = if (active && !onMedia) Color.White else if (onMedia) Color.White else tint, modifier = Modifier.size(size * 0.5f)) }
}

@Composable
fun PillButton(text: String, icon: ImageVector?, modifier: Modifier = Modifier, filled: Boolean = true, onMedia: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .height(48.dp)
            .bounceClick(onClick = onClick)
            .then(
                when {
                    onMedia && filled -> Modifier.softShadow(shape, 16.dp, alpha = 0.22f).clip(shape).background(Color.White)
                    onMedia -> Modifier.glassLite(shape, alpha = 0.2f)
                    filled -> Modifier.softShadow(shape, 14.dp, alpha = 0.25f).clip(shape).background(Bop.Ink)
                    else -> Modifier.glassLite(shape, alpha = 0.62f, shadow = 12.dp)
                },
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val c = when { onMedia && filled -> Bop.Ink; onMedia -> Color.White; filled -> Color.White; else -> Bop.Text }
        if (icon != null) { Icon(icon, null, tint = c, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = BopType.BodyStrong, color = c, maxLines = 1, softWrap = false)
    }
}

@Composable
fun MarqueeText(text: String, style: androidx.compose.ui.text.TextStyle, color: Color, modifier: Modifier = Modifier) {
    Text(text, style = style, color = color, maxLines = 1, modifier = modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2200, repeatDelayMillis = 2600, velocity = 34.dp))
}

// --- Skeletons -----------------------------------------------------------------------

fun Modifier.shimmer(corner: Dp = 12.dp): Modifier = composed {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x")
    this.clip(RoundedCornerShape(corner)).drawBehind {
        drawRect(Color.Black.copy(alpha = 0.05f))
        drawRect(Brush.linearGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
            start = Offset(size.width * x, 0f), end = Offset(size.width * (x + 0.6f), size.height),
        ))
    }
}

@Composable
fun ShelfSkeleton() {
    Column(Modifier.padding(top = 26.dp)) {
        Box(Modifier.padding(start = 20.dp).size(180.dp, 22.dp).shimmer(6.dp))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.padding(start = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(3) {
                Column {
                    Box(Modifier.size(152.dp).shimmer(14.dp))
                    Spacer(Modifier.height(9.dp))
                    Box(Modifier.size(110.dp, 14.dp).shimmer(4.dp))
                }
            }
        }
    }
}

@Composable
fun ErrorState(message: String, modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Column(modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Couldn’t load this", style = BopType.Headline, color = Bop.Text)
        Spacer(Modifier.height(6.dp))
        Text(message, style = BopType.Caption, color = Bop.Text3, maxLines = 3)
        Spacer(Modifier.height(18.dp))
        PillButton("Try again", null, filled = false, onClick = onRetry)
    }
}

// --- Async loading -------------------------------------------------------------------

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

class Loader<T>(val state: Load<T>, val retry: () -> Unit)

/** Results survive tab switches / back navigation so revisits render instantly. */
object ScreenCache {
    private val map = object : LinkedHashMap<String, Pair<Long, Any?>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, Any?>>?) = size > 40
    }
    @Synchronized fun get(key: String, maxAgeMs: Long): Any? = map[key]?.takeIf { System.currentTimeMillis() - it.first < maxAgeMs }?.second
    @Synchronized fun put(key: String, v: Any?) { map[key] = System.currentTimeMillis() to v }
}

@Suppress("UNCHECKED_CAST")
@Composable
fun <T> rememberLoad(cacheKey: String, maxAgeMs: Long = 15 * 60_000L, block: suspend () -> T): Loader<T> {
    var attempt by remember(cacheKey) { mutableIntStateOf(0) }
    var state by remember(cacheKey) {
        mutableStateOf<Load<T>>(ScreenCache.get(cacheKey, maxAgeMs)?.let { Load.Ready(it as T) } ?: Load.Loading)
    }
    LaunchedEffect(cacheKey, attempt) {
        if (state is Load.Ready && attempt == 0) return@LaunchedEffect
        if (state !is Load.Ready) state = Load.Loading
        state = try {
            Load.Ready(block()).also { ScreenCache.put(cacheKey, it.value) }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            if (state is Load.Ready) state else Load.Failed(e.message ?: "Network error")
        }
    }
    return Loader(state) { attempt++ }
}

fun Color.mix(other: Color, f: Float) = lerp(this, other, f)
