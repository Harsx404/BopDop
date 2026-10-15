package com.dopdop.bopdop.ui.glass

import android.content.Context
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Colours derived from artwork: deep wells (c0..c3), a bright [accent], a
 * mid-tone [wash] that the Now Playing art melts into, and two [pastel]s that
 * tint the liquid-metal backdrop.
 */
@Immutable
data class ArtPalette(
    val c0: Color, val c1: Color, val c2: Color, val c3: Color, val accent: Color,
    val wash: Color, val washDeep: Color, val pastel: Color, val pastel2: Color,
) {
    companion object {
        val Default = ArtPalette(
            Color(0xFF2B1466), Color(0xFF4C0F3E), Color(0xFF0D1C52), Color(0xFF14081F), Color(0xFFFF5AA8),
            wash = Color(0xFF7D9A45), washDeep = Color(0xFF4F6527),
            pastel = Color(0xFFD3E0F0), pastel2 = Color(0xFFA9B8CC),
        )
    }
}

private val paletteCache = LruCache<String, ArtPalette>(64)

private fun tone(argb: Int, lMin: Float, lMax: Float, sBoost: Float = 1.1f): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(argb, hsl)
    hsl[1] = (hsl[1] * sBoost).coerceIn(0f, 0.95f)
    hsl[2] = hsl[2].coerceIn(lMin, lMax)
    return Color(ColorUtils.HSLToColor(hsl))
}

suspend fun extractPalette(context: Context, url: String): ArtPalette? {
    if (url.isBlank()) return null
    paletteCache.get(url)?.let { return it }
    val req = ImageRequest.Builder(context).data(url).size(160).allowHardware(false).build()
    val bmp = (context.imageLoader.execute(req) as? SuccessResult)?.image?.toBitmap() ?: return null
    return withContext(Dispatchers.Default) {
        val p = Palette.from(bmp).maximumColorCount(16).generate()
        val dom = p.getDominantColor(0xFF2B1466.toInt())
        val swatches = listOfNotNull(p.darkVibrantSwatch, p.vibrantSwatch, p.mutedSwatch, p.darkMutedSwatch, p.lightVibrantSwatch)
            .sortedByDescending { it.population }.map { it.rgb }
        val pick = { i: Int -> swatches.getOrNull(i) ?: dom }
        // The wash continues the bottom of the artwork, so sample its lower band.
        val lower = Palette.from(bmp).setRegion(0, bmp.height * 2 / 3, bmp.width, bmp.height).maximumColorCount(8).generate()
        val washSrc = (lower.vibrantSwatch ?: lower.mutedSwatch ?: lower.dominantSwatch)?.rgb ?: dom
        val accentSrc = p.lightVibrantSwatch?.rgb ?: p.vibrantSwatch?.rgb ?: p.lightMutedSwatch?.rgb ?: dom
        ArtPalette(
            c0 = tone(dom, 0.16f, 0.36f),
            c1 = tone(pick(0), 0.14f, 0.34f, 1.25f),
            c2 = tone(pick(1), 0.10f, 0.28f),
            c3 = tone(pick(2), 0.06f, 0.18f),
            accent = tone(accentSrc, 0.62f, 0.78f, 1.2f),
            wash = tone(washSrc, 0.40f, 0.50f, 1.15f),
            washDeep = tone(washSrc, 0.22f, 0.30f, 1.1f),
            // Metal tints: light crest tone + deeper trough tone, saturated enough to read as colour.
            pastel = tone(accentSrc, 0.76f, 0.84f, 1.35f),
            pastel2 = tone(washSrc, 0.66f, 0.74f, 1.3f),
        ).also { paletteCache.put(url, it) }
    }
}

@Composable
fun rememberArtPalette(url: String?): ArtPalette {
    val ctx = LocalContext.current
    val p by produceState(url?.let { paletteCache.get(it) } ?: ArtPalette.Default, url) {
        value = url?.let { runCatching { extractPalette(ctx, it) }.getOrNull() } ?: ArtPalette.Default
    }
    return p
}

fun Color.readableOn(): Color = if (ColorUtils.calculateLuminance(toArgb()) > 0.5) Color.Black else Color.White
