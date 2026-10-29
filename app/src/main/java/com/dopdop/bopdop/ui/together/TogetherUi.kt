package com.dopdop.bopdop.ui.together

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dopdop.bopdop.R
import com.dopdop.bopdop.data.together.ChatMessage
import com.dopdop.bopdop.data.together.Reaction
import com.dopdop.bopdop.data.together.Together
import com.dopdop.bopdop.ui.AppState
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.glass.LocalReduceMotion
import com.dopdop.bopdop.ui.glass.glassLite
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import kotlinx.coroutines.delay
import kotlin.math.sin

/** A colour per person, steady across phones since it comes from their name. */
private val AVATAR_COLORS = listOf(
    Color(0xFFFF8A3D), Color(0xFF7A4DFF), Color(0xFF2EB67D), Color(0xFFE0359B),
    Color(0xFF3D8BFF), Color(0xFFE5B12E), Color(0xFF00A8A8), Color(0xFFEF5350),
)

fun avatarColor(name: String): Color = AVATAR_COLORS[(name.trim().lowercase().hashCode() and 0x7fffffff) % AVATAR_COLORS.size]

/** A round initial in the person's colour, with an optional ring (the host, or you). */
@Composable
fun Avatar(name: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, ring: Color? = null) {
    Box(
        modifier.size(size).then(if (ring != null) Modifier.border(2.dp, ring, CircleShape).padding(3.dp) else Modifier)
            .clip(CircleShape).background(avatarColor(name)),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.trim().take(1).uppercase().ifEmpty { "?" }, style = BopType.BodyStrong.copy(fontSize = (size.value * 0.42f).sp), color = Color.White)
    }
}

/**
 * A QR code that looks like part of the app: round dots, soft rounded finder eyes and the BopDop
 * mark in the middle. The eyes stay dark (scanners look for dark centres), and high error
 * correction keeps it scannable with the mark on it.
 */
@Composable
fun PrettyQr(text: String, modifier: Modifier = Modifier, ink: Color = Color(0xFF15161B), eye: Color = ink) {
    val m = remember(text) {
        Encoder.encode(text, ErrorCorrectionLevel.H, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 0)).matrix
    }
    val bg = painterResource(R.drawable.ic_launcher_bg)
    val fg = painterResource(R.drawable.ic_launcher_fg)
    BoxWithConstraints(modifier.clip(RoundedCornerShape(22.dp)).background(Color.White).padding(14.dp), contentAlignment = Alignment.Center) {
        val n = m.width
        // Room for the mark: a centred square about a fifth of the code, kept on whole modules.
        val hole = (n / 5).let { if (it % 2 == n % 2) it else it + 1 }
        val from = (n - hole) / 2
        Canvas(Modifier.fillMaxSize()) {
            val cell = size.minDimension / n
            fun inFinder(x: Int, y: Int) = (x < 7 && y < 7) || (x >= n - 7 && y < 7) || (x < 7 && y >= n - 7)
            for (y in 0 until n) for (x in 0 until n) {
                if (m[x, y].toInt() != 1 || inFinder(x, y)) continue
                if (x in from until from + hole && y in from until from + hole) continue
                drawCircle(ink, cell * 0.43f, Offset((x + 0.5f) * cell, (y + 0.5f) * cell))
            }
            finderEye(0f, 0f, cell, ink, eye)
            finderEye((n - 7) * cell, 0f, cell, ink, eye)
            finderEye(0f, (n - 7) * cell, cell, ink, eye)
        }
        val markDp = maxWidth * (hole.toFloat() / n) * 0.9f
        Box(Modifier.size(markDp).clip(RoundedCornerShape(markDp * 0.28f)), contentAlignment = Alignment.Center) {
            Image(bg, null, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f })
            Image(fg, "BopDop", Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f })
        }
    }
}

/** A finder pattern as a rounded ring around a rounded accent centre. */
private fun DrawScope.finderEye(x: Float, y: Float, cell: Float, ink: Color, eye: Color) {
    val outer = 7 * cell
    drawRoundRect(ink, Offset(x, y), Size(outer, outer), CornerRadius(cell * 2.2f))
    drawRoundRect(Color.White, Offset(x + cell, y + cell), Size(5 * cell, 5 * cell), CornerRadius(cell * 1.5f))
    drawRoundRect(eye, Offset(x + 2 * cell, y + 2 * cell), Size(3 * cell, 3 * cell), CornerRadius(cell * 1.1f))
}

