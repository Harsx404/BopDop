package com.dopdop.bopdop.data

import com.dopdop.bopdop.data.model.SongSet
import android.content.Context
import com.dopdop.bopdop.data.model.Card
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.model.cardsToJson
import com.dopdop.bopdop.data.model.toCards
import com.dopdop.bopdop.data.model.toJsonArray
import com.dopdop.bopdop.data.model.toTracks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class GlassLevel(val label: String) { CLEAR("Clear"), REGULAR("Regular"), FROSTED("Frosted") }

/**
 * Streaming audio quality. [maxKbps] caps YouTube formats; [saavn] is the JioSaavn file suffix.
 * High takes the best there is (and JioSaavn's 320 kbps match); the others save data.
 */
enum class AudioQuality(val label: String, val maxKbps: Int, val saavn: String) {
    HIGH("High", Int.MAX_VALUE, "_320"), NORMAL("Normal", 140, "_160"), SAVER("Data saver", 80, "_96"),
}

/** Light or dark look; [SYSTEM] follows the phone. */
enum class ThemeMode(val label: String) { SYSTEM("Auto"), LIGHT("Light"), DARK("Dark") }

/** Highest music-video picture to stream; [AUTO] follows the connection. */
enum class VideoQuality(val label: String, val maxHeight: Int) {
    AUTO("Auto", 0), UHD("4K", 2160), QHD("1440p", 1440), FHD("1080p", 1080), HD("720p", 720), SD("480p", 480),
}

