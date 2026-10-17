package com.dopdop.bopdop.data.lyrics

import com.dopdop.bopdop.data.model.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParseTest {
    @Test fun wordTimedLinesKeepJustTheWords() {
        val lrc = """
            [ti:Sahiba]
            [length:03:37]

            [00:00.28]<00:00.28>Je <00:00.67>ishq <00:01.28>hai <00:01.51>kareya, <00:02.42>
            [00:04.25]<00:04.25>Rabb <00:04.73>manneya <00:05.67>tainu
        """.trimIndent()
        assertEquals(listOf(LyricLine(280, "Je ishq hai kareya,"), LyricLine(4250, "Rabb manneya tainu")), LyricsApi.parseLrc(lrc))
    }

    @Test fun plainTimedLinesStillWork() {
        assertEquals(listOf(LyricLine(61_500, "Hello")), LyricsApi.parseLrc("[01:01.50]Hello"))
    }
}
