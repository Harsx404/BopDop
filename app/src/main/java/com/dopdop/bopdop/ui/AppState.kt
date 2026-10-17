package com.dopdop.bopdop.ui

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController
import com.dopdop.bopdop.data.MusicRepository
import com.dopdop.bopdop.data.model.Card
import com.dopdop.bopdop.data.model.CardKind
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.share.ShareCodec
import com.dopdop.bopdop.data.share.SharePayload
import com.dopdop.bopdop.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed interface Sheet {
    data class TrackOptions(val track: Track, val playlistId: String? = null) : Sheet
    data class CardOptions(val card: Card) : Sheet
    data class AddToPlaylist(val tracks: List<Track>) : Sheet
    data class NewPlaylist(val tracks: List<Track>) : Sheet
    data class RenamePlaylist(val id: String, val name: String) : Sheet
    data object SleepTimer : Sheet
    data object AudioPipeline : Sheet
    data object ImportPlaylist : Sheet
}

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val EXPLORE = "explore"
    const val TOGETHER = "together?code={code}"
    const val WRAPPED = "wrapped"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val COLLECTION = "collection?id={id}&kind={kind}&title={title}&sub={sub}&art={art}"
    const val ARTIST = "artist?id={id}&name={name}&art={art}"
    const val LOCAL = "local?id={id}"
    const val MOOD = "mood?title={title}&params={params}"
    const val SEE_ALL = "seeall?key={key}"
    const val REPLAY = "replay"
    const val SIGN_IN = "signin"
    const val SHARE_QR = "shareqr?key={key}"
    const val IMPORT = "import?key={key}"
    const val SCAN = "scan"
    const val EQ = "eq"
    val tabs = listOf(HOME, EXPLORE, LIBRARY)
}

/** A thing to put on a share code, with what the card shows about it. */
data class ShareRequest(val payload: SharePayload, val title: String, val subtitle: String, val artwork: String)

/** Lists handed to "See all" without serialising them into a route. */
object SeeAllHolder {
    private val map = HashMap<String, Pair<String, Any>>()
    fun put(title: String, items: Any): String = (title + items.hashCode()).also { map[it] = title to items }
    fun get(key: String) = map[key]
}

@Stable
class AppState(val nav: NavHostController, val scope: CoroutineScope) {
    var sheet by mutableStateOf<Sheet?>(null)
    /** The song open in the share studio, if any. */
    var shareTrack by mutableStateOf<Track?>(null)
    var toast by mutableStateOf<Pair<Long, String>?>(null)
        private set
    /** 0 = mini player, 1 = full Now Playing. */
    val player = Animatable(0f)
    var nowPlayingPage by mutableStateOf(NowPlayingPage.ART)
    /** Where the mini player's cover sits (root coords) — the player genies into it on close. */
    var miniArt by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)

    fun toast(msg: String) { toast = System.nanoTime() to msg }

    fun expandPlayer(page: NowPlayingPage = NowPlayingPage.ART) {
        nowPlayingPage = page
        scope.launch { player.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 380f)) }
    }

    fun collapsePlayer() { scope.launch { player.animateTo(0f, spring(dampingRatio = 1f, stiffness = 280f)) } }

    private fun enc(s: String) = Uri.encode(s)

    fun open(card: Card) {
        when (card.kind) {
            CardKind.ARTIST -> openArtist(card.id, card.title, card.artwork)
            CardKind.ALBUM, CardKind.PLAYLIST -> {
                if (card.id.startsWith("local:")) nav.navigate("local?id=${enc(card.rawId)}")
                else nav.navigate("collection?id=${enc(card.id)}&kind=${card.kind.name}&title=${enc(card.title)}&sub=${enc(card.subtitle)}&art=${enc(card.artwork)}")
            }
            CardKind.STATION, CardKind.MOOD -> playStation(card)
        }
    }

    fun openArtist(id: String, name: String = "", art: String = "") =
        nav.navigate("artist?id=${enc(id)}&name=${enc(name)}&art=${enc(art)}")

    fun openAlbum(id: String, title: String = "", art: String = "") =
        nav.navigate("collection?id=${enc(id)}&kind=ALBUM&title=${enc(title)}&sub=&art=${enc(art)}")

    fun openLocal(id: String) = nav.navigate("local?id=${enc(id)}")
    fun openMood(title: String, params: String) = nav.navigate("mood?title=${enc(title)}&params=${enc(params)}")
    fun seeAll(title: String, items: Any) = nav.navigate("seeall?key=${enc(SeeAllHolder.put(title, items))}")

    fun shareQr(req: ShareRequest) = nav.navigate("shareqr?key=${enc(SeeAllHolder.put("qr", req))}")

    fun openScanner() = nav.navigate(Routes.SCAN) { launchSingleTop = true }

    /** A scanned code or opened `bopdop://share/…` link. */
    fun openShared(text: String) {
        Regex("^bopdop://together/([A-Za-z0-9]{4,8})").find(text)?.let { nav.navigate("together?code=${enc(it.groupValues[1])}"); return }
        when (val p = ShareCodec.decode(text)) {
            is SharePayload.Playlist -> nav.navigate("import?key=${enc(SeeAllHolder.put("import", p))}")
            is SharePayload.Collection -> open(p.card)
            null -> toast("That isn’t a BopDop code")
        }
    }

    fun playStation(card: Card) {
        toast("Tuning in to ${card.title}…")
        scope.launch {
            val tracks = runCatching { MusicRepository.station(card) }.getOrDefault(emptyList())
            if (tracks.isEmpty()) toast("Station is quiet right now") else PlayerConnection.play(tracks, 0, card.title)
        }
    }

    /** Play button on an artist / station card: artist → their top songs, station → the station. */
    fun playStationOrArtist(card: Card) {
        if (card.kind != CardKind.ARTIST) { if (card.kind == CardKind.STATION || card.kind == CardKind.MOOD) playStation(card) else open(card); return }
        scope.launch {
            val page = runCatching { MusicRepository.artist(card.id) }.getOrNull()
            if (page == null || page.topSongs.isEmpty()) toast("Couldn’t load ${card.title}")
            else PlayerConnection.play(page.topSongs, 0, page.name)
        }
    }

    fun goTab(route: String) {
        nav.navigate(route) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}

enum class NowPlayingPage { ART, LYRICS, QUEUE }

val LocalApp = staticCompositionLocalOf<AppState> { error("AppState not provided") }
