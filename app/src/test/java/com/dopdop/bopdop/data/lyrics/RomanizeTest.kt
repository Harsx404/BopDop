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

    @Test fun anyNonLatinScriptCanBeRomanized() {
        listOf("நான்", "నేను", "ನಾನು", "ഞാൻ", "আমি", "હું", "ମୁଁ", "میں", "사랑해", "我爱你", "夜に駆ける", "Привет", "Γεια", "ฉันรักเธอ")
            .forEach { assertTrue(it, Romanize.canRomanize(it)) }
        assertFalse(Romanize.canRomanize("Shape of You (Remix) 2024"))
    }

    @Test fun japaneseIsSpottedByItsKana() {
        assertTrue(Romanize.isJapanese(listOf("夜に駆ける")))
        assertFalse(Romanize.isJapanese(listOf("我爱你")))
    }
}
