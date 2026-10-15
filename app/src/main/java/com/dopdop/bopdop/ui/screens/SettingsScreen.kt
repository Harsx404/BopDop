package com.dopdop.bopdop.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MotionPhotosOff
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.HighQuality
import com.dopdop.bopdop.ui.glass.glassEdges
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dopdop.bopdop.BuildConfig
import com.dopdop.bopdop.data.GlassLevel
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.account.AccountStore
import com.dopdop.bopdop.data.ytm.YtMusic
import com.dopdop.bopdop.ui.Routes
import com.dopdop.bopdop.ui.components.Artwork
import com.dopdop.bopdop.playback.PlaybackService
import com.dopdop.bopdop.ui.LocalApp
import com.dopdop.bopdop.ui.components.ScreenCache
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.glass.glassLite
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType

private val LANGS = listOf("hindi", "english", "punjabi", "tamil", "telugu", "marathi", "gujarati", "bengali", "kannada", "malayalam", "bhojpuri", "haryanvi", "rajasthani", "odia", "urdu")

@Composable
fun SettingsScreen() {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val lib by LibraryStore.state.collectAsState()
    val s = lib.settings
    val account by AccountStore.account.collectAsState()
    LargeTitleScaffold(title = "Settings", showBack = true) {
        item {
            Group("Account") {
                val a = account
                if (a == null) {
                    Row(Modifier.fillMaxWidth().bounceClick(0.99f) { app.nav.navigate(Routes.SIGN_IN) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.AccountCircle)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Sign in to YouTube Music", style = BopType.BodyStrong, color = Bop.Text)
                            Text("Your own Home mixes, Liked Music and playlists. Optional — everything else works without it.", style = BopType.Caption, color = Bop.Text2)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("Sign in", style = BopType.Caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(650)), color = Color.White,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Bop.Accent).padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Artwork(a.photo.ifBlank { null }, Modifier.size(46.dp), circle = true, size = 160)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = BopType.BodyStrong, color = Bop.Text, maxLines = 1)
                            Text(a.handle.ifBlank { "YouTube Music" }, style = BopType.Caption, color = Bop.Text2, maxLines = 1)
                        }
                        Text(
                            "Sign out", style = BopType.BodyStrong, color = Bop.Danger,
                            modifier = Modifier.bounceClick {
                                AccountStore.signOut(); YtMusic.clearCache(); ScreenCache.put("home", null)
                                app.toast("Signed out")
                            }.padding(8.dp),
                        )
                    }
                }
            }
            Group("Sound") {
                ToggleRow(Icons.Rounded.HighQuality, "Hi-Res match", "Play YouTube Music songs from JioSaavn’s 320 kbps copy when the same recording exists.", s.hiRes) {
                    LibraryStore.updateSettings { it.copy(hiRes = !it.hiRes) }
                }
                Row(Modifier.fillMaxWidth().bounceClick(0.99f) { app.nav.navigate(Routes.EQ) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.GraphicEq)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Equalizer", style = BopType.BodyStrong, color = Bop.Text)
                        Text(if (s.eq.enabled) "${s.eq.preset} · on" else "Shape the sound — bass, vocals, sparkle.", style = BopType.Caption, color = Bop.Text2)
                    }
                    Spacer(Modifier.width(8.dp))
                    EqMini(s.eq.gains, s.eq.enabled)
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Bop.Text3, modifier = Modifier.size(22.dp))
                }
                ToggleRow(Icons.Rounded.AllInclusive, "Autoplay", "When your queue ends, keep going with similar songs.", s.autoplay) {
                    LibraryStore.updateSettings { it.copy(autoplay = !it.autoplay) }
                }
            }
            Group("Look & feel") {
                RowHead(Icons.Rounded.WaterDrop, "Glass", "How much the background shows through.")
                Row(Modifier.padding(start = 56.dp, end = 16.dp, bottom = 14.dp).fillMaxWidth().glassLite(RoundedCornerShape(14.dp), alpha = 0.45f).padding(4.dp)) {
                    GlassLevel.entries.forEach { g ->
                        val on = g == s.glass
                        val bg by animateColorAsState(if (on) Bop.Ink else Color.Transparent, label = "g")
                        Text(
                            g.label, style = BopType.BodyStrong, color = if (on) Color.White else Bop.Text2,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(bg)
                                .bounceClick { LibraryStore.updateSettings { it.copy(glass = g) } }.padding(vertical = 9.dp),
                        )
                    }
                }
                ToggleRow(Icons.Rounded.MotionPhotosOff, "Reduce motion", "Freeze the living background and calm the animations.", s.reduceMotion) {
                    LibraryStore.updateSettings { it.copy(reduceMotion = !it.reduceMotion) }
                }
            }
            Group("Home feed") {
                ToggleRow(Icons.Rounded.AutoAwesome, "JioSaavn picks", "Mix Indian charts, new releases and stations into Home.", s.showSaavnHome) {
                    LibraryStore.updateSettings { it.copy(showSaavnHome = !it.showSaavnHome) }
                    ScreenCache.put("home", null)
                }
                RowHead(Icons.Rounded.Language, "Languages", "Shapes JioSaavn charts and stations.")
                val chosen = s.languages.split(",").filter { it.isNotBlank() }.toSet()
                FlowRow(Modifier.padding(start = 56.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LANGS.forEach { l ->
                        val on = l in chosen
                        Text(
                            l.replaceFirstChar { it.uppercase() }, style = BopType.Caption, color = if (on) Color.White else Bop.Text,
                            modifier = Modifier.bounceClick {
                                val next = if (on) chosen - l else chosen + l
                                if (next.isNotEmpty()) {
                                    LibraryStore.updateSettings { it.copy(languages = next.joinToString(",")) }
                                    ScreenCache.put("home", null)
                                }
                            }.clip(RoundedCornerShape(50)).background(if (on) Bop.Ink else Color.White.copy(alpha = 0.55f))
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            Group("Storage") {
                RowHead(Icons.Rounded.DownloadForOffline, "Offline cache", "Songs you play are kept so replays are instant and data-free. Applies on next launch.")
                Row(Modifier.padding(start = 56.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(512, 1024, 2048, 4096).forEach { mb ->
                        val on = s.cacheMb == mb
                        Text(
                            if (mb < 1024) "$mb MB" else "${mb / 1024} GB", style = BopType.Caption, color = if (on) Color.White else Bop.Text,
                            modifier = Modifier.bounceClick { LibraryStore.updateSettings { it.copy(cacheMb = mb) } }
                                .clip(RoundedCornerShape(50)).background(if (on) Bop.Ink else Color.White.copy(alpha = 0.55f))
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
                Text(
                    "Clear cached audio", style = BopType.BodyStrong, color = Color(0xFFFF6B8B),
                    modifier = Modifier.padding(start = 56.dp, bottom = 16.dp).bounceClick {
                        val c = PlaybackService.cache(ctx)
                        val mb = c.cacheSpace / (1024 * 1024)
                        c.keys.toList().forEach { runCatching { c.removeResource(it) } }
                        app.toast("Freed $mb MB")
                    },
                )
            }
            Group("Coming soon") {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Groups)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Listen Together", style = BopType.BodyStrong, color = Bop.Text)
                        Text("Same song, same second, with friends anywhere.", style = BopType.Caption, color = Bop.Text2)
                    }
                    Text("SOON", style = BopType.Micro, color = Bop.Ink, modifier = Modifier.clip(RoundedCornerShape(50)).background(Bop.Gold).padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("BopDop", style = BopType.Title, color = Bop.Text)
                Text("v${BuildConfig.VERSION_NAME} · DopDop music engine", style = BopType.Caption, color = Bop.Text3)
                Text("YouTube Music catalogue · JioSaavn 320 kbps · LRCLIB lyrics", style = BopType.Caption, color = Bop.Text3)
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title.uppercase(), style = BopType.Micro, color = Bop.Text3, modifier = Modifier.padding(start = 28.dp, top = 22.dp, bottom = 8.dp))
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().glassLite(RoundedCornerShape(24.dp), alpha = 0.5f, shadow = 14.dp), content = content)
}

@Composable
private fun IconBadge(icon: ImageVector) {
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.9f)).glassEdges(RoundedCornerShape(10.dp), 0.8f, true), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Bop.Ink, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun RowHead(icon: ImageVector, title: String, sub: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
        IconBadge(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = BopType.BodyStrong, color = Bop.Text)
            Text(sub, style = BopType.Caption, color = Bop.Text2)
        }
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, sub: String, on: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().bounceClick(0.99f, onClick = onToggle).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = BopType.BodyStrong, color = Bop.Text)
            Text(sub, style = BopType.Caption, color = Bop.Text2)
        }
        Spacer(Modifier.width(12.dp))
        GlassSwitch(on)
    }
}

/** iOS-style switch with a springy glass thumb. */
@Composable
fun GlassSwitch(on: Boolean) {
    val x by animateDpAsState(if (on) 22.dp else 2.dp, spring(dampingRatio = 0.6f, stiffness = 600f), label = "x")
    val track by animateColorAsState(if (on) Color(0xFF34C759) else Bop.Ink.copy(alpha = 0.12f), label = "t")
    Box(Modifier.size(52.dp, 32.dp).clip(RoundedCornerShape(50)).background(track)) {
        Box(Modifier.offset(x = x, y = 2.dp).size(28.dp).clip(CircleShape).background(Color.White))
    }
}
