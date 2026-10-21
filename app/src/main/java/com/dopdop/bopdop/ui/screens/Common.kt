package com.dopdop.bopdop.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dopdop.bopdop.data.model.Card
import com.dopdop.bopdop.data.model.Shelf
import com.dopdop.bopdop.data.model.ShelfStyle
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.ui.LocalApp
import com.dopdop.bopdop.ui.LocalChromePadding
import com.dopdop.bopdop.ui.Sheet
import com.dopdop.bopdop.ui.components.CardTile
import com.dopdop.bopdop.ui.components.SectionHeader
import com.dopdop.bopdop.ui.components.TrackRow
import com.dopdop.bopdop.ui.components.TrackTile
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.components.riseIn
import com.dopdop.bopdop.ui.glass.glass
import com.dopdop.bopdop.ui.glass.glassLite
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * Large-title page: the title scrolls away and reappears small in a
 * progressively blurred top bar (no hard edge — the content dissolves into it).
 */
@Composable
fun LargeTitleScaffold(
    title: String,
    eyebrow: String = "",
    state: LazyListState = rememberLazyListState(),
    showBack: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val haze = rememberHazeState()
    val app = LocalApp.current
    val collapsed by remember { derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 140 } }
    val titleAlpha by animateFloatAsState(if (collapsed) 1f else 0f, label = "ta")
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().hazeSource(haze),
            contentPadding = LocalChromePadding.current,
        ) {
            item(key = "__title") {
                Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = if (showBack) 52.dp else 14.dp, start = 20.dp, end = 16.dp)) {
                    if (eyebrow.isNotEmpty()) Text(eyebrow.uppercase(), style = BopType.Micro, color = Bop.Text3)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = BopType.Hero, color = Bop.Text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        trailing()
                    }
                }
            }
            content()
        }
        TopBlurBar(haze, titleAlpha, title, showBack = showBack, onBack = { app.nav.popBackStack() })
    }
}

@Composable
fun TopBlurBar(
    haze: dev.chrisbanes.haze.HazeState,
    titleAlpha: Float,
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val style = remember {
        HazeBlurStyle {
            blurRadius(22.dp)
            backgroundColor(Bop.Pearl)
            colorEffects(listOf(HazeColorEffect.tint(Bop.Pearl.copy(alpha = 0.55f))))
            // Fade the whole effect (blur + tint) out toward the bottom: no hard edge.
            mask(Brush.verticalGradient(0f to Color.Black, 0.55f to Color.Black, 1f to Color.Transparent))
        }
    }
    Box(Modifier.fillMaxWidth()) {
        // At rest only a soft scrim keeps the status bar legible; the glass bar fades in on scroll.
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Brush.verticalGradient(listOf(Bop.Pearl.copy(alpha = 0.7f), Color.Transparent))))
        if (titleAlpha > 0.01f) Box(
            Modifier.matchParentSizeOrBar()
                .graphicsLayer { alpha = titleAlpha }
                .hazeBlur(HazeInput.Sources(haze), style),
        )
        Row(
            Modifier.windowInsetsPadding(WindowInsets.statusBars).fillMaxWidth().height(52.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) BackButton(haze, onBack) else Spacer(Modifier.width(40.dp))
            Text(
                title, style = BopType.Headline, color = Bop.Text.copy(alpha = titleAlpha), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Row(Modifier.width(40.dp), horizontalArrangement = Arrangement.End) { trailing() }
        }
    }
}

@Composable
private fun Modifier.matchParentSizeOrBar(): Modifier {
    val top = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    return this.fillMaxWidth().height(top + 72.dp)
}

@Composable
fun BackButton(haze: dev.chrisbanes.haze.HazeState? = null, onBack: () -> Unit) {
    val press = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    // In a top bar it floats over the scrolling page: true glass that refracts what slides beneath.
    val surface = if (haze != null) Modifier.glass(CircleShape, state = haze, emphasis = 1.5f, shadow = 10.dp, interactionSource = press, strong = true)
    else Modifier.glassLite(CircleShape, alpha = 0.7f, shadow = 10.dp)
    Box(Modifier.size(42.dp).bounceClick(0.88f, interactionSource = press, onClick = onBack).then(surface), contentAlignment = Alignment.Center) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, "Back", tint = Bop.Text, modifier = Modifier.size(18.dp).padding(start = 4.dp))
    }
}

