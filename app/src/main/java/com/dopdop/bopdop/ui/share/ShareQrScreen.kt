package com.dopdop.bopdop.ui.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.dopdop.bopdop.data.share.ShareCodec
import com.dopdop.bopdop.data.share.SharePayload
import com.dopdop.bopdop.data.ytm.YtMusic
import com.dopdop.bopdop.ui.LocalApp
import com.dopdop.bopdop.ui.SeeAllHolder
import com.dopdop.bopdop.ui.ShareRequest
import com.dopdop.bopdop.ui.components.PillButton
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.glass.LocalReduceMotion
import com.dopdop.bopdop.ui.glass.glassEdges
import com.dopdop.bopdop.ui.glass.glassLite
import com.dopdop.bopdop.ui.glass.rememberArtPalette
import com.dopdop.bopdop.ui.glass.softShadow
import com.dopdop.bopdop.ui.screens.BackButton
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.sin

/** Share a playlist / album as a 3D QR diorama — live, rotatable, verified to scan. */
@Composable
fun ShareQrScreen(key: String) {
    val app = LocalApp.current
    val req = SeeAllHolder.get(key)?.second as? ShareRequest ?: run { app.nav.popBackStack(); return }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current
    var look by remember { mutableStateOf(QrLook()) }
    var flat by remember { mutableStateOf(false) }
    val palette = rememberArtPalette(req.artwork.ifBlank { null })
    val themeFromCover = remember(palette) { coverTheme(palette.accent.toArgb(), palette.washDeep.toArgb()) }
    val theme = look.theme ?: themeFromCover
    val link = remember(req) { ShareCodec.encodeForQr(req.payload) }
    val fullLink = remember(req) { ShareCodec.encode(req.payload) }

    val cover by produceState<Bitmap?>(null, req.artwork) {
        if (req.artwork.isBlank()) return@produceState
        val r = ImageRequest.Builder(ctx).data(YtMusic.resize(req.artwork, 400)).size(400).allowHardware(false).build()
        val res = ctx.imageLoader.execute(r)
        value = (res as? SuccessResult)?.image?.toBitmap()
    }
    val withCover = look.coverInCode && cover != null
    val matrix = remember(link, withCover) { QrArt.matrix(link, withCover) }
    val iso = remember(matrix, theme, look.shape, cover, withCover, look.decor) {
        IsoQr(matrix, theme, look.shape, if (withCover) cover else null, look.decor, seed = link.hashCode())
    }

    // The share image + whether its 3D code reads on its own (re-rendered off the main thread).
    val card by produceState<ShareCard?>(null, theme, look, cover) {
        value = null
        delay(250)
        value = withContext(Dispatchers.Default) {
            runCatching { QrArt.card(ctx, link, req.title, req.subtitle, cover, theme, look) }.getOrNull()
        }
    }

    // Live stage motion: 3D ⇄ flat, drag to turn, idle sway.
    val tilt by animateFloatAsState(if (flat) 0f else 1f, spring(dampingRatio = 0.72f, stiffness = 140f), label = "tilt")
    val yawDrag = remember { Animatable(45f) }
    val time by produceState(0f, reduce) {
        if (reduce) return@produceState
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) withInfiniteAnimationFrameMillis { value = (it - start) / 1000f }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 28.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton { app.nav.popBackStack() }
            Text("Share code", style = BopType.Headline, color = Bop.Text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(42.dp).bounceClick(0.88f) { app.openScanner() }.glassLite(CircleShape, alpha = 0.7f, shadow = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.QrCodeScanner, "Scan a code", tint = Bop.Text, modifier = Modifier.size(21.dp)) }
        }

        // --- Stage -------------------------------------------------------------------------
        BoxWithConstraints(Modifier.fillMaxWidth().height(380.dp)) {
            Canvas(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectHorizontalDragGestures(onDragEnd = {
                        scope.launch { yawDrag.animateTo(45f, spring(dampingRatio = 0.5f, stiffness = 60f)) }
                    }) { change, d ->
                        change.consume()
                        scope.launch { yawDrag.snapTo((yawDrag.value - d * 0.25f).coerceIn(5f, 85f)) }
                    }
                },
            ) {
                val sway = if (flat) 0f else (sin(time * 0.8f) * 5f)
                drawIntoCanvas { c ->
                    val nc = c.nativeCanvas
                    if (tilt > 0.05f) drawGroundShadow(nc, size.width, size.height, (46 * tilt).toInt())
                    iso.draw(nc, size.width, size.height, tilt, yawDrag.value + sway, card?.takeIf { it.scans3d }?.pitch ?: 56f)
                }
            }
        }

        // 3D / Flat segmented control
        Row(Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)) {
            Segmented(listOf("3D", "Flat"), if (flat) 1 else 0) { flat = it == 1 }
        }
        ScanStatus(card, flat)

        Text(req.title, style = BopType.Title, color = Bop.Text, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 2.dp))
        Text(req.subtitle, style = BopType.Body, color = Bop.Text2, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp))
        val p = req.payload
        if (p is SharePayload.Playlist && p.ids.size > ShareCodec.QR_MAX_TRACKS) {
            Text(
                "The code carries the first ${ShareCodec.QR_MAX_TRACKS} of ${p.ids.size} songs so it stays easy to scan — “Link” sends them all.",
                style = BopType.Caption, color = Bop.Text3, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 6.dp),
            )
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Share", Icons.Rounded.IosShare, Modifier.weight(1.3f)) {
                card?.let { shareImage(ctx, it.bitmap, req.title, fullLink) } ?: app.toast("Rendering…")
            }
            if (Build.VERSION.SDK_INT >= 29) PillButton("Save", Icons.Rounded.Download, Modifier.weight(1f), filled = false) {
                card?.let { app.toast(if (saveToGallery(ctx, it.bitmap, req.title)) "Saved to Pictures/BopDop" else "Couldn’t save") }
            }
            PillButton("Link", Icons.Rounded.Link, Modifier.weight(1f), filled = false) {
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "${req.title} on BopDop\n$fullLink"), "Share link"))
            }
        }

        // --- Style ------------------------------------------------------------------------
        Panel {
            Label("World")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeSwatch("Cover", themeFromCover, look.theme == null) { look = look.copy(theme = null) }
                IsoThemes.forEach { t -> ThemeSwatch(t.name, t, look.theme == t) { look = look.copy(theme = t) } }
            }
            Label("Blocks")
            Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BlockShape.entries.forEach { s -> Chip(s.label, look.shape == s) { look = look.copy(shape = s) } }
            }
            Label("Details")
            Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Cover in code", look.coverInCode) { look = look.copy(coverInCode = !look.coverInCode) }
                Chip("Grass", look.decor) { look = look.copy(decor = !look.decor) }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun ScanStatus(card: ShareCard?, flat: Boolean) {
    val (icon, text, color) = when {
        card == null -> Triple(null, "Checking the code scans…", Bop.Text3)
        flat || card.scans3d -> Triple(Icons.Rounded.CheckCircle, if (flat) "Flat view — point any camera at the screen" else "Verified: this 3D code scans", Color(0xFF2E9E5B))
        else -> Triple(Icons.Rounded.Info, "Too detailed to scan in 3D — the image adds a small flat code", Color(0xFFB7791F))
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (icon == null) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.6.dp, color = Bop.Text3)
        else Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = BopType.Caption, color = if (icon == null) Bop.Text3 else color)
    }
}

