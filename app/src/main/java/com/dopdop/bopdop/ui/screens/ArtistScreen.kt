package com.dopdop.bopdop.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.MusicRepository
import com.dopdop.bopdop.data.model.ArtistPage
import com.dopdop.bopdop.data.model.Card
import com.dopdop.bopdop.data.model.CardKind
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.ui.LocalApp
import com.dopdop.bopdop.ui.LocalChromePadding
import com.dopdop.bopdop.ui.Sheet
import com.dopdop.bopdop.ui.components.Artwork
import com.dopdop.bopdop.ui.components.ErrorState
import com.dopdop.bopdop.ui.components.GlassIconButton
import com.dopdop.bopdop.ui.components.Load
import com.dopdop.bopdop.ui.components.PillButton
import com.dopdop.bopdop.ui.components.SectionHeader
import com.dopdop.bopdop.ui.components.TrackRow
import com.dopdop.bopdop.ui.components.rememberLoad
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun ArtistScreen(id: String, name: String, art: String) {
    val app = LocalApp.current
    val lib by LibraryStore.state.collectAsState()
    val current by PlayerConnection.current.collectAsState()
    val load = rememberLoad("artist:$id") { MusicRepository.artist(id) }
    val page = (load.state as? Load.Ready<ArtistPage>)?.value
    val haze = rememberHazeState()
    val list = rememberLazyListState()
    val scroll by remember { derivedStateOf { if (list.firstVisibleItemIndex > 0) 2000f else list.firstVisibleItemScrollOffset.toFloat() } }
    val titleAlpha by animateFloatAsState(if (scroll > 900f) 1f else 0f, label = "ta")
    val title = page?.name ?: name
    val image = page?.artwork?.ifBlank { null } ?: art
    val card = Card(id, CardKind.ARTIST, title, "Artist", image)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().hazeSource(haze), contentPadding = LocalChromePadding.current) {
            item(key = "hero") {
                Box(Modifier.fillMaxWidth().height(460.dp)) {
                    Artwork(
                        image, Modifier.fillMaxSize().graphicsLayer {
                            translationY = scroll * 0.45f
                            val s = 1f + (-scroll).coerceAtLeast(0f) / 1200f
                            scaleX = s; scaleY = s
                        },
                        corner = 0.dp, size = 1200,
                    )
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.30f), 0.3f to Color.Transparent, 0.55f to Bop.Base.copy(alpha = 0f), 0.82f to Bop.Base.copy(alpha = 0.82f), 1f to Bop.Base)))
                    Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 18.dp)) {
                        Text((page?.subtitle ?: "Artist").uppercase(), style = BopType.Micro, color = Bop.Text2)
                        Text(title, style = BopType.Hero.copy(fontSize = BopType.Hero.fontSize * 1.2f), color = Bop.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            PillButton("Play", Icons.Rounded.PlayArrow, Modifier.width(112.dp)) { page?.topSongs?.let { PlayerConnection.play(it, 0, title) } }
                            PillButton("Radio", Icons.Rounded.Radio, Modifier.width(112.dp), filled = false) {
                                page?.topSongs?.firstOrNull()?.let { PlayerConnection.startRadio(it, "$title Radio") }
                            }
                            val saved = id in lib.savedIds
                            GlassIconButton(if (saved) Icons.Rounded.Check else Icons.Rounded.PersonAdd, "Follow", size = 46.dp, active = saved) {
                                app.toast(if (LibraryStore.toggleSaved(card)) "Following $title" else "Unfollowed $title")
                            }
                        }
                    }
                }
            }
            when (val s = load.state) {
                is Load.Loading -> items(6) { RowSkeleton() }
                is Load.Failed -> item { ErrorState(s.message, onRetry = load.retry) }
                is Load.Ready -> {
                    val p = s.value
                    if (p.topSongs.isNotEmpty()) {
                        item { SectionHeader("Top songs", action = if (p.topSongs.size > 5) "See all" else null) { app.seeAll("$title · Top songs", p.topSongs) } }
                        val top = p.topSongs.take(5)
                        items(top.size) { i ->
                            val t = top[i]
                            TrackRow(t, index = i + 1, playing = current?.id == t.id, onMore = { app.sheet = Sheet.TrackOptions(t) }) {
                                PlayerConnection.play(p.topSongs, i, title)
                            }
                        }
                    }
                    if (p.albums.isNotEmpty()) item { SectionHeader("Albums & singles"); CardRow(p.albums, 160.dp) }
                    if (p.similar.isNotEmpty()) item { SectionHeader("Fans also like"); CardRow(p.similar, 120.dp) }
                }
            }
        }
        TopBlurBar(haze, titleAlpha, title, showBack = true, onBack = { app.nav.popBackStack() })
    }
}
