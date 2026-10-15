package com.dopdop.bopdop.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.MusicRepository
import com.dopdop.bopdop.data.PlayStats
import com.dopdop.bopdop.data.Replay
import com.dopdop.bopdop.data.ReplayRange
import com.dopdop.bopdop.data.ReplaySummary
import com.dopdop.bopdop.data.model.Shelf
import com.dopdop.bopdop.data.share.SharePayload
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.ui.LocalApp
import com.dopdop.bopdop.ui.Sheet
import com.dopdop.bopdop.ui.ShareRequest
import com.dopdop.bopdop.ui.components.Artwork
import com.dopdop.bopdop.ui.components.GlassChip
import com.dopdop.bopdop.ui.components.GlassIconButton
import com.dopdop.bopdop.ui.components.Load
import com.dopdop.bopdop.ui.components.PillButton
import com.dopdop.bopdop.ui.components.SectionHeader
import com.dopdop.bopdop.ui.components.TrackRow
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.components.rememberLoad
import com.dopdop.bopdop.ui.components.shimmer
import com.dopdop.bopdop.ui.glass.glassEdges
import com.dopdop.bopdop.ui.glass.glassLite
import com.dopdop.bopdop.ui.glass.rememberArtPalette
import com.dopdop.bopdop.ui.glass.softShadow
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType
import java.time.format.TextStyle
import java.util.Locale

/**
 * Replay — your listening, Spotify-Wrapped style but always on: top songs and
 * artists for a period, a Replay mix to play / keep / share, your 30-day
 * listening trend, what's rising in your rotation, and what's trending now.
 */