// --- Shelves -------------------------------------------------------------------------

fun LazyListScope.shelf(shelf: Shelf, key: String) {
    // Each shelf rises into place the first time it scrolls into view.
    item(key = key) {
        Box(Modifier.riseIn()) {
            when (shelf.style) {
                ShelfStyle.QUICK_PICKS, ShelfStyle.TRACKS -> QuickPicksShelf(shelf)
                ShelfStyle.ARTISTS -> CardsShelf(shelf, 120.dp)
                ShelfStyle.LARGE_CARDS -> CardsShelf(shelf, 210.dp)
                ShelfStyle.CARDS -> if (shelf.cards.isEmpty() && shelf.tracks.isNotEmpty()) TrackTilesShelf(shelf) else CardsShelf(shelf, 152.dp)
            }
        }
    }
}

@Composable
fun QuickPicksShelf(shelf: Shelf) {
    val app = LocalApp.current
    val current by PlayerConnection.current.collectAsState()
    val columns = remember(shelf) { shelf.tracks.chunked(4) }
    Column {
        SectionHeader(shelf.title, shelf.subtitle, action = if (shelf.tracks.size > 4) "See all" else null) { app.seeAll(shelf.title, shelf.tracks) }
        LazyRow(contentPadding = PaddingValues(end = 20.dp)) {
            items(columns.size) { ci ->
                Column(Modifier.width(330.dp)) {
                    columns[ci].forEachIndexed { i, t ->
                        TrackRow(
                            t, playing = current?.id == t.id, swipe = false,
                            onMore = { app.sheet = Sheet.TrackOptions(t) },
                        ) { PlayerConnection.play(shelf.tracks, ci * 4 + i, shelf.title) }
                    }
                }
            }
        }
    }
}

@Composable
fun CardsShelf(shelf: Shelf, size: Dp) {
    val app = LocalApp.current
    Column {
        SectionHeader(shelf.title, shelf.subtitle, action = if (shelf.cards.size > 6) "See all" else null) { app.seeAll(shelf.title, shelf.cards) }
        CardRow(shelf.cards, size)
    }
}

@Composable
fun CardRow(cards: List<Card>, size: Dp = 152.dp) {
    val app = LocalApp.current
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(cards, key = { it.id }) { c ->
            CardTile(c, width = size, onLongClick = { app.holdCard(c) }) { app.open(c) }
        }
    }
}

@Composable
fun TrackTilesShelf(shelf: Shelf) {
    val app = LocalApp.current
    val current by PlayerConnection.current.collectAsState()
    Column {
        SectionHeader(shelf.title, shelf.subtitle, action = if (shelf.tracks.size > 6) "See all" else null) { app.seeAll(shelf.title, shelf.tracks) }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(shelf.tracks.size) { i ->
                val t = shelf.tracks[i]
                TrackTile(t, playing = current?.id == t.id, onLongClick = { app.holdTrack(t) }) {
                    PlayerConnection.play(shelf.tracks, i, shelf.title)
                }
            }
        }
    }
}

fun LazyListScope.trackItems(tracks: List<Track>, context: String, currentId: String?, numbered: Boolean = false, playlistId: String? = null, onSheet: (Sheet) -> Unit) {
    items(tracks.size, key = { "t$it:" + tracks[it].id }) { i ->
        val t = tracks[i]
        TrackRow(
            t,
            index = if (numbered) i + 1 else null,
            showArt = !numbered,
            playing = currentId == t.id,
            onMore = { onSheet(Sheet.TrackOptions(t, playlistId)) },
        ) { PlayerConnection.play(tracks, i, context) }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.08f)))
