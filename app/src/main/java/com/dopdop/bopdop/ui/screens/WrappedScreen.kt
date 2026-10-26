package com.dopdop.bopdop.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dopdop.bopdop.data.PlayStats
import com.dopdop.bopdop.data.Replay
import com.dopdop.bopdop.data.ReplayRange
import com.dopdop.bopdop.data.ReplaySummary
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.ui.LocalApp
import com.dopdop.bopdop.ui.components.Artwork
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.glass.rememberArtPalette
import com.dopdop.bopdop.ui.share.shareBitmap
import com.dopdop.bopdop.ui.theme.BopType
import kotlinx.coroutines.launch
import java.text.NumberFormat

private const val CARD_MS = 6500

/**
 * Replay as a story: full-screen animated cards that advance on their own. Tap the right side
 * for the next card, the left to go back, hold to pause; share sends the card on screen.
 */
@Composable
fun WrappedScreen() {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val stats by PlayStats.state.collectAsState()
    val summary = remember(stats.events.size) {
        Replay.summarize(stats, ReplayRange.YEAR).takeIf { !it.isEmpty } ?: Replay.summarize(stats, ReplayRange.ALL)
    }
    val range = remember(summary) { if (Replay.summarize(stats, ReplayRange.YEAR).isEmpty) "All time" else "This year" }
    if (summary.isEmpty) {
        Box(Modifier.fillMaxSize().background(Color(0xFF16141C)).windowInsetsPadding(WindowInsets.statusBars).padding(28.dp), contentAlignment = Alignment.Center) {
            Text("Listen for a while and your story writes itself.", style = BopType.Title, color = Color.White)
            CloseButton(Modifier.align(Alignment.TopEnd)) { app.nav.popBackStack() }
        }
        return
    }
    val cards = remember(summary) { storyCards(summary) }
    var page by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val palette = rememberArtPalette(summary.topTracks.firstOrNull()?.track?.artwork)

    LaunchedEffect(page, paused) {
        if (paused) { progress.stop(); return@LaunchedEffect }
        if (progress.value >= 1f) progress.snapTo(0f)
        val left = ((1f - progress.value) * CARD_MS).toInt()
        progress.animateTo(1f, tween(left, easing = LinearEasing))
        if (page < cards.lastIndex) { progress.snapTo(0f); page++ }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(palette.wash, palette.washDeep, Color(0xFF0E0D12))))) {
        Box(
            Modifier.fillMaxSize().drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }
                .pointerInput(cards.size) {
                    detectTapGestures(
                        onPress = { paused = true; tryAwaitRelease(); paused = false },
                        onTap = { o ->
                            scope.launch {
                                if (o.x < size.width / 3f) { if (page > 0) { page--; progress.snapTo(0f) } else progress.snapTo(0f) }
                                else if (page < cards.lastIndex) { page++; progress.snapTo(0f) }
                            }
                        },
                    )
                },
        ) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(palette.wash, palette.washDeep, Color(0xFF0E0D12)))))
            AnimatedContent(page, transitionSpec = { (fadeIn(tween(420)) + scaleIn(tween(520), 0.94f)) togetherWith fadeOut(tween(260)) }, label = "story") { p ->
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 26.dp, vertical = 70.dp)) {
                    cards[p](range)
                }
            }
            Text("BopDop Replay", style = BopType.Caption.copy(fontWeight = FontWeight(700)), color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 22.dp))
        }
        // Story chrome stays out of shared images.
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                cards.indices.forEach { i ->
                    val f = when { i < page -> 1f; i == page -> progress.value; else -> 0f }
                    Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.3f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(Color.White))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
                if (summary.topTracks.isNotEmpty()) {
                    // Your top songs, straight from the story, any card.
                    Text(
                        "Play top songs", style = BopType.BodyStrong, color = Color(0xFF15161B),
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).bounceClick {
                            PlayerConnection.play(summary.topTracks.map { it.track }, 0, "Your Replay · $range")
                        }.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                    Spacer(Modifier.weight(1f))
                }
                RoundIcon(Icons.Rounded.IosShare, "Share card") {
                    scope.launch { shareBitmap(ctx, layer.toImageBitmap().asAndroidBitmap(), "replay-${page + 1}", "My BopDop Replay", "Share your Replay") }
                }
                Spacer(Modifier.width(10.dp))
                RoundIcon(Icons.Rounded.Close, "Close") { app.nav.popBackStack() }
            }
        }
    }
}