data class Settings(
    /** Play YouTube Music tracks from JioSaavn's 320 kbps copy when the same recording exists there. */
    val hiRes: Boolean = true,
    /** Keep the music going with related tracks when the queue ends. */
    val autoplay: Boolean = true,
    val glass: GlassLevel = GlassLevel.REGULAR,
    val languages: String = "hindi,english",
    /** Play cache size; 0 = unlimited. */
    val cacheMb: Int = 1024,
    /** Keep the play cache where Android never clears it (app storage, not cache). Applies on next launch. */
    val keepCache: Boolean = false,
    /** Lyrics page: the cover slowly swirls behind the words. */
    val lyricsMotion: Boolean = true,
    /** Spotify Canvas loops behind the cover (signed in to Spotify); on mobile data only when allowed. */
    val canvas: Boolean = true,
    val canvasOnData: Boolean = false,
    /** Add-on servers (base URLs), asked in order for lossless copies when [lossless] is on. */
    val addons: List<String> = emptyList(),
    val lossless: Boolean = false,
    /** Ask add-ons for Dolby Atmos mixes when this phone can decode them. */
    val atmos: Boolean = true,
    /** USB DACs: hand the DAC the file's own sample rate and bit depth, untouched (Android 14+). */
    val bitPerfect: Boolean = false,
    /** Keep your own Spotify playlists (and Liked Songs) copied into the library, synced on launch. */
    val spotifyAutoImport: Boolean = true,
    val reduceMotion: Boolean = false,
    val showSaavnHome: Boolean = true,
    val videoQuality: VideoQuality = VideoQuality.AUTO,
    /** Trim gaps of silence inside and between tracks. */
    val skipSilence: Boolean = false,
    /** Level every track to the same loudness. */
    val normalize: Boolean = false,
    /** Preferred display refresh rate in Hz; 0 leaves it to the system. */
    val refreshRate: Int = 0,
    /** Only show music from this folder (MediaStore relative path); null = every folder. */
    val localFolder: String? = null,
    /** Hide short clips, voice notes, recordings and system sounds from local music. */
    val filterNonMusic: Boolean = true,
    /** Decode to 32-bit float instead of 16-bit PCM. */
    val floatOutput: Boolean = false,
    /** Automix: seconds the outgoing and incoming songs overlap; 0 = off. */
    val crossfadeSec: Int = 0,
    /** Show the line being sung in the media notification / lock screen. */
    val lockScreenLyrics: Boolean = false,
    val seekStyle: SeekStyle = SeekStyle.WAVE,
    val mascot: Mascot = Mascot.KITTY,
    val festiveLevel: FestiveLevel = FestiveLevel.FULL,
    val festivePick: FestivePick = FestivePick.AUTO,
    /** An update the user waved away; the next newer one is offered again. */
    val skippedUpdate: String = "",
    /**
     * Now-playing island around the camera cutout, inside the app. Unset means the default: on,
     * except on phones that already have their own island (see NativeIsland).
     */
    val islandInApp: Boolean? = null,
    /** The same island over other apps (needs "display over other apps"). */
    val islandOverlay: Boolean = false,
    /** Android 16 Live Update: what's playing in the phone's own island / status chip. */
    val liveUpdate: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Audio quality on Wi-Fi and other unmetered networks. */
    val audioWifi: AudioQuality = AudioQuality.HIGH,
    /** Audio quality on mobile data (metered networks). */
    val audioMobile: AudioQuality = AudioQuality.HIGH,
    /** Playback speed, 0.5x to 2x; pitch stays the same. */
    val playbackSpeed: Float = 1f,
    /** Shuffle and repeat as last left, so they survive restarts. [repeat] is a Media3 REPEAT_MODE_*. */
    val shuffle: Boolean = false,
    val repeat: Int = 0,
    /** Library playlists and saved albums as a list instead of a grid of covers. */
    val libraryList: Boolean = false,
    /** ListenBrainz user token (scrobbling is on while it's set) and the account it belongs to. */
    val listenBrainzToken: String = "",
    val listenBrainzUser: String = "",
    /** Last.fm (or Libre.fm) session from the web approval; key/secret only when the user pasted their own. */
    val lastFmSession: String = "",
    val lastFmUser: String = "",
    val lastFmKey: String = "",
    val lastFmSecret: String = "",
    val lastFmLibre: Boolean = false,
    val eq: com.dopdop.bopdop.playback.EqState = com.dopdop.bopdop.playback.EqState(),
) {
    fun toJson() = JSONObject().put("hiRes", hiRes).put("autoplay", autoplay).put("glass", glass.name)
        .put("languages", languages).put("cacheMb", cacheMb).put("keepCache", keepCache).put("lyricsMotion", lyricsMotion).put("canvas", canvas).put("canvasOnData", canvasOnData)
            .put("addons", JSONArray(addons)).put("lossless", lossless).put("atmos", atmos).put("bitPerfect", bitPerfect).put("spotifyAutoImport", spotifyAutoImport).put("reduceMotion", reduceMotion).put("showSaavnHome", showSaavnHome)
        .put("videoQuality", videoQuality.name).put("skipSilence", skipSilence).put("normalize", normalize)
        .put("refreshRate", refreshRate).put("localFolder", localFolder ?: "").put("filterNonMusic", filterNonMusic)
        .put("floatOutput", floatOutput).put("crossfadeSec", crossfadeSec).put("lockScreenLyrics", lockScreenLyrics)
        .put("seekStyle", seekStyle.name).put("mascot", mascot.name)
        .put("festiveLevel", festiveLevel.name).put("festivePick", festivePick.name).put("skippedUpdate", skippedUpdate)
        .put("islandOverlay", islandOverlay).put("liveUpdate", liveUpdate).put("themeMode", themeMode.name)
        .put("audioWifi", audioWifi.name).put("audioMobile", audioMobile.name).put("playbackSpeed", playbackSpeed.toDouble())
        .put("shuffle", shuffle).put("repeat", repeat).put("libraryList", libraryList).put("listenBrainzToken", listenBrainzToken).put("listenBrainzUser", listenBrainzUser)
            .put("lastFmSession", lastFmSession).put("lastFmUser", lastFmUser).put("lastFmKey", lastFmKey).put("lastFmSecret", lastFmSecret).put("lastFmLibre", lastFmLibre).apply { islandInApp?.let { put("islandInApp", it) } }
        .put("eq", eq.toJson())

    companion object {
        fun fromJson(o: JSONObject?) = if (o == null) Settings() else Settings(
            hiRes = o.optBoolean("hiRes", true),
            autoplay = o.optBoolean("autoplay", true),
            glass = runCatching { GlassLevel.valueOf(o.optString("glass")) }.getOrDefault(GlassLevel.REGULAR),
            languages = o.optString("languages", "hindi,english"),
            cacheMb = o.optInt("cacheMb", 1024),
            keepCache = o.optBoolean("keepCache", false),
            lyricsMotion = o.optBoolean("lyricsMotion", true),
            canvas = o.optBoolean("canvas", true),
            canvasOnData = o.optBoolean("canvasOnData", false),
            addons = o.optJSONArray("addons")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty(),
            lossless = o.optBoolean("lossless", false),
            atmos = o.optBoolean("atmos", true),
            bitPerfect = o.optBoolean("bitPerfect", false),
            spotifyAutoImport = o.optBoolean("spotifyAutoImport", true),
            reduceMotion = o.optBoolean("reduceMotion", false),
            showSaavnHome = o.optBoolean("showSaavnHome", true),
            videoQuality = runCatching { VideoQuality.valueOf(o.optString("videoQuality")) }.getOrDefault(VideoQuality.AUTO),
            skipSilence = o.optBoolean("skipSilence", false),
            normalize = o.optBoolean("normalize", false),
            refreshRate = o.optInt("refreshRate", 0),
            localFolder = o.optString("localFolder").ifEmpty { null },
            filterNonMusic = o.optBoolean("filterNonMusic", true),
            floatOutput = o.optBoolean("floatOutput", false),
            crossfadeSec = o.optInt("crossfadeSec", 0),
            lockScreenLyrics = o.optBoolean("lockScreenLyrics", false),
            seekStyle = runCatching { SeekStyle.valueOf(o.optString("seekStyle")) }.getOrDefault(SeekStyle.WAVE),
            // The old pets all became the kitty; "no pet" stays no mascot.
            mascot = runCatching { Mascot.valueOf(o.optString("mascot")) }.getOrNull()
                ?: if (o.optString("seekPet") == "NONE") Mascot.NONE else Mascot.KITTY,
            // "Pet only" dressed up the old pets; with them gone it showed nothing, so it reads as Off.
            festiveLevel = if (o.optString("festiveLevel") == "LIMITED") FestiveLevel.OFF
                else runCatching { FestiveLevel.valueOf(o.optString("festiveLevel")) }.getOrDefault(FestiveLevel.FULL),
            festivePick = runCatching { FestivePick.valueOf(o.optString("festivePick")) }.getOrDefault(FestivePick.AUTO),
            skippedUpdate = o.optString("skippedUpdate"),
            islandInApp = if (o.has("islandInApp")) o.optBoolean("islandInApp") else null,
            islandOverlay = o.optBoolean("islandOverlay", false),
            liveUpdate = o.optBoolean("liveUpdate", true),
            themeMode = runCatching { ThemeMode.valueOf(o.optString("themeMode")) }.getOrDefault(ThemeMode.SYSTEM),
            audioWifi = runCatching { AudioQuality.valueOf(o.optString("audioWifi")) }.getOrDefault(AudioQuality.HIGH),
            audioMobile = runCatching { AudioQuality.valueOf(o.optString("audioMobile")) }.getOrDefault(AudioQuality.HIGH),
            playbackSpeed = o.optDouble("playbackSpeed", 1.0).toFloat().coerceIn(0.5f, 2f),
            shuffle = o.optBoolean("shuffle", false),
            repeat = o.optInt("repeat", 0).coerceIn(0, 2),
            libraryList = o.optBoolean("libraryList", false),
            listenBrainzToken = o.optString("listenBrainzToken"),
            listenBrainzUser = o.optString("listenBrainzUser"),
            lastFmSession = o.optString("lastFmSession"),
            lastFmUser = o.optString("lastFmUser"),
            lastFmKey = o.optString("lastFmKey"),
            lastFmSecret = o.optString("lastFmSecret"),
            lastFmLibre = o.optBoolean("lastFmLibre"),
            eq = com.dopdop.bopdop.playback.EqState.fromJson(o.optJSONObject("eq")),
        )
    }
}

