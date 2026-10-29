package com.dopdop.bopdop.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dopdop.bopdop.data.together.ChatMessage
import com.dopdop.bopdop.data.together.Reaction
import com.dopdop.bopdop.data.together.RoomStatus
import com.dopdop.bopdop.data.together.Together
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.ui.LocalApp
import com.dopdop.bopdop.ui.components.Artwork
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.festive.ThemedIcon as Icon
import com.dopdop.bopdop.ui.glass.LocalReduceMotion
import com.dopdop.bopdop.ui.glass.glassLite
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType
import com.dopdop.bopdop.ui.together.Avatar
import com.dopdop.bopdop.ui.together.PrettyQr
import com.dopdop.bopdop.ui.together.avatarColor
import kotlin.math.cos
import kotlin.math.sin

private const val CODE_LEN = 6

/** Someone on the stage: who, and whether they're the host or you. */
private data class Seat(val id: String, val name: String, val host: Boolean, val me: Boolean)

/**
 * Listen Together. The room is a circle: the song in the middle, everyone who's in it around the
 * edge, arriving and leaving as they join and go, their reactions popping up over their heads.
 */
@Composable
fun TogetherScreen(initialCode: String) {
    val room by Together.state.collectAsState()
    var name by remember { mutableStateOf(Together.name.ifBlank { android.os.Build.MODEL.take(24) }) }
    var code by remember { mutableStateOf(Together.normalize(initialCode)) }
    // A room link opened while in another room moves you to the new one.
    LaunchedEffect(initialCode) { if (initialCode.isNotBlank() && Together.normalize(initialCode) != room.code) Together.join(initialCode, name) }

    val seats = when (room.status) {
        // You always sit at the bottom of the ring; everyone else fills in around it.
        RoomStatus.LIVE -> room.members.map { Seat(it.id, it.name, it.host, it.id == room.meId) }.sortedByDescending { it.me }
        // Not in a room yet: only you, under the name you're typing.
        else -> listOf(Seat("me", name.ifBlank { "You" }, host = false, me = true))
    }
    val caption = when (room.status) {
        RoomStatus.IDLE -> "Just you so far"
        RoomStatus.CONNECTING -> "Joining ${room.code}…"
        RoomStatus.RECONNECTING -> "Reconnecting…"
        RoomStatus.WAITING -> "Waiting for the host to let you in"
        RoomStatus.LIVE -> if (room.members.size <= 1) "Waiting for friends to join" else "${room.members.size} listening"
    }

    LargeTitleScaffold(title = "Listen Together", showBack = true) {
        item(key = "stage") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Stage(seats, if (room.live) room.reactions else emptyList(), searching = room.status in setOf(RoomStatus.CONNECTING, RoomStatus.RECONNECTING, RoomStatus.WAITING))
                Text(caption, style = BopType.Caption, color = Bop.Text2)
            }
        }
        item(key = "body") {
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                when (room.status) {
                    RoomStatus.IDLE -> Lobby(name, { name = it.take(24) }, code, { code = Together.normalize(it).take(CODE_LEN) }, room.error)
                    RoomStatus.CONNECTING, RoomStatus.RECONNECTING, RoomStatus.WAITING -> Text(
                        "Cancel", style = BopType.BodyStrong, color = Bop.Text2,
                        modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(50)).bounceClick { Together.leave() }.padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                    RoomStatus.LIVE -> Live(room.code)
                }
            }
        }
    }
}

// --- The stage ----------------------------------------------------------------------------

/**
 * The song turning in the middle, and a seat on the ring for each person. Seats glide to make
 * room when someone joins, and each new person grows into theirs.
 */
