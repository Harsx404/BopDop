package com.dopdop.bopdop.ui.components

import androidx.compose.foundation.layout.size
import com.dopdop.bopdop.ui.festive.ThemedIcon as Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

/** A service BopDop connects to, and where its real logo comes from. */
enum class Brand(val label: String, val domain: String, val simpleIcon: String? = null, val color: String? = null) {
    SPOTIFY("Spotify", "spotify.com", "spotify", "1ED760"),
    YOUTUBE_MUSIC("YouTube Music", "music.youtube.com", "youtubemusic", "FF0000"),
    LASTFM("Last.fm", "last.fm"),
    LIBREFM("Libre.fm", "libre.fm"),
    LISTENBRAINZ("ListenBrainz", "listenbrainz.org"),
    JIOSAAVN("JioSaavn", "jiosaavn.com");

    /**
     * Free logo sources, best first: Simple Icons' SVG (crisp at any size, in the brand's colour)
     * when it has the brand, then Google's favicon service, which has every site.
     */
    val urls: List<String> get() = listOfNotNull(
        simpleIcon?.let { "https://cdn.simpleicons.org/$it" + (color?.let { c -> "/$c" } ?: "") },
        "https://www.google.com/s2/favicons?domain=$domain&sz=128",
    )
}

/** The brand's logo from the free logo providers; [fallback] while it loads or when offline. */
@Composable
fun BrandLogo(brand: Brand, size: Dp, fallback: ImageVector, modifier: Modifier = Modifier, fallbackTint: Color = Color.Unspecified) {
    val ctx = LocalContext.current
    var source by remember(brand) { mutableIntStateOf(0) }
    var loaded by remember(brand) { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.foundation.layout.Box(modifier.size(size), contentAlignment = androidx.compose.ui.Alignment.Center) {
        if (!loaded) Icon(fallback, null, tint = fallbackTint, modifier = Modifier.size(size))
        val url = brand.urls.getOrNull(source) ?: return@Box
        AsyncImage(
            model = ImageRequest.Builder(ctx).data(url).build(),
            contentDescription = brand.label,
            modifier = Modifier.size(size),
            onSuccess = { loaded = true },
            onError = { source++ },
        )
    }
}
