package com.dopdop.bopdop.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.PlayStats
import com.dopdop.bopdop.data.Replay
import com.dopdop.bopdop.data.ReplayRange
import com.dopdop.bopdop.data.ReplaySummary
import com.dopdop.bopdop.data.TrackStat
import com.dopdop.bopdop.data.share.SharePayload
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.ui.LocalApp
import com.dopdop.bopdop.ui.Sheet
import com.dopdop.bopdop.ui.ShareRequest
import com.dopdop.bopdop.ui.components.Artwork
import com.dopdop.bopdop.ui.components.EqualizerBars
import com.dopdop.bopdop.ui.components.PillButton
import com.dopdop.bopdop.ui.components.SectionHeader
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.glass.glassLite
import com.dopdop.bopdop.ui.glass.rememberArtPalette
import com.dopdop.bopdop.ui.glass.softShadow
import com.dopdop.bopdop.ui.share.Segmented
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType
import java.text.NumberFormat
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Replay — your listening, always on. Built like an editorial recap rather
 * than a stats dump: your #1 song as a cover story, the year in one number,
 * an artist podium, the ranked songs, when in the day you listen, and how it's
 * trending. Everything here is played for real (a play counts after 30 s).
 */
@Composable
fun ReplayScreen() {
    val app = LocalApp.current
    val stats by PlayStats.state.collectAsState()
    var range by rememberSaveable { mutableStateOf(ReplayRange.MONTH) }
    val r = remember(stats, range) { Replay.summarize(stats, range) }
    val current by PlayerConnection.current.collectAsState()
    val labels = listOf("4 wks", "6 mo", "Year", "All")

    LargeTitleScaffold(title = "Replay", eyebrow = rangeEyebrow(range), showBack = true) {
        item(key = "range") {
            Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                Segmented(labels, ReplayRange.entries.indexOf(range), itemW = 78.dp) { range = ReplayRange.entries[it] }
            }
        }
        if (r.isEmpty) {
            item(key = "empty") { EmptyReplay() }
            return@LargeTitleScaffold
        }
        item(key = "hero") { NumberOne(r.topTracks.first(), range) }
        item(key = "minutes") { BigNumber(r) }
        if (r.topArtists.isNotEmpty()) item(key = "podium") { Podium(r) }
        if (r.topTracks.size > 1) {
            item(key = "songsH") { SectionHeader("Top songs", range.label) }
            val songs = r.topTracks.drop(1).take(19)
            val maxPlays = r.topTracks.first().plays.coerceAtLeast(1)
            songs.forEachIndexed { i, s ->
                item(key = "song" + s.track.id) {
                    RankedSong(i + 2, s, maxPlays, current?.id == s.track.id,
                        onMore = { app.sheet = Sheet.TrackOptions(s.track) }) {
                        PlayerConnection.play(r.topTracks.map { it.track }, i + 1, "Replay · ${range.label}")
                    }
                }
            }
        }
        item(key = "clock") { ListeningClock(r) }
        item(key = "trend") { TrendBars(r) }
        if (r.rising.isNotEmpty()) {
            item(key = "riseH") { SectionHeader("On the rise", "Played more this week") }
            item(key = "rise") { Rising(r) }
        }
        item(key = "mix") { ReplayMix(r, range) }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

private fun rangeEyebrow(r: ReplayRange): String = when (r) {
    ReplayRange.MONTH -> "Last 4 weeks"
    ReplayRange.HALF_YEAR -> "Last 6 months"
    ReplayRange.YEAR -> "${java.time.LocalDate.now().year} so far"
    ReplayRange.ALL -> "All time"
}

private fun plays(n: Int) = if (n == 1) "1 play" else "$n plays"
private fun fmt(n: Int) = NumberFormat.getIntegerInstance().format(n)

// --- Cover story: your #1 -------------------------------------------------------------------

@Composable
private fun NumberOne(top: TrackStat, range: ReplayRange) {
    val app = LocalApp.current
    val palette = rememberArtPalette(top.track.artwork)
    val shape = RoundedCornerShape(40.dp)
    Box(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().aspectRatio(0.86f)
            .softShadow(shape, 24.dp, alpha = 0.24f, dy = 10.dp).clip(shape)
            .bounceClick(0.985f) { PlayerConnection.startRadio(top.track, "Your #1") },
    ) {
        Artwork(top.track.artwork, Modifier.fillMaxSize(), corner = 0.dp, size = 1000)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.25f), 0.25f to Color.Transparent, 0.55f to Color.Transparent, 1f to palette.washDeep.copy(alpha = 0.96f))))
        Text(
            "YOUR #1 SONG", style = BopType.Micro, color = Color.White,
            modifier = Modifier.padding(18.dp).glassLite(RoundedCornerShape(50), alpha = 0.22f).padding(horizontal = 12.dp, vertical = 7.dp),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
            com.dopdop.bopdop.ui.components.MarqueeText(top.track.title, BopType.Display.copy(fontSize = 28.sp), Color.White)
            Text(top.track.artist, style = BopType.Headline, color = Color.White.copy(alpha = 0.82f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillButton("Play", Icons.Rounded.PlayArrow, Modifier.width(112.dp), onMedia = true) { PlayerConnection.startRadio(top.track, "Your #1") }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(fmt(top.plays), style = BopType.Title, color = Color.White)
                    Text(if (top.plays == 1) "play" else "plays · ${range.label.lowercase()}", style = BopType.Caption, color = Color.White.copy(alpha = 0.75f))
                }
            }
        }
    }
}

