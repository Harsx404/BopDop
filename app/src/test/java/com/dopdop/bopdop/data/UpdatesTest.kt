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
        // Point releases of a beta.
        assertTrue(Updates.isNewer("1.3.0-beta.6.1", "1.3.0-beta.6"))
        assertFalse(Updates.isNewer("1.3.0-beta.6", "1.3.0-beta.6.1"))
        assertTrue(Updates.isNewer("1.3.0-beta.7", "1.3.0-beta.6.1"))
        assertTrue(Updates.isNewer("1.3.0-beta.6.7.1", "1.3.0-beta.6.7"))
        assertTrue(Updates.isNewer("1.3.0-beta.6.7.5", "1.3.0-beta.6.7.1"))
    }

    @Test fun picksThePhonesOwnBuildElseUniversal() {
        val assets = listOf(
            "BopDop-v1.4.0.apk" to "u", "BopDop-v1.4.0-arm64-v8a.apk" to "a64",
            "BopDop-v1.4.0-armeabi-v7a.apk" to "a32", "BopDop-v1.4.0-x86_64.apk" to "x64",
        )
        org.junit.Assert.assertEquals("a64", Updates.pickApk(assets, listOf("arm64-v8a", "armeabi-v7a")))
        org.junit.Assert.assertEquals("u", Updates.pickApk(assets, listOf("riscv64")))
        org.junit.Assert.assertEquals("only", Updates.pickApk(listOf("BopDop-v1.3.0.apk" to "only"), listOf("arm64-v8a")))
    }
}
