package com.dopdop.bopdop.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomanizeTest {
    @Test fun hindiReadsTheWayItIsTyped() {
        assertEquals("tum hi ho", Romanize.line("तुम ही हो"))
        assertEquals("teri aankhon mein", Romanize.line("तेरी आंखों में"))
        assertEquals("samajhna", Romanize.line("समझना"))
        assertEquals("kamal", Romanize.line("कमल"))
        assertEquals("mera dil", Romanize.line("मेरा दिल"))
    }

    @Test fun punjabiReadsTheWayItIsTyped() {
        assertEquals("tere naal", Romanize.line("ਤੇਰੇ ਨਾਲ"))
        assertEquals("sohna", Romanize.line("ਸੋਹਣਾ"))
    }

    @Test fun leavesLatinAndPunctuationAlone() {
        assertEquals("Baby, tu", Romanize.line("Baby, तू"))
        assertTrue(Romanize.hasIndic("ओ साथी"))
        assertFalse(Romanize.hasIndic("Tum hi ho"))
    }
}
