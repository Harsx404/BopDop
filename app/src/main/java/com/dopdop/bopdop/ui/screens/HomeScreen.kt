package com.dopdop.bopdop.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.MusicRepository
import com.dopdop.bopdop.data.model.Card
import com.dopdop.bopdop.data.model.CardKind
import com.dopdop.bopdop.data.model.HomeFeed
import com.dopdop.bopdop.data.model.Shelf
import com.dopdop.bopdop.data.model.ShelfStyle
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.ui.LocalApp
import com.dopdop.bopdop.ui.LocalChromePadding
import com.dopdop.bopdop.ui.Routes
import com.dopdop.bopdop.ui.Sheet
import com.dopdop.bopdop.ui.components.Artwork
import com.dopdop.bopdop.ui.components.ErrorState
import com.dopdop.bopdop.ui.components.Load
import com.dopdop.bopdop.ui.components.PillButton
import com.dopdop.bopdop.ui.components.SectionHeader
import com.dopdop.bopdop.ui.components.ShelfSkeleton
import com.dopdop.bopdop.ui.components.TrackTile
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.components.rememberLoad
import com.dopdop.bopdop.ui.components.shimmer
import com.dopdop.bopdop.ui.glass.glassEdges
import com.dopdop.bopdop.ui.glass.glassLite
import com.dopdop.bopdop.ui.glass.rememberArtPalette
import com.dopdop.bopdop.ui.glass.softShadow
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.util.Calendar
import kotlin.math.absoluteValue

private enum class HomeFilter(val label: String) {
    ALL("All"), HOT("Hot Tracks"), NEW("New Releases"), CHARTS("Charts"), ARTISTS("Artists"), RADIO("Mixes & Radio");

    fun accepts(s: Shelf): Boolean {
        val t = s.title.lowercase()
        return when (this) {
            ALL -> true
            HOT -> s.tracks.isNotEmpty() && s.cards.isEmpty()
            NEW -> s.style == ShelfStyle.LARGE_CARDS || "release" in t || "new " in t || t.startsWith("new")
            CHARTS -> "chart" in t || "top " in t || t.startsWith("top") || "trending" in t
            ARTISTS -> s.style == ShelfStyle.ARTISTS || (s.cards.isNotEmpty() && s.cards.all { it.kind == CardKind.ARTIST })
            RADIO -> "mix" in t || "radio" in t || "station" in t || s.cards.any { it.kind == CardKind.STATION || it.kind == CardKind.MOOD }
        }
    }
}

/** One page of the hero carousel: a track (plays its radio) or a collection (opens / plays). */
private data class Featured(val key: String, val title: String, val subtitle: String, val art: String, val track: Track?, val card: Card?, val all: List<Track>)

@Composable
fun HomeScreen() {
    val app = LocalApp.current
    val lib by LibraryStore.state.collectAsState()
    val current by PlayerConnection.current.collectAsState()
    val home = rememberLoad("home") { MusicRepository.home() }
    var filter by rememberSaveable { mutableStateOf(HomeFilter.ALL) }
    val haze = rememberHazeState()
    val list = rememberLazyListState()
    val collapsed by remember { derivedStateOf { list.firstVisibleItemIndex > 1 } }
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Late night listening"
        }
    }
    val feed = (home.state as? Load.Ready<HomeFeed>)?.value
    val shelves = feed?.shelves.orEmpty().filter(filter::accepts)
    val featured = remember(shelves) { featuredFrom(shelves) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().hazeSource(haze), contentPadding = LocalChromePadding.current) {
            item(key = "top") {
                Row(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = 20.dp, end = 20.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleButton(onClick = { app.nav.navigate(Routes.SETTINGS) }) { MenuGlyph() }
                    Spacer(Modifier.weight(1f))
                    CircleButton(onClick = { app.goTab(Routes.SEARCH) }) { Icon(Icons.Rounded.Search, "Search", tint = Bop.Text, modifier = Modifier.size(24.dp)) }
                    Spacer(Modifier.width(12.dp))
                    val account by com.dopdop.bopdop.data.account.AccountStore.account.collectAsState()
                    Avatar(account?.photo?.ifBlank { null } ?: current?.artwork ?: lib.history.firstOrNull()?.artwork) {
                        if (account == null) app.nav.navigate(Routes.SIGN_IN) else app.goTab(Routes.LIBRARY)
                    }
                }
            }
            item(key = "headline") {
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 26.dp)) {
                    Text(greeting.uppercase(), style = BopType.Micro, color = Bop.Text3)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Music That Truly\nMoves With You.",
                        style = BopType.Hero.copy(fontSize = BopType.Hero.fontSize * 1.06f, fontWeight = FontWeight(560)),
                        color = Bop.Text,
                    )
                }
            }
            item(key = "chips") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(HomeFilter.entries.toList()) { f -> Chip(f.label, f == filter) { filter = f } }
                }
            }
            item(key = "hero") {
                when {
                    home.state is Load.Loading -> Box(Modifier.padding(horizontal = 36.dp).fillMaxWidth().height(440.dp).shimmer(40.dp))
                    featured.isNotEmpty() -> HeroCarousel(featured)
                }
            }
            if (filter == HomeFilter.ALL) {
                item(key = "mixes") { MadeForYou(lib.liked, lib.history) }
                if (lib.history.isNotEmpty()) item(key = "recent") {
                    val recent = lib.history.take(16)
                    Column {
                        SectionHeader("Jump back in", action = "See all") { app.openLocal("history") }
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(recent.size) { i ->
                                val t = recent[i]
                                TrackTile(t, playing = current?.id == t.id, width = 140.dp, onLongClick = { app.sheet = Sheet.TrackOptions(t) }) {
                                    PlayerConnection.startRadio(t, "Jump back in")
                                }
                            }
                        }
                    }
                }
            }
            when (val s = home.state) {
                is Load.Loading -> items(3) { ShelfSkeleton() }
                is Load.Failed -> item { ErrorState(s.message, onRetry = home.retry) }
                is Load.Ready -> {
                    if (shelves.isEmpty()) item {
                        Text("Nothing in ${filter.label} right now.", style = BopType.Body, color = Bop.Text2, modifier = Modifier.padding(28.dp))
                    }
                    shelves.forEachIndexed { i, sh -> shelf(sh, "shelf$i${sh.title}") }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
        TopBlurBar(haze, if (collapsed) 1f else 0f, "", showBack = false, onBack = {})
    }
}