@Composable
private fun Stage(seats: List<Seat>, reactions: List<Reaction>, searching: Boolean) {
    val reduce = LocalReduceMotion.current
    val track by PlayerConnection.current.collectAsState()
    val playing by PlayerConnection.isPlaying.collectAsState()
    // A slow turn while the music plays, like a record.
    val spin by produceState(0f, playing, reduce) {
        if (!playing || reduce) return@produceState
        val base = value
        val start = withFrameMillis { it }
        while (true) withFrameMillis { value = base + (it - start) / 1000f * 9f }
    }
    // When each reaction arrived here, so it rises and fades on its own clock.
    val arrived = remember { mutableStateMapOf<Long, Long>() }
    LaunchedEffect(reactions) { val now = System.currentTimeMillis(); reactions.forEach { arrived.getOrPut(it.seq) { now } } }
    val clock by produceState(System.currentTimeMillis(), reactions) {
        while (arrived.values.any { System.currentTimeMillis() - it < 2600 }) withFrameMillis { value = System.currentTimeMillis() }
    }

    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 24.dp).height(372.dp), contentAlignment = Alignment.Center) {
        val ring = 116.dp
        Canvas(Modifier.size(ring * 2)) {
            drawCircle(Bop.Ink.copy(alpha = 0.14f), size.minDimension / 2, style = Stroke(1.5.dp.toPx(),
                pathEffect = if (searching) PathEffect.dashPathEffect(floatArrayOf(10f, 12f), spin * 4f) else null))
        }
        // The song, or headphones before anything plays.
        Box(Modifier.size(132.dp).graphicsLayer { rotationZ = spin }.clip(CircleShape).background(Bop.Ink.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            val art = track?.artwork
            if (art != null) Artwork(art, Modifier.size(132.dp), circle = true, size = 400)
            else Icon(Icons.Rounded.Headphones, null, tint = Bop.Text2, modifier = Modifier.size(40.dp))
        }
        // A spindle hole, so a turning cover reads as a record rather than a broken image.
        if (track != null) Box(Modifier.size(14.dp).clip(CircleShape).background(Bop.Pearl))
        seats.forEachIndexed { i, s ->
            key(s.id) {
                // Evenly round the ring from the bottom (you), gliding when the count changes.
                val target = 90f + 360f / seats.size * i
                val angle by animateFloatAsState(target, spring(dampingRatio = 0.8f, stiffness = 120f), label = "seat")
                val grow = remember { Animatable(0f) }
                LaunchedEffect(Unit) { grow.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f)) }
                val rad = Math.toRadians(angle.toDouble())
                val mine = reactions.filter { it.from == s.id || (s.me && it.mine) }
                    .filter { clock - (arrived[it.seq] ?: clock) < 2600 }
                SeatView(
                    s, mine.map { it to (clock - (arrived[it.seq] ?: clock)) / 2600f }, reduce, nameAbove = sin(rad) < -0.3,
                    Modifier.offset(x = ring * cos(rad).toFloat(), y = ring * sin(rad).toFloat()).graphicsLayer {
                        scaleX = grow.value; scaleY = grow.value; alpha = grow.value.coerceIn(0f, 1f)
                    },
                )
            }
        }
    }
}

/** One person: their circle, their name under it, and any reactions they just sent rising above. */
@Composable
private fun SeatView(s: Seat, rising: List<Pair<Reaction, Float>>, reduce: Boolean, nameAbove: Boolean, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Avatar(s.name, 54.dp, ring = if (s.me) Bop.Ink else null)
        Text(
            (if (s.me) "You" else s.name) + if (s.host) " · host" else "",
            style = BopType.Caption.copy(fontWeight = FontWeight(600)), color = Bop.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.offset(y = if (nameAbove) (-42).dp else 42.dp).widthIn(max = 96.dp),
        )
        // Reactions rise from the seat, clear of a name written above it.
        val lift = if (nameAbove) 58.dp else 38.dp
        rising.forEachIndexed { k, (r, age) ->
            key(r.seq) {
                Text(r.emoji, fontSize = 30.sp, modifier = Modifier.offset(x = 14.dp * ((k % 3) - 1), y = -lift - (if (reduce) 0f else 46f * age).dp)
                    .graphicsLayer {
                        alpha = 1f - ((age - 0.6f) / 0.4f).coerceIn(0f, 1f)
                        val pop = if (age < 0.12f) 0.5f + age / 0.12f * 0.6f else 1.1f
                        scaleX = pop; scaleY = pop
                    })
            }
        }
    }
}

// --- Before a room ------------------------------------------------------------------------