/** Glass capsule with a sliding white lens — the same control language as the tab bar. */
@Composable
fun Segmented(options: List<String>, selected: Int, itemW: androidx.compose.ui.unit.Dp = 88.dp, onSelect: (Int) -> Unit) {
    val lens by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.65f, stiffness = 420f), label = "seg")
    Box(Modifier.height(44.dp).width(itemW * options.size + 8.dp).glassLite(RoundedCornerShape(50), alpha = 0.5f, shadow = 10.dp).padding(4.dp)) {
        Box(
            Modifier.offset(x = itemW * lens).width(itemW).fillMaxHeight()
                .softShadow(RoundedCornerShape(50), 8.dp, alpha = 0.14f, dy = 2.dp).clip(RoundedCornerShape(50)).background(Color.White),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, o ->
                Box(Modifier.width(itemW).fillMaxHeight().clip(RoundedCornerShape(50)).bounceClick(0.94f) { onSelect(i) }, contentAlignment = Alignment.Center) {
                    Text(o, style = BopType.BodyStrong, color = if (i == selected) Bop.Text else Bop.Text2)
                }
            }
        }
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().glassLite(RoundedCornerShape(28.dp), alpha = 0.55f, shadow = 14.dp).padding(vertical = 6.dp)) { content() }
}