private typealias StoryCard = @Composable (range: String) -> Unit

private fun storyCards(s: ReplaySummary): List<StoryCard> = buildList {
    add { range -> Intro(range, s) }
    s.topTracks.firstOrNull()?.let { top -> add { _ -> TopSong(top.track.artwork, top.track.title, top.track.artist, top.plays) } }
    if (s.topTracks.size > 1) add { _ -> TopList("Your top songs", s.topTracks.take(5).map { Triple(it.track.artwork, it.track.title, it.track.artist) }, circle = false) }
    s.topArtists.firstOrNull()?.let { a -> add { _ -> TopArtist(a.artwork, a.name, a.plays) } }
    if (s.topArtists.size > 1) add { _ -> TopList("Your top artists", s.topArtists.take(5).map { Triple(it.artwork, it.name, "${it.plays} plays") }, circle = true) }
    s.busiestHour?.let { h -> add { _ -> Clock(h, s.hours) } }
    add { range -> Summary(range, s) }
}

/** Fade-and-rise entrance for a card's pieces, staggered by [order]. */
@Composable
private fun Modifier.rise(order: Int): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(120L * order); a.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 180f)) }
    return graphicsLayer { alpha = a.value.coerceIn(0f, 1f); translationY = (1f - a.value) * 60f }
}

@Composable
private fun CountUp(target: Int, style: androidx.compose.ui.text.TextStyle) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(target) { a.animateTo(target.toFloat(), tween(1600, easing = FastOutSlowInEasing)) }
    Text(NumberFormat.getIntegerInstance().format(a.value.toInt()), style = style, color = Color.White)
}

private val Big get() = BopType.Display.copy(fontSize = 54.sp, lineHeight = 58.sp, fontWeight = FontWeight(800))

@Composable
private fun Intro(range: String, s: ReplaySummary) = Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
    Text(range.uppercase(), style = BopType.Micro, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.rise(0))
    Text("Your year,\nin music.", style = Big, color = Color.White, modifier = Modifier.rise(1))
    Spacer(Modifier.height(30.dp))
    Box(Modifier.rise(2)) { CountUp(s.minutes, Big.copy(fontSize = 64.sp)) }
    Text("minutes listened", style = BopType.Title, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.rise(3))
}

@Composable
private fun TopSong(art: String, title: String, artist: String, plays: Int) = Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
    Text("YOUR #1 SONG", style = BopType.Micro, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.rise(0))
    Spacer(Modifier.height(18.dp))
    val spin = remember { Animatable(-14f) }
    LaunchedEffect(Unit) { spin.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 120f)) }
    Artwork(art, Modifier.size(250.dp).rise(1).graphicsLayer { rotationZ = spin.value }, corner = 22.dp, size = 800, elevation = 24.dp)
    Spacer(Modifier.height(22.dp))
    com.dopdop.bopdop.ui.components.MarqueeText(title, BopType.Display.copy(fontSize = 28.sp), Color.White, Modifier.fillMaxWidth().rise(2), center = true)
    Text(artist, style = BopType.Headline, color = Color.White.copy(alpha = 0.75f), maxLines = 1, modifier = Modifier.rise(3))
    Text("$plays plays", style = BopType.BodyStrong, color = Color.White, modifier = Modifier.padding(top = 10.dp).rise(4))
}