@Composable
private fun Lobby(name: String, onName: (String) -> Unit, code: String, onCode: (String) -> Unit, error: String) {
    val app = LocalApp.current
    if (error.isNotBlank()) Text(error, style = BopType.Body, color = Bop.Danger, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Underlined("Your name", name, onName, KeyboardCapitalization.Words)
    Pill("Start a room", filled = true) { Together.create(name) }
    Text("Everyone plays their own copy; the room keeps you all on the same song and second. Works with BopDop on the web too.",
        style = BopType.Caption, color = Bop.Text3)
    Spacer(Modifier.height(4.dp))
    Text("Have a code?", style = BopType.Headline, color = Bop.Text)
    CodeField(code, onCode) { if (code.length >= 4) Together.join(code, name) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) { Pill("Join", filled = code.length >= 4) { if (code.length >= 4) Together.join(code, name) } }
        Row(
            Modifier.height(50.dp).clip(RoundedCornerShape(50)).background(Bop.Ink.copy(alpha = 0.08f)).bounceClick { app.openScanner() }.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.QrCodeScanner, null, tint = Bop.Text, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Scan", style = BopType.BodyStrong, color = Bop.Text)
        }
    }
}

/** A plain field: a small label, the text, a line under it. */
@Composable
private fun Underlined(label: String, value: String, onChange: (String) -> Unit, caps: KeyboardCapitalization) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = BopType.Caption, color = Bop.Text3)
        BasicTextField(value, onChange, singleLine = true, textStyle = BopType.Title.copy(color = Bop.Text), cursorBrush = SolidColor(Bop.Ink),
            keyboardOptions = KeyboardOptions(capitalization = caps), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Bop.Ink.copy(alpha = 0.18f)))
    }
}

/** The room code, typed as six spaced letters over six short underlines. */
@Composable
private fun CodeField(code: String, onCode: (String) -> Unit, onGo: () -> Unit) {
    BasicTextField(
        code, onCode, singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(CODE_LEN) { i ->
                    val at = i == code.length.coerceAtMost(CODE_LEN - 1)
                    val line by animateColorAsState(if (at) Bop.Ink else Bop.Ink.copy(alpha = 0.18f), label = "code")
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(code.getOrNull(i)?.toString() ?: " ", style = BopType.Display.copy(fontFamily = FontFamily.Monospace, fontSize = 26.sp), color = Bop.Text)
                        Box(Modifier.fillMaxWidth().height(if (at) 2.dp else 1.dp).background(line))
                    }
                }
            }
        },
    )
}

// --- In a room ----------------------------------------------------------------------------

