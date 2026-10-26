package com.dopdop.bopdop.ui.player

import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ThumbDown
import com.dopdop.bopdop.data.pinnedFirst
import com.dopdop.bopdop.data.PlaylistSort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Cast
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
                    Sheet.SleepTimer -> SleepTimerSheet(app)
                    Sheet.SeekBarLook -> SeekLookSheet()
                    Sheet.PlaybackSpeed -> SpeedSheet(app)
                    Sheet.AudioPipeline -> AudioPipelineSheet()
                    Sheet.ImportPlaylist -> ImportPlaylistSheet(app)
                    is Sheet.SortPlaylist -> SortPlaylistSheet(app, s.id)
                    Sheet.Scrobbling -> ScrobblingSheet(app)
                    Sheet.LastFm -> LastFmSheet(app)
                    is Sheet.LyricsSearch -> LyricsSearchSheet(app, s.track)
                    Sheet.ReportProblem -> ReportProblemSheet(app)
                    is Sheet.SongInfo -> SongInfoSheet(s.track)
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

internal fun trackLink(t: Track) = if (t.id.startsWith("yt:")) "https://music.youtube.com/watch?v=${t.rawId}"
    else "https://www.jiosaavn.com/search/${android.net.Uri.encode("${t.title} ${t.artist}")}"

