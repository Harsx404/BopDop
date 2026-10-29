package com.dopdop.bopdop.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import com.dopdop.bopdop.ui.festive.ThemedIcon as Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.Mascot
import com.dopdop.bopdop.data.SeekStyle
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType

private const val DEMO_MS = 24_000L

/**
 * Pick how the seek bar looks and who runs along it, with a live preview that plays (and pauses,
 * so you can see the mascot nap) right in the sheet.
 */
@Composable
fun ColumnScope.SeekLookSheet() {
    val lib by LibraryStore.state.collectAsState()
    val s = lib.settings
    var playing by remember { mutableStateOf(true) }
    var pos by remember { mutableLongStateOf(DEMO_MS * 3 / 10) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        while (true) { kotlinx.coroutines.delay(100); pos = (pos + 100) % DEMO_MS }
    }
    Text("Seek bar", style = BopType.Title, color = Bop.Text, modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp))
    Text("Seasonal follows the festival. Pick any character to keep it all year.", style = BopType.Caption, color = Bop.Text2, modifier = Modifier.padding(horizontal = 22.dp))
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3A2E52), Color(0xFF1B1824)))).padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { SeekBar(pos, DEMO_MS, playing, onSeek = { pos = it.coerceIn(0, DEMO_MS) }, seed = 7) }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(if (Bop.pixel) Bop.MediaAccent else Color.White).bounceClick(0.9f) { playing = !playing },
            contentAlignment = Alignment.Center,
        ) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause preview" else "Play preview", tint = Bop.Graphite, modifier = Modifier.size(20.dp)) }
    }
    Label("Style")
    // Three to a row, so every style keeps a comfortable target.
    androidx.compose.foundation.layout.Column(Modifier.padding(horizontal = 16.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SeekStyle.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { st ->
                    Choice(st.label, st == s.seekStyle, Modifier.weight(1f)) { LibraryStore.updateSettings { it.copy(seekStyle = st) } }
                }
            }
        }
    }
    Label("Mascot")
    LazyRow(
        Modifier.fillMaxWidth().selectableGroup().padding(bottom = 12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(Mascot.entries, key = { it.name }) { m ->
            Choice(m.label, m == s.mascot, Modifier.width(80.dp), art = if (m == Mascot.NONE) null else ({ MascotIcon(m) }), keepArtSpace = true) {
                LibraryStore.updateSettings { it.copy(mascot = m) }
            }
        }
    }
}

@Composable
private fun Label(text: String) =
    Text(text.uppercase(), style = BopType.Micro, color = Bop.Text3, modifier = Modifier.padding(start = 22.dp, top = 6.dp, bottom = 8.dp))

@Composable
private fun Choice(label: String, on: Boolean, modifier: Modifier, art: (@Composable () -> Unit)? = null, keepArtSpace: Boolean = false, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) Bop.Ink else Bop.Veil.copy(alpha = 0.6f), label = "choice")
    Column(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).background(bg)
            .semantics { selected = on; role = Role.RadioButton }.bounceClick(0.94f, onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (art != null) { art(); Spacer(Modifier.height(4.dp)) } else if (keepArtSpace) Spacer(Modifier.height(44.dp))
        Text(label, style = BopType.Caption, color = if (on) Bop.OnInk else Bop.Text, textAlign = TextAlign.Center, maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/** The mascot, walking (or hopping) in place. */
@Composable
private fun MascotIcon(mascot: Mascot) {
    val frames = rememberMascotFrames(mascot)
    val t by com.dopdop.bopdop.ui.festive.rememberFestiveTime(fps = 12)
    // On a dusk backing, like the player.
    Canvas(Modifier.size(56.dp, 44.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2E2640))) {
        // Wide mascots (the bike) shrink to fit the tile.
        val first = frames.walk[0]
        drawMascot(frames, size.width / 2, size.height - 4.dp.toPx(), t, MascotMode.WALK, 1f, heightDp = if (frames.gridCell > 0) 32f else minOf(28f, 44f * first.height / first.width))
    }
}
