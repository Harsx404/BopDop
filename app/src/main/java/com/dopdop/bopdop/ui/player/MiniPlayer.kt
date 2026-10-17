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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.ui.graphics.drawscope.scale
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
            Artwork(
                t.artwork,
                Modifier.size(46.dp).onGloballyPositioned { c ->
                    // Only at rest — while the player is open the chrome is faded/moved.
                    if (app.player.value == 0f && dragX.value == 0f) app.miniArt = c.boundsInRoot()
                },
                corner = 14.dp, size = 226,
            )
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
        MiniPet(progress, playing, Modifier.align(Alignment.BottomStart).padding(horizontal = 18.dp))
    }
}

/** The seek bar pet, smaller, trotting along the mini player's progress line. */
@Composable
private fun MiniPet(progress: Float, playing: Boolean, modifier: Modifier) {
    val lib by com.dopdop.bopdop.data.LibraryStore.state.collectAsState()
    val pet = lib.settings.seekPet
    if (pet == com.dopdop.bopdop.data.SeekPet.NONE) return
    val reduce = com.dopdop.bopdop.ui.glass.LocalReduceMotion.current
    // Progress arrives in steps; glide between them so the pet walks instead of teleporting.
    val x by androidx.compose.animation.core.animateFloatAsState(progress, androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.LinearEasing), label = "miniPet")
    val time by androidx.compose.runtime.produceState(0f, reduce) {
        if (reduce) return@produceState
        val start = androidx.compose.animation.core.withInfiniteAnimationFrameMillis { it }
        while (true) androidx.compose.animation.core.withInfiniteAnimationFrameMillis { value = (it - start) / 1000f }
    }
    androidx.compose.foundation.Canvas(modifier.fillMaxWidth().height(22.dp)) {
        val px = size.width * x
        val feet = size.height - 1.dp.toPx()
        scale(0.72f, androidx.compose.ui.geometry.Offset(px, feet)) {
            drawPet(pet, px, feet, time, if (playing) PetMode.RUN else PetMode.SIT, 1f)
        }
    }
}
