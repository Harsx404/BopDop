package com.dopdop.bopdop.ui.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Sms
import com.dopdop.bopdop.ui.festive.ThemedIcon as Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ClippingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.dopdop.bopdop.data.MusicRepository
import com.dopdop.bopdop.data.model.Lyrics
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.net.Http
import com.dopdop.bopdop.data.ytm.YtMusic
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.playback.VideoResolver
import com.dopdop.bopdop.ui.LocalApp
import com.dopdop.bopdop.ui.components.Artwork
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.glass.ArtPalette
import com.dopdop.bopdop.ui.glass.rememberArtPalette
import com.dopdop.bopdop.ui.player.Bars
import com.dopdop.bopdop.ui.player.coverPastBars
import com.dopdop.bopdop.ui.player.forScript
import com.dopdop.bopdop.ui.player.measureBars
import com.dopdop.bopdop.ui.player.measuredBars
import com.dopdop.bopdop.ui.player.trackLink
import com.dopdop.bopdop.ui.theme.BopType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What sits behind a share card: the song's moving picture (or its art), its colour, or a fade. */
private enum class Backdrop { MOTION, COLOR, FADE }

private enum class CardKind(val label: String) { SONG("Song"), LYRICS("Lyrics") }

/** A few seconds of the music video at the point you're listening, looped silently behind the card. */
private data class Clip(val url: String, val userAgent: String?, val startMs: Long, val aspect: Float)

private val Panel = Color(0xFF1C1C21)
private const val CLIP_MS = 8_000L

/**
 * Share a song the way you'd show a friend: a story-sized card with the cover framed over the
 * song's own music video (or its art), or the lyrics you're hearing right now. Swipe or switch
 * between the two, pick a backdrop, then send it straight to a chat, Instagram, a text or anywhere.
 */
@Composable
fun ShareStudio(t: Track, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val palette = rememberArtPalette(t.artwork)
    val lyrics by produceState<Lyrics?>(null, t.id) { value = runCatching { MusicRepository.lyrics(t) }.getOrNull() }
    val lines = remember(lyrics) { lyrics?.let { linesNow(it, if (PlayerConnection.current.value?.id == t.id) PlayerConnection.position else 0L) }.orEmpty() }
    val kinds = if (lines.isEmpty()) listOf(CardKind.SONG) else CardKind.entries
    val pager = rememberPagerState { kinds.size }
    val backdrops = remember { mutableStateListOf(Backdrop.MOTION, Backdrop.COLOR) }
    val layers = listOf(rememberGraphicsLayer(), rememberGraphicsLayer())
    val clip by produceState<Clip?>(null, t.id) {
        // The player is only readable on the main thread: take the position before going to IO.
        val listening = if (PlayerConnection.current.value?.id == t.id) PlayerConnection.position else 0L
        value = withContext(Dispatchers.IO) {
            runCatching { clipFor(t, listening) }.onFailure { android.util.Log.w("ShareStudio", "No video clip for ${t.id}", it) }.getOrNull()
        }
    }
    var video by remember { mutableStateOf<TextureView?>(null) }
    var freeze by remember { mutableStateOf<ImageBitmap?>(null) }

    /** The card on screen as a picture; a playing video is frozen into it first. */
    suspend fun snapshot(): Bitmap {
        val page = pager.currentPage
        if (kinds[page] == CardKind.SONG && backdrops[page] == Backdrop.MOTION) {
            freeze = video?.bitmap?.asImageBitmap()
            withFrameNanos { }; withFrameNanos { }
        }
        return layers[page].toImageBitmap().asAndroidBitmap().also { freeze = null }
    }
    val caption = "${t.title} · ${t.artist}\n${trackLink(t)}"
    fun send(build: (Uri) -> Intent) = scope.launch {
        val uri = cardFile(ctx, snapshot(), t.title)
        val intent = build(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { ctx.startActivity(intent) } catch (_: ActivityNotFoundException) {
            ctx.startActivity(Intent.createChooser(imageIntent(uri, caption).setPackage(null), "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
                .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Panel)
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.padding(top = 10.dp, bottom = 14.dp).size(40.dp, 5.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.25f)))
                HorizontalPager(
                    state = pager,
                    contentPadding = PaddingValues(horizontal = 80.dp),
                    pageSpacing = 16.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) { page ->
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(18.dp))
                            .drawWithContent { layers[page].record { this@drawWithContent.drawContent() }; drawLayer(layers[page]) },
                    ) {
                        when (kinds[page]) {
                            CardKind.SONG -> SongCard(t, palette, backdrops[page], clip, freeze.takeIf { page == pager.currentPage }) { video = it }
                            CardKind.LYRICS -> LyricsCard(t, palette, backdrops[page], lines)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                if (kinds.size > 1) {
                    SegmentedSwitch(kinds.map { it.label }, pager.currentPage) { scope.launch { pager.animateScrollToPage(it) } }
                    Spacer(Modifier.height(14.dp))
                }
                BackdropDots(t, palette, kinds[pager.currentPage], backdrops[pager.currentPage]) { backdrops[pager.currentPage] = it }
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.08f)))
                Spacer(Modifier.height(16.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    item {
                        Target(Icons.Rounded.Link, "Copy link", Color.White.copy(alpha = 0.12f)) {
                            ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(t.title, caption))
                            app.toast("Link copied")
                        }
                    }
                    item { Target(Icons.Rounded.Chat, "WhatsApp", Color(0xFF25D366)) { send { imageIntent(it, caption).setPackage("com.whatsapp") } } }
                    item {
                        Target(Icons.Rounded.CameraAlt, "Instagram", null) { send { imageIntent(it, caption).setPackage("com.instagram.android") } }
                    }
                    item {
                        Target(Icons.Rounded.Sms, "Message", Color.White.copy(alpha = 0.12f)) {
                            try {
                                ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).putExtra("sms_body", caption).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (_: ActivityNotFoundException) { app.toast("No messaging app") }
                        }
                    }
                    item {
                        Target(Icons.Rounded.MoreHoriz, "More", Color.White.copy(alpha = 0.12f)) {
                            send { Intent.createChooser(imageIntent(it, caption), "Share ${t.title}") }
                        }
                    }
                }
            }
        }
    }
    val player = clip?.let { rememberClipPlayer(it) }
    // Follow the card's surface: it changes when pages recycle or the backdrop is switched.
    LaunchedEffect(player, video) { player?.let { p -> video?.let(p::setVideoTextureView) ?: p.clearVideoSurface() } }
}

