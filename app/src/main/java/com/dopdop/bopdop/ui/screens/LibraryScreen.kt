package com.dopdop.bopdop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.UserPlaylist
import com.dopdop.bopdop.data.model.Card
import com.dopdop.bopdop.data.model.CardKind
import com.dopdop.bopdop.data.model.formatTotal
import com.dopdop.bopdop.data.model.songs
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.ui.LocalApp
import com.dopdop.bopdop.ui.Sheet
import com.dopdop.bopdop.ui.components.Artwork
import com.dopdop.bopdop.ui.components.GlassIconButton
import com.dopdop.bopdop.ui.components.SectionHeader
import com.dopdop.bopdop.ui.components.TrackTile
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.glass.glassEdges
import com.dopdop.bopdop.ui.glass.glassLite
import com.dopdop.bopdop.ui.glass.rememberArtPalette
import com.dopdop.bopdop.ui.glass.softShadow
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType
import com.dopdop.bopdop.data.PlayStats
import androidx.compose.runtime.remember
import com.dopdop.bopdop.data.account.AccountStore
import com.dopdop.bopdop.data.ytm.YtMusic
import com.dopdop.bopdop.ui.components.CardTile
import com.dopdop.bopdop.ui.components.Load
import com.dopdop.bopdop.ui.components.rememberLoad
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.ThumbUp

