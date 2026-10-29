package com.dopdop.bopdop.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** A bounded current/next window. UI-thread confined; shared requests survive a page handoff. */
class ShortsPreloader<K, V>(
    private val scope: CoroutineScope,
    private val load: suspend (K) -> V,
    private val warm: suspend (K, V) -> Unit,
) {
    private val requests = linkedMapOf<K, Deferred<V>>()
    private val warmed = hashSetOf<K>()
    private val resolving = Semaphore(2)
    private var warmer: Job? = null
    private var window: List<K> = emptyList()

    suspend fun get(key: K): V {
        val request = requests.getOrPut(key) { scope.async(start = CoroutineStart.LAZY) { resolving.withPermit { load(key) } } }
        return try { request.await() } catch (e: Exception) {
            // Cancelling a waiting page does not cancel a shared request for the next page.
            if (request.isCancelled && requests[key] === request) requests.remove(key)
            throw e
        }
    }

    fun updateWindow(current: K, upcoming: List<K>) {
        val next = (listOf(current) + upcoming).distinct().take(3)
        if (next == window) return
        window = next
        warmer?.cancel()
        requests.keys.filter { it !in next }.forEach { requests.remove(it)?.cancel() }
        warmed.retainAll(next.toSet())
        // One speculative download at a time leaves bandwidth and a resolver slot for playback.
        warmer = scope.launch {
            next.drop(1).forEach { key ->
                if (key !in warmed) try {
                    warm(key, get(key))
                    warmed += key
                } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Playback retries normally. */ }
            }
        }
    }

    fun close() { warmer?.cancel(); requests.values.toList().forEach { it.cancel() }; requests.clear(); warmed.clear() }
    fun invalidate(key: K) { requests.remove(key)?.cancel(); warmed.remove(key) }
}

/** Less speculative data on a metered connection, with no repeated or current clip in the window. */
fun shortsAhead(ids: List<String>, page: Int, metered: Boolean): List<String> = ids.drop(page + 1)
    .filter { it != ids.getOrNull(page) }.distinct().take(if (metered) 1 else 2)
