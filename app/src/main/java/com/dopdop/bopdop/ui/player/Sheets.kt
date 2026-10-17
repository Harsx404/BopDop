package com.dopdop.bopdop.ui.player

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.MusicRepository
import com.dopdop.bopdop.data.model.Card
import com.dopdop.bopdop.data.model.CardKind
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.model.songs
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.ui.AppState
import com.dopdop.bopdop.ui.Sheet
import com.dopdop.bopdop.ui.ShareRequest
import com.dopdop.bopdop.data.share.SharePayload
import androidx.compose.material.icons.rounded.QrCode2
import com.dopdop.bopdop.ui.components.Artwork
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.glass.LocalHaze
import com.dopdop.bopdop.ui.glass.glass
import com.dopdop.bopdop.ui.glass.glassLite
import com.dopdop.bopdop.ui.screens.Collage
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SheetHost(app: AppState) {
    val sheet = app.sheet
    val scrimA by androidx.compose.animation.core.animateFloatAsState(if (sheet != null) 0.45f else 0f, tween(260), label = "scrim")
    if (scrimA > 0.001f) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrimA))
                .clickable(remember { MutableInteractionSource() }, null) { app.sheet = null },
        )
    }
    // Keep the last sheet around while it animates out.
    var shown by remember { mutableStateOf<Sheet?>(null) }
    if (sheet != null) shown = sheet
    AnimatedVisibility(
        visible = sheet != null,
        enter = slideInVertically(spring(dampingRatio = 0.82f, stiffness = 420f)) { it } + fadeIn(),
        exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(220)),
        modifier = Modifier.fillMaxSize(),
    ) {
        val drag = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val overPlayer = app.player.value > 0.5f
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.ime)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .graphicsLayer { translationY = drag.value }
                    .glass(RoundedCornerShape(34.dp), state = if (overPlayer) null else LocalHaze.current, emphasis = if (overPlayer) 2.4f else 1.7f, shadow = 24.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(onDragEnd = {
                            scope.launch { if (drag.value > 160f) app.sheet = null; drag.animateTo(0f, spring(dampingRatio = 0.7f)) }
                        }) { change, d -> change.consume(); scope.launch { drag.snapTo((drag.value + d).coerceAtLeast(0f)) } }
                    }
                    .padding(bottom = 10.dp),
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(40.dp, 5.dp).clip(RoundedCornerShape(50)).background(Bop.Ink.copy(alpha = 0.18f)))
                }
                when (val s = shown) {
                    is Sheet.TrackOptions -> TrackOptions(app, s.track, s.playlistId)
                    is Sheet.CardOptions -> CardOptions(app, s.card)
                    is Sheet.AddToPlaylist -> AddToPlaylist(app, s.tracks)
                    is Sheet.NewPlaylist -> NameInput(app, "New playlist", "Create", "") { name ->
                        val p = LibraryStore.createPlaylist(name, s.tracks)
                        app.toast(if (s.tracks.isEmpty()) "Created “${p.name}”" else "Added to “${p.name}”")
                    }
                    is Sheet.RenamePlaylist -> NameInput(app, "Rename playlist", "Save", s.name) { name -> LibraryStore.renamePlaylist(s.id, name) }
                    Sheet.SleepTimer -> SleepTimer(app)
                    Sheet.AudioPipeline -> AudioPipelineSheet()
                    Sheet.ImportPlaylist -> ImportPlaylistSheet(app)
                    null -> {}
                }
            }
        }
    }
}

@Composable
private fun Header(art: @Composable () -> Unit, title: String, sub: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        art()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            com.dopdop.bopdop.ui.components.MarqueeText(title, BopType.Headline, Bop.Text)
            Text(sub, style = BopType.Caption, color = Bop.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Box(Modifier.padding(horizontal = 18.dp, vertical = 4.dp).fillMaxWidth().height(0.5.dp).background(Bop.Ink.copy(alpha = 0.08f)))
}

@Composable
private fun Action(icon: ImageVector, label: String, tint: Color = Bop.Text, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().bounceClick(0.98f, onClick = onClick).padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(label, style = BopType.Body, color = tint)
    }
}