@Composable
fun LibraryScreen() {
    val app = LocalApp.current
    val lib by LibraryStore.state.collectAsState()
    val current by PlayerConnection.current.collectAsState()
    val savedCollections = lib.saved.filter { it.kind == CardKind.ALBUM || it.kind == CardKind.PLAYLIST }
    val following = lib.saved.filter { it.kind == CardKind.ARTIST }

    val account by AccountStore.account.collectAsState()
    val scan = { app.openScanner() }
    LargeTitleScaffold(
        title = "Library",
        eyebrow = "${songs(lib.liked.size)} liked · ${lib.playlists.size} playlists",
        trailing = {
            GlassIconButton(Icons.Rounded.QrCodeScanner, "Scan a code", size = 44.dp, onClick = scan)
            Spacer(Modifier.width(10.dp))
            GlassIconButton(Icons.Rounded.Add, "New playlist", size = 44.dp) { app.sheet = Sheet.NewPlaylist(emptyList()) }
        },
    ) {
        item(key = "liked") { LikedHero() }

        item(key = "replay") { ReplayBanner { app.nav.navigate(com.dopdop.bopdop.ui.Routes.REPLAY) } }

        if (account != null) item(key = "ytm") { YtMusicLibrary() }

        item(key = "shortcuts") {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Shortcut(
                    "Recently Played", songs(lib.history.size), Icons.Rounded.History, Modifier.weight(1f),
                    art = { m -> MiniCollage(lib.history.map { it.artwork }.distinct().take(4), m) },
                ) { app.openLocal("history") }
                Shortcut(
                    "Following", if (following.isEmpty()) "No artists yet" else "${following.size} artists", Icons.Rounded.Person, Modifier.weight(1f),
                    art = { m -> AvatarStack(following.map { it.artwork }.take(3), m) },
                ) { if (following.isNotEmpty()) app.seeAll("Following", following) else app.goTab(com.dopdop.bopdop.ui.Routes.SEARCH) }
            }
        }

        item(key = "offline") { DeviceShortcuts() }

        if (lib.history.isNotEmpty()) item(key = "recent") {
            val recent = lib.history.take(14)
            Column {
                SectionHeader("Recently played", action = "See all") { app.openLocal("history") }
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(recent.size) { i ->
                        val t = recent[i]
                        TrackTile(t, playing = current?.id == t.id, width = 124.dp, onLongClick = { app.sheet = Sheet.TrackOptions(t) }) {
                            PlayerConnection.play(recent, i, "Recently played")
                        }
                    }
                }
            }
        }

        item(key = "plHeader") { SectionHeader("Your playlists", action = "New") { app.sheet = Sheet.NewPlaylist(emptyList()) } }
        val tiles: List<Any> = listOf<Any>("new") + lib.playlists
        val rows = tiles.chunked(2)
        items(rows.size, key = { "pl$it" }) { r ->
            Row(Modifier.padding(horizontal = 20.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                rows[r].forEach { item ->
                    Box(Modifier.weight(1f)) {
                        if (item is UserPlaylist) PlaylistCard(item) else NewPlaylistCard { app.sheet = Sheet.NewPlaylist(emptyList()) }
                    }
                }
                if (rows[r].size == 1) Spacer(Modifier.weight(1f))
            }
        }

        if (savedCollections.isNotEmpty()) {
            item(key = "savedHeader") { SectionHeader("Saved albums & playlists") }
            val sr = savedCollections.chunked(2)
            items(sr.size, key = { "sv$it" }) { r ->
                Row(Modifier.padding(horizontal = 20.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    sr[r].forEach { c -> Box(Modifier.weight(1f)) { SavedCard(c) } }
                    if (sr[r].size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        if (following.isNotEmpty()) item(key = "following") {
            Column {
                SectionHeader("Artists you follow")
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(following, key = { it.id }) { a ->
                        Column(Modifier.width(96.dp).bounceClick(onLongClick = { app.sheet = Sheet.CardOptions(a) }) { app.open(a) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Artwork(a.artwork, Modifier.size(96.dp), circle = true, size = 300, elevation = 10.dp)
                            Spacer(Modifier.height(8.dp))
                            Text(a.title, style = BopType.Caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(600)), color = Bop.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

/** Music that plays without data: files on the phone and songs kept whole in the cache. */
@Composable
private fun DeviceShortcuts() {
    val app = LocalApp.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val lib by LibraryStore.state.collectAsState()
    val local by com.dopdop.bopdop.data.local.LocalMusic.tracks.collectAsState()
    var allowed by remember { mutableStateOf(com.dopdop.bopdop.data.local.LocalMusic.hasPermission(ctx)) }
    val cached by produceState(emptyList<com.dopdop.bopdop.data.model.Track>(), lib.history.size) {
        value = com.dopdop.bopdop.playback.CachedSongs.list(ctx)
    }
    LaunchedEffect(allowed, lib.settings.localFolder, lib.settings.filterNonMusic) { if (allowed) com.dopdop.bopdop.data.local.LocalMusic.refresh(ctx) }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        if (granted) app.openLocal("device") else app.toast("Allow music access to play songs on this phone")
    }
    Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Shortcut(
            "On this device", if (!allowed) "Tap to allow" else songs(local.size), Icons.Rounded.PhoneAndroid, Modifier.weight(1f),
            art = { m -> GradientArt(m, Icons.Rounded.PhoneAndroid, listOf(Color(0xFFE0B98A), Color(0xFFB07A45))) },
        ) { if (allowed) app.openLocal("device") else ask.launch(com.dopdop.bopdop.data.local.LocalMusic.permission) }
        Shortcut(
            "Cached songs", if (cached.isEmpty()) "Plays offline" else songs(cached.size), Icons.Rounded.OfflinePin, Modifier.weight(1f),
            art = { m -> MiniCollage(cached.map { it.artwork }.distinct().take(4), m) },
        ) { app.openLocal("cached") }
    }
}

/** Liked Songs as a hero: fanned covers over their own colour wash, play + shuffle. */
@Composable
private fun LikedHero() {
    val app = LocalApp.current
    val lib by LibraryStore.state.collectAsState()
    val liked = lib.liked
    val arts = liked.map { it.artwork }.distinct().take(3)
    val palette = rememberArtPalette(arts.firstOrNull())
    val shape = RoundedCornerShape(36.dp)
    val bg = if (arts.isEmpty()) listOf(Color(0xFFB4C8E4), Color(0xFF7E95C2)) else listOf(palette.wash, palette.washDeep)
    Box(
        Modifier.padding(horizontal = 20.dp, vertical = 14.dp).fillMaxWidth().height(232.dp)
            .bounceClick(0.985f) { app.openLocal("liked") }
            .softShadow(shape, 20.dp, alpha = 0.2f, dy = 6.dp)
            .clip(shape)
            .background(Brush.linearGradient(bg))
            .glassEdges(shape, 0.9f, true),
    ) {
        // Fanned record stack on the right.
        arts.reversed().forEachIndexed { k, a ->
            val i = arts.size - 1 - k
            Artwork(
                a,
                Modifier.size(150.dp).align(Alignment.CenterEnd).offset(x = (10 - i * 34).dp, y = (i * 6).dp)
                    .graphicsLayer { rotationZ = 10f - i * 9f },
                corner = 22.dp, size = 400, elevation = 16.dp,
            )
        }
        if (arts.isEmpty()) Icon(Icons.Rounded.Favorite, null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(150.dp).align(Alignment.CenterEnd).offset(x = (-10).dp))
        Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
            Box(Modifier.size(38.dp).softShadow(CircleShape, 8.dp, alpha = 0.12f, dy = 2.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.94f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Favorite, null, tint = Bop.Like, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("Liked Songs", style = BopType.Display, color = Color.White)
            Text(
                if (liked.isEmpty()) "Tap ♥ on anything you love" else "${songs(liked.size)} · ${formatTotal(liked.sumOf { it.durationSec })}",
                style = BopType.Body, color = Color.White.copy(alpha = 0.85f),
            )
            if (liked.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroButton(Icons.Rounded.PlayArrow, "Play", filled = true) { PlayerConnection.play(liked, 0, "Liked Songs") }
                    HeroButton(Icons.Rounded.Shuffle, "Shuffle", filled = false) { PlayerConnection.play(liked, 0, "Liked Songs", shuffle = true) }
                }
            }
        }
    }
}

@Composable
private fun HeroButton(icon: ImageVector, label: String, filled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.height(40.dp).bounceClick(0.92f, onClick = onClick)
            .then(if (filled) Modifier.clip(shape).background(Color.White) else Modifier.glassLite(shape, alpha = 0.26f))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (filled) Bop.Ink else Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = BopType.BodyStrong, color = if (filled) Bop.Ink else Color.White)
    }
}

@Composable
private fun Shortcut(title: String, sub: String, icon: ImageVector, modifier: Modifier, art: @Composable (Modifier) -> Unit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier.height(86.dp).bounceClick(0.97f, onClick = onClick).glassLite(shape, alpha = 0.6f, shadow = 12.dp).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        art(Modifier.size(64.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Icon(icon, null, tint = Bop.Text2, modifier = Modifier.size(16.dp))
            Text(title, style = BopType.BodyStrong, color = Bop.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = BopType.Caption, color = Bop.Text2, maxLines = 1)
        }
    }
}

@Composable
private fun MiniCollage(arts: List<String>, modifier: Modifier) {
    if (arts.isEmpty()) {
        Box(modifier.clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(Color(0xFFADC27A), Color(0xFF6F8B3C)))), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.History, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
    } else Collage(arts, modifier, 18.dp)
}

/** Overlapping circular artist avatars. */
@Composable
private fun AvatarStack(arts: List<String>, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        if (arts.isEmpty()) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFB4C8E4), Color(0xFF7E95C2)))), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Person, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
        arts.forEachIndexed { i, a ->
            Artwork(a, Modifier.offset(x = (i * 12).dp).size(40.dp).border(2.dp, Color.White, CircleShape), circle = true, size = 160)
        }
    }
}

/** Square collage with a frosted caption bar floating inside the bottom edge. */
@Composable
private fun PlaylistCard(p: UserPlaylist) {
    val app = LocalApp.current
    CaptionCard(
        title = p.name, sub = songs(p.tracks.size),
        onLong = { app.sheet = Sheet.CardOptions(Card(p.cardId, CardKind.PLAYLIST, p.name, "Your playlist", p.artwork)) },
        onClick = { app.openLocal(p.id) },
    ) { m -> Collage(p.tracks.map { it.artwork }.distinct(), m, 0.dp) }
}

@Composable
private fun SavedCard(c: Card) {
    val app = LocalApp.current
    CaptionCard(c.title, c.subtitle.ifBlank { if (c.kind == CardKind.ALBUM) "Album" else "Playlist" }, onLong = { app.sheet = Sheet.CardOptions(c) }, onClick = { app.open(c) }) { m ->
        Artwork(c.artwork, m, corner = 0.dp, size = 500)
    }
}

@Composable
private fun CaptionCard(title: String, sub: String, onLong: () -> Unit, onClick: () -> Unit, art: @Composable (Modifier) -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).bounceClick(0.97f, onLongClick = onLong, onClick = onClick)
            .softShadow(shape, 16.dp, alpha = 0.18f, dy = 5.dp).clip(shape),
    ) {
        art(Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f))))
        Column(
            Modifier.align(Alignment.BottomCenter).padding(8.dp).fillMaxWidth()
                .glassLite(RoundedCornerShape(20.dp), tint = Color.Black, alpha = 0.28f).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(title, style = BopType.BodyStrong, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = BopType.Caption, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun NewPlaylistCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier.fillMaxWidth().aspectRatio(1f).bounceClick(0.97f, onClick = onClick).glassLite(shape, alpha = 0.45f, shadow = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(Bop.Ink), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("New playlist", style = BopType.BodyStrong, color = Bop.Text)
        Text("Make it yours", style = BopType.Caption, color = Bop.Text2)
    }
}

/** Spotify-Wrapped-style entry: your top songs and listening trend. */
@Composable
private fun ReplayBanner(onClick: () -> Unit) {
    val stats by PlayStats.state.collectAsState()
    val top = remember(stats.events.size) {
        stats.events.takeLast(400).groupingBy { it.id }.eachCount().entries.sortedByDescending { it.value }
            .mapNotNull { stats.tracks[it.key]?.artwork }.distinct().take(3)
    }
    val shape = RoundedCornerShape(30.dp)
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().height(96.dp)
            .bounceClick(0.98f, onClick = onClick)
            .softShadow(shape, 16.dp, alpha = 0.22f, dy = 5.dp).clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF2443C4), Color(0xFF7A45CC), Color(0xFFE2678F))))
            .glassEdges(shape, 0.9f)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("REPLAY", style = BopType.Micro, color = Color.White.copy(alpha = 0.75f))
            Text("Your top songs & trends", style = BopType.Headline, color = Color.White, maxLines = 1)
            Text(if (stats.events.isEmpty()) "Starts counting as you listen" else "${stats.events.size} plays counted", style = BopType.Caption, color = Color.White.copy(alpha = 0.8f))
        }
        Box(Modifier.size(width = 104.dp, height = 70.dp)) {
            top.reversed().forEachIndexed { k, a ->
                val i = top.size - 1 - k
                Artwork(a, Modifier.size(60.dp).align(Alignment.CenterEnd).offset(x = (-i * 20).dp).graphicsLayer { rotationZ = 8f - i * 8f }, corner = 14.dp, size = 200, elevation = 10.dp)
            }
            if (top.isEmpty()) Icon(Icons.Rounded.Insights, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(52.dp).align(Alignment.CenterEnd))
        }
    }
}

