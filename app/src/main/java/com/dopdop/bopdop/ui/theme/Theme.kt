package com.dopdop.bopdop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
 * Light, airy "soft glass on liquid metal" palette: graphite text on a pearl /
 * silver backdrop, white glass, photos carry the colour.
 */
object Bop {
    /** Graphite — primary text and solid buttons. */
    val Ink = Color(0xFF15161B)
    val Text = Color(0xFF15161B)
    /** Clear glass lets the backdrop through, so secondary text firms up to stay readable. */
    var clear by androidx.compose.runtime.mutableStateOf(false)
    val Text2 get() = if (clear) Color(0xE015161B) else Color(0x9E15161B)
    val Text3 get() = if (clear) Color(0xB815161B) else Color(0x6615161B)
    val Hairline = Color(0x1415161B)
    /** Base of the metal backdrop (also the window background). */
    val Pearl = Color(0xFFDCDFE4)
    /** Text / icons sitting on photos and colour washes. */
    val OnMedia = Color.White
    val OnMedia2 = Color(0xCCFFFFFF)
    val OnMedia3 = Color(0x99FFFFFF)
    val Violet = Color(0xFF7A4DFF)
    val Magenta = Color(0xFFE0359B)
    val Orange = Color(0xFFFF8A3D)
    val Sage = Color(0xFF8FA65A)
    val Sky = Color(0xFF8FB3D9)
    val Gold = Color(0xFFFFD36B)
    val Like = Color(0xFFFF3B6B)
    /** Active / primary accent on light glass. */
    val Accent = Color(0xFF15161B)
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
    val Hero = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight(600), fontSize = 36.sp, letterSpacing = (-0.035).em, lineHeight = 40.sp)
    val Display = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight(600), fontSize = 30.sp, letterSpacing = (-0.03).em, lineHeight = 34.sp)
    val Title = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight(600), fontSize = 21.sp, letterSpacing = (-0.02).em, lineHeight = 26.sp)
    val Headline = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight(600), fontSize = 17.sp, letterSpacing = (-0.01).em, lineHeight = 22.sp)
    val Body = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight(500), fontSize = 15.sp, letterSpacing = (-0.005).em, lineHeight = 20.sp)
    val BodyStrong = Body.copy(fontWeight = FontWeight(600))
    val Caption = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight(500), fontSize = 13.sp, lineHeight = 17.sp)
    val Micro = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight(700), fontSize = 10.5.sp, letterSpacing = 0.08.em)
    val Lyric = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight(700), fontSize = 29.sp, letterSpacing = (-0.03).em, lineHeight = 36.sp)
}

@Composable
fun BopDopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Bop.Ink, onPrimary = Color.White,
            secondary = Bop.Sage, background = Bop.Pearl, surface = Color(0xFFF2F3F5),
            onBackground = Bop.Text, onSurface = Bop.Text,
            surfaceVariant = Color(0xFFE6E8EC), onSurfaceVariant = Bop.Text2,
        ),
        content = content,
    )
}
