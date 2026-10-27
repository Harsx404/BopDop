package com.dopdop.bopdop.data

import android.content.Context
import android.util.LruCache
import com.dopdop.bopdop.data.model.Card
import com.dopdop.bopdop.data.model.CardKind
import com.dopdop.bopdop.data.model.Collection
import com.dopdop.bopdop.data.model.HomeFeed
import com.dopdop.bopdop.data.model.Shelf
import com.dopdop.bopdop.data.model.ShelfStyle
import com.dopdop.bopdop.data.model.toJsonArray
import com.dopdop.bopdop.data.model.toTracks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Album / playlist pages and mood-tile covers kept on disk, so they open instantly, survive a
 * restart and still show offline. Fresh copies replace them after [COLLECTION_TTL]; when the
 * network fails, an older copy is used rather than an error.
 */
object CatalogCache {
    private const val COLLECTION_TTL = 12 * 3600_000L
    private const val MOOD_ART_TTL = 7 * 24 * 3600_000L
    private const val MAX_FILES = 400

    private var dir: File? = null
    private val memory = LruCache<String, String>(80)

    fun init(context: Context) {
        if (dir != null) return
        dir = File(context.cacheDir, "catalog").apply { mkdirs() }
    }

    // --- albums & playlists ----------------------------------------------------------------

    suspend fun collection(key: String, load: suspend () -> Collection): Collection {
        val cached = read("col:$key")
        cached?.takeIf { it.fresh(COLLECTION_TTL) }?.let { fromJson(it.json)?.let { c -> return c } }
        return try {
            load().also { if (it.tracks.isNotEmpty()) write("col:$key", toJson(it).toString()) }
        } catch (e: Exception) {
            // Offline or the catalogue hiccuped: an older copy beats an error page.
            cached?.let { fromJson(it.json) } ?: throw e
        }
    }

    private fun toJson(c: Collection) = JSONObject().put("id", c.id).put("kind", c.kind.name).put("title", c.title)
        .put("subtitle", c.subtitle).put("artwork", c.artwork).put("year", c.year).put("tracks", c.tracks.toJsonArray())

    private fun fromJson(text: String): Collection? = runCatching {
        val o = JSONObject(text)
        Collection(
            o.getString("id"), runCatching { CardKind.valueOf(o.optString("kind")) }.getOrDefault(CardKind.PLAYLIST),
            o.optString("title"), o.optString("subtitle"), o.optString("artwork"), o.optJSONArray("tracks").toTracks(), o.optString("year"),
        )
    }.getOrNull()

    // --- Home ------------------------------------------------------------------------------------

    /** Signed-in and signed-out feeds are kept apart, so one never shows up as the other. */
    private fun homeKey() = com.dopdop.bopdop.data.account.AccountStore.let { a ->
        if (a.signedIn) "home:in:${a.account.value?.handle.orEmpty()}" else "home:out"
    }

    /** The last Home, already read and parsed: warmed at launch so Home can show it on its first frame. */
    @Volatile private var homeParsed: Pair<String, HomeFeed>? = null

    suspend fun saveHome(feed: HomeFeed) = homeKey().let { key -> homeParsed = key to feed; write(key, JSONObject()
        .put("chips", JSONArray().also { a -> feed.chips.forEach { (l, p) -> a.put(JSONArray().put(l).put(p)) } })
        .put("shelves", JSONArray().also { a ->
            feed.shelves.forEach { sh ->
                a.put(JSONObject().put("title", sh.title).put("subtitle", sh.subtitle).put("style", sh.style.name)
                    .put("tracks", sh.tracks.toJsonArray())
                    .put("cards", JSONArray().also { c -> sh.cards.forEach { c.put(JSONObject().put("id", it.id).put("kind", it.kind.name).put("title", it.title).put("subtitle", it.subtitle).put("artwork", it.artwork)) } }))
            }
        }).toString()) }

    /** [lastHome] if it's already been read: no disk, safe while composing. */
    fun lastHomeNow(): HomeFeed? = homeParsed?.takeIf { it.first == homeKey() }?.second

