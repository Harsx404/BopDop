package com.dopdop.bopdop.ui.player

import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.History
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.MusicRepository
import com.dopdop.bopdop.data.model.Lyrics
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.playback.RepeatMode
import com.dopdop.bopdop.ui.AppState
import com.dopdop.bopdop.ui.components.Artwork
import com.dopdop.bopdop.ui.components.EqualizerBars
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.components.shimmer
import com.dopdop.bopdop.ui.glass.ArtPalette
import com.dopdop.bopdop.ui.glass.glassLite
import com.dopdop.bopdop.ui.theme.BopType
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

// --- Queue ---------------------------------------------------------------------------

@Composable
fun QueueView(app: AppState, modifier: Modifier = Modifier) {
    val queue by PlayerConnection.queue.collectAsState()
    val index by PlayerConnection.index.collectAsState()
    val shuffle by PlayerConnection.shuffle.collectAsState()
    val repeat by PlayerConnection.repeat.collectAsState()
    val lib by LibraryStore.state.collectAsState()
    val upNext = queue.drop(index + 1)
    val rowH = 64.dp
    val rowPx = with(LocalDensity.current) { rowH.toPx() }
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    var played by remember { mutableStateOf(false) }
    val target = if (dragFrom >= 0) (dragFrom + (dragDy / rowPx).roundToInt()).coerceIn(0, upNext.size - 1) else -1

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (played) "Played" else "Up next", style = BopType.Title, color = Color.White)
                Text(
                    when {
                        played -> if (index == 1) "1 song earlier in this queue" else "$index songs earlier in this queue"
                        lib.settings.autoplay -> "Autoplay keeps it going"
                        else -> "${upNext.size} songs"
                    },
                    style = BopType.Caption, color = Color.White.copy(alpha = 0.55f),
                )
            }
            QueueChip(Icons.Rounded.History, played) { played = !played }
            Spacer(Modifier.width(6.dp))
            QueueChip(Icons.Rounded.Shuffle, shuffle) { PlayerConnection.toggleShuffle() }
            Spacer(Modifier.width(6.dp))
            QueueChip(if (repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, repeat != RepeatMode.OFF) { PlayerConnection.cycleRepeat() }
            Spacer(Modifier.width(6.dp))
            QueueChip(Icons.Rounded.AllInclusive, lib.settings.autoplay) { LibraryStore.updateSettings { it.copy(autoplay = !it.autoplay) } }
        }
        if (played) { PlayedList(queue, index); return@Column }
        if (upNext.isEmpty()) {
            Text("Nothing queued. Add songs with “Play next” or let Autoplay surprise you.", style = BopType.Body, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(24.dp))
            return@Column
        }
        Text(
            "Clear", style = BopType.BodyStrong, color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.End).padding(end = 22.dp).bounceClick { PlayerConnection.clearUpNext() },
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(upNext.size, key = { upNext[it].id + "#" + it }) { i ->
                val t = upNext[i]
                val isDragged = i == dragFrom
                // Rows between source and target make room for the dragged row.
                val shift = when {
                    dragFrom < 0 || isDragged -> 0f
                    dragFrom < target && i in (dragFrom + 1)..target -> -rowPx
                    dragFrom > target && i in target until dragFrom -> rowPx
                    else -> 0f
                }
                val animShift by animateFloatAsState(shift, spring(dampingRatio = 0.8f, stiffness = 600f), label = "shift")
                Row(
                    Modifier
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragged) dragDy else animShift
                            scaleX = if (isDragged) 1.03f else 1f; scaleY = scaleX
                        }
                        .fillMaxWidth().height(rowH)
                        .padding(horizontal = 12.dp)
                        .then(if (isDragged) Modifier.glassLite(RoundedCornerShape(16.dp), alpha = 0.16f) else Modifier)
                        .bounceClick(0.99f) { PlayerConnection.skipTo(index + 1 + i) }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(t.artwork, Modifier.size(46.dp), corner = 8.dp, size = 226)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = BopType.BodyStrong, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist, style = BopType.Caption, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(Modifier.size(36.dp).clip(CircleShape).bounceClick(0.85f) { PlayerConnection.removeAt(index + 1 + i) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, "Remove", tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(18.dp))
                    }
                    Box(
                        Modifier.size(40.dp).pointerInput(upNext.size, i) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragFrom = i; dragDy = 0f },
                                onDragEnd = {
                                    if (target >= 0 && target != dragFrom) PlayerConnection.move(index + 1 + dragFrom, index + 1 + target)
                                    dragFrom = -1; dragDy = 0f
                                },
                                onDragCancel = { dragFrom = -1; dragDy = 0f },
                            ) { change, d -> change.consume(); dragDy += d.y }
                        },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.DragHandle, "Reorder", tint = Color.White.copy(alpha = 0.6f)) }
                }
            }
        }
    }
}

/** What already played in this queue, latest first; tap one to go back to it. */
@Composable
private fun PlayedList(queue: List<com.dopdop.bopdop.data.model.Track>, index: Int) {
    val before = queue.take(index)
    if (before.isEmpty()) {
        Text("Nothing has played yet in this queue.", style = BopType.Body, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(24.dp))
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(before.size, key = { "p$it:" + before[before.size - 1 - it].id }) { k ->
            val i = before.size - 1 - k
            val t = before[i]
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp)
                    .bounceClick(0.99f) { PlayerConnection.skipTo(i) }.padding(horizontal = 10.dp).graphicsLayer { alpha = 0.8f },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(t.artwork, Modifier.size(46.dp), corner = 8.dp, size = 226)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = BopType.BodyStrong, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, style = BopType.Caption, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Rounded.Replay, "Play again", tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun QueueChip(icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).bounceClick(0.85f, onClick = onClick)
            .background(if (on) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (on) Color.Black else Color.White.copy(alpha = 0.8f), modifier = Modifier.size(19.dp)) }
}