// --- The headline number --------------------------------------------------------------------

@Composable
private fun BigNumber(r: ReplaySummary) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(r.minutes) { anim.snapTo(0f); anim.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    val shown = (r.minutes * anim.value).toInt()
    val hours = r.minutes / 60
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 26.dp)) {
        Text("YOU LISTENED FOR", style = BopType.Micro, color = Bop.Text3)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(fmt(shown), style = BopType.Hero.copy(fontSize = 68.sp, lineHeight = 70.sp, fontWeight = FontWeight(700)), color = Bop.Text)
            Spacer(Modifier.width(10.dp))
            Text("minutes", style = BopType.Title, color = Bop.Text2, modifier = Modifier.padding(bottom = 12.dp))
        }
        Text(
            buildString {
                if (hours >= 1) append("That’s about $hours hour${if (hours == 1) "" else "s"} of music · ")
                append(plays(r.plays))
                append(" · ${r.topTracks.size} different song${if (r.topTracks.size == 1) "" else "s"}")
            },
            style = BopType.Body, color = Bop.Text2,
        )
    }
}

// --- Artist podium --------------------------------------------------------------------------

@Composable
private fun Podium(r: ReplaySummary) {
    val app = LocalApp.current
    val a = r.topArtists
    Column {
        SectionHeader("Top artists")
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            a.getOrNull(1)?.let { PodiumSpot(2, it.name, it.artwork, it.plays, 88.dp, 46.dp) { it.artistId?.let { id -> app.openArtist(id, it.name) } } }
            a.getOrNull(0)?.let { PodiumSpot(1, it.name, it.artwork, it.plays, 118.dp, 70.dp) { it.artistId?.let { id -> app.openArtist(id, it.name) } } }
            a.getOrNull(2)?.let { PodiumSpot(3, it.name, it.artwork, it.plays, 88.dp, 30.dp) { it.artistId?.let { id -> app.openArtist(id, it.name) } } }
        }
        a.drop(3).take(5).forEachIndexed { i, s ->
            Row(
                Modifier.fillMaxWidth().bounceClick(0.985f) { s.artistId?.let { app.openArtist(it, s.name) } }.padding(horizontal = 22.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 4}", style = BopType.Headline, color = Bop.Text3, modifier = Modifier.width(30.dp))
                Artwork(s.artwork, Modifier.size(42.dp), circle = true, size = 160)
                Spacer(Modifier.width(12.dp))
                Text(s.name, style = BopType.BodyStrong, color = Bop.Text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(plays(s.plays), style = BopType.Caption, color = Bop.Text3)
            }
        }
    }
}

