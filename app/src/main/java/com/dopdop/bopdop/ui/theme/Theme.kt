package com.dopdop.bopdop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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
 * Liquid Glass palette: white type and clear glass floating over a vivid,
 * slowly flowing aurora (blue → violet → magenta → coral) that takes its hues
 * from whatever is playing. Solid "on" states use the system blue.
 */
object Bop {
    /** Graphite — content sitting on bright white fills (play disc, white pills). */
    val Ink = Color(0xFF15161B)
    /** Primary text and icons on glass / aurora. */
    val Text = Color.White
    val Text2 = Color(0xA8FFFFFF)
    val Text3 = Color(0x6BFFFFFF)
    val Hairline = Color(0x1FFFFFFF)
    /** Deep indigo base under the aurora (also the window background). */
    val Base = Color(0xFF161338)
    /** Active toggles, primary buttons, cursors — the Control Center blue. */
    val Accent = Color(0xFF0A5CFF)
    val AccentDeep = Color(0xFF0646D6)
    val Green = Color(0xFF30C35A)
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
    val Danger = Color(0xFFFF6B8B)
    val Brand = Brush.linearGradient(listOf(Sky, Violet))
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
        colorScheme = darkColorScheme(
            primary = Bop.Accent, onPrimary = Color.White,
            secondary = Bop.Violet, background = Bop.Base, surface = Color(0xFF221E4A),
            onBackground = Bop.Text, onSurface = Bop.Text,
            surfaceVariant = Color(0xFF2C2858), onSurfaceVariant = Bop.Text2,
        ),
        content = content,
    )
}
