package com.dopdop.bopdop.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    @Test fun numbersCompareByPart() {
        assertTrue(Updates.isNewer("1.10.0", "1.9.2"))
        assertTrue(Updates.isNewer("v1.3.0", "1.2.0"))
        assertFalse(Updates.isNewer("1.2.0", "1.2.0"))
        assertFalse(Updates.isNewer("1.2.0", "1.3.0-beta.1"))
    }

    @Test fun releasesBeatTheirBetasAndLaterBetasBeatEarlierOnes() {
        assertTrue(Updates.isNewer("1.3.0", "1.3.0-beta.1"))
        assertTrue(Updates.isNewer("1.3.0-beta.2", "1.3.0-beta.1"))
        assertFalse(Updates.isNewer("1.3.0-beta.1", "1.3.0"))
        assertTrue(Updates.isNewer("1.3.0-beta.1", "1.2.0"))
    }
}