@Composable
private fun PodiumSpot(rank: Int, name: String, art: String, plays: Int, size: Dp, block: Dp, onClick: () -> Unit) {
    val ring = when (rank) { 1 -> listOf(Color(0xFFFFD36B), Color(0xFFFFA94D)); 2 -> listOf(Color(0xFFE3E7EE), Color(0xFFAEB6C2)); else -> listOf(Color(0xFFE8B48A), Color(0xFFB9805A)) }
    Column(Modifier.width(size + 12.dp).bounceClick(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Artwork(art, Modifier.size(size).border(3.dp, Brush.linearGradient(ring), CircleShape), circle = true, size = 300, elevation = 12.dp)
            Box(
                Modifier.offset(y = 10.dp).size(26.dp).clip(CircleShape).background(Brush.linearGradient(ring)),
                contentAlignment = Alignment.Center,
            ) { Text("$rank", style = BopType.Caption.copy(fontWeight = FontWeight(800)), color = Bop.Ink) }
        }
        Spacer(Modifier.height(16.dp))
        Text(name, style = BopType.BodyStrong, color = Bop.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(plays(plays), style = BopType.Caption, color = Bop.Text3)
        Spacer(Modifier.height(8.dp))
        // The podium step itself, frosted.
        Box(Modifier.fillMaxWidth().height(block).glassLite(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), alpha = 0.55f))
    }
}

// --- Ranked songs ---------------------------------------------------------------------------

@Composable
private fun RankedSong(rank: Int, s: TrackStat, maxPlays: Int, playing: Boolean, onMore: () -> Unit, onClick: () -> Unit) {
    val palette = rememberArtPalette(s.track.artwork)
    Row(
        Modifier.fillMaxWidth().bounceClick(0.985f, onLongClick = onMore, onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Outlined numerals — editorial, not a list bullet.
        Text(
            "$rank",
            style = BopType.Display.copy(fontSize = 30.sp, fontWeight = FontWeight(800), drawStyle = Stroke(width = 2.2f)),
            color = if (rank <= 3) Bop.Text else Bop.Text3,
            modifier = Modifier.width(44.dp),
        )
        Box {
            Artwork(s.track.artwork, Modifier.size(54.dp), corner = 14.dp, size = 226)
            if (playing) Box(Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                EqualizerBars(Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(s.track.title, style = BopType.BodyStrong, color = Bop.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.track.artist, style = BopType.Caption, color = Bop.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(50)).background(Bop.Ink.copy(alpha = 0.06f))) {
                    Box(Modifier.fillMaxWidth(s.plays / maxPlays.toFloat()).height(4.dp).clip(RoundedCornerShape(50)).background(palette.wash))
                }
                Spacer(Modifier.width(8.dp))
                Text(plays(s.plays), style = BopType.Caption, color = Bop.Text3)
            }
        }
    }
}

// --- When you listen ------------------------------------------------------------------------

@Composable
private fun ListeningClock(r: ReplaySummary) {
    val peak = r.busiestHour
    val persona = when (peak) {
        null -> ""
        in 5..10 -> "Early riser"
        in 11..16 -> "Daytime listener"
        in 17..21 -> "Evening unwinder"
        else -> "Night owl"
    }
    val max = (r.hours.maxOrNull() ?: 1).coerceAtLeast(1)
    val grow = remember { Animatable(0f) }
    LaunchedEffect(r.hours) { grow.snapTo(0f); grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth().glassLite(RoundedCornerShape(30.dp), alpha = 0.55f, shadow = 14.dp).padding(20.dp)) {
        Text("WHEN YOU LISTEN", style = BopType.Micro, color = Bop.Text3)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(persona.ifEmpty { "—" }, style = BopType.Title, color = Bop.Text)
                Text(peak?.let { "Most plays around ${hour(it)}" } ?: "", style = BopType.Caption, color = Bop.Text2)
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(1.35f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                val inner = size.minDimension * 0.22f
                val outer = size.minDimension * 0.46f
                // Dial
                drawCircle(Bop.Ink.copy(alpha = 0.05f), outer, c)
                drawCircle(Color.White.copy(alpha = 0.7f), inner, c)
                for (h in 0 until 24) {
                    val a = (h / 24f) * 2 * PI - PI / 2
                    val v = r.hours[h] / max.toFloat() * grow.value
                    val len = inner + 6.dp.toPx() + (outer - inner - 6.dp.toPx()) * v.coerceAtLeast(0.04f)
                    val col = if (h == peak) Bop.Ink else Bop.Ink.copy(alpha = 0.22f + 0.5f * v)
                    drawLine(col, Offset(c.x + (inner + 6.dp.toPx()) * cos(a).toFloat(), c.y + (inner + 6.dp.toPx()) * sin(a).toFloat()),
                        Offset(c.x + len * cos(a).toFloat(), c.y + len * sin(a).toFloat()), 7.dp.toPx(), StrokeCap.Round)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(peak?.let { hour(it) } ?: "—", style = BopType.Title, color = Bop.Text)
                Text("peak", style = BopType.Caption, color = Bop.Text3)
            }
            // Clock labels
            Text("12am", style = BopType.Micro, color = Bop.Text3, modifier = Modifier.align(Alignment.TopCenter))
            Text("12pm", style = BopType.Micro, color = Bop.Text3, modifier = Modifier.align(Alignment.BottomCenter))
            Text("6am", style = BopType.Micro, color = Bop.Text3, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp))
            Text("6pm", style = BopType.Micro, color = Bop.Text3, modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp))
        }
    }
}

