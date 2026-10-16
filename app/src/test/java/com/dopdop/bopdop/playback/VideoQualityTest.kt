package com.dopdop.bopdop.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoQualityTest {
    private fun opt(id: String, mime: String, h: Int, fps: Int = 30) = PictureOption(id, mime, h * 16 / 9, h, fps)
    private val formats = listOf(
        opt("avc480", "video/mp4; codecs=\"avc1.4d401e\"", 480),
        opt("avc1080", "video/mp4; codecs=\"avc1.640028\"", 1080),
        opt("vp1080", "video/webm; codecs=\"vp9\"", 1080),
        opt("vp1080p60", "video/webm; codecs=\"vp9\"", 1080, 60),
        opt("vp2160", "video/webm; codecs=\"vp9\"", 2160),
        opt("av2160", "video/mp4; codecs=\"av01.0.12M.08\"", 2160),
    )

    @Test fun takesTallestUnderTheCapPreferringH264() {
        assertEquals("avc1080", choosePicture(formats, 1080) { true })
        assertEquals("vp2160", choosePicture(formats, 2160) { true })
    }

    @Test fun skipsWhatThePhoneCannotDecode() {
        assertEquals("vp1080p60", choosePicture(formats, 2160) { !it.mimeType.contains("avc1") && it.height < 2160 })
    }

    @Test fun fallsBackToSmallestWhenNothingFitsTheCap() {
        assertEquals("avc480", choosePicture(formats, 240) { true })
        assertNull(choosePicture(formats, 1080) { false })
    }
}
