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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.SeekPet
import com.dopdop.bopdop.data.SeekStyle
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType

private const val DEMO_MS = 24_000L

/**
 * Pick how the seek bar looks and who runs along it, with a live preview that plays (and pauses,
 * so you can see the pet nap) right in the sheet.
 */
@Composable
fun ColumnScope.SeekLookSheet() {
    val lib by LibraryStore.state.collectAsState()
    val s = lib.settings
    var playing by remember { mutableStateOf(true) }
    val pos by produceState(DEMO_MS * 3 / 10, playing) {
        if (!playing) return@produceState
        val start = withInfiniteAnimationFrameMillis { it }
        val base = value
        while (true) withInfiniteAnimationFrameMillis { value = (base + (it - start)) % DEMO_MS }
    }
    Text("Seek bar", style = BopType.Title, color = Bop.Text, modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp))
    Text("Long-press the seek bar in the player to come back here.", style = BopType.Caption, color = Bop.Text2, modifier = Modifier.padding(horizontal = 22.dp))
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3A2E52), Color(0xFF1B1824)))).padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { SeekBar(pos, DEMO_MS, playing, onSeek = {}, seed = 7) }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(Color.White).bounceClick(0.9f) { playing = !playing },
            contentAlignment = Alignment.Center,
        ) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause preview" else "Play preview", tint = Bop.Ink, modifier = Modifier.size(20.dp)) }
    }
    Label("Style")
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SeekStyle.entries.forEach { st ->
            Choice(st.label, st == s.seekStyle, Modifier.weight(1f)) { LibraryStore.updateSettings { it.copy(seekStyle = st) } }
        }
    }
    Label("Pet")
    Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SeekPet.entries.forEach { p ->
            Choice(p.label, p == s.seekPet, Modifier.weight(1f), art = if (p == SeekPet.NONE) null else ({ PetIcon(p) })) {
                LibraryStore.updateSettings { it.copy(seekPet = p) }
            }
        }
    }
}

@Composable
private fun Label(text: String) =
    Text(text.uppercase(), style = BopType.Micro, color = Bop.Text3, modifier = Modifier.padding(start = 22.dp, top = 6.dp, bottom = 8.dp))

@Composable
private fun Choice(label: String, on: Boolean, modifier: Modifier, art: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) Bop.Ink else Color.White.copy(alpha = 0.6f), label = "choice")
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(bg).bounceClick(0.94f, onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (art != null) { art(); Spacer(Modifier.height(4.dp)) } else Spacer(Modifier.height(36.dp))
        Text(label, style = BopType.Caption, color = if (on) Color.White else Bop.Text, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** The pet, trotting in place. */
@Composable
private fun PetIcon(pet: SeekPet) {
    val t by produceState(0f) {
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) withInfiniteAnimationFrameMillis { value = (it - start) / 1000f }
    }
    // On a dusk backing, like the player, so a white bunny shows up on a light sheet too.
    Canvas(Modifier.size(42.dp, 32.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2E2640))) {
        drawPet(pet, size.width / 2, size.height - 4.dp.toPx(), t, PetMode.RUN, 1f)
    }
}
