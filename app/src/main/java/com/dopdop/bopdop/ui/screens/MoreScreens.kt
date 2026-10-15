package com.dopdop.bopdop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.MusicRepository
import com.dopdop.bopdop.data.model.Card
import com.dopdop.bopdop.data.model.CardKind
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.model.songs
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.ui.LocalApp
import com.dopdop.bopdop.ui.SeeAllHolder
import com.dopdop.bopdop.ui.Sheet
import com.dopdop.bopdop.ui.components.Artwork
import com.dopdop.bopdop.ui.components.CardTile
import com.dopdop.bopdop.ui.components.ErrorState
import com.dopdop.bopdop.ui.components.GlassIconButton
import com.dopdop.bopdop.ui.components.Load
import com.dopdop.bopdop.ui.components.SectionHeader
import com.dopdop.bopdop.ui.components.ShelfSkeleton
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.components.rememberLoad
import com.dopdop.bopdop.ui.glass.glassEdges
import com.dopdop.bopdop.ui.glass.glassLite
import com.dopdop.bopdop.ui.glass.softShadow
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType

@Composable
fun MoodScreen(title: String, params: String) {
    val load = rememberLoad("mood:$params", 60 * 60_000L) { MusicRepository.moodCategory(params) }
    LargeTitleScaffold(title = title, eyebrow = "Mood & genre", showBack = true) {
        when (val s = load.state) {
            is Load.Loading -> items(3) { ShelfSkeleton() }
            is Load.Failed -> item { ErrorState(s.message, onRetry = load.retry) }
            is Load.Ready -> s.value.forEachIndexed { i, sh -> shelf(sh, "m$i${sh.title}") }
        }
    }
}

@Suppress("UNCHECKED_CAST")
@Composable
fun SeeAllScreen(key: String) {
    val app = LocalApp.current
    val current by PlayerConnection.current.collectAsState()
    val (title, items) = SeeAllHolder.get(key) ?: ("" to emptyList<Any>())
    val list = items as List<Any>
    LargeTitleScaffold(title = title, showBack = true) {
        if (list.firstOrNull() is Track) {
            trackItems(list as List<Track>, title, current?.id) { app.sheet = it }
        } else cardGridItems(list as List<Card>)
    }
}

fun LazyListScope.cardGridItems(cards: List<Card>) {
    val rows = cards.chunked(2)
    items(rows.size) { i ->
        val app = LocalApp.current
        Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            rows[i].forEach { c ->
                BoxWithConstraints(Modifier.weight(1f)) {
                    CardTile(c, width = maxWidth, onLongClick = { app.sheet = Sheet.CardOptions(c) }) { app.open(c) }
                }
            }
            if (rows[i].size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

