package com.dopdop.bopdop.ui.player

import android.view.TextureView
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.playback.VideoPlaybackState
import com.dopdop.bopdop.ui.glass.softShadow
import com.dopdop.bopdop.ui.glass.mediaGlass
import com.dopdop.bopdop.ui.theme.BopType
import dev.chrisbanes.haze.HazeState

/**
 * Song ⇄ Video, as a liquid-glass capsule like the transport droplets: the glass track
 * carries a white thumb that slides under the selected mode.
 */
@Composable
fun VideoModeSwitch(video: VideoPlaybackState, haze: HazeState, modifier: Modifier = Modifier) {
    val isVideo = video.active || video.loading
    val segment = 96.dp
    val thumbX by animateDpAsState(if (isVideo) segment else 0.dp, spring(dampingRatio = 0.72f, stiffness = 520f), label = "modeThumb")
    val shape = RoundedCornerShape(50)
    val press = remember { MutableInteractionSource() }
    Box(modifier.mediaGlass(shape, haze, emphasis = 1.1f, interactionSource = press, strong = true).padding(4.dp)) {
        Box(
            Modifier.offset(x = thumbX).width(segment).height(38.dp)
                .softShadow(shape, 10.dp, color = Color.Black, alpha = 0.14f, dy = 2.dp)
                .clip(shape).background(Color.White),
        )
        Row {
            listOf(false, true).forEach { option ->
                val selected = option == isVideo
                Row(
                    Modifier.width(segment).height(38.dp).clip(shape)
                        .selectable(selected, interactionSource = press, indication = null, role = Role.Tab) { PlayerConnection.setVideoEnabled(option) },
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                ) {
                    val ink = if (selected) Color(0xFF15161B) else Color.White
                    if (option && video.loading) CircularProgressIndicator(Modifier.size(14.dp), color = ink, strokeWidth = 2.dp)
                    else Icon(if (option) Icons.Rounded.Videocam else Icons.Rounded.MusicNote, null, Modifier.size(17.dp), tint = ink)
                    Text(if (option && video.loading) "Finding…" else if (option) "Video" else "Song", style = BopType.Caption, color = ink, maxLines = 1)
                }
            }
        }
    }
}

/**
 * The music video standing in for the cover: same full-bleed frame, centre-cropped to fill it
 * the way the artwork is, so the hero's own fade carries it into the wash.
 */
@Composable
fun MusicVideoHero(video: VideoPlaybackState, modifier: Modifier = Modifier) {
    val playing by PlayerConnection.isPlaying.collectAsState()
    val buffering by PlayerConnection.buffering.collectAsState()
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds().background(Color.Black), contentAlignment = Alignment.Center) {
        val ar = video.aspectRatio.coerceIn(0.3f, 3f)
        // Cover-fit: whichever side is short grows until the frame is filled.
        val picH = maxOf(maxHeight, maxWidth / ar)
        val picW = picH * ar
        AndroidView(
            factory = { context -> TextureView(context).also(PlayerConnection::attachVideoSurface) },
            modifier = Modifier.requiredSize(picW, picH),
            onReset = null,
            onRelease = { it.keepScreenOn = false; PlayerConnection.detachVideoSurface(it) },
            update = { it.keepScreenOn = playing; PlayerConnection.attachVideoSurface(it) },
        )
        if (buffering) CircularProgressIndicator(Modifier.size(32.dp), color = Color.White, strokeWidth = 2.dp)
    }
}