private fun featuredFrom(shelves: List<Shelf>): List<Featured> {
    val trackShelf = shelves.firstOrNull { it.tracks.size >= 3 }
    if (trackShelf != null) {
        val ts = trackShelf.tracks.take(10)
        return ts.map { Featured("t" + it.id, it.title, it.artist, it.artwork, it, null, trackShelf.tracks) }
    }
    val cards = shelves.flatMap { it.cards }.filter { it.kind == CardKind.ALBUM || it.kind == CardKind.PLAYLIST }.take(10)
    return cards.map { Featured("c" + it.id, it.title, it.subtitle, it.artwork, null, it, emptyList()) }
}

@Composable
private fun HeroCarousel(items: List<Featured>) {
    val app = LocalApp.current
    val pager = rememberPagerState { items.size }
    HorizontalPager(
        state = pager,
        contentPadding = PaddingValues(horizontal = 36.dp),
        pageSpacing = 14.dp,
        modifier = Modifier.fillMaxWidth(),
        key = { items[it].key },
    ) { page ->
        val f = items[page]
        val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue
        val palette = rememberArtPalette(f.art)
        val shape = RoundedCornerShape(42.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .height(440.dp)
                .graphicsLayer {
                    val s = 1f - (offset * 0.08f).coerceAtMost(0.08f)
                    scaleX = s; scaleY = s
                    alpha = 1f - (offset * 0.25f).coerceAtMost(0.4f)
                }
                .softShadow(shape, 22.dp, alpha = 0.22f, dy = 10.dp)
                .clip(shape)
                .bounceClick(0.985f) { if (f.card != null) app.open(f.card) else f.track?.let { PlayerConnection.startRadio(it, "Featured") } },
        ) {
            Artwork(f.art, Modifier.fillMaxSize(), corner = 0.dp, size = 1000)
            // Sky-to-wash grading like the reference: legible title on top, artwork melting into its own colour below.
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.30f), 0.28f to Color.Transparent, 0.62f to Color.Transparent, 1f to palette.wash.copy(alpha = 0.92f))))
            Column(Modifier.fillMaxWidth().padding(top = 30.dp, start = 22.dp, end = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(f.title, style = BopType.Display.copy(fontSize = BopType.Display.fontSize * 0.92f), color = Bop.OnMedia, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(f.subtitle, style = BopType.Body, color = Bop.OnMedia2, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            PillButton(
                if (f.card != null) "Listen" else "Play Now", Icons.Rounded.PlayArrow,
                Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp).width(190.dp).height(60.dp),
                onMedia = true,
            ) {
                if (f.track != null) PlayerConnection.play(f.all, f.all.indexOf(f.track).coerceAtLeast(0), "Featured")
                else f.card?.let { app.playStationOrArtist(it) }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val bg by animateColorAsState(if (selected) Bop.Accent else Color.White.copy(alpha = 0.12f), label = "chip")
    Text(
        label,
        style = BopType.BodyStrong.copy(fontWeight = if (selected) FontWeight(650) else FontWeight(500)),
        color = if (selected) Color.White else Bop.Text2,
        modifier = Modifier
            .bounceClick(0.94f, onClick = onClick)
            .softShadow(shape, if (selected) 14.dp else 8.dp, alpha = if (selected) 0.28f else 0.12f, dy = 4.dp)
            .clip(shape).background(bg).glassEdges(shape, if (selected) 0.7f else 1f)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
private fun CircleButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(54.dp).bounceClick(0.9f, onClick = onClick).glassLite(CircleShape, alpha = 0.14f, shadow = 12.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The reference's staggered three-line menu glyph. */
@Composable
private fun MenuGlyph() {
    Box(Modifier.size(24.dp).drawBehind {
        val w = 2.2.dp.toPx()
        val c = Bop.Text
        drawLine(c, Offset(size.width * 0.12f, size.height * 0.24f), Offset(size.width * 0.88f, size.height * 0.24f), w, StrokeCap.Round)
        drawLine(c, Offset(size.width * 0.12f, size.height * 0.5f), Offset(size.width * 0.72f, size.height * 0.5f), w, StrokeCap.Round)
        drawLine(c, Offset(size.width * 0.12f, size.height * 0.76f), Offset(size.width * 0.5f, size.height * 0.76f), w, StrokeCap.Round)
    })
}

@Composable
private fun Avatar(art: String?, onClick: () -> Unit) {
    Box(
        Modifier.size(54.dp).bounceClick(0.9f, onClick = onClick).softShadow(CircleShape, 12.dp, dy = 4.dp)
            .clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).glassEdges(CircleShape).padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) Artwork(art, Modifier.fillMaxSize(), circle = true, size = 160)
        else Box(Modifier.fillMaxSize().clip(CircleShape).background(Bop.Brand), contentAlignment = Alignment.Center) {
            Text("B", style = BopType.Title, color = Color.White)
        }
    }
}

private data class Mix(val title: String, val subtitle: String, val icon: ImageVector, val colors: List<Color>, val art: String?, val play: () -> Unit)

@Composable
private fun MadeForYou(liked: List<Track>, history: List<Track>) {
    val app = LocalApp.current
    val mixes = buildList {
        add(Mix(
            "Liked Songs", if (liked.isEmpty()) "Tap ♥ on anything" else "${liked.size} songs · shuffle",
            Icons.Rounded.Favorite, listOf(Color(0xFF4F8DF5), Color(0xFF2B3FC2)), liked.firstOrNull()?.artwork,
        ) {
            if (liked.isEmpty()) app.openLocal("liked") else PlayerConnection.play(liked, context = "Liked Songs", shuffle = true)
        })
        history.firstOrNull()?.let { seed ->
            add(Mix("Discovery Station", "Endless, from ${seed.artist.split(",").first()}", Icons.Rounded.AutoAwesome,
                listOf(Color(0xFF8A55E0), Color(0xFF4A2A9E)), seed.artwork) { PlayerConnection.startRadio(seed, "Discovery Station") })
        }
        add(Mix("Your Replay", "Top songs, artists & trends", Icons.Rounded.History,
            listOf(Color(0xFFF07A8E), Color(0xFFB2408A)), history.getOrNull(2)?.artwork) {
            app.nav.navigate(Routes.REPLAY)
        })
        if (liked.size >= 3) add(Mix("Deep Cuts", "Radio from your likes", Icons.Rounded.Shuffle,
            listOf(Color(0xFF3FB6D9), Color(0xFF2560B8)), liked.random().artwork) {
            PlayerConnection.startRadio(liked.random(), "Deep Cuts")
        })
    }
    Column {
        SectionHeader("Made for you")
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(mixes, key = { it.title }) { m -> MixCard(m) }
        }
    }
}

@Composable
private fun MixCard(m: Mix) {
    val shape = RoundedCornerShape(30.dp)
    Box(
        Modifier.size(250.dp, 150.dp).bounceClick(onClick = m.play)
            .softShadow(shape, 16.dp, alpha = 0.18f, dy = 6.dp)
            .clip(shape)
            .background(Brush.linearGradient(m.colors))
            .glassEdges(shape, 0.9f),
    ) {
        if (m.art != null) {
            Artwork(
                m.art,
                Modifier.size(132.dp).align(Alignment.BottomEnd).offset(30.dp, 26.dp).graphicsLayer { rotationZ = -12f },
                corner = 22.dp, size = 300, elevation = 18.dp,
            )
        }
        Column(Modifier.padding(18.dp).fillMaxSize()) {
            Box(Modifier.size(36.dp).glassLite(CircleShape, alpha = 0.3f), contentAlignment = Alignment.Center) {
                Icon(m.icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(m.title, style = BopType.Title, color = Color.White, maxLines = 1)
            Text(m.subtitle, style = BopType.Caption, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(160.dp))
        }
    }
}