private fun share(ctx: android.content.Context, text: String) {
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun trackLink(t: Track) = if (t.id.startsWith("yt:")) "https://music.youtube.com/watch?v=${t.rawId}"
    else "https://www.jiosaavn.com/search/${android.net.Uri.encode("${t.title} ${t.artist}")}"

@Composable
private fun ColumnScope.TrackOptions(app: AppState, t: Track, playlistId: String?) {
    val ctx = LocalContext.current
    val lib by LibraryStore.state.collectAsState()
    val liked = t.id in lib.likedIds
    val close = { app.sheet = null }
    Header({ Artwork(t.artwork, Modifier.size(56.dp), corner = 10.dp, size = 226) }, t.title, listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString(" · "))
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Action(Icons.Rounded.SkipNext, "Play next") { PlayerConnection.playNext(t); app.toast("Playing next"); close() }
        Action(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") { PlayerConnection.addToQueue(listOf(t)); app.toast("Added to queue"); close() }
        Action(Icons.Rounded.Radio, "Start radio") { PlayerConnection.startRadio(t); close() }
        Action(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) "Remove from Liked" else "Like", if (liked) Color(0xFFFF4D7E) else Bop.Text) {
            LibraryStore.toggleLike(t); close()
        }
        Action(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist…") { app.sheet = Sheet.AddToPlaylist(listOf(t)) }
        if (playlistId != null) Action(Icons.Rounded.RemoveCircleOutline, "Remove from this playlist") { LibraryStore.removeFromPlaylist(playlistId, t.id); close() }
        t.albumId?.let { id -> Action(Icons.Rounded.Album, "Go to album") { close(); app.collapsePlayer(); app.openAlbum(id, t.album, t.artwork) } }
        t.artistId?.let { id -> Action(Icons.Rounded.Person, "Go to artist") { close(); app.collapsePlayer(); app.openArtist(id, t.artist.split(",").first()) } }
        Action(Icons.Rounded.Share, "Share") { share(ctx, "${t.title} — ${t.artist}\n${trackLink(t)}"); close() }
    }
}

@Composable
private fun ColumnScope.CardOptions(app: AppState, c: Card) {
    val ctx = LocalContext.current
    val lib by LibraryStore.state.collectAsState()
    val close = { app.sheet = null }
    val local = c.id.startsWith("local:")
    val localPl = if (local) lib.playlists.firstOrNull { it.cardId == c.id } else null
    suspend fun tracks(): List<Track> = when {
        localPl != null -> localPl.tracks
        c.kind == CardKind.ARTIST -> MusicRepository.artist(c.id).topSongs
        c.kind == CardKind.STATION || c.kind == CardKind.MOOD -> MusicRepository.station(c)
        else -> MusicRepository.collection(c).tracks
    }
    fun withTracks(block: (List<Track>) -> Unit) = app.scope.launch {
        val ts = runCatching { tracks() }.getOrDefault(emptyList())
        if (ts.isEmpty()) app.toast("Nothing to play") else block(ts)
    }
    Header({
        if (localPl != null) Collage(localPl.tracks.map { it.artwork }.distinct(), Modifier.size(56.dp), 10.dp)
        else Artwork(c.artwork, Modifier.size(56.dp), corner = 10.dp, circle = c.kind == CardKind.ARTIST, size = 226)
    }, c.title, c.subtitle.ifBlank { c.kind.name.lowercase().replaceFirstChar { it.uppercase() } })
    Action(Icons.Rounded.PlayArrow, "Play") { withTracks { PlayerConnection.play(it, 0, c.title) }; close() }
    Action(Icons.Rounded.Shuffle, "Shuffle") { withTracks { PlayerConnection.play(it, 0, c.title, shuffle = true) }; close() }
    Action(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") { withTracks { PlayerConnection.addToQueue(it); app.toast("Added ${it.size} songs to queue") }; close() }
    if (localPl != null && localPl.tracks.isNotEmpty()) Action(Icons.Rounded.QrCode2, "Share as code") {
        close(); app.shareQr(ShareRequest(SharePayload.Playlist(localPl.name, localPl.tracks.map { it.id }), localPl.name, songs(localPl.tracks.size), localPl.artwork))
    }
    if (!local && c.kind in setOf(CardKind.ALBUM, CardKind.PLAYLIST, CardKind.ARTIST)) Action(Icons.Rounded.QrCode2, "Share as code") {
        close(); app.shareQr(ShareRequest(SharePayload.Collection(c), c.title, c.subtitle.ifBlank { c.kind.name.lowercase().replaceFirstChar { it.uppercase() } }, c.artwork))
    }
    if (localPl != null) {
        Action(Icons.Rounded.Edit, "Rename") { app.sheet = Sheet.RenamePlaylist(localPl.id, localPl.name) }
        Action(Icons.Rounded.Delete, "Delete playlist", Color(0xFFFF6B8B)) {
            LibraryStore.deletePlaylist(localPl.id); close(); app.nav.popBackStack(); app.toast("Playlist deleted")
        }
    } else {
        val saved = c.id in lib.savedIds
        Action(if (saved) Icons.Rounded.BookmarkRemove else Icons.Rounded.BookmarkAdd, if (saved) "Remove from Library" else "Add to Library") {
            app.toast(if (LibraryStore.toggleSaved(c)) "Added to Library" else "Removed from Library"); close()
        }
        Action(Icons.Rounded.Share, "Share") {
            val link = if (c.id.startsWith("yt:")) when (c.kind) {
                CardKind.ARTIST -> "https://music.youtube.com/channel/${c.rawId}"
                CardKind.ALBUM -> "https://music.youtube.com/browse/${c.rawId}"
                else -> "https://music.youtube.com/playlist?list=${c.rawId}"
            } else "https://www.jiosaavn.com/search/${android.net.Uri.encode(c.title)}"
            share(ctx, "${c.title}\n$link"); close()
        }
    }
}

@Composable
private fun ColumnScope.AddToPlaylist(app: AppState, tracks: List<Track>) {
    val lib by LibraryStore.state.collectAsState()
    Text("Add to playlist", style = BopType.Title, color = Bop.Text, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Action(Icons.Rounded.Add, "New playlist") { app.sheet = Sheet.NewPlaylist(tracks) }
        lib.playlists.forEach { p ->
            Row(
                Modifier.fillMaxWidth().bounceClick(0.98f) {
                    LibraryStore.addToPlaylist(p.id, tracks); app.toast("Added to “${p.name}”"); app.sheet = null
                }.padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Collage(p.tracks.map { it.artwork }.distinct(), Modifier.size(48.dp), 8.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(p.name, style = BopType.BodyStrong, color = Bop.Text)
                    Text(songs(p.tracks.size), style = BopType.Caption, color = Bop.Text2)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.NameInput(app: AppState, title: String, cta: String, initial: String, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(250); runCatching { focus.requestFocus() } }
    val submit = { if (name.isNotBlank()) { onDone(name.trim()); app.sheet = null } }
    Text(title, style = BopType.Title, color = Bop.Text, modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp))
    Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(54.dp).glassLite(RoundedCornerShape(16.dp), alpha = 0.6f).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
        if (name.isEmpty()) Text("Playlist name", style = BopType.Body, color = Bop.Text3)
        BasicTextField(
            name, { name = it.take(60) }, singleLine = true,
            textStyle = BopType.Headline.copy(color = Bop.Text), cursorBrush = SolidColor(Bop.Ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
    Box(
        Modifier.padding(18.dp).fillMaxWidth().height(50.dp).bounceClick { submit() }.clip(RoundedCornerShape(50))
            .background(if (name.isBlank()) Bop.Ink.copy(alpha = 0.25f) else Bop.Ink),
        contentAlignment = Alignment.Center,
    ) { Text(cta, style = BopType.BodyStrong, color = Color.White) }
}

@Composable
private fun ColumnScope.SleepTimer(app: AppState) {
    val at by PlayerConnection.sleepAt.collectAsState()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    Row(Modifier.padding(horizontal = 22.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Bedtime, null, tint = Bop.Text)
        Spacer(Modifier.width(10.dp))
        Text("Sleep timer", style = BopType.Title, color = Bop.Text, modifier = Modifier.weight(1f))
        at?.let {
            Text(
                if (it == Long.MAX_VALUE) "End of song" else "%d:%02d left".format(((it - now) / 60000).coerceAtLeast(0), ((it - now) / 1000 % 60).coerceAtLeast(0)),
                style = BopType.BodyStrong, color = Bop.Gold,
            )
        }
    }
    val opts = listOf(5, 10, 15, 30, 45, 60, 90)
    Column {
        opts.chunked(4).forEach { row ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { m ->
                    Box(
                        Modifier.weight(1f).height(50.dp).bounceClick { PlayerConnection.sleepIn(m); app.toast("Stopping in $m min"); app.sheet = null }
                            .glassLite(RoundedCornerShape(16.dp), alpha = 0.6f),
                        contentAlignment = Alignment.Center,
                    ) { Text("$m min", style = BopType.BodyStrong, color = Bop.Text) }
                }
                if (row.size < 4) Box(
                    Modifier.weight(1f).height(50.dp).bounceClick { PlayerConnection.sleepIn(0); app.toast("Stopping after this song"); app.sheet = null }
                        .glassLite(RoundedCornerShape(16.dp), alpha = 0.6f),
                    contentAlignment = Alignment.Center,
                ) { Text("End of song", style = BopType.Caption, color = Bop.Text) }
            }
        }
    }
    if (at != null) Action(Icons.Rounded.TimerOff, "Turn off timer", Color(0xFFFF6B8B)) { PlayerConnection.cancelSleep(); app.sheet = null }
}

@Composable
fun ToastHost(app: AppState) {
    val toast = app.toast
    var visible by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) {
        val t = toast ?: return@LaunchedEffect
        visible = t.second
        delay(2200)
        visible = null
    }
    Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = visible != null,
            enter = slideInVertically(spring(dampingRatio = 0.6f, stiffness = 500f)) { -it * 2 } + fadeIn(),
            exit = slideOutVertically(tween(220)) { -it * 2 } + fadeOut(),
        ) {
            var last by remember { mutableStateOf("") }
            visible?.let { last = it }
            Text(
                last, style = BopType.BodyStrong, color = Bop.Text,
                modifier = Modifier.glass(RoundedCornerShape(50), emphasis = 1.6f).padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

/** The live signal path, source to speaker, refreshed while open. */
@Composable
private fun ColumnScope.AudioPipelineSheet() {
    val ctx = LocalContext.current
    val info by com.dopdop.bopdop.playback.AudioPipeline.info.collectAsState()
    val current by PlayerConnection.current.collectAsState()
    val quality by com.dopdop.bopdop.playback.StreamResolver.quality.collectAsState()
    val settings = LibraryStore.state.collectAsState().value.settings
    val fx by com.dopdop.bopdop.playback.AudioFx.state.collectAsState()
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(500); tick++ } }
    val leveler = com.dopdop.bopdop.playback.AudioPipeline.leveler
    val out = remember(tick) { com.dopdop.bopdop.playback.AudioPipeline.output(ctx) }
    fun khz(hz: Int) = if (hz <= 0) "—" else "%s kHz".format(if (hz % 1000 == 0) "${hz / 1000}" else "%.1f".format(hz / 1000f))
    val source = when {
        current?.source == com.dopdop.bopdop.data.model.Source.LOCAL -> "This phone"
        else -> current?.let { quality[it.id]?.via } ?: "—"
    }
    Text("Audio pipeline", style = BopType.Title, color = Bop.Text, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
    Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 12.dp)) {
        PipeStage("Source", listOf(
            "From" to source, "Format" to info.codec, "Sample rate" to khz(info.sampleRate),
            "Bitrate" to if (info.bitrateKbps > 0) "${info.bitrateKbps} kbps" else (current?.let { quality[it.id]?.kbps?.takeIf { k -> k > 0 }?.let { k -> "$k kbps" } } ?: "—"),
            "Channels" to when (info.channels) { 0 -> "—"; 1 -> "Mono"; 2 -> "Stereo"; else -> "${info.channels} ch" },
        ))
        PipeStage("Decoder", listOf("Decoder" to info.decoder))
        PipeStage("Processing", listOf(
            "Normalization" to when {
                !settings.normalize -> "Off"
                leveler == null -> "On"
                else -> "%+.1f dB (track at %.0f dBFS)".format(leveler.gainDb, leveler.measuredDb.coerceAtLeast(-99f))
            },
            "Skip silence" to if (settings.skipSilence) "On" else "Off",
            "Equalizer" to if (fx.enabled) fx.preset else "Off",
            "Spatial" to if (fx.enabled && fx.spatial > 0.01f) "${(fx.spatial * 100).toInt()}%" else "Off",
            "Automix" to if (settings.crossfadeSec > 0) "${settings.crossfadeSec} s crossfade" else "Off",
        ))
        PipeStage("Output", listOf(
            "Device" to out.name, "Route" to out.route,
            "AudioTrack" to "${info.sinkEncoding} · ${khz(info.sinkRate)}",
            "Buffer" to if (info.bufferMs > 0) "${info.bufferMs} ms" else "—",
            "Offload" to if (info.offload) "Yes" else "No",
        ))
    }
}

@Composable
private fun PipeStage(title: String, rows: List<Pair<String, String>>) {
    Text(title.uppercase(), style = BopType.Micro, color = Bop.Text3, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
    Column(Modifier.fillMaxWidth().glassLite(RoundedCornerShape(16.dp), alpha = 0.55f).padding(horizontal = 14.dp, vertical = 10.dp)) {
        rows.forEach { (k, v) ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                Text(k, style = BopType.Body, color = Bop.Text2, modifier = Modifier.width(120.dp))
                Text(v, style = BopType.BodyStrong, color = Bop.Text)
            }
        }
    }
}

/** Paste a Spotify / YouTube playlist link; songs are matched to YouTube Music and saved as a playlist. */
@Composable
private fun ColumnScope.ImportPlaylistSheet(app: AppState) {
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    var link by remember { mutableStateOf(clip.getText()?.text?.takeIf { com.dopdop.bopdop.data.PlaylistImport.recognises(it) }.orEmpty()) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val busy = progress != null
    Text("Import a playlist", style = BopType.Title, color = Bop.Text, modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp))
    Text("Paste a Spotify or YouTube playlist link. Each song is matched to YouTube Music by title, artist and length.",
        style = BopType.Caption, color = Bop.Text2, modifier = Modifier.padding(horizontal = 22.dp))
    Box(Modifier.padding(horizontal = 18.dp, vertical = 12.dp).fillMaxWidth().height(54.dp).glassLite(RoundedCornerShape(16.dp), alpha = 0.6f).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
        if (link.isEmpty()) Text("https://open.spotify.com/playlist/…", style = BopType.Body, color = Bop.Text3)
        BasicTextField(
            link, { link = it.trim(); error = null }, singleLine = true, enabled = !busy,
            textStyle = BopType.Body.copy(color = Bop.Text), cursorBrush = SolidColor(Bop.Ink),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    error?.let { Text(it, style = BopType.Caption, color = Bop.Danger, modifier = Modifier.padding(horizontal = 22.dp)) }
    progress?.let { (done, total) ->
        Text(if (total == 0) "Reading the playlist…" else "Matching songs… $done of $total", style = BopType.Caption, color = Bop.Text2, modifier = Modifier.padding(horizontal = 22.dp))
    }
    val ready = com.dopdop.bopdop.data.PlaylistImport.recognises(link) && !busy
    Box(
        Modifier.padding(18.dp).fillMaxWidth().height(50.dp).clip(RoundedCornerShape(50))
            .background(if (ready) Bop.Ink else Bop.Ink.copy(alpha = 0.25f))
            .bounceClick {
                if (!ready) return@bounceClick
                progress = 0 to 0
                scope.launch {
                    try {
                        val r = com.dopdop.bopdop.data.PlaylistImport.import(link) { d, t -> progress = d to t }
                        if (r.matched.isEmpty()) { error = "Couldn't match any songs from that playlist"; return@launch }
                        val p = LibraryStore.createPlaylist(r.name, r.matched)
                        app.sheet = null
                        app.toast(if (r.missed.isEmpty()) "Imported ${r.matched.size} songs" else "Imported ${r.matched.size} songs · ${r.missed.size} not found")
                        app.openLocal(p.id)
                    } catch (e: Exception) {
                        error = e.message ?: "Import failed"
                    } finally {
                        progress = null
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) { Text(if (busy) "Importing…" else "Import", style = BopType.BodyStrong, color = Color.White) }
}
