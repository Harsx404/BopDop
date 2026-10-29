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
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Podcasts
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
import com.dopdop.bopdop.data.model.Shelf
import com.dopdop.bopdop.ui.components.ShelfSkeleton
import com.dopdop.bopdop.data.together.Together
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.ui.LocalApp
import com.dopdop.bopdop.ui.components.ErrorState
import com.dopdop.bopdop.ui.components.Load
import com.dopdop.bopdop.ui.components.SectionHeader
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.components.riseIn
import com.dopdop.bopdop.ui.components.rememberLoad
import com.dopdop.bopdop.ui.components.shimmer
import com.dopdop.bopdop.ui.glass.glassLite
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType

/**
 * Discovery tab — music you haven't found yet: daily mixes made from your taste, what's charting,
 * what's just out, moods & genres, and listening with friends. Your own music lives in Library.
 */
@Composable
fun ExploreScreen() {
    val app = LocalApp.current
    val mixes by DailyMixes.mixes.collectAsState()
    val room by Together.state.collectAsState()
    LaunchedEffect(Unit) { DailyMixes.refresh() }
    val moods = rememberLoad("moods", 6 * 3600_000L) { MusicRepository.moods() }
    val charts = rememberLoad("charts", 3 * 3600_000L) { MusicRepository.charts() }
    val fresh = rememberLoad("explore", 3 * 3600_000L) { MusicRepository.explore() }

    LargeTitleScaffold(title = "Explore", eyebrow = "Find something new") {
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
        item(key = "together") {
            ActionTile(
                "Listen Together", if (room.live) "In room ${room.code}" else "Same song, same second, with friends", Icons.Rounded.Groups,
                listOf(Color(0xFFB9A6E8), Color(0xFF7B63C2)), Modifier.padding(horizontal = 20.dp, vertical = 14.dp).fillMaxWidth(),
            ) { app.nav.navigate("together?code=") }
        }
        item(key = "podcasts") {
            ActionTile(
                "Podcasts", "Shows and episodes from YouTube Music and JioSaavn", Icons.Rounded.Podcasts,
                listOf(Color(0xFFFFB86B), Color(0xFFE0592B)), Modifier.padding(horizontal = 20.dp).padding(bottom = 14.dp).fillMaxWidth(),
            ) { app.nav.navigate(com.dopdop.bopdop.ui.Routes.PODCASTS) }
        }
        // What's charting, then what's new: a couple of rows each, the rest a tap away.
        discoveryShelves(charts.state, "charts", 2)
        discoveryShelves(fresh.state, "fresh", 2) { !it.title.contains("mood", ignoreCase = true) && !it.title.contains("genre", ignoreCase = true) }
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
                    Row(Modifier.riseIn(r).padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rows[r].forEach { (title, params) -> MoodTile(title, params, wide = false, modifier = Modifier.weight(1f)) { app.openMood(title, params) } }
                        if (rows[r].size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.discoveryShelves(
    state: Load<List<Shelf>>, key: String, count: Int, keep: (Shelf) -> Boolean = { true },
) {
    when (state) {
        is Load.Loading -> item(key = "${key}Loading") { ShelfSkeleton() }
        is Load.Failed -> {}
        is Load.Ready -> state.value.filter { (it.tracks.size + it.cards.size) >= 3 && keep(it) }.take(count)
            .forEachIndexed { i, sh -> shelf(sh, "$key$i${sh.title}") }
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