@Composable
fun ReplayScreen() {
    val app = LocalApp.current
    val stats by PlayStats.state.collectAsState()
    var range by rememberSaveable { mutableStateOf(ReplayRange.MONTH) }
    val r = remember(stats, range) { Replay.summarize(stats, range) }
    val current by PlayerConnection.current.collectAsState()
    val charts = rememberLoad("charts", 60 * 60_000L) { MusicRepository.charts() }

    LargeTitleScaffold(title = "Replay", eyebrow = "Your listening", showBack = true) {
        item(key = "ranges") {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ReplayRange.entries.toList()) { g -> GlassChip(g.label, g == range) { range = g } }
            }
        }
        if (r.isEmpty) item(key = "empty") { EmptyReplay() }
        else {
            item(key = "mix") { ReplayMix(r, range) }
            item(key = "tiles") { StatTiles(r) }
        }
        item(key = "trend") { TrendChart(r) }
        if (r.rising.isNotEmpty()) {
            item(key = "risingH") { SectionHeader("On the rise", "Your trend right now") }
            items(r.rising.size, key = { "rise" + r.rising[it].track.id }) { i ->
                val s = r.rising[i]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TrackRow(s.track, Modifier.weight(1f), playing = current?.id == s.track.id, onMore = { app.sheet = Sheet.TrackOptions(s.track) }) {
                        PlayerConnection.play(r.rising.map { it.track }, i, "On the rise")
                    }
                    Delta(s.recent, s.before)
                }
            }
        }
        if (r.topTracks.isNotEmpty()) {
            item(key = "topH") { SectionHeader("Top songs", range.label) }
            val top = r.topTracks.take(20)
            items(top.size, key = { "top" + top[it].track.id }) { i ->
                Row(Modifier.padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", style = BopType.Headline, color = if (i < 3) Bop.Text else Bop.Text3, modifier = Modifier.width(26.dp))
                    TrackRow(top[i].track, Modifier.weight(1f), playing = current?.id == top[i].track.id, onMore = { app.sheet = Sheet.TrackOptions(top[i].track) }) {
                        PlayerConnection.play(r.topTracks.map { it.track }, i, "Replay · ${range.label}")
                    }
                    Text(plays(top[i].plays), style = BopType.Caption, color = Bop.Text3, modifier = Modifier.padding(end = 20.dp))
                }
            }
        }
        if (r.topArtists.isNotEmpty()) item(key = "artists") {
            Column {
                SectionHeader("Top artists", range.label)
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(r.topArtists.size) { i ->
                        val a = r.topArtists[i]
                        Column(
                            Modifier.width(100.dp).bounceClick { a.artistId?.let { app.openArtist(it, a.name) } },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box {
                                Artwork(a.artwork, Modifier.size(100.dp), circle = true, size = 300, elevation = 10.dp)
                                Text(
                                    "${i + 1}", style = BopType.Caption.copy(fontWeight = FontWeight(700)), color = Color.White,
                                    modifier = Modifier.align(Alignment.BottomStart).glassLite(RoundedCornerShape(50), tint = Bop.Accent, alpha = 1f).padding(horizontal = 9.dp, vertical = 3.dp),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(a.name, style = BopType.Caption.copy(fontWeight = FontWeight(600)), color = Bop.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(plays(a.plays), style = BopType.Caption, color = Bop.Text3)
                        }
                    }
                }
            }
        }
        // Global trend right now.
        item(key = "trendingH") { SectionHeader("Trending now", "Charts · YouTube Music") }
        when (val s = charts.state) {
            is Load.Loading -> item(key = "trendingL") { Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(220.dp).shimmer(24.dp)) }
            is Load.Failed -> item(key = "trendingF") {
                Text("Charts aren’t reachable right now.", style = BopType.Body, color = Bop.Text2, modifier = Modifier.padding(horizontal = 20.dp))
            }
            is Load.Ready -> trending(s.value, current?.id)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun plays(n: Int) = if (n == 1) "1 play" else "$n plays"

private fun androidx.compose.foundation.lazy.LazyListScope.trending(shelves: List<Shelf>, currentId: String?) {
    val songs = shelves.firstOrNull { it.tracks.size >= 5 }
    if (songs != null) {
        val top = songs.tracks.take(10)
        item(key = "trendTitle") { Text(songs.title, style = BopType.BodyStrong, color = Bop.Text2, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
        items(top.size, key = { "trend" + top[it].id }) { i ->
            val app = LocalApp.current
            Row(Modifier.padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", style = BopType.Headline, color = if (i < 3) Bop.Text else Bop.Text3, modifier = Modifier.width(26.dp))
                TrackRow(top[i], Modifier.weight(1f), playing = currentId == top[i].id, onMore = { app.sheet = Sheet.TrackOptions(top[i]) }) {
                    PlayerConnection.play(songs.tracks, i, songs.title)
                }
            }
        }
    }
    shelves.filter { it !== songs && it.cards.isNotEmpty() }.take(2).forEachIndexed { i, sh -> shelf(sh, "trendShelf$i") }
}

@Composable
private fun Delta(recent: Int, before: Int) {
    Row(Modifier.padding(end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.ArrowDropUp, null, tint = Bop.Green, modifier = Modifier.size(22.dp))
        Text(if (before == 0) "new" else "+${recent - before}", style = BopType.Caption.copy(fontWeight = FontWeight(650)), color = Bop.Text)
    }
}

@Composable
private fun EmptyReplay() {
    Column(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().glassLite(RoundedCornerShape(28.dp), alpha = 0.12f, shadow = 14.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.Insights, null, tint = Bop.Text, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(10.dp))
        Text("Your Replay builds as you listen", style = BopType.Headline, color = Bop.Text)
        Spacer(Modifier.height(4.dp))
        Text(
            "A song counts once you’ve listened for 30 seconds. Come back after a few sessions for your top songs, artists and trends.",
            style = BopType.Caption, color = Bop.Text2, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** The Replay mix: your top songs for the period, playable, savable, shareable. */
@Composable
private fun ReplayMix(r: ReplaySummary, range: ReplayRange) {
    val app = LocalApp.current
    val tracks = r.topTracks.map { it.track }
    val arts = tracks.map { it.artwork }.distinct().take(4)
    val palette = rememberArtPalette(arts.firstOrNull())
    val shape = RoundedCornerShape(34.dp)
    val name = "Replay · ${range.label}"
    Box(
        Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().height(250.dp)
            .softShadow(shape, 22.dp, alpha = 0.26f, dy = 8.dp).clip(shape)
            .background(Brush.linearGradient(listOf(palette.aurora[1], palette.aurora[2], palette.aurora[3])))
            .glassEdges(shape, 0.9f),
    ) {
        Collage(arts, Modifier.size(150.dp).align(Alignment.TopEnd).padding(top = 18.dp, end = 18.dp), 22.dp)
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Text("REPLAY MIX", style = BopType.Micro, color = Color.White.copy(alpha = 0.75f))
            Text("Your top ${tracks.size.coerceAtMost(50)}", style = BopType.Display, color = Color.White)
            Text(
                tracks.take(3).joinToString(", ") { it.artist.split(",").first() }.ifEmpty { range.label },
                style = BopType.Caption, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.6f),
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton("Play", Icons.Rounded.PlayArrow, Modifier.width(108.dp), onMedia = true) { PlayerConnection.play(tracks, 0, name) }
                GlassIconButton(Icons.Rounded.Save, "Save as playlist", size = 46.dp, onMedia = true) {
                    LibraryStore.createPlaylist(name, tracks); app.toast("Saved “$name” to your Library")
                }
                GlassIconButton(Icons.Rounded.QrCode2, "Share code", size = 46.dp, onMedia = true) {
                    app.shareQr(ShareRequest(SharePayload.Playlist("My $name", tracks.map { it.id }), "My $name", "Top ${tracks.size} on BopDop", arts.firstOrNull().orEmpty()))
                }
            }
        }
    }
}

/** Control-Center-style glass tiles: the headline numbers. */
@Composable
private fun StatTiles(r: ReplaySummary) {
    Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile("${r.minutes}", "minutes", Modifier.weight(1f))
        Tile("${r.plays}", "plays", Modifier.weight(1f))
        Tile(r.busiestHour?.let { hour(it) } ?: "—", "peak hour", Modifier.weight(1f))
    }
}

private fun hour(h: Int) = when { h == 0 -> "12am"; h < 12 -> "${h}am"; h == 12 -> "12pm"; else -> "${h - 12}pm" }

@Composable
private fun Tile(value: String, label: String, modifier: Modifier) {
    Column(modifier.height(86.dp).glassLite(RoundedCornerShape(26.dp), alpha = 0.12f, shadow = 12.dp).padding(14.dp), verticalArrangement = Arrangement.Bottom) {
        Text(value, style = BopType.Title, color = Bop.Text, maxLines = 1)
        Text(label, style = BopType.Caption, color = Bop.Text2)
    }
}

/**
 * 30-day listening trend: one bar per day (minutes), today in the accent,
 * a dashed daily average, tap a bar to read its day.
 */
@Composable
private fun TrendChart(r: ReplaySummary) {
    val days = r.last30
    val max = (days.maxOfOrNull { it.second } ?: 0).coerceAtLeast(10)
    val avg = days.sumOf { it.second } / days.size.toFloat()
    val week = days.takeLast(7).sumOf { it.second }
    val prevWeek = days.dropLast(7).takeLast(7).sumOf { it.second }
    var picked by remember { mutableStateOf<Int?>(null) }
    val grow by animateFloatAsState(1f, tween(700), label = "grow")
    Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth().glassLite(RoundedCornerShape(28.dp), alpha = 0.12f, shadow = 14.dp).padding(18.dp)) {
        Text("LISTENING TREND · 30 DAYS", style = BopType.Micro, color = Bop.Text3)
        Spacer(Modifier.height(6.dp))
        val p = picked?.let { days[it] }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (p != null) "${p.second} min" else "$week min", style = BopType.Display, color = Bop.Text)
            Spacer(Modifier.width(8.dp))
            Text(
                if (p != null) p.first.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + p.first.dayOfMonth + " " + p.first.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                else "this week" + when {
                    prevWeek == 0 -> ""
                    week >= prevWeek -> " · ▲ ${((week - prevWeek) * 100 / prevWeek)}%"
                    else -> " · ▼ ${((prevWeek - week) * 100 / prevWeek)}%"
                },
                style = BopType.Caption, color = Bop.Text2, modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Canvas(
            Modifier.fillMaxWidth().height(120.dp).pointerInput(days) {
                detectTapGestures { o ->
                    val i = (o.x / (size.width / days.size)).toInt().coerceIn(days.indices)
                    picked = if (picked == i) null else i
                }
            },
        ) {
            val n = days.size
            val gap = 2.dp.toPx()
            val bw = (size.width - gap * (n - 1)) / n
            val baseY = size.height
            days.forEachIndexed { i, (_, m) ->
                val h = (m / max.toFloat()) * size.height * grow
                val x = i * (bw + gap)
                val c = when {
                    picked == i -> Bop.Accent
                    i == n - 1 -> Color.White
                    else -> Color.White.copy(alpha = if (picked != null) 0.3f else 0.5f)
                }
                if (m > 0) {
                    val hh = h.coerceAtLeast(3.dp.toPx())
                    val rr = (bw / 2).coerceAtMost(4.dp.toPx())
                    // Rounded top, square base: anchored to the baseline.
                    val path = Path().apply {
                        addRoundRect(androidx.compose.ui.geometry.RoundRect(x, baseY - hh, x + bw, baseY, CornerRadius(rr, rr), CornerRadius(rr, rr), CornerRadius.Zero, CornerRadius.Zero))
                    }
                    drawPath(path, c)
                } else drawRoundRect(Color.White.copy(alpha = 0.12f), Offset(x, baseY - 2.dp.toPx()), Size(bw, 2.dp.toPx()), CornerRadius(1f))
            }
            // Daily average
            val ay = baseY - (avg / max) * size.height
            drawLine(Color.White.copy(alpha = 0.45f), Offset(0f, ay), Offset(size.width, ay), 1.dp.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Text(days.first().first.let { "${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}" }, style = BopType.Caption, color = Bop.Text3)
            Spacer(Modifier.weight(1f))
            Text("avg ${avg.toInt()} min/day", style = BopType.Caption, color = Bop.Text3)
            Spacer(Modifier.weight(1f))
            Text("Today", style = BopType.Caption, color = Bop.Text3)
        }
    }
}
