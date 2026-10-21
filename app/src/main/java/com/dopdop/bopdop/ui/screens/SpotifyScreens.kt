package com.dopdop.bopdop.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.model.songs
import com.dopdop.bopdop.data.spotify.SpotifyAccount
import com.dopdop.bopdop.data.spotify.SpotifyApi
import com.dopdop.bopdop.data.spotify.SpotifyMatch
import com.dopdop.bopdop.data.spotify.SpotifyPlaylist
import com.dopdop.bopdop.data.spotify.SpotifyTrack
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.ui.LocalApp
import com.dopdop.bopdop.ui.Routes
import com.dopdop.bopdop.ui.Sheet
import com.dopdop.bopdop.ui.components.Artwork
import com.dopdop.bopdop.ui.components.ErrorState
import com.dopdop.bopdop.ui.components.Load
import com.dopdop.bopdop.ui.components.PillButton
import com.dopdop.bopdop.ui.components.TrackRow
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.components.rememberLoad
import com.dopdop.bopdop.ui.components.shimmer
import com.dopdop.bopdop.ui.glass.glassEdges
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val SPOTIFY_LOGIN = "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"
private val SPOTIFY_HOSTS = listOf("spotify.com", "scdn.co", "spotifycdn.com", "google.com", "gstatic.com", "facebook.com", "apple.com")

/**
 * Spotify's own login page in a WebView (password, Google, Facebook or Apple). When it lands on
 * open.spotify.com signed in, the `sp_dc` session cookie is kept and the page closes; the password
 * never passes through BopDop.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginScreen() {
    val app = LocalApp.current
    var progress by remember { mutableIntStateOf(0) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var canBack by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    BackHandler(enabled = canBack) { web?.goBack() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.windowInsetsPadding(WindowInsets.statusBars).fillMaxWidth().height(56.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton { app.nav.popBackStack() }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Connect Spotify", style = BopType.Headline, color = Bop.Text)
                Text("Your playlists and Liked Songs", style = BopType.Caption, color = Bop.Text3)
            }
            Spacer(Modifier.width(42.dp))
        }
        if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, color = Bop.Accent, trackColor = Color.Transparent, modifier = Modifier.fillMaxWidth().height(2.dp))
        val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp).padding(top = 6.dp).clip(shape).background(Color(0xFF121212)).glassEdges(shape, 0.6f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // The phone's own browser, minus the WebView marker, so Spotify serves its mobile page.
                        settings.userAgentString = settings.userAgentString.replace("; wv", "")
                        setBackgroundColor(0xFF121212.toInt())
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, p: Int) { progress = p }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val host = request.url.host?.lowercase() ?: return true
                                // Spotify and the sign-in providers it hands off to; nothing else opens here.
                                return request.url.scheme != "https" || SPOTIFY_HOSTS.none { host == it || host.endsWith(".$it") }
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                canBack = view.canGoBack()
                                if (done || url?.startsWith(SpotifyAccount.ORIGIN) != true) return
                                val spDc = CookieManager.getInstance().getCookie(SpotifyAccount.ORIGIN)?.split(";")
                                    ?.map { it.trim() }?.firstOrNull { it.startsWith("sp_dc=") }?.substringAfter('=')?.ifBlank { null } ?: return
                                done = true
                                view.visibility = android.view.View.GONE
                                SpotifyAccount.signIn(spDc)
                                app.scope.launch {
                                    SpotifyApi.profile()?.let(SpotifyAccount::setProfile)
                                    app.toast("Connected to Spotify. Importing your playlists…")
                                    com.dopdop.bopdop.data.spotify.SpotifyImport.auto(app.nav.context, force = true)
                                }
                                app.nav.popBackStack()
                                app.nav.navigate(Routes.SPOTIFY)
                            }
                        }
                        web = this
                        loadUrl(SPOTIFY_LOGIN)
                    }
                },
                onRelease = { it.destroy() },
            )
        }
        Text(
            "You sign in on Spotify's own page. BopDop keeps only the session on this phone, never your password.",
            style = BopType.Caption, color = Bop.Text3, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp).windowInsetsPadding(WindowInsets.navigationBars),
        )
    }
}

/** Your Spotify playlists (Liked Songs first); open one to play it here. */
@Composable
fun SpotifyLibraryScreen() {
    val app = LocalApp.current
    val profile by SpotifyAccount.profile.collectAsState()
    val load = rememberLoad("spotify:playlists", 10 * 60_000L) { SpotifyApi.playlists() }
    LargeTitleScaffold(title = "Spotify", eyebrow = profile?.name?.let { "Signed in as $it" } ?: "", showBack = true) {
        when (val s = load.state) {
            is Load.Loading -> items(6) { Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().height(58.dp).shimmer(14.dp)) }
            is Load.Failed -> item { ErrorState(s.message, onRetry = load.retry) }
            is Load.Ready -> {
                val mine = com.dopdop.bopdop.data.spotify.SpotifyImport.yours(s.value, profile?.name)
                item { ImportCard(mine) }
                items(s.value.size, key = { s.value[it].id }) { i -> SpotifyPlaylistRow(s.value[i]) { app.openSpotifyPlaylist(s.value[i]) } }
            }
        }
        item {
            Text(
                "Disconnect Spotify", style = BopType.BodyStrong, color = Bop.Danger,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp).bounceClick {
                    SpotifyAccount.signOut(); app.toast("Spotify disconnected"); app.nav.popBackStack()
                },
            )
        }
    }
}

