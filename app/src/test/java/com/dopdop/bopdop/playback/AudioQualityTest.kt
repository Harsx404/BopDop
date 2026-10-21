package com.dopdop.bopdop.playback

import com.dopdop.bopdop.data.AudioQuality
import com.dopdop.bopdop.ui.player.speedLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioQualityTest {
    private val url = "https://aac.saavncdn.com/123/abc_320.mp4"

    @Test fun saavnFilesFollowTheQuality() {
        assertEquals(url, StreamResolver.saavnAt(url, AudioQuality.HIGH))
        assertEquals("https://aac.saavncdn.com/123/abc_160.mp4", StreamResolver.saavnAt(url, AudioQuality.NORMAL))
        assertEquals("https://aac.saavncdn.com/123/abc_96.mp4", StreamResolver.saavnAt(url, AudioQuality.SAVER))
    }

    @Test fun speedsReadLikeAPlayer() {
        assertEquals("1×", speedLabel(1f))
        assertEquals("1.5×", speedLabel(1.5f))
        assertEquals("1.25×", speedLabel(1.25f))
        assertEquals("0.75×", speedLabel(0.75f))
    }
}