/** The signed-in account's YouTube Music library: Liked Music + their playlists. */
@Composable
private fun YtMusicLibrary() {
    val app = LocalApp.current
    val load = rememberLoad("ytm:library", 5 * 60_000L) { YtMusic.libraryPlaylists() }
    val liked = Card("yt:LM", CardKind.PLAYLIST, "Liked Music", "YouTube Music", "")
    val cards = listOf(liked) + ((load.state as? Load.Ready)?.value.orEmpty())
    Column {
        SectionHeader("From YouTube Music", "Your account")
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(cards, key = { it.id }) { c ->
                if (c.id == liked.id) Column(Modifier.width(140.dp).bounceClick { app.open(c) }) {
                    GradientArt(Modifier.size(140.dp), Icons.Rounded.ThumbUp, listOf(Color(0xFFE2678F), Color(0xFF7A45CC)))
                    Spacer(Modifier.height(9.dp))
                    Text(c.title, style = BopType.BodyStrong, color = Bop.Text, maxLines = 1)
                    Text(c.subtitle, style = BopType.Caption, color = Bop.Text2, maxLines = 1)
                } else CardTile(c, width = 140.dp, onLongClick = { app.sheet = Sheet.CardOptions(c) }) { app.open(c) }
            }
        }
    }
}