// --- Cards ----------------------------------------------------------------------------------

@Composable
private fun SongCard(t: Track, palette: ArtPalette, backdrop: Backdrop, clip: Clip?, freeze: ImageBitmap?, onVideo: (TextureView?) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        when (backdrop) {
            Backdrop.MOTION -> {
                // The art stands in until (or unless) the video arrives.
                Artwork(t.artwork, Modifier.fillMaxSize().then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(18.dp) else Modifier), corner = 0.dp, size = 600)
                if (clip != null) {
                    // Centre-crop the landscape video into the tall card, past any bars baked into it.
                    var view by remember { mutableStateOf<TextureView?>(null) }
                    var bars by remember(t.id) { mutableStateOf(measuredBars[t.id] ?: Bars.NONE) }
                    LaunchedEffect(view) {
                        val tv = view ?: return@LaunchedEffect
                        if (t.id !in measuredBars) measureBars(tv, { true }, first = 1200, every = 1500) { bars = it; measuredBars[t.id] = it }
                    }
                    val crop by animateFloatAsState(if (bars == Bars.NONE) 0f else 1f, tween(500), label = "crop")
                    val pic = coverPastBars(clip.aspect, bars, crop)
                    AndroidView(
                        factory = { c -> TextureView(c).also { view = it; onVideo(it) } },
                        modifier = pic,
                        onRelease = { view = null; onVideo(null) },
                    )
                    freeze?.let { Image(it, null, pic, contentScale = ContentScale.Crop) }
                }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)))
            }
            Backdrop.COLOR -> Box(Modifier.fillMaxSize().background(palette.wash))
            Backdrop.FADE -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(palette.wash, Color(0xFF0E0E12)))))
        }
        // The framed cover, title and artist — the part of the card that says what it is.
        Column(
            Modifier.fillMaxWidth(0.82f).clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = if (backdrop == Backdrop.MOTION) 0.42f else 0.18f)).padding(10.dp),
        ) {
            Artwork(t.artwork, Modifier.fillMaxWidth().aspectRatio(1f), corner = 6.dp, size = 600)
            Spacer(Modifier.height(10.dp))
            Text(t.title, style = BopType.BodyStrong.forScript(t.title), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(t.artist, style = BopType.Caption, color = Color.White.copy(alpha = 0.75f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Brand()
        }
    }
}

@Composable
private fun LyricsCard(t: Track, palette: ArtPalette, backdrop: Backdrop, lines: List<String>) {
    Box(Modifier.fillMaxSize()) {
        when (backdrop) {
            Backdrop.MOTION -> {
                Artwork(t.artwork, Modifier.fillMaxSize().then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(30.dp) else Modifier), corner = 0.dp, size = 400)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            }
            Backdrop.COLOR -> Box(Modifier.fillMaxSize().background(palette.washDeep))
            Backdrop.FADE -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(palette.wash, Color(0xFF0E0E12)))))
        }
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(t.artwork, Modifier.size(34.dp), corner = 6.dp, size = 200)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(t.title, style = BopType.Caption.copy(fontWeight = FontWeight(700)).forScript(t.title), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, style = BopType.Caption, color = Color.White.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.weight(1f))
            val size = when (lines.sumOf { it.length }) { in 0..60 -> 26.sp; in 61..110 -> 22.sp; else -> 18.sp }
            lines.forEach { line ->
                Text(line, style = BopType.Lyric.copy(fontSize = size, lineHeight = size * 1.18f, fontWeight = FontWeight(800)).forScript(line), color = Color.White,
                    modifier = Modifier.padding(vertical = 2.dp))
            }
            Spacer(Modifier.weight(1f))
            Brand()
        }
    }
}

