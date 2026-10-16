package com.dopdop.bopdop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dopdop.bopdop.data.DailyMix
import com.dopdop.bopdop.data.DailyMixes
import com.dopdop.bopdop.data.MusicRepository
import com.dopdop.bopdop.data.together.Together
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.ui.LocalApp
import com.dopdop.bopdop.ui.Routes
import com.dopdop.bopdop.ui.Sheet
import com.dopdop.bopdop.ui.components.ErrorState
import com.dopdop.bopdop.ui.components.Load
import com.dopdop.bopdop.ui.components.SectionHeader
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.components.rememberLoad
import com.dopdop.bopdop.ui.components.shimmer
import com.dopdop.bopdop.ui.glass.glassLite
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType

/** Discovery tab: your daily mixes, ways to listen with friends or bring music in, and moods & genres. */
@Composable
fun ExploreScreen() {
    val app = LocalApp.current
    val mixes by DailyMixes.mixes.collectAsState()
    val room by Together.state.collectAsState()
    LaunchedEffect(Unit) { DailyMixes.refresh() }
    val moods = rememberLoad("moods", 6 * 3600_000L) { MusicRepository.moods() }

    LargeTitleScaffold(title = "Explore", eyebrow = "Mixes, moods & friends") {
        item(key = "mixes") {
            Column {
                SectionHeader("Your daily mixes")
                if (mixes.isEmpty()) {
                    Text("Play a few songs and your mixes show up here, fresh every day.", style = BopType.Body, color = Bop.Text2,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                } else {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(mixes, key = { it.id }) { m -> MixCard(m) { app.openLocal("mix:${m.id}") } }
                    }
                }
            }
        }
        item(key = "actions") {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionTile(
                        "Listen Together", if (room.live) "In room ${room.code}" else "Same song, same second", Icons.Rounded.Groups,
                        listOf(Color(0xFFB9A6E8), Color(0xFF7B63C2)), Modifier.weight(1f),
                    ) { app.nav.navigate("together?code=") }
                    ActionTile(
                        "Import playlist", "From Spotify or YouTube", Icons.Rounded.PlaylistAdd,
                        listOf(Color(0xFF8FD1B0), Color(0xFF3E9A72)), Modifier.weight(1f),
                    ) { app.sheet = Sheet.ImportPlaylist }
                }
                ActionTile(
                    "Your Replay story", "Your listening, as cards you can share", Icons.Rounded.AutoAwesome,
                    listOf(Color(0xFFF2B37E), Color(0xFFD9627A)), Modifier.fillMaxWidth(),
                ) { app.nav.navigate(Routes.WRAPPED) }
            }
        }
        item(key = "moodsHeader") { SectionHeader("Moods & genres") }
        when (val s = moods.state) {
            is Load.Loading -> item(key = "moodsLoading") {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(3) { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { repeat(2) { Box(Modifier.weight(1f).height(110.dp).shimmer(18.dp)) } } }
                }
            }
            is Load.Failed -> item(key = "moodsError") { ErrorState(s.message, onRetry = moods.retry) }
            is Load.Ready -> {
                val rows = s.value.chunked(2)
                items(rows.size, key = { "em$it" }) { r ->
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rows[r].forEach { (title, params) -> MoodTile(title, params, wide = false, modifier = Modifier.weight(1f)) { app.openMood(title, params) } }
                        if (rows[r].size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun MixCard(m: DailyMix, onClick: () -> Unit) {
    Column(Modifier.width(156.dp).bounceClick(onLongClick = { PlayerConnection.play(m.tracks, 0, m.title, shuffle = true) }, onClick = onClick)) {
        Box {
            Collage(m.artworks, Modifier.size(156.dp))
            Text(m.title.removePrefix("Daily "), style = BopType.BodyStrong, color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(m.title, style = BopType.BodyStrong, color = Bop.Text, maxLines = 1)
        Text(m.subtitle, style = BopType.Caption, color = Bop.Text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ActionTile(title: String, sub: String, icon: ImageVector, colors: List<Color>, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier.height(86.dp).bounceClick(0.97f, onClick = onClick).glassLite(shape, alpha = 0.6f, shadow = 12.dp).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradientArt(Modifier.size(64.dp), icon, colors)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = BopType.BodyStrong, color = Bop.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = BopType.Caption, color = Bop.Text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
