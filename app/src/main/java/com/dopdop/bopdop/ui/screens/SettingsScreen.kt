package com.dopdop.bopdop.ui.screens

import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Contactless
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Pets
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dopdop.bopdop.data.local.LocalMusic
import com.dopdop.bopdop.ui.Sheet
import com.dopdop.bopdop.ui.refreshRates
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.TextFields
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
import com.dopdop.bopdop.data.VideoQuality
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
import com.dopdop.bopdop.ui.theme.family

private val LANGS = listOf("hindi", "english", "punjabi", "tamil", "telugu", "marathi", "gujarati", "bengali", "kannada", "malayalam", "bhojpuri", "haryanvi", "rajasthani", "odia", "urdu")

@Composable
fun SettingsScreen() {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val lib by LibraryStore.state.collectAsState()
    val s = lib.settings
    val account by AccountStore.account.collectAsState()
    var query by remember { mutableStateOf("") }
    val total = remember { Hits() }
    LargeTitleScaffold(title = "Settings", showBack = true) {
        item { SettingsSearch(query) { query = it } }
        item(key = "group:Account") {
            CompositionLocalProvider(LocalQuery provides query, LocalTotal provides total) {
                Group("Account") {
                    val spotify by com.dopdop.bopdop.data.spotify.SpotifyAccount.profile.collectAsState()
                    NavRow(
                        Icons.Rounded.GraphicEq, if (spotify != null) "Spotify · ${spotify!!.name}" else "Connect Spotify",
                        if (spotify != null) "Your Spotify playlists and Liked Songs, and Canvas videos in the player." else "Sign in on Spotify's page to play your playlists and Liked Songs here.",
                        keywords = "spotify account sign in canvas playlists liked", brand = com.dopdop.bopdop.ui.components.Brand.SPOTIFY,
                    ) { app.nav.navigate(if (spotify != null) com.dopdop.bopdop.ui.Routes.SPOTIFY else com.dopdop.bopdop.ui.Routes.SPOTIFY_LOGIN) }
                    Searchable("Sign in to YouTube Music", "account sign out library liked playlists") {
                        val a = account
                        if (a == null) {
                            Row(Modifier.fillMaxWidth().bounceClick(0.99f) { app.nav.navigate(Routes.SIGN_IN) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconBadge(Icons.Rounded.AccountCircle, com.dopdop.bopdop.ui.components.Brand.YOUTUBE_MUSIC)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Sign in to YouTube Music", style = BopType.BodyStrong, color = Bop.Text)
                                    Text("Your own Home mixes, Liked Music and playlists. Optional — everything else works without it.", style = BopType.Caption, color = Bop.Text2)
                                }
                                Spacer(Modifier.width(12.dp))
                                Text("Sign in", style = BopType.Caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(650)), color = Bop.OnInk,
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
                }
            }
        }
        item(key = "group:Sound") {
            CompositionLocalProvider(LocalQuery provides query, LocalTotal provides total) {
                Group("Sound") {
                    ToggleRow(Icons.Rounded.Headphones, "Resume when headphones reconnect", "If disconnecting your Bluetooth headphones, earbuds or car paused the music, it plays again when they're back (within 30 minutes).", s.resumeOnConnect, keywords = "bluetooth car reconnect resume auto play earbuds") {
                        LibraryStore.updateSettings { it.copy(resumeOnConnect = !it.resumeOnConnect) }
                    }
                    RowHead(Icons.Rounded.Wifi, "Audio quality on Wi-Fi", "High plays the best stream there is, including JioSaavn's 320 kbps copy.") {
                        ChipRow(com.dopdop.bopdop.data.AudioQuality.entries, s.audioWifi, { it.label }) { q -> LibraryStore.updateSettings { it.copy(audioWifi = q) } }
                    }
                    RowHead(Icons.Rounded.SignalCellularAlt, "Audio quality on mobile data", "Normal is about 128–160 kbps; Data saver about 70–96 kbps. Applies from the next song.") {
                        ChipRow(com.dopdop.bopdop.data.AudioQuality.entries, s.audioMobile, { it.label }) { q -> LibraryStore.updateSettings { it.copy(audioMobile = q) } }
                    }
                    RowHead(Icons.Rounded.Speed, "Playback speed", "Faster or slower without changing the pitch. Also in the player's ⋯ menu.") {
                        ChipRow(listOf(0.5f, 0.75f, 0.9f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f), s.playbackSpeed, { com.dopdop.bopdop.ui.player.speedLabel(it) }) { v -> LibraryStore.updateSettings { it.copy(playbackSpeed = v) } }
                    }
                    ToggleRow(Icons.Rounded.VolumeUp, "Loudness normalization", "Levels every track to the same loudness.", s.normalize) {
                        LibraryStore.updateSettings { it.copy(normalize = !it.normalize) }
                    }
                    ToggleRow(Icons.Rounded.VolumeOff, "Skip silence", "Trims gaps of silence inside and between tracks.", s.skipSilence) {
                        LibraryStore.updateSettings { it.copy(skipSilence = !it.skipSilence) }
                    }
                    RowHead(Icons.Rounded.AutoAwesome, "Automix", "Blends each song into the next. Albums that flow track-to-track stay gapless.", keywords = "crossfade gapless fade") {
                        ChipRow(listOf(0, 3, 6, 9, 12), s.crossfadeSec, { if (it == 0) "Off" else "$it s" }) { sec -> LibraryStore.updateSettings { it.copy(crossfadeSec = sec) } }
                    }
                    RowHead(Icons.Rounded.Tune, "Output precision", if (s.floatOutput) "Sends 32-bit float to Android's mixer: more headroom after the EQ and leveling." else "16-bit PCM, the standard output.") {
                        Segmented(listOf("16-bit PCM", "32-bit float"), if (s.floatOutput) 1 else 0) { i -> LibraryStore.updateSettings { it.copy(floatOutput = i == 1) } }
                    }
                    Searchable("Equalizer", "EQ bass treble vocals presets spatial sound") {
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
                    }
                    ToggleRow(Icons.Rounded.AllInclusive, "Autoplay", "When your queue ends, keep going with similar songs.", s.autoplay) {
                        LibraryStore.updateSettings { it.copy(autoplay = !it.autoplay) }
                    }
                    NavRow(
                        Icons.Rounded.ThumbDown, "Not for me",
                        if (lib.disliked.isEmpty()) "Songs you mark “Not for me” stay out of radio, autoplay and mixes."
                        else "${com.dopdop.bopdop.data.model.songs(lib.disliked.size)} kept out of radio, autoplay and mixes",
                        keywords = "dislike hide block skip",
                    ) { app.openLocal("disliked") }
                    val pipe by com.dopdop.bopdop.playback.AudioPipeline.info.collectAsState()
                    NavRow(Icons.Rounded.Insights, "Stats for nerds", keywords = "audio pipeline codec bitrate bit depth sample rate decoder", sub = if (pipe.sinkRate > 0) "${pipe.codec} → ${pipe.sinkEncoding} · ${"%.1f".format(pipe.sinkRate / 1000f).removeSuffix(".0")} kHz" else "Live signal path, decoder to output") {
                        app.sheet = Sheet.AudioPipeline
                    }
                }
            }
        }
        item(key = "group:Sources & lossless") {
            CompositionLocalProvider(LocalQuery provides query, LocalTotal provides total) {
                Group("Sources & lossless") { AddonRows() }
            }
        }
        item(key = "group:Lyrics") {
            CompositionLocalProvider(LocalQuery provides query, LocalTotal provides total) {
                Group("Lyrics") {
                    ToggleRow(Icons.Rounded.BlurOn, "Moving background", "The cover swirls slowly behind the lyrics.", s.lyricsMotion, keywords = "motion animated lyrics background") {
                        LibraryStore.updateSettings { it.copy(lyricsMotion = !it.lyricsMotion) }
                    }
                    ToggleRow(Icons.Rounded.Fullscreen, "Full screen lyrics", "While a song plays, the controls fade away after a few seconds so the words fill the screen. Tap to bring them back.", s.lyricsFullScreen, keywords = "immersive fullscreen hide controls karaoke") {
                        LibraryStore.updateSettings { it.copy(lyricsFullScreen = !it.lyricsFullScreen) }
                    }
                    ToggleRow(Icons.Rounded.Lyrics, "Lyrics on lock screen", "Shows the line being sung in the notification and on the lock screen.", s.lockScreenLyrics) {
                        LibraryStore.updateSettings { it.copy(lockScreenLyrics = !it.lockScreenLyrics) }
                    }
                }
            }
        }
        item(key = "group:Local music") {
            CompositionLocalProvider(LocalQuery provides query, LocalTotal provides total) {
                Group("Local music") {
                    var allowed by remember { mutableStateOf(LocalMusic.hasPermission(ctx)) }
                    val folders by LocalMusic.folders.collectAsState()
                    LaunchedEffect(allowed, s.filterNonMusic) { if (allowed) LocalMusic.refresh(ctx) }
                    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
                    if (!allowed) {
                        NavRow(Icons.Rounded.Folder, "Allow music access", "Play songs saved on this phone.") { ask.launch(LocalMusic.permission) }
                    } else {
                        RowHead(Icons.Rounded.Folder, "Local music folder", s.localFolder ?: "All music folders") {
                            ChipRow(listOf<String?>(null) + folders, s.localFolder, { f -> f?.substringAfterLast('/')?.ifBlank { f } ?: "All" }) { f ->
                                LibraryStore.updateSettings { it.copy(localFolder = f) }
                            }
                        }
                    }
                    ToggleRow(Icons.Rounded.FilterAlt, "Filter non-music audio", "Hides clips under 30 seconds, WAV files, voice notes, recordings and system sounds.", s.filterNonMusic) {
                        LibraryStore.updateSettings { it.copy(filterNonMusic = !it.filterNonMusic) }
                    }
                }
            }
        }
        item(key = "group:Music video") {
            CompositionLocalProvider(LocalQuery provides query, LocalTotal provides total) {
                Group("Music video") {
                    RowHead(Icons.Rounded.Videocam, "Video quality", "Auto streams 1080p on Wi‑Fi and 480p on mobile data. 4K plays only where your phone can decode it smoothly; applies to the next video.") {
                        FlowRow(Modifier.padding(start = 56.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            VideoQuality.entries.forEach { q ->
                                val on = s.videoQuality == q
                                Text(
                                    q.label, style = BopType.Caption, color = if (on) Bop.OnInk else Bop.Text,
                                    modifier = Modifier.bounceClick { LibraryStore.updateSettings { it.copy(videoQuality = q) } }
                                        .clip(RoundedCornerShape(50)).background(if (on) Bop.Ink else Bop.Veil.copy(alpha = 0.55f))
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        item(key = "group:Look & feel") {
            CompositionLocalProvider(LocalQuery provides query, LocalTotal provides total) {
                Group("Look & feel") {
                    AppIconRow()
                    ToggleRow(Icons.Rounded.Videocam, "Spotify Canvas", "The short looping video Spotify shows behind a song, over its cover. Needs Spotify connected.", s.canvas, keywords = "animated cover art motion video spotify") {
                        LibraryStore.updateSettings { it.copy(canvas = !it.canvas) }
                    }
                    if (s.canvas) ToggleRow(Icons.Rounded.SignalCellularAlt, "Canvas on mobile data", "Off: Canvas plays on Wi-Fi only.", s.canvasOnData, keywords = "canvas data cellular") {
                        LibraryStore.updateSettings { it.copy(canvasOnData = !it.canvasOnData) }
                    }
                    RowHead(Icons.Rounded.DarkMode, "Theme", "Auto follows your phone's light or dark setting.", keywords = "dark light mode night") {
                        ChipRow(com.dopdop.bopdop.data.ThemeMode.entries, s.themeMode, { it.label }) { m -> LibraryStore.updateSettings { it.copy(themeMode = m) } }
                    }
                    RowHead(Icons.Rounded.TextFields, "Font", "Used for all text in BopDop. Each choice is shown in its own font; Import adds a .ttf or .otf file of your own.", keywords = "typeface font family text style") {
                        FontChips(s) { f -> LibraryStore.updateSettings { it.copy(font = f) } }
                    }
                    RowHead(Icons.Rounded.FormatSize, "Text size", "On top of your phone's own font size.", keywords = "font size bigger smaller larger text scale accessibility") {
                        ChipRow(com.dopdop.bopdop.data.TextSize.entries, s.textSize, { it.label }) { z -> LibraryStore.updateSettings { it.copy(textSize = z) } }
                    }
                    RowHead(Icons.Rounded.WaterDrop, "Glass", "How much the background shows through.", keywords = "blur transparency") {
                        Row(Modifier.padding(start = 56.dp, end = 16.dp, bottom = 14.dp).fillMaxWidth().glassLite(RoundedCornerShape(14.dp), alpha = 0.45f).padding(4.dp)) {
                            GlassLevel.entries.forEach { g ->
                                val on = g == s.glass
                                val bg by animateColorAsState(if (on) Bop.Ink else Color.Transparent, label = "g")
                                Text(
                                    g.label, style = BopType.BodyStrong, color = if (on) Bop.OnInk else Bop.Text2,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(bg)
                                        .bounceClick { LibraryStore.updateSettings { it.copy(glass = g) } }.padding(vertical = 9.dp),
                                )
                            }
                        }
                    }
                    val rates = remember { ctx.refreshRates() }
                    if (rates.size > 1) {
                        RowHead(Icons.Rounded.Speed, "Refresh rate", if (s.refreshRate == 0) "Auto: Android picks the rate." else "${s.refreshRate} Hz requested. Android may lower it when needed.") {
                            ChipRow(listOf(0) + rates, s.refreshRate, { if (it == 0) "Auto" else "$it Hz" }) { hz -> LibraryStore.updateSettings { it.copy(refreshRate = hz) } }
                        }
                    }
                    NavRow(Icons.Rounded.Pets, "Seek bar & mascot", "${s.seekStyle.label} seek bar${when (s.mascot) { com.dopdop.bopdop.data.Mascot.NONE -> ""; com.dopdop.bopdop.data.Mascot.BUNNY -> " with the bunny hopping along"; com.dopdop.bopdop.data.Mascot.BIKE -> " with the bike riding along"; com.dopdop.bopdop.data.Mascot.WAGON -> " with the wagon rolling along"; com.dopdop.bopdop.data.Mascot.DINO -> " with the dino stomping along"; else -> " with the ${s.mascot.label.lowercase()} walking along".replace("the hana","Hana").replace("the mei","Mei").replace("the yui","Yui")}}. Long-press the seek bar to change it from the player.") {
                        app.sheet = com.dopdop.bopdop.ui.Sheet.SeekBarLook
                    }
                    ToggleRow(Icons.Rounded.MotionPhotosOff, "Reduce motion", "Freeze the living background and calm the animations.", s.reduceMotion) {
                        LibraryStore.updateSettings { it.copy(reduceMotion = !it.reduceMotion) }
                    }
                }
            }
        }
        item(key = "group:Festive") {
            CompositionLocalProvider(LocalQuery provides query, LocalTotal provides total) {
                Group("Festive") {
                    val festive = com.dopdop.bopdop.ui.festive.LocalFestive.current
                    val now = festive.festival?.let { f ->
                        val end = com.dopdop.bopdop.data.Festivals.window(f, java.time.LocalDate.now().year)?.endInclusive
                        "${f.label} is on" + (end?.let { " until " + it.format(java.time.format.DateTimeFormatter.ofPattern("MMM d")) } ?: "") + ". "
                    }.orEmpty()
                    RowHead(Icons.Rounded.Celebration, "Festive themes", now + "Themes arrive 5 days before a festival and leave when it ends; festivals that are over for the year drop off the list.") {
                        ChipRow(com.dopdop.bopdop.data.FestiveLevel.entries, s.festiveLevel, { it.label }) { l -> LibraryStore.updateSettings { it.copy(festiveLevel = l) } }
                    }
                    if (s.festiveLevel != com.dopdop.bopdop.data.FestiveLevel.OFF) {
                        RowHead(Icons.Rounded.Public, "Celebrate", "My region follows where your phone is: Navaratri in India and Nepal, Halloween elsewhere. Pick one to swap it in while a festival is on.") {
                            ChipRow(com.dopdop.bopdop.data.Festivals.picks(java.time.LocalDate.now()), s.festivePick.takeIf { it in com.dopdop.bopdop.data.Festivals.picks(java.time.LocalDate.now()) } ?: com.dopdop.bopdop.data.FestivePick.AUTO, { it.label }) { f -> LibraryStore.updateSettings { it.copy(festivePick = f) } }
                        }
                    }
                }
            }
        }
        item(key = "group:Island") {
            CompositionLocalProvider(LocalQuery provides query, LocalTotal provides total) {
                Group("Island") {
                    val native = com.dopdop.bopdop.ui.island.NativeIsland.name
                    if (android.os.Build.VERSION.SDK_INT >= 36) {
                        ToggleRow(Icons.Rounded.Contactless, "Show in your phone's island",
                            (native?.let { "Shows the song in your $it" } ?: "Shows the song in your status bar and island") + " as a Live Update while music plays.", s.liveUpdate) {
                            LibraryStore.updateSettings { it.copy(liveUpdate = !it.liveUpdate) }
                        }
                    }
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        ToggleRow(Icons.Rounded.Contactless, "Island over other apps", "BopDop's own island while you use other apps. Asks to display over other apps.", s.islandOverlay) {
                            val turnOn = !s.islandOverlay
                            if (turnOn && !android.provider.Settings.canDrawOverlays(ctx)) {
                                runCatching {
                                    ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + ctx.packageName)))
                                }
                            }
                            LibraryStore.updateSettings { it.copy(islandOverlay = turnOn) }
                        }
                    }
                }
            }
        }
        item(key = "group:Home feed") {
            CompositionLocalProvider(LocalQuery provides query, LocalTotal provides total) {
                Group("Home feed") {
                    ToggleRow(Icons.Rounded.AutoAwesome, "JioSaavn picks", "Mix Indian charts, new releases and stations into Home.", s.showSaavnHome) {
                        LibraryStore.updateSettings { it.copy(showSaavnHome = !it.showSaavnHome) }
                        ScreenCache.put("home", null)
                    }
                    RowHead(Icons.Rounded.Language, "Languages", "Shapes JioSaavn charts and stations.") {
                        val chosen = s.languages.split(",").filter { it.isNotBlank() }.toSet()
                        FlowRow(Modifier.padding(start = 56.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LANGS.forEach { l ->
                                val on = l in chosen
                                Text(
                                    l.replaceFirstChar { it.uppercase() }, style = BopType.Caption, color = if (on) Bop.OnInk else Bop.Text,
                                    modifier = Modifier.bounceClick {
                                        val next = if (on) chosen - l else chosen + l
                                        if (next.isNotEmpty()) {
                                            LibraryStore.updateSettings { it.copy(languages = next.joinToString(",")) }
                                            ScreenCache.put("home", null)
                                        }
                                    }.clip(RoundedCornerShape(50)).background(if (on) Bop.Ink else Bop.Veil.copy(alpha = 0.55f))
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        item(key = "group:Storage") {
            CompositionLocalProvider(LocalQuery provides query, LocalTotal provides total) {
                Group("Storage") {
                    RowHead(Icons.Rounded.DownloadForOffline, "Offline cache", "Songs you play are kept so replays are instant and data-free. Applies on next launch. Remove one from its ⋯ menu.", keywords = "storage clear space data unlimited keep remove") {
                        Row(Modifier.padding(start = 56.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(512, 1024, 2048, 4096, 0).forEach { mb ->
                                val on = s.cacheMb == mb
                                Text(
                                    when { mb == 0 -> "Unlimited"; mb < 1024 -> "$mb MB"; else -> "${mb / 1024} GB" }, style = BopType.Caption, color = if (on) Bop.OnInk else Bop.Text,
                                    modifier = Modifier.bounceClick { LibraryStore.updateSettings { it.copy(cacheMb = mb) } }
                                        .clip(RoundedCornerShape(50)).background(if (on) Bop.Ink else Bop.Veil.copy(alpha = 0.55f))
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                )
                            }
                        }
                        Row(Modifier.padding(start = 56.dp, end = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Keep cached songs", style = BopType.BodyStrong, color = Bop.Text)
                                Text("Store them where Android won't clear them to free space or after updates. Applies on next launch.", style = BopType.Caption, color = Bop.Text2)
                            }
                            Spacer(Modifier.width(12.dp))
                            Box(Modifier.bounceClick { LibraryStore.updateSettings { it.copy(keepCache = !it.keepCache) } }) { GlassSwitch(s.keepCache) }
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
                }
            }
        }
        item(key = "group:Backup") {
            CompositionLocalProvider(LocalQuery provides query, LocalTotal provides total) {
                Group("Backup") { BackupRows() }
            }
        }
        item(key = "group:Scrobbling") {
            CompositionLocalProvider(LocalQuery provides query, LocalTotal provides total) {
                Group("Scrobbling") {
                NavRow(
                    Icons.Rounded.BarChart, "ListenBrainz",
                    if (s.listenBrainzToken.isNotBlank()) "Scrobbling as ${s.listenBrainzUser}" else "Keep a record of what you listen to on listenbrainz.org.",
                    keywords = "scrobble history stats", brand = com.dopdop.bopdop.ui.components.Brand.LISTENBRAINZ,
                ) { app.sheet = Sheet.Scrobbling }
                NavRow(
                    Icons.Rounded.BarChart, if (s.lastFmLibre) "Libre.fm" else "Last.fm",
                    if (s.lastFmSession.isNotBlank()) "Scrobbling as ${s.lastFmUser}" else "Scrobble to Last.fm or Libre.fm.",
                    keywords = "scrobble last.fm lastfm libre.fm history",
                    brand = if (s.lastFmLibre) com.dopdop.bopdop.ui.components.Brand.LIBREFM else com.dopdop.bopdop.ui.components.Brand.LASTFM,
                ) { app.sheet = Sheet.LastFm }
            }
            }
        }
        item(key = "group:Help") {
            CompositionLocalProvider(LocalQuery provides query, LocalTotal provides total) {
                Group("Help") {
                    NavRow(Icons.Rounded.BugReport, "Report a problem", "Send what went wrong with BopDop's recent log, so it can be fixed.", keywords = "bug crash feedback log help support") {
                        app.sheet = Sheet.ReportProblem
                    }
                }
            }
        }
        item(key = "group:With friends") {
            CompositionLocalProvider(LocalQuery provides query, LocalTotal provides total) {
            Group("With friends") {
                    NavRow(Icons.Rounded.Groups, "Listen Together", "Start or join a room and listen with friends.") {
                        app.nav.navigate("together?code=")
                    }
                }
            }
        }
        // Built lazily: only the groups on screen are composed when Settings opens.
        item {
            if (query.isNotBlank() && total.n == 0) {
                Text("No settings match “${query.trim()}”", style = BopType.Body, color = Bop.Text2, modifier = Modifier.fillMaxWidth().padding(36.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            if (query.isBlank()) {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("BopDop", style = BopType.Title, color = Bop.Text)
                    Text("v${BuildConfig.VERSION_NAME} · DopDop music engine", style = BopType.Caption, color = Bop.Text3)
                    // Updates: what's newer on GitHub, or a check on demand.
                    val update by com.dopdop.bopdop.data.Updates.available.collectAsState()
                    val scope = androidx.compose.runtime.rememberCoroutineScope()
                    val u = update
                    val install by com.dopdop.bopdop.data.UpdateInstaller.state.collectAsState()
                    (install as? com.dopdop.bopdop.data.InstallState.Failed)?.let { Text(it.message, style = BopType.Caption, color = Color(0xFFFF6B8B), modifier = Modifier.padding(top = 8.dp)) }
                    Text(
                        if (u != null) com.dopdop.bopdop.ui.components.updateButtonLabel(install).let { if (it == "Update") "Install ${u.title}" else it } else "Check for updates",
                        style = BopType.BodyStrong, color = if (u != null) Bop.OnInk else Bop.Text,
                        modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(50))
                            .background(if (u != null) Bop.Ink else Bop.Veil.copy(alpha = 0.55f))
                            .bounceClick {
                                if (u != null) app.nav.navigate(com.dopdop.bopdop.ui.Routes.UPDATE)
                                else scope.launch {
                                    app.toast("Checking for updates…")
                                    val found = com.dopdop.bopdop.data.Updates.check(force = true)
                                    app.toast(if (found != null) "${found.title} is available" else "You're on the latest version")
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                    Text("YouTube Music catalogue · JioSaavn 320 kbps · LRCLIB lyrics", style = BopType.Caption, color = Bop.Text3)
                }
            }
        }
    }
}

// --- Search ---------------------------------------------------------------------------
// Each setting row checks the query itself; a group shows its card only while something in it
// matches. Matching the group's own name shows the whole group.

private class Hits { var n by mutableIntStateOf(0) }

private val LocalQuery = compositionLocalOf { "" }
private val LocalGroup = compositionLocalOf<Pair<String, Hits>?> { null }
private val LocalTotal = compositionLocalOf<Hits?> { null }

/** Every word of [query] appears somewhere in [text] (hyphens ignored, so "wifi" finds "Wi-Fi"). */
internal fun settingMatches(query: String, vararg text: String): Boolean {
    val words = query.trim().lowercase().replace("-", "").split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val hay = text.joinToString(" ").lowercase().replace("-", "")
    return words.all { it in hay }
}

@Composable
private fun Searchable(vararg text: String, content: @Composable () -> Unit) {
    val group = LocalGroup.current
    val total = LocalTotal.current
    val hit = settingMatches(LocalQuery.current, group?.first.orEmpty(), *text)
    DisposableEffect(hit, group, total) {
        if (hit) { group?.second?.let { it.n++ }; total?.let { it.n++ } }
        onDispose { if (hit) { group?.second?.let { it.n-- }; total?.let { it.n-- } } }
    }
    if (hit) content()
}

@Composable
private fun SettingsSearch(query: String, onChange: (String) -> Unit) {
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    Row(
        Modifier.padding(horizontal = 16.dp).padding(top = 16.dp).fillMaxWidth().height(48.dp)
            .glassLite(RoundedCornerShape(50), alpha = 0.5f).padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = Bop.Text2, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text("Search settings", style = BopType.Body, color = Bop.Text3)
            androidx.compose.foundation.text.BasicTextField(
                query, onChange, singleLine = true,
                textStyle = BopType.Body.copy(color = Bop.Text), cursorBrush = androidx.compose.ui.graphics.SolidColor(Bop.Ink),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) Box(Modifier.size(32.dp).clip(CircleShape).bounceClick { onChange("") }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Close, "Clear", tint = Bop.Text2, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    val hits = remember { Hits() }
    val group = remember(title) { title to hits }
    val shown = LocalQuery.current.isBlank() || hits.n > 0
    if (shown) Text(title.uppercase(), style = BopType.Micro, color = Bop.Text3, modifier = Modifier.padding(start = 28.dp, top = 22.dp, bottom = 8.dp))
    Column(if (shown) Modifier.padding(horizontal = 16.dp).fillMaxWidth().glassLite(RoundedCornerShape(24.dp), alpha = 0.5f, shadow = 14.dp) else Modifier.fillMaxWidth()) {
        val column = this
        CompositionLocalProvider(LocalGroup provides group) { column.content() }
    }
}

@Composable
private fun IconBadge(icon: ImageVector, brand: com.dopdop.bopdop.ui.components.Brand? = null) {
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Bop.Veil.copy(alpha = 0.9f)).glassEdges(RoundedCornerShape(10.dp), 0.8f, true), contentAlignment = Alignment.Center) {
        if (brand != null) com.dopdop.bopdop.ui.components.BrandLogo(brand, 22.dp, icon, fallbackTint = Bop.Ink)
        else Icon(icon, null, tint = Bop.Ink, modifier = Modifier.size(19.dp))
    }
}

/** A setting's title, with its choices ([below]) underneath. */
@Composable
private fun RowHead(icon: ImageVector, title: String, sub: String, keywords: String = "", below: @Composable () -> Unit = {}) = Searchable(title, sub, keywords) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
        IconBadge(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = BopType.BodyStrong, color = Bop.Text)
            Text(sub, style = BopType.Caption, color = Bop.Text2)
        }
    }
    below()
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, sub: String, on: Boolean, keywords: String = "", onToggle: () -> Unit) = Searchable(title, sub, keywords) {
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

/** Single-choice pill chips under a [RowHead]. */
@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    FlowRow(Modifier.padding(start = 56.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            val on = o == selected
            Text(
                label(o), style = BopType.Caption, color = if (on) Bop.OnInk else Bop.Text,
                modifier = Modifier.bounceClick { onPick(o) }.clip(RoundedCornerShape(50))
                    .background(if (on) Bop.Ink else Bop.Veil.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

/**
 * The font choices as chips, each label set in the font it picks. "Your font" shows once a file is
 * imported (by its own name, in itself); the last chip imports one.
 */
@Composable
private fun FontChips(s: com.dopdop.bopdop.data.Settings, onPick: (com.dopdop.bopdop.data.AppFont) -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val custom = com.dopdop.bopdop.data.CustomFont.file(ctx, s.customFont)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) app.scope.launch {
            val got = com.dopdop.bopdop.data.CustomFont.import(ctx, uri)
            if (got == null) app.toast("That file isn't a font BopDop can use")
            else {
                LibraryStore.updateSettings { it.copy(font = com.dopdop.bopdop.data.AppFont.CUSTOM, customFont = got.first, customFontName = got.second) }
                app.toast("Using ${got.second}")
            }
        }
    }
    FlowRow(Modifier.padding(start = 56.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        com.dopdop.bopdop.data.AppFont.entries.filter { it != com.dopdop.bopdop.data.AppFont.CUSTOM || custom != null }.forEach { f ->
            val on = f == s.font
            Text(
                if (f == com.dopdop.bopdop.data.AppFont.CUSTOM) s.customFontName.ifBlank { f.label } else f.label,
                style = BopType.Body.copy(fontFamily = f.family(custom)), maxLines = 1,
                color = if (on) Bop.OnInk else Bop.Text,
                modifier = Modifier.bounceClick { onPick(f) }.clip(RoundedCornerShape(50))
                    .background(if (on) Bop.Ink else Bop.Veil.copy(alpha = 0.55f)).padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        Text(
            if (custom == null) "+ Import font" else "+ Another font", style = BopType.Body, color = Bop.Text,
            modifier = Modifier.bounceClick {
                // Font files come with many types (font/ttf, application/x-font-ttf, octet-stream...):
                // offer everything and let the import check it.
                pick.launch(arrayOf("*/*"))
            }.clip(RoundedCornerShape(50)).border(1.dp, Bop.Text.copy(alpha = 0.25f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/** Mutually exclusive options in one glass track, like the Glass picker. */
@Composable
private fun Segmented(options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.padding(start = 56.dp, end = 16.dp, bottom = 14.dp).fillMaxWidth().glassLite(RoundedCornerShape(14.dp), alpha = 0.45f).padding(4.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val bg by animateColorAsState(if (on) Bop.Ink else Color.Transparent, label = "seg")
            Text(
                label, style = BopType.BodyStrong, color = if (on) Bop.OnInk else Bop.Text2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(bg).bounceClick { onPick(i) }.padding(vertical = 9.dp),
            )
        }
    }
}

/**
 * Back up to a file the user picks, or restore one. Restoring replaces the library, so it shows
 * what the file holds and asks first.
 */
@Composable
private fun BackupRows() {
    val app = LocalApp.current
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<org.json.JSONObject?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) app.scope.launch {
            runCatching { com.dopdop.bopdop.data.Backup.write(ctx, uri) }
                .onSuccess { app.toast("Backed up") }.onFailure { app.toast("Couldn't save the backup") }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) app.scope.launch {
            val o = runCatching { com.dopdop.bopdop.data.Backup.read(ctx, uri) }.getOrNull()
            if (o == null) app.toast("That isn't a BopDop backup") else pending = o
        }
    }
    NavRow(Icons.Rounded.Save, "Back up to a file", "Liked songs, playlists, saved albums, history, plays, settings and lyric timings.", keywords = "export backup save transfer phone") {
        save.launch(com.dopdop.bopdop.data.Backup.fileName())
    }
    NavRow(Icons.Rounded.Restore, "Restore from a file", "Replaces what's on this phone with a backup.", keywords = "import restore backup transfer phone") {
        open.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
    }
    pending?.let { o ->
        val sum = remember(o) { com.dopdop.bopdop.data.Backup.summary(o) }
        val date = remember(sum) {
            java.time.Instant.ofEpochMilli(sum.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Restore backup from $date?") },
            text = { Text("${sum.liked} liked songs, ${sum.playlists} playlists and ${sum.plays} plays. This replaces your library, history and settings on this phone.") },
            confirmButton = {
                Text("Restore", style = BopType.BodyStrong, color = Color(0xFFFF6B8B), modifier = Modifier.bounceClick {
                    com.dopdop.bopdop.data.Backup.restore(o); pending = null; app.toast("Library restored")
                }.padding(12.dp))
            },
            dismissButton = {
                Text("Cancel", style = BopType.BodyStrong, color = Bop.Text2, modifier = Modifier.bounceClick { pending = null }.padding(12.dp))
            },
        )
    }
}

/**
 * Lossless and Atmos sources the user adds by URL (the common music add-on protocol), with the
 * switches that use them, plus bit-perfect USB output.
 */
@Composable
private fun AddonRows() {
    val app = LocalApp.current
    val s = LibraryStore.state.collectAsState().value.settings
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val names = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    LaunchedEffect(s.addons) {
        s.addons.forEach { base -> runCatching { com.dopdop.bopdop.data.addon.Addons.manifest(base) }.getOrNull()?.let { names[base] = it.name + if (it.version.isNotBlank()) " · v" + it.version else "" } }
    }
    SourcesList(s, names)
    ToggleRow(Icons.Rounded.SurroundSound, "Dolby Atmos", if (com.dopdop.bopdop.data.addon.DeviceAudio.playsAtmos()) "Ask add-ons for Atmos mixes. This phone can play them." else "This phone has no Atmos decoder, so add-ons are asked for stereo.", s.atmos, keywords = "atmos dolby spatial surround") {
        LibraryStore.updateSettings { it.copy(atmos = !it.atmos) }
    }
    if (com.dopdop.bopdop.playback.BitPerfect.supported) ToggleRow(Icons.Rounded.Usb, "Bit-perfect USB output", "With a USB DAC: the song's own sample rate and bit depth, untouched. Volume moves to the DAC; the equalizer and normalization pause.", s.bitPerfect, keywords = "usb dac bit perfect exclusive audiophile") {
        LibraryStore.updateSettings { it.copy(bitPerfect = !it.bitPerfect) }
    }
    Searchable("Add-ons", "add-on server url manifest", "addon source") {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
            Text("Add an add-on", style = BopType.BodyStrong, color = Bop.Text, modifier = Modifier.padding(start = 40.dp, top = 6.dp))
            Text("Paste an add-on's URL. BopDop includes none; use only sources you have the right to play.", style = BopType.Caption, color = Bop.Text2, modifier = Modifier.padding(start = 40.dp, bottom = 8.dp))
            Row(Modifier.padding(start = 40.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp)).background(Bop.Veil.copy(alpha = 0.55f)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                    if (url.isEmpty()) Text("https://…", style = BopType.Body, color = Bop.Text3)
                    androidx.compose.foundation.text.BasicTextField(url, { url = it; error = null }, singleLine = true, enabled = !busy,
                        textStyle = BopType.Body.copy(color = Bop.Text), cursorBrush = androidx.compose.ui.graphics.SolidColor(Bop.Ink), modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.width(8.dp))
                Text(if (busy) "…" else "Add", style = BopType.BodyStrong, color = Bop.OnInk, modifier = Modifier.clip(RoundedCornerShape(50)).background(Bop.Ink).bounceClick {
                    val base = com.dopdop.bopdop.data.addon.Addons.normalize(url) ?: run { error = "Use an https:// address"; return@bounceClick }
                    if (base in s.addons) { error = "Already added"; return@bounceClick }
                    busy = true
                    app.scope.launch {
                        runCatching { com.dopdop.bopdop.data.addon.Addons.manifest(base) }
                            .onSuccess { m -> LibraryStore.updateSettings { it.copy(addons = it.addons + base) }; names[base] = m.name; url = ""; app.toast("Added ${m.name}") }
                            .onFailure { error = it.message ?: "That add-on didn't answer" }
                        busy = false
                    }
                }.padding(horizontal = 16.dp, vertical = 10.dp))
            }
            error?.let { Text(it, style = BopType.Caption, color = Color(0xFFFF6B8B), modifier = Modifier.padding(start = 40.dp, top = 6.dp)) }
        }
    }
}

/**
 * Where songs play from, best first: each add-on, JioSaavn's 320 kbps copy and YouTube Music.
 * A song starts on the best built-in source and moves to a higher add-on if one has it. Every
 * source but YouTube (which has every song) can be switched off, and any can be moved.
 */
@Composable
private fun SourcesList(s: com.dopdop.bopdop.data.Settings, names: Map<String, String>) = Searchable("Sources", "Priority order of add-ons, JioSaavn and YouTube Music; switch sources off; test add-ons", "source priority order addon saavn youtube test lossless flac") {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val order = s.sources()
    val tests = remember { androidx.compose.runtime.mutableStateMapOf<String, com.dopdop.bopdop.data.addon.Addons.TestResult?>() }
    val testing = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    fun move(key: String, by: Int) {
        val list = order.toMutableList()
        val i = list.indexOf(key)
        val j = (i + by).coerceIn(0, list.lastIndex)
        if (i < 0 || i == j) return
        list.removeAt(i); list.add(j, key)
        LibraryStore.updateSettings { it.copy(sourceOrder = list) }
    }
    Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 10.dp)) {
        Text("Sources", style = BopType.BodyStrong, color = Bop.Text, modifier = Modifier.padding(start = 40.dp))
        Text("Best first. Songs start right away on JioSaavn or YouTube, then move to a higher add-on without a gap when it has them.",
            style = BopType.Caption, color = Bop.Text2, modifier = Modifier.padding(start = 40.dp, bottom = 6.dp))
        // What the playing song is coming from, and what the add-ons did for it: answers "why isn't this lossless?".
        val playing by com.dopdop.bopdop.playback.PlayerConnection.current.collectAsState()
        val qualities by com.dopdop.bopdop.playback.StreamResolver.quality.collectAsState()
        val notes by com.dopdop.bopdop.playback.StreamResolver.addonNotes.collectAsState()
        playing?.let { t ->
            val q = qualities[t.id]
            Column(Modifier.padding(start = 40.dp, bottom = 6.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(Bop.Veil.copy(alpha = 0.35f)).padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text("Now: ${t.title}", style = BopType.Caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(600)), color = Bop.Text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text("From ${q?.via ?: "…"}${q?.let { " · " + it.label } ?: ""}", style = BopType.Caption, color = Bop.Text2)
                notes[t.id]?.let { Text("Add-ons: $it", style = BopType.Caption, color = Bop.Text2) }
            }
        }
        order.forEachIndexed { i, key ->
            val addon = key != com.dopdop.bopdop.data.Settings.SAAVN && key != com.dopdop.bopdop.data.Settings.YOUTUBE
            val on = s.sourceOn(key)
            val title = when (key) {
                com.dopdop.bopdop.data.Settings.SAAVN -> "JioSaavn"
                com.dopdop.bopdop.data.Settings.YOUTUBE -> "YouTube Music"
                else -> names[key] ?: "Checking…"
            }
            val sub = when (key) {
                com.dopdop.bopdop.data.Settings.SAAVN -> "320 kbps AAC copy of the same recording, when it has one"
                com.dopdop.bopdop.data.Settings.YOUTUBE -> "Every song. Always on: it plays whatever the others don't"
                else -> key.removePrefix("https://")
            }
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp))
                .background(Bop.Veil.copy(alpha = if (on) 0.5f else 0.25f)).padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", style = BopType.Headline, color = if (on) Bop.Text else Bop.Text3, modifier = Modifier.width(26.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = BopType.BodyStrong, color = if (on) Bop.Text else Bop.Text3, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(sub, style = BopType.Caption, color = Bop.Text3, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(8.dp))
                    if (key == com.dopdop.bopdop.data.Settings.YOUTUBE) Text("Always on", style = BopType.Caption, color = Bop.Text2, modifier = Modifier.padding(end = 8.dp))
                    else Box(Modifier.bounceClick {
                        LibraryStore.updateSettings {
                            if (key == com.dopdop.bopdop.data.Settings.SAAVN) it.copy(hiRes = !it.hiRes)
                            else it.copy(addonsOff = if (key in it.addonsOff) it.addonsOff - key else it.addonsOff + key)
                        }
                    }) { GlassSwitch(on) }
                }
                Row(Modifier.padding(start = 22.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.KeyboardArrowUp, "Move up", tint = if (i > 0) Bop.Text else Bop.Text3.copy(alpha = 0.4f),
                        modifier = Modifier.size(34.dp).clip(CircleShape).bounceClick { move(key, -1) }.padding(5.dp))
                    Icon(Icons.Rounded.KeyboardArrowDown, "Move down", tint = if (i < order.lastIndex) Bop.Text else Bop.Text3.copy(alpha = 0.4f),
                        modifier = Modifier.size(34.dp).clip(CircleShape).bounceClick { move(key, 1) }.padding(5.dp))
                    Spacer(Modifier.weight(1f))
                    if (addon) {
                        val busy = key in testing
                        Text(if (busy) "Testing…" else "Test", style = BopType.Caption, color = Bop.Text,
                            modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, Bop.Text.copy(alpha = 0.25f), RoundedCornerShape(50))
                                .bounceClick {
                                    if (busy) return@bounceClick
                                    testing += key; tests[key] = null
                                    app.scope.launch {
                                        tests[key] = com.dopdop.bopdop.data.addon.Addons.test(key, com.dopdop.bopdop.playback.PlayerConnection.current.value, ctx)
                                        testing -= key
                                    }
                                }.padding(horizontal = 12.dp, vertical = 6.dp))
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Rounded.Close, "Remove", tint = Bop.Text2, modifier = Modifier.size(34.dp).clip(CircleShape).bounceClick {
                            LibraryStore.updateSettings { it.copy(addons = it.addons - key, addonsOff = it.addonsOff - key, sourceOrder = it.sourceOrder - key) }
                        }.padding(7.dp))
                    }
                }
                tests[key]?.let { r ->
                    Text((if (r.ok) "✓ " else "✕ ") + r.message + " · " + "%.1f s".format(r.ms / 1000f), style = BopType.Caption,
                        color = if (r.ok) Color(0xFF34C759) else Color(0xFFFF6B8B), modifier = Modifier.padding(start = 26.dp, top = 2.dp, bottom = 2.dp))
                }
            }
        }
    }
}

/** Home-screen icon: three looks, previewed as they'll appear. */
@Composable
private fun AppIconRow() = Searchable("App icon", "Classic, orange glass or light glass", "launcher home screen logo dark") {
    val ctx = LocalContext.current
    var current by remember { mutableStateOf<com.dopdop.bopdop.AppIcon?>(null) }
    LaunchedEffect(Unit) { current = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.dopdop.bopdop.AppIcon.chosen(ctx) } }
    val app = LocalApp.current
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.AutoAwesome)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("App icon", style = BopType.BodyStrong, color = Bop.Text)
                Text("Changes when you leave BopDop; your launcher may take a moment.", style = BopType.Caption, color = Bop.Text2)
            }
        }
        Row(Modifier.padding(start = 56.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            com.dopdop.bopdop.AppIcon.entries.forEach { icon ->
                val on = icon == current
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.bounceClick {
                    if (!on) {
                        com.dopdop.bopdop.AppIcon.choose(ctx, icon); current = icon
                        app.toast("${icon.label} icon: it changes when you leave BopDop")
                    }
                }) {
                    Box(
                        Modifier.size(58.dp).clip(RoundedCornerShape(16.dp))
                            .border(if (on) 2.5.dp else 0.dp, if (on) Bop.Ink else Color.Transparent, RoundedCornerShape(16.dp)),
                    ) {
                        // Adaptive icons show the middle two thirds of their 108dp layers.
                        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(icon.background), null, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f })
                        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(icon.foreground), null, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f })
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(icon.label, style = BopType.Caption, color = if (on) Bop.Text else Bop.Text2)
                }
            }
        }
    }
}

/** A row that opens something else. */
@Composable
private fun NavRow(icon: ImageVector, title: String, sub: String, keywords: String = "", brand: com.dopdop.bopdop.ui.components.Brand? = null, onClick: () -> Unit) = Searchable(title, sub, keywords) {
    Row(Modifier.fillMaxWidth().bounceClick(0.99f, onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, brand)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = BopType.BodyStrong, color = Bop.Text)
            Text(sub, style = BopType.Caption, color = Bop.Text2)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Bop.Text3, modifier = Modifier.size(22.dp))
    }
}
