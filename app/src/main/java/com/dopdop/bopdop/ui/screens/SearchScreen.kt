package com.dopdop.bopdop.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.MusicRepository
import com.dopdop.bopdop.data.model.Card
import com.dopdop.bopdop.data.model.SearchResults
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.ui.LocalApp
import com.dopdop.bopdop.ui.LocalChromePadding
import com.dopdop.bopdop.ui.Sheet
import com.dopdop.bopdop.ui.components.Artwork
import com.dopdop.bopdop.ui.components.ErrorState
import com.dopdop.bopdop.ui.components.Load
import com.dopdop.bopdop.ui.components.SectionHeader
import com.dopdop.bopdop.ui.components.TrackRow
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.components.rememberLoad
import com.dopdop.bopdop.ui.components.shimmer
import com.dopdop.bopdop.ui.glass.glass
import com.dopdop.bopdop.ui.glass.glassEdges
import com.dopdop.bopdop.ui.glass.glassLite
import com.dopdop.bopdop.ui.glass.softShadow
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlin.math.abs

private enum class Filter(val label: String) { ALL("Top"), SONGS("Songs"), ARTISTS("Artists"), ALBUMS("Albums"), PLAYLISTS("Playlists") }

/**
 * Which catalogue the results come from. Spotify shows only for people signed in to it; its songs
 * play from your sources like any other. The pick stays for the rest of the session.
 */
private enum class Catalog(val brand: com.dopdop.bopdop.ui.components.Brand) {
    YOUTUBE(com.dopdop.bopdop.ui.components.Brand.YOUTUBE_MUSIC), SPOTIFY(com.dopdop.bopdop.ui.components.Brand.SPOTIFY)
}
private var lastCatalog = Catalog.YOUTUBE

@Composable
fun SearchScreen() {
    val app = LocalApp.current
    val focus = LocalFocusManager.current
    var text by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    val haze = rememberHazeState()
    val lib by LibraryStore.state.collectAsState()

    LaunchedEffect(text) {
        if (text.isBlank()) { suggestions = emptyList(); return@LaunchedEffect }
        delay(220)
        suggestions = MusicRepository.suggestions(text)
    }
    // Results while typing: once the typing pauses, the top songs, artist and albums for it.
    var live by remember { mutableStateOf<SearchResults?>(null) }
    LaunchedEffect(text) {
        val q = text.trim()
        if (q.length < 2) { live = null; return@LaunchedEffect }
        delay(450)
        runCatching { MusicRepository.search(q) }.getOrNull()?.let { live = it }
    }

    fun submit(q: String) {
        val v = q.trim()
        if (v.isEmpty()) return
        text = v; submitted = v; filter = Filter.ALL
        LibraryStore.addRecentSearch(v)
        focus.clearFocus()
    }

    val showSuggestions = focused && text.isNotBlank() && text != submitted
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            when {
                showSuggestions -> SuggestionList(text, suggestions, live, ::submit)
                submitted.isNotBlank() -> Results(submitted, filter) { filter = it }
                else -> Browse(lib.recentSearches, ::submit)
            }
        }
        // Floating glass search field
        Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).glass(RoundedCornerShape(26.dp), state = haze, emphasis = 1.3f).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = Bop.Text2, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) Text("Songs, artists, albums, lyrics…", style = BopType.Body, color = Bop.Text3)
                    BasicTextField(
                        value = text, onValueChange = { text = it },
                        singleLine = true,
                        textStyle = BopType.Body.copy(color = Bop.Text),
                        cursorBrush = SolidColor(Bop.Ink),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit(text) }),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                    )
                }
                if (text.isNotEmpty()) {
                    Box(Modifier.size(28.dp).bounceClick(0.85f) { text = ""; submitted = "" }.glassLite(CircleShape, tint = Bop.Ink, alpha = 0.12f), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, "Clear", tint = Bop.Text, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

private val TOP_PAD = 84.dp

@Composable
private fun SuggestionList(q: String, suggestions: List<String>, live: SearchResults?, onPick: (String) -> Unit) {
    val app = LocalApp.current
    val current by PlayerConnection.current.collectAsState()
    val keep = { LibraryStore.addRecentSearch(q) }
    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), contentPadding = PaddingValues(top = TOP_PAD, bottom = 300.dp)) {
        items((listOf(q) + suggestions.filterNot { it.equals(q, true) }).take(if (live == null) 12 else 4)) { s ->
            Row(
                Modifier.fillMaxWidth().bounceClick(0.98f) { onPick(s) }.padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = Bop.Text3, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(16.dp))
                Text(s, style = BopType.Body, color = Bop.Text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Rounded.NorthWest, null, tint = Bop.Text3, modifier = Modifier.size(18.dp))
            }
        }
        if (live != null && !live.isEmpty) {
            item { SectionHeader("Top results") }
            live.artists.firstOrNull()?.let { a -> item { CardLine(a) { keep(); app.open(a) } } }
            val songs = live.tracks.take(4)
            items(songs.size) { i ->
                val t = songs[i]
                TrackRow(t, playing = current?.id == t.id, onMore = { app.sheet = Sheet.TrackOptions(t) }) { keep(); PlayerConnection.startRadio(t, "Search · $q") }
            }
            live.albums.take(2).forEach { c -> item { CardLine(c) { keep(); app.open(c) } } }
        }
    }
}

