package com.dopdop.bopdop.ui.screens

import com.dopdop.bopdop.data.SmartPlaylists
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.ThumbDown
import kotlin.math.roundToInt
import com.dopdop.bopdop.ui.glass.glassLite
import com.dopdop.bopdop.data.UserPlaylist
import com.dopdop.bopdop.data.PlaylistSort
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.animation.core.spring
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCode2
import com.dopdop.bopdop.data.share.SharePayload
import com.dopdop.bopdop.ui.ShareRequest
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Shuffle
import com.dopdop.bopdop.ui.festive.ThemedIcon as Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.MusicRepository
import com.dopdop.bopdop.data.model.Card
import com.dopdop.bopdop.data.model.CardKind
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.model.formatTotal
import com.dopdop.bopdop.data.model.songs
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.ui.LocalApp
import com.dopdop.bopdop.ui.LocalChromePadding
import com.dopdop.bopdop.ui.Sheet
import com.dopdop.bopdop.ui.components.Artwork
import com.dopdop.bopdop.ui.components.ErrorState
import com.dopdop.bopdop.ui.components.GlassIconButton
import com.dopdop.bopdop.ui.components.Load
import com.dopdop.bopdop.ui.components.PillButton
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.components.rememberLoad
import com.dopdop.bopdop.ui.components.shimmer
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** Shared album / playlist page: parallax blurred-art backdrop, hero artwork, actions, tracks. */
@Composable
fun CollectionLayout(
    title: String,
    subtitle: String,
    meta: String,
    artUrl: String?,
    tracks: List<Track>?,
    context: String,
    numbered: Boolean,
    playlistId: String? = null,
    art: (@Composable (Modifier) -> Unit)? = null,
    onSubtitle: (() -> Unit)? = null,
    saved: Boolean? = null,
    onSave: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    loading: Boolean = false,
    error: String? = null,
    onRetry: () -> Unit = {},
    empty: String = "Nothing here yet",
    /** Shown above the songs (a playlist's sort and edit buttons). */
    toolbar: (@Composable () -> Unit)? = null,
    /** Arranging: rows get drag handles ([onMove]) and remove buttons ([onRemove]) instead of playing. */
    editing: Boolean = false,
    onMove: ((Int, Int) -> Unit)? = null,
    onRemove: ((Int) -> Unit)? = null,
) {
    val app = LocalApp.current
    val drag = remember { ReorderDrag() }
    val haze = rememberHazeState()
    val list = rememberLazyListState()
    val current by PlayerConnection.current.collectAsState()
    val scroll by remember { derivedStateOf { if (list.firstVisibleItemIndex > 0) 2000f else list.firstVisibleItemScrollOffset.toFloat() } }
    val titleAlpha by animateFloatAsState(if (scroll > 700f) 1f else 0f, label = "ta")

    Box(Modifier.fillMaxSize()) {
        // Backdrop: the artwork, massively blurred, drifting slower than the content (parallax).
        if (!artUrl.isNullOrBlank()) {
            com.dopdop.bopdop.ui.components.BlurredArtwork(
                artUrl,
                Modifier.fillMaxWidth().height(520.dp)
                    .graphicsLayer {
                        translationY = -scroll * 0.35f
                        alpha = (1f - scroll / 1400f).coerceIn(0f, 0.75f)
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(Brush.verticalGradient(0f to Color.Black, 0.55f to Color.Black.copy(alpha = 0.7f), 1f to Color.Transparent), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                    }
            )
            Box(Modifier.fillMaxWidth().height(522.dp).background(Brush.verticalGradient(listOf(Bop.Pearl.copy(alpha = 0.10f), Bop.Pearl.copy(alpha = 0.45f), Color.Transparent))))
        }
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().hazeSource(haze), contentPadding = LocalChromePadding.current) {
            item(key = "hero") {
                Column(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(top = 56.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val m = Modifier.size(248.dp).graphicsLayer {
                        val s = 1f - (scroll / 2600f).coerceIn(0f, 0.15f)
                        scaleX = s; scaleY = s; translationY = scroll * 0.18f
                    }
                    if (art != null) art(m) else Artwork(artUrl, m, corner = 18.dp, size = 800, elevation = 28.dp)
                    Spacer(Modifier.height(22.dp))
                    com.dopdop.bopdop.ui.components.MarqueeText(title, BopType.Display, Bop.Text, Modifier.fillMaxWidth(), center = true)
                    if (subtitle.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            subtitle, style = BopType.Headline, color = if (onSubtitle != null) Bop.Text else Bop.Text2,
                            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = if (onSubtitle != null) Modifier.bounceClick(onClick = onSubtitle) else Modifier,
                        )
                    }
                    if (meta.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(meta, style = BopType.Caption, color = Bop.Text3, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (saved != null && onSave != null) GlassIconButton(if (saved) Icons.Rounded.Check else Icons.Rounded.Add, "Save", size = 46.dp, active = saved, onClick = onSave)
                        PillButton("Play", Icons.Rounded.PlayArrow, Modifier.width(112.dp)) { tracks?.let { PlayerConnection.play(it, 0, context) } }
                        PillButton("Shuffle", Icons.Rounded.Shuffle, Modifier.width(120.dp), filled = false) { tracks?.let { PlayerConnection.play(it, 0, context, shuffle = true) } }
                        if (onShare != null) GlassIconButton(Icons.Rounded.QrCode2, "Share code", size = 46.dp, onClick = onShare)
                        if (onMore != null) GlassIconButton(Icons.Rounded.MoreHoriz, "More", size = 46.dp, onClick = onMore)
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }
            when {
                error != null -> item { ErrorState(error, onRetry = onRetry) }
                loading || tracks == null -> items(8) { RowSkeleton() }
                tracks.isEmpty() -> item {
                    androidx.compose.foundation.layout.Column {
                        if (Bop.halloween) com.dopdop.bopdop.ui.festive.HalloweenEmptyArt(Modifier.fillMaxWidth().padding(top = 28.dp).height(96.dp))
                        Text(empty, style = BopType.Body, color = Bop.Text2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(36.dp))
                    }
                }
                else -> {
                    if (toolbar != null) item(key = "toolbar") { toolbar() }
                    if (editing && onMove != null) reorderItems(tracks, drag, onMove, onRemove)
                    else trackItems(tracks, context, current?.id, numbered = numbered, playlistId = playlistId) { app.sheet = it }
                    item {
                        Text(
                            "${songs(tracks.size)} · ${formatTotal(tracks.sumOf { it.durationSec })}",
                            style = BopType.Caption, color = Bop.Text3, modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        )
                    }
                }
            }
        }
        TopBlurBar(haze, titleAlpha, title, showBack = true, onBack = { app.nav.popBackStack() })
    }
}


@Composable
fun RowSkeleton() {
    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(50.dp).shimmer(8.dp)); Spacer(Modifier.width(14.dp))
        Column { Box(Modifier.size(190.dp, 14.dp).shimmer(4.dp)); Spacer(Modifier.height(6.dp)); Box(Modifier.size(120.dp, 12.dp).shimmer(4.dp)) }
    }
}

@Composable
fun CollectionScreen(id: String, kind: String, title: String, sub: String, art: String) {
    val app = LocalApp.current
    val lib by LibraryStore.state.collectAsState()
    val k = runCatching { CardKind.valueOf(kind) }.getOrDefault(CardKind.PLAYLIST)
    val card = Card(id, k, title, sub, art)
    val load = rememberLoad("col:$id") { MusicRepository.collection(card) }
    val c = (load.state as? Load.Ready)?.value
    val tracks = c?.tracks
    val isAlbum = k == CardKind.ALBUM
    val artistId = tracks?.firstOrNull()?.artistId
    val saveCard = card.copy(title = c?.title ?: title, subtitle = c?.subtitle ?: sub, artwork = c?.artwork?.ifBlank { null } ?: art)
    CollectionLayout(
        title = c?.title ?: title,
        subtitle = c?.subtitle ?: sub,
        meta = listOfNotNull(if (isAlbum) "Album" else "Playlist", c?.year?.ifBlank { null }, if (id.startsWith("sv:")) "320 kbps" else null).joinToString(" · "),
        artUrl = c?.artwork?.ifBlank { null } ?: art,
        tracks = tracks,
        context = c?.title ?: title,
        numbered = isAlbum,
        onSubtitle = if (isAlbum && artistId != null) ({ app.openArtist(artistId, c?.subtitle ?: sub) }) else null,
        saved = id in lib.savedIds,
        onSave = { app.toast(if (LibraryStore.toggleSaved(saveCard)) "Added to Library" else "Removed from Library") },
        onMore = { app.sheet = Sheet.CardOptions(saveCard) },
        onShare = { app.shareQr(ShareRequest(SharePayload.Collection(saveCard), saveCard.title, if (isAlbum) "Album · ${saveCard.subtitle}" else "Playlist", saveCard.artwork)) },
        loading = load.state is Load.Loading,
        error = (load.state as? Load.Failed)?.message,
        onRetry = load.retry,
    )
}

/** Liked songs, history, or a user playlist. */
@Composable
fun LocalCollectionScreen(id: String) {
    val app = LocalApp.current
    val lib by LibraryStore.state.collectAsState()
    if (id.startsWith("mix:")) { MixCollection(id.removePrefix("mix:")); return }
    if (id.startsWith("smart:")) { SmartCollection(id.removePrefix("smart:")); return }
    when (id) {
        "liked" -> CollectionLayout(
            title = "Liked Songs", subtitle = "", meta = songs(lib.liked.size), artUrl = lib.liked.firstOrNull()?.artwork,
            tracks = lib.liked, context = "Liked Songs", numbered = false,
            art = { m -> GradientArt(m, Icons.Rounded.Favorite, listOf(Color(0xFFAFC4E0), Color(0xFF7E95C2))) },
            empty = "Songs you ♥ land here.",
        )
        "history" -> CollectionLayout(
            title = "Recently Played", subtitle = "", meta = songs(lib.history.size), artUrl = lib.history.firstOrNull()?.artwork,
            tracks = lib.history, context = "Recently Played", numbered = false,
            art = { m -> GradientArt(m, Icons.Rounded.History, listOf(Color(0xFFADC27A), Color(0xFF6F8B3C))) },
            onMore = { LibraryStore.clearHistory(); app.toast("History cleared") },
            empty = "Start listening — your history shows up here.",
        )
        "disliked" -> CollectionLayout(
            title = "Not for me", subtitle = "Kept out of radio, autoplay and mixes", meta = songs(lib.disliked.size),
            artUrl = lib.disliked.firstOrNull()?.artwork, tracks = lib.disliked, context = "Not for me", numbered = false,
            art = { m -> GradientArt(m, Icons.Rounded.ThumbDown, listOf(Color(0xFFB4B7C2), Color(0xFF6B6F7E))) },
            empty = "Nothing here. Mark a song “Not for me” from its ⋯ menu and it won't come up in radio or mixes.",
        )
        "device" -> {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val tracks by com.dopdop.bopdop.data.local.LocalMusic.tracks.collectAsState()
            LaunchedEffect(lib.settings.localFolder, lib.settings.filterNonMusic) { com.dopdop.bopdop.data.local.LocalMusic.refresh(ctx) }
            CollectionLayout(
                title = "On this device", subtitle = lib.settings.localFolder ?: "All music folders", meta = songs(tracks.size),
                artUrl = tracks.firstOrNull()?.artwork, tracks = tracks, context = "On this device", numbered = false,
                art = { m -> GradientArt(m, Icons.Rounded.PhoneAndroid, listOf(Color(0xFFE0B98A), Color(0xFFB07A45))) },
                empty = "No music found on this phone. Pick a folder in Settings › Local music.",
            )
        }
        "downloads" -> {
            val tracks by com.dopdop.bopdop.playback.Downloads.tracks.collectAsState()
            CollectionLayout(
                title = "Downloads", subtitle = "Plays with no internet", meta = songs(tracks.size),
                artUrl = tracks.firstOrNull()?.artwork, tracks = tracks, context = "Downloads", numbered = false,
                art = { m -> GradientArt(m, Icons.Rounded.Download, listOf(Color(0xFF9DB0F2), Color(0xFF4B5FC0))) },
                empty = "Download songs, albums and playlists from their ⋯ menu to play them offline.",
            )
        }
        "cached" -> {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val tracks by produceState(emptyList<com.dopdop.bopdop.data.model.Track>(), lib.history.size) {
                value = com.dopdop.bopdop.playback.CachedSongs.list(ctx)
            }
            CollectionLayout(
                title = "Cached songs", subtitle = "Plays offline", meta = songs(tracks.size),
                artUrl = tracks.firstOrNull()?.artwork, tracks = tracks, context = "Cached songs", numbered = false,
                art = { m -> GradientArt(m, Icons.Rounded.OfflinePin, listOf(Color(0xFF8FC7C2), Color(0xFF3F8A85))) },
                empty = "Songs you play in full are kept here and play without data.",
            )
        }
        else -> {
            val p = lib.playlists.firstOrNull { it.id == id }
            if (p == null) {
                Text("Playlist not found", style = BopType.Body, color = Bop.Text2, modifier = Modifier.statusBarsPad().padding(40.dp))
                return
            }
            var editing by remember(id) { mutableStateOf(false) }
            CollectionLayout(
                title = p.name, subtitle = "Your playlist",
                meta = if (p.tracks.isEmpty()) songs(0) else "${songs(p.tracks.size)} · ${formatTotal(p.tracks.sumOf { it.durationSec })}",
                artUrl = p.artwork, tracks = p.sorted, context = p.name, numbered = false, playlistId = p.id,
                toolbar = { PlaylistToolbar(p, editing) { editing = !editing } },
                editing = editing && p.sort == PlaylistSort.CUSTOM,
                onMove = { from, to -> LibraryStore.movePlaylistTrack(p.id, from, to) },
                onRemove = { i -> p.tracks.getOrNull(i)?.let { LibraryStore.removeFromPlaylist(p.id, it.id) } },
                art = { m -> Collage(p.tracks.map { it.artwork }.distinct(), m) },
                onMore = { app.sheet = Sheet.CardOptions(Card(p.cardId, CardKind.PLAYLIST, p.name, "Your playlist", p.artwork)) },
                onShare = if (p.tracks.isEmpty()) null else ({
                    app.shareQr(ShareRequest(SharePayload.Playlist(p.name, p.tracks.map { it.id }), p.name, songs(p.tracks.size), p.artwork))
                }),
                empty = "Add songs from any track’s ••• menu.",
            )
        }
    }
}

@Composable
private fun Modifier.statusBarsPad() = this.windowInsetsPadding(WindowInsets.statusBars)

/** Sort on the left; Edit (arrange and remove) on the right while the playlist is in its own order. */
@Composable
private fun PlaylistToolbar(p: UserPlaylist, editing: Boolean, onEdit: () -> Unit) {
    val app = LocalApp.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        ToolPill(Icons.Rounded.SwapVert, p.sort.label) { app.sheet = Sheet.SortPlaylist(p.id) }
        Spacer(Modifier.weight(1f))
        if (p.sort == PlaylistSort.CUSTOM) ToolPill(if (editing) Icons.Rounded.Check else Icons.Rounded.Edit, if (editing) "Done" else "Edit", on = editing, onClick = onEdit)
    }
}