@Composable
private fun TopArtist(art: String, name: String, plays: Int) = Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
    Text("YOUR TOP ARTIST", style = BopType.Micro, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.rise(0))
    Spacer(Modifier.height(18.dp))
    Artwork(art, Modifier.size(230.dp).rise(1), circle = true, size = 800, elevation = 24.dp)
    Spacer(Modifier.height(22.dp))
    com.dopdop.bopdop.ui.components.MarqueeText(name, Big.copy(fontSize = 36.sp), Color.White, Modifier.fillMaxWidth().rise(2), center = true)
    Text("$plays plays", style = BopType.BodyStrong, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.rise(3))
}

@Composable
private fun TopList(heading: String, rows: List<Triple<String, String, String>>, circle: Boolean) = Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
    Text(heading, style = Big.copy(fontSize = 38.sp), color = Color.White, modifier = Modifier.rise(0))
    Spacer(Modifier.height(24.dp))
    rows.forEachIndexed { i, (art, title, sub) ->
        Row(Modifier.padding(vertical = 8.dp).rise(i + 1), verticalAlignment = Alignment.CenterVertically) {
            Text("${i + 1}", style = BopType.Title.copy(fontWeight = FontWeight(800)), color = Color.White, modifier = Modifier.width(30.dp))
            Artwork(art, Modifier.size(58.dp), circle = circle, corner = 12.dp, size = 300)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = BopType.BodyStrong, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, style = BopType.Caption, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
            }
        }
    }
}

@Composable
private fun Clock(hour: Int, hours: List<Int>) = Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
    val (who, line) = when (hour) {
        in 5..10 -> "Early bird" to "Your music starts with the sunrise."
        in 11..16 -> "Daytime groover" to "You soundtrack the middle of the day."
        in 17..21 -> "Evening unwinder" to "Your evenings are when the music comes on."
        else -> "Night owl" to "The world sleeps; you press play."
    }
    Text("YOU'RE A", style = BopType.Micro, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.rise(0))
    Text(who, style = Big, color = Color.White, modifier = Modifier.rise(1))
    Text(line, style = BopType.Headline, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(top = 8.dp).rise(2))
    Spacer(Modifier.height(30.dp))
    val peak = hours.max().coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(140.dp).rise(3), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        hours.forEachIndexed { h, n ->
            val grow = remember { Animatable(0f) }
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(400L + h * 30L); grow.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 140f)) }
            Box(Modifier.weight(1f).fillMaxHeight((n.toFloat() / peak).coerceAtLeast(0.03f) * grow.value)
                .clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = if (h == hour) 1f else 0.45f)))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("12am", "6am", "12pm", "6pm", "12am").forEach { Text(it, style = BopType.Micro, color = Color.White.copy(alpha = 0.6f)) }
    }
}

@Composable
private fun Summary(range: String, s: ReplaySummary) = Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
    Text("$range, wrapped", style = Big.copy(fontSize = 40.sp), color = Color.White, modifier = Modifier.rise(0))
    Spacer(Modifier.height(24.dp))
    Row(Modifier.rise(1)) {
        Stat("Minutes", NumberFormat.getIntegerInstance().format(s.minutes), Modifier.weight(1f))
        Stat("Plays", NumberFormat.getIntegerInstance().format(s.plays), Modifier.weight(1f))
    }
    Spacer(Modifier.height(18.dp))
    s.topTracks.firstOrNull()?.let { Stat("Top song", it.track.title, Modifier.rise(2)) }
    Spacer(Modifier.height(14.dp))
    s.topArtists.firstOrNull()?.let { Stat("Top artist", it.name, Modifier.rise(3)) }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) = Column(modifier) {
    Text(label.uppercase(), style = BopType.Micro, color = Color.White.copy(alpha = 0.65f))
    com.dopdop.bopdop.ui.components.MarqueeText(value, BopType.Display.copy(fontSize = 27.sp), Color.White)
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).bounceClick(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun CloseButton(modifier: Modifier, onClick: () -> Unit) = Box(modifier) { RoundIcon(Icons.Rounded.Close, "Close", onClick) }
