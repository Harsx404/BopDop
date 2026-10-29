package com.dopdop.bopdop.data

import android.content.Context
import com.dopdop.bopdop.data.model.Source
import com.dopdop.bopdop.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import kotlin.random.Random

data class DailyMix(val id: String, val title: String, val subtitle: String, val tracks: List<Track>) {
    val artworks: List<String> get() = tracks.map { it.artwork }.filter { it.isNotBlank() }.distinct().take(4)
}

/**
 * Up to six mixes rebuilt once a day, one per artist you've been playing most: a few of
 * your own favourites by them, woven through related songs you may not know yet.
 */
object DailyMixes {
    private const val MAX_MIXES = 6
    private const val MIX_SIZE = 30
    private lateinit var file: File
    private val lock = Mutex()
    private val _mixes = MutableStateFlow<List<DailyMix>>(emptyList())
    val mixes: StateFlow<List<DailyMix>> = _mixes.asStateFlow()
    private var builtFor = -1L

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.filesDir, "daily_mixes.json")
        runCatching {
            val o = JSONObject(file.readText())
            builtFor = o.optLong("day", -1)
            _mixes.value = o.optJSONArray("mixes").let { a ->
                (0 until (a?.length() ?: 0)).mapNotNull { i ->
                    val m = a!!.optJSONObject(i) ?: return@mapNotNull null
                    val t = m.optJSONArray("tracks") ?: JSONArray()
                    DailyMix(m.optString("id"), m.optString("title"), m.optString("subtitle"),
                        (0 until t.length()).mapNotNull { k -> t.optJSONObject(k)?.let(Track::fromJson) })
                }
            }
        }
    }

    fun byId(id: String) = _mixes.value.firstOrNull { it.id == id }

    /** Rebuilds when the day has turned (or [force]); cheap no-op otherwise. */
    suspend fun refresh(force: Boolean = false) = lock.withLock {
        val today = LocalDate.now().toEpochDay()
        if (!force && builtFor == today && _mixes.value.isNotEmpty()) return@withLock
        val built = withContext(Dispatchers.IO) { build(today) }
        if (built.isEmpty()) return@withLock
        _mixes.value = built
        builtFor = today
        withContext(Dispatchers.IO) { save(today, built) }
    }

    private suspend fun build(day: Long): List<DailyMix> = coroutineScope {
        val known = knownTracks()
        val seeds = seedArtists(known).take(MAX_MIXES)
        val lib = LibraryStore.state.value
        val taste = Taste.from(PlayStats.state.value, lib.liked)
        val likedIds = lib.liked.map { it.id }.toHashSet()
        seeds.mapIndexed { i, artist ->
            async {
                val rnd = Random(day * 31 + i)
                // Your songs by this artist, minus ones you've been skipping.
                val own = known.values.filter { primaryArtist(it) == artist && !LibraryStore.isDisliked(it) &&
                    (it.id in likedIds || taste.skips(it) < Taste.SKIPS_TO_DROP) }.shuffled(rnd)
                val seed = own.firstOrNull() ?: return@async null
                // Radio from up to three of them, so a mix isn't just one song's neighbours.
                val related = runCatching { MusicRepository.related(seed, own.drop(1).take(2)) }.getOrDefault(emptyList())
                    .filter { it.source != Source.LOCAL }
                // Two new songs for every one you know, so it feels familiar but still finds things.
                val mixed = ArrayList<Track>()
                val a = own.take(10).iterator(); val b = related.shuffled(rnd).iterator()
                while (mixed.size < MIX_SIZE && (a.hasNext() || b.hasNext())) {
                    if (a.hasNext()) mixed += a.next()
                    repeat(2) { if (b.hasNext()) mixed += b.next() }
                }
                val tracks = mixed.distinctBy { it.id }
                if (tracks.size < 8) return@async null
                val others = tracks.map(::primaryArtist).filter { it.isNotBlank() && it != artist }.distinct().take(2)
                DailyMix("d${i + 1}", "Daily Mix ${i + 1}", (listOf(artist) + others).joinToString(", ") + " and more", tracks)
            }
        }.awaitAll().filterNotNull()
            // A new find belongs to one mix only: later mixes drop songs an earlier one already has.
            .let { mixes ->
                val used = HashSet<String>()
                mixes.map { m -> m.copy(tracks = m.tracks.filter { t -> t.id in known || used.add(t.id) }) }
            }
            .filter { it.tracks.size >= 8 }
            .mapIndexed { i, m -> m.copy(id = "d${i + 1}", title = "Daily Mix ${i + 1}") }
    }

    /** Every song the app has seen you play or like, by id. */
    private fun knownTracks(): Map<String, Track> {
        val lib = LibraryStore.state.value
        return (PlayStats.state.value.tracks.values + lib.history + lib.liked)
            .filter { it.source != Source.LOCAL }.associateBy { it.id }
    }

    /** Artists ranked by plays over the last two months, likes counting as two plays. */
    private fun seedArtists(known: Map<String, Track>): List<String> {
        val since = System.currentTimeMillis() - 60 * 86_400_000L
        val score = HashMap<String, Int>()
        PlayStats.state.value.events.filter { it.at >= since }.forEach { e ->
            known[e.id]?.let { t -> primaryArtist(t).takeIf { it.isNotBlank() }?.let { score[it] = (score[it] ?: 0) + 1 } }
        }
        LibraryStore.state.value.liked.forEach { t -> primaryArtist(t).takeIf { it.isNotBlank() }?.let { score[it] = (score[it] ?: 0) + 2 } }
        if (score.isEmpty()) LibraryStore.state.value.history.forEach { t -> primaryArtist(t).takeIf { it.isNotBlank() }?.let { score[it] = (score[it] ?: 0) + 1 } }
        return score.entries.sortedByDescending { it.value }.map { it.key }
    }

    internal fun primaryArtist(t: Track) = t.artist.split(",", "&", " x ").firstOrNull()?.trim().orEmpty()

    private fun save(day: Long, mixes: List<DailyMix>) {
        val o = JSONObject().put("day", day).put("mixes", JSONArray().also { a ->
            mixes.forEach { m ->
                a.put(JSONObject().put("id", m.id).put("title", m.title).put("subtitle", m.subtitle)
                    .put("tracks", JSONArray().also { t -> m.tracks.forEach { t.put(it.toJson()) } }))
            }
        })
        val tmp = File(file.parentFile, "daily_mixes.json.tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(file)
    }
}