@Composable
private fun ColumnScope.TrackOptions(app: AppState, t: Track, playlistId: String?) {
    val ctx = LocalContext.current
    val lib by LibraryStore.state.collectAsState()
    val liked = t.id in lib.likedIds
    val close = { app.sheet = null }
    Header({ Artwork(t.artwork, Modifier.size(56.dp), corner = 10.dp, size = 226) }, t.title, listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString(" · "))
    Column(Modifier.verticalScroll(rememberScrollState())) {
        // The player's own controls (sleep, speed, cast, stats) lead when this is the song playing.
        if (PlayerConnection.current.value?.id == t.id) {
            val sleepAt by PlayerConnection.sleepAt.collectAsState()
            Action(Icons.Rounded.Bedtime, sleepLabel(sleepAt)?.let { "Sleep timer · ${it.removePrefix("Sleep ")}" } ?: "Sleep timer") { app.sheet = Sheet.SleepTimer }
            Action(Icons.Rounded.Speed, "Playback speed · ${speedLabel(lib.settings.playbackSpeed)}") { app.sheet = Sheet.PlaybackSpeed }
            Action(Icons.Rounded.Cast, "Cast to a device") { close(); com.dopdop.bopdop.playback.CastPicker.show(ctx) }
            Action(Icons.Rounded.Insights, "Stats for nerds") { app.sheet = Sheet.AudioPipeline }
            Action(Icons.Rounded.Search, "Find other lyrics") { app.sheet = Sheet.LyricsSearch(t) }
        }
        Action(Icons.Rounded.SkipNext, "Play next") { PlayerConnection.playNext(t); app.toast("Playing next"); close() }
        Action(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") { PlayerConnection.addToQueue(listOf(t)); app.toast("Added to queue"); close() }
        Action(Icons.Rounded.Radio, "Start radio") { PlayerConnection.startRadio(t); close() }
        Action(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) "Remove from Liked" else "Like", if (liked) Color(0xFFFF4D7E) else Bop.Text) {
            LibraryStore.toggleLike(t); close()
        }
        val disliked = lib.isDisliked(t)
        Action(Icons.Rounded.ThumbDown, if (disliked) "Undo “Not for me”" else "Not for me") {
            val now = LibraryStore.toggleDislike(t)
            if (now && PlayerConnection.current.value?.id == t.id) PlayerConnection.next()
            app.toast(if (now) "Got it. It stays out of radio and mixes" else "Back in rotation"); close()
        }
        Action(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist…") { app.sheet = Sheet.AddToPlaylist(listOf(t)) }
        if (playlistId != null) Action(Icons.Rounded.RemoveCircleOutline, "Remove from this playlist") { LibraryStore.removeFromPlaylist(playlistId, t.id); close() }
        t.albumId?.let { id -> Action(Icons.Rounded.Album, "Go to album") { close(); app.collapsePlayer(); app.openAlbum(id, t.album, t.artwork) } }
        t.artistId?.let { id -> Action(Icons.Rounded.Person, "Go to artist") { close(); app.collapsePlayer(); app.openArtist(id, t.artist.split(",").first()) } }
        if (t.source != com.dopdop.bopdop.data.model.Source.LOCAL) {
            val dl by com.dopdop.bopdop.playback.Downloads.status.collectAsState()
            val st = dl[t.id]
            when (st?.state) {
                androidx.media3.exoplayer.offline.Download.STATE_COMPLETED ->
                    Action(Icons.Rounded.DownloadDone, "Remove download") { com.dopdop.bopdop.playback.Downloads.remove(ctx, t.id); app.toast("Download removed"); close() }
                androidx.media3.exoplayer.offline.Download.STATE_QUEUED, androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING, androidx.media3.exoplayer.offline.Download.STATE_RESTARTING ->
                    Action(Icons.Rounded.Downloading, if (st.percent > 0f) "Downloading ${st.percent.toInt()}% · tap to cancel" else "Waiting to download · tap to cancel") {
                        com.dopdop.bopdop.playback.Downloads.remove(ctx, t.id); close()
                    }
                else -> Action(Icons.Rounded.Download, "Download") { com.dopdop.bopdop.playback.Downloads.download(ctx, listOf(t)); app.toast("Downloading"); close() }
            }
        }
        if (t.source != com.dopdop.bopdop.data.model.Source.LOCAL && remember(t.id) { com.dopdop.bopdop.playback.CachedSongs.isCached(ctx, t) }) {
            Action(Icons.Rounded.DeleteSweep, "Remove from cache") {
                app.scope.launch { com.dopdop.bopdop.playback.CachedSongs.remove(ctx, t) }; app.toast("Removed from cache"); close()
            }
        }
        Action(Icons.Rounded.Share, "Share") { close(); app.shareTrack = t }
        Action(Icons.Rounded.Info, "Song info") { app.sheet = Sheet.SongInfo(t) }
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
        localPl != null -> localPl.sorted
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
    if (c.kind == CardKind.ALBUM || c.kind == CardKind.PLAYLIST) Action(Icons.Rounded.Download, "Download all") {
        withTracks { com.dopdop.bopdop.playback.Downloads.download(ctx, it); app.toast("Downloading ${it.size} songs") }; close()
    }
    if (localPl != null && localPl.tracks.isNotEmpty()) Action(Icons.Rounded.QrCode2, "Share as code") {
        close(); app.shareQr(ShareRequest(SharePayload.Playlist(localPl.name, localPl.tracks.map { it.id }), localPl.name, songs(localPl.tracks.size), localPl.artwork))
    }
    if (!local && c.kind in setOf(CardKind.ALBUM, CardKind.PLAYLIST, CardKind.ARTIST)) Action(Icons.Rounded.QrCode2, "Share as code") {
        close(); app.shareQr(ShareRequest(SharePayload.Collection(c), c.title, c.subtitle.ifBlank { c.kind.name.lowercase().replaceFirstChar { it.uppercase() } }, c.artwork))
    }
    if (localPl != null) {
        Action(Icons.Rounded.PushPin, if (localPl.pinned) "Unpin" else "Pin to top") {
            LibraryStore.setPinned(localPl.id, !localPl.pinned); app.toast(if (localPl.pinned) "Unpinned" else "Pinned to the top of Library"); close()
        }
        Action(Icons.Rounded.SwapVert, "Sort songs · ${localPl.sort.label}") { app.sheet = Sheet.SortPlaylist(localPl.id) }
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

/** Tick one or more playlists, then add. Playlists that already have the song(s) are marked. */
@Composable
private fun ColumnScope.AddToPlaylist(app: AppState, tracks: List<Track>) {
    val lib by LibraryStore.state.collectAsState()
    val picked = remember { mutableStateListOf<String>() }
    Text("Add to playlists", style = BopType.Title, color = Bop.Text, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        Action(Icons.Rounded.Add, "New playlist") { app.sheet = Sheet.NewPlaylist(tracks) }
        lib.playlists.pinnedFirst().forEach { p ->
            val has = tracks.all { t -> p.tracks.any { it.id == t.id } }
            val on = p.id in picked
            Row(
                Modifier.fillMaxWidth().bounceClick(0.98f) {
                    if (!has) { if (on) picked.remove(p.id) else picked.add(p.id) }
                }.padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Collage(p.tracks.map { it.artwork }.distinct(), Modifier.size(48.dp), 8.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = BopType.BodyStrong, color = Bop.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (has) "Already added" else songs(p.tracks.size), style = BopType.Caption, color = Bop.Text2)
                }
                Icon(
                    if (on || has) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                    tint = if (on) Bop.Ink else Bop.Text3, modifier = Modifier.size(24.dp),
                )
            }
        }
    }
    if (lib.playlists.isNotEmpty()) Box(
        Modifier.padding(horizontal = 18.dp, vertical = 10.dp).fillMaxWidth().height(50.dp).clip(RoundedCornerShape(50))
            .background(if (picked.isEmpty()) Bop.Ink.copy(alpha = 0.25f) else Bop.Ink)
            .bounceClick {
                if (picked.isEmpty()) return@bounceClick
                LibraryStore.addToPlaylists(picked.toSet(), tracks)
                val one = lib.playlists.firstOrNull { it.id == picked.singleOrNull() }
                app.toast(if (one != null) "Added to “${one.name}”" else "Added to ${picked.size} playlists")
                app.sheet = null
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            when (picked.size) { 0 -> "Pick playlists"; 1 -> "Add"; else -> "Add to ${picked.size} playlists" },
            style = BopType.BodyStrong, color = if (picked.isEmpty()) Bop.Text2 else Bop.OnInk,
        )
    }
}

/** How a playlist lists and plays its songs. */
@Composable
private fun ColumnScope.SortPlaylistSheet(app: AppState, id: String) {
    val lib by LibraryStore.state.collectAsState()
    val p = lib.playlists.firstOrNull { it.id == id } ?: return
    Text("Sort songs", style = BopType.Title, color = Bop.Text, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
    PlaylistSort.entries.forEach { o ->
        val on = o == p.sort
        Row(
            Modifier.fillMaxWidth().bounceClick(0.98f) { LibraryStore.setSort(id, o); app.sheet = null }.padding(horizontal = 22.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(o.label, style = if (on) BopType.BodyStrong else BopType.Body, color = Bop.Text, modifier = Modifier.weight(1f))
            if (on) Icon(Icons.Rounded.Check, null, tint = Bop.Text, modifier = Modifier.size(20.dp))
        }
    }
    Text("Custom order is the one you arrange with Edit on the playlist.", style = BopType.Caption, color = Bop.Text3,
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
}

/** Connect or disconnect ListenBrainz with a user token. */
@Composable
private fun ColumnScope.ScrobblingSheet(app: AppState) {
    val ctx = LocalContext.current
    val lib by LibraryStore.state.collectAsState()
    val s = lib.settings
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val rule = "A song counts once you've heard half of it, or four minutes."
    Text("ListenBrainz", style = BopType.Title, color = Bop.Text, modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp))
    if (s.listenBrainzToken.isNotBlank()) {
        Text("Scrobbling as ${s.listenBrainzUser}. $rule", style = BopType.Body, color = Bop.Text2, modifier = Modifier.padding(horizontal = 22.dp))
        Box(
            Modifier.padding(18.dp).fillMaxWidth().height(50.dp).clip(RoundedCornerShape(50)).background(Bop.Veil.copy(alpha = 0.7f))
                .bounceClick {
                    LibraryStore.updateSettings { it.copy(listenBrainzToken = "", listenBrainzUser = "") }
                    app.toast("ListenBrainz disconnected"); app.sheet = null
                },
            contentAlignment = Alignment.Center,
        ) { Text("Disconnect", style = BopType.BodyStrong, color = Bop.Danger) }
        return
    }
    Text("Paste your user token from ListenBrainz › Settings. $rule", style = BopType.Caption, color = Bop.Text2, modifier = Modifier.padding(horizontal = 22.dp))
    Text(
        "Get your token", style = BopType.BodyStrong, color = Bop.Accent,
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp).bounceClick {
            runCatching {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://listenbrainz.org/settings/")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        },
    )
    Box(Modifier.padding(horizontal = 18.dp, vertical = 4.dp).fillMaxWidth().height(54.dp).glassLite(RoundedCornerShape(16.dp), alpha = 0.6f).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
        if (token.isEmpty()) Text("User token", style = BopType.Body, color = Bop.Text3)
        BasicTextField(
            token, { token = it.trim(); error = null }, singleLine = true, enabled = !busy,
            textStyle = BopType.Body.copy(color = Bop.Text), cursorBrush = SolidColor(Bop.Ink),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    error?.let { Text(it, style = BopType.Caption, color = Bop.Danger, modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)) }
    val ready = token.length >= 8 && !busy
    Box(
        Modifier.padding(18.dp).fillMaxWidth().height(50.dp).clip(RoundedCornerShape(50))
            .background(if (ready) Bop.Ink else Bop.Ink.copy(alpha = 0.25f))
            .bounceClick {
                if (!ready) return@bounceClick
                busy = true
                scope.launch {
                    try {
                        val user = com.dopdop.bopdop.data.scrobble.ListenBrainz.validate(token)
                        if (user == null) error = "ListenBrainz didn't accept that token"
                        else {
                            LibraryStore.updateSettings { it.copy(listenBrainzToken = token, listenBrainzUser = user) }
                            com.dopdop.bopdop.data.scrobble.ListenBrainz.flush()
                            app.toast("Scrobbling as $user"); app.sheet = null
                        }
                    } catch (e: Exception) {
                        error = "Couldn't reach ListenBrainz. Check your connection."
                    } finally {
                        busy = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) { Text(if (busy) "Checking…" else "Connect", style = BopType.BodyStrong, color = if (ready) Bop.OnInk else Bop.Text2) }
}

/**
 * Last.fm / Libre.fm: allow BopDop on the site in the browser, come back, tap Done. With no API
 * key in this build, paste your own (last.fm/api/account/create) under "Use your own API key".
 */
@Composable
private fun ColumnScope.LastFmSheet(app: AppState) {
    val ctx = LocalContext.current
    val lib by LibraryStore.state.collectAsState()
    val s = lib.settings
    val scope = rememberCoroutineScope()
    val fm = com.dopdop.bopdop.data.scrobble.LastFm
    var token by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var ownKey by remember { mutableStateOf(false) }
    val site = if (s.lastFmLibre) "Libre.fm" else "Last.fm"
    Text(site, style = BopType.Title, color = Bop.Text, modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp))
    if (s.lastFmSession.isNotBlank()) {
        Text("Scrobbling as ${s.lastFmUser}. A song counts after half of it, or four minutes.", style = BopType.Body, color = Bop.Text2, modifier = Modifier.padding(horizontal = 22.dp))
        Box(
            Modifier.padding(18.dp).fillMaxWidth().height(50.dp).clip(RoundedCornerShape(50)).background(Bop.Veil.copy(alpha = 0.7f))
                .bounceClick { LibraryStore.updateSettings { it.copy(lastFmSession = "", lastFmUser = "") }; app.toast("$site disconnected"); app.sheet = null },
            contentAlignment = Alignment.Center,
        ) { Text("Disconnect", style = BopType.BodyStrong, color = Bop.Danger) }
        return
    }
    Row(Modifier.padding(horizontal = 18.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(false to "Last.fm", true to "Libre.fm").forEach { (libre, label) ->
            val on = s.lastFmLibre == libre
            Text(label, style = BopType.BodyStrong, color = if (on) Bop.OnInk else Bop.Text,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Bop.Ink else Bop.Veil.copy(alpha = 0.6f))
                    .bounceClick { LibraryStore.updateSettings { it.copy(lastFmLibre = libre) }; token = null }.padding(horizontal = 14.dp, vertical = 8.dp))
        }
    }
    if (!fm.hasKeys || ownKey) {
        Text("Last.fm needs your own API key: create one free at last.fm/api/account/create and paste the key and secret here.", style = BopType.Caption, color = Bop.Text2, modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp))
        @Composable
        fun Field(hint: String, value: String, onChange: (String) -> Unit) = Box(
            Modifier.padding(horizontal = 18.dp, vertical = 4.dp).fillMaxWidth().height(50.dp).glassLite(RoundedCornerShape(16.dp), alpha = 0.6f).padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) Text(hint, style = BopType.Body, color = Bop.Text3)
            BasicTextField(value, onChange, singleLine = true, textStyle = BopType.Body.copy(color = Bop.Text), cursorBrush = SolidColor(Bop.Ink), modifier = Modifier.fillMaxWidth())
        }
        Field("API key", s.lastFmKey) { v -> LibraryStore.updateSettings { it.copy(lastFmKey = v.trim()) } }
        Field("Shared secret", s.lastFmSecret) { v -> LibraryStore.updateSettings { it.copy(lastFmSecret = v.trim()) } }
    } else {
        Text("Use your own API key", style = BopType.Caption, color = Bop.Text3, modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp).bounceClick { ownKey = true })
    }
    Text(
        if (token == null) "You'll allow BopDop on $site in your browser, then come back here."
        else "Allowed it on $site? Tap Done.",
        style = BopType.Caption, color = Bop.Text2, modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
    )
    error?.let { Text(it, style = BopType.Caption, color = Bop.Danger, modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)) }
    val ready = fm.hasKeys && !busy
    Box(
        Modifier.padding(18.dp).fillMaxWidth().height(50.dp).clip(RoundedCornerShape(50))
            .background(if (ready) Bop.Ink else Bop.Ink.copy(alpha = 0.25f))
            .bounceClick {
                if (!ready) return@bounceClick
                busy = true; error = null
                scope.launch {
                    try {
                        val t = token
                        if (t == null) {
                            val (fresh, url) = fm.startAuth()
                            token = fresh
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } else {
                            val sess = fm.finishAuth(t)
                            if (sess == null) error = "$site hasn't confirmed yet. Allow BopDop there, then tap Done."
                            else {
                                LibraryStore.updateSettings { it.copy(lastFmSession = sess.first, lastFmUser = sess.second) }
                                fm.flush()
                                app.toast("Scrobbling to $site as ${sess.second}"); app.sheet = null
                            }
                        }
                    } catch (e: Exception) {
                        error = e.message?.takeIf { it.startsWith("Last.fm") } ?: "Couldn't reach $site. Check your connection."
                    } finally { busy = false }
                }
            },
        contentAlignment = Alignment.Center,
    ) { Text(when { busy -> "Working…"; token == null -> "Sign in with $site"; else -> "Done" }, style = BopType.BodyStrong, color = if (ready) Bop.OnInk else Bop.Text2) }
}

/**
 * Wrong or missing lyrics (F44): search LRCLIB with any words, see which results are synced and how
 * long each recording is, and pick one. The pick is kept for the song; "Automatic" undoes it.
 */
@Composable
private fun ColumnScope.LyricsSearchSheet(app: AppState, t: Track) {
    val api = com.dopdop.bopdop.data.lyrics.LyricsApi
    var q by remember(t.id) { mutableStateOf("${com.dopdop.bopdop.data.lyrics.LyricsApi.cleanTitle(t.title)} ${t.artist.split(",").first().trim()}".trim()) }
    var hits by remember(t.id) { mutableStateOf<List<com.dopdop.bopdop.data.lyrics.LyricsApi.Hit>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun run() {
        busy = true; error = null
        scope.launch {
            runCatching { api.search(q) }.onSuccess { hits = it }.onFailure { error = "Couldn't reach the lyrics search. Check your connection." }
            busy = false
        }
    }
    LaunchedEffect(t.id) { run() }
    Text("Find lyrics", style = BopType.Title, color = Bop.Text, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
    Row(Modifier.padding(horizontal = 18.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(48.dp).glassLite(RoundedCornerShape(16.dp), alpha = 0.6f).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
            BasicTextField(q, { q = it }, singleLine = true, textStyle = BopType.Body.copy(color = Bop.Text), cursorBrush = SolidColor(Bop.Ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { run() }), modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.width(8.dp))
        Text(if (busy) "…" else "Search", style = BopType.BodyStrong, color = Bop.OnInk,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Bop.Ink).bounceClick { if (!busy) run() }.padding(horizontal = 16.dp, vertical = 12.dp))
    }
    error?.let { Text(it, style = BopType.Caption, color = Bop.Danger, modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)) }
    if (api.hasPick(t)) Text("Automatic (undo your pick)", style = BopType.BodyStrong, color = Bop.Accent,
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp).bounceClick { api.choose(t, null); app.toast("Lyrics back to automatic"); app.sheet = null })
    val list = hits
    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
        if (list != null && list.isEmpty() && !busy) Text("Nothing found. Try fewer words, or the song's original title.", style = BopType.Body, color = Bop.Text2,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
        list.orEmpty().take(30).forEach { h ->
            Row(
                Modifier.fillMaxWidth().bounceClick(0.98f) { api.choose(t, h.lyrics); app.toast("Using these lyrics for this song"); app.sheet = null }
                    .padding(horizontal = 22.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(h.title, style = BopType.BodyStrong, color = Bop.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOf(h.artist, h.album).filter { it.isNotBlank() }.joinToString(" · "), style = BopType.Caption, color = Bop.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(h.lyrics.synced.firstOrNull { it.text.isNotBlank() }?.text ?: h.lyrics.plain?.lineSequence()?.firstOrNull { it.isNotBlank() }.orEmpty(),
                        style = BopType.Caption, color = Bop.Text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (h.lyrics.isSynced) "Synced" else "Text only", style = BopType.Caption, color = if (h.lyrics.isSynced) Bop.Text else Bop.Text3)
                    // How far its length is from the song's: a close one usually lines up.
                    if (h.durationSec > 0) Text(com.dopdop.bopdop.data.model.formatDuration(h.durationSec) +
                        (if (t.durationSec > 0 && kotlin.math.abs(h.durationSec - t.durationSec) <= 3) " ✓" else ""), style = BopType.Caption, color = Bop.Text2)
                }
            }
        }
    }
}

/** Report a problem (F56): an optional note, then the share sheet with the report file. */
@Composable
private fun ColumnScope.ReportProblemSheet(app: AppState) {
    val ctx = LocalContext.current
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Text("Report a problem", style = BopType.Title, color = Bop.Text, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
    Text("Say what went wrong and when. BopDop adds its own recent log, the last crash and your phone model; no passwords or sign-ins. You choose where to send it.",
        style = BopType.Caption, color = Bop.Text2, modifier = Modifier.padding(horizontal = 22.dp))
    Box(Modifier.padding(horizontal = 18.dp, vertical = 10.dp).fillMaxWidth().heightIn(min = 96.dp).glassLite(RoundedCornerShape(16.dp), alpha = 0.6f).padding(14.dp)) {
        if (note.isEmpty()) Text("e.g. the cover didn't load after the song changed", style = BopType.Body, color = Bop.Text3)
        BasicTextField(note, { note = it }, textStyle = BopType.Body.copy(color = Bop.Text), cursorBrush = SolidColor(Bop.Ink), modifier = Modifier.fillMaxWidth())
    }
    Box(
        Modifier.padding(horizontal = 18.dp, vertical = 6.dp).fillMaxWidth().height(50.dp).clip(RoundedCornerShape(50)).background(Bop.Ink)
            .bounceClick {
                if (busy) return@bounceClick
                busy = true
                scope.launch {
                    runCatching { com.dopdop.bopdop.data.ProblemReport.build(ctx, note) }
                        .onSuccess { f -> app.sheet = null; com.dopdop.bopdop.data.ProblemReport.share(ctx, f) }
                        .onFailure { app.toast("Couldn't make the report") }
                    busy = false
                }
            },
        contentAlignment = Alignment.Center,
    ) { Text(if (busy) "Collecting…" else "Share report", style = BopType.BodyStrong, color = Bop.OnInk) }
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
    ) { Text(cta, style = BopType.BodyStrong, color = if (name.isBlank()) Bop.Text2 else Bop.OnInk) }
}

/** 1x, 1.25x, 0.75x… */
fun speedLabel(v: Float): String = (if (v % 1f == 0f) "%.0f" else if ((v * 10) % 1f == 0f) "%.1f" else "%.2f").format(v) + "×"

/** Playback speed presets; pitch stays put. Saved, so it carries to the next songs and restarts. */
@Composable
private fun ColumnScope.SpeedSheet(app: AppState) {
    val lib by LibraryStore.state.collectAsState()
    val now = lib.settings.playbackSpeed
    Row(Modifier.padding(horizontal = 22.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Speed, null, tint = Bop.Text)
        Spacer(Modifier.width(10.dp))
        Text("Playback speed", style = BopType.Title, color = Bop.Text, modifier = Modifier.weight(1f))
        Text(speedLabel(now), style = BopType.BodyStrong, color = Bop.Text2)
    }
    val presets = listOf(0.5f, 0.75f, 0.9f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f)
    presets.chunked(5).forEach { row ->
        Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { v ->
                val on = v == now
                Box(
                    Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(16.dp)).background(if (on) Bop.Ink else Bop.Veil.copy(alpha = 0.6f))
                        .bounceClick(0.94f) { LibraryStore.updateSettings { it.copy(playbackSpeed = v) } },
                    contentAlignment = Alignment.Center,
                ) { Text(speedLabel(v), style = BopType.BodyStrong, color = if (on) Bop.OnInk else Bop.Text) }
            }
            repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    Spacer(Modifier.height(8.dp))
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
    Text("Stats for nerds", style = BopType.Title, color = Bop.Text, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
    Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 12.dp)) {
        PipeStage("Source", listOf(
            "From" to source, "Format" to info.codec, "Sample rate" to khz(info.sampleRate),
            "Bitrate" to if (info.bitrateKbps > 0) "${info.bitrateKbps} kbps" else (current?.let { quality[it.id]?.kbps?.takeIf { k -> k > 0 }?.let { k -> "$k kbps" } } ?: "—"),
            "Bit depth" to when {
                info.bitDepth > 0 -> "${info.bitDepth}-bit"
                info.codec in setOf("FLAC", "ALAC", "PCM") -> "Lossless"
                info.codec == "—" -> "—"
                else -> "None (lossy)"
            },
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
            "Loaded ahead" to remember(tick) { ((PlayerConnection.bufferedPosition - PlayerConnection.position) / 1000).coerceAtLeast(0).let { "$it s" } },
            "Offload" to if (info.offload) "Yes" else "No",
            "Bit-perfect" to (com.dopdop.bopdop.playback.BitPerfect.active.collectAsState().value ?: if (settings.bitPerfect) "Waiting for a USB DAC" else "Off"),
        ))
    }
}

/** Release date, album, label and credits from the catalogue, plus how much you've played it. */
@Composable
private fun ColumnScope.SongInfoSheet(t: Track) {
    val info by androidx.compose.runtime.produceState<com.dopdop.bopdop.data.SongInfo?>(null, t.id) {
        value = runCatching { com.dopdop.bopdop.data.SongInfo.load(t) }.getOrDefault(com.dopdop.bopdop.data.SongInfo())
    }
    val stats by com.dopdop.bopdop.data.PlayStats.state.collectAsState()
    val lib by LibraryStore.state.collectAsState()
    val plays = remember(stats, t.id) { stats.events.filter { it.id == t.id } }
    val fmt = remember { java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy") }
    fun day(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(fmt)
    fun released(s: String) = runCatching { java.time.LocalDate.parse(s).format(fmt) }.getOrDefault(s)
    Header({ Artwork(t.artwork, Modifier.size(56.dp), corner = 10.dp, size = 226) }, t.title, t.artist)
    Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 12.dp)) {
        val i = info
        if (i == null) {
            Text("Looking it up…", style = BopType.Body, color = Bop.Text2, modifier = Modifier.padding(vertical = 14.dp))
        } else {
            val about = listOf(
                "Released" to released(i.released), "Album" to i.album, "Label" to i.label, "Language" to i.language,
                "Length" to if (t.durationSec > 0) "%d:%02d".format(t.durationSec / 60, t.durationSec % 60) else "",
                "Source" to when (t.source) {
                    com.dopdop.bopdop.data.model.Source.SAAVN -> "JioSaavn"
                    com.dopdop.bopdop.data.model.Source.LOCAL -> "This phone"
                    com.dopdop.bopdop.data.model.Source.SPOTIFY -> "Spotify (plays from your sources)"
                    else -> "YouTube Music"
                },
            ).filter { it.second.isNotBlank() }
            PipeStage("About", about)
            if (i.credits.isNotEmpty()) PipeStage("Credits", i.credits.map { it.role to it.names.joinToString(", ") })
            if (i.copyright.isNotBlank()) Text(i.copyright, style = BopType.Caption, color = Bop.Text3, modifier = Modifier.padding(top = 10.dp))
        }
        PipeStage("Your plays", if (plays.isEmpty()) listOf("Plays" to "Not yet") else listOf(
            "Plays" to plays.size.toString(),
            "First" to day(plays.first().at),
            "Latest" to day(plays.last().at),
            "Liked" to if (t.id in lib.likedIds) "Yes" else "No",
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
    ) { Text(if (busy) "Importing…" else "Import", style = BopType.BodyStrong, color = if (ready) Bop.OnInk else Bop.Text2) }
}