/** How a playlist's songs are listed (and played). [CUSTOM] is the order they were added or arranged in. */
enum class PlaylistSort(val label: String) {
    CUSTOM("Custom order"), RECENT("Recently added"), TITLE("Title"), ARTIST("Artist"), LONGEST("Longest first");

    fun apply(tracks: List<Track>): List<Track> = when (this) {
        CUSTOM -> tracks
        RECENT -> tracks.asReversed()
        TITLE -> tracks.sortedBy { it.title.lowercase() }
        ARTIST -> tracks.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
        LONGEST -> tracks.sortedByDescending { it.durationSec }
    }
}

data class UserPlaylist(
    val id: String, val name: String, val createdAt: Long, val tracks: List<Track>,
    /** Pinned playlists come first in Library and the add-to-playlist list. */
    val pinned: Boolean = false,
    val sort: PlaylistSort = PlaylistSort.CUSTOM,
) {
    val cardId get() = "local:$id"
    val artwork get() = tracks.firstOrNull()?.artwork.orEmpty()
    val sorted get() = sort.apply(tracks)
}

/** Pinned first, otherwise as they are. */
fun List<UserPlaylist>.pinnedFirst() = sortedByDescending { it.pinned }

data class LibraryState(
    val liked: List<Track> = emptyList(),
    val playlists: List<UserPlaylist> = emptyList(),
    val saved: List<Card> = emptyList(),
    val history: List<Track> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val settings: Settings = Settings(),
    /** Marked "Not for me": skipped, and kept out of radio, autoplay and mixes. */
    val disliked: List<Track> = emptyList(),
) {
    val likedIds: Set<String> by lazy { liked.mapTo(HashSet()) { it.id } }
    val savedIds: Set<String> by lazy { saved.mapTo(HashSet()) { it.id } }
    private val dislikedSet: SongSet by lazy { SongSet(disliked) }

    /** The same recording from any source counts. */
    fun isDisliked(t: Track) = t in dislikedSet
}

