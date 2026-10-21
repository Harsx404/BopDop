package com.dopdop.bopdop.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class JapaneseRomajiTest {
    @Test fun kanaToHepburn() {
        assertEquals("supaakuru", JapaneseRomaji.kana("スパークル"))
        assertEquals("kitto", JapaneseRomaji.kana("きっと"))
        assertEquals("matcha", JapaneseRomaji.kana("まっちゃ"))
        assertEquals("kyou", JapaneseRomaji.kana("きょう"))
        assertEquals("shinjitsu", JapaneseRomaji.kana("しんじつ"))
        assertEquals("kon'ya", JapaneseRomaji.kana("こんや"))
        assertEquals("fantajii", JapaneseRomaji.kana("ファンタジー"))
    }

    @Test fun linesReadLikeTypedLyrics() {
        assertEquals("kimi no na wa", JapaneseRomaji.line("君の名は"))
        assertEquals("kyou wa hare da ne", JapaneseRomaji.line("今日は晴れだね"))
        assertEquals("watashi wo mite", JapaneseRomaji.line("私を見て"))
        assertEquals("aishiteru", JapaneseRomaji.line("愛してる"))
        assertEquals("suki desu", JapaneseRomaji.line("好きです"))
        assertEquals("ikanai", JapaneseRomaji.line("行かない"))
        assertEquals("yume wo mita, kimi to", JapaneseRomaji.line("夢を見た、君と"))
        assertEquals("I love you mada", JapaneseRomaji.line("I love you まだ"))
    }
}
