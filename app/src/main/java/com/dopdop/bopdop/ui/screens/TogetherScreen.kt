package com.dopdop.bopdop.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dopdop.bopdop.data.together.RoomStatus
import com.dopdop.bopdop.data.together.Together
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.glass.glassLite
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** Start or join a Listen Together room; while in one, its code, QR, people and controls. */
@Composable
fun TogetherScreen(initialCode: String) {
    val room by Together.state.collectAsState()
    var name by remember { mutableStateOf(Together.name.ifBlank { android.os.Build.MODEL.take(24) }) }
    var code by remember { mutableStateOf(Together.normalize(initialCode)) }
    // A room link opened while in another room moves you to the new one.
    LaunchedEffect(initialCode) { if (initialCode.isNotBlank() && Together.normalize(initialCode) != room.code) Together.join(initialCode, name) }

    LargeTitleScaffold(title = "Listen Together", eyebrow = "Same song, same second", showBack = true) {
        item(key = "body") {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (room.status) {
                    RoomStatus.IDLE -> {
                        if (room.error.isNotBlank()) Text(room.error, style = BopType.Body, color = Bop.Danger)
                        Text("Everyone plays their own copy; the room keeps you all on the same song and second. Works with BopDop on the web too.",
                            style = BopType.Body, color = Bop.Text2)
                        Field("Your name", name) { name = it.take(24) }
                        Pill("Start a room", filled = true) { Together.create(name) }
                        Text("or join one", style = BopType.Caption, color = Bop.Text3, modifier = Modifier.align(Alignment.CenterHorizontally))
                        Field("Room code", code, caps = true) { code = Together.normalize(it) }
                        Pill("Join", filled = code.length >= 4) { if (code.length >= 4) Together.join(code, name) }
                    }
                    RoomStatus.CONNECTING, RoomStatus.RECONNECTING -> Text(if (room.status == RoomStatus.CONNECTING) "Connecting to ${room.code}…" else "Reconnecting…", style = BopType.Headline, color = Bop.Text)
                    RoomStatus.WAITING -> Text("Waiting for the host to let you in…", style = BopType.Headline, color = Bop.Text)
                    RoomStatus.LIVE -> Live(room.code)
                }
            }
        }
    }
}

@Composable
private fun Live(code: String) {
    val room by Together.state.collectAsState()
    val qr = remember(code) { qrBitmap("bopdop://together/$code", 600) }
    Column(Modifier.fillMaxWidth().glassLite(RoundedCornerShape(26.dp), alpha = 0.6f, shadow = 12.dp).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("ROOM CODE", style = BopType.Micro, color = Bop.Text3)
        Text(code, style = BopType.Display.copy(fontSize = 40.sp, letterSpacing = 6.sp), color = Bop.Text)
        Spacer(Modifier.height(12.dp))
        Image(qr.asImageBitmap(), "Room QR code", Modifier.size(180.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(8.dp))
        Spacer(Modifier.height(8.dp))
        Text("Scan with a phone camera, or type the code in BopDop", style = BopType.Caption, color = Bop.Text2, textAlign = TextAlign.Center)
    }
    if (room.pending.isNotEmpty() && room.isHost) {
        Text("WANTS TO JOIN", style = BopType.Micro, color = Bop.Text3)
        room.pending.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, style = BopType.BodyStrong, color = Bop.Text, modifier = Modifier.weight(1f))
                Text("Deny", style = BopType.BodyStrong, color = Bop.Danger, modifier = Modifier.bounceClick { Together.approve(p.id, false) }.padding(10.dp))
                Text("Let in", style = BopType.BodyStrong, color = Bop.Accent, modifier = Modifier.bounceClick { Together.approve(p.id, true) }.padding(10.dp))
            }
        }
    }
    Text("IN THE ROOM · ${room.members.size}", style = BopType.Micro, color = Bop.Text3)
    room.members.forEach { m ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(Bop.Ink.copy(alpha = 0.8f)), contentAlignment = Alignment.Center) {
                Text(m.name.take(1).uppercase(), style = BopType.BodyStrong, color = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Text(m.name + if (m.id == room.meId) " (you)" else "", style = BopType.Body, color = Bop.Text, modifier = Modifier.weight(1f))
            if (m.host) Text("HOST", style = BopType.Micro, color = Bop.Graphite, modifier = Modifier.clip(RoundedCornerShape(50)).background(Bop.Gold).padding(horizontal = 8.dp, vertical = 4.dp))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("❤️", "🔥", "😂", "👏", "🎉").forEach { e ->
            Text(e, fontSize = 24.sp, modifier = Modifier.clip(CircleShape).bounceClick { Together.react(e) }.padding(6.dp))
        }
    }
    room.reaction?.let { (who, emoji) -> Text("$who $emoji", style = BopType.Body, color = Bop.Text2) }
    if (room.isHost) {
        Text("WHO CONTROLS PLAYBACK", style = BopType.Micro, color = Bop.Text3)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Only me", room.mode == "host") { Together.setMode(false) }
            Choice("Everyone", room.mode == "all") { Together.setMode(true) }
        }
    } else {
        Text(if (room.canControl) "Everyone can control playback." else "The host controls playback; you follow along.", style = BopType.Caption, color = Bop.Text2)
    }
    Pill(if (room.isHost) "End room" else "Leave room", filled = false) { if (room.isHost) Together.end() else Together.leave() }
}

@Composable
private fun Field(hint: String, value: String, caps: Boolean = false, onChange: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().height(54.dp).glassLite(RoundedCornerShape(16.dp), alpha = 0.6f).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) Text(hint, style = BopType.Body, color = Bop.Text3)
        BasicTextField(
            value, onChange, singleLine = true, textStyle = BopType.Headline.copy(color = Bop.Text), cursorBrush = SolidColor(Bop.Ink),
            keyboardOptions = KeyboardOptions(capitalization = if (caps) KeyboardCapitalization.Characters else KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Pill(label: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(50))
            .background(if (filled) Bop.Ink else Bop.Ink.copy(alpha = 0.12f)).bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = BopType.BodyStrong, color = if (filled) Bop.OnInk else Bop.Text) }
}

@Composable
private fun Choice(label: String, on: Boolean, onClick: () -> Unit) {
    Text(label, style = BopType.Caption, color = if (on) Bop.OnInk else Bop.Text,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Bop.Ink else Bop.Veil.copy(alpha = 0.55f)).bounceClick(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp))
}

private fun qrBitmap(text: String, size: Int): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val px = IntArray(size * size) { if (m[it % size, it / size]) 0xFF15161B.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}
