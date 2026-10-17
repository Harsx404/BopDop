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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.State
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
import com.dopdop.bopdop.ui.glass.LocalReduceMotion
import com.dopdop.bopdop.ui.glass.SilkGradient
import com.dopdop.bopdop.ui.glass.glassEdges
import com.dopdop.bopdop.ui.glass.glassLite
import com.dopdop.bopdop.ui.glass.rememberArtPalette
import com.dopdop.bopdop.ui.glass.rememberSilkTime
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

/** The hero's pages and the shelf they were taken from, so that shelf doesn't repeat them below. */
private class HeroPick(val pages: List<Featured>, val from: Shelf?)

/** Most shelves shown under All; the filters reach the rest. */
private const val ALL_SHELVES = 6

@Composable
fun HomeScreen() {
    val app = LocalApp.current
    val lib by LibraryStore.state.collectAsState()
    val current by PlayerConnection.current.collectAsState()
    val home = rememberLoad("home") { MusicRepository.home() }
    var filter by rememberSaveable { mutableStateOf(HomeFilter.ALL) }
    val haze = rememberHazeState()
    val festive = com.dopdop.bopdop.ui.festive.LocalFestive.current
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
    val matching = feed?.shelves.orEmpty().filter(filter::accepts)
    val hero = remember(matching) { featuredFrom(matching) }
    val featured = hero.pages
    // Below the hero: its shelf minus what the hero already shows, no near-empty rows, and under
    // All only the first few shelves so the page stays calm.
    val shelves = remember(matching, hero, filter) {
        matching.map { sh -> if (sh === hero.from) sh.copy(tracks = sh.tracks.drop(featured.size)) else sh }
            .filter { it.tracks.size + it.cards.size >= 3 }
            .let { if (filter == HomeFilter.ALL) it.take(ALL_SHELVES) else it }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().hazeSource(haze), contentPadding = LocalChromePadding.current) {
            item(key = "top") {
                Box {
                // The festival's touch hangs behind the header and scrolls away with it.
                if (festive.decor) festive.festival?.let { com.dopdop.bopdop.ui.festive.FestiveHomeDecor(it, Modifier.matchParentSize()) }
                Row(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = 20.dp, end = 20.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleButton(onClick = { app.nav.navigate(Routes.SETTINGS) }) { MenuGlyph() }
                    Spacer(Modifier.weight(1f))
                    CircleButton(onClick = { app.nav.navigate(Routes.SEARCH) }) { Icon(Icons.Rounded.Search, "Search", tint = Bop.Text, modifier = Modifier.size(24.dp)) }
                    Spacer(Modifier.width(12.dp))
                    val account by com.dopdop.bopdop.data.account.AccountStore.account.collectAsState()
                    Avatar(account?.photo?.ifBlank { null } ?: current?.artwork ?: lib.history.firstOrNull()?.artwork) {
                        if (account == null) app.nav.navigate(Routes.SIGN_IN) else app.goTab(Routes.LIBRARY)
                    }
                }
                }
            }
            item(key = "headline") {
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp)) {
                    festive.festival?.takeIf { festive.decor }?.let { f ->
                        Text(f.greeting.uppercase(), style = BopType.Micro, color = com.dopdop.bopdop.ui.festive.FestiveColors.accent(f), modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Text(greeting, style = BopType.Hero.copy(fontWeight = FontWeight(560)), color = Bop.Text)
                }
            }
            item(key = "update") { com.dopdop.bopdop.ui.components.UpdateBanner() }
            item(key = "chips") {
                LazyRow(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(HomeFilter.entries.toList()) { f -> Chip(f.label, f == filter) { filter = f } }
                }
            }
            item(key = "hero") {
                when {
                    home.state is Load.Loading -> Box(Modifier.padding(horizontal = 36.dp).fillMaxWidth().height(HERO_HEIGHT).shimmer(40.dp))
                    featured.isNotEmpty() -> HeroCarousel(featured)
                }
            }
            // Under All, Quick picks carries straight on from the hero; your own rows follow, then the catalogue.
            val lead = if (filter == HomeFilter.ALL) shelves.firstOrNull { it.style == ShelfStyle.QUICK_PICKS } else null
            lead?.let { shelf(it, "lead${it.title}") }
            if (filter == HomeFilter.ALL) {
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
                item(key = "mixes") { MadeForYou(lib.liked, lib.history) }
            }
            when (val s = home.state) {
                is Load.Loading -> items(3) { ShelfSkeleton() }
                is Load.Failed -> item { ErrorState(s.message, onRetry = home.retry) }
                is Load.Ready -> {
                    if (shelves.isEmpty()) item {
                        Text("Nothing in ${filter.label} right now.", style = BopType.Body, color = Bop.Text2, modifier = Modifier.padding(28.dp))
                    }
                    shelves.forEachIndexed { i, sh -> if (sh !== lead) shelf(sh, "shelf$i${sh.title}") }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
        TopBlurBar(haze, if (collapsed) 1f else 0f, "", showBack = false, onBack = {})
    }
}

private val HERO_HEIGHT = 380.dp

private fun featuredFrom(shelves: List<Shelf>): HeroPick {
    val trackShelf = shelves.firstOrNull { it.tracks.size >= 3 }
    if (trackShelf != null) {
        val ts = trackShelf.tracks.take(6)
        return HeroPick(ts.map { Featured("t" + it.id, it.title, it.artist, it.artwork, it, null, trackShelf.tracks) }, trackShelf)
    }
    val cards = shelves.flatMap { it.cards }.filter { it.kind == CardKind.ALBUM || it.kind == CardKind.PLAYLIST }.take(6)
    return HeroPick(cards.map { Featured("c" + it.id, it.title, it.subtitle, it.artwork, null, it, emptyList()) }, null)
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
                .height(HERO_HEIGHT)
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
            // Artwork melts into its own colour at the bottom, where the title sits.
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 0.78f to palette.washDeep.copy(alpha = 0.55f), 1f to palette.washDeep.copy(alpha = 0.92f))))
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 22.dp, end = 18.dp, bottom = 22.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f)) {
                    com.dopdop.bopdop.ui.components.MarqueeText(f.title, BopType.Display, Bop.OnMedia)
                    Spacer(Modifier.height(3.dp))
                    Text(f.subtitle, style = BopType.Body, color = Bop.OnMedia2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(12.dp))
                // Icon-only play: a small white disc, no label.
                Box(
                    Modifier.size(52.dp).bounceClick(0.88f) {
                        if (f.track != null) PlayerConnection.play(f.all, f.all.indexOf(f.track).coerceAtLeast(0), "Featured")
                        else f.card?.let { app.playStationOrArtist(it) }
                    }.softShadow(CircleShape, 14.dp, color = Color.Black, alpha = 0.22f, dy = 3.dp).clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.PlayArrow, "Play", tint = Bop.Ink, modifier = Modifier.size(28.dp)) }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val bg by animateColorAsState(if (selected) Color.White else Color.White.copy(alpha = 0.42f), label = "chip")
    Text(
        label,
        style = BopType.BodyStrong.copy(fontWeight = if (selected) FontWeight(650) else FontWeight(500)),
        color = if (selected) Bop.Text else Bop.Text2,
        modifier = Modifier
            .bounceClick(0.94f, onClick = onClick)
            .softShadow(shape, if (selected) 14.dp else 8.dp, alpha = if (selected) 0.16f else 0.08f, dy = 4.dp)
            .clip(shape).background(bg).glassEdges(shape, 1f, light = true)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
private fun CircleButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(54.dp).bounceClick(0.9f, onClick = onClick).glassLite(CircleShape, alpha = 0.6f, shadow = 12.dp),
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
            .clip(CircleShape).background(Color.White).padding(3.dp),
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
            Icons.Rounded.Favorite, listOf(Color(0xFFF2A7C3), Color(0xFF7C6FD6)), liked.firstOrNull()?.artwork,
        ) {
            if (liked.isEmpty()) app.openLocal("liked") else PlayerConnection.play(liked, context = "Liked Songs", shuffle = true)
        })
        history.firstOrNull()?.let { seed ->
            add(Mix("Discovery Station", "Endless, from ${seed.artist.split(",").first()}", Icons.Rounded.AutoAwesome,
                listOf(Color(0xFFB9D98A), Color(0xFF3F8A6E)), seed.artwork) { PlayerConnection.startRadio(seed, "Discovery Station") })
        }
        add(Mix("Your Replay", "Your listening, as stories", Icons.Rounded.History,
            listOf(Color(0xFFFFC98E), Color(0xFFD0667A)), history.getOrNull(2)?.artwork) {
            app.nav.navigate(Routes.WRAPPED)
        })
        if (liked.size >= 3) add(Mix("Deep Cuts", "Radio from your likes", Icons.Rounded.Shuffle,
            listOf(Color(0xFF9EE3E0), Color(0xFF3E6FB0)), liked.random().artwork) {
            PlayerConnection.startRadio(liked.random(), "Deep Cuts")
        })
    }
    val time = rememberSilkTime(!LocalReduceMotion.current && com.dopdop.bopdop.ui.LocalTabActive.current)
    Column {
        SectionHeader("Made for you")
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(mixes, key = { _, m -> m.title }) { i, m -> MixCard(m, time, i.toFloat()) }
        }
    }
}