private fun hour(h: Int) = when { h == 0 -> "12am"; h < 12 -> "${h}am"; h == 12 -> "12pm"; else -> "${h - 12}pm" }

// --- 30-day trend ---------------------------------------------------------------------------

@Composable
private fun TrendBars(r: ReplaySummary) {
    val days = r.last30
    val max = (days.maxOfOrNull { it.second } ?: 0).coerceAtLeast(10)
    val avg = days.sumOf { it.second } / days.size.toFloat()
    val week = days.takeLast(7).sumOf { it.second }
    val prevWeek = days.dropLast(7).takeLast(7).sumOf { it.second }
    var picked by remember { mutableStateOf<Int?>(null) }
    val grow = remember { Animatable(0f) }
    LaunchedEffect(days) { grow.snapTo(0f); grow.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth().glassLite(RoundedCornerShape(30.dp), alpha = 0.55f, shadow = 14.dp).padding(20.dp)) {
        Text("LAST 30 DAYS", style = BopType.Micro, color = Bop.Text3)
        Spacer(Modifier.height(4.dp))
        val p = picked?.let { days[it] }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (p != null) "${p.second} min" else "$week min", style = BopType.Display, color = Bop.Text)
            Spacer(Modifier.width(8.dp))
            val change = when {
                p != null -> p.first.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + p.first.dayOfMonth + " " + p.first.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                prevWeek == 0 -> "this week"
                week >= prevWeek -> "this week · up ${(week - prevWeek) * 100 / prevWeek}%"
                else -> "this week · down ${(prevWeek - week) * 100 / prevWeek}%"
            }
            Text(change, style = BopType.Caption, color = Bop.Text2, modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(14.dp))
        Canvas(
            Modifier.fillMaxWidth().height(110.dp).pointerInput(days) {
                detectTapGestures { o -> val i = (o.x / (size.width / days.size)).toInt().coerceIn(days.indices); picked = if (picked == i) null else i }
            },
        ) {
            val n = days.size
            val gap = 3.dp.toPx()
            val bw = (size.width - gap * (n - 1)) / n
            days.forEachIndexed { i, (_, mins) ->
                val x = i * (bw + gap)
                val h = (mins / max.toFloat()) * size.height * grow.value
                val col = when {
                    picked == i -> Bop.Ink
                    i == n - 1 -> Bop.Ink.copy(alpha = 0.85f)
                    else -> Bop.Ink.copy(alpha = if (picked != null) 0.14f else 0.24f)
                }
                if (mins > 0) drawRoundRect(col, Offset(x, size.height - h.coerceAtLeast(3.dp.toPx())), Size(bw, h.coerceAtLeast(3.dp.toPx())), CornerRadius(bw / 2))
                else drawRoundRect(Bop.Ink.copy(alpha = 0.07f), Offset(x, size.height - 3.dp.toPx()), Size(bw, 3.dp.toPx()), CornerRadius(bw / 2))
            }
            val ay = size.height - (avg / max) * size.height
            drawLine(Bop.Ink.copy(alpha = 0.35f), Offset(0f, ay), Offset(size.width, ay), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())))
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Text(days.first().first.let { "${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}" }, style = BopType.Caption, color = Bop.Text3)
            Spacer(Modifier.weight(1f))
            Text("avg ${avg.toInt()} min a day", style = BopType.Caption, color = Bop.Text3)
            Spacer(Modifier.weight(1f))
            Text("Today", style = BopType.Caption, color = Bop.Text3)
        }
    }
}