@Composable
private fun ToolPill(icon: ImageVector, label: String, on: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.bounceClick(onClick = onClick).clip(RoundedCornerShape(50))
            .background(if (on) Bop.Ink else Bop.Veil.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (on) Bop.OnInk else Bop.Text, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = BopType.Caption, color = if (on) Bop.OnInk else Bop.Text)
    }
}

/** The row being dragged while arranging a playlist, and how far it has moved. */
private class ReorderDrag {
    var from by mutableIntStateOf(-1)
    var dy by mutableFloatStateOf(0f)
}

private val EDIT_ROW = 64.dp

/** Songs with a remove button and a drag handle; the rows in between slide aside to make room. */
private fun LazyListScope.reorderItems(tracks: List<Track>, drag: ReorderDrag, onMove: (Int, Int) -> Unit, onRemove: ((Int) -> Unit)?) {
    items(tracks.size, key = { "e$it:" + tracks[it].id }) { i ->
        val t = tracks[i]
        val rowPx = with(LocalDensity.current) { EDIT_ROW.toPx() }
        fun target() = (drag.from + (drag.dy / rowPx).roundToInt()).coerceIn(0, tracks.size - 1)
        val dragged = i == drag.from
        val to = if (drag.from >= 0) target() else -1
        val shift = when {
            drag.from < 0 || dragged -> 0f
            drag.from < to && i in (drag.from + 1)..to -> -rowPx
            drag.from > to && i in to until drag.from -> rowPx
            else -> 0f
        }
        val slide by animateFloatAsState(shift, spring(dampingRatio = 0.8f, stiffness = 600f), label = "slide")
        Row(
            Modifier.zIndex(if (dragged) 1f else 0f)
                .graphicsLayer { translationY = if (dragged) drag.dy else slide; scaleX = if (dragged) 1.02f else 1f; scaleY = scaleX }
                .fillMaxWidth().height(EDIT_ROW).padding(horizontal = 12.dp)
                .then(if (dragged) Modifier.glassLite(RoundedCornerShape(16.dp), alpha = 0.7f) else Modifier)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onRemove != null) {
                Box(Modifier.size(36.dp).clip(CircleShape).bounceClick(0.85f) { onRemove(i) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.RemoveCircle, "Remove", tint = Bop.Danger, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(4.dp))
            }
            Artwork(t.artwork, Modifier.size(46.dp), corner = 10.dp, size = 226)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, style = BopType.BodyStrong, color = Bop.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.artist, style = BopType.Caption, color = Bop.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier.size(44.dp).pointerInput(tracks.size, i) {
                    detectDragGestures(
                        onDragStart = { drag.from = i; drag.dy = 0f },
                        onDragEnd = {
                            val dest = target()
                            if (drag.from >= 0 && dest != drag.from) onMove(drag.from, dest)
                            drag.from = -1; drag.dy = 0f
                        },
                        onDragCancel = { drag.from = -1; drag.dy = 0f },
                    ) { change, d -> change.consume(); drag.dy += d.y }
                },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.DragHandle, "Drag to move", tint = Bop.Text2) }
        }
    }
}

