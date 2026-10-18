package com.dopdop.bopdop.ui.island

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IslandTest {
    @Test fun phonesWithTheirOwnIslandAreRecognised() {
        assertEquals("Now Bar", NativeIsland.detect("samsung", "samsung", 35))
        assertEquals("Live Alerts", NativeIsland.detect("OnePlus", "OnePlus", 35))
        assertEquals("Origin Island", NativeIsland.detect("vivo", "iQOO", 35))
        assertEquals("Origin Island", NativeIsland.detect("vivo", "vivo", 36))
        assertNull(NativeIsland.detect("samsung", "samsung", 34))
        assertNull(NativeIsland.detect("Google", "google", 36))
    }
}
