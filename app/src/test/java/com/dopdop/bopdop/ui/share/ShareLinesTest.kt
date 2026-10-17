package com.dopdop.bopdop.ui.share

import com.dopdop.bopdop.data.model.LyricLine
import com.dopdop.bopdop.data.model.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Test

class ShareLinesTest {
    private val song = Lyrics(
        listOf(LyricLine(0, "First"), LyricLine(5_000, "Second"), LyricLine(9_000, "Third"), LyricLine(12_000, ""), LyricLine(14_000, "After the gap")),
        null,
    )

    @Test fun startsAtTheLineBeingSungAndStopsAtAPause() {
        assertEquals(listOf("Second", "Third"), linesNow(song, 6_000))
    }

    @Test fun skipsALeadingGap() {
        assertEquals(listOf("After the gap"), linesNow(song, 12_500))
    }

    @Test fun plainLyricsStartFromTheTopAndStayCardSized() {
        val plain = Lyrics(emptyList(), (1..10).joinToString("\n") { "Line $it" })
        assertEquals(listOf("Line 1", "Line 2", "Line 3", "Line 4"), linesNow(plain, 60_000))
    }
}