/** Single JSON file in app storage; writes are debounced off the main thread. */
object LibraryStore {
    private lateinit var file: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()
    val settings get() = _state.value.settings

    @OptIn(FlowPreview::class)
    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.filesDir, "library.json")
        _state.value = runCatching { read(JSONObject(file.readText())) }.getOrDefault(LibraryState())
        com.dopdop.bopdop.playback.AudioFx.state.value = _state.value.settings.eq
        _state.drop(1).debounce(400).onEach { write(it) }.launchIn(scope)
    }

    private fun read(o: JSONObject) = LibraryState(
        liked = o.optJSONArray("liked").toTracks(),
        playlists = o.optJSONArray("playlists")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val p = a.optJSONObject(i) ?: return@mapNotNull null
                UserPlaylist(
                    p.optString("id"), p.optString("name"), p.optLong("createdAt"), p.optJSONArray("tracks").toTracks(),
                    pinned = p.optBoolean("pinned"),
                    sort = runCatching { PlaylistSort.valueOf(p.optString("sort")) }.getOrDefault(PlaylistSort.CUSTOM),
                )
            }
        }.orEmpty(),
        saved = o.optJSONArray("saved").toCards(),
        history = o.optJSONArray("history").toTracks(),
        recentSearches = o.optJSONArray("recentSearches")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(),
        disliked = o.optJSONArray("disliked").toTracks(),
        settings = Settings.fromJson(o.optJSONObject("settings")),
    )

    /** The whole library as stored, for backups. */
    fun toJson(): JSONObject = toJson(_state.value)

    /** Replace the library with a backup's; the ListenBrainz sign-in on this phone is kept if the backup has none. */
    fun restore(o: JSONObject) {
        val next = read(o)
        val cur = _state.value.settings
        // Sign-ins never travel in backups: keep this phone's.
        _state.value = next.copy(settings = next.settings.copy(
            listenBrainzToken = cur.listenBrainzToken, listenBrainzUser = cur.listenBrainzUser,
            lastFmSession = cur.lastFmSession, lastFmUser = cur.lastFmUser, lastFmKey = cur.lastFmKey, lastFmSecret = cur.lastFmSecret,
        ))
        com.dopdop.bopdop.playback.AudioFx.state.value = _state.value.settings.eq
    }

    private fun write(s: LibraryState) {
        val tmp = File(file.parentFile, "library.json.tmp")
        tmp.writeText(toJson(s).toString())
        tmp.renameTo(file)
    }

    private fun toJson(s: LibraryState): JSONObject {
        return JSONObject()
            .put("liked", s.liked.toJsonArray())
            .put("playlists", JSONArray().also { a ->
                s.playlists.forEach { p -> a.put(JSONObject().put("id", p.id).put("name", p.name).put("createdAt", p.createdAt).put("tracks", p.tracks.toJsonArray())
                    .put("pinned", p.pinned).put("sort", p.sort.name)) }
            })
            .put("saved", s.saved.cardsToJson())
            .put("history", s.history.toJsonArray())
            .put("recentSearches", JSONArray(s.recentSearches))
            .put("disliked", s.disliked.toJsonArray())
            .put("settings", s.settings.toJson())
    }

    fun isLiked(id: String) = id in _state.value.likedIds

    fun toggleLike(t: Track): Boolean {
        var nowLiked = false
        _state.update { s ->
            if (t.id in s.likedIds) s.copy(liked = s.liked.filterNot { it.id == t.id })
            else { nowLiked = true; s.copy(liked = listOf(t) + s.liked, disliked = s.disliked.filterNot { it in SongSet(listOf(t)) }) }
        }
        return nowLiked
    }

    fun isDisliked(t: Track) = _state.value.isDisliked(t)

    /** "Not for me" on or off; returns true when the song is now marked. Marking it also unlikes it. */
    fun toggleDislike(t: Track): Boolean {
        var now = false
        _state.update { s ->
            if (s.isDisliked(t)) s.copy(disliked = s.disliked.filterNot { it in SongSet(listOf(t)) })
            else { now = true; s.copy(disliked = listOf(t) + s.disliked, liked = s.liked.filterNot { it.id == t.id }) }
        }
        return now
    }

    fun toggleSaved(c: Card): Boolean {
        var nowSaved = false
        _state.update { s ->
            if (c.id in s.savedIds) s.copy(saved = s.saved.filterNot { it.id == c.id })
            else { nowSaved = true; s.copy(saved = listOf(c) + s.saved) }
        }
        return nowSaved
    }

    fun recordPlay(t: Track) = _state.update { s ->
        s.copy(history = (listOf(t) + s.history.filterNot { it.id == t.id }).take(300))
    }

    fun clearHistory() = _state.update { it.copy(history = emptyList()) }

    fun addRecentSearch(q: String) {
        val v = q.trim()
        if (v.isEmpty()) return
        _state.update { s -> s.copy(recentSearches = (listOf(v) + s.recentSearches.filterNot { it.equals(v, true) }).take(12)) }
    }

    fun clearRecentSearches() = _state.update { it.copy(recentSearches = emptyList()) }

    fun createPlaylist(name: String, tracks: List<Track> = emptyList()): UserPlaylist {
        val p = UserPlaylist(UUID.randomUUID().toString().take(12), name.trim().ifEmpty { "My playlist" }, System.currentTimeMillis(), tracks)
        _state.update { it.copy(playlists = listOf(p) + it.playlists) }
        return p
    }

    fun addToPlaylist(id: String, tracks: List<Track>) = addToPlaylists(setOf(id), tracks)

    /** Add [tracks] to every playlist in [ids], skipping songs a playlist already has. */
    fun addToPlaylists(ids: Set<String>, tracks: List<Track>) = _state.update { s ->
        s.copy(playlists = s.playlists.map { p ->
            if (p.id !in ids) p else p.copy(tracks = p.tracks + tracks.filter { t -> p.tracks.none { it.id == t.id } }.distinctBy { it.id })
        })
    }

    /** Move a song within a playlist's own (custom) order. */
    fun movePlaylistTrack(id: String, from: Int, to: Int) = _state.update { s ->
        s.copy(playlists = s.playlists.map { p ->
            if (p.id != id || from == to || from !in p.tracks.indices || to !in p.tracks.indices) p
            else p.copy(tracks = p.tracks.toMutableList().apply { add(to, removeAt(from)) })
        })
    }

    fun setPinned(id: String, pinned: Boolean) = _state.update { s ->
        s.copy(playlists = s.playlists.map { if (it.id == id) it.copy(pinned = pinned) else it })
    }

    fun setSort(id: String, sort: PlaylistSort) = _state.update { s ->
        s.copy(playlists = s.playlists.map { if (it.id == id) it.copy(sort = sort) else it })
    }

    fun removeFromPlaylist(id: String, trackId: String) = _state.update { s ->
        s.copy(playlists = s.playlists.map { p -> if (p.id != id) p else p.copy(tracks = p.tracks.filterNot { it.id == trackId }) })
    }

    fun renamePlaylist(id: String, name: String) = _state.update { s ->
        s.copy(playlists = s.playlists.map { if (it.id == id) it.copy(name = name) else it })
    }

    fun deletePlaylist(id: String) = _state.update { s -> s.copy(playlists = s.playlists.filterNot { it.id == id }) }

    fun updateSettings(block: (Settings) -> Settings) = _state.update { it.copy(settings = block(it.settings)) }
}
