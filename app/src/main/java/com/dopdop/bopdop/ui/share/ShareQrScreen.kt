package com.dopdop.bopdop.ui.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
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
import com.dopdop.bopdop.ui.components.GlassChip
import com.dopdop.bopdop.ui.components.PillButton
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.glass.glassEdges
import com.dopdop.bopdop.ui.glass.glassLite
import com.dopdop.bopdop.ui.glass.rememberArtPalette
import com.dopdop.bopdop.ui.glass.softShadow
import com.dopdop.bopdop.ui.screens.BackButton
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Styled QR share card: pick dots, eyes, colours, logo and ground; share or save the image. */
@Composable
fun ShareQrScreen(key: String) {
    val app = LocalApp.current
    val req = SeeAllHolder.get(key)?.second as? ShareRequest ?: run { app.nav.popBackStack(); return }
    val ctx = LocalContext.current
    var style by remember { mutableStateOf(QrStyle()) }
    val palette = rememberArtPalette(req.artwork.ifBlank { null })
    val artStart = palette.accent.toArgb()
    val artEnd = palette.washDeep.toArgb()
    val link = remember(req) { ShareCodec.encodeForQr(req.payload) }
    val fullLink = remember(req) { ShareCodec.encode(req.payload) }

    val cover by produceState<Bitmap?>(null, req.artwork) {
        if (req.artwork.isBlank()) return@produceState
        val r = ImageRequest.Builder(ctx).data(YtMusic.resize(req.artwork, 400)).size(400).allowHardware(false).build()
        value = (ctx.imageLoader.execute(r) as? SuccessResult)?.image?.toBitmap()
    }
    val card by produceState<Bitmap?>(null, style, cover, artStart, artEnd) {
        value = withContext(Dispatchers.Default) {
            runCatching { QrArt.card(ctx, link, req.title, req.subtitle, cover, style, artStart, artEnd) }.getOrNull()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                BackButton { app.nav.popBackStack() }
                Text("Share code", style = BopType.Headline, color = Bop.Text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(42.dp))
            }
            // Preview of exactly the image that gets shared.
            val shape = RoundedCornerShape(30.dp)
            Box(
                Modifier.padding(horizontal = 44.dp, vertical = 14.dp).fillMaxWidth().aspectRatio(1080f / 1533f)
                    .softShadow(shape, 26.dp, alpha = 0.35f, dy = 10.dp).clip(shape).glassLite(shape, alpha = 0.06f),
                contentAlignment = Alignment.Center,
            ) {
                card?.let { Image(it.asImageBitmap(), "Share code for ${req.title}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            }
            val p = req.payload
            if (p is SharePayload.Playlist && p.ids.size > ShareCodec.QR_MAX_TRACKS) {
                Text(
                    "The code carries the first ${ShareCodec.QR_MAX_TRACKS} of ${p.ids.size} songs so it stays easy to scan. “Link” shares them all.",
                    style = BopType.Caption, color = Bop.Text2, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 4.dp),
                )
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Share", Icons.Rounded.IosShare, Modifier.weight(1.3f)) {
                    card?.let { shareImage(ctx, it, req.title, fullLink) }
                }
                if (Build.VERSION.SDK_INT >= 29) PillButton("Save", Icons.Rounded.Download, Modifier.weight(1f), filled = false) {
                    card?.let { app.toast(if (saveToGallery(ctx, it, req.title)) "Saved to Pictures/BopDop" else "Couldn’t save") }
                }
                PillButton("Link", Icons.Rounded.Link, Modifier.weight(1f), filled = false) {
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "${req.title} on BopDop\n$fullLink"), "Share link"))
                }
            }

            Panel {
                Label("Dots")
                ChipRow { DotStyle.entries.forEach { d -> GlassChip(d.label, style.dots == d) { style = style.copy(dots = d) } } }
                Label("Corners")
                ChipRow { EyeStyle.entries.forEach { e -> GlassChip(e.label, style.eyes == e) { style = style.copy(eyes = e) } } }
                Label("Colour")
                ChipRow {
                    Swatch("Cover", Color(artStart), Color(artEnd), style.palette == null) { style = style.copy(palette = null) }
                    QrPresets.forEach { q -> Swatch(q.name, Color(q.start), Color(q.end), style.palette == q) { style = style.copy(palette = q) } }
                }
                Label("Centre")
                ChipRow { LogoStyle.entries.forEach { l -> GlassChip(l.label, style.logo == l) { style = style.copy(logo = l) } } }
                Label("Background")
                ChipRow { CardBg.entries.forEach { b -> GlassChip(b.label, style.bg == b) { style = style.copy(bg = b) } } }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().glassLite(RoundedCornerShape(28.dp), alpha = 0.12f, shadow = 14.dp).padding(vertical = 6.dp)) { content() }
}

@Composable
private fun Label(text: String) =
    Text(text.uppercase(), style = BopType.Micro, color = Bop.Text3, modifier = Modifier.padding(start = 18.dp, top = 12.dp, bottom = 8.dp))

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun Swatch(name: String, a: Color, b: Color, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.bounceClick(0.9f, onClick = onClick)) {
        Box(Modifier.size(42.dp).glassLite(CircleShape, alpha = if (selected) 0.4f else 0.08f).padding(if (selected) 4.dp else 3.dp)) {
            Canvas(Modifier.fillMaxSize().clip(CircleShape).glassEdges(CircleShape, 0.7f)) {
                drawRect(Brush.linearGradient(listOf(a, b), start = Offset.Zero, end = Offset(size.width, size.height)))
            }
        }
        Text(name, style = BopType.Micro.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified), color = if (selected) Bop.Text else Bop.Text3, modifier = Modifier.padding(top = 4.dp))
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
