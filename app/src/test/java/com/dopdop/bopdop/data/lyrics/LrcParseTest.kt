package com.dopdop.bopdop.data.lyrics

import com.dopdop.bopdop.data.model.LyricLine
import com.dopdop.bopdop.data.model.LyricWord
import com.dopdop.bopdop.data.model.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParseTest {
    private val lrc = """
        [ti:Sahiba]
        [length:03:37]

        [00:00.28]<00:00.28>Je <00:00.67>ishq <00:01.28>hai <00:01.51>kareya, <00:02.42>
        [00:04.25]<00:04.25>Rabb <00:04.73>manneya <00:05.67>tainu
    """.trimIndent()

    @Test fun wordTimedLinesKeepTheTextAndEveryWordsStart() {
        val lines = LyricsApi.parseLrc(lrc)
        assertEquals(listOf("Je ishq hai kareya,", "Rabb manneya tainu"), lines.map { it.text })
        assertEquals(listOf(280L, 4250L), lines.map { it.timeMs })
        assertEquals(
            listOf(LyricWord(280, "Je "), LyricWord(670, "ishq "), LyricWord(1280, "hai "), LyricWord(1510, "kareya,")),
            lines[0].words,
        )
        // The words join back into the line, so the karaoke sweep can be drawn over it.
        lines.forEach { assertEquals(it.text, it.words.joinToString("") { w -> w.text }) }
        assertTrue(Lyrics(lines, null).isWordSynced)
    }

    @Test fun plainTimedLinesStillWork() {
        assertEquals(listOf(LyricLine(61_500, "Hello")), LyricsApi.parseLrc("[01:01.50]Hello"))
    }

    @Test fun lyricsSurviveTheTripToDisk() {
        val l = Lyrics(LyricsApi.parseLrc(lrc), "plain text")
        assertEquals(l, LyricsDisk.decode(org.json.JSONObject(LyricsDisk.encode(l).toString())))
    }

    @Test fun offsetLabelsReadNaturally() {
        assertEquals("In sync", LyricOffsets.label(0))
        assertEquals("+0.50 s", LyricOffsets.label(500))
        assertEquals("−0.25 s", LyricOffsets.label(-250))
    }
}