/** "Import my playlists": your own playlists and Liked Songs into BopDop's library, with live progress. */
@Composable
private fun ImportCard(mine: List<SpotifyPlaylist>) {
    val app = LocalApp.current
    val progress by com.dopdop.bopdop.data.spotify.SpotifyImport.progress.collectAsState()
    val p = progress
    Column(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Bop.Veil.copy(alpha = 0.55f)).padding(16.dp),
    ) {
        when {
            p == null -> {
                val auto = LibraryStore.state.collectAsState().value.settings.spotifyAutoImport
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Import automatically", style = BopType.BodyStrong, color = Bop.Text)
                        Text("Your ${mine.size} playlists (with Liked Songs) stay copied in your library: new songs are added when the app opens, at most every 12 hours.",
                            style = BopType.Caption, color = Bop.Text2, modifier = Modifier.padding(top = 2.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.bounceClick { LibraryStore.updateSettings { it.copy(spotifyAutoImport = !it.spotifyAutoImport) } }) { GlassSwitch(auto) }
                }
                Spacer(Modifier.height(10.dp))
                PillButton("Sync now", Icons.Rounded.LibraryAdd, filled = false) {
                    if (mine.isNotEmpty()) app.scope.launch { com.dopdop.bopdop.data.spotify.SpotifyImport.run(mine); com.dopdop.bopdop.data.spotify.SpotifyMatch.save() }
                }
            }
            !p.finished -> {
                Text("Importing ${p.listsDone + 1} of ${p.listsTotal} · ${p.playlist}", style = BopType.BodyStrong, color = Bop.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (p.songsTotal > 0) "Matching songs ${p.songsDone} of ${p.songsTotal}" else "Reading the playlist…", style = BopType.Caption, color = Bop.Text2, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                val f = if (p.listsTotal == 0) 0f else (p.listsDone + if (p.songsTotal > 0) p.songsDone.toFloat() / p.songsTotal else 0f) / p.listsTotal
                LinearProgressIndicator(progress = { f }, color = Bop.Accent, trackColor = Bop.Veil, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)))
                Text("You can leave this page; it keeps going.", style = BopType.Caption, color = Bop.Text3, modifier = Modifier.padding(top = 8.dp))
            }
            else -> {
                Text("Done", style = BopType.BodyStrong, color = Bop.Text)
                Text(p.summary, style = BopType.Caption, color = Bop.Text2, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                PillButton("OK", null, filled = false) { com.dopdop.bopdop.data.spotify.SpotifyImport.dismiss() }
            }
        }
    }
}

