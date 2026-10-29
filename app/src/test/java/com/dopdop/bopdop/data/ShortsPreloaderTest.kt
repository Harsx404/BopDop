package com.dopdop.bopdop.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class ShortsPreloaderTest {
    @Test fun wifiWarmsTwoDistinctNextClipsAndMobileWarmsOne() {
        val ids = listOf("a", "b", "b", "a", "c", "d")
        assertEquals(listOf("b", "c"), shortsAhead(ids, 0, false))
        assertEquals(listOf("b"), shortsAhead(ids, 0, true))
        assertTrue(shortsAhead(ids, ids.lastIndex, false).isEmpty())
    }

    @Test fun scrollingOntoAPrefetchSharesItsExistingResolution() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val gate = CompletableDeferred<String>()
        var calls = 0
        val loader = ShortsPreloader<String, String>(scope, load = { calls++; gate.await() }, warm = { _, _ -> })
        try {
            loader.updateWindow("a", listOf("b")); yield()
            val playback = async { loader.get("b") }; yield()
            gate.complete("ready")
            assertEquals("ready", playback.await())
            assertEquals(1, calls)
        } finally { loader.close(); scope.cancel() }
    }

    @Test fun staleWarmupsAreCancelledAfterFastSwipes() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val never = CompletableDeferred<String>()
        val loader = ShortsPreloader<String, String>(scope, load = { key ->
            if (key == "b") try { started.complete(Unit); never.await() } finally { cancelled.complete(Unit) }
            else "ready-$key"
        }, warm = { _, _ -> })
        try {
            loader.updateWindow("a", listOf("b")); started.await()
            loader.updateWindow("d", listOf("e")); cancelled.await()
            assertEquals("ready-d", loader.get("d"))
        } finally { loader.close(); scope.cancel() }
    }
}
