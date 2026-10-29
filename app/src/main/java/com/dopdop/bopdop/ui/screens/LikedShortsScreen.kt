package com.dopdop.bopdop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dopdop.bopdop.data.FeedShort
import com.dopdop.bopdop.data.ShortsStore
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.ui.components.Artwork
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.festive.ThemedIcon as Icon
import com.dopdop.bopdop.ui.player.ShortsViewer
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType

/** A persistent way back to liked clips, without first needing a song with Shorts. */
@Composable
fun LikedShortsScreen() {
    val saved by ShortsStore.state.collectAsState()
    var opened by remember { mutableStateOf<FeedShort?>(null) }
    LargeTitleScaffold("Liked Shorts", "${saved.liked.size} saved on this device", showBack = true) {
        saved.error?.let { message -> item { Text(message, color = Bop.Text2, modifier = Modifier.padding(20.dp)) } }
        if (saved.liked.isEmpty()) item {
            Text("Tap the heart while watching a Short to save it here.", style = BopType.BodyStrong,
                color = Bop.Text2, modifier = Modifier.padding(20.dp))
        }
        items(saved.liked, key = { it.item.clip.id }) { liked ->
            val item = liked.item
            Row(Modifier.fillMaxWidth().bounceClick { opened = item }.padding(horizontal = 20.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Artwork(item.clip.thumbnail, Modifier.size(56.dp, 84.dp), corner = 10.dp, size = 240)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(item.clip.title, style = BopType.BodyStrong, color = Bop.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (item.song.isShortAudio) "Original audio" else item.song.title, style = BopType.Caption,
                        color = Bop.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Rounded.Favorite, "Unlike Short", tint = Bop.Text,
                    modifier = Modifier.size(44.dp).bounceClick { ShortsStore.toggleLike(item) }.padding(10.dp))
            }
        }
    }
    opened?.let { item ->
        ShortsViewer(item.song, listOf(item.clip)) { song ->
            PlayerConnection.continueFromShorts(song)
            opened = null
        }
    }
}