/**
 * Over every screen while you're in a room: each reaction floats up the right edge with who
 * sent it, and a new chat message slides in at the top for a few seconds (tap it to open the
 * room). Nothing shows on the room screen's own chat, which already has them.
 */
@Composable
fun TogetherOverlay(app: AppState, onRoomScreen: Boolean) {
    val room by Together.state.collectAsState()
    if (!room.live) return
    val reduce = LocalReduceMotion.current
    // Each reaction is shown once, from the moment it arrives.
    val floating = remember { mutableStateListOf<Reaction>() }
    var seenReaction by remember { mutableStateOf(room.reactions.lastOrNull()?.seq ?: 0L) }
    LaunchedEffect(room.reactions) {
        room.reactions.filter { it.seq > seenReaction }.forEach { floating += it }
        seenReaction = room.reactions.lastOrNull()?.seq ?: seenReaction
    }
    var banner by remember { mutableStateOf<ChatMessage?>(null) }
    var seenChat by remember { mutableStateOf(room.chat.lastOrNull()?.seq ?: 0L) }
    LaunchedEffect(room.chat, onRoomScreen) {
        val fresh = room.chat.lastOrNull { it.seq > seenChat && !it.mine }
        seenChat = room.chat.lastOrNull()?.seq ?: seenChat
        if (fresh != null && !onRoomScreen) { banner = fresh; delay(4200); if (banner == fresh) banner = null }
    }
    Box(Modifier.fillMaxSize()) {
        // The room screen shows reactions over each person's seat instead.
        if (onRoomScreen) floating.clear() else floating.forEach { r ->
            key(r.seq) { FloatingReaction(r, reduce) { floating.remove(r) } }
        }
        AnimatedVisibility(
            banner != null && !onRoomScreen,
            enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(top = 8.dp, start = 14.dp, end = 14.dp),
        ) {
            val c = banner ?: return@AnimatedVisibility
            Row(
                Modifier.fillMaxWidth().glassLite(RoundedCornerShape(22.dp), alpha = 0.92f, shadow = 16.dp)
                    .bounceClick(0.97f) { banner = null; app.collapsePlayer(); app.nav.navigate("together?code=") { launchSingleTop = true } }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(c.name, 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = BopType.Caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(700)), color = avatarColor(c.name), maxLines = 1)
                    Text(c.text, style = BopType.Body, color = Bop.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** One emoji drifting up and swaying, its sender's name under it, fading out near the top. */
@Composable
private fun FloatingReaction(r: Reaction, reduce: Boolean, onDone: () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(if (reduce) 1600 else 3200, easing = LinearEasing)); onDone() }
    // Each one takes its own lane and sway, so a burst spreads out instead of stacking.
    val lane = (r.seq % 4).toInt()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight
        val v = p.value
        Column(
            Modifier.align(Alignment.BottomEnd)
                .padding(end = (18 + lane * 26).dp, bottom = 170.dp)
                .offset(x = if (reduce) 0.dp else (sin(v * 9f + lane) * 14f).dp, y = -(h * 0.5f) * v)
                .graphicsLayer {
                    alpha = (if (v < 0.1f) v / 0.1f else 1f) * (1f - ((v - 0.7f) / 0.3f).coerceIn(0f, 1f))
                    val s = if (v < 0.12f) 0.6f + v / 0.12f * 0.5f else 1.1f - (v - 0.12f) * 0.2f
                    scaleX = s; scaleY = s
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(r.emoji, fontSize = 38.sp)
            Text(
                if (r.mine) "You" else r.name, style = BopType.Micro.copy(letterSpacing = 0.sp), color = Color.White, maxLines = 1,
                modifier = Modifier.widthIn(max = 90.dp).clip(RoundedCornerShape(50)).background(avatarColor(r.name).copy(alpha = 0.9f)).padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}
