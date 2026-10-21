package com.dopdop.bopdop.data.lyrics

import com.atilika.kuromoji.ipadic.Token
import com.atilika.kuromoji.ipadic.Tokenizer

/**
 * Japanese lyrics → romaji the way they're usually typed: "君の名は" → "kimi no na wa",
 * "今日は晴れだね" → "kyou wa hare da ne", "スパークル" → "supaakuru".
 *
 * Kuromoji (with the IPA dictionary) splits each line into words and gives every word its
 * reading, kanji included. Readings become Hepburn with a few lyric conventions: particles
 * は/へ/を read "wa"/"e"/"wo", long vowels are doubled letters (no macrons), and verb endings and
 * auxiliaries stay on their word ("mite", "aishiteru") rather than floating apart.
 */
object JapaneseRomaji {
    /** Built once, on first use; loading the dictionary takes a moment, so call off the main thread. */
    private val tokenizer by lazy { Tokenizer() }

    fun line(text: String): String {
        val words = ArrayList<String>()
        var glue = false
        var prev: Token? = null
        for (tk in tokenizer.tokenize(text)) {
            val part = romaji(tk)
            if (part.isBlank()) { glue = false; continue }
            when {
                // Punctuation hugs the word before it.
                tk.partOfSpeechLevel1 == "記号" -> if (words.isEmpty()) words += part else words[words.lastIndex] += part
                glue && words.isNotEmpty() && attaches(tk, prev) -> words[words.lastIndex] += part
                else -> words += part
            }
            glue = tk.partOfSpeechLevel1 != "記号"
            prev = tk
        }
        return words.joinToString(" ").replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Endings that belong to the word before: conjugations (auxiliaries after a verb or adjective,
     * "mita", "shinai"), て/で/ば, verb and noun suffixes. After a noun the copula stays a word of
     * its own, as lyrics are usually typed ("hare da", "suki desu").
     */
    private fun attaches(tk: Token, prev: Token?): Boolean {
        val pos1 = tk.partOfSpeechLevel1
        val pos2 = tk.partOfSpeechLevel2
        val afterInflecting = prev?.partOfSpeechLevel1 in setOf("動詞", "形容詞", "助動詞")
        return (pos1 == "助動詞" && afterInflecting) ||
            (pos1 == "助詞" && pos2 == "接続助詞" && tk.surface in setOf("て", "で", "ば", "ちゃ", "じゃ")) ||
            (pos1 == "動詞" && (pos2 == "非自立" || pos2 == "接尾")) ||
            (pos1 == "名詞" && pos2 == "接尾" && tk.surface in setOf("さん", "ちゃん", "くん", "様", "たち", "達")) ||
            (pos1 == "形容詞" && pos2 == "非自立")
    }

    private fun romaji(tk: Token): String {
        val surface = tk.surface
        if (tk.partOfSpeechLevel1 == "記号") return PUNCT[surface] ?: if (surface.any { it.isLetterOrDigit() }) surface else ""
        if (tk.partOfSpeechLevel1 == "助詞") when (surface) {
            "は" -> return "wa"
            "へ" -> return "e"
            "を" -> return "wo"
        }
        // Words already in Latin letters or digits ("love", "2") stay as written.
        if (surface.none { isKana(it) || isKanji(it) }) return surface
        // The dictionary's reading (katakana); unknown words fall back to their own kana.
        val reading = tk.reading?.takeIf { it != "*" && it.isNotBlank() } ?: surface
        if (reading.any(::isKanji)) return ""
        return kana(reading)
    }

    private fun isKana(c: Char) = c in '぀'..'ヿ'
    private fun isKanji(c: Char) = c in '一'..'鿿' || c in '㐀'..'䶿' || c == '々'

    /** Hiragana or katakana → Hepburn romaji. */
    internal fun kana(s: String): String {
        val k = buildString { s.forEach { append(if (it in 'ぁ'..'ゖ') it + 0x60 else it) } } // all katakana
        val out = StringBuilder()
        var i = 0
        var doubleNext = false
        while (i < k.length) {
            val c = k[i]
            // Two-kana sounds first (キャ, シュ, ファ…), then single kana.
            val pair = if (i + 1 < k.length) DIGRAPHS[k.substring(i, i + 2)] else null
            val syl = pair ?: MONO[c]
            when {
                c == 'ッ' -> { doubleNext = true; i++; continue }
                c == 'ー' -> { out.lastOrNull { it in "aeiou" }?.let { out.append(it) }; i++; continue }
                c == 'ン' -> {
                    out.append('n')
                    // ん before a vowel or y is written n' so it isn't read as "na"/"nya".
                    val next = k.getOrNull(i + 1)?.let { n -> MONO[n] }
                    if (next != null && (next[0] in "aeiouy")) out.append('\'')
                    i++; continue
                }
                syl != null -> {
                    if (doubleNext) { out.append(if (syl.startsWith("ch")) 't' else syl[0]); doubleNext = false }
                    out.append(syl)
                    i += if (pair != null) 2 else 1
                }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }

    private val PUNCT = mapOf("、" to ",", "。" to ".", "！" to "!", "？" to "?", "・" to " ", "「" to "\"", "」" to "\"", "『" to "\"", "』" to "\"", "（" to "(", "）" to ")", "〜" to "~", "～" to "~", "…" to "…", "♪" to "♪")

    private val MONO: Map<Char, String> = buildMap {
        val rows = listOf(
            "アイウエオ" to listOf("a", "i", "u", "e", "o"),
            "カキクケコ" to listOf("ka", "ki", "ku", "ke", "ko"), "ガギグゲゴ" to listOf("ga", "gi", "gu", "ge", "go"),
            "サシスセソ" to listOf("sa", "shi", "su", "se", "so"), "ザジズゼゾ" to listOf("za", "ji", "zu", "ze", "zo"),
            "タチツテト" to listOf("ta", "chi", "tsu", "te", "to"), "ダヂヅデド" to listOf("da", "ji", "zu", "de", "do"),
            "ナニヌネノ" to listOf("na", "ni", "nu", "ne", "no"),
            "ハヒフヘホ" to listOf("ha", "hi", "fu", "he", "ho"), "バビブベボ" to listOf("ba", "bi", "bu", "be", "bo"),
            "パピプペポ" to listOf("pa", "pi", "pu", "pe", "po"),
            "マミムメモ" to listOf("ma", "mi", "mu", "me", "mo"),
            "ヤユヨ" to listOf("ya", "yu", "yo"),
            "ラリルレロ" to listOf("ra", "ri", "ru", "re", "ro"),
            "ワヰヱヲ" to listOf("wa", "i", "e", "wo"),
            "ァィゥェォ" to listOf("a", "i", "u", "e", "o"), "ャュョヮ" to listOf("ya", "yu", "yo", "wa"),
            "ヴ" to listOf("vu"), "ヵヶ" to listOf("ka", "ke"),
        )
        rows.forEach { (kana, roma) -> kana.forEachIndexed { i, c -> put(c, roma[i]) } }
    }

    private val DIGRAPHS: Map<String, String> = buildMap {
        val yo = mapOf('ャ' to "a", 'ュ' to "u", 'ョ' to "o")
        val bases = mapOf(
            'キ' to "ky", 'ギ' to "gy", 'シ' to "sh", 'ジ' to "j", 'チ' to "ch", 'ヂ' to "j", 'ニ' to "ny",
            'ヒ' to "hy", 'ビ' to "by", 'ピ' to "py", 'ミ' to "my", 'リ' to "ry",
        )
        bases.forEach { (b, r) -> yo.forEach { (s, v) -> put("$b$s", r + v) } }
        // Loanword sounds.
        listOf(
            "ファ" to "fa", "フィ" to "fi", "フェ" to "fe", "フォ" to "fo", "フュ" to "fyu",
            "ティ" to "ti", "ディ" to "di", "トゥ" to "tu", "ドゥ" to "du", "テュ" to "tyu", "デュ" to "dyu",
            "ウィ" to "wi", "ウェ" to "we", "ウォ" to "wo", "ヴァ" to "va", "ヴィ" to "vi", "ヴェ" to "ve", "ヴォ" to "vo",
            "シェ" to "she", "ジェ" to "je", "チェ" to "che", "ツァ" to "tsa", "ツィ" to "tsi", "ツェ" to "tse", "ツォ" to "tso",
            "イェ" to "ye", "クァ" to "kwa", "グァ" to "gwa",
        ).forEach { (k, v) -> put(k, v) }
    }
}