    /** Whatever Home showed last time, however old: it's only a stand-in while the fresh one loads. */
    suspend fun lastHome(): HomeFeed? {
        val key = homeKey()
        homeParsed?.takeIf { it.first == key }?.let { return it.second }
        return parseHome(key)?.also { homeParsed = key to it }
    }

    private suspend fun parseHome(key: String): HomeFeed? = read(key)?.let { hit ->
        runCatching {
            val o = JSONObject(hit.json)
            val chips = o.optJSONArray("chips")?.let { a -> (0 until a.length()).map { a.getJSONArray(it).let { p -> p.getString(0) to p.getString(1) } } }.orEmpty()
            val shelves = o.optJSONArray("shelves")?.let { a ->
                (0 until a.length()).map { i ->
                    val s = a.getJSONObject(i)
                    Shelf(
                        title = s.optString("title"), subtitle = s.optString("subtitle"),
                        tracks = s.optJSONArray("tracks").toTracks(),
                        cards = s.optJSONArray("cards")?.let { c ->
                            (0 until c.length()).map { j ->
                                val k = c.getJSONObject(j)
                                Card(k.getString("id"), runCatching { CardKind.valueOf(k.optString("kind")) }.getOrDefault(CardKind.PLAYLIST), k.optString("title"), k.optString("subtitle"), k.optString("artwork"))
                            }
                        }.orEmpty(),
                        style = runCatching { ShelfStyle.valueOf(s.optString("style")) }.getOrDefault(ShelfStyle.CARDS),
                    )
                }
            }.orEmpty()
            HomeFeed(shelves, chips).takeIf { shelves.isNotEmpty() }
        }.getOrNull()
    }

    // --- mood tiles ----------------------------------------------------------------------------

    /** Never more than three category pages loading at once, however fast the grid is flung. */
    private val moodGate = Semaphore(3)

    /** Covers already known for a mood tile — no network, safe to call while composing. */
    fun moodArtNow(params: String): List<String>? = memory.get("mood:$params")?.let(::urls)

    suspend fun moodArt(params: String): List<String> {
        val key = "mood:$params"
        read(key)?.takeIf { it.fresh(MOOD_ART_TTL) }?.let { return urls(it.json).also { _ -> memory.put(key, it.json) } }
        return moodGate.withPermit {
            val art = MusicRepository.moodCategory(params).flatMap { sh -> sh.cards.map { it.artwork } + sh.tracks.map { it.artwork } }
                .filter { it.isNotBlank() }.distinct().take(2)
            if (art.isNotEmpty()) write(key, JSONArray(art).toString())
            art
        }
    }

    private fun urls(json: String) = runCatching { JSONArray(json).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())

    // --- storage ----------------------------------------------------------------------------

    private class Hit(val json: String, val at: Long) { fun fresh(ttl: Long) = System.currentTimeMillis() - at < ttl }

    private suspend fun read(key: String): Hit? = withContext(Dispatchers.IO) {
        memory.get(key)?.let { return@withContext Hit(it, System.currentTimeMillis()) }
        val f = file(key) ?: return@withContext null
        if (!f.exists()) return@withContext null
        runCatching { Hit(f.readText(), f.lastModified()).also { memory.put(key, it.json) } }.getOrNull()
    }

    private suspend fun write(key: String, json: String) = withContext(Dispatchers.IO) {
        memory.put(key, json)
        val f = file(key) ?: return@withContext
        runCatching {
            File(f.parentFile, f.name + ".tmp").apply { writeText(json); renameTo(f) }
            trim()
        }
    }

    /** Oldest files go first once the folder passes [MAX_FILES]. */
    private fun trim() {
        val files = dir?.listFiles()?.takeIf { it.size > MAX_FILES } ?: return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_FILES).forEach { it.delete() }
    }

    private fun file(key: String): File? {
        val d = dir ?: return null
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(d, "$hash.json")
    }
}