@Composable
private fun Label(text: String) =
    Text(text.uppercase(), style = BopType.Micro, color = Bop.Text3, modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 8.dp))

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Bop.Ink else Color.White.copy(alpha = 0.7f), label = "chip")
    Text(
        label, style = BopType.BodyStrong, color = if (selected) Color.White else Bop.Text,
        modifier = Modifier.bounceClick(0.94f, onClick = onClick).clip(RoundedCornerShape(50)).background(bg)
            .glassEdges(RoundedCornerShape(50), if (selected) 0f else 1f, true).padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

/** A tiny isometric cube in the theme's colours. */
@Composable
private fun ThemeSwatch(name: String, t: IsoTheme, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.bounceClick(0.9f, onClick = onClick)) {
        Box(
            Modifier.size(58.dp).clip(RoundedCornerShape(18.dp))
                .background(if (selected) Color.White else Color.White.copy(alpha = 0.45f))
                .glassEdges(RoundedCornerShape(18.dp), if (selected) 1f else 0.6f, true),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(38.dp)) {
                val w = size.width; val h = size.height
                fun quad(c: Int, vararg p: Float) {
                    val path = androidx.compose.ui.graphics.Path().apply { moveTo(p[0], p[1]); lineTo(p[2], p[3]); lineTo(p[4], p[5]); lineTo(p[6], p[7]); close() }
                    drawPath(path, Color(c))
                }
                // ground slab
                quad(t.ground, w * 0.5f, h * 0.38f, w, h * 0.62f, w * 0.5f, h * 0.86f, 0f, h * 0.62f)
                quad(t.soil, 0f, h * 0.62f, w * 0.5f, h * 0.86f, w * 0.5f, h, 0f, h * 0.76f)
                quad(androidx.core.graphics.ColorUtils.blendARGB(t.soil, 0xFF000000.toInt(), 0.18f), w * 0.5f, h * 0.86f, w, h * 0.62f, w, h * 0.76f, w * 0.5f, h)
                // eye block
                quad(t.eye, w * 0.5f, h * 0.14f, w * 0.78f, h * 0.28f, w * 0.5f, h * 0.42f, w * 0.22f, h * 0.28f)
                quad(androidx.core.graphics.ColorUtils.blendARGB(t.eye, 0xFFFFFFFF.toInt(), 0.18f), w * 0.22f, h * 0.28f, w * 0.5f, h * 0.42f, w * 0.5f, h * 0.62f, w * 0.22f, h * 0.48f)
                quad(androidx.core.graphics.ColorUtils.blendARGB(t.eye, 0xFF000000.toInt(), 0.22f), w * 0.5f, h * 0.42f, w * 0.78f, h * 0.28f, w * 0.78f, h * 0.48f, w * 0.5f, h * 0.62f)
                // grass blade
                quad(t.grass, w * 0.82f, h * 0.6f, w * 0.9f, h * 0.36f, w * 0.94f, h * 0.6f, w * 0.88f, h * 0.64f)
            }
        }
        Text(name, style = BopType.Caption, color = if (selected) Bop.Text else Bop.Text3, modifier = Modifier.padding(top = 5.dp))
    }
}

private fun fileName(title: String) = "bopdop-" + title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifEmpty { "code" } + ".png"

private fun shareImage(ctx: Context, bmp: Bitmap, title: String, link: String) {
    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
    val f = File(dir, fileName(title))
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    val send = Intent(Intent.ACTION_SEND).setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, "$title on BopDop — scan it in BopDop or open:\n$link")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, "Share code"))
}

private fun saveToGallery(ctx: Context, bmp: Bitmap, title: String): Boolean = runCatching {
    if (Build.VERSION.SDK_INT < 29) return false
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName(title))
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BopDop")
    }
    val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } != null
}.getOrDefault(false)
