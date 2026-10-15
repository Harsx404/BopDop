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

    suspend fun postJson(url: String, body: JSONObject, headers: Map<String, String> = emptyMap()): JSONObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .post(body.toString().toRequestBody(JSON))
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
            JSONObject(res.body?.string().orEmpty())
        }
    }
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

    suspend fun getOrLoad(key: K, load: suspend () -> V): V = get(key) ?: load().also { put(key, it) }
}