@Composable
private fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Text("B", style = BopType.Micro.copy(fontSize = 9.sp, letterSpacing = 0.sp), color = Color(0xFF15161B))
        }
        Spacer(Modifier.width(5.dp))
        Text("BopDop", style = BopType.Caption.copy(fontWeight = FontWeight(700)), color = Color.White)
    }
}

// --- Controls -------------------------------------------------------------------------------

@Composable
private fun SegmentedSwitch(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(0.76f).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f)).padding(3.dp)) {
        labels.forEachIndexed { i, label ->
            val bg by animateColorAsState(if (i == selected) Color.White else Color.Transparent, label = "seg")
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(bg).bounceClick(0.96f) { onSelect(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = BopType.BodyStrong, color = if (i == selected) Color(0xFF15161B) else Color.White) }
        }
    }
}

@Composable
private fun BackdropDots(t: Track, palette: ArtPalette, kind: CardKind, selected: Backdrop, onPick: (Backdrop) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Backdrop.entries.forEach { b ->
            val on = b == selected
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .border(2.dp, if (on) Color.White else Color.Transparent, CircleShape).padding(4.dp)
                    .clip(CircleShape).bounceClick(0.9f) { onPick(b) },
            ) {
                when (b) {
                    Backdrop.MOTION -> Artwork(t.artwork, Modifier.fillMaxSize(), circle = true, size = 160)
                    Backdrop.COLOR -> Box(Modifier.fillMaxSize().background(if (kind == CardKind.LYRICS) palette.washDeep else palette.wash))
                    Backdrop.FADE -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(palette.wash, Color(0xFF0E0E12)))))
                }
            }
        }
    }
}

@Composable
private fun Target(icon: ImageVector, label: String, color: Color?, onClick: () -> Unit) {
    Column(Modifier.width(64.dp).bounceClick(0.92f, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(
                if (color != null) Brush.linearGradient(listOf(color, color))
                // Instagram's sunset.
                else Brush.linearGradient(listOf(Color(0xFFFEDA75), Color(0xFFFA7E1E), Color(0xFFD62976), Color(0xFF962FBF), Color(0xFF4F5BD5))),
            ),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(label, style = BopType.Caption, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// --- Video clip -----------------------------------------------------------------------------

@OptIn(UnstableApi::class)
@Composable
private fun rememberClipPlayer(clip: Clip): ExoPlayer {
    val ctx = LocalContext.current
    val player = remember(clip) {
        ExoPlayer.Builder(ctx).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            val source = ProgressiveMediaSource.Factory(OkHttpDataSource.Factory(Http.client).setUserAgent(clip.userAgent))
                .createMediaSource(MediaItem.fromUri(clip.url))
            setMediaSource(ClippingMediaSource(source, clip.startMs * 1000, (clip.startMs + CLIP_MS) * 1000))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

/** The song's music video, at the moment of the song you're on, in a small size that loads fast. */
private suspend fun clipFor(t: Track, listening: Long): Clip? {
    val match = YtMusic.musicVideoFor(t) ?: return null
    val streams = VideoResolver.prepare(match.videoId, 360)
    val songAt = match.segments.singleOrNull()?.let { it.videoStartMs - it.songStartMs }?.coerceAtLeast(0) ?: 0L
    val videoMs = match.durationSec * 1000L
    var start = songAt + listening
    if (videoMs > CLIP_MS && start > videoMs - CLIP_MS) start = (videoMs - CLIP_MS).coerceAtLeast(0)
    val aspect = if (streams.width > 0 && streams.height > 0) streams.width.toFloat() / streams.height else 16f / 9f
    return Clip(streams.video.url, streams.video.userAgent, start, aspect)
}

// --- Lyrics & files -------------------------------------------------------------------------

/** The line playing at [positionMs] and the next few, about a story card's worth. */
internal fun linesNow(l: Lyrics, positionMs: Long): List<String> {
    val all = if (l.isSynced) l.synced.map { it.text } else l.plain.orEmpty().lines()
    val from = if (l.isSynced) l.synced.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0) else 0
    val out = mutableListOf<String>()
    for (line in all.drop(from)) {
        val s = line.trim()
        if (s.isEmpty() || s == "♪") { if (out.isNotEmpty()) break else continue }
        if (out.size == 4 || out.sumOf { it.length } + s.length > 150) break
        out += s
    }
    return out
}

private fun cardFile(ctx: Context, bmp: Bitmap, name: String): Uri {
    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
    val safe = name.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').take(40).ifEmpty { "song" }
    val f = File(dir, "bopdop-$safe.png")
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
}

private fun imageIntent(uri: Uri, text: String) = Intent(Intent.ACTION_SEND).setType("image/png")
    .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, text)
    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
