package com.dopdop.bopdop.ui.festive

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import com.dopdop.bopdop.R
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dopdop.bopdop.ui.glass.LocalReduceMotion
import com.dopdop.bopdop.ui.theme.Bop
import kotlin.math.floor
import kotlin.math.roundToInt

/** AI-generated 24-pixel atlas family; ordinal order is recorded in the saved prompts. */
enum class HalloweenGlyph {
    HOME, EXPLORE, LIBRARY, SEARCH, HEART, HEART_OUTLINE, PLAY, PAUSE, NEXT, PREVIOUS,
    LYRICS, TOGETHER, OUTPUT, HEADPHONES, QUEUE, MORE, DOWN, BACK, CLOSE, ADD, CHECK,
    DOWNLOAD, SHUFFLE, REPEAT, SETTINGS, MUSIC, MOON, SUN, PUMPKIN, PET, EQUALIZER, TRASH, SHARE, GLASS, VIDEO, MENU,
    VOLUME, VOLUME_HIGH, VOLUME_OFF, BLUETOOTH, USB, CAST, ACCOUNT, GLOBE, HISTORY, FOLDER;

    companion object {
        fun fromName(name: String): HalloweenGlyph? = when (name.substringAfterLast('.')) {
            "Home" -> HOME; "Explore", "ExploreOff" -> EXPLORE; "LibraryMusic", "LibraryBooks", "Album", "CollectionsBookmark" -> LIBRARY
            "Search", "ManageSearch" -> SEARCH; "Favorite" -> HEART; "FavoriteBorder" -> HEART_OUTLINE
            "PlayArrow", "PlayCircle", "PlayCircleFilled" -> PLAY; "Pause", "PauseCircle" -> PAUSE
            "SkipNext", "FastForward" -> NEXT; "SkipPrevious", "FastRewind" -> PREVIOUS
            "Lyrics", "Subtitles" -> LYRICS; "Groups", "Group", "People" -> TOGETHER
            "PhoneAndroid", "Devices" -> OUTPUT; "Cast" -> CAST; "Usb" -> USB; "Bluetooth" -> BLUETOOTH
            "VolumeDown" -> VOLUME; "VolumeUp", "Speaker" -> VOLUME_HIGH; "VolumeOff", "VolumeMute" -> VOLUME_OFF
            "Headphones", "Headset" -> HEADPHONES; "QueueMusic", "PlaylistPlay", "Queue", "PlaylistAdd" -> QUEUE
            "MoreHoriz", "MoreVert" -> MORE; "Menu" -> MENU; "KeyboardArrowDown", "ExpandMore" -> DOWN
            "ArrowBack", "ArrowBackIos", "ChevronLeft" -> BACK; "Close", "Clear" -> CLOSE
            "Add", "AddCircle", "AddCircleOutline" -> ADD; "Check", "Done", "CheckCircle" -> CHECK
            "Download", "FileDownload", "Downloading", "OfflinePin" -> DOWNLOAD
            "Shuffle" -> SHUFFLE; "Repeat", "RepeatOne" -> REPEAT; "Settings", "Tune" -> SETTINGS
            "MusicNote", "Audiotrack", "AudioFile" -> MUSIC; "DarkMode", "Bedtime", "Nightlight" -> MOON
            "LightMode", "WbSunny" -> SUN; "Celebration", "AutoAwesome" -> PUMPKIN; "Pets" -> PET
            "Equalizer", "GraphicEq" -> EQUALIZER; "Delete", "DeleteOutline" -> TRASH
            "Share", "IosShare" -> SHARE; "WaterDrop", "BlurOn" -> GLASS; "Videocam", "VideoLibrary" -> VIDEO
            "AccountCircle", "Person", "PersonOutline", "ManageAccounts" -> ACCOUNT
            "Public", "Language" -> GLOBE; "History", "Schedule", "AccessTime" -> HISTORY
            "Folder", "FolderOpen", "FolderSpecial" -> FOLDER
            else -> null
        }
    }
}

@Composable
fun HalloweenGlyphIcon(glyph: HalloweenGlyph, description: String?, modifier: Modifier = Modifier, tint: Color = Bop.Text, active: Boolean = false) {
    val atlas = ImageBitmap.imageResource(when (glyph.ordinal / 16) {
        0 -> R.drawable.halloween_icons_a
        1 -> R.drawable.halloween_icons_b
        else -> R.drawable.halloween_icons_c
    })
    val cell = glyph.ordinal % 16
    val reduce = LocalReduceMotion.current
    val selection by animateFloatAsState(if (active) 1f else 0f, spring(dampingRatio = 0.58f, stiffness = 500f), label = "halloweenIcon")
    val clock = rememberHalloweenTime(active, fps = 12)
    Canvas(modifier.then(Modifier.size(24.dp)).graphicsLayer {
        // Lift by whole physical pixels: scaling/rotating the raster-like glyph would soften its grid.
        if (!reduce) translationY = (-selection * 1.dp.toPx()).roundToInt().toFloat()
    }.then(if (description == null) Modifier else Modifier.semantics { contentDescription = description })) {
        val u = floor(size.minDimension / 24f).coerceAtLeast(1f)
        val left = ((size.width - u * 24) / 2).roundToInt().toFloat()
        val top = ((size.height - u * 24) / 2).roundToInt().toFloat()
        drawImage(atlas, srcOffset = IntOffset(cell % 4 * 24, cell / 4 * 24),
            srcSize = IntSize(24, 24), dstOffset = IntOffset(left.toInt(), top.toInt()),
            dstSize = IntSize((24 * u).toInt(), (24 * u).toInt()),
            colorFilter = ColorFilter.tint(tint), filterQuality = FilterQuality.None)
        if (active && !reduce) {
            // A two-pixel glint travels above the selected glyph, on the same logical grid.
            val step = (clock.value * 4).toInt() % 8
            if (step < 4) drawRect(tint.copy(alpha = 0.85f), Offset(left + (16 + step) * u, top + (3 - step / 2) * u), Size(u, u))
        }
    }
}

/** Drop-in Material Icon signatures: brand art/painters and unmapped symbols retain their originals. */
@Composable
fun ThemedIcon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val glyph = if (Bop.halloween) HalloweenGlyph.fromName(imageVector.name) else null
    if (glyph == null) MaterialIcon(imageVector, contentDescription, modifier, tint)
    else {
        val rtl = imageVector.autoMirror && androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
        HalloweenGlyphIcon(glyph, contentDescription, if (rtl) modifier.graphicsLayer { scaleX = -1f } else modifier, tint)
    }
}

@Composable
fun ThemedIcon(painter: Painter, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) =
    MaterialIcon(painter, contentDescription, modifier, tint)

@Composable
fun ThemedIcon(bitmap: ImageBitmap, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) =
    MaterialIcon(bitmap, contentDescription, modifier, tint)