/** An artist, album or playlist as one line in the results while typing. */
@Composable
private fun CardLine(c: Card, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().bounceClick(0.98f, onClick = onClick).padding(horizontal = 20.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        val artist = c.kind == com.dopdop.bopdop.data.model.CardKind.ARTIST
        Artwork(c.artwork, Modifier.size(52.dp), corner = 14.dp, circle = artist, size = 226)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(c.title, style = BopType.BodyStrong, color = Bop.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                c.subtitle.ifBlank { c.kind.name.lowercase().replaceFirstChar { it.uppercase() } },
                style = BopType.Caption, color = Bop.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Deterministic, pleasant gradient per category name. */
private fun tileColors(name: String): List<Color> {
    val palettes = listOf(
        listOf(0xFF9FB8D8, 0xFF6F8DBF), listOf(0xFF9DB56A, 0xFF6F8B3C), listOf(0xFFE4B48C, 0xFFC98A6A),
        listOf(0xFF8FC7C1, 0xFF4F8F89), listOf(0xFFC7B3E0, 0xFF8E78B8), listOf(0xFFE8C77A, 0xFFC09A45),
        listOf(0xFFE3A3A8, 0xFFB8707A), listOf(0xFF9BB0A0, 0xFF5E7666), listOf(0xFFA9C4E8, 0xFF7C93C8),
        listOf(0xFFD8A58A, 0xFFA8705A),
    )
    return palettes[abs(name.hashCode()) % palettes.size].map { Color(it) }
}

@Composable
private fun Browse(recent: List<String>, onPick: (String) -> Unit) {
    val app = LocalApp.current
    val moods = rememberLoad("moods", 6 * 3600_000L) { MusicRepository.moods() }
    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), contentPadding = PaddingValues(top = TOP_PAD - 18.dp, bottom = LocalChromePadding.current.calculateBottomPadding())) {
        if (recent.isNotEmpty()) {
            item {
                SectionHeader("Recent searches", action = "Clear") { LibraryStore.clearRecentSearches() }
                FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.forEach { r ->
                        Row(
                            Modifier.bounceClick { onPick(r) }.glassLite(RoundedCornerShape(50), alpha = 0.55f, shadow = 8.dp).padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.History, null, tint = Bop.Text3, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(r, style = BopType.Caption, color = Bop.Text)
                        }
                    }
                }
            }
        }
        item { SectionHeader("Browse all") }
        when (val s = moods.state) {
            is Load.Loading -> item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(4) { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { repeat(2) { Box(Modifier.weight(1f).height(96.dp).shimmer(18.dp)) } } }
                }
            }
            is Load.Failed -> item { ErrorState(s.message, onRetry = moods.retry) }
            is Load.Ready -> {
                // Editorial rhythm: one wide tile, then two pairs, repeat.
                val rows = buildList {
                    var i = 0; var k = 0
                    val all = s.value
                    while (i < all.size) {
                        val n = if (k % 3 == 0) 1 else 2
                        add(all.subList(i, minOf(i + n, all.size))); i += n; k++
                    }
                }
                items(rows.size, key = { "mrow$it" }) { r ->
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rows[r].forEach { (title, params) ->
                            MoodTile(title, params, wide = rows[r].size == 1, modifier = Modifier.weight(1f)) { app.openMood(title, params) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Results(q: String, filter: Filter, onFilter: (Filter) -> Unit) {
    val app = LocalApp.current
    val current by PlayerConnection.current.collectAsState()
    val spotifyOn = com.dopdop.bopdop.data.spotify.SpotifyAccount.profile.collectAsState().value != null || com.dopdop.bopdop.data.spotify.SpotifyAccount.signedIn
    var catalog by remember { mutableStateOf(lastCatalog) }
    val cat = if (spotifyOn) catalog else Catalog.YOUTUBE
    val res = rememberLoad("search:${cat.name}:$q", 10 * 60_000L) {
        if (cat == Catalog.SPOTIFY) MusicRepository.searchSpotify(q) else MusicRepository.search(q)
    }
    val from = if (cat == Catalog.SPOTIFY) "Spotify · $q" else "Search · $q"
    val listState = rememberLazyListState()
    LaunchedEffect(filter, cat) { listState.scrollToItem(0) }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(top = TOP_PAD, bottom = LocalChromePadding.current.calculateBottomPadding())) {
        if (spotifyOn) item {
            // One catalogue at a time: the same song from both would only list twice.
            Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp).fillMaxWidth().glassLite(RoundedCornerShape(50), alpha = 0.4f).padding(4.dp)) {
                Catalog.entries.forEach { c ->
                    val on = c == cat
                    val bg by animateColorAsState(if (on) Bop.Ink else androidx.compose.ui.graphics.Color.Transparent, label = "cat")
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(50)).background(bg)
                            .bounceClick { catalog = c; lastCatalog = c }.padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        com.dopdop.bopdop.ui.components.BrandLogo(c.brand, 18.dp, Icons.Rounded.Search)
                        Spacer(Modifier.width(8.dp))
                        Text(c.brand.label, style = BopType.BodyStrong, color = if (on) Bop.OnInk else Bop.Text)
                    }
                }
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Filter.entries.toList()) { f ->
                    val on = f == filter
                    val bg by animateColorAsState(if (on) Bop.Ink else Bop.Veil.copy(alpha = 0.55f), label = "f")
                    Text(
                        f.label, style = BopType.BodyStrong, color = if (on) Bop.OnInk else Bop.Text,
                        modifier = Modifier.bounceClick { onFilter(f) }.clip(RoundedCornerShape(50)).background(bg)
                            .glassEdges(RoundedCornerShape(50), if (on) 0f else 1f, true)
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
            }
        }
        when (val s = res.state) {
            is Load.Loading -> items(8) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(50.dp).shimmer(8.dp)); Spacer(Modifier.width(14.dp))
                    Column { Box(Modifier.size(180.dp, 14.dp).shimmer(4.dp)); Spacer(Modifier.height(6.dp)); Box(Modifier.size(110.dp, 12.dp).shimmer(4.dp)) }
                }
            }
            is Load.Failed -> item { ErrorState(s.message, onRetry = res.retry) }
            is Load.Ready -> {
                val r: SearchResults = s.value
                if (r.isEmpty) item { Text("No results for “$q”", style = BopType.Body, color = Bop.Text2, modifier = Modifier.padding(28.dp)) }
                when (filter) {
                    Filter.ALL -> {
                        val topArtist = r.artists.firstOrNull { it.title.equals(q, true) }
                        val top: Any? = topArtist ?: r.tracks.firstOrNull()
                        if (top != null) item { TopResult(top) }
                        if (r.tracks.isNotEmpty()) {
                            item { SectionHeader("Songs", action = "See all") { onFilter(Filter.SONGS) } }
                            val first = r.tracks.take(5)
                            items(first.size) { i ->
                                val t = first[i]
                                TrackRow(t, playing = current?.id == t.id, onMore = { app.sheet = Sheet.TrackOptions(t) }) { PlayerConnection.startRadio(t, from) }
                            }
                        }
                        if (r.artists.isNotEmpty()) item { SectionHeader("Artists"); CardRow(r.artists, 116.dp) }
                        if (r.albums.isNotEmpty()) item { SectionHeader("Albums"); CardRow(r.albums) }
                        if (r.playlists.isNotEmpty()) item { SectionHeader("Playlists"); CardRow(r.playlists) }
                    }
                    Filter.SONGS -> items(r.tracks.size) { i ->
                        val t = r.tracks[i]
                        TrackRow(t, playing = current?.id == t.id, onMore = { app.sheet = Sheet.TrackOptions(t) }) { PlayerConnection.startRadio(t, from) }
                    }
                    Filter.ARTISTS -> cardGrid(r.artists)
                    Filter.ALBUMS -> cardGrid(r.albums)
                    Filter.PLAYLISTS -> cardGrid(r.playlists)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.cardGrid(cards: List<Card>) {
    val rows = cards.chunked(2)
    items(rows.size) { i ->
        val app = LocalApp.current
        Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            rows[i].forEach { c ->
                Box(Modifier.weight(1f)) {
                    androidx.compose.foundation.layout.BoxWithConstraints {
                        com.dopdop.bopdop.ui.components.CardTile(c, width = maxWidth, onLongClick = { app.holdCard(c) }) { app.open(c) }
                    }
                }
            }
            if (rows[i].size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun TopResult(top: Any) {
    val app = LocalApp.current
    val shape = RoundedCornerShape(24.dp)
    val (title, sub, art, circle) = when (top) {
        is Card -> listOf(top.title, "Artist", top.artwork, true)
        is Track -> listOf(top.title, "Song · ${top.artist}", top.artwork, false)
        else -> return
    }
    Column {
        SectionHeader("Top result")
        Row(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth()
                .bounceClick { if (top is Card) app.open(top) else PlayerConnection.startRadio(top as Track, "Top result") }
                .glassLite(shape, alpha = 0.5f, shadow = 14.dp).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(art as String, Modifier.size(92.dp), corner = 14.dp, circle = circle as Boolean, elevation = 12.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                com.dopdop.bopdop.ui.components.MarqueeText(title as String, BopType.Title, Bop.Text)
                Spacer(Modifier.height(4.dp))
                Text(sub as String, style = BopType.Caption, color = Bop.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier.size(48.dp).bounceClick(0.88f) {
                    if (top is Card) app.playStationOrArtist(top) else PlayerConnection.startRadio(top as Track, "Top result")
                }.clip(CircleShape).background(Bop.Ink),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, "Play", tint = Bop.OnInk, modifier = Modifier.size(28.dp)) }
        }
    }
}

/** Genre tile coloured by its own cover art, with two tilted covers peeking out. */
@Composable
internal fun MoodTile(title: String, params: String, wide: Boolean, modifier: Modifier, onClick: () -> Unit) {
    // Covers come from the catalog cache (memory, then disk), so scrolling back never refetches.
    val known = remember(params) { com.dopdop.bopdop.data.CatalogCache.moodArtNow(params) }
    val list by androidx.compose.runtime.produceState(known.orEmpty(), params) {
        if (known == null) value = runCatching { com.dopdop.bopdop.data.CatalogCache.moodArt(params) }.onFailure { android.util.Log.w("MoodTile", "cover load failed", it) }.getOrDefault(emptyList())
    }
    val loading = known == null && list.isEmpty()
    val palette = com.dopdop.bopdop.ui.glass.rememberArtPalette(list.firstOrNull())
    val fallback = tileColors(title)
    val top by animateColorAsState(if (list.isEmpty()) fallback[0] else androidx.compose.ui.graphics.lerp(palette.wash, Color(0xFF9A9DA4), 0.28f), androidx.compose.animation.core.tween(700), label = "mt")
    val bottom by animateColorAsState(if (list.isEmpty()) fallback[1] else androidx.compose.ui.graphics.lerp(palette.washDeep, Color(0xFF55585F), 0.22f), androidx.compose.animation.core.tween(700), label = "mb")
    val shape = RoundedCornerShape(28.dp)
    val h = if (wide) 150.dp else 128.dp
    val cover = if (wide) 112.dp else 84.dp
    Box(
        modifier.height(h).bounceClick(onClick = onClick)
            .softShadow(shape, 14.dp, alpha = 0.16f)
            .clip(shape)
            .background(Brush.linearGradient(listOf(top, bottom), start = androidx.compose.ui.geometry.Offset.Zero, end = androidx.compose.ui.geometry.Offset.Infinite))
            .glassEdges(shape, 0.8f, true),
    ) {
        // Back cover, then front cover — a little stack of records.
        list.getOrNull(1)?.let {
            Artwork(it, Modifier.size(cover).align(Alignment.BottomEnd).offset((-cover.value * 0.42f).dp, cover * 0.22f).graphicsLayer { rotationZ = -10f; alpha = 0.9f }, corner = 14.dp, size = 300, elevation = 10.dp)
        }
        if (list.isNotEmpty()) {
            Artwork(list[0], Modifier.size(cover).align(Alignment.BottomEnd).offset(cover * 0.12f, cover * 0.16f).graphicsLayer { rotationZ = 12f }, corner = 14.dp, size = 300, elevation = 14.dp)
        } else if (loading) {
            Box(Modifier.size(cover).align(Alignment.BottomEnd).offset(cover * 0.12f, cover * 0.16f).graphicsLayer { rotationZ = 12f }.glassLite(RoundedCornerShape(14.dp), alpha = 0.25f))
        }
        Column(Modifier.padding(16.dp).fillMaxWidth(if (wide) 0.62f else 0.7f)) {
            Text(title, style = if (wide) BopType.Title else BopType.Headline, color = Color.White, maxLines = 2)
            if (wide) Text("Playlists & mixes", style = BopType.Caption, color = Color.White.copy(alpha = 0.8f))
        }
    }
}