@Composable
private fun Live(code: String) {
    val room by Together.state.collectAsState()
    val ctx = LocalContext.current
    val app = LocalApp.current
    var showQr by remember { mutableStateOf(false) }
    LaunchedEffect(room.chat.size) { Together.chatSeen() }
    DisposableEffect(Unit) { onDispose { Together.chatSeen() } }
    fun copy() { ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Room code", code)); app.toast("Code copied") }

    // The code, and the ways to pass it on.
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(code, style = BopType.Display.copy(fontFamily = FontFamily.Monospace, fontSize = 34.sp, letterSpacing = 6.sp), color = Bop.Text,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).bounceClick { copy() }.padding(horizontal = 6.dp))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallAction(Icons.Rounded.IosShare, "Invite") {
                val text = "Listen with me on BopDop. Room code: $code\nbopdop://together/$code"
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Invite to the room").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            SmallAction(Icons.Rounded.ContentCopy, "Copy") { copy() }
            SmallAction(Icons.Rounded.QrCode2, "QR", on = showQr) { showQr = !showQr }
        }
        AnimatedVisibility(showQr, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            PrettyQr("bopdop://together/$code", Modifier.padding(top = 16.dp).size(210.dp))
        }
    }

    // Knocks at the door, for the host.
    if (room.isHost) room.pending.forEach { p ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.name, 36.dp)
            Spacer(Modifier.width(10.dp))
            Text("${p.name} wants to join", style = BopType.Body, color = Bop.Text, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Deny", style = BopType.BodyStrong, color = Bop.Text2, modifier = Modifier.clip(RoundedCornerShape(50)).bounceClick { Together.approve(p.id, false) }.padding(horizontal = 12.dp, vertical = 8.dp))
            Text("Let in", style = BopType.BodyStrong, color = Bop.OnInk, modifier = Modifier.clip(RoundedCornerShape(50)).background(Bop.Ink).bounceClick { Together.approve(p.id, true) }.padding(horizontal = 14.dp, vertical = 8.dp))
        }
    }

    // Reactions go up over your seat on everyone's stage.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Together.REACTIONS.forEach { e ->
            Text(e, fontSize = 26.sp, modifier = Modifier.clip(CircleShape).bounceClick(0.8f) { Together.react(e) }.padding(4.dp))
        }
    }

    Chat(room.chat)

    if (room.isHost) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Who picks the music", style = BopType.Body, color = Bop.Text, modifier = Modifier.weight(1f))
            Row(Modifier.clip(RoundedCornerShape(50)).background(Bop.Ink.copy(alpha = 0.08f)).padding(3.dp)) {
                Segment("Me", room.mode == "host") { Together.setMode(false) }
                Segment("Everyone", room.mode == "all") { Together.setMode(true) }
            }
        }
    } else {
        Text(if (room.canControl) "Everyone can pick the music." else "The host picks the music; you follow along.", style = BopType.Caption, color = Bop.Text2)
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            if (room.isHost) "End room for everyone" else "Leave room", style = BopType.BodyStrong, color = Bop.Danger,
            modifier = Modifier.clip(RoundedCornerShape(50)).bounceClick { if (room.isHost) Together.end() else Together.leave() }.padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.height(38.dp).clip(RoundedCornerShape(50)).background(if (on) Bop.Ink else Bop.Ink.copy(alpha = 0.08f)).bounceClick(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (on) Bop.OnInk else Bop.Text, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = BopType.Caption.copy(fontWeight = FontWeight(600)), color = if (on) Bop.OnInk else Bop.Text)
    }
}

@Composable
private fun Segment(label: String, on: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) Bop.Ink else Color.Transparent, label = "seg")
    Text(label, style = BopType.Caption.copy(fontWeight = FontWeight(600)), color = if (on) Bop.OnInk else Bop.Text2,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).bounceClick(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp))
}

@Composable
private fun Pill(label: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(50))
            .background(if (filled) Bop.Ink else Bop.Ink.copy(alpha = 0.1f)).bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = BopType.BodyStrong, color = if (filled) Bop.OnInk else Bop.Text3) }
}

/** The room's chat as plain lines (each name in its person's colour), and a box to write in. */
@Composable
private fun Chat(chat: List<ChatMessage>) {
    var draft by remember { mutableStateOf("") }
    fun send() { if (draft.isNotBlank()) { Together.chat(draft); draft = "" } }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Chat", style = BopType.Headline, color = Bop.Text)
        if (chat.isEmpty()) Text("Nothing yet. What you write here pops up on everyone's screen.", style = BopType.Caption, color = Bop.Text3)
        chat.takeLast(30).forEach { c -> Line(c) }
        Row(Modifier.fillMaxWidth().glassLite(RoundedCornerShape(50), alpha = 0.55f).padding(start = 18.dp, end = 5.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (draft.isEmpty()) Text("Say something", style = BopType.Body, color = Bop.Text3)
                BasicTextField(draft, { draft = it.take(400) }, textStyle = BopType.Body.copy(color = Bop.Text), cursorBrush = SolidColor(Bop.Ink), maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }), modifier = Modifier.fillMaxWidth())
            }
            val ready = draft.isNotBlank()
            Box(Modifier.size(40.dp).clip(CircleShape).background(if (ready) Bop.Ink else Color.Transparent).bounceClick(0.88f) { send() }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = if (ready) Bop.OnInk else Bop.Text3, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun Line(c: ChatMessage) {
    Row(verticalAlignment = Alignment.Top) {
        Avatar(c.name, 26.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                pushStyle(androidx.compose.ui.text.SpanStyle(color = avatarColor(c.name), fontWeight = FontWeight(700)))
                append(if (c.mine) "You" else c.name); pop()
                append("  "); append(c.text)
            },
            style = BopType.Body, color = Bop.Text, modifier = Modifier.padding(top = 3.dp),
        )
    }
}

