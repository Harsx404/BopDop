package com.dopdop.bopdop.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import com.dopdop.bopdop.ui.festive.ThemedIcon as Icon
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.model.CardKind
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.ui.AppState
import com.dopdop.bopdop.ui.HoldMenu
import com.dopdop.bopdop.ui.Sheet
import com.dopdop.bopdop.ui.components.Artwork
import com.dopdop.bopdop.ui.glass.glass
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType

/**
 * Long-press on a song, album, playlist or artist: the screen frosts over, the item lifts out as a
 * big card, and its most-used actions sit right under it. "More…" opens the full sheet.
 */
@Composable
fun HoldMenuHost(app: AppState) {
    val hold = app.hold
    var shown by remember { mutableStateOf<HoldMenu?>(null) }
    if (hold != null) shown = hold
    val close = { app.hold = null }
    androidx.activity.compose.BackHandler(enabled = hold != null, onBack = close)
    AnimatedVisibility(hold != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
        val h = shown ?: return@AnimatedVisibility
        Box(
            Modifier.fillMaxSize()
                .glass(RectangleShape, emphasis = 1.6f, shadow = 0.dp, rim = false)
                .background(Color.Black.copy(alpha = 0.25f))
                .clickable(remember { MutableInteractionSource() }, null, onClick = close),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.padding(24.dp).widthIn(max = 340.dp).animateEnterExit(
                    enter = scaleIn(spring(dampingRatio = 0.7f, stiffness = 500f), initialScale = 0.86f),
                    exit = scaleOut(tween(140), targetScale = 0.92f),
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Preview(h)
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), emphasis = 1.4f)) {
                    Actions(app, h, close)
                }
            }
        }
    }
}

@Composable
private fun Preview(h: HoldMenu) {
    val art = h.track?.artwork ?: h.card?.artwork.orEmpty()
    val title = h.track?.title ?: h.card?.title.orEmpty()
    val sub = h.track?.let { listOf(it.artist, it.album).filter(String::isNotBlank).joinToString(" · ") } ?: h.card?.subtitle.orEmpty()
    val round = h.card?.kind == CardKind.ARTIST
    Artwork(art, Modifier.size(220.dp), corner = 26.dp, circle = round, size = 700, elevation = 22.dp)
    Spacer(Modifier.height(14.dp))
    Text(title, style = BopType.Title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (sub.isNotBlank()) Text(sub, style = BopType.Body, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun Actions(app: AppState, h: HoldMenu, close: () -> Unit) {
    val ctx = LocalContext.current
    val lib by LibraryStore.state.collectAsState()
    @Composable
    fun Item(icon: ImageVector, label: String, tint: Color = Bop.Text, last: Boolean = false, onClick: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = BopType.BodyStrong, color = tint)
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        if (!last) Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(0.5.dp).background(Bop.Ink.copy(alpha = 0.08f)))
    }
    val t = h.track
    val c = h.card
    if (t != null) {
        val liked = t.id in lib.likedIds
        Item(Icons.Rounded.SkipNext, "Play next") { PlayerConnection.playNext(t); app.toast("Playing next"); close() }
        Item(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") { PlayerConnection.addToQueue(listOf(t)); app.toast("Added to queue"); close() }
        Item(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) "Remove from Liked" else "Like", if (liked) Color(0xFFFF4D7E) else Bop.Text) {
            LibraryStore.toggleLike(t); close()
        }
        Item(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist…") { close(); app.sheet = Sheet.AddToPlaylist(listOf(t)) }
        if (t.source != com.dopdop.bopdop.data.model.Source.LOCAL) {
            Item(Icons.Rounded.Download, "Download") { com.dopdop.bopdop.playback.Downloads.download(ctx, listOf(t)); app.toast("Downloading"); close() }
        }
        Item(Icons.Rounded.Share, "Share") { close(); app.shareTrack = t }
    } else if (c != null) {
        val saved = c.id in lib.savedIds
        Item(Icons.Rounded.OpenInNew, "Open") { close(); app.open(c) }
        Item(if (saved) Icons.Rounded.BookmarkRemove else Icons.Rounded.BookmarkAdd, if (saved) "Remove from Library" else "Save to Library") {
            val now = LibraryStore.toggleSaved(c); app.toast(if (now) "Saved to your library" else "Removed from your library"); close()
        }
    }
    Item(Icons.Rounded.MoreHoriz, "More…", last = true) { close(); h.more() }
}