@Composable
fun GradientArt(modifier: Modifier, icon: ImageVector, colors: List<Color>) {
    Box(
        modifier.clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.fillMaxSize(0.36f)) }
}

/** 2×2 artwork collage for user playlists. */
@Composable
fun Collage(arts: List<String>, modifier: Modifier, corner: androidx.compose.ui.unit.Dp = 18.dp) {
    if (arts.size < 4) {
        if (arts.isEmpty()) GradientArt(modifier, Icons.Rounded.QueueMusic, listOf(Color(0xFF3A3550), Color(0xFF1A1A26)))
        else Artwork(arts.first(), modifier, corner = corner, size = 600)
        return
    }
    Column(modifier.clip(RoundedCornerShape(corner))) {
        Row(Modifier.weight(1f)) { Artwork(arts[0], Modifier.weight(1f).fillMaxSize(), corner = 0.dp, size = 300); Artwork(arts[1], Modifier.weight(1f).fillMaxSize(), corner = 0.dp, size = 300) }
        Row(Modifier.weight(1f)) { Artwork(arts[2], Modifier.weight(1f).fillMaxSize(), corner = 0.dp, size = 300); Artwork(arts[3], Modifier.weight(1f).fillMaxSize(), corner = 0.dp, size = 300) }
    }
}