@Composable
private fun SpotifyPlaylistRow(p: SpotifyPlaylist, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().bounceClick(0.98f, onClick = onClick).padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(p.cover, Modifier.size(58.dp), corner = 12.dp, size = 226)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = BopType.BodyStrong, color = Bop.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (p.owner.isBlank()) "Playlist" else "Playlist · ${p.owner}", style = BopType.Caption, color = Bop.Text2, maxLines = 1)
        }
    }
}

/**
 * One Spotify playlist. Its songs show at once; each is then matched to YouTube Music six at a
 * time. A song with no match stays listed, dimmed, and is skipped when playing.
 */
@Composable
fun SpotifyPlaylistScreen(id: String, name: String, art: String) {
    val app = LocalApp.current
    val current by PlayerConnection.current.collectAsState()
    val songs = remember(id) { mutableStateListOf<SpotifyTrack>() }
    val matched = remember(id) { mutableStateMapOf<String, Track>() }
    var checked by remember(id) { mutableIntStateOf(0) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var loading by remember(id) { mutableStateOf(true) }
    LaunchedEffect(id) {
        runCatching { SpotifyApi.tracks(id) { page -> songs.clear(); songs.addAll(page) } }
            .onFailure { error = it.message ?: "Couldn't load this playlist" }
        loading = false
        val gate = Semaphore(6)
        coroutineScope {
            songs.toList().map { s ->
                async { gate.withPermit { SpotifyMatch.match(s)?.let { matched[s.uri] = it }; checked++ } }
            }.awaitAll()
        }
    }
    val playable = songs.mapNotNull { matched[it.uri] }
    LargeTitleScaffold(title = name, eyebrow = "Spotify · " + songs(songs.size), showBack = true) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Artwork(art, Modifier.size(180.dp), corner = 22.dp, size = 600)
                Spacer(Modifier.height(14.dp))
                if (checked < songs.size) Text("Matching on YouTube Music… $checked of ${songs.size}", style = BopType.Caption, color = Bop.Text2)
                else if (songs.isNotEmpty()) Text("${playable.size} of ${songs.size} songs found", style = BopType.Caption, color = Bop.Text2)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Play", Icons.Rounded.PlayArrow) { if (playable.isNotEmpty()) { PlayerConnection.play(playable, 0, name); app.expandPlayer() } }
                    PillButton("Shuffle", Icons.Rounded.Shuffle, filled = false) { if (playable.isNotEmpty()) { PlayerConnection.play(playable, 0, name, shuffle = true); app.expandPlayer() } }
                    PillButton("Save", Icons.Rounded.LibraryAdd, filled = false) {
                        if (playable.isEmpty()) return@PillButton
                        LibraryStore.createPlaylist(name, playable); app.toast("Saved “$name” to your library")
                    }
                }
            }
        }
        error?.let { item { ErrorState(it) { app.nav.popBackStack() } } }
        if (loading) items(8) { Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().height(52.dp).shimmer(12.dp)) }
        items(songs.size, key = { "sp$it" + songs[it].uri }) { i ->
            val s = songs[i]
            val t = matched[s.uri]
            if (t != null) {
                TrackRow(t, playing = current?.id == t.id, onMore = { app.sheet = Sheet.TrackOptions(t) }) {
                    PlayerConnection.play(playable, playable.indexOf(t).coerceAtLeast(0), name)
                }
            } else {
                // Not matched yet (or not on YouTube Music): shown as Spotify lists it, dimmed.
                Row(Modifier.fillMaxWidth().alpha(if (SpotifyMatch.failed(s.uri)) 0.35f else 0.6f).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(s.cover, Modifier.size(48.dp), corner = 10.dp, size = 120)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, style = BopType.BodyStrong, color = Bop.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (SpotifyMatch.failed(s.uri)) "Not on YouTube Music" else s.artist, style = BopType.Caption, color = Bop.Text2, maxLines = 1)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Opens a Spotify playlist page. */
fun com.dopdop.bopdop.ui.AppState.openSpotifyPlaylist(p: SpotifyPlaylist) =
    nav.navigate("spotify/playlist?id=${Uri.encode(p.id)}&name=${Uri.encode(p.name)}&art=${Uri.encode(p.cover)}")
