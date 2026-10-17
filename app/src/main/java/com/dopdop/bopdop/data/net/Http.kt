package com.dopdop.bopdop.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val JSON = "application/json".toMediaType()

    suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
            res.body?.string().orEmpty()
        }
    }

    suspend fun getJson(url: String, headers: Map<String, String> = emptyMap()): JSONObject =
        JSONObject(getText(url, headers))

    suspend fun postJson(url: String, body: JSONObject, headers: Map<String, String> = emptyMap()): JSONObject =
        JSONObject(postText(url, body, headers))

    suspend fun postText(url: String, body: JSONObject, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .post(body.toString().toRequestBody(JSON))
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
            res.body?.string().orEmpty()
        }
    }
}

/**
 * Response memo bounded by size, not count: catalogue pages are hundreds of KB of JSON, and
 * holding them as parsed trees ran the heap past 500 MB while scrolling. Text is several times
 * smaller and is parsed again on a hit.
 */
class TextCache(private val ttlMs: Long, private val maxChars: Long) {
    private data class Entry(val at: Long, val text: String)
    private val map = LinkedHashMap<String, Entry>(64, 0.75f, true)
    private var chars = 0L

    @Synchronized fun get(key: String): String? {
        val e = map[key] ?: return null
        if (System.currentTimeMillis() - e.at > ttlMs) { map.remove(key); chars -= e.text.length; return null }
        return e.text
    }

    @Synchronized fun put(key: String, text: String) {
        if (text.length > maxChars / 4) return
        map.put(key, Entry(System.currentTimeMillis(), text))?.let { chars -= it.text.length }
        chars += text.length
        val it = map.entries.iterator()
        while (chars > maxChars && it.hasNext()) { val e = it.next(); chars -= e.value.text.length; it.remove() }
    }

    @Synchronized fun clear() { map.clear(); chars = 0 }
}

/** Small TTL memo for catalogue calls (same idea as the desktop backend's musicCache). */
class TtlCache<K, V>(private val ttlMs: Long, private val max: Int = 200) {
    private data class Entry<V>(val at: Long, val value: V)
    private val map = object : LinkedHashMap<K, Entry<V>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>?) = size > max
    }

    @Synchronized fun get(key: K): V? = map[key]?.takeIf { System.currentTimeMillis() - it.at < ttlMs }?.value
    @Synchronized fun put(key: K, value: V) { map[key] = Entry(System.currentTimeMillis(), value) }
    @Synchronized fun remove(key: K) { map.remove(key) }
    @Synchronized fun clear() = map.clear()

    suspend fun getOrLoad(key: K, load: suspend () -> V): V = get(key) ?: load().also { put(key, it) }
}
