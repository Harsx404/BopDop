package com.dopdop.bopdop.data

import android.content.Context
import android.util.LruCache
import com.dopdop.bopdop.data.model.CardKind
import com.dopdop.bopdop.data.model.Collection
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
