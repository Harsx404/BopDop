package com.dopdop.bopdop.data.ytm

import org.junit.Assert.assertEquals
import org.junit.Test

class YtShortsTest {
    @Test fun readsYouTubeViewCounts() {
        assertEquals(19_000_000L, YtShorts.viewCount("19M views"))
        assertEquals(8_700L, YtShorts.viewCount("8.7K views"))
        assertEquals(1_100_000L, YtShorts.viewCount("1.1M views"))
        assertEquals(2_000_000_000L, YtShorts.viewCount("2B views"))
        assertEquals(523L, YtShorts.viewCount("523 views"))
        assertEquals(0L, YtShorts.viewCount("No views"))
    }
}
