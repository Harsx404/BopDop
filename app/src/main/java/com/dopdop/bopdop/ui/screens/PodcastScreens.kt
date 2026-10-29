package com.dopdop.bopdop.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.MusicRepository
import com.dopdop.bopdop.data.model.Card
import com.dopdop.bopdop.data.model.CardKind
import com.dopdop.bopdop.data.model.Episode
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.ui.LocalApp
import com.dopdop.bopdop.ui.Sheet
import com.dopdop.bopdop.ui.components.Artwork
import com.dopdop.bopdop.ui.components.ErrorState
import com.dopdop.bopdop.ui.components.Load
import com.dopdop.bopdop.ui.components.PillButton
import com.dopdop.bopdop.ui.components.ShelfSkeleton
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.components.rememberLoad
import com.dopdop.bopdop.ui.components.riseIn
import com.dopdop.bopdop.ui.components.shimmer
import com.dopdop.bopdop.ui.festive.ThemedIcon as Icon
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType

/** Podcasts from YouTube Music and JioSaavn: popular shows and episodes, trending Indian shows, then shelves by topic. */
@Composable
fun PodcastsScreen() {
    val feed = rememberLoad("podcasts", 3 * 3600_000L) { MusicRepository.podcasts() }
    LargeTitleScaffold(title = "Podcasts", eyebrow = "YouTube Music · JioSaavn", showBack = true) {
        when (val s = feed.state) {
            is Load.Loading -> items(3) { ShelfSkeleton() }
            is Load.Failed -> item { ErrorState(s.message, onRetry = feed.retry) }
            is Load.Ready -> s.value.filter { it.tracks.size + it.cards.size >= 2 }.forEachIndexed { i, sh -> shelf(sh, "pod$i${sh.title}") }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

/** One show: who makes it, what it's about, and its episodes, newest first. */
@Composable
fun PodcastScreen(id: String, title: String, art: String) {
    val app = LocalApp.current
    val show = rememberLoad("podcast:$id", 30 * 60_000L) { MusicRepository.podcastShow(id) }
    val lib by LibraryStore.state.collectAsState()
    val current by PlayerConnection.current.collectAsState()
    val ready = (show.state as? Load.Ready)?.value
    val name = ready?.title ?: title
    val cover = ready?.artwork?.ifBlank { null } ?: art
    LargeTitleScaffold(title = name, eyebrow = "Podcast", showBack = true) {
        item(key = "head") {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(cover, Modifier.size(132.dp), corner = 18.dp, size = 600, elevation = 10.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    ready?.author?.takeIf { it.isNotBlank() }?.let { a ->
                        Text(a, style = BopType.BodyStrong, color = Bop.Text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.bounceClick { ready.authorId?.let { app.openArtist(it, a) } })
                    }
                    ready?.let { Text("${it.episodes.size} episodes", style = BopType.Caption, color = Bop.Text2, modifier = Modifier.padding(top = 2.dp)) }
                }
            }
            ready?.description?.takeIf { it.isNotBlank() }?.let { About(it) }
            ready?.let { s ->
                val card = Card(s.id, CardKind.PLAYLIST, s.title, s.author, s.artwork)
                val saved = card.id in lib.savedIds
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Play latest", Icons.Rounded.PlayArrow, Modifier.weight(1f)) {
                        if (s.episodes.isNotEmpty()) PlayerConnection.play(s.episodes.map { it.track }, 0, s.title)
                    }
                    PillButton(if (saved) "Saved" else "Save", if (saved) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd, Modifier.weight(1f), filled = false) {
                        app.toast(if (LibraryStore.toggleSaved(card)) "Saved to Library" else "Removed from Library")
                    }
                }
            }
        }
        when (val s = show.state) {
            is Load.Loading -> items(6) { EpisodeSkeleton() }
            is Load.Failed -> item { ErrorState(s.message, onRetry = show.retry) }
            is Load.Ready -> {
                val eps = s.value.episodes
                if (eps.isEmpty()) item { Text("No episodes yet.", style = BopType.Body, color = Bop.Text2, modifier = Modifier.padding(28.dp)) }
                items(eps.size, key = { "ep" + eps[it].track.id }) { i ->
                    EpisodeRow(eps[i], playing = current?.id == eps[i].track.id, order = i, onMore = { app.sheet = Sheet.TrackOptions(eps[i].track) }) {
                        // From this episode on, the way the show lists them.
                        PlayerConnection.play(eps.map { it.track }, i, s.value.title)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

/** The show's blurb: three lines, the rest a tap away. */
@Composable
private fun About(text: String) {
    var open by remember { mutableStateOf(false) }
    Text(
        text, style = BopType.Body, color = Bop.Text2, maxLines = if (open) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).animateContentSize().bounceClick(0.99f) { open = !open },
    )
}

/** "1 hr 14 min", "37 min". */
internal fun spokenLength(sec: Int): String = when {
    sec <= 0 -> ""
    sec >= 3600 -> "${sec / 3600} hr" + (sec % 3600 / 60).let { if (it > 0) " $it min" else "" }
    else -> "${(sec / 60).coerceAtLeast(1)} min"
}

@Composable
private fun EpisodeRow(e: Episode, playing: Boolean, order: Int, onMore: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.riseIn(order.coerceAtMost(8)).fillMaxWidth().bounceClick(0.98f, onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box {
            Artwork(e.track.artwork, Modifier.width(118.dp).aspectRatio(16f / 9f), corner = 10.dp, size = 400)
            spokenLength(e.track.durationSec).takeIf { it.isNotEmpty() }?.let {
                Text(it, style = BopType.Micro.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified), color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.62f)).padding(horizontal = 5.dp, vertical = 2.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.track.title, style = BopType.BodyStrong, color = if (playing) Bop.Accent else Bop.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (e.date.isNotBlank()) Text(e.date, style = BopType.Caption, color = Bop.Text3, modifier = Modifier.padding(top = 2.dp))
            if (e.description.isNotBlank()) Text(e.description.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(), style = BopType.Caption, color = Bop.Text2,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(Icons.Rounded.MoreHoriz, "More", tint = Bop.Text2, modifier = Modifier.padding(start = 4.dp).size(22.dp).bounceClick(0.85f, onClick = onMore))
    }
}

@Composable
private fun EpisodeSkeleton() {
    Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Box(Modifier.width(118.dp).aspectRatio(16f / 9f).shimmer(10.dp))
        Spacer(Modifier.width(12.dp))
        Column { Box(Modifier.size(180.dp, 14.dp).shimmer(4.dp)); Spacer(Modifier.height(6.dp)); Box(Modifier.size(110.dp, 12.dp).shimmer(4.dp)) }
    }
}
