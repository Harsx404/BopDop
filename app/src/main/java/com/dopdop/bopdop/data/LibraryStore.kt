package com.dopdop.bopdop.data

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
    val cacheMb: Int = 1024,
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
    val seekPet: SeekPet = SeekPet.NONE,
    val festiveLevel: FestiveLevel = FestiveLevel.FULL,
    val festivePick: FestivePick = FestivePick.AUTO,
    val eq: com.dopdop.bopdop.playback.EqState = com.dopdop.bopdop.playback.EqState(),
) {
    fun toJson() = JSONObject().put("hiRes", hiRes).put("autoplay", autoplay).put("glass", glass.name)
        .put("languages", languages).put("cacheMb", cacheMb).put("reduceMotion", reduceMotion).put("showSaavnHome", showSaavnHome)
        .put("videoQuality", videoQuality.name).put("skipSilence", skipSilence).put("normalize", normalize)
        .put("refreshRate", refreshRate).put("localFolder", localFolder ?: "").put("filterNonMusic", filterNonMusic)
        .put("floatOutput", floatOutput).put("crossfadeSec", crossfadeSec).put("lockScreenLyrics", lockScreenLyrics)
        .put("seekStyle", seekStyle.name).put("seekPet", seekPet.name)
        .put("festiveLevel", festiveLevel.name).put("festivePick", festivePick.name)
        .put("eq", eq.toJson())

    companion object {
        fun fromJson(o: JSONObject?) = if (o == null) Settings() else Settings(
            hiRes = o.optBoolean("hiRes", true),
            autoplay = o.optBoolean("autoplay", true),
            glass = runCatching { GlassLevel.valueOf(o.optString("glass")) }.getOrDefault(GlassLevel.REGULAR),
            languages = o.optString("languages", "hindi,english"),
            cacheMb = o.optInt("cacheMb", 1024),
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
            seekPet = runCatching { SeekPet.valueOf(o.optString("seekPet")) }.getOrDefault(SeekPet.NONE),
            festiveLevel = runCatching { FestiveLevel.valueOf(o.optString("festiveLevel")) }.getOrDefault(FestiveLevel.FULL),
            festivePick = runCatching { FestivePick.valueOf(o.optString("festivePick")) }.getOrDefault(FestivePick.AUTO),
            eq = com.dopdop.bopdop.playback.EqState.fromJson(o.optJSONObject("eq")),
        )
    }
}

data class UserPlaylist(val id: String, val name: String, val createdAt: Long, val tracks: List<Track>) {
    val cardId get() = "local:$id"
    val artwork get() = tracks.firstOrNull()?.artwork.orEmpty()
}

data class LibraryState(
    val liked: List<Track> = emptyList(),
    val playlists: List<UserPlaylist> = emptyList(),
    val saved: List<Card> = emptyList(),
    val history: List<Track> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val settings: Settings = Settings(),
) {
    val likedIds: Set<String> by lazy { liked.mapTo(HashSet()) { it.id } }
    val savedIds: Set<String> by lazy { saved.mapTo(HashSet()) { it.id } }
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
                UserPlaylist(p.optString("id"), p.optString("name"), p.optLong("createdAt"), p.optJSONArray("tracks").toTracks())
            }
        }.orEmpty(),
        saved = o.optJSONArray("saved").toCards(),
        history = o.optJSONArray("history").toTracks(),
        recentSearches = o.optJSONArray("recentSearches")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(),
        settings = Settings.fromJson(o.optJSONObject("settings")),
    )

    private fun write(s: LibraryState) {
        val o = JSONObject()
            .put("liked", s.liked.toJsonArray())
            .put("playlists", JSONArray().also { a ->
                s.playlists.forEach { p -> a.put(JSONObject().put("id", p.id).put("name", p.name).put("createdAt", p.createdAt).put("tracks", p.tracks.toJsonArray())) }
            })
            .put("saved", s.saved.cardsToJson())
            .put("history", s.history.toJsonArray())
            .put("recentSearches", JSONArray(s.recentSearches))
            .put("settings", s.settings.toJson())
        val tmp = File(file.parentFile, "library.json.tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(file)
    }

    fun isLiked(id: String) = id in _state.value.likedIds

    fun toggleLike(t: Track): Boolean {
        var nowLiked = false
        _state.update { s ->
            if (t.id in s.likedIds) s.copy(liked = s.liked.filterNot { it.id == t.id })
            else { nowLiked = true; s.copy(liked = listOf(t) + s.liked) }
        }
        return nowLiked
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

    fun addToPlaylist(id: String, tracks: List<Track>) = _state.update { s ->
        s.copy(playlists = s.playlists.map { p ->
            if (p.id != id) p else p.copy(tracks = p.tracks + tracks.filter { t -> p.tracks.none { it.id == t.id } })
        })
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