// --- Rising ---------------------------------------------------------------------------------

@Composable
private fun Rising(r: ReplaySummary) {
    val app = LocalApp.current
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(r.rising.size) { i ->
            val s = r.rising[i]
            Column(Modifier.width(140.dp).bounceClick(onLongClick = { app.sheet = Sheet.TrackOptions(s.track) }) { PlayerConnection.play(r.rising.map { it.track }, i, "On the rise") }) {
                Box {
                    Artwork(s.track.artwork, Modifier.size(140.dp), corner = 24.dp, size = 400, elevation = 12.dp)
                    Row(
                        Modifier.align(Alignment.BottomStart).padding(8.dp).glassLite(RoundedCornerShape(50), tint = Color.Black, alpha = 0.32f).padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.NorthEast, null, tint = Color.White, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(if (s.before == 0) "new" else "+${s.recent - s.before}", style = BopType.Caption.copy(fontWeight = FontWeight(700)), color = Color.White)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(s.track.title, style = BopType.BodyStrong, color = Bop.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s.track.artist, style = BopType.Caption, color = Bop.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// --- The mix --------------------------------------------------------------------------------

@Composable
private fun ReplayMix(r: ReplaySummary, range: ReplayRange) {
    val app = LocalApp.current
    val tracks = r.topTracks.map { it.track }
    val arts = tracks.map { it.artwork }.distinct().take(4)
    val palette = rememberArtPalette(arts.firstOrNull())
    val shape = RoundedCornerShape(34.dp)
    val name = "Replay · ${range.label}"
    Column {
        SectionHeader("Your Replay mix", "Every song above, in order")
        Row(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().softShadow(shape, 18.dp, alpha = 0.2f, dy = 6.dp).clip(shape)
                .background(Brush.linearGradient(listOf(palette.wash, palette.washDeep))).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Collage(arts, Modifier.size(96.dp), 18.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = BopType.Headline, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${tracks.size} songs", style = BopType.Caption, color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MixButton(Icons.Rounded.PlayArrow, "Play") { PlayerConnection.play(tracks, 0, name) }
                    MixButton(Icons.Rounded.BookmarkAdd, "Save") { LibraryStore.createPlaylist(name, tracks); app.toast("Saved “$name” to your Library") }
                    MixButton(Icons.Rounded.QrCode2, "Code") {
                        app.shareQr(ShareRequest(SharePayload.Playlist("My $name", tracks.map { it.id }), "My $name", "Top ${tracks.size} on BopDop", arts.firstOrNull().orEmpty()))
                    }
                }
            }
        }
    }
}

@Composable
private fun MixButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.height(36.dp).bounceClick(0.92f, onClick = onClick).glassLite(RoundedCornerShape(50), alpha = 0.24f).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = BopType.Caption.copy(fontWeight = FontWeight(650)), color = Color.White)
    }
}

// --- Empty ----------------------------------------------------------------------------------

@Composable
private fun EmptyReplay() {
    Column(
        Modifier.padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth().glassLite(RoundedCornerShape(32.dp), alpha = 0.55f, shadow = 14.dp).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // A quiet clock waiting to be filled.
        Canvas(Modifier.size(120.dp)) {
            val c = center; val inner = size.minDimension * 0.22f; val outer = size.minDimension * 0.48f
            for (h in 0 until 24) {
                val a = (h / 24f) * 2 * PI - PI / 2
                val len = inner + (outer - inner) * (0.25f + 0.2f * ((h * 7) % 5) / 5f)
                drawLine(Bop.Ink.copy(alpha = 0.14f), Offset(c.x + inner * cos(a).toFloat(), c.y + inner * sin(a).toFloat()),
                    Offset(c.x + len * cos(a).toFloat(), c.y + len * sin(a).toFloat()), 5.dp.toPx(), StrokeCap.Round)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Your Replay starts with a song", style = BopType.Title, color = Bop.Text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            "A play counts after 30 seconds. Listen for a few days and this fills with your top songs, artists and the hours you listen.",
            style = BopType.Body, color = Bop.Text2, textAlign = TextAlign.Center,
        )
    }
}
