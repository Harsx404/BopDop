package com.dopdop.bopdop.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.ui.AppState
import com.dopdop.bopdop.ui.components.Artwork
import com.dopdop.bopdop.ui.components.MarqueeText
import com.dopdop.bopdop.ui.components.PlayPauseGlyph
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.glass.glass
import com.dopdop.bopdop.ui.glass.rememberArtPalette
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun MiniPlayer(app: AppState, modifier: Modifier = Modifier) {
    val track by PlayerConnection.current.collectAsState()
    val playing by PlayerConnection.isPlaying.collectAsState()
    val t = track ?: return
    val palette = rememberArtPalette(t.artwork)
    val press = remember { MutableInteractionSource() }
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(t.id) {
        while (true) {
            val d = PlayerConnection.duration
            progress = if (d > 0) (PlayerConnection.position.toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(300)
        }
    }
    val dragX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer {
                translationX = dragX.value
                alpha = 1f - (abs(dragX.value) / 900f).coerceIn(0f, 0.6f)
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val v = dragX.value
                            when {
                                v < -140f -> PlayerConnection.next()
                                v > 140f -> PlayerConnection.previous()
                            }
                            dragX.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 500f))
                        }
                    },
                ) { change, d -> change.consume(); scope.launch { dragX.snapTo(dragX.value + d * 0.85f) } }
            }
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total < -40f) app.expandPlayer() }) { change, d ->
                    change.consume(); total += d
                }
            }
            .glass(shape, emphasis = 1.3f, shadow = 18.dp, interactionSource = press)
            .bounceClick(0.98f, interactionSource = press) { app.expandPlayer() },
    ) {
        // A whisper of the artwork's colour washing through the glass.
        // (Clipped: the glass layer doesn't clip its children, so an unclipped wash shows square corners.)
        Box(Modifier.matchParentSize().clip(shape).background(Brush.horizontalGradient(listOf(palette.wash.copy(alpha = 0.22f), Color.Transparent))))
        Row(Modifier.fillMaxHeight().padding(start = 9.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(t.artwork, Modifier.size(46.dp), corner = 14.dp, size = 226)
            Spacer(Modifier.width(12.dp))
            AnimatedContent(
                targetState = t, modifier = Modifier.weight(1f),
                transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut()) },
                label = "mini",
            ) { tr ->
                Column {
                    MarqueeText(tr.title, BopType.BodyStrong, Bop.Text)
                    Text(tr.artist, style = BopType.Caption, color = Bop.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.size(46.dp).clip(CircleShape).bounceClick(0.85f) { PlayerConnection.toggle() }, contentAlignment = Alignment.Center) {
                PlayPauseGlyph(playing, Modifier.size(22.dp), color = Bop.Ink)
            }
            Box(Modifier.size(46.dp).clip(CircleShape).bounceClick(0.85f) { PlayerConnection.next() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.FastForward, "Next", tint = Bop.Text, modifier = Modifier.size(28.dp))
            }
        }
        // Progress hairline
        Box(Modifier.align(Alignment.BottomStart).padding(horizontal = 18.dp).fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).background(Color.Black.copy(alpha = 0.07f))) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Brush.horizontalGradient(listOf(palette.wash, palette.washDeep))))
        }
    }
}