@Composable
private fun MixCard(m: Mix, time: State<Float>, seed: Float) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier.size(232.dp, 140.dp).bounceClick(onClick = m.play)
            .softShadow(shape, 16.dp, color = m.colors.last(), alpha = 0.32f, dy = 6.dp)
            .clip(shape),
    ) {
        SilkGradient(m.colors, time, seed, Modifier.fillMaxSize())
        if (m.art != null) {
            Artwork(
                m.art,
                Modifier.size(122.dp).align(Alignment.BottomEnd).offset(28.dp, 24.dp).graphicsLayer { rotationZ = -12f },
                corner = 20.dp, size = 300, elevation = 18.dp,
            )
        }
        // Keeps the white title readable over the bright patches of the silk.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.22f))))
        Box(Modifier.matchParentSize().glassEdges(shape, 0.9f, light = true))
        Column(Modifier.padding(16.dp).fillMaxSize()) {
            Box(Modifier.size(34.dp).softShadow(CircleShape, 8.dp, alpha = 0.12f, dy = 2.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.94f)), contentAlignment = Alignment.Center) {
                Icon(m.icon, null, tint = m.colors.last(), modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(m.title, style = BopType.Title, color = Color.White, maxLines = 1)
            Text(m.subtitle, style = BopType.Caption, color = Color.White.copy(alpha = 0.88f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(150.dp))
        }
    }
}
