package com.dopdop.bopdop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dopdop.bopdop.R

/**
 * "Soft glass on liquid metal". Light: graphite text on a pearl / silver backdrop, white glass.
 * Dark: soft white text on a deep gunmetal backdrop, smoked glass. Photos carry the colour in
 * both. The tokens read [dark], so everything drawn with them follows the theme.
 */
object Bop {
    /** Dark theme on; set by the root from Settings (or the system). */
    var dark by androidx.compose.runtime.mutableStateOf(false)
    /** Clear glass lets the backdrop through, so secondary text firms up to stay readable. */
    var clear by androidx.compose.runtime.mutableStateOf(false)
    /** A complete seasonal palette; explicit light/dark preference is still respected. */
    var halloween by androidx.compose.runtime.mutableStateOf(false)
    /** Diwali's diya-lit palette, from the same 15 colours as its pixel art. */
    var diwali by androidx.compose.runtime.mutableStateOf(false)
    /** A pixel-art festival is on: its glyphs replace the Material icons. */
    val pixel get() = halloween || diwali

    /** Always graphite: icons and text on white discs and pills, whatever the theme. */
    val Graphite = Color(0xFF15161B)
    private val Snow = Color(0xFFF2F2F5)

    /** Primary text and solid buttons: graphite in light, soft white in dark. */
    val Ink get() = if (halloween) (if (dark) Color(0xFFF4E4C1) else Color(0xFF251C2E)) else if (diwali) (if (dark) Color(0xFFFFF1C9) else Color(0xFF251B3A)) else if (dark) Snow else Graphite
    /** Text and icons on an [Ink] surface. */
    val OnInk get() = if (dark) Graphite else Color.White
    val Text get() = Ink
    val Text2 get() = if (halloween) (if (dark) Color(0xFFD2BEDD) else Color(0xFF67536E)) else if (diwali) (if (dark) Color(0xFFEBD3C1) else Color(0xFF6A4B66)) else if (dark) (if (clear) Color(0xE0F2F2F5) else Color(0xA8F2F2F5)) else if (clear) Color(0xE015161B) else Color(0x9E15161B)
    val Text3 get() = if (halloween) (if (dark) Color(0xFFBCA6CE) else Color(0xFF6D586D)) else if (diwali) (if (dark) Color(0xFFC9A9B6) else Color(0xFF7E5F78)) else if (dark) (if (clear) Color(0xB8F2F2F5) else Color(0x70F2F2F5)) else if (clear) Color(0xB815161B) else Color(0x6615161B)
    val Hairline get() = if (dark) Color(0x1FF2F2F5) else Color(0x1415161B)
    /** Base of the metal backdrop (also the window background). */
    val Pearl get() = if (halloween) (if (dark) Color(0xFF140E1D) else Color(0xFFF2E7D1)) else if (diwali) (if (dark) Color(0xFF17152D) else Color(0xFFFFF1C9)) else if (dark) Color(0xFF101116) else Color(0xFFDCDFE4)
    /** The tint glass and chips are made of: white in light, smoked graphite in dark. */
    val Veil get() = if (halloween) (if (dark) Color(0xFF2B2137) else Color(0xFFF1E4D2)) else if (diwali) (if (dark) Color(0xFF292147) else Color(0xFFFFF6E0)) else if (dark) Color(0xFF2A2B33) else Color.White
    /** A selected light chip (graphite text on it in both themes). */
    val Chip get() = if (dark) Snow else Color.White
    /** Text / icons sitting on photos and colour washes. */
    val OnMedia get() = if (halloween) Color(0xFFF4E4C1) else if (diwali) Color(0xFFFFF1C9) else Color.White
    val OnMedia2 get() = if (halloween) Color(0xFFE0CFDF) else if (diwali) Color(0xFFF3DCCB) else Color(0xCCFFFFFF)
    val OnMedia3 get() = if (halloween) Color(0xFFC5AFCF) else if (diwali) Color(0xFFD9BDB9) else Color(0x99FFFFFF)
    val Violet = Color(0xFF7A4DFF)
    val Magenta = Color(0xFFE0359B)
    val Orange = Color(0xFFFF8A3D)
    val Sage = Color(0xFF8FA65A)
    val Sky = Color(0xFF8FB3D9)
    val Gold = Color(0xFFFFD36B)
    val Like get() = if (halloween) Color(0xFFFFA34C) else if (diwali) Color(0xFFF4A23A) else Color(0xFFFF3B6B)
    /** Active / primary accent on glass. */
    val Accent get() = if (halloween) (if (dark) Color(0xFFF7A34F) else Color(0xFFA7441B)) else if (diwali) (if (dark) Color(0xFFFFD166) else Color(0xFF9E421E)) else Ink
    val MediaAccent get() = if (halloween) Color(0xFFF7A34F) else if (diwali) Color(0xFFFFD166) else Color.White
    val Danger = Color(0xFFE5484D)
    val Brand = Brush.linearGradient(listOf(Sky, Sage))
}

@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: Int) = Font(
    R.font.jakarta,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Jakarta = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700), jakarta(800))

