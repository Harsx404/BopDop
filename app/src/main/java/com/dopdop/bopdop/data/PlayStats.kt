package com.dopdop.bopdop.data

import android.content.Context
import com.dopdop.bopdop.data.model.Track
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One counted play: [at] epoch ms. */
data class PlayEvent(val id: String, val at: Long)

data class StatsState(
    val events: List<PlayEvent> = emptyList(),
    val tracks: Map<String, Track> = emptyMap(),
    /** Seconds listened per local day (epoch day). */
    val daily: Map<Long, Int> = emptyMap(),
)

/**
 * Listening history for Replay. Like Spotify, a play only counts once a song
 * has actually been listened to for 30 s (or half of a shorter song); every
 * second played also adds to that day's listening time.
 */
object PlayStats {
    private const val MAX_EVENTS = 50_000
    private lateinit var file: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(StatsState())
    val state: StateFlow<StatsState> = _state.asStateFlow()

    private var sessionId: String? = null
    private var sessionSec = 0
    private var counted = false

    @OptIn(FlowPreview::class)
    @Synchronized
    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.filesDir, "plays.json")
        _state.value = runCatching { read(JSONObject(file.readText())) }.getOrDefault(StatsState())
        _state.drop(1).debounce(2000).onEach { write(it) }.launchIn(scope)
    }

    /** Called once per second of actual playback. */
    @Synchronized
    fun tick(t: Track, seconds: Int = 1) {
        if (!::file.isInitialized) return
        if (t.id != sessionId) { sessionId = t.id; sessionSec = 0; counted = false }
        sessionSec += seconds
        val threshold = if (t.durationSec in 1..59) (t.durationSec / 2).coerceAtLeast(5) else 30
        val day = LocalDate.now().toEpochDay()
        val countNow = !counted && sessionSec >= threshold
        if (countNow) counted = true
        _state.update { s ->
            s.copy(
                daily = s.daily + (day to (s.daily[day] ?: 0) + seconds),
                events = if (countNow) (s.events + PlayEvent(t.id, System.currentTimeMillis())).takeLast(MAX_EVENTS) else s.events,
                tracks = if (countNow) s.tracks + (t.id to t.copy(streamUrl = null)) else s.tracks,
            )
        }
    }

    /** A new song (or the same one again from the top) starts a fresh listening session. */
    @Synchronized
    fun newSession() { sessionId = null }

    fun clear() = _state.update { StatsState() }

    private fun read(o: JSONObject): StatsState {
        val tracks = HashMap<String, Track>()
        o.optJSONObject("tracks")?.let { m -> m.keys().forEach { k -> runCatching { tracks[k] = Track.fromJson(m.getJSONObject(k)) } } }
        val ev = o.optJSONArray("events")
        val events = (0 until (ev?.length() ?: 0)).mapNotNull { i ->
            val a = ev!!.optJSONArray(i) ?: return@mapNotNull null
            PlayEvent(a.optString(0), a.optLong(1)).takeIf { it.id.isNotEmpty() }
        }
        val daily = HashMap<Long, Int>()
        o.optJSONObject("daily")?.let { m -> m.keys().forEach { k -> k.toLongOrNull()?.let { daily[it] = m.optInt(k) } } }
        return StatsState(events, tracks, daily)
    }

    private fun write(s: StatsState) {
        val used = s.events.mapTo(HashSet()) { it.id }
        val o = JSONObject()
            .put("tracks", JSONObject().also { m -> s.tracks.forEach { (k, t) -> if (k in used) m.put(k, t.toJson()) } })
            .put("events", JSONArray().also { a -> s.events.forEach { e -> a.put(JSONArray().put(e.id).put(e.at)) } })
            .put("daily", JSONObject().also { m -> s.daily.forEach { (d, v) -> m.put(d.toString(), v) } })
        val tmp = File(file.parentFile, "plays.json.tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(file)
    }
}

// --- Replay -------------------------------------------------------------------------------

enum class ReplayRange(val label: String, val days: Int?) {
    MONTH("4 weeks", 28), HALF_YEAR("6 months", 182), YEAR("This year", null), ALL("All time", null);
}

data class ArtistStat(val name: String, val artistId: String?, val artwork: String, val plays: Int)
data class TrackStat(val track: Track, val plays: Int)
data class RisingStat(val track: Track, val recent: Int, val before: Int)

data class ReplaySummary(
    val minutes: Int,
    val plays: Int,
    val topTracks: List<TrackStat>,
    val topArtists: List<ArtistStat>,
    val rising: List<RisingStat>,
    /** Minutes per day for the last 30 days, oldest first. */
    val last30: List<Pair<LocalDate, Int>>,
    val busiestHour: Int?,
) {
    val isEmpty get() = plays == 0
}

object Replay {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun summarize(s: StatsState, range: ReplayRange, now: Long = System.currentTimeMillis()): ReplaySummary {
        val today = LocalDate.now(zone)
        val fromDay = when (range) {
            ReplayRange.YEAR -> today.withDayOfYear(1)
            ReplayRange.ALL -> LocalDate.MIN
            else -> today.minusDays(range.days!!.toLong() - 1)
        }
        val fromMs = if (fromDay == LocalDate.MIN) 0L else fromDay.atStartOfDay(zone).toInstant().toEpochMilli()
        val events = s.events.filter { it.at >= fromMs }

        val counts = events.groupingBy { it.id }.eachCount()
        val topTracks = counts.entries.sortedByDescending { it.value }
            .mapNotNull { (id, n) -> s.tracks[id]?.let { TrackStat(it, n) } }.take(50)

        val artistPlays = HashMap<String, Int>()
        val artistTop = HashMap<String, Pair<Track, Int>>()
        counts.forEach { (id, n) ->
            val t = s.tracks[id] ?: return@forEach
            val name = t.artist.split(",", "&", " x ").firstOrNull()?.trim().orEmpty()
            if (name.isEmpty()) return@forEach
            artistPlays[name] = (artistPlays[name] ?: 0) + n
            if ((artistTop[name]?.second ?: 0) < n) artistTop[name] = t to n
        }
        val topArtists = artistPlays.entries.sortedByDescending { it.value }.take(12).map { (name, n) ->
            val t = artistTop[name]!!.first
            ArtistStat(name, t.artistId, t.artwork, n)
        }

        // Momentum: last 7 days vs the 7 before.
        val week = 7 * 86_400_000L
        val recent = s.events.filter { it.at >= now - week }.groupingBy { it.id }.eachCount()
        val before = s.events.filter { it.at in (now - 2 * week) until (now - week) }.groupingBy { it.id }.eachCount()
        val rising = recent.entries
            .map { (id, r) -> Triple(id, r, before[id] ?: 0) }
            .filter { (_, r, b) -> r >= 2 && r > b }
            .sortedByDescending { (_, r, b) -> (r - b) * 2 + r }
            .mapNotNull { (id, r, b) -> s.tracks[id]?.let { RisingStat(it, r, b) } }
            .take(8)

        val last30 = (29 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            d to ((s.daily[d.toEpochDay()] ?: 0) + 30) / 60
        }
        val minutes = s.daily.entries.filter { LocalDate.ofEpochDay(it.key) >= fromDay }.sumOf { it.value } / 60
        val busiestHour = events.groupingBy { Instant.ofEpochMilli(it.at).atZone(zone).hour }.eachCount().maxByOrNull { it.value }?.key

        return ReplaySummary(minutes, events.size, topTracks, topArtists, rising, last30, busiestHour)
    }
}
