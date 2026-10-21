package com.dopdop.bopdop.data

import com.dopdop.bopdop.data.scrobble.LastFm
import org.junit.Assert.assertEquals
import org.junit.Test

class LastFmTest {
    @Test fun signsSortedParamsWithoutFormat() {
        // md5("api_keyxxmethodauth.getTokensecret")
        val sig = LastFm.sign(mapOf("method" to "auth.getToken", "api_key" to "xx", "format" to "json"), "secret")
        val expected = java.security.MessageDigest.getInstance("MD5").digest("api_keyxxmethodauth.getTokensecret".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, sig)
    }

    @Test fun countsAfterHalfOrFourMinutes() {
        assertEquals(90, LastFm.needed(180))
        assertEquals(240, LastFm.needed(900))
    }
}