object BopType {
    // A compact scale: phones already enlarge text with the system font size.
    private class Styles(f: FontFamily) {
        val hero = TextStyle(fontFamily = f, fontWeight = FontWeight(600), fontSize = 31.sp, letterSpacing = (-0.035).em, lineHeight = 35.sp)
        val display = TextStyle(fontFamily = f, fontWeight = FontWeight(600), fontSize = 25.sp, letterSpacing = (-0.03).em, lineHeight = 29.sp)
        val title = TextStyle(fontFamily = f, fontWeight = FontWeight(600), fontSize = 18.5.sp, letterSpacing = (-0.02).em, lineHeight = 23.sp)
        val headline = TextStyle(fontFamily = f, fontWeight = FontWeight(600), fontSize = 15.5.sp, letterSpacing = (-0.01).em, lineHeight = 20.sp)
        val body = TextStyle(fontFamily = f, fontWeight = FontWeight(500), fontSize = 14.sp, letterSpacing = (-0.005).em, lineHeight = 19.sp)
        val bodyStrong = body.copy(fontWeight = FontWeight(600))
        val caption = TextStyle(fontFamily = f, fontWeight = FontWeight(500), fontSize = 12.sp, lineHeight = 16.sp)
        val micro = TextStyle(fontFamily = f, fontWeight = FontWeight(700), fontSize = 10.sp, letterSpacing = 0.08.em)
        val lyric = TextStyle(fontFamily = f, fontWeight = FontWeight(700), fontSize = 26.sp, letterSpacing = (-0.03).em, lineHeight = 32.sp)
    }
    private var styles by mutableStateOf(Styles(Jakarta))

    /** The family every style uses: the font picked in Settings › Appearance. Text redraws when it changes. */
    var family: FontFamily = Jakarta
        set(value) { if (value != field) { field = value; styles = Styles(value) } }

    val Hero get() = styles.hero
    val Display get() = styles.display
    val Title get() = styles.title
    val Headline get() = styles.headline
    val Body get() = styles.body
    val BodyStrong get() = styles.bodyStrong
    val Caption get() = styles.caption
    val Micro get() = styles.micro
    val Lyric get() = styles.lyric
}

/**
 * The fonts on offer: BopDop's own, families every Android phone already has (nothing to download),
 * and the user's imported [custom] file, which falls back to BopDop's font if it has gone missing.
 */
fun com.dopdop.bopdop.data.AppFont.family(custom: java.io.File? = null): FontFamily = when (this) {
    com.dopdop.bopdop.data.AppFont.BOPDOP -> Jakarta
    com.dopdop.bopdop.data.AppFont.SYSTEM -> FontFamily.Default
    com.dopdop.bopdop.data.AppFont.CONDENSED -> condensed
    com.dopdop.bopdop.data.AppFont.SERIF -> FontFamily.Serif
    com.dopdop.bopdop.data.AppFont.MONO -> FontFamily.Monospace
    com.dopdop.bopdop.data.AppFont.CASUAL -> casual
    com.dopdop.bopdop.data.AppFont.CURSIVE -> FontFamily.Cursive
    com.dopdop.bopdop.data.AppFont.CUSTOM -> custom?.let { customFamilies.getOrPut(it.path) { FontFamily(Font(it)) } } ?: Jakarta
}

/** One FontFamily per imported file, so recompositions don't keep reloading it. */
private val customFamilies = HashMap<String, FontFamily>()

@OptIn(ExperimentalTextApi::class)
private val condensed = FontFamily(
    Font(androidx.compose.ui.text.font.DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Normal),
    Font(androidx.compose.ui.text.font.DeviceFontFamilyName("sans-serif-condensed-medium"), FontWeight.Medium),
    Font(androidx.compose.ui.text.font.DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Bold),
)

@OptIn(ExperimentalTextApi::class)
private val casual = FontFamily(Font(androidx.compose.ui.text.font.DeviceFontFamilyName("casual")))

@Composable
fun BopDopTheme(content: @Composable () -> Unit) {
    val library by com.dopdop.bopdop.data.LibraryStore.state.collectAsState()
    val settings = library.settings
    val festive = com.dopdop.bopdop.ui.festive.rememberFestive(settings.festiveLevel, settings.festivePick, settings.halloweenPreview, settings.diwaliPreview)
    Bop.halloween = festive.festival == com.dopdop.bopdop.data.Festival.HALLOWEEN
    Bop.diwali = festive.festival == com.dopdop.bopdop.data.Festival.DIWALI
    androidx.compose.runtime.CompositionLocalProvider(com.dopdop.bopdop.ui.festive.LocalFestive provides festive) {
    MaterialTheme(
        colorScheme = if (Bop.dark) androidx.compose.material3.darkColorScheme(
            primary = Bop.Accent, onPrimary = Bop.Graphite,
            secondary = if (Bop.pixel) Bop.Accent else Bop.Sage, background = Bop.Pearl, surface = if (Bop.pixel) Bop.Veil else Color(0xFF1A1B21),
            onBackground = Bop.Text, onSurface = Bop.Text,
            surfaceVariant = Color(0xFF24252C), onSurfaceVariant = Bop.Text2,
        ) else lightColorScheme(
            primary = Bop.Accent, onPrimary = Color.White,
            secondary = if (Bop.pixel) Bop.Accent else Bop.Sage, background = Bop.Pearl, surface = if (Bop.pixel) Bop.Veil else Color(0xFFF2F3F5),
            onBackground = Bop.Text, onSurface = Bop.Text,
            surfaceVariant = Color(0xFFE6E8EC), onSurfaceVariant = Bop.Text2,
        ),
        content = content,
    )
    }
}