/** A playlist made from your listening (see [SmartPlaylists]); Discover is fetched when opened. */
@Composable
private fun SmartCollection(smartId: String) {
    val lib by LibraryStore.state.collectAsState()
    val stats by com.dopdop.bopdop.data.PlayStats.state.collectAsState()
    if (smartId == SmartPlaylists.DISCOVER) {
        val load = rememberLoad("smart:discover", 6 * 3600_000L) { SmartPlaylists.discover(stats, lib) }
        val p = (load.state as? Load.Ready)?.value
        CollectionLayout(
            title = "Discover", subtitle = p?.subtitle ?: "New to you",
            meta = p?.let { songs(it.tracks.size) + " · from your recent favourites" }.orEmpty(),
            artUrl = p?.artworks?.firstOrNull(), tracks = p?.tracks, context = "Discover", numbered = false,
            art = { m -> if (p != null && p.artworks.isNotEmpty()) Collage(p.artworks, m) else GradientArt(m, Icons.Rounded.Explore, DISCOVER_COLORS) },
            loading = load.state is Load.Loading, error = (load.state as? Load.Failed)?.message, onRetry = load.retry,
            empty = "Play a few songs first, and Discover finds more like them.",
        )
        return
    }
    val p = remember(stats.events.size, lib.liked, lib.disliked) { SmartPlaylists.build(stats, lib) }.firstOrNull { it.id == smartId }
    if (p == null) {
        Text("Not enough listening for this one yet", style = BopType.Body, color = Bop.Text2, modifier = Modifier.statusBarsPad().padding(40.dp))
        return
    }
    CollectionLayout(
        title = p.title, subtitle = p.subtitle, meta = songs(p.tracks.size) + " · updates as you listen",
        artUrl = p.artworks.firstOrNull(), tracks = p.tracks, context = p.title, numbered = false,
        art = { m -> Collage(p.artworks, m) },
    )
}

internal val DISCOVER_COLORS = listOf(Color(0xFF9DB0F2), Color(0xFF6A4FC0))

/** One of today's daily mixes. */
@Composable
private fun MixCollection(mixId: String) {
    val mixes by com.dopdop.bopdop.data.DailyMixes.mixes.collectAsState()
    val m = mixes.firstOrNull { it.id == mixId }
    if (m == null) {
        Text("This mix has been refreshed", style = BopType.Body, color = Bop.Text2, modifier = Modifier.statusBarsPad().padding(40.dp))
        return
    }
    CollectionLayout(
        title = m.title, subtitle = m.subtitle, meta = songs(m.tracks.size) + " · refreshed daily",
        artUrl = m.artworks.firstOrNull(), tracks = m.tracks, context = m.title, numbered = false,
        art = { mod -> Collage(m.artworks, mod) },
        empty = "This mix is empty.",
    )
}
